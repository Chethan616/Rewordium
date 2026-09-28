import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:in_app_purchase_android/billing_client_wrappers.dart';
import 'package:in_app_purchase_android/in_app_purchase_android.dart';

/// Purchase state for UI - never expose raw billing codes
enum PurchaseState {
  idle,
  loading,
  processing,
  pending,
  restoring,
  success,
  alreadyOwned,
  cancelled,
  billingUnavailable,
  error,
}

/// User-friendly messages for each state
class PurchaseStateMessages {
  static const Map<PurchaseState, String> titles = {
    PurchaseState.idle: '',
    PurchaseState.loading: 'Loading...',
    PurchaseState.processing: 'Processing Payment',
    PurchaseState.pending: 'Payment Pending',
    PurchaseState.restoring: 'Restoring Purchases',
    PurchaseState.success: 'Welcome to Pro! 🎉',
    PurchaseState.alreadyOwned: 'Already Subscribed',
    PurchaseState.cancelled: '',
    PurchaseState.billingUnavailable: 'Service Unavailable',
    PurchaseState.error: 'Something Went Wrong',
  };

  static const Map<PurchaseState, String> messages = {
    PurchaseState.idle: '',
    PurchaseState.loading: 'Please wait...',
    PurchaseState.processing:
        'Please don\'t go back or close the app.\nThis will only take a moment.',
    PurchaseState.pending:
        'Google Play is waiting for your payment to be confirmed (this can take '
            'a few minutes for UPI and some cards).\nPro unlocks automatically '
            'once it goes through — you can safely close this screen.',
    PurchaseState.restoring: 'Checking for previous purchases...',
    PurchaseState.success:
        'You now have unlimited access to all Pro features. Thank you for your support!',
    PurchaseState.alreadyOwned:
        'You already have an active Pro subscription. Enjoy your unlimited access!',
    PurchaseState.cancelled: 'Payment was cancelled.',
    PurchaseState.billingUnavailable:
        'Payment services are temporarily unavailable. Please check your internet connection and try again.',
    PurchaseState.error:
        'We couldn\'t complete your purchase. Please try again or contact support if the problem persists.',
  };
}

/// Called whenever Google Play reports an active subscription.
///
/// [autoRenewing] is false when the user cancelled but is still inside the
/// paid period; Play keeps reporting the purchase until that period ends.
typedef SubscriptionActiveCallback = void Function(
  String productId,
  String? purchaseToken,
  DateTime? purchaseDate,
  bool isRestored,
  bool autoRenewing,
);

/// Service for handling Google Play In-App Purchases (Subscriptions)
///
/// Product IDs should match what's configured in Google Play Console:
/// - Monetize → Products → Subscriptions
class BillingService extends ChangeNotifier {
  static const String _monthlySubscriptionId = 'rewordium_pro_monthly';
  static const String _yearlySubscriptionId = 'rewordium_pro_yearly';

  /// Promotional offer applied when the user is eligible for it.
  static const String _specialOfferId = 'special-discount';

  static final Set<String> _productIds = {
    _monthlySubscriptionId,
    _yearlySubscriptionId,
  };

  final InAppPurchase _inAppPurchase = InAppPurchase.instance;
  StreamSubscription<List<PurchaseDetails>>? _subscription;

  List<ProductDetails> _products = [];
  final List<PurchaseDetails> _purchases = [];
  bool _isAvailable = false;
  bool _purchasePending = false;
  bool _loading = true;
  String? _errorMessage;
  PurchaseState _purchaseState = PurchaseState.idle;

  // For tracking restore purchases completion
  bool _isRestoring = false;
  Completer<bool>? _restoreCompleter;

  // Callbacks
  Function(bool success, String? message)? onPurchaseComplete;
  SubscriptionActiveCallback? onSubscriptionActive;
  Function(PurchaseState state)? onStateChanged;

  // Getters
  List<ProductDetails> get products => _products;
  List<PurchaseDetails> get purchases => _purchases;
  bool get isAvailable => _isAvailable;
  bool get purchasePending => _purchasePending;
  bool get loading => _loading;
  String? get errorMessage => _errorMessage;
  PurchaseState get purchaseState => _purchaseState;

  /// Whether back navigation should be blocked (during payment processing)
  bool get shouldBlockNavigation => _purchaseState == PurchaseState.processing;

  /// Get user-friendly title for current state
  String get stateTitle => PurchaseStateMessages.titles[_purchaseState] ?? '';

  /// Get user-friendly message for current state
  String get stateMessage =>
      PurchaseStateMessages.messages[_purchaseState] ?? '';

  /// Update purchase state and notify
  void _updateState(PurchaseState newState) {
    _purchaseState = newState;
    onStateChanged?.call(newState);
    notifyListeners();
  }

  /// Reset state to idle
  void resetState() {
    // A pending payment is still pending when the screen is reopened.
    _purchaseState = _purchasePending ? PurchaseState.pending : PurchaseState.idle;
    _errorMessage = null;
    notifyListeners();
  }

  ProductDetails? get monthlyProduct => _productFor(_monthlySubscriptionId);

  ProductDetails? get yearlyProduct => _productFor(_yearlySubscriptionId);

  /// Play returns one [GooglePlayProductDetails] per offer of a subscription.
  /// Pick the one we will actually buy — the special offer when the user is
  /// eligible (Play only lists offers the user can redeem), otherwise the
  /// base plan — so the price shown is exactly the price charged.
  ProductDetails? _productFor(String productId) {
    final candidates = _products.where((p) => p.id == productId).toList();
    if (candidates.isEmpty) return null;
    String? offerIdOf(ProductDetails product) {
      if (product is GooglePlayProductDetails) {
        final index = product.subscriptionIndex;
        final offers = product.productDetails.subscriptionOfferDetails;
        if (index != null && offers != null && index < offers.length) {
          return offers[index].offerId;
        }
      }
      return null;
    }

    for (final product in candidates) {
      if (offerIdOf(product) == _specialOfferId) return product;
    }
    for (final product in candidates) {
      if (offerIdOf(product) == null) return product; // base plan
    }
    return candidates.first;
  }

  bool get hasActiveSubscription {
    return _purchases.any((purchase) =>
        purchase.status == PurchaseStatus.purchased ||
        purchase.status == PurchaseStatus.restored);
  }

  /// Initialize the billing service
  Future<void> initialize() async {
    // Only initialize on Android
    if (!Platform.isAndroid) {
      _loading = false;
      _errorMessage = 'In-app purchases only available on Android';
      notifyListeners();
      return;
    }

    _isAvailable = await _inAppPurchase.isAvailable();

    if (!_isAvailable) {
      _loading = false;
      _errorMessage = 'Store not available';
      notifyListeners();
      return;
    }

    // Listen to purchase updates
    final purchaseUpdated = _inAppPurchase.purchaseStream;
    _subscription = purchaseUpdated.listen(
      _onPurchaseUpdated,
      onDone: _onPurchaseStreamDone,
      onError: _onPurchaseStreamError,
    );

    // Load products
    await _loadProducts();

    // Silently re-validate existing subscriptions. This keeps renewing
    // subscribers' entitlement fresh on every launch without flashing a
    // "Restoring purchases" / "Welcome to Pro" screen at them.
    await syncActivePurchases();

    _loading = false;
    notifyListeners();
  }

  /// Load available products from the store
  Future<void> _loadProducts() async {
    try {
      final response = await _inAppPurchase.queryProductDetails(_productIds);

      if (response.notFoundIDs.isNotEmpty) {
        if (kDebugMode) {
          print('[BillingService] Products not found: ${response.notFoundIDs}');
        }
      }

      _products = response.productDetails;

      if (kDebugMode) {
        print('[BillingService] Loaded ${_products.length} products');
        for (var product in _products) {
          print('[BillingService] Product: ${product.id} - ${product.price}');
        }
      }

      notifyListeners();
    } catch (e) {
      _errorMessage = 'Error loading products: $e';
      if (kDebugMode) print('[BillingService] $_errorMessage');
    }
  }

  /// Queries Google Play for subscriptions that are active right now and
  /// reports them via [onSubscriptionActive] (acknowledging any that were
  /// never acknowledged — Play refunds unacknowledged purchases after 3 days).
  /// Does not touch the UI state. Safe to call repeatedly, e.g. after sign-in.
  Future<void> syncActivePurchases() async {
    if (!Platform.isAndroid || !_isAvailable) return;
    try {
      final addition = _inAppPurchase
          .getPlatformAddition<InAppPurchaseAndroidPlatformAddition>();
      final response = await addition.queryPastPurchases();
      if (response.error != null && kDebugMode) {
        print('[BillingService] queryPastPurchases error: ${response.error}');
      }
      for (final purchase in response.pastPurchases) {
        final wrapper = purchase.billingClientPurchase;
        if (wrapper.purchaseState != PurchaseStateWrapper.purchased) {
          continue; // pending payments are reported when they complete
        }
        if (!wrapper.isAcknowledged) {
          await _inAppPurchase.completePurchase(purchase);
        }
        purchase.status = PurchaseStatus.restored;
        _purchases.removeWhere((p) => p.productID == purchase.productID);
        _purchases.add(purchase);
        _reportSubscription(purchase, isRestored: true);
      }
      notifyListeners();
    } catch (e) {
      if (kDebugMode) print('[BillingService] syncActivePurchases failed: $e');
    }
  }

  /// Purchase a subscription
  Future<bool> purchaseSubscription(ProductDetails productDetails) async {
    if (!_isAvailable) {
      _updateState(PurchaseState.billingUnavailable);
      return false;
    }

    _purchasePending = true;
    _errorMessage = null;
    _updateState(PurchaseState.processing);

    try {
      // The chosen product entry already carries the offer token of the
      // offer we displayed (see _productFor), so price shown == price paid.
      final PurchaseParam purchaseParam =
          Platform.isAndroid && productDetails is GooglePlayProductDetails
              ? GooglePlayPurchaseParam(productDetails: productDetails)
              : PurchaseParam(productDetails: productDetails);

      // For subscriptions, we use buyNonConsumable
      final success = await _inAppPurchase.buyNonConsumable(
        purchaseParam: purchaseParam,
      );

      if (!success) {
        _purchasePending = false;
        _updateState(PurchaseState.error);
      }

      return success;
    } catch (e) {
      _purchasePending = false;
      if (kDebugMode) print('[BillingService] Purchase error: $e');
      final message = e.toString().toLowerCase();
      if (message.contains('already') || message.contains('owned')) {
        _updateState(PurchaseState.alreadyOwned);
      } else {
        _updateState(PurchaseState.error);
      }
      return false;
    }
  }

  /// Purchase monthly subscription
  Future<bool> purchaseMonthly() async {
    if (monthlyProduct == null) {
      _errorMessage = 'Monthly subscription not available';
      notifyListeners();
      return false;
    }
    return purchaseSubscription(monthlyProduct!);
  }

  /// Purchase yearly subscription
  Future<bool> purchaseYearly() async {
    if (yearlyProduct == null) {
      _errorMessage = 'Yearly subscription not available';
      notifyListeners();
      return false;
    }
    return purchaseSubscription(yearlyProduct!);
  }

  /// Restore previous purchases (user initiated).
  /// Returns true if an active purchase was found.
  Future<bool> restorePurchases() async {
    if (!_isAvailable) {
      _updateState(PurchaseState.billingUnavailable);
      return false;
    }

    _isRestoring = true;
    _restoreCompleter = Completer<bool>();
    _updateState(PurchaseState.restoring);

    try {
      await _inAppPurchase.restorePurchases();

      // The purchase stream delivers the result (an empty list when the user
      // owns nothing); the timeout only guards against a stalled store.
      final result = await _restoreCompleter!.future.timeout(
        const Duration(seconds: 15),
        onTimeout: () => hasActiveSubscription,
      );

      _isRestoring = false;
      if (!result && _purchaseState == PurchaseState.restoring) {
        _updateState(PurchaseState.idle);
      }
      return result;
    } catch (e) {
      _isRestoring = false;
      _restoreCompleter = null;
      _errorMessage = 'Error restoring purchases: $e';
      _updateState(PurchaseState.error);
      if (kDebugMode) print('[BillingService] $_errorMessage');
      return false;
    }
  }

  void _completeRestore(bool found) {
    if (_isRestoring &&
        _restoreCompleter != null &&
        !_restoreCompleter!.isCompleted) {
      _restoreCompleter!.complete(found);
    }
  }

  /// Handle purchase updates
  Future<void> _onPurchaseUpdated(List<PurchaseDetails> purchaseDetailsList) async {
    if (purchaseDetailsList.isEmpty) {
      // restorePurchases() found nothing.
      _completeRestore(false);
      return;
    }
    for (var purchaseDetails in purchaseDetailsList) {
      if (kDebugMode) {
        print(
            '[BillingService] Purchase update: ${purchaseDetails.productID} - ${purchaseDetails.status}');
      }

      switch (purchaseDetails.status) {
        case PurchaseStatus.pending:
          // Slow payment methods (UPI, cash, some cards). Don't trap the user
          // on a blocking spinner; Pro unlocks when Play confirms.
          _purchasePending = true;
          _updateState(PurchaseState.pending);
          break;

        case PurchaseStatus.purchased:
          _purchasePending = false;
          _handleSuccessfulPurchase(purchaseDetails, isRestored: false);
          _completeRestore(true);
          break;

        case PurchaseStatus.restored:
          _purchasePending = false;
          _handleSuccessfulPurchase(purchaseDetails, isRestored: true);
          _completeRestore(true);
          break;

        case PurchaseStatus.error:
          _purchasePending = false;
          _handlePurchaseError(purchaseDetails);
          _completeRestore(false);
          break;

        case PurchaseStatus.canceled:
          _purchasePending = false;
          _updateState(PurchaseState.cancelled);
          onPurchaseComplete?.call(false, null);
          _completeRestore(false);
          break;
      }

      // Acknowledge the purchase. Unacknowledged purchases are refunded by
      // Google Play after 3 days.
      if (purchaseDetails.pendingCompletePurchase) {
        try {
          await _inAppPurchase.completePurchase(purchaseDetails);
        } catch (e) {
          if (kDebugMode) print('[BillingService] completePurchase failed: $e');
        }
      }
    }
  }

  /// Handle purchase errors with user-friendly states
  void _handlePurchaseError(PurchaseDetails purchaseDetails) {
    final errorMessage = (purchaseDetails.error?.message ?? '').toLowerCase();
    final errorCode = (purchaseDetails.error?.code ?? '').toLowerCase();
    if (errorMessage.contains('already') ||
        errorMessage.contains('owned') ||
        errorMessage.contains('item_already_owned') ||
        errorCode.contains('already')) {
      _updateState(PurchaseState.alreadyOwned);
      onPurchaseComplete?.call(false, null);
      // They own it — make sure the entitlement is applied.
      unawaited(syncActivePurchases());
      return;
    }

    if (errorMessage.contains('unavailable') ||
        errorMessage.contains('disconnected') ||
        errorMessage.contains('service')) {
      _updateState(PurchaseState.billingUnavailable);
      onPurchaseComplete?.call(false, null);
      return;
    }

    // Generic error
    _updateState(PurchaseState.error);
    onPurchaseComplete?.call(false, null);
  }

  /// Handle successful purchase
  void _handleSuccessfulPurchase(
    PurchaseDetails purchaseDetails, {
    required bool isRestored,
  }) {
    _purchases.removeWhere((p) => p.productID == purchaseDetails.productID);
    _purchases.add(purchaseDetails);

    _updateState(PurchaseState.success);
    _reportSubscription(purchaseDetails, isRestored: isRestored);
    onPurchaseComplete?.call(true, null);
  }

  void _reportSubscription(PurchaseDetails purchaseDetails, {required bool isRestored}) {
    String? purchaseToken;
    DateTime? purchaseDate;
    var autoRenewing = true;
    if (Platform.isAndroid && purchaseDetails is GooglePlayPurchaseDetails) {
      final wrapper = purchaseDetails.billingClientPurchase;
      purchaseToken = wrapper.purchaseToken;
      autoRenewing = wrapper.isAutoRenewing;
      final purchaseTime = wrapper.purchaseTime;
      if (purchaseTime > 0) {
        purchaseDate =
            DateTime.fromMillisecondsSinceEpoch(purchaseTime, isUtc: true);
      }
    }
    onSubscriptionActive?.call(
      purchaseDetails.productID,
      purchaseToken,
      purchaseDate,
      isRestored,
      autoRenewing,
    );
  }

  void _onPurchaseStreamDone() {
    _subscription?.cancel();
  }

  void _onPurchaseStreamError(dynamic error) {
    if (kDebugMode) {
      print('[BillingService] Purchase stream error: $error');
    }
    _completeRestore(false);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
