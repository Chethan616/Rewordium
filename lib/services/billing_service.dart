import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:in_app_purchase_android/in_app_purchase_android.dart';
import 'package:in_app_purchase_android/billing_client_wrappers.dart';

/// Purchase state for UI - never expose raw billing codes
enum PurchaseState {
  idle,
  loading,
  processing,
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
    PurchaseState.success: 'Welcome to Pro! 🎉',
    PurchaseState.alreadyOwned: 'Already Subscribed',
    PurchaseState.cancelled: '',
    PurchaseState.billingUnavailable: 'Service Unavailable',
    PurchaseState.error: 'Something Went Wrong',
  };

  static const Map<PurchaseState, String> messages = {
    PurchaseState.idle: '',
    PurchaseState.loading: 'Please wait...',
    PurchaseState.processing: 'Please don\'t go back or close the app.\nThis will only take a moment.',
    PurchaseState.success: 'You now have unlimited access to all Pro features. Thank you for your support!',
    PurchaseState.alreadyOwned: 'You already have an active Pro subscription. Enjoy your unlimited access!',
    PurchaseState.cancelled: 'Payment was cancelled.',
    PurchaseState.billingUnavailable: 'Payment services are temporarily unavailable. Please check your internet connection and try again.',
    PurchaseState.error: 'We couldn\'t complete your purchase. Please try again or contact support if the problem persists.',
  };
}

/// Service for handling Google Play In-App Purchases (Subscriptions)
/// 
/// Product IDs should match what's configured in Google Play Console:
/// - Monetize → Products → Subscriptions
class BillingService extends ChangeNotifier {
  static const String _monthlySubscriptionId = 'rewordium_pro_monthly';
  static const String _yearlySubscriptionId = 'rewordium_pro_yearly';
  
  static final Set<String> _productIds = {
    _monthlySubscriptionId,
    _yearlySubscriptionId,
  };

  final InAppPurchase _inAppPurchase = InAppPurchase.instance;
  StreamSubscription<List<PurchaseDetails>>? _subscription;
  
  List<ProductDetails> _products = [];
  List<PurchaseDetails> _purchases = [];
  bool _isAvailable = false;
  bool _purchasePending = false;
  bool _loading = true;
  String? _errorMessage;
  PurchaseState _purchaseState = PurchaseState.idle;
  
  // Callbacks
  Function(bool success, String? message)? onPurchaseComplete;
  Function(String productId, String? purchaseToken)? onSubscriptionActive;
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
  String get stateMessage => PurchaseStateMessages.messages[_purchaseState] ?? '';

  /// Update purchase state and notify
  void _updateState(PurchaseState newState) {
    _purchaseState = newState;
    onStateChanged?.call(newState);
    notifyListeners();
  }

  /// Reset state to idle
  void resetState() {
    _purchaseState = PurchaseState.idle;
    _errorMessage = null;
    notifyListeners();
  }
  
  ProductDetails? get monthlyProduct => _products.cast<ProductDetails?>().firstWhere(
    (p) => p?.id == _monthlySubscriptionId,
    orElse: () => null,
  );
  
  ProductDetails? get yearlyProduct => _products.cast<ProductDetails?>().firstWhere(
    (p) => p?.id == _yearlySubscriptionId,
    orElse: () => null,
  );
  
  bool get hasActiveSubscription {
    return _purchases.any((purchase) => 
      purchase.status == PurchaseStatus.purchased ||
      purchase.status == PurchaseStatus.restored
    );
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
    
    // Restore purchases to check existing subscriptions
    await restorePurchases();

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
      final purchaseParam = PurchaseParam(productDetails: productDetails);
      
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
      _updateState(PurchaseState.error);
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

  /// Restore previous purchases
  Future<void> restorePurchases() async {
    try {
      await _inAppPurchase.restorePurchases();
    } catch (e) {
      _errorMessage = 'Error restoring purchases: $e';
      if (kDebugMode) print('[BillingService] $_errorMessage');
    }
  }

  /// Handle purchase updates
  void _onPurchaseUpdated(List<PurchaseDetails> purchaseDetailsList) {
    for (var purchaseDetails in purchaseDetailsList) {
      if (kDebugMode) {
        print('[BillingService] Purchase update: ${purchaseDetails.productID} - ${purchaseDetails.status}');
      }

      switch (purchaseDetails.status) {
        case PurchaseStatus.pending:
          _purchasePending = true;
          _updateState(PurchaseState.processing);
          break;
          
        case PurchaseStatus.purchased:
        case PurchaseStatus.restored:
          _purchasePending = false;
          _handleSuccessfulPurchase(purchaseDetails);
          break;
          
        case PurchaseStatus.error:
          _purchasePending = false;
          _handlePurchaseError(purchaseDetails);
          break;
          
        case PurchaseStatus.canceled:
          _purchasePending = false;
          _updateState(PurchaseState.cancelled);
          onPurchaseComplete?.call(false, null);
          break;
      }

      // Complete the purchase
      if (purchaseDetails.pendingCompletePurchase) {
        _inAppPurchase.completePurchase(purchaseDetails);
      }
    }
  }

  /// Handle purchase errors with user-friendly states
  void _handlePurchaseError(PurchaseDetails purchaseDetails) {
    // Check for specific error codes on Android
    if (Platform.isAndroid && purchaseDetails is GooglePlayPurchaseDetails) {
      final billingResponse = purchaseDetails.billingClientPurchase.purchaseState;
      
      // Check error message for "already owned" scenario
      final errorMessage = purchaseDetails.error?.message?.toLowerCase() ?? '';
      if (errorMessage.contains('already') || errorMessage.contains('owned')) {
        _updateState(PurchaseState.alreadyOwned);
        onPurchaseComplete?.call(false, null);
        return;
      }
    }
    
    // Check generic error message
    final errorMessage = purchaseDetails.error?.message?.toLowerCase() ?? '';
    if (errorMessage.contains('already') || 
        errorMessage.contains('owned') ||
        errorMessage.contains('item_already_owned')) {
      _updateState(PurchaseState.alreadyOwned);
      onPurchaseComplete?.call(false, null);
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
  void _handleSuccessfulPurchase(PurchaseDetails purchaseDetails) {
    // Add to purchases list
    _purchases.removeWhere((p) => p.productID == purchaseDetails.productID);
    _purchases.add(purchaseDetails);
    
    // Verify purchase on your server if needed
    // For now, we just trust Google Play's verification
    
    // Get purchase token for Android
    String? purchaseToken;
    if (Platform.isAndroid && purchaseDetails is GooglePlayPurchaseDetails) {
      purchaseToken = purchaseDetails.billingClientPurchase.purchaseToken;
    }
    
    // Update state to success
    _updateState(PurchaseState.success);
    
    // Notify listeners
    onSubscriptionActive?.call(purchaseDetails.productID, purchaseToken);
    onPurchaseComplete?.call(true, null);
  }

  void _onPurchaseStreamDone() {
    _subscription?.cancel();
  }

  void _onPurchaseStreamError(dynamic error) {
    if (kDebugMode) {
      print('[BillingService] Purchase stream error: $error');
    }
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
