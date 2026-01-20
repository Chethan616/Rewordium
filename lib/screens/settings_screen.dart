import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import 'package:android_intent_plus/android_intent.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'dart:math';
import '../providers/keyboard_provider.dart';
import '../services/news_subscription_service.dart';
import '../theme/app_theme.dart';
import '../services/rewordium_keyboard_service.dart';
import '../services/force_update_service.dart';
import '../services/billing_service.dart';
import '../theme/theme_provider.dart';
import '../utils/lottie_assets.dart';
import '../widgets/animated_card.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../widgets/broken_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/signup_screen.dart';
import '../screens/licenses_screen.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../providers/auth_provider.dart';
import '../widgets/upgrade_dialog.dart';
import 'admin_panel.dart';

// In-memory state for thunder Easter egg (resets on app restart)
int _thunderTapCount = 0;
int _lastMessageIndex = -1;

// Funny messages for thunder button (exactly 9)
const List<String> _thunderMessages = [
  '⚡ You pressed it again. We\'re judging. Gently.',
  '⚡ Nothing happened. But something almost did.',
  '⚡ AI status: confident. Results: questionable.',
  '⚡ Somewhere, a semicolon just moved.',
  '⚡ The code is fine. Probably.',
  '⚡ This feature exists purely because we could.',
  '⚡ If this works, don\'t touch anything.',
  '⚡ Achievement unlocked: Unnecessary Interaction.',
  '⚡ ok ur doomed.',
];

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // Admin access state
  int _adminTapCount = 0;
  DateTime? _lastTapTime;

  // Random generator for thunder messages
  final Random _random = Random();

  // News subscription state
  bool _isNewsSubscribed = false;
  bool _isLoadingNewsSubscription = true;

  @override
  void initState() {
    super.initState();
    _loadNewsSubscriptionStatus();
  }

  Future<void> _loadNewsSubscriptionStatus() async {
    try {
      final isSubscribed =
          await NewsSubscriptionService.isUserSubscribedToNews();
      if (mounted) {
        setState(() {
          _isNewsSubscribed = isSubscribed;
          _isLoadingNewsSubscription = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoadingNewsSubscription = false;
        });
      }
    }
  }

  Future<void> _toggleNewsSubscription(bool value) async {
    setState(() {
      _isLoadingNewsSubscription = true;
    });

    final success = await NewsSubscriptionService.toggleNewsSubscription(value);

    if (mounted) {
      if (success) {
        setState(() {
          _isNewsSubscribed = value;
          _isLoadingNewsSubscription = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(value
                ? 'Subscribed to news updates!'
                : 'Unsubscribed from news updates'),
            duration: const Duration(seconds: 2),
            behavior: SnackBarBehavior.floating,
          ),
        );
      } else {
        setState(() {
          _isLoadingNewsSubscription = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Failed to update subscription. Please try again.'),
            duration: Duration(seconds: 2),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  /// Thunder button Easter egg handler
  void _onThunderTap() {
    _thunderTapCount++;

    if (_thunderTapCount >= 10) {
      // Activate Broken Code Mode on 10th tap - navigate to full-screen
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (context) => const BrokenScreen(),
        ),
      );
    } else {
      // Show random funny message (taps 1-9)
      // Try to avoid repeating the last message
      int newIndex;
      do {
        newIndex = _random.nextInt(_thunderMessages.length);
      } while (newIndex == _lastMessageIndex && _thunderMessages.length > 1);

      _lastMessageIndex = newIndex;

      ScaffoldMessenger.of(context).clearSnackBars();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _thunderMessages[newIndex],
            style: const TextStyle(fontSize: 14),
          ),
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
          backgroundColor: Colors.grey[850],
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    }
  }

  void _checkAdminAccess() {
    final BuildContext context = this.context;
    final now = DateTime.now();

    // Reset counter if more than 2 seconds have passed since last tap
    if (_lastTapTime != null &&
        now.difference(_lastTapTime!) > const Duration(seconds: 2)) {
      _adminTapCount = 0;
    }

    _lastTapTime = now;
    _adminTapCount++;

    // Show remaining taps needed
    final remainingTaps = 5 - _adminTapCount;
    if (remainingTaps > 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Tap $remainingTaps more times to access admin panel'),
          duration: const Duration(seconds: 1),
        ),
      );
    }

    if (_adminTapCount >= 5) {
      _adminTapCount = 0;

      // Navigate to admin panel
      Navigator.push(
        context,
        MaterialPageRoute(builder: (context) => const AdminPanel()),
      );
    }
  }

  void _navigateToLogin() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const LoginScreen()),
    );
  }

  void _signOut(BuildContext context) async {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Confirm Sign Out'),
        content: const Text('Are you sure you want to sign out?'),
        actions: [
          CupertinoDialogAction(
            child: const Text('Cancel'),
            onPressed: () => Navigator.of(context).pop(),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            child: const Text('Sign Out'),
            onPressed: () async {
              Navigator.of(context).pop();
              final authProvider =
                  Provider.of<AuthProvider>(context, listen: false);
              await authProvider.signOut();

              if (context.mounted) {
                // Navigate to login screen and clear all routes
                Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (context) => const LoginScreen()),
                  (route) => false,
                );
              }
            },
          ),
        ],
      ),
    );
  }

  /// Restore previous purchases
  Future<void> _restorePurchases(BuildContext context) async {
    // Show loading dialog with message
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        content: Row(
          children: [
            const CircularProgressIndicator(),
            const SizedBox(width: 20),
            Text('Restoring purchases...', style: AppTheme.bodyMedium),
          ],
        ),
      ),
    );

    try {
      final billingService =
          Provider.of<BillingService>(context, listen: false);
      final authProvider = Provider.of<AuthProvider>(context, listen: false);

      // Call restore purchases and wait for result
      final success = await billingService.restorePurchases();

      // Give a moment for the auth provider to update if purchase was restored
      if (success) {
        await authProvider.refreshSubscriptionStatus();
      }

      if (context.mounted) {
        Navigator.of(context).pop(); // Close loading dialog

        if (success &&
            (billingService.purchaseState == PurchaseState.success ||
                billingService.purchaseState == PurchaseState.alreadyOwned ||
                billingService.hasActiveSubscription)) {
          // Purchase was restored successfully
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                        'Pro subscription restored successfully! Enjoy unlimited access.'),
                  ),
                ],
              ),
              backgroundColor: Colors.green.shade700,
              duration: const Duration(seconds: 4),
              behavior: SnackBarBehavior.floating,
            ),
          );
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
                        'No previous purchases found. If you believe this is an error, please contact support.'),
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
        Navigator.of(context).pop(); // Close loading

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

  /// Opens the native ReBoard keyboard settings activity
  Future<void> _openReboardSettings() async {
    final intent = AndroidIntent(
      action: 'android.intent.action.VIEW',
      data: 'ui://ReBoard/settings/home',
    );
    await intent.launch();
  }

  void _showIOSStyleKeyboardActivationDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          title: const Text(
            'Reactivate Keyboard',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            textAlign: TextAlign.center,
          ),
          content: const Text(
            'To apply your changes, please reactivate your keyboard.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 15),
          ),
          actionsAlignment: MainAxisAlignment.spaceEvenly,
          actionsPadding:
              const EdgeInsets.only(bottom: 12, left: 12, right: 12),
          actions: [
            TextButton(
              style: TextButton.styleFrom(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                padding:
                    const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
                backgroundColor: Colors.grey[200],
              ),
              onPressed: () => Navigator.of(context).pop(),
              child: const Text(
                'Cancel',
                style:
                    TextStyle(color: Colors.black, fontWeight: FontWeight.w500),
              ),
            ),
            TextButton(
              style: TextButton.styleFrom(
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                padding:
                    const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
                backgroundColor: Colors.blue,
              ),
              onPressed: () {
                Navigator.of(context).pop();
                RewordiumKeyboardService.openKeyboardSettings();
              },
              child: const Text(
                'Reactivate',
                style:
                    TextStyle(color: Colors.white, fontWeight: FontWeight.w500),
              ),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;
    final isLoggedIn = authProvider.isLoggedIn;
    final isPro = authProvider.isPro;
    final userName = authProvider.userName ?? 'User';

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CustomAppBar(
              title: "Settings",
              showBackButton: false,
              actions: [
                IconButton(
                  icon: const Icon(CupertinoIcons.bolt),
                  onPressed: _onThunderTap,
                  color: AppTheme.primaryColor,
                ),
              ],
            ),

            // ============================================
            // ACCOUNT SECTION
            // ============================================
            _buildSectionHeader(
              icon: CupertinoIcons.person_circle,
              title: "Account",
              color: AppTheme.primaryColor,
            ),
            AnimatedCard(
              child: isLoggedIn
                  ? _buildUserProfile(context, userName, isPro, authProvider)
                  : _buildLoginPrompt(context),
            ),

            // ============================================
            // GENERAL SETTINGS SECTION
            // ============================================
            _buildSectionHeader(
              icon: CupertinoIcons.slider_horizontal_3,
              title: "General",
              color: Colors.blue,
            ),
            AnimatedCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  // Dark Mode Toggle
                  _buildSettingItem(
                    icon: isDarkMode
                        ? CupertinoIcons.moon_fill
                        : CupertinoIcons.sun_max_fill,
                    iconColor: isDarkMode ? Colors.indigo : Colors.orange,
                    title: "Dark Mode",
                    subtitle: isDarkMode
                        ? "Dark theme enabled"
                        : "Light theme enabled",
                    trailing: CupertinoSwitch(
                      value: isDarkMode,
                      onChanged: (value) => themeProvider.toggleTheme(),
                      activeTrackColor: AppTheme.primaryColor,
                    ),
                  ),
                  if (isLoggedIn) ...[
                    const Divider(height: 1, indent: 72),
                    // News & Updates Toggle
                    _buildSettingItem(
                      icon: CupertinoIcons.bell_fill,
                      iconColor: Colors.pink,
                      title: "News & Updates",
                      subtitle: "Product news and feature announcements",
                      trailing: _isLoadingNewsSubscription
                          ? const SizedBox(
                              width: 24,
                              height: 24,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : CupertinoSwitch(
                              value: _isNewsSubscribed,
                              onChanged: _toggleNewsSubscription,
                              activeTrackColor: AppTheme.primaryColor,
                            ),
                    ),
                  ],
                ],
              ),
            ),

            // ============================================
            // KEYBOARD & AI SECTION
            // ============================================
            _buildSectionHeader(
              icon: CupertinoIcons.sparkles,
              title: "Keyboard & AI",
              color: Colors.purple,
            ),
            AnimatedCard(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  // Keyboard Settings
                  _buildSettingItem(
                    icon: CupertinoIcons.keyboard,
                    iconColor: AppTheme.primaryColor,
                    title: "Rewordium AI Keyboard",
                    subtitle: "Customize appearance and behavior",
                    trailing: const Icon(CupertinoIcons.chevron_right,
                        color: Colors.grey, size: 18),
                    onTap: () => _openReboardSettings(),
                  ),
                  const Divider(height: 1, indent: 72),
                  // Advanced AI Settings
                  _buildSettingItem(
                    icon: Icons.psychology_rounded,
                    iconColor: Colors.deepPurple,
                    iconGradient: true,
                    title: "Advanced AI Settings",
                    subtitle: "Use your own LLM API key",
                    trailing: const Icon(CupertinoIcons.chevron_right,
                        color: Colors.grey, size: 18),
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) =>
                              const AdvancedAISettingsScreen(),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),

            // Personalize Keyboard prompt for non-logged-in users
            if (!isLoggedIn)
              AnimatedCard(
                child: Column(
                  children: [
                    SizedBox(
                      height: 80,
                      child: LottieAssets.getKeyboardAnimation(),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      "Create an account to personalize your keyboard",
                      textAlign: TextAlign.center,
                      style: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.textSecondaryColor,
                      ),
                    ),
                    const SizedBox(height: 12),
                    CustomButton(
                      text: "Create Account",
                      onPressed: _navigateToLogin,
                      type: ButtonType.primary,
                      width: 180,
                      height: 44,
                    ),
                  ],
                ),
              ),

            // ============================================
            // ABOUT SECTION
            // ============================================
            _buildSectionHeader(
              icon: CupertinoIcons.info_circle,
              title: "About",
              color: Colors.teal,
            ),
            _buildAppInfoCard(context),

            // ============================================
            // DANGER ZONE (only logged in)
            // ============================================
            if (isLoggedIn) ...[
              _buildSectionHeader(
                icon: CupertinoIcons.exclamationmark_triangle,
                title: "Danger Zone",
                color: Colors.red,
              ),
              AnimatedCard(
                padding: EdgeInsets.zero,
                child: _buildSettingItem(
                  icon: CupertinoIcons.trash,
                  iconColor: Colors.red,
                  title: "Delete Account",
                  subtitle: "Permanently delete your account and data",
                  trailing: const Icon(CupertinoIcons.chevron_right,
                      color: Colors.red, size: 18),
                  onTap: () => _confirmDeleteAccount(context),
                  isDanger: true,
                ),
              ),
            ],

            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  /// Build a section header with icon
  Widget _buildSectionHeader({
    required IconData icon,
    required String title,
    required Color color,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Row(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: color.withOpacity(0.15),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(icon, size: 16, color: color),
          ),
          const SizedBox(width: 10),
          Text(
            title,
            style: AppTheme.bodyLarge.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
          ),
        ],
      ),
    );
  }

  /// Build a setting item row
  Widget _buildSettingItem({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String subtitle,
    required Widget trailing,
    VoidCallback? onTap,
    bool iconGradient = false,
    bool isDanger = false,
  }) {
    final content = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              gradient: iconGradient
                  ? LinearGradient(
                      colors: [iconColor, iconColor.withOpacity(0.7)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    )
                  : null,
              color: iconGradient ? null : iconColor.withOpacity(0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(
              icon,
              color: iconGradient ? Colors.white : iconColor,
              size: 20,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTheme.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: isDanger ? Colors.red : null,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: AppTheme.bodySmall.copyWith(
                    color: isDanger
                        ? Colors.red.withOpacity(0.7)
                        : AppTheme.textSecondaryColor,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          trailing,
        ],
      ),
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: content,
      );
    }
    return content;
  }

  /// Build app info card with version and credits
  Widget _buildAppInfoCard(BuildContext context) {
    return FutureBuilder<PackageInfo>(
      future: PackageInfo.fromPlatform(),
      builder: (context, snapshot) {
        String appVersion = '1.0.0';
        String buildNumber = '1';
        if (snapshot.hasData) {
          appVersion = snapshot.data!.version;
          buildNumber = snapshot.data!.buildNumber;
        }

        return AnimatedCard(
          padding: EdgeInsets.zero,
          child: Column(
            children: [
              // App Version
              GestureDetector(
                onTap: _checkAdminAccess,
                child: _buildSettingItem(
                  icon: CupertinoIcons.app_badge,
                  iconColor: Colors.blue,
                  title: "App Version",
                  subtitle: "v$appVersion (Build $buildNumber)",
                  trailing: const SizedBox.shrink(),
                ),
              ),
              const Divider(height: 1, indent: 72),
              // Check for Updates
              _buildSettingItem(
                icon: CupertinoIcons.arrow_down_circle,
                iconColor: Colors.green,
                title: "Check for Updates",
                subtitle: "Download the latest version",
                trailing: const Icon(CupertinoIcons.chevron_right,
                    color: Colors.grey, size: 18),
                onTap: () => ForceUpdateService.manualUpdateCheck(context),
              ),
              const Divider(height: 1, indent: 72),
              // Credits & Licenses
              _buildSettingItem(
                icon: CupertinoIcons.heart_fill,
                iconColor: Colors.pink,
                title: "Credits & Licenses",
                subtitle: "Open source attributions",
                trailing: const Icon(CupertinoIcons.chevron_right,
                    color: Colors.grey, size: 18),
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (context) => const LicensesScreen()),
                  );
                },
              ),
              // FlorisBoard attribution
              Container(
                margin: const EdgeInsets.all(16),
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: AppTheme.textSecondaryColor.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    Icon(CupertinoIcons.keyboard,
                        color: Colors.green.shade600, size: 16),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        "Based on FlorisBoard • Apache License 2.0",
                        style: AppTheme.bodySmall.copyWith(
                          fontSize: 11,
                          color: AppTheme.textSecondaryColor,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildUserProfile(BuildContext context, String name, bool isPro,
      AuthProvider authProvider) {
    final planType = authProvider.planType;

    return Column(
      children: [
        Row(
          children: [
            Container(
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                color: AppTheme.primaryColor.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                CupertinoIcons.person_fill,
                color: AppTheme.primaryColor,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    name,
                    style: AppTheme.bodyLarge.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      if (isPro)
                        Icon(
                            planType == 'onetime'
                                ? CupertinoIcons.rocket_fill
                                : CupertinoIcons.star_fill,
                            color: Colors.amber,
                            size: 16),
                      if (isPro) const SizedBox(width: 4),
                      Text(
                        isPro
                            ? (planType == 'onetime'
                                ? "Lifetime Pro User"
                                : "Pro User")
                            : "Standard User",
                        style: AppTheme.bodySmall
                            .copyWith(color: isPro ? Colors.amber : null),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        const Divider(height: 32),
        if (isPro) ...[
          if (planType != 'onetime')
            CustomButton(
              text: "Manage Subscription",
              onPressed: () {
                // Open Google Play subscription management
                launchUrl(
                  Uri.parse(
                      'https://play.google.com/store/account/subscriptions'),
                  mode: LaunchMode.externalApplication,
                );
              },
              type: ButtonType.secondary,
              icon: CupertinoIcons.settings,
            ),
          const SizedBox(height: 12),
          CustomButton(
            text: "Restore Purchases",
            onPressed: () => _restorePurchases(context),
            type: ButtonType.secondary,
            icon: CupertinoIcons.arrow_counterclockwise,
          ),
        ] else ...[
          // Show current credits for free users
          Container(
            padding: const EdgeInsets.all(12),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: AppTheme.primaryColor.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Icon(
                  CupertinoIcons.star,
                  color: AppTheme.primaryColor,
                  size: 18,
                ),
                const SizedBox(width: 8),
                Text(
                  'Credits: ${authProvider.credits ?? 0}',
                  style: AppTheme.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppTheme.primaryColor,
                  ),
                ),
              ],
            ),
          ),
          CustomButton(
            text: "Upgrade to Pro",
            onPressed: () {
              showUpgradeDialog(context); // Use Google Play Billing
            },
            type: ButtonType.primary,
            width: double.infinity,
          ),
          const SizedBox(height: 12),
          // Restore purchases button for free users who previously purchased
          CustomButton(
            text: "Restore Purchases",
            onPressed: () => _restorePurchases(context),
            type: ButtonType.secondary,
            icon: CupertinoIcons.arrow_counterclockwise,
          ),
        ],
        if ((isPro && planType != 'onetime') || !isPro)
          const SizedBox(height: 16),
        CustomButton(
          text: "Sign Out",
          onPressed: () => _signOut(context),
          type: ButtonType.secondary,
          icon: CupertinoIcons.arrow_right_square,
        ),
      ],
    );
  }

  Widget _buildLoginPrompt(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 50,
          height: 50,
          decoration: BoxDecoration(
            color: AppTheme.primaryColor.withOpacity(0.1),
            shape: BoxShape.circle,
          ),
          child: Icon(
            CupertinoIcons.person,
            color: AppTheme.primaryColor,
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "Create an account",
                style: AppTheme.bodyLarge.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                "Sync your settings across devices",
                style: AppTheme.bodySmall,
              ),
            ],
          ),
        ),
        CustomButton(
          text: "Login",
          onPressed: _navigateToLogin,
          width: 80,
          height: 40,
          type: ButtonType.primary,
        ),
      ],
    );
  }

  IconData _getPersonaIcon(String personaName) {
    switch (personaName.toLowerCase()) {
      case 'happy':
        return CupertinoIcons.smiley;
      case 'sad':
        return CupertinoIcons.exclamationmark_circle;
      case 'humor':
        return CupertinoIcons.hand_thumbsup;
      case 'formal':
        return CupertinoIcons.briefcase;
      case 'casual':
        return CupertinoIcons.chat_bubble;
      default:
        return CupertinoIcons.person;
    }
  }

  void _confirmDeleteAccount(BuildContext context) {
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Delete Account'),
        content: const Text(
            'Are you sure you want to delete your account? This action cannot be undone.'),
        actions: [
          CupertinoDialogAction(
            child: const Text('Cancel'),
            onPressed: () => Navigator.of(context).pop(),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            child: const Text('Delete'),
            onPressed: () async {
              Navigator.of(context).pop();

              showDialog(
                context: context,
                barrierDismissible: false,
                builder: (BuildContext loadingContext) {
                  Future.microtask(() async {
                    final authProvider =
                        Provider.of<AuthProvider>(context, listen: false);
                    bool success = false;
                    try {
                      success = await authProvider.deleteAccount();
                    } catch (e) {
                      // Handle error
                    }

                    if (!loadingContext.mounted) return;
                    Navigator.of(loadingContext).pop();

                    if (success && context.mounted) {
                      Navigator.of(context).pushAndRemoveUntil(
                        MaterialPageRoute(
                            builder: (context) => const SignupScreen()),
                        (route) => false,
                      );
                    } else if (context.mounted) {
                      final error =
                          authProvider.error ?? 'Failed to delete account.';
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(error)),
                      );
                    }
                  });

                  return const Center(child: CircularProgressIndicator());
                },
              );
            },
          ),
        ],
      ),
    );
  }

  // Admin access state and method are already defined above
}
