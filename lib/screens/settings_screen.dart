import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import 'package:rewordium/screens/keyboard_settings_screen.dart';
import '../providers/keyboard_provider.dart';
import '../theme/app_theme.dart';
import '../services/rewordium_keyboard_service.dart';
import '../theme/theme_provider.dart';
import '../utils/lottie_assets.dart';
import '../widgets/animated_card.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/signup_screen.dart';
import '../providers/auth_provider.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  // Navigate to login screen
  void _navigateToLogin(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const LoginScreen()),
    );
  }
  
  // Keyboard functionality removed to fix Android build issues

  // Sign out
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
              Navigator.of(context).pop(); // Close dialog
              final authProvider =
                  Provider.of<AuthProvider>(context, listen: false);
              await authProvider.signOut();

              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('You have been signed out')),
                );
              }
            },
          ),
        ],
      ),
    );
  }

  // Keyboard functionality removed to fix Android build issues

  // Keyboard functionality removed to fix Android build issues

  // Show iOS-style dialog for keyboard reactivation
  void _showIOSStyleKeyboardActivationDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          title: const Text('Reactivate Keyboard', 
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            textAlign: TextAlign.center,
          ),
          content: const Text(
            'To apply your changes, please reactivate your keyboard.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 15),
          ),
          actionsAlignment: MainAxisAlignment.spaceEvenly,
          actionsPadding: const EdgeInsets.only(bottom: 12, left: 12, right: 12),
          actions: [
            // Cancel button
            TextButton(
              style: TextButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
                backgroundColor: Colors.grey[200],
              ),
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel',
                style: TextStyle(color: Colors.black, fontWeight: FontWeight.w500),
              ),
            ),
            // Reactivate button
            TextButton(
              style: TextButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 10),
                backgroundColor: Colors.blue,
              ),
              onPressed: () {
                Navigator.of(context).pop();
                RewordiumKeyboardService.openKeyboardSettings();
              },
              child: const Text('Reactivate',
                style: TextStyle(color: Colors.white, fontWeight: FontWeight.w500),
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
    // Keyboard provider removed to fix Android build issues

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
                  onPressed: () {},
                  color: AppTheme.primaryColor,
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text("Account", style: AppTheme.headingSmall),
            ),
            AnimatedCard(
              child: isLoggedIn
                  ? _buildUserProfile(context, userName, isPro, authProvider)
                  : _buildLoginPrompt(context),
            ),
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text("Appearance", style: AppTheme.headingSmall),
            ),
            _buildSettingToggle("Dark Mode", isDarkMode, (value) {
              themeProvider.toggleTheme();
            }),
            
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text("Keyboard", style: AppTheme.headingSmall),
            ),
            
            // Rewordium AI Keyboard Settings
            AnimatedCard(
              child: InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const KeyboardSettingsScreen()),
                ),
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
            ),
            
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
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
                      const Icon(Icons.info_outline, size: 16, color: Colors.orange),
                      const SizedBox(width: 4),
                      GestureDetector(
                        onTap: () => _showIOSStyleKeyboardActivationDialog(context),
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
                            const Icon(Icons.open_in_new, size: 14, color: Colors.blue),
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
                final selectedPersonas = keyboardProvider.selectedKeyboardPersonas;
                final isParaphraserEnabled = keyboardProvider.isParaphraserEnabled;
                
                return Column(
                  children: [
                    if (!isParaphraserEnabled)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
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
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
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
                                        // If trying to select and already have 3 selected
                                        if (checked == true && 
                                            !selectedPersonas.contains(persona.name) &&
                                            selectedPersonas.length >= 3) {
                                          ScaffoldMessenger.of(context).showSnackBar(
                                            const SnackBar(
                                              content: Text('You can only select up to 3 personas'),
                                              duration: Duration(seconds: 2),
                                            ),
                                          );
                                          return;
                                        }
                                        keyboardProvider.toggleKeyboardPersona(persona.name);
                                      }
                                    : null, // Disabled when paraphraser is off
                                  activeColor: AppTheme.primaryColor,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                  ],
                );
              },
            ),
            if (!isLoggedIn)
              AnimatedCard(
                animationDelay: 600,
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
                      onPressed: () => _navigateToLogin(context),
                      type: ButtonType.primary,
                      width: 200,
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 20),
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
          ],
        ),
      ),
    );
  }

  // Widget to show when user is logged in
  Widget _buildUserProfile(BuildContext context, String name, bool isPro,
      AuthProvider authProvider) {
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
                    name,
                    style: AppTheme.bodyLarge.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    isPro ? "Pro User" : "Normal User",
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
        const Divider(height: 32),
        if (!isPro)
          CustomButton(
            text: "Upgrade to Pro",
            onPressed: () {
              // TODO: Implement upgrade to pro
            },
            type: ButtonType.primary,
            width: double.infinity,
          ),
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

  // Widget to show when user is not logged in
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
          onPressed: () => _navigateToLogin(context),
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
              Navigator.of(context).pop(); // Close confirm dialog first

              // Show loading dialog and keep its context reference
              showDialog(
                context: context,
                barrierDismissible: false,
                builder: (BuildContext loadingContext) {
                  // Save the loadingContext so we can close it later
                  Future.delayed(Duration.zero, () async {
                    final authProvider =
                        Provider.of<AuthProvider>(context, listen: false);
                    bool success = false;
                    try {
                      success = await authProvider.deleteAccount();
                    } catch (e) {
                      // Log or handle error if needed
                    }

                    if (!loadingContext.mounted) return;

                    Navigator.of(loadingContext).pop(); // Close loading dialog

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
}
