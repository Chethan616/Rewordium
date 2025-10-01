import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import '../theme/theme_provider.dart';
import '../theme/app_theme.dart';
import '../services/keyboard_service.dart';

class SystemKeyboardScreen extends StatefulWidget {
  const SystemKeyboardScreen({Key? key}) : super(key: key);

  @override
  _SystemKeyboardScreenState createState() => _SystemKeyboardScreenState();
}

class _SystemKeyboardScreenState extends State<SystemKeyboardScreen> {
  final KeyboardService _keyboardService = KeyboardService();
  bool _isKeyboardEnabled = false;
  KeyboardLayout _currentLayout = KeyboardLayout.onePlus;
  bool _hapticFeedbackEnabled = true;
  bool _aiSuggestionsEnabled = true;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadKeyboardSettings();
  }

  Future<void> _loadKeyboardSettings() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final isEnabled = await _keyboardService.isKeyboardEnabled();
      final currentLayout = await _keyboardService.getCurrentLayout();

      setState(() {
        _isKeyboardEnabled = isEnabled;
        _currentLayout = currentLayout;
        _isLoading = false;
      });
    } catch (e) {
      print('Error loading keyboard settings: $e');
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _openKeyboardSettings() async {
    await _keyboardService.openKeyboardSettings();
    // Refresh the status after a delay to allow the user to change settings
    await Future.delayed(const Duration(seconds: 2));
    await _loadKeyboardSettings();
  }

  Future<void> _setKeyboardLayout(KeyboardLayout layout) async {
    setState(() {
      _isLoading = true;
    });

    try {
      final success = await _keyboardService.setKeyboardLayout(layout);
      if (success) {
        setState(() {
          _currentLayout = layout;
        });
      }
    } catch (e) {
      print('Error setting keyboard layout: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _setHapticFeedback(bool enabled) async {
    try {
      final success = await _keyboardService.setHapticFeedback(enabled);
      if (success) {
        setState(() {
          _hapticFeedbackEnabled = enabled;
        });
      }
    } catch (e) {
      print('Error setting haptic feedback: $e');
    }
  }

  Future<void> _setAiSuggestions(bool enabled) async {
    try {
      final success = await _keyboardService.setAiSuggestions(enabled);
      if (success) {
        setState(() {
          _aiSuggestionsEnabled = enabled;
        });
      }
    } catch (e) {
      print('Error setting AI suggestions: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('System Keyboard Settings'),
        elevation: 0,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Keyboard Status Card
                    Card(
                      elevation: 2,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Keyboard Status',
                              style: AppTheme.headingSmall,
                            ),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Icon(
                                  _isKeyboardEnabled
                                      ? Icons.check_circle
                                      : Icons.error,
                                  color: _isKeyboardEnabled
                                      ? Colors.green
                                      : Colors.red,
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    _isKeyboardEnabled
                                        ? 'Rewordium Keyboard is enabled'
                                        : 'Rewordium Keyboard is not enabled',
                                    style: TextStyle(
                                      color: _isKeyboardEnabled
                                          ? Colors.green
                                          : Colors.red,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton.icon(
                              onPressed: _openKeyboardSettings,
                              icon: const Icon(Icons.settings),
                              label: const Text('Open Keyboard Settings'),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Theme.of(context).primaryColor,
                                minimumSize: const Size(double.infinity, 48),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(8),
                                ),
                              ),
                            ),
                            if (!_isKeyboardEnabled)
                              Padding(
                                padding: const EdgeInsets.only(top: 8.0),
                                child: Text(
                                  'To use the Rewordium Keyboard, you need to enable it in your device settings and select it as your default keyboard.',
                                  style: TextStyle(
                                    color: isDarkMode
                                        ? Colors.grey[400]
                                        : Colors.grey[600],
                                    fontSize: 12,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Keyboard Layout Card
                    Card(
                      elevation: 2,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Keyboard Layout',
                              style: AppTheme.headingSmall,
                            ),
                            const SizedBox(height: 16),
                            RadioListTile<KeyboardLayout>(
                              title: const Text('OnePlus Style'),
                              value: KeyboardLayout.onePlus,
                              groupValue: _currentLayout,
                              onChanged: (KeyboardLayout? value) {
                                if (value != null) {
                                  _setKeyboardLayout(value);
                                }
                              },
                              activeColor: Theme.of(context).primaryColor,
                            ),
                            RadioListTile<KeyboardLayout>(
                              title: const Text('Samsung Style'),
                              value: KeyboardLayout.samsung,
                              groupValue: _currentLayout,
                              onChanged: (KeyboardLayout? value) {
                                if (value != null) {
                                  _setKeyboardLayout(value);
                                }
                              },
                              activeColor: Theme.of(context).primaryColor,
                            ),
                            RadioListTile<KeyboardLayout>(
                              title: const Text('Apple Style'),
                              value: KeyboardLayout.apple,
                              groupValue: _currentLayout,
                              onChanged: (KeyboardLayout? value) {
                                if (value != null) {
                                  _setKeyboardLayout(value);
                                }
                              },
                              activeColor: Theme.of(context).primaryColor,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Additional Settings Card
                    Card(
                      elevation: 2,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Additional Settings',
                              style: AppTheme.headingSmall,
                            ),
                            const SizedBox(height: 16),
                            SwitchListTile(
                              title: const Text('Haptic Feedback'),
                              subtitle: const Text(
                                  'Vibrate when a key is pressed'),
                              value: _hapticFeedbackEnabled,
                              onChanged: _setHapticFeedback,
                              activeColor: Theme.of(context).primaryColor,
                            ),
                            SwitchListTile(
                              title: const Text('AI Suggestions'),
                              subtitle: const Text(
                                  'Show AI-powered word suggestions'),
                              value: _aiSuggestionsEnabled,
                              onChanged: _setAiSuggestions,
                              activeColor: Theme.of(context).primaryColor,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // About Card
                    Card(
                      elevation: 2,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'About',
                              style: AppTheme.headingSmall,
                            ),
                            const SizedBox(height: 16),
                            Text(
                              'Rewordium Keyboard v1.0',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: isDarkMode
                                    ? Colors.grey[300]
                                    : Colors.grey[800],
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'A system-wide keyboard with AI-powered suggestions using OpenAI technology. Choose from multiple keyboard styles and customize your typing experience.',
                              style: TextStyle(
                                color: isDarkMode
                                    ? Colors.grey[400]
                                    : Colors.grey[600],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
