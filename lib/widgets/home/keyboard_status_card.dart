import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../theme/app_theme.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';
import 'package:flutter/widgets.dart';
import '../../providers/keyboard_provider.dart';
import '../experimental_keyboard_dialog.dart';

class KeyboardStatusCard extends StatefulWidget {
  const KeyboardStatusCard({super.key});

  @override
  State<KeyboardStatusCard> createState() => _KeyboardStatusCardState();
}

class _KeyboardStatusCardState extends State<KeyboardStatusCard> with WidgetsBindingObserver {
  late final KeyboardProvider _keyboardProvider;
  late final VoidCallback _onAppResume;

  @override
  void initState() {
    super.initState();
    // Get the provider reference
    _keyboardProvider = Provider.of<KeyboardProvider>(context, listen: false);
    
    // Setup app resume callback
    _onAppResume = () {
      // Add a small delay to ensure the app is fully resumed
      Future.delayed(const Duration(milliseconds: 500), () {
        _keyboardProvider.checkKeyboardStatus();
      });
    };
    
    // Add lifecycle observer
    WidgetsBinding.instance.addObserver(this);
    
    // Start checking keyboard status when widget initializes
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _keyboardProvider.startKeyboardStatusCheck();
    });
  }

  @override
  void dispose() {
    // Stop checking keyboard status when widget is disposed
    _keyboardProvider.stopKeyboardStatusCheck();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Check keyboard status when app resumes
    if (state == AppLifecycleState.resumed) {
      _onAppResume();
    }
  }

  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;
    final keyboardProvider = Provider.of<KeyboardProvider>(context);
    final isEnabled = keyboardProvider.isSystemKeyboardEnabled;
    final isParaphraserEnabled = keyboardProvider.isParaphraserEnabled;

    return AnimatedCard(
      animationDelay: 300,
      padding: EdgeInsets.all(isSmallScreen ? 12 : 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: isEnabled ? Colors.green : Colors.amber,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                "Keyboard Status",
                style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
              ),
              const Spacer(),
              // Status indicator with refresh button
              Row(
                children: [
                  Text(
                    isEnabled ? "Enabled" : "Disabled",
                    style: TextStyle(
                      color: isEnabled ? Colors.green : Colors.amber,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton(
                    icon: const Icon(Icons.refresh, size: 20),
                    onPressed: () {
                      keyboardProvider.checkKeyboardStatus();
                    },
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    iconSize: 20,
                    color: Theme.of(context).primaryColor,
                  ),
                ],
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
                      isEnabled ? "System Keyboard Active" : "Manage Your Keyboard",
                      style: AppTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      "Write better in all your apps",
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 4),
              CustomButton(
                text: "Manage",
                onPressed: () async {
                  // Show experimental feature dialog first
                  final shouldProceed = await showExperimentalKeyboardDialog(context);
                  if (shouldProceed) {
                    // Open the phone's keyboard settings
                    keyboardProvider.openKeyboardSettings();
                  }
                },
                width: isSmallScreen ? 85 : 110,
                height: isSmallScreen ? 40 : 48,
                type: ButtonType.primary,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Enable Personas",
                      style: AppTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      "Enhance your writing with AI personas",
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 4),
              Switch(
                value: isParaphraserEnabled,
                onChanged: (_) => keyboardProvider.toggleParaphraser(),
                activeColor: AppTheme.primaryColor,
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 80,
            child: LottieAssets.getKeyboardAnimation(),
          ),
        ],
      ),
    );
  }
  
  void _showKeyboardSettings(BuildContext context, KeyboardProvider provider) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => KeyboardSettingsSheet(provider: provider),
    );
  }
}

class KeyboardSettingsSheet extends StatelessWidget {
  final KeyboardProvider provider;
  
  const KeyboardSettingsSheet({super.key, required this.provider});
  
  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final bool isSmallScreen = screenSize.width < 360;
    
    return Padding(
      padding: EdgeInsets.all(isSmallScreen ? 16 : 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.withOpacity(0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text("Keyboard Settings", style: AppTheme.headingMedium),
          const SizedBox(height: 16),
          _buildSettingItem(
            title: "Keyboard Layout",
            subtitle: "Select your preferred keyboard layout",
            trailing: DropdownButton<KeyboardLayout>(
              value: provider.layout,
              onChanged: (layout) {
                if (layout != null) {
                  provider.setLayout(layout);
                }
              },
              items: KeyboardLayout.values.map((layout) {
                return DropdownMenuItem<KeyboardLayout>(
                  value: layout,
                  child: Text(provider.layoutName(layout)),
                );
              }).toList(),
              underline: const SizedBox(),
            ),
          ),
          const Divider(),
          _buildSettingItem(
            title: "Haptic Feedback",
            subtitle: "Vibrate when typing",
            trailing: Switch(
              value: provider.soundOn,
              onChanged: (_) => provider.toggleSound(),
              activeColor: AppTheme.primaryColor,
            ),
          ),
          const Divider(),
          _buildSettingItem(
            title: "Paraphraser Button",
            subtitle: "Show paraphraser button on keyboard",
            trailing: Switch(
              value: provider.isParaphraserEnabled,
              onChanged: (_) => provider.toggleParaphraser(),
              activeColor: AppTheme.primaryColor,
            ),
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: CustomButton(
              text: "Close",
              onPressed: () => Navigator.pop(context),
              type: ButtonType.secondary,
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }
  
  Widget _buildSettingItem({
    required String title,
    required String subtitle,
    required Widget trailing,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTheme.bodyLarge),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: AppTheme.bodySmall.copyWith(
                    color: AppTheme.textSecondaryColor,
                  ),
                ),
              ],
            ),
          ),
          trailing,
        ],
      ),
    );
  }
}
