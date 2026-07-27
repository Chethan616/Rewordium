import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_colorpicker/flutter_colorpicker.dart';
import '../services/rewordium_keyboard_service.dart';

/// Default values matching AppPrefs.kt exactly
const _kDefaultTrailWidth = 5.5;
const _kDefaultHeightPortrait = 100;
const _kDefaultHeightLandscape = 100;
const _kDefaultRoundedSmartbarRadius = 28.0;

class KeyboardQuickSettingsScreen extends StatefulWidget {
  const KeyboardQuickSettingsScreen({super.key});

  @override
  State<KeyboardQuickSettingsScreen> createState() =>
      _KeyboardQuickSettingsScreenState();
}

class _KeyboardQuickSettingsScreenState
    extends State<KeyboardQuickSettingsScreen> {
  bool _isLoading = true;
  Map<String, dynamic> _settings = {};

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    setState(() => _isLoading = true);
    try {
      final settings = await RewordiumKeyboardService.getQuickSettings();
      setState(() {
        _settings = settings;
        _isLoading = false;
      });
    } catch (e) {
      debugPrint('Error loading quick settings: $e');
      setState(() => _isLoading = false);
    }
  }

  Future<void> _updateSetting(String key, dynamic value) async {
    setState(() {
      _settings[key] = value;
    });
    await RewordiumKeyboardService.updateQuickSetting(key, value);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    if (_isLoading) {
      return Scaffold(
        appBar: AppBar(title: const Text('Keyboard Settings'), centerTitle: true),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Keyboard Settings'),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          // ──────────────────────────── Appearance ────────────────────────────
          _buildSectionHeader('Appearance & Theme'),
          _buildM3DropdownTile(
            icon: Icons.brightness_6_rounded,
            title: 'Theme Mode',
            value: _settings['themeMode']?.toString() ?? 'FOLLOW_SYSTEM',
            options: const ['ALWAYS_DAY', 'ALWAYS_NIGHT', 'FOLLOW_SYSTEM'],
            labels: const ['Always Light', 'Always Dark', 'Follow System'],
            onChanged: (v) => _updateSetting('themeMode', v),
          ),
          _buildSwitchTile(
            icon: Icons.rounded_corner_rounded,
            title: 'Rounded Smartbar',
            subtitle: 'Experimental rounded design for the smartbar',
            value: _settings['experimentalRoundedSmartbar'] == true,
            onChanged: (v) => _updateSetting('experimentalRoundedSmartbar', v),
          ),
          if (_settings['experimentalRoundedSmartbar'] == true)
            _buildSliderTile(
              icon: Icons.blur_circular_rounded,
              title: 'Rounded Smartbar Radius',
              value: (_settings['experimentalRoundedSmartbarRadius'] ?? _kDefaultRoundedSmartbarRadius).toDouble(),
              min: 0.0,
              max: 32.0,
              defaultValue: _kDefaultRoundedSmartbarRadius,
              displaySuffix: 'px',
              onChanged: (v) => _updateSetting('experimentalRoundedSmartbarRadius', v.toInt()),
            ),


          // ──────────────────────────── Sizing ────────────────────────────────
          _buildSectionHeader('Keyboard Sizing'),
          _buildSliderTile(
            icon: Icons.height_rounded,
            title: 'Portrait Height',
            value: (_settings['heightFactorPortrait'] ?? _kDefaultHeightPortrait)
                .toDouble(),
            min: 50,
            max: 150,
            defaultValue: _kDefaultHeightPortrait.toDouble(),
            displaySuffix: '%',
            onChanged: (v) => _updateSetting('heightFactorPortrait', v.toInt()),
          ),
          _buildSliderTile(
            icon: Icons.screen_rotation_rounded,
            title: 'Landscape Height',
            value: (_settings['heightFactorLandscape'] ?? _kDefaultHeightLandscape)
                .toDouble(),
            min: 50,
            max: 150,
            defaultValue: _kDefaultHeightLandscape.toDouble(),
            displaySuffix: '%',
            onChanged: (v) => _updateSetting('heightFactorLandscape', v.toInt()),
          ),

          const SizedBox(height: 8),

          // ──────────────────────────── Typing ────────────────────────────────
          _buildSectionHeader('Typing & Correction'),
          _buildSwitchTile(
            icon: Icons.gesture_rounded,
            title: 'Glide Typing',
            subtitle: 'Swipe between letters to type',
            value: _settings['glideEnabled'] == true,
            onChanged: (v) => _updateSetting('glideEnabled', v),
          ),
          if (_settings['glideEnabled'] == true)
            _buildSliderTile(
              icon: Icons.line_weight_rounded,
              title: 'Glide Trail Width',
              value: (_settings['glideTrailWidth'] ?? _kDefaultTrailWidth)
                  .toDouble(),
              min: 2.0,
              max: 30.0,
              defaultValue: _kDefaultTrailWidth,
              displaySuffix: 'px',
              onChanged: (v) => _updateSetting('glideTrailWidth', v),
            ),
          _buildSwitchTile(
            icon: Icons.text_fields_rounded,
            title: 'Auto Capitalization',
            subtitle: 'Capitalize first word of sentences',
            value: _settings['autoCapitalization'] == true,
            onChanged: (v) => _updateSetting('autoCapitalization', v),
          ),
          _buildSwitchTile(
            icon: Icons.pin_rounded,
            title: 'Number Row',
            subtitle: 'Show a dedicated number row',
            value: _settings['numberRow'] == true,
            onChanged: (v) => _updateSetting('numberRow', v),
          ),
          _buildSwitchTile(
            icon: Icons.auto_fix_high_rounded,
            title: 'Smartbar',
            subtitle: 'Show suggestions toolbar above keyboard',
            value: _settings['smartbarEnabled'] == true,
            onChanged: (v) => _updateSetting('smartbarEnabled', v),
          ),
          if (_settings['smartbarEnabled'] == true)
            _buildM3DropdownTile(
              icon: Icons.view_headline_rounded,
              title: 'Suggestions Display',
              value: _settings['suggestionDisplayMode']?.toString() ?? 'CLASSIC',
              options: const ['CLASSIC', 'DYNAMIC', 'HIDDEN'],
              labels: const ['Classic', 'Dynamic', 'Hidden'],
              onChanged: (v) => _updateSetting('suggestionDisplayMode', v),
            ),
          _buildSwitchTile(
            icon: Icons.contacts_rounded,
            title: 'Learn from Contacts',
            subtitle: 'Use contact names for typing suggestions',
            value: _settings['spellingUseContacts'] == true,
            onChanged: (v) => _updateSetting('spellingUseContacts', v),
          ),

          const SizedBox(height: 8),

          // ──────────────────────────── Clipboard ─────────────────────────────
          _buildSectionHeader('Clipboard & History'),
          _buildSwitchTile(
            icon: Icons.content_paste_rounded,
            title: 'Clipboard History',
            subtitle: 'Save copied text to clipboard history',
            value: _settings['clipboardHistoryEnabled'] == true,
            onChanged: (v) => _updateSetting('clipboardHistoryEnabled', v),
          ),
          if (_settings['clipboardHistoryEnabled'] == true)
            _buildSwitchTile(
              icon: Icons.smart_button_rounded,
              title: 'Show Suggestions',
              subtitle: 'Show copied items as smartbar suggestions',
              value: _settings['clipboardSuggestionEnabled'] == true,
              onChanged: (v) => _updateSetting('clipboardSuggestionEnabled', v),
            ),
          if (_settings['clipboardHistoryEnabled'] == true && _settings['clipboardSuggestionEnabled'] == true)
            _buildSliderTile(
              icon: Icons.timer_rounded,
              title: 'Suggestion Timeout',
              value: (_settings['clipboardSuggestionTimeout'] ?? 30).toDouble(),
              min: 0,
              max: 120,
              defaultValue: 30,
              displaySuffix: 's',
              onChanged: (v) => _updateSetting('clipboardSuggestionTimeout', v.toInt()),
            ),

          const SizedBox(height: 8),

          // ──────────────────────────── Feedback ──────────────────────────────
          _buildSectionHeader('Input Feedback'),
          _buildSwitchTile(
            icon: Icons.vibration_rounded,
            title: 'Keypress Vibration',
            subtitle: 'Haptic feedback on key press',
            value: _settings['hapticEnabled'] == true,
            onChanged: (v) => _updateSetting('hapticEnabled', v),
          ),
          if (_settings['hapticEnabled'] == true)
            _buildM3DropdownTile(
              icon: Icons.phonelink_ring_rounded,
              title: 'Vibration Mode',
              value: _settings['hapticVibrationMode']?.toString() ??
                  'USE_VIBRATOR_DIRECTLY',
              options: const [
                'USE_VIBRATOR_DIRECTLY',
                'USE_HAPTIC_FEEDBACK_INTERFACE',
              ],
              labels: const ['Vibrator (Direct)', 'Haptic Interface'],
              onChanged: (v) => _updateSetting('hapticVibrationMode', v),
            ),

          const SizedBox(height: 8),

          // ──────────────────────────── Spacebar ──────────────────────────────
          _buildSectionHeader('Spacebar'),
          _buildM3DropdownTile(
            icon: Icons.space_bar_rounded,
            title: 'Spacebar Label',
            value: _settings['spaceBarMode']?.toString() ?? 'CURRENT_LANGUAGE',
            options: const [
              'NOTHING',
              'CURRENT_LANGUAGE',
              'SPACE_BAR_KEY',
              'CUSTOM_LABEL',
            ],
            labels: const [
              'Nothing',
              'Current Language',
              'Space Bar',
              'Custom Label',
            ],
            onChanged: (v) => _updateSetting('spaceBarMode', v),
          ),
          if (_settings['spaceBarMode'] == 'CUSTOM_LABEL')
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: TextFormField(
                initialValue: _settings['spaceBarCustomLabel']?.toString() ?? '',
                decoration: InputDecoration(
                  labelText: 'Custom Label Text',
                  hintText: 'Enter text to display on spacebar',
                  filled: true,
                  fillColor: Theme.of(context).colorScheme.surfaceContainerHighest.withOpacity(0.3),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(
                      color: Theme.of(context).colorScheme.primary,
                      width: 2,
                    ),
                  ),
                  prefixIcon: Icon(
                    Icons.label_outline_rounded,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
                onChanged: (v) => _updateSetting('spaceBarCustomLabel', v),
              ),
            ),

          const SizedBox(height: 16),

          // ──────────────────────────── Navigation ────────────────────────────
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Column(
              children: [
                ListTile(
                  leading: Icon(Icons.language_rounded,
                      color: Theme.of(context).colorScheme.primary),
                  title: const Text(
                    'Languages & Layouts',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: const Text(
                    'Manage languages, QWERTY, AZERTY and other layouts',
                  ),
                  trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 16),
                  onTap: () => RewordiumKeyboardService.openLanguagesSettings(),
                ),
                const Divider(height: 1, indent: 16, endIndent: 16),
                ListTile(
                  leading: Icon(Icons.tune_rounded,
                      color: Theme.of(context).colorScheme.secondary),
                  title: const Text(
                    'More Options',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: const Text('Open full native Reboard settings'),
                  trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 16),
                  // Opens the native FlorisAppActivity main screen
                  onTap: () async {
                    await RewordiumKeyboardService.openReboardSettings();
                  },
                ),
              ],
            ),
          ),

          const SizedBox(height: 32),
        ],
      ),
    );
  }

  // ───────────────────────── Helpers ──────────────────────────────────────────

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        title.toUpperCase(),
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }

  Widget _buildSwitchTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return SwitchListTile(
      secondary: Icon(icon, color: Theme.of(context).colorScheme.onSurfaceVariant),
      title: Text(title),
      subtitle: Text(subtitle),
      value: value,
      onChanged: onChanged,
    );
  }

  Widget _buildM3DropdownTile({
    required IconData icon,
    required String title,
    required String value,
    required List<String> options,
    required List<String> labels,
    required ValueChanged<String> onChanged,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    final int selectedIndex = options.indexOf(value);
    final String selectedLabel = selectedIndex != -1 ? labels[selectedIndex] : value;

    return ListTile(
      leading: Icon(icon, color: colorScheme.onSurfaceVariant),
      title: Text(title),
      subtitle: Text(selectedLabel),
      onTap: () {
        showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
              contentPadding: const EdgeInsets.only(top: 16, bottom: 8),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: List.generate(options.length, (i) {
                    return RadioListTile<String>(
                      title: Text(labels[i]),
                      value: options[i],
                      groupValue: value,
                      activeColor: colorScheme.primary,
                      onChanged: (String? newValue) {
                        if (newValue != null) {
                          onChanged(newValue);
                        }
                        Navigator.pop(context);
                      },
                    );
                  }),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('CANCEL'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildSliderTile({
    required IconData icon,
    required String title,
    double value = 0,
    required double min,
    required double max,
    required double defaultValue,
    required String displaySuffix,
    required ValueChanged<double> onChanged,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    return ListTile(
      leading: Icon(icon, color: colorScheme.onSurfaceVariant),
      title: Text(title),
      subtitle: Text('${value.toStringAsFixed(displaySuffix == 'px' ? 1 : 0)}$displaySuffix'),
      onTap: () {
        double currentValue = value;
        showDialog(
          context: context,
          builder: (context) {
            return StatefulBuilder(
              builder: (context, setDialogState) {
                return AlertDialog(
                  title: Text(title),
                  content: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('${currentValue.toStringAsFixed(displaySuffix == 'px' ? 1 : 0)}$displaySuffix', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                      SliderTheme(
                        data: SliderTheme.of(context).copyWith(
                          trackHeight: 8,
                          thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
                          overlayShape: const RoundSliderOverlayShape(overlayRadius: 24),
                          activeTrackColor: colorScheme.primary,
                          inactiveTrackColor: colorScheme.surfaceContainerHighest,
                        ),
                        child: Slider(
                          value: currentValue,
                          min: min,
                          max: max,
                          divisions: (max - min) > 200 ? null : ((max - min) / (displaySuffix == 'px' ? 1 : 5)).round().clamp(1, 100),
                          label: '${currentValue.toStringAsFixed(displaySuffix == 'px' ? 1 : 0)}$displaySuffix',
                          onChanged: (val) {
                            setDialogState(() => currentValue = val);
                            onChanged(val);
                          },
                        ),
                      ),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.restart_alt_rounded),
                        title: const Text('Reset to default'),
                        subtitle: Text('Restore default ($defaultValue$displaySuffix)'),
                        onTap: () {
                          setDialogState(() => currentValue = defaultValue);
                          onChanged(defaultValue);
                        },
                      )
                    ],
                  ),
                  actions: [
                    TextButton(
                      child: const Text('OK'),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                  ],
                );
              },
            );
          },
        );
      },
    );
  }
}
