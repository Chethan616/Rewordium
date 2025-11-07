import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../theme/app_theme.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart'; // Import the CustomButton widget
import '../accessibility_disclosure_dialog.dart';

class AssistantStatusCard extends StatefulWidget {
  const AssistantStatusCard({super.key});

  @override
  State<AssistantStatusCard> createState() => _AssistantStatusCardState();
}

class _AssistantStatusCardState extends State<AssistantStatusCard>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.noxquill.rewordium/accessibility');

  bool? _isServiceEnabled;

  @override
  void initState() {
    super.initState();
    debugPrint(
        'Initializing AssistantStatusCard with channel: ${_channel.name}');
    WidgetsBinding.instance.addObserver(this);
    // Add a small delay to ensure the native side is fully initialized
    Future.delayed(const Duration(milliseconds: 300), () {
      _checkServiceStatus();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      Future.delayed(const Duration(milliseconds: 500), () {
        _checkServiceStatus();
      });
    }
  }

  Future<void> _checkServiceStatus() async {
    if (!mounted) {
      debugPrint('_checkServiceStatus: Not mounted, returning early');
      return;
    }

    debugPrint('Checking accessibility service status...');
    try {
      debugPrint('Invoking method: isAccessibilityServiceEnabled');
      final bool isEnabled =
          await _channel.invokeMethod('isAccessibilityServiceEnabled');
      debugPrint('Received response from native: $isEnabled');

      if (mounted) {
        setState(() {
          _isServiceEnabled = isEnabled;
        });
      } else {
        debugPrint('Widget was unmounted while waiting for response');
      }
    } on PlatformException catch (e) {
      debugPrint('PlatformException when checking accessibility status:');
      debugPrint('Code: ${e.code}');
      debugPrint('Message: ${e.message}');
      debugPrint('Details: ${e.details}');
      debugPrint('Stacktrace: ${e.stacktrace}');

      if (mounted) {
        setState(() {
          _isServiceEnabled = false;
        });
      }
    } catch (e, stacktrace) {
      debugPrint('Unexpected error when checking accessibility status:');
      debugPrint('Error: $e');
      debugPrint('Stacktrace: $stacktrace');

      if (mounted) {
        setState(() {
          _isServiceEnabled = false;
        });
      }
    }
  }

  Future<void> _requestSettings() async {
    // Show prominent disclosure dialog first
    final bool? accepted = await AccessibilityDisclosureDialog.show(context);

    if (accepted == true) {
      // User explicitly consented, proceed to settings
      try {
        await _channel.invokeMethod('requestAccessibilitySettings');
      } on PlatformException catch (e) {
        debugPrint("Failed to open accessibility settings: '${e.message}'.");
      }
    } else {
      // User declined or dismissed dialog
      debugPrint("User declined accessibility permission");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
                'Accessibility permission is required for AI features to work'),
            duration: Duration(seconds: 3),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;

    return AnimatedCard(
      animationDelay: 400,
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      child: _buildContent(isSmallScreen),
    );
  }

  Widget _buildContent(bool isSmallScreen) {
    if (_isServiceEnabled == null) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_isServiceEnabled!) {
      return _buildEnabledState(isSmallScreen);
    } else {
      return _buildDisabledState(isSmallScreen);
    }
  }

  Widget _buildEnabledState(bool isSmallScreen) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: const BoxDecoration(
                color: Colors.green,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              "Assistant Status",
              style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
            ),
            const SizedBox(width: 8),
            SizedBox(
              height: isSmallScreen ? 58 : 106,
              child: LottieAssets.getAssistantAnimation(),
            ),
            const Spacer(),
            const Text(
              "Active",
              style: TextStyle(
                color: Colors.green,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "AI Assistant is Active",
                    style: AppTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    "Ready to help in other apps",
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 4),
            // THE FIX: Changed type to primary for a filled green button
            CustomButton(
              text: "Manage",
              onPressed: _requestSettings,
              width: isSmallScreen ? 85 : 110,
              height: isSmallScreen ? 40 : 48,
              type: ButtonType.primary,
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            SizedBox(
              height: isSmallScreen ? 80 : 110,
              child: LottieAssets
                  .getEgmailAnimation(), // Correct method name assumed
            ),
            const SizedBox(width: 12),
            SizedBox(
              height: isSmallScreen ? 0 : 0,
              child: LottieAssets.getAssistantAnimation(),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildDisabledState(bool isSmallScreen) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: const BoxDecoration(
                color: Colors.amber,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              "Assistant Status",
              style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
            ),
            const Spacer(),
            const Text(
              "Disabled",
              style: TextStyle(
                color: Colors.amber,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "Enable AI Assistant",
                    style: AppTheme.bodyMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    "Get AI help in any app",
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 4),
            CustomButton(
              text: "Enable",
              onPressed: _requestSettings,
              width: isSmallScreen ? 85 : 110,
              height: isSmallScreen ? 40 : 48,
              type: ButtonType.primary,
            ),
          ],
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 80,
          child: LottieAssets.getEgmailAnimation(),
        ),
      ],
    );
  }
}
