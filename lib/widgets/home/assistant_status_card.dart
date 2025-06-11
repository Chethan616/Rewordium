import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../theme/app_theme.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';

class AssistantStatusCard extends StatefulWidget {
  // Remove the isEnabled parameter from constructor, it will be fetched internally
  const AssistantStatusCard({super.key});

  @override
  _AssistantStatusCardState createState() => _AssistantStatusCardState();
}

class _AssistantStatusCardState extends State<AssistantStatusCard> with WidgetsBindingObserver {
  static const platform = MethodChannel('com.example.yc_startup/accessibility');
  final ValueNotifier<bool> _isServiceEnabled = ValueNotifier<bool>(false);
  bool _isLoading = true;
  bool _wasPaused = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _isServiceEnabled.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _wasPaused) {
      _checkAccessibilityServiceStatus();
    } else if (state == AppLifecycleState.paused) {
      _wasPaused = true;
    }
  }

  Future<void> _initStatus() async {
    await _checkAccessibilityServiceStatus();
    // Check status every 2 seconds when the widget is active
    _periodicStatusCheck();
  }

  void _periodicStatusCheck() {
    Future.delayed(const Duration(seconds: 2), () {
      if (mounted) {
        _checkAccessibilityServiceStatus();
        _periodicStatusCheck();
      }
    });
  }

  Future<void> _checkAccessibilityServiceStatus() async {
    if (!mounted) return;
    
    setState(() => _isLoading = true);
    
    try {
      final bool? result = await platform.invokeMethod<bool>('isAccessibilityServiceEnabled');
      if (mounted) {
        _isServiceEnabled.value = result ?? false;
      }
    } on PlatformException catch (e) {
      debugPrint("Failed to check accessibility status: '${e.message}'.");
      if (mounted) {
        _isServiceEnabled.value = false;
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _requestAccessibilitySettings() async {
    try {
      await platform.invokeMethod('requestAccessibilitySettings');
    } on PlatformException catch (e) {
      // Handle error, e.g., show a toast or log
      print("Failed to invoke method: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;

    return AnimatedCard(
      animationDelay: 400,
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              ValueListenableBuilder<bool>(
                valueListenable: _isServiceEnabled,
                builder: (context, isEnabled, _) {
                  return Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: _isLoading ? Colors.grey : (isEnabled ? Colors.green : Colors.red),
                      shape: BoxShape.circle,
                    ),
                  );
                },
              ),
              const SizedBox(width: 8),
              Text(
                "Assistant Status",
                style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: ValueListenableBuilder<bool>(
                  valueListenable: _isServiceEnabled,
                  builder: (context, isEnabled, _) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _isLoading ? "Checking..." : (isEnabled ? "Enabled" : "Disabled"),
                          style: AppTheme.bodyMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          "Get grammar help in any app",
                          style: AppTheme.bodySmall,
                        ),
                      ],
                    );
                  },
                ),
              ),
              const SizedBox(width: 4),
              ValueListenableBuilder<bool>(
                valueListenable: _isServiceEnabled,
                builder: (context, isEnabled, _) {
                  return CustomButton(
                    text: isEnabled ? "Settings" : "Enable",
                    onPressed: _requestAccessibilitySettings,
                    width: isSmallScreen ? 85 : 110,
                    height: isSmallScreen ? 40 : 48,
                    type: ButtonType.secondary,
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 80,
            child: LottieAssets.getAssistantAnimation(),
          ),
        ],
      ),
    );
  }
}
