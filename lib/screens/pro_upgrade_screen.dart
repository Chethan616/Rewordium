import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/billing_service.dart';
import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';

/// Full-screen Pro upgrade experience with proper state handling
class ProUpgradeScreen extends StatefulWidget {
  const ProUpgradeScreen({Key? key}) : super(key: key);

  @override
  State<ProUpgradeScreen> createState() => _ProUpgradeScreenState();
}

class _ProUpgradeScreenState extends State<ProUpgradeScreen> {
  String? _selectedPlan; // 'monthly' or 'yearly'

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
        if (state == PurchaseState.error || state == PurchaseState.billingUnavailable) {
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

    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: Icon(Icons.close, color: AppTheme.textPrimaryColor),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Text(
          'Upgrade to Pro',
          style: TextStyle(color: AppTheme.textPrimaryColor),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: isLoading
            ? const Center(child: CircularProgressIndicator())
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
                        style: AppTheme.headingSmall,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 16),
                      
                      // Monthly Plan
                      if (monthlyProduct != null)
                        _buildPlanCard(
                          title: 'Monthly',
                          price: monthlyProduct.price,
                          period: 'per month',
                          description: 'Flexibility to cancel anytime',
                          isSelected: _selectedPlan == 'monthly',
                          onTap: () => setState(() => _selectedPlan = 'monthly'),
                        ),
                      
                      const SizedBox(height: 12),
                      
                      // Yearly Plan
                      if (yearlyProduct != null)
                        _buildPlanCard(
                          title: 'Yearly',
                          price: yearlyProduct.price,
                          period: 'per year',
                          description: 'Best value - Save 40%',
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
                    TextButton(
                      onPressed: () => billingService.restorePurchases(),
                      child: Text(
                        'Restore Purchases',
                        style: TextStyle(color: AppTheme.primaryColor),
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
                color: Colors.amber.withOpacity(0.3),
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
          style: AppTheme.headingMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'Get unlimited access to all Pro features',
          style: AppTheme.bodyMedium.copyWith(
            color: AppTheme.textSecondaryColor,
          ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildFeaturesSection() {
    final features = [
        ('Unlimited AI rewrites (default model)', Icons.all_inclusive, 'No daily limits'),
        ('Priority support', Icons.support_agent, 'Get faster help from our support team'),
        ('Access to experimental features', Icons.new_releases, 'Selected features may be available for testing'),];


    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade300),
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
                    color: Colors.green.shade50,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(
                    feature.$2,
                    color: Colors.green.shade600,
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
                        style: AppTheme.bodyMedium.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        feature.$3,
                        style: AppTheme.bodySmall.copyWith(
                          color: AppTheme.textSecondaryColor,
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  Icons.check_circle,
                  color: Colors.green.shade600,
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
    String? badge,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isSelected ? Colors.green.shade50 : AppTheme.cardColor,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected ? Colors.green : Colors.grey.shade300,
            width: isSelected ? 2 : 1,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: Colors.green.withOpacity(0.2),
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
                      Text(
                        title,
                        style: AppTheme.bodyLarge.copyWith(
                          fontWeight: FontWeight.bold,
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
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.textSecondaryColor,
                    ),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  price,
                  style: AppTheme.headingSmall.copyWith(
                    color: Colors.green.shade700,
                  ),
                ),
                Text(
                  period,
                  style: AppTheme.bodySmall.copyWith(
                    color: AppTheme.textSecondaryColor,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSubscribeButton(BillingService billingService) {
    return ElevatedButton(
      onPressed: _selectedPlan == null
          ? null
          : () => _handlePurchase(billingService),
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
    return Text(
      'Subscriptions auto-renew until canceled. You can manage or cancel your subscription anytime in Google Play settings. By subscribing, you agree to our Terms of Service.',
      style: TextStyle(
        fontSize: 11,
        color: Colors.grey.shade600,
      ),
      textAlign: TextAlign.center,
    );
  }

  Widget _buildUnavailableCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.orange.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.orange.shade200),
      ),
      child: Column(
        children: [
          Icon(Icons.cloud_off, color: Colors.orange.shade700, size: 48),
          const SizedBox(height: 12),
          Text(
            'Store Unavailable',
            style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Please check your internet connection and try again.',
            style: AppTheme.bodyMedium,
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
        color: AppTheme.cardColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade300),
      ),
      child: Column(
        children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 16),
          Text(
            'Loading subscription plans...',
            style: AppTheme.bodyMedium,
          ),
        ],
      ),
    );
  }

  /// Processing screen - blocks navigation
  Widget _buildProcessingScreen(BuildContext context, BillingService billingService) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          _showExitWarningDialog(context);
        }
      },
      child: Scaffold(
        backgroundColor: AppTheme.backgroundColor,
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
                    child: const Center(
                      child: SizedBox(
                        width: 50,
                        height: 50,
                        child: CircularProgressIndicator(
                          strokeWidth: 4,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.green),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  Text(
                    billingService.stateTitle,
                    style: AppTheme.headingMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    billingService.stateMessage,
                    style: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.textSecondaryColor,
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
                    'Secure payment powered by Google Play',
                    style: AppTheme.bodySmall.copyWith(
                      color: Colors.grey.shade500,
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
  Widget _buildSuccessScreen(BuildContext context, BillingService billingService) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
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
                  color: Colors.green.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.check_circle,
                  color: Colors.green.shade600,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: AppTheme.headingLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: AppTheme.bodyMedium.copyWith(
                  color: AppTheme.textSecondaryColor,
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
  Widget _buildAlreadyOwnedScreen(BuildContext context, BillingService billingService) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: Icon(Icons.close, color: AppTheme.textPrimaryColor),
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
                  color: Colors.blue.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.verified,
                  color: Colors.blue.shade600,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: AppTheme.headingLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: AppTheme.bodyMedium.copyWith(
                  color: AppTheme.textSecondaryColor,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 48),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    launchUrl(
                      Uri.parse('https://play.google.com/store/account/subscriptions'),
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
  Widget _buildErrorScreen(BuildContext context, BillingService billingService) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: Icon(Icons.close, color: AppTheme.textPrimaryColor),
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
                  color: Colors.red.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.error_outline,
                  color: Colors.red.shade400,
                  size: 80,
                ),
              ),
              const SizedBox(height: 32),
              Text(
                billingService.stateTitle,
                style: AppTheme.headingLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                billingService.stateMessage,
                style: AppTheme.bodyMedium.copyWith(
                  color: AppTheme.textSecondaryColor,
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
                    backgroundColor: AppTheme.primaryColor,
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
