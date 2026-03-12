import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:expressive_refresh/expressive_refresh.dart';
import '../widgets/whats_new_sheet.dart';
import '../providers/keyboard_provider.dart';
import '../services/news_subscription_service.dart';
import '../services/rewordium_keyboard_service.dart';
import '../services/force_update_service.dart';
import '../services/in_app_update_service.dart';
import '../services/billing_service.dart';
import '../theme/theme_provider.dart';
import '../utils/lottie_assets.dart';
import '../widgets/animated_card.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../screens/version_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/signup_screen.dart';
import '../screens/licenses_screen.dart';
import '../screens/advanced_ai_settings_screen.dart';
import '../providers/auth_provider.dart';
import '../widgets/upgrade_dialog.dart';
import 'admin_panel.dart';



class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // Admin access state
  int _adminTapCount = 0;
  DateTime? _lastTapTime;



  // News subscription state
  bool _isNewsSubscribed = false;
  bool _isLoadingNewsSubscription = true;

  // Rewordium header toggle (pro users)
  bool _removeRewordiumHeader = false;

  @override
  void initState() {
    super.initState();
    _loadNewsSubscriptionStatus();
    _loadHeaderPref();
  }

  Future<void> _loadHeaderPref() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        _removeRewordiumHeader = prefs.getBool('remove_rewordium_header') ?? false;
      });
    }
  }

  Future<void> _toggleHeaderPref(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('remove_rewordium_header', value);
    if (mounted) {
      setState(() => _removeRewordiumHeader = value);
    }
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

  /// Thunder button handler — opens the version info screen
  void _onThunderTap() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const VersionScreen(),
      ),
    );
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
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => _SignOutSheet(parentContext: this.context),
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
            const LoadingIndicatorM3E(
              constraints: BoxConstraints(maxWidth: 24, maxHeight: 24),
            ),
            const SizedBox(width: 20),
            Text('Restoring purchases...', style: Theme.of(context).textTheme.bodyMedium!),
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
    if (defaultTargetPlatform != TargetPlatform.android) return;
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

    return ExpressiveRefreshIndicator(
      onRefresh: () async {
        if (isLoggedIn) {
          await Future.wait([
            authProvider.refreshUserData(),
            authProvider.refreshSubscriptionStatus(),
            _loadNewsSubscriptionStatus(),
          ]);
        }
      },
      color: Theme.of(context).colorScheme.primary,
      backgroundColor: Theme.of(context).colorScheme.surface,
      child: SingleChildScrollView(
      physics: const AlwaysScrollableScrollPhysics(
        parent: BouncingScrollPhysics(),
      ),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      "Settings",
                      style: Theme.of(context).textTheme.headlineMedium!.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(CupertinoIcons.bolt),
                    onPressed: _onThunderTap,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ],
              ),
            ),

            // ============================================
            // ACCOUNT SECTION
            // ============================================
            _buildSectionHeader(
              icon: CupertinoIcons.person_circle,
              title: "Account",
              color: Theme.of(context).colorScheme.primary,
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
                  // Theme Mode Selector
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 40,
                              height: 40,
                              decoration: BoxDecoration(
                                color: (themeProvider.themeMode == ThemeMode.system
                                    ? Theme.of(context).colorScheme.primary
                                    : isDarkMode ? Colors.indigo : Colors.orange).withOpacity(0.12),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Icon(
                                themeProvider.themeMode == ThemeMode.system
                                    ? Icons.brightness_auto
                                    : isDarkMode
                                        ? CupertinoIcons.moon_fill
                                        : CupertinoIcons.sun_max_fill,
                                color: themeProvider.themeMode == ThemeMode.system
                                    ? Theme.of(context).colorScheme.primary
                                    : isDarkMode ? Colors.indigo : Colors.orange,
                                size: 20,
                              ),
                            ),
                            const SizedBox(width: 16),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  "Appearance",
                                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  themeProvider.themeMode == ThemeMode.system
                                      ? "Following system theme"
                                      : isDarkMode
                                          ? "Dark theme enabled"
                                          : "Light theme enabled",
                                  style: Theme.of(context).textTheme.bodySmall!.copyWith(
                                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: SegmentedButton<ThemeMode>(
                            segments: const [
                              ButtonSegment(
                                value: ThemeMode.system,
                                label: Text('Auto'),
                                icon: Icon(Icons.brightness_auto, size: 18),
                              ),
                              ButtonSegment(
                                value: ThemeMode.light,
                                label: Text('Light'),
                                icon: Icon(CupertinoIcons.sun_max_fill, size: 18),
                              ),
                              ButtonSegment(
                                value: ThemeMode.dark,
                                label: Text('Dark'),
                                icon: Icon(CupertinoIcons.moon_fill, size: 18),
                              ),
                            ],
                            selected: {themeProvider.themeMode},
                            onSelectionChanged: (Set<ThemeMode> selected) {
                              themeProvider.setThemeMode(selected.first);
                            },
                            showSelectedIcon: false,
                            style: ButtonStyle(
                              visualDensity: VisualDensity.compact,
                              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                          ),
                        ),
                      ],
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
                              child: LoadingIndicatorM3E(
                                constraints: BoxConstraints(maxWidth: 24, maxHeight: 24),
                              ),
                            )
                          : Switch(
                              value: _isNewsSubscribed,
                              onChanged: _toggleNewsSubscription,
                            ),
                    ),
                  ],
                  if (isPro) ...[
                    const Divider(height: 1, indent: 72),
                    // Remove Rewordium Header Toggle (pro only)
                    _buildSettingItem(
                      icon: CupertinoIcons.doc_text,
                      iconColor: Colors.teal,
                      title: "Remove Rewordium Header",
                      subtitle: "Hide branding in exported PDFs",
                      trailing: Switch(
                        value: _removeRewordiumHeader,
                        onChanged: _toggleHeaderPref,
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
                    iconColor: Theme.of(context).colorScheme.primary,
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
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
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
            style: Theme.of(context).textTheme.bodyLarge!.copyWith(
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
              color: iconGradient ? iconColor : iconColor.withOpacity(0.12),
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
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    fontWeight: FontWeight.w600,
                    color: isDanger ? Colors.red : null,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    color: isDanger
                        ? Colors.red.withOpacity(0.7)
                        : Theme.of(context).colorScheme.onSurfaceVariant,
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
              // What's New
              _buildSettingItem(
                icon: CupertinoIcons.sparkles,
                iconColor: Colors.orange,
                title: "What's New",
                subtitle: "See the latest features",
                trailing: const Icon(CupertinoIcons.chevron_right,
                    color: Colors.grey, size: 18),
                onTap: () => WhatsNewSheet.show(context),
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
                onTap: () {
                  ForceUpdateService.manualUpdateCheck(context);
                  InAppUpdateService.checkForUpdate();
                },
              ),
              const Divider(height: 1, indent: 72),
              // Rate Us
              _buildSettingItem(
                icon: CupertinoIcons.star_fill,
                iconColor: Colors.amber,
                title: "Rate Us",
                subtitle: "Love Rewordium? Leave a review",
                trailing: const Icon(CupertinoIcons.chevron_right,
                    color: Colors.grey, size: 18),
                onTap: () {
                  const channel = MethodChannel('com.noxquill.rewordium/review');
                  channel.invokeMethod('showReview');
                },
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
                  color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.08),
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
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          fontSize: 11,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
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

    final photoUrl = authProvider.user?.photoURL;
    final email = authProvider.user?.email;

    return Column(
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 25,
              backgroundColor: Theme.of(context).colorScheme.primaryContainer,
              backgroundImage: photoUrl != null ? NetworkImage(photoUrl) : null,
              child: photoUrl == null
                  ? Icon(CupertinoIcons.person_fill, color: Theme.of(context).colorScheme.onPrimaryContainer)
                  : null,
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    name,
                    style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (email != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      email,
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
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
                        style: Theme.of(context).textTheme.bodySmall!
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
                // Open platform-specific subscription management
                launchUrl(
                  Uri.parse(
                      defaultTargetPlatform == TargetPlatform.iOS
                          ? 'https://apps.apple.com/account/subscriptions'
                          : 'https://play.google.com/store/account/subscriptions'),
                  mode: LaunchMode.externalApplication,
                );
              },
              type: ButtonType.secondary,
              icon: CupertinoIcons.settings,
              width: double.infinity,
            ),
          const SizedBox(height: 12),
          CustomButton(
            text: "Restore Purchases",
            onPressed: () => _restorePurchases(context),
            type: ButtonType.secondary,
            icon: CupertinoIcons.arrow_counterclockwise,
            width: double.infinity,
          ),
        ] else ...[
          // Show current credits for free users
          Container(
            padding: const EdgeInsets.all(12),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                Icon(
                  CupertinoIcons.star,
                  color: Theme.of(context).colorScheme.primary,
                  size: 18,
                ),
                const SizedBox(width: 8),
                Text(
                  'Credits: ${authProvider.credits ?? 0}',
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    fontWeight: FontWeight.w600,
                    color: Theme.of(context).colorScheme.primary,
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
            width: double.infinity,
          ),
        ],
        if ((isPro && planType != 'onetime') || !isPro)
          const SizedBox(height: 16),
        CustomButton(
          text: "Sign Out",
          onPressed: () => _signOut(context),
          type: ButtonType.secondary,
          icon: CupertinoIcons.arrow_right_square,
          width: double.infinity,
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
            color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
            shape: BoxShape.circle,
          ),
          child: Icon(
            CupertinoIcons.person,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                "Create an account",
                style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                "Sync your settings across devices",
                style: Theme.of(context).textTheme.bodySmall!,
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
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetCtx) => _DeleteAccountSheet(
        onDelete: () async {
          final authProvider =
              Provider.of<AuthProvider>(context, listen: false);
          bool success = false;
          try {
            success = await authProvider.deleteAccount();
          } catch (e) {
            // Handle error
          }
          return success;
        },
        onSuccess: () {
          if (context.mounted) {
            Navigator.of(context).pushAndRemoveUntil(
              MaterialPageRoute(
                  builder: (context) => const SignupScreen()),
              (route) => false,
            );
          }
        },
        onError: () {
          if (context.mounted) {
            final authProvider =
                Provider.of<AuthProvider>(context, listen: false);
            final error =
                authProvider.error ?? 'Failed to delete account.';
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(error)),
            );
          }
        },
      ),
    );
  }

  // Admin access state and method are already defined above
}

/// Bottom sheet widget for delete account confirmation with loading state.
class _DeleteAccountSheet extends StatefulWidget {
  final Future<bool> Function() onDelete;
  final VoidCallback onSuccess;
  final VoidCallback onError;

  const _DeleteAccountSheet({
    required this.onDelete,
    required this.onSuccess,
    required this.onError,
  });

  @override
  State<_DeleteAccountSheet> createState() => _DeleteAccountSheetState();
}

class _DeleteAccountSheetState extends State<_DeleteAccountSheet> {
  bool _isDeleting = false;

  Future<void> _handleDelete() async {
    setState(() => _isDeleting = true);
    final success = await widget.onDelete();
    if (!mounted) return;
    Navigator.of(context).pop();
    if (success) {
      widget.onSuccess();
    } else {
      widget.onError();
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Handle
          Container(
            width: 36,
            height: 5,
            decoration: BoxDecoration(
              color: cs.onSurfaceVariant.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(3),
            ),
          ),
          const SizedBox(height: 20),
          // Warning icon
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: cs.error.withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(
              CupertinoIcons.exclamationmark_triangle_fill,
              color: cs.error,
              size: 28,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'Delete Account',
            style: Theme.of(context).textTheme.titleLarge!.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Are you sure you want to delete your account? This action cannot be undone.',
            style: Theme.of(context).textTheme.bodyMedium!.copyWith(
              color: cs.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          // Delete button
          SizedBox(
            width: double.infinity,
            height: 48,
            child: FilledButton(
              onPressed: _isDeleting ? null : _handleDelete,
              style: FilledButton.styleFrom(
                backgroundColor: cs.error,
                foregroundColor: cs.onError,
                disabledBackgroundColor: cs.error.withValues(alpha: 0.5),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              child: _isDeleting
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: LoadingIndicatorM3E(
                        color: Colors.white,
                        constraints: BoxConstraints(maxWidth: 24, maxHeight: 24),
                      ),
                    )
                  : const Text('Delete Account',
                      style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 12),
          // Cancel button
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton(
              onPressed: _isDeleting ? null : () => Navigator.of(context).pop(),
              style: OutlinedButton.styleFrom(
                side: BorderSide(
                  color: cs.onSurfaceVariant.withValues(alpha: 0.3),
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              child: Text(
                'Cancel',
                style: TextStyle(
                  color: cs.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Bottom sheet widget for sign-out confirmation with loading state.
class _SignOutSheet extends StatefulWidget {
  final BuildContext parentContext;
  const _SignOutSheet({required this.parentContext});

  @override
  State<_SignOutSheet> createState() => _SignOutSheetState();
}

class _SignOutSheetState extends State<_SignOutSheet> {
  bool _isLoggingOut = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 36,
            height: 5,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(3),
            ),
          ),
          const SizedBox(height: 20),
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.error.withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(
              CupertinoIcons.arrow_right_square,
              color: Theme.of(context).colorScheme.error,
              size: 28,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'Sign Out',
            style: Theme.of(context).textTheme.titleLarge!.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Are you sure you want to sign out?',
            style: Theme.of(context).textTheme.bodyMedium!.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: _isLoggingOut
                ? const Center(child: LoadingIndicatorM3E())
                : FilledButton(
                    onPressed: () async {
                      setState(() => _isLoggingOut = true);
                      final authProvider =
                          Provider.of<AuthProvider>(context, listen: false);
                      await authProvider.signOut();
                      if (widget.parentContext.mounted) {
                        Navigator.of(widget.parentContext).pushAndRemoveUntil(
                          MaterialPageRoute(builder: (context) => const LoginScreen()),
                          (route) => false,
                        );
                      }
                    },
                    style: FilledButton.styleFrom(
                      backgroundColor: Theme.of(context).colorScheme.error,
                      foregroundColor: Theme.of(context).colorScheme.onError,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    child: const Text('Sign Out', style: TextStyle(fontWeight: FontWeight.w600)),
                  ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton(
              onPressed: _isLoggingOut ? null : () => Navigator.of(context).pop(),
              style: OutlinedButton.styleFrom(
                side: BorderSide(
                  color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              child: Text(
                'Cancel',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
