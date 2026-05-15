import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:in_app_purchase_android/in_app_purchase_android.dart';
import 'package:in_app_purchase_android/billing_client_wrappers.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:m3e_collection/m3e_collection.dart';
import '../services/billing_service.dart';
import '../providers/auth_provider.dart';

/// Full-screen Pro upgrade experience with proper state handling
class ProUpgradeScreen extends StatefulWidget {
  const ProUpgradeScreen({Key? key}) : super(key: key);

  @override
  State<ProUpgradeScreen> createState() => _ProUpgradeScreenState();
}

class _ProUpgradeScreenState extends State<ProUpgradeScreen> {
  String? _selectedPlan; // 'monthly' or 'yearly'
  static const String _specialOfferId = 'special-discount';

  @override
  void initState() {
    super.initState();
    // Reset billing state when screen opens
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<BillingService>().resetState();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer2<AuthProvider, BillingService>(
      builder: (context, authProvider, billingService, child) {
        final state = billingService.purchaseState;

        // Show result screens for terminal states
        if (state == PurchaseState.success) {
          return _buildSuccessScreen(context, billingService);
        }
        if (state == PurchaseState.alreadyOwned) {
          return _buildAlreadyOwnedScreen(context, billingService);
        }
        if (state == PurchaseState.error ||
            state == PurchaseState.billingUnavailable) {
          return _buildErrorScreen(context, billingService);
        }
        if (state == PurchaseState.processing) {
          return _buildProcessingScreen(context, billingService);
        }

        // Default: show plan selection
        return _buildPlanSelectionScreen(context, authProvider, billingService);
      },
    );
  }

  /// Main plan selection screen
  Widget _buildPlanSelectionScreen(
    BuildContext context,
    AuthProvider authProvider,
    BillingService billingService,
  ) {
    final monthlyProduct = billingService.monthlyProduct;
    final yearlyProduct = billingService.yearlyProduct;
    final isLoading = billingService.loading;
    final monthlyPricing =
      monthlyProduct != null ? _resolvePlanPricing(monthlyProduct) : null;
    _PlanPricing? yearlyPricing;
    if (yearlyProduct != null) {
      yearlyPricing = _resolvePlanPricing(yearlyProduct);
      // If no discount badge came from the offer itself, compute one
      // by comparing monthly×12 vs yearly price.
      if (yearlyPricing.discountBadge == null && monthlyProduct != null) {
        yearlyPricing = _enrichYearlyWithMonthlySavings(
          yearlyPricing,
          yearlyProduct,
          monthlyProduct,
        );
      }
    }

    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon:
              Icon(Icons.close, color: Theme.of(context).colorScheme.onSurface),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Text(
          'Upgrade to Pro',
          style: TextStyle(color: Theme.of(context).colorScheme.onSurface),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: isLoading
            ? Center(child: LoadingIndicatorM3E())
            : SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Hero section
                    _buildHeroSection(),
                    const SizedBox(height: 32),

                    // Features list
                    _buildFeaturesSection(),
                    const SizedBox(height: 32),

                    // Plan selection
                    if (!billingService.isAvailable)
                      _buildUnavailableCard()
                    else if (billingService.products.isEmpty)
                      _buildLoadingPlansCard()
                    else ...[
                      Text(
                        'Choose Your Plan',
                        style: Theme.of(context).textTheme.titleLarge!,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 16),

                      // Monthly Plan
                        if (monthlyProduct != null && monthlyPricing != null)
                        _buildPlanCard(
                          title: 'Monthly',
                          price: '₹40.50',
                          originalPrice: '₹270.00',
                          discountBadge: 'Save 85%',
                          period: 'per month',
                          description: 'Flexibility to cancel anytime',
                          isSelected: _selectedPlan == 'monthly',
                          onTap: () =>
                              setState(() => _selectedPlan = 'monthly'),
                        ),

                      const SizedBox(height: 12),

                      // Yearly Plan
                      if (yearlyProduct != null && yearlyPricing != null)
                        _buildPlanCard(
                          title: 'Yearly',
                          price: yearlyPricing.price,
                          originalPrice: yearlyPricing.originalPrice,
                          discountBadge: yearlyPricing.discountBadge,
                          period: 'per year',
                          description: yearlyPricing.discountBadge != null
                              ? 'Best value'
                              : 'Best value - Save more',
                          badge: 'BEST VALUE',
                          isSelected: _selectedPlan == 'yearly',
                          onTap: () => setState(() => _selectedPlan = 'yearly'),
                        ),

                      const SizedBox(height: 24),

                      // Subscribe button
                      _buildSubscribeButton(billingService),
                    ],

                    const SizedBox(height: 24),

                    // Terms
                    _buildTermsText(),

                    const SizedBox(height: 16),

                    // Restore purchases
                    TextButton.icon(
                      onPressed: () =>
                          _handleRestorePurchases(context, billingService),
                      icon: Icon(Icons.restore,
                          color: Theme.of(context).colorScheme.primary,
                          size: 18),
                      label: Text(
                        'Restore Purchases',
                        style: TextStyle(
                            color: Theme.of(context).colorScheme.primary),
                      ),
                    ),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _buildHeroSection() {
    return Column(
      children: [
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [Colors.amber.shade400, Colors.orange.shade600],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.amber.withValues(alpha: 0.3),
                blurRadius: 20,
                spreadRadius: 5,
              ),
            ],
          ),
          child: const Icon(
            Icons.star_rounded,
            color: Colors.white,
            size: 48,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          'Unlock Your Full Potential',
          style: Theme.of(context).textTheme.headlineSmall!,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'Get unlimited access to all Pro features',
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildFeaturesSection() {
    final features = [
      (
        'Unlimited AI rewrites (default model)',
        Icons.all_inclusive,
        'No daily limits'
      ),
      (
        'Priority support',
        Icons.support_agent,
        'Get faster help from our support team'
      ),
      (
        'Access to experimental features',
        Icons.new_releases,
        'Selected features may be available for testing'
      ),
    ];

    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: isDark ? Colors.grey.shade700 : Colors.grey.shade300),
      ),
      child: Column(
        children: features.map((feature) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: isDark
                        ? Colors.green.shade900.withValues(alpha: 0.3)
                        : Colors.green.shade50,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    feature.$2,
                    color:
                        isDark ? Colors.green.shade400 : Colors.green.shade600,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        feature.$1,
                        style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                      Text(
                        feature.$3,
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant,
                            ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.check_circle,
                  color: Theme.of(context).brightness == Brightness.dark
                      ? Colors.green.shade400
                      : Colors.green.shade600,
                  size: 22,
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildPlanCard({
    required String title,
    required String price,
    required String period,
    required String description,
    String? originalPrice,
    String? discountBadge,
    String? badge,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isSelected
              ? (isDark
                  ? Colors.green.shade900.withValues(alpha: 0.3)
                  : Colors.green.shade50)
              : Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected
                ? Colors.green
                : (isDark ? Colors.grey.shade700 : Colors.grey.shade300),
            width: isSelected ? 2 : 1,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: Colors.green.withValues(alpha: isDark ? 0.3 : 0.2),
                    blurRadius: 10,
                    spreadRadius: 2,
                  ),
                ]
              : null,
        ),
        child: Row(
          children: [
            Radio<bool>(
              value: true,
              groupValue: isSelected,
              onChanged: (_) => onTap(),
              activeColor: Colors.green,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          title,
                          style:
                              Theme.of(context).textTheme.bodyLarge!.copyWith(
                                    fontWeight: FontWeight.bold,
                                  ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (badge != null) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              colors: [Colors.orange, Colors.deepOrange],
                            ),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            badge,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 140),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  if (originalPrice != null && originalPrice != price)
                    Text(
                      originalPrice,
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: Theme.of(context)
                                .colorScheme
                                .onSurfaceVariant,
                            decoration: TextDecoration.lineThrough,
                            decorationColor: Theme.of(context)
                                .colorScheme
                                .onSurfaceVariant,
                          ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  if (discountBadge != null) ...[
                    const SizedBox(height: 2),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Theme.of(context).brightness == Brightness.dark
                            ? Colors.green.shade900.withValues(alpha: 0.5)
                            : Colors.green.shade50,
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: Theme.of(context).brightness == Brightness.dark
                              ? Colors.green.shade700
                              : Colors.green.shade300,
                          width: 0.5,
                        ),
                      ),
                      child: Text(
                        discountBadge,
                        style: TextStyle(
                          color: Theme.of(context).brightness == Brightness.dark
                              ? Colors.green.shade300
                              : Colors.green.shade700,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(height: 2),
                  ],
                  Text(
                    price,
                    style: Theme.of(context).textTheme.titleMedium!.copyWith(
                          color: Theme.of(context).brightness == Brightness.dark
                              ? Colors.green.shade400
                              : Colors.green.shade700,
                          fontWeight: FontWeight.bold,
                        ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    period,
                    style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSubscribeButton(BillingService billingService) {
    return ElevatedButton(
      onPressed:
          _selectedPlan == null ? null : () => _handlePurchase(billingService),
      style: ElevatedButton.styleFrom(
        backgroundColor: Colors.green,
        foregroundColor: Colors.white,
        disabledBackgroundColor: Colors.grey.shade300,
        padding: const EdgeInsets.symmetric(vertical: 16),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        elevation: 2,
      ),
      child: Text(
        _selectedPlan == null ? 'Select a Plan' : 'Subscribe Now',
        style: const TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildTermsText() {
    final storeName = Platform.isIOS ? 'App Store' : 'Google Play';
    return Text(
      'Subscriptions auto-renew until canceled. You can manage or cancel your subscription anytime in $storeName settings. By subscribing, you agree to our Terms of Service.',
      style: TextStyle(
        fontSize: 11,
        color: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
      textAlign: TextAlign.center,
    );
  }

  Widget _buildUnavailableCard() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark
            ? Colors.orange.shade900.withValues(alpha: 0.3)
            : Colors.orange.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
            color: isDark ? Colors.orange.shade700 : Colors.orange.shade200),
      ),
      child: Column(
        children: [
          Icon(Icons.cloud_off,
              color: isDark ? Colors.orange.shade400 : Colors.orange.shade700,
              size: 48),
          const SizedBox(height: 12),
          Text(
            'Store Unavailable',
            style: Theme.of(context)
                .textTheme
                .bodyLarge!
                .copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Please check your internet connection and try again.',
            style: Theme.of(context).textTheme.bodyMedium!,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingPlansCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
            color: Theme.of(context).brightness == Brightness.dark
                ? Colors.grey.shade700
                : Colors.grey.shade300),
      ),
      child: Column(
        children: [
          LoadingIndicatorM3E(),
          const SizedBox(height: 16),
          Text(
            'Loading subscription plans...',
            style: Theme.of(context).textTheme.bodyMedium!,
          ),
        ],
      ),
    );
  }

  _PlanPricing _resolvePlanPricing(ProductDetails productDetails) {
    if (productDetails is! GooglePlayProductDetails) {
      return _PlanPricing(price: productDetails.price);
    }

    final offers = productDetails.productDetails.subscriptionOfferDetails;
    if (offers == null || offers.isEmpty) {
      return _PlanPricing(price: productDetails.price);
    }

    SubscriptionOfferDetailsWrapper? discountedOffer;
    for (final offer in offers) {
      if (offer.offerId == _specialOfferId) {
        discountedOffer = offer;
        break;
      }
    }

    if (discountedOffer == null) {
      return _PlanPricing(price: productDetails.price);
    }

    SubscriptionOfferDetailsWrapper? basePlanOffer;
    for (final offer in offers) {
      if (offer.offerId == null &&
          offer.basePlanId == discountedOffer.basePlanId) {
        basePlanOffer = offer;
        break;
      }
    }
    basePlanOffer ??= offers.firstWhere(
      (offer) => offer.offerId == null,
      orElse: () => discountedOffer!,
    );

    final discountedPrice = _extractOfferPrice(discountedOffer);
    final originalPrice = _extractOfferPrice(basePlanOffer);

    // Calculate discount percentage from micros
    String? discountBadge;
    final baseMicros = _extractOfferMicros(basePlanOffer);
    final discountMicros = _extractOfferMicros(discountedOffer);
    if (baseMicros != null && discountMicros != null && baseMicros > 0 && discountMicros < baseMicros) {
      final percentOff = ((baseMicros - discountMicros) / baseMicros * 100).round();
      if (percentOff > 0) {
        discountBadge = '$percentOff% OFF';
      }
    }

    return _PlanPricing(
      price: discountedPrice ?? productDetails.price,
      originalPrice: originalPrice,
      discountBadge: discountBadge,
    );
  }

  /// Compute yearly savings by comparing monthly×12 vs yearly price.
  /// Returns a new _PlanPricing with the discount badge and original price filled in.
  _PlanPricing _enrichYearlyWithMonthlySavings(
    _PlanPricing currentYearly,
    ProductDetails yearlyProduct,
    ProductDetails monthlyProduct,
  ) {
    final yearlyMicros = _getProductMicros(yearlyProduct);
    final monthlyMicros = _getProductMicros(monthlyProduct);

    if (yearlyMicros == null || monthlyMicros == null || monthlyMicros <= 0) {
      return currentYearly;
    }

    final monthlyEquivYearly = monthlyMicros * 12;
    if (yearlyMicros >= monthlyEquivYearly) {
      return currentYearly; // yearly isn't cheaper
    }

    final percentOff =
        ((monthlyEquivYearly - yearlyMicros) / monthlyEquivYearly * 100)
            .round();
    if (percentOff <= 0) return currentYearly;

    // Format the monthly×12 price as the struck-out original
    final currencyCode = _getProductCurrency(monthlyProduct) ?? '';
    final monthlyEquivFormatted =
        '${currencyCode.isNotEmpty ? '$currencyCode ' : ''}${(monthlyEquivYearly / 1000000).toStringAsFixed(2)}';

    return _PlanPricing(
      price: currentYearly.price,
      originalPrice: currentYearly.originalPrice ?? monthlyEquivFormatted,
      discountBadge: 'Save $percentOff%',
    );
  }

  /// Get the recurring price in micros from a ProductDetails.
  int? _getProductMicros(ProductDetails product) {
    if (product is GooglePlayProductDetails) {
      final offers = product.productDetails.subscriptionOfferDetails;
      if (offers != null && offers.isNotEmpty) {
        // Use the base plan offer (no offerId) or fallback to first
        final baseOffer = offers.firstWhere(
          (o) => o.offerId == null,
          orElse: () => offers.first,
        );
        return _extractOfferMicros(baseOffer);
      }
    }
    return null;
  }

  /// Get the currency code from a ProductDetails.
  String? _getProductCurrency(ProductDetails product) {
    if (product is GooglePlayProductDetails) {
      final offers = product.productDetails.subscriptionOfferDetails;
      if (offers != null && offers.isNotEmpty) {
        final baseOffer = offers.firstWhere(
          (o) => o.offerId == null,
          orElse: () => offers.first,
        );
        if (baseOffer.pricingPhases.isNotEmpty) {
          return baseOffer.pricingPhases.last.priceCurrencyCode;
        }
      }
    }
    return null;
  }

  /// Extract the recurring price in micros from an offer's pricing phases.
  int? _extractOfferMicros(SubscriptionOfferDetailsWrapper offer) {
    if (offer.pricingPhases.isEmpty) return null;
    for (final phase in offer.pricingPhases.reversed) {
      if (phase.priceAmountMicros > 0) return phase.priceAmountMicros;
    }
    return offer.pricingPhases.last.priceAmountMicros;
  }

  String? _extractOfferPrice(SubscriptionOfferDetailsWrapper offer) {
    if (offer.pricingPhases.isEmpty) {
      return null;
    }

    PricingPhaseWrapper? selectedPhase;
    for (final phase in offer.pricingPhases.reversed) {
      if (phase.priceAmountMicros > 0) {
        selectedPhase = phase;
        break;
      }
    }

    selectedPhase ??= offer.pricingPhases.last;
    if (selectedPhase.formattedPrice.isNotEmpty) {
      return selectedPhase.formattedPrice;
    }

    return _formatPrice(
      selectedPhase.priceAmountMicros,
      selectedPhase.priceCurrencyCode,
    );
  }

  String _formatPrice(int amountMicros, String currencyCode) {
    final amount = amountMicros / 1000000;
    return '${currencyCode.toUpperCase()} ${amount.toStringAsFixed(2)}';
  }


  /// Processing screen - blocks navigation
  Widget _buildProcessingScreen(
      BuildContext context, BillingService billingService) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          _showExitWarningDialog(context);
        }
      },
      child: Scaffold(
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Animated loading indicator
                  Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: SizedBox(
                        width: 50,
                        height: 50,
                        child: LoadingIndicatorM3E(
                          color: Colors.green,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  Text(
                    billingService.stateTitle,
                    style: Theme.of(context).textTheme.headlineSmall!,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    billingService.stateMessage,
                    style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 32),
                  Icon(
                    Icons.lock_outline,
                    color: Colors.grey.shade400,
                    size: 24,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    Platform.isIOS
                        ? 'Secure payment powered by App Store'
                        : 'Secure payment powered by Google Play',
                    style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Success screen
  Widget _buildSuccessScreen(
      BuildContext context, BillingService billingService) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Success animation
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: isDark
                      ? Colors.green.shade900.withValues(alpha: 0.3)
                      : Colors.green.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.check_circle,
                  color: isDark ? Colors.green.shade400 : Colors.green.shade600,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: Theme.of(context).textTheme.headlineMedium!,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 48),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    billingService.resetState();
                    Navigator.of(context).pop(true);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const Text(
                    'Continue',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Already owned screen
  Widget _buildAlreadyOwnedScreen(
      BuildContext context, BillingService billingService) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon:
              Icon(Icons.close, color: Theme.of(context).colorScheme.onSurface),
          onPressed: () {
            billingService.resetState();
            Navigator.of(context).pop();
          },
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: isDark
                      ? Colors.blue.shade900.withValues(alpha: 0.3)
                      : Colors.blue.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.verified,
                  color: isDark ? Colors.blue.shade400 : Colors.blue.shade600,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: Theme.of(context).textTheme.headlineMedium!,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 48),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    final url = Platform.isIOS
                        ? 'https://apps.apple.com/account/subscriptions'
                        : 'https://play.google.com/store/account/subscriptions';
                    launchUrl(
                      Uri.parse(url),
                      mode: LaunchMode.externalApplication,
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const Text(
                    'Manage Subscription',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () {
                  billingService.resetState();
                  Navigator.of(context).pop();
                },
                child: const Text('Continue to App'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Error screen
  Widget _buildErrorScreen(
      BuildContext context, BillingService billingService) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon:
              Icon(Icons.close, color: Theme.of(context).colorScheme.onSurface),
          onPressed: () {
            billingService.resetState();
            Navigator.of(context).pop();
          },
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  color: isDark
                      ? Colors.red.shade900.withValues(alpha: 0.3)
                      : Colors.red.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.error_outline,
                  color: isDark ? Colors.red.shade400 : Colors.red.shade400,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: Theme.of(context).textTheme.headlineMedium!,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 48),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    billingService.resetState();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.primary,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const Text(
                    'Try Again',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () {
                  billingService.resetState();
                  Navigator.of(context).pop();
                },
                child: const Text('Cancel'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showExitWarningDialog(BuildContext context) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('Payment in Progress'),
        content: const Text(
          'Your payment is still being processed. Leaving now may cancel your purchase.\n\nAre you sure you want to leave?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Stay'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop(); // Close dialog
              context.read<BillingService>().resetState();
              Navigator.of(context).pop(); // Close screen
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Leave Anyway'),
          ),
        ],
      ),
    );
  }

  Future<void> _handlePurchase(BillingService billingService) async {
    if (_selectedPlan == 'monthly') {
      await billingService.purchaseMonthly();
    } else if (_selectedPlan == 'yearly') {
      await billingService.purchaseYearly();
    }
  }

  /// Handle restore purchases with loading indicator and feedback
  Future<void> _handleRestorePurchases(
      BuildContext context, BillingService billingService) async {
    // Show loading dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        content: Row(
          children: [
            LoadingIndicatorM3E(),
            const SizedBox(width: 20),
            Text('Restoring purchases...',
                style: Theme.of(context).textTheme.bodyMedium!),
          ],
        ),
      ),
    );

    try {
      final authProvider = context.read<AuthProvider>();

      // Call restore purchases and wait for result
      final success = await billingService.restorePurchases();

      // Refresh auth status to reflect any restored purchases
      if (success) {
        await authProvider.refreshSubscriptionStatus();
      }

      if (context.mounted) {
        Navigator.of(context).pop(); // Close loading dialog

        // Check if any purchase was restored
        if (success &&
            (billingService.purchaseState == PurchaseState.success ||
                billingService.purchaseState == PurchaseState.alreadyOwned ||
                billingService.hasActiveSubscription)) {
          // Purchase was restored successfully - show success message
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text('Pro subscription restored successfully!'),
                  ),
                ],
              ),
              backgroundColor: Colors.green.shade700,
              duration: const Duration(seconds: 3),
              behavior: SnackBarBehavior.floating,
            ),
          );

          // Close the upgrade screen after a brief delay since user is now Pro
          await Future.delayed(const Duration(seconds: 1));
          if (context.mounted) {
            Navigator.of(context).pop(true); // Return true to indicate success
          }
        } else {
          // No purchases found to restore
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.info_outline, color: Colors.white),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                      'No previous purchases found. If you believe this is an error, please contact support.',
                    ),
                  ),
                ],
              ),
              backgroundColor: Colors.orange.shade700,
              duration: const Duration(seconds: 4),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    } catch (e) {
      if (context.mounted) {
        Navigator.of(context).pop(); // Close loading dialog

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error_outline, color: Colors.white),
                const SizedBox(width: 12),
                Expanded(child: Text('Error restoring purchases: $e')),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 4),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }
}

class _PlanPricing {
  final String price;
  final String? originalPrice;
  final String? discountBadge;

  const _PlanPricing({required this.price, this.originalPrice, this.discountBadge});
}

/// Navigate to Pro upgrade screen
void showProUpgradeScreen(BuildContext context) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (context) => const ProUpgradeScreen(),
      fullscreenDialog: true,
    ),
  );
}
