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
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("Account", style: AppTheme.headingSmall),
            ),
            AnimatedCard(
              child: isLoggedIn
                  ? _buildUserProfile(context, userName, isPro, authProvider)
                  : _buildLoginPrompt(context),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("Appearance", style: AppTheme.headingSmall),
            ),
            _buildSettingToggle("Dark Mode", isDarkMode, (value) {
              themeProvider.toggleTheme();
            }),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("AI Settings", style: AppTheme.headingSmall),
            ),
            AnimatedCard(
              padding: EdgeInsets.zero,
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const AdvancedAISettingsScreen(),
                  ),
                );
              },
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [
                            AppTheme.primaryColor,
                            AppTheme.primaryColor.withOpacity(0.7)
                          ],
                        ),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(
                        Icons.psychology,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "Advanced AI Settings",
                            style: AppTheme.bodyLarge.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            "Use your own LLM API key",
                            style: AppTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      CupertinoIcons.chevron_right,
                      color: Colors.grey,
                      size: 20,
                    ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("Keyboard", style: AppTheme.headingSmall),
            ),
            AnimatedCard(
              padding: EdgeInsets.zero,
              onTap: () => _openReboardSettings(),
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        CupertinoIcons.keyboard,
                        color: AppTheme.primaryColor,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "Rewordium AI Keyboard",
                            style: AppTheme.bodyLarge.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            "Customize appearance and behavior",
                            style: AppTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      CupertinoIcons.chevron_right,
                      color: Colors.grey,
                    ),
                  ],
                ),
              ),
            ),
            // OLD KEYBOARD PERSONA SELECTOR - COMMENTED OUT (Now using ReBoard native settings)
            /*
            Padding(
              padding:
                  const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "Select up to 3 personas for your keyboard",
                    style: AppTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.info_outline,
                          size: 16, color: Colors.orange),
                      const SizedBox(width: 4),
                      GestureDetector(
                        onTap: () =>
                            _showIOSStyleKeyboardActivationDialog(context),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              'Tap here to reactivate',
                              style: AppTheme.bodySmall.copyWith(
                                color: Colors.blue,
                                fontStyle: FontStyle.italic,
                                decoration: TextDecoration.underline,
                              ),
                            ),
                            const SizedBox(width: 4),
                            const Icon(Icons.open_in_new,
                                size: 14, color: Colors.blue),
                          ],
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        "for changes to take effect",
                        style: AppTheme.bodySmall.copyWith(
                          color: Colors.orange,
                          fontSize: 10,
                          fontStyle: FontStyle.italic,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 4),
            Consumer<KeyboardProvider>(
              builder: (context, keyboardProvider, child) {
                final personas = keyboardProvider.personas;
                final selectedPersonas =
                    keyboardProvider.selectedKeyboardPersonas;
                final isParaphraserEnabled =
                    keyboardProvider.isParaphraserEnabled;

                return Column(
                  children: [
                    if (!isParaphraserEnabled)
                      Padding(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16.0, vertical: 8.0),
                        child: Text(
                          "Enable Paraphraser Persona in home screen to select personas",
                          style: AppTheme.bodyMedium.copyWith(
                            color: Colors.orange,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    for (var persona in personas)
                      AnimatedCard(
                        padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
                        child: Opacity(
                          opacity: isParaphraserEnabled ? 1.0 : 0.5,
                          child: Row(
                            children: [
                              Container(
                                width: 40,
                                height: 40,
                                decoration: BoxDecoration(
                                  color: selectedPersonas.contains(persona.name)
                                      ? AppTheme.primaryColor
                                      : AppTheme.primaryColor.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Icon(
                                  _getPersonaIcon(persona.name),
                                  color: selectedPersonas.contains(persona.name)
                                      ? Colors.white
                                      : AppTheme.primaryColor,
                                  size: 20,
                                ),
                              ),
                              const SizedBox(width: 16),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      persona.name,
                                      style: AppTheme.bodyMedium.copyWith(
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      persona.description,
                                      style: AppTheme.bodySmall,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                              Checkbox(
                                value: selectedPersonas.contains(persona.name),
                                onChanged: isParaphraserEnabled
                                    ? (bool? checked) {
                                        if (checked == true &&
                                            !selectedPersonas
                                                .contains(persona.name) &&
                                            selectedPersonas.length >= 3) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            const SnackBar(
                                              content: Text(
                                                  'You can only select up to 3 personas'),
                                              duration: Duration(seconds: 2),
                                            ),
                                          );
                                          return;
                                        }
                                        keyboardProvider.toggleKeyboardPersona(
                                            persona.name);
                                      }
                                    : null,
                                activeColor: AppTheme.primaryColor,
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                );
              },
            ),
            */
            // END OF OLD KEYBOARD PERSONA SELECTOR
            if (!isLoggedIn)
              AnimatedCard(
                child: Column(
                  children: [
                    SizedBox(
                      height: 100,
                      child: LottieAssets.getKeyboardAnimation(),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      "To personalize the Keyboard, open an account",
                      textAlign: TextAlign.center,
                      style: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.textSecondaryColor,
                      ),
                    ),
                    const SizedBox(height: 16),
                    CustomButton(
                      text: "Create Account",
                      onPressed: _navigateToLogin,
                      type: ButtonType.primary,
                      width: 200,
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 20),
            // User Preferences Section
            if (isLoggedIn) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                child: Text("User Preferences", style: AppTheme.headingSmall),
              ),
              AnimatedCard(
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Icon(
                        CupertinoIcons.mail,
                        color: AppTheme.primaryColor,
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'News & Updates',
                            style: AppTheme.bodyMedium.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Receive product news, feature announcements and more!',
                            style: AppTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    if (_isLoadingNewsSubscription)
                      const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else
                      CupertinoSwitch(
                        value: _isNewsSubscribed,
                        onChanged: _toggleNewsSubscription,
                        activeTrackColor: AppTheme.primaryColor,
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],
            if (isLoggedIn)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: CustomButton(
                  text: 'Delete Account',
                  type: ButtonType.secondary,
                  customColor: Colors.red,
                  onPressed: () => _confirmDeleteAccount(context),
                ),
              ),
            const SizedBox(height: 20),
            // App Information Section
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("App Information", style: AppTheme.headingSmall),
            ),
            _buildAppInfoSection(context),
            // Credits Section
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text("Credits", style: AppTheme.headingSmall),
            ),
            _buildCreditsSection(context),
            const SizedBox(height: 20),
          ],
        ),
      ),
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

  Widget _buildSettingToggle(String title, bool isOn,
      [Function(bool)? onChanged]) {
    return AnimatedCard(
      padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: AppTheme.bodyMedium),
          Switch(
            value: isOn,
            onChanged: onChanged,
            activeColor: AppTheme.primaryColor,
          ),
        ],
      ),
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

  // Build method is already defined above, removing the duplicate

  Widget _buildAppInfoSection(BuildContext context) {
    String appVersion = '1.0.0';
    String buildNumber = '1';

    return FutureBuilder<PackageInfo>(
      future: PackageInfo.fromPlatform(),
      builder: (context, snapshot) {
        if (snapshot.hasData) {
          appVersion = snapshot.data!.version;
          buildNumber = snapshot.data!.buildNumber;
        }

        return Column(
          children: [
            // App Version with admin access (tap 5 times)
            GestureDetector(
              onTap: _checkAdminAccess,
              child: AnimatedCard(
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        CupertinoIcons.info,
                        color: AppTheme.primaryColor,
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "App Version",
                            style: AppTheme.bodyMedium.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            "v$appVersion ($buildNumber)",
                            style: AppTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            // Check for Updates
            AnimatedCard(
              onTap: () {
                ForceUpdateService.manualUpdateCheck(context);
              },
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: AppTheme.primaryColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      CupertinoIcons.arrow_down_circle,
                      color: AppTheme.primaryColor,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          "Check for Updates",
                          style: AppTheme.bodyMedium.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          "Tap to check for app updates",
                          style: AppTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    CupertinoIcons.chevron_right,
                    color: AppTheme.textSecondaryColor,
                    size: 16,
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildCreditsSection(BuildContext context) {
    return AnimatedCard(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => const LicensesScreen()),
        );
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(
                  CupertinoIcons.heart_fill,
                  color: Colors.green,
                  size: 20,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Credits & Licenses",
                      style: AppTheme.bodyLarge.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      "View open source licenses and attributions",
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                CupertinoIcons.chevron_right,
                color: AppTheme.textSecondaryColor,
                size: 18,
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Divider(height: 1),
          const SizedBox(height: 16),
          // License Notice Text
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.textSecondaryColor.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                const Icon(CupertinoIcons.keyboard,
                    color: Colors.green, size: 18),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    "Based on FlorisBoard • Apache License 2.0",
                    style: AppTheme.bodySmall.copyWith(
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
