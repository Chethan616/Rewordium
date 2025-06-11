import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_colorpicker/flutter_colorpicker.dart';
import '../services/rewordium_keyboard_service.dart';
import '../widgets/keyboard/keyboard_preview.dart';

class KeyboardSettingsScreen extends StatefulWidget {
  const KeyboardSettingsScreen({Key? key}) : super(key: key);

  @override
  _KeyboardSettingsScreenState createState() => _KeyboardSettingsScreenState();
}

class _KeyboardSettingsScreenState extends State<KeyboardSettingsScreen> {
  bool _isLoading = true;
  bool _isKeyboardEnabled = false;
  
  // Keyboard settings
  String _themeColor = '#007AFF'; // Default iOS blue
  bool _isDarkMode = false;
  bool _isHapticFeedbackEnabled = true; // Changed default to true
  bool _isAutoCapitalizeEnabled = true;
  bool _isDoubleSpacePeriodEnabled = true;
  bool _isAutocorrectEnabled = true; // Add this line
  
  @override
  void initState() {
    super.initState();
    _loadSettings();
  }
  
  Future<void> _loadSettings() async {
    if (!mounted) return;
    
    setState(() {
      _isLoading = true;
    });
    
    try {
      // First ensure defaults are set
      await RewordiumKeyboardService.setHapticFeedback(true);
      await RewordiumKeyboardService.setAutoCapitalize(true);
      await RewordiumKeyboardService.setDoubleSpacePeriod(true);
      
      // Check if keyboard is enabled
      final isEnabled = await RewordiumKeyboardService.isKeyboardEnabled();
      
      // Load keyboard settings
      final settings = await RewordiumKeyboardService.getKeyboardSettings();
      
      if (!mounted) return;
      
      setState(() {
        _isKeyboardEnabled = isEnabled;
        _themeColor = settings['themeColor'] ?? '#007AFF';
        _isDarkMode = settings['darkMode'] ?? false;
        _isHapticFeedbackEnabled = settings['hapticFeedback'] ?? true;
        _isAutoCapitalizeEnabled = settings['autoCapitalize'] ?? true;
        _isDoubleSpacePeriodEnabled = settings['doubleSpacePeriod'] ?? true;
        _isAutocorrectEnabled = settings['autocorrect'] ?? true;
        _isLoading = false;
      });
      
      // Debug log the loaded settings
      print('Loaded keyboard settings: ${{
        'hapticFeedback': _isHapticFeedbackEnabled,
        'autoCapitalize': _isAutoCapitalizeEnabled,
        'doubleSpacePeriod': _isDoubleSpacePeriodEnabled,
      }}');
      
    } catch (e) {
      print('Error loading keyboard settings: $e');
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }
  
  void _openKeyboardSettings() async {
    await RewordiumKeyboardService.openKeyboardSettings();
  }
  
  void _updateThemeColor(String colorHex) async {
    try {
      // Show a loading dialog while applying changes
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (BuildContext context) {
          return AlertDialog(
            title: const Text('Applying Theme Changes'),
            content: const Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Restarting keyboard with new theme...'),
              ],
            ),
          );
        },
      );
      
      // Update theme color in native code
      await RewordiumKeyboardService.updateThemeColor(colorHex);
      
      // Force a complete restart of the keyboard with multiple refresh attempts
      await RewordiumKeyboardService.refreshKeyboard();
      await Future.delayed(const Duration(milliseconds: 300));
      await RewordiumKeyboardService.refreshKeyboard();
      await Future.delayed(const Duration(milliseconds: 300));
      await RewordiumKeyboardService.refreshKeyboard();
      
      // Update state and close dialog
      setState(() {
        _themeColor = colorHex;
      });
      
      // Close the loading dialog
      Navigator.of(context, rootNavigator: true).pop();
      
      // Show iOS-style activation prompt
      _showIOSStyleKeyboardActivationDialog();
      
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Theme color updated and keyboard restarted')),
      );
    } catch (e) {
      // Close the loading dialog if there's an error
      Navigator.of(context, rootNavigator: true).pop();
      
      print('Error updating theme color: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to update theme color')),
      );
    }
  }
  
  void _toggleDarkMode(bool value) async {
    try {
      // Show a loading dialog while applying changes
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (BuildContext context) {
          return AlertDialog(
            title: const Text('Applying Theme Changes'),
            content: const Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Restarting keyboard with new theme...'),
              ],
            ),
          );
        },
      );
      
      // Update dark mode in native code
      await RewordiumKeyboardService.setDarkMode(value);
      
      // Force a complete restart of the keyboard with multiple refresh attempts
      await RewordiumKeyboardService.refreshKeyboard();
      await Future.delayed(const Duration(milliseconds: 300));
      await RewordiumKeyboardService.refreshKeyboard();
      await Future.delayed(const Duration(milliseconds: 300));
      await RewordiumKeyboardService.refreshKeyboard();
      
      // Update state and close dialog
      setState(() {
        _isDarkMode = value;
      });
      
      Navigator.of(context).pop(); // Close loading dialog
      
      // Show iOS-style activation prompt
      _showIOSStyleKeyboardActivationDialog();
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Dark mode ${value ? 'enabled' : 'disabled'} successfully'))
      );
    } catch (e) {
      // Close the loading dialog if there's an error
      Navigator.of(context, rootNavigator: true).pop();
      
      print('Error toggling dark mode: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to update dark mode')),
      );
    }
  }
  
  void _toggleHapticFeedback(bool value) async {
    try {
      await RewordiumKeyboardService.setHapticFeedback(value);
      await RewordiumKeyboardService.refreshKeyboard();
      setState(() {
        _isHapticFeedbackEnabled = value;
      });
    } catch (e) {
      print('Error toggling haptic feedback: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to update haptic feedback setting')),
      );
    }
  }
  
  void _toggleAutoCapitalize(bool value) async {
    try {
      await RewordiumKeyboardService.setAutoCapitalize(value);
      await RewordiumKeyboardService.refreshKeyboard();
      setState(() {
        _isAutoCapitalizeEnabled = value;
      });
    } catch (e) {
      print('Error toggling auto-capitalize: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to update auto-capitalize setting')),
      );
    }
  }
  
  void _toggleDoubleSpacePeriod(bool value) async {
    try {
      await RewordiumKeyboardService.setDoubleSpacePeriod(value);
      await RewordiumKeyboardService.refreshKeyboard();
      setState(() {
        _isDoubleSpacePeriodEnabled = value;
      });
    } catch (e) {
      print('Error toggling double-space period: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to update double-space period setting')),
      );
    }
  }
  
  void _showColorPicker() {
    Color pickerColor = Color(int.parse(_themeColor.substring(1, 7), radix: 16) + 0xFF000000);
    
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Pick a color'),
          content: SingleChildScrollView(
            child: ColorPicker(
              pickerColor: pickerColor,
              onColorChanged: (Color color) {
                pickerColor = color;
              },
              pickerAreaHeightPercent: 0.8,
              enableAlpha: false,
              displayThumbColor: true,
              showLabel: true,
            ),
          ),
          actions: <Widget>[
            TextButton(
              child: const Text('Cancel'),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            TextButton(
              child: const Text('Apply'),
              onPressed: () {
                final hexColor = '#${pickerColor.value.toRadixString(16).substring(2, 8)}';
                _updateThemeColor(hexColor);
                Navigator.of(context).pop();
              },
            ),
          ],
        );
      },
    );
  }
  
  // Show iOS-style dialog for keyboard reactivation
  void _showIOSStyleKeyboardActivationDialog() {
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
            'To apply your theme changes, please reactivate your keyboard.',
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
    if (_isLoading) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Keyboard Settings'),
        ),
        body: const Center(
          child: CircularProgressIndicator(),
        ),
      );
    }
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('Keyboard Settings'),
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Keyboard Preview
              _isLoading
                ? const Center(child: CircularProgressIndicator())
                : Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16.0),
                    child: KeyboardPreview(
                      themeColor: _themeColor,
                      isDarkMode: _isDarkMode,
                    ),
                  ),
              
              // Status card
              _isLoading
                ? const Center(child: CircularProgressIndicator())
                : Card(
                    elevation: 2,
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 12,
                                height: 12,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: _isKeyboardEnabled
                                      ? Colors.green
                                      : Colors.red,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                _isKeyboardEnabled
                                    ? 'Keyboard is enabled'
                                    : 'Keyboard is disabled',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: _openKeyboardSettings,
                            child: Text(_isKeyboardEnabled
                                ? 'Open Keyboard Settings'
                                : 'Enable Keyboard'),
                          ),
                        ],
                      ),
                    ),
                  ),
              const SizedBox(height: 24),
              const Text(
                'Appearance',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              
              // Theme color card with reset button
Card(
  elevation: 2,
  child: ListTile(
    title: const Text('Theme Color'),
    subtitle: const Text('Change the accent of return key according to your flavour'),
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Reset button (only visible when not default color)
        if (_themeColor != '#007AFF')
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.blue),
            tooltip: 'Reset to default',
            onPressed: () {
              _updateThemeColor('#007AFF');
            },
          ),
        // Color indicator
        GestureDetector(
          onTap: _showColorPicker,
          child: Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(left: 8),
            decoration: BoxDecoration(
              color: Color(int.parse(_themeColor.substring(1, 7), radix: 16) + 0xFF000000),
              shape: BoxShape.circle,
              border: Border.all(color: Colors.grey),
            ),
          ),
        ),
      ],
    ),
    onTap: _showColorPicker,
  ),
),
              
              // Dark mode
              Card(
                elevation: 2,
                child: SwitchListTile(
                  title: const Text('Dark Mode'),
                  subtitle: const Text('Use dark theme for keyboard'),
                  value: _isDarkMode,
                  onChanged: _toggleDarkMode,
                ),
              ),
              
              const SizedBox(height: 24),
              const Text(
                'Behavior',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              
              // Haptic feedback
              Card(
                elevation: 2,
                child: SwitchListTile(
                  title: const Text('Haptic Feedback'),
                  subtitle: const Text('Vibrate when keys are pressed'),
                  value: _isHapticFeedbackEnabled,
                  onChanged: _toggleHapticFeedback,
                ),
              ),
              
              // Auto-capitalize
              Card(
                elevation: 2,
                child: SwitchListTile(
                  title: const Text('Auto-Capitalize'),
                  subtitle: const Text('Automatically capitalize first word in sentences'),
                  value: _isAutoCapitalizeEnabled,
                  onChanged: _toggleAutoCapitalize,
                ),
              ),
              
              // Double-space period
              Card(
                elevation: 2,
                child: SwitchListTile(
                  title: const Text('Double-Space for Period'),
                  subtitle: const Text('Insert period when space is tapped twice'),
                  value: _isDoubleSpacePeriodEnabled,
                  onChanged: _toggleDoubleSpacePeriod,
                ),
              ),
              
              const SizedBox(height: 24),
              const Text(
                'About',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              
              // About card
              Card(
                elevation: 2,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Rewordium AI Keyboard',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'A professional iOS-styled keyboard with AI-powered features. Customize the appearance and behavior to match your preferences.',
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Version: 1.0.0',
                        style: TextStyle(color: Colors.grey),
                      ),
                    ],
                  ),
                ),
              ),
              
              // Add autocorrect toggle
              Card(
                elevation: 2,
                child: SwitchListTile(
                  title: const Text('Autocorrect'),
                  subtitle: const Text('Automatically correct misspelled words'),
                  value: _isAutocorrectEnabled,
                  onChanged: (value) {
                    setState(() {
                      _isAutocorrectEnabled = value;
                    });
                    // Save the setting
                    _saveSettings();
                  },
                ),
              ),
              Card(
                elevation: 2,
                child : // Add a toggle for autocorrect in your settings screen
                    SwitchListTile(
                      title: Text('Autocorrect'),
                      subtitle: Text('Automatically correct misspelled words'),
                      value: _isAutocorrectEnabled,
                      onChanged: (value) {
                        setState(() {
                          _isAutocorrectEnabled = value;
                        });
                        // Save the setting
                        _saveSettings();
                      },
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
  
  Future<void> _saveSettings() async {
    try {
      // Use individual setting methods instead of a non-existent saveKeyboardSettings method
      await RewordiumKeyboardService.updateThemeColor(_themeColor);
      await RewordiumKeyboardService.setDarkMode(_isDarkMode);
      await RewordiumKeyboardService.setHapticFeedback(_isHapticFeedbackEnabled);
      await RewordiumKeyboardService.setAutoCapitalize(_isAutoCapitalizeEnabled);
      await RewordiumKeyboardService.setDoubleSpacePeriod(_isDoubleSpacePeriodEnabled);
      
      // For autocorrect, you'll need to add a new method to RewordiumKeyboardService
      // For now, we'll just log that it's not implemented yet
      print('Note: Autocorrect setting is not yet implemented in the native service');
      
      // Refresh the keyboard to apply changes
      await RewordiumKeyboardService.refreshKeyboard();
    } catch (e) {
      print('Error saving keyboard settings: $e');
    }
  }

}


