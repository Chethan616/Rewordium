import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'dart:async';
import '../services/keyboard_service.dart' as service;
import '../services/rewordium_keyboard_service.dart';
import '../services/swipe_gesture_service.dart';

enum KeyboardLayout { oneplus, samsung, apple }

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

class Persona {
  final String name;
  final String description;

  Persona({required this.name, required this.description});

  factory Persona.fromJson(Map<String, dynamic> json) {
    return Persona(
      name: json['name'] as String,
      description: json['description'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'description': description,
    };
  }
}

class KeyboardProvider extends ChangeNotifier {
  // Show iOS-style dialog for keyboard reactivation
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
            'To apply your persona changes, please reactivate your keyboard.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 15),
          ),
          actionsAlignment: MainAxisAlignment.spaceEvenly,
          actionsPadding:
              const EdgeInsets.only(bottom: 12, left: 12, right: 12),
          actions: [
            // Cancel button
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
            // Reactivate button
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

  KeyboardLayout _layout = KeyboardLayout.oneplus;
  bool _soundOn = false;
  List<String> _suggestions = [];
  bool _isSystemKeyboardEnabled = false;
  bool _isParaphraserEnabled = false;

  // Timer for periodic keyboard status check
  Timer? _keyboardStatusCheckTimer;

  // Method to check keyboard status
  Future<void> checkKeyboardStatus() async {
    try {
      final bool isEnabled =
          await service.KeyboardService().isKeyboardEnabled();
      if (isEnabled != _isSystemKeyboardEnabled) {
        _isSystemKeyboardEnabled = isEnabled;
        notifyListeners();
      }
    } catch (e) {
      print('Error checking keyboard status: $e');
    }
  }

  // Start periodic status check
  void startKeyboardStatusCheck() {
    // Check immediately
    checkKeyboardStatus();

    // Then check every 5 seconds
    _keyboardStatusCheckTimer?.cancel();
    _keyboardStatusCheckTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      checkKeyboardStatus();
    });
  }

  // Stop checking status
  void stopKeyboardStatusCheck() {
    _keyboardStatusCheckTimer?.cancel();
    _keyboardStatusCheckTimer = null;
  }

  // Persona management
  List<Persona> _personas = [
    Persona(
        name: 'Happy',
        description:
            'Rewrite text in a cheerful, optimistic, and upbeat tone.'),
    Persona(
        name: 'Sad',
        description:
            'Rewrite text in a melancholic, somber, and reflective tone.'),
    Persona(
        name: 'Humor',
        description: 'Rewrite text in a witty, funny, and humorous tone.'),
    Persona(
        name: 'Formal',
        description: 'Rewrite text in a professional, business-like tone.'),
    Persona(
        name: 'Casual',
        description: 'Rewrite text in a relaxed, conversational tone.')
  ];
  String _activePersona = 'Happy';

  // Selected personas for keyboard (max 3)
  List<String> _selectedKeyboardPersonas = ['Happy', 'Sad', 'Humor'];

  // Instance of the keyboard service
  final service.KeyboardService _keyboardService = service.KeyboardService();

  // Swipe gesture settings
  bool _swipeGesturesEnabled = false;
  double _swipeSensitivity = 0.8;
  bool _deleteSwipeEnabled = true;
  bool _spaceSwipeEnabled = true;
  bool _directionalSwipeEnabled = true;
  Map<String, dynamic> _performanceMetrics = {};
  Timer? _performanceTimer;

  KeyboardLayout get layout => _layout;
  bool get soundOn => _soundOn;
  List<String> get suggestions => _suggestions;
  bool get isSystemKeyboardEnabled => _isSystemKeyboardEnabled;

  @override
  void dispose() {
    stopKeyboardStatusCheck();
    _performanceTimer?.cancel();
    super.dispose();
  }

  bool get isParaphraserEnabled => _isParaphraserEnabled;
  List<Persona> get personas => _personas;
  String get activePersona => _activePersona;
  List<String> get selectedKeyboardPersonas => _selectedKeyboardPersonas;

  // Swipe gesture getters
  bool get swipeGesturesEnabled => _swipeGesturesEnabled;
  double get swipeSensitivity => _swipeSensitivity;
  bool get deleteSwipeEnabled => _deleteSwipeEnabled;
  bool get spaceSwipeEnabled => _spaceSwipeEnabled;
  bool get directionalSwipeEnabled => _directionalSwipeEnabled;
  Map<String, dynamic> get performanceMetrics => _performanceMetrics;

  String layoutName(KeyboardLayout layout) {
    switch (layout) {
      case KeyboardLayout.oneplus:
        return 'OnePlus';
      case KeyboardLayout.samsung:
        return 'Samsung';
      case KeyboardLayout.apple:
        return 'Apple';
    }
  }

  // Initialize provider state from SharedPreferences and keyboard service
  Future<void> initializeFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();

    // Check if the keyboard is enabled in the system
    final isEnabled = await _keyboardService.isKeyboardEnabled();
    _isSystemKeyboardEnabled = isEnabled;
    await prefs.setBool('system_keyboard_enabled', isEnabled);

    print('Keyboard enabled status: $isEnabled');

    _isParaphraserEnabled = prefs.getBool('paraphraser_enabled') ?? false;

    // Start periodic keyboard status check
    _startKeyboardStatusCheck();

    // Get current layout from keyboard service if available
    try {
      final currentLayout = await _keyboardService.getCurrentLayout();
      // Convert between the two enum types (they have different case conventions)
      switch (currentLayout) {
        case service.KeyboardLayout.onePlus:
          _layout = KeyboardLayout.oneplus;
          break;
        case service.KeyboardLayout.samsung:
          _layout = KeyboardLayout.samsung;
          break;
        case service.KeyboardLayout.apple:
          _layout = KeyboardLayout.apple;
          break;
      }
    } catch (e) {
      // If service fails, use saved preferences
      final layoutIndex = prefs.getInt('keyboard_layout') ?? 0;
      _layout = KeyboardLayout.values[layoutIndex];
    }

    _soundOn = prefs.getBool('keyboard_sound') ?? false;

    // Load custom personas and active persona
    await _loadPersonas();

    // Load swipe gesture settings
    await _loadSwipeGestureSettings();

    // Apply settings to the keyboard service
    await _keyboardService.setHapticFeedback(_soundOn);
    // Note: setParaphraserButton method not implemented in service yet
    // await _keyboardService.setParaphraserButton(_isParaphraserEnabled);

    // Set the active persona in the keyboard service if enabled
    if (_isSystemKeyboardEnabled) {
      await _keyboardService.setPersona(_activePersona);
      await _keyboardService.setKeyboardPersonas(_selectedKeyboardPersonas);

      // Initialize swipe gestures if enabled
      if (_swipeGesturesEnabled) {
        await _initializeSwipeGestures();
      }
    }

    notifyListeners();
  }

  // Start periodic keyboard status check
  void _startKeyboardStatusCheck() {
    // Cancel any existing timer
    _keyboardStatusCheckTimer?.cancel();

    // Check keyboard status more frequently (every 2 seconds)
    _keyboardStatusCheckTimer =
        Timer.periodic(const Duration(seconds: 2), (_) async {
      await _checkKeyboardStatus();
    });

    // Also check immediately and force a check after a short delay
    _checkKeyboardStatus();

    // Force another check after a short delay to ensure we get the correct status
    Future.delayed(const Duration(milliseconds: 500), () {
      _checkKeyboardStatus();
    });
  }

  // Check keyboard status and update state if changed
  Future<void> _checkKeyboardStatus() async {
    try {
      // Try multiple times to get accurate keyboard status
      bool isEnabled = false;

      // First attempt
      isEnabled = await _keyboardService.isKeyboardEnabled();
      print('First keyboard status check: $isEnabled');

      // Wait a moment and check again to verify
      if (!isEnabled) {
        await Future.delayed(const Duration(milliseconds: 100));
        final secondCheck = await _keyboardService.isKeyboardEnabled();
        print('Second keyboard status check: $secondCheck');
        if (secondCheck) {
          isEnabled = true;
        }
      }

      // If keyboard is enabled in phone settings, always set status to true
      if (isEnabled) {
        print('Keyboard is enabled in phone settings, setting status to true');
        _isSystemKeyboardEnabled = true;

        // Save to preferences
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool('system_keyboard_enabled', true);

        // Apply all settings
        await applyAllKeyboardSettings();

        notifyListeners();

        // Log for debugging
        print('Successfully updated keyboard status to ENABLED');
      }
      // Only update if the status has changed and keyboard is disabled
      else if (isEnabled != _isSystemKeyboardEnabled) {
        print('Keyboard status changed: $isEnabled');
        _isSystemKeyboardEnabled = isEnabled;

        // Save to preferences
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool('system_keyboard_enabled', isEnabled);

        notifyListeners();

        // Log for debugging
        print('Successfully updated keyboard status to DISABLED');
      }
    } catch (e) {
      print('Error checking keyboard status: $e');
    }
  }

  // Apply all keyboard settings to the keyboard service
  Future<void> applyAllKeyboardSettings() async {
    try {
      // Convert layout enum to service enum
      service.KeyboardLayout serviceLayout;
      switch (_layout) {
        case KeyboardLayout.oneplus:
          serviceLayout = service.KeyboardLayout.onePlus;
          break;
        case KeyboardLayout.samsung:
          serviceLayout = service.KeyboardLayout.samsung;
          break;
        case KeyboardLayout.apple:
          serviceLayout = service.KeyboardLayout.apple;
          break;
      }

      await _keyboardService.setKeyboardLayout(serviceLayout);
      await _keyboardService.setHapticFeedback(_soundOn);
      // Note: setParaphraserButton method not implemented in service yet
      // await _keyboardService.setParaphraserButton(_isParaphraserEnabled);
      await _keyboardService.setPersona(_activePersona);
      await _keyboardService.setKeyboardPersonas(_selectedKeyboardPersonas);

      print(
          'Applied all keyboard settings: Layout=$_layout, Sound=$_soundOn, Personas=$_selectedKeyboardPersonas');
    } catch (e) {
      print('Error applying keyboard settings: $e');
    }
  }

  // Ensure keyboard is enabled and settings are applied
  Future<void> ensureKeyboardEnabled(BuildContext context) async {
    try {
      // Check if keyboard is enabled
      final isEnabled = await _keyboardService.isKeyboardEnabled();
      print('Keyboard enabled status on app start: $isEnabled');

      if (!isEnabled) {
        // Show a dialog to prompt the user to enable the keyboard
        if (context.mounted) {
          showDialog(
            context: context,
            barrierDismissible: false,
            builder: (context) => AlertDialog(
              title: const Text('Enable Keyboard'),
              content: const Text(
                  'To use all features of this app, please enable the Rewordium keyboard. '
                  'You will be redirected to keyboard settings.'),
              actions: [
                TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                  },
                  child: const Text('Cancel'),
                ),
                TextButton(
                  onPressed: () async {
                    Navigator.of(context).pop();

                    // Open keyboard settings
                    await _keyboardService.openKeyboardSettings();

                    // Check if keyboard was enabled after settings were closed
                    final nowEnabled =
                        await _keyboardService.isKeyboardEnabled();
                    if (nowEnabled && nowEnabled != _isSystemKeyboardEnabled) {
                      _isSystemKeyboardEnabled = nowEnabled;

                      // Save to preferences
                      final prefs = await SharedPreferences.getInstance();
                      await prefs.setBool(
                          'system_keyboard_enabled', nowEnabled);

                      // Apply all settings
                      await applyAllKeyboardSettings();

                      notifyListeners();
                    }
                  },
                  child: const Text('Enable'),
                ),
              ],
            ),
          );
        }
      } else if (isEnabled != _isSystemKeyboardEnabled) {
        // Update state if different from what we have
        _isSystemKeyboardEnabled = isEnabled;

        // Save to preferences
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool('system_keyboard_enabled', isEnabled);

        // Apply all settings
        await applyAllKeyboardSettings();

        notifyListeners();
      } else {
        // If already enabled and state is correct, just ensure settings are applied
        await applyAllKeyboardSettings();
      }
    } catch (e) {
      print('Error ensuring keyboard is enabled: $e');
    }
  }

  // Set the keyboard layout for both in-app and system-wide keyboard
  void setLayout(KeyboardLayout layout) async {
    _layout = layout;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('keyboard_layout', layout.index);

    // Convert between the two enum types (they have different case conventions)
    service.KeyboardLayout serviceLayout;
    switch (layout) {
      case KeyboardLayout.oneplus:
        serviceLayout = service.KeyboardLayout.onePlus;
        break;
      case KeyboardLayout.samsung:
        serviceLayout = service.KeyboardLayout.samsung;
        break;
      case KeyboardLayout.apple:
        serviceLayout = service.KeyboardLayout.apple;
        break;
    }

    // Update the system keyboard layout if enabled
    if (_isSystemKeyboardEnabled) {
      await _keyboardService.setKeyboardLayout(serviceLayout);
    }

    notifyListeners();

    if (navigatorKey.currentContext != null) {
      ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
        SnackBar(
            content: Text('Keyboard layout changed to ${layoutName(layout)}')),
      );
    }
  }

  void toggleSound() async {
    _soundOn = !_soundOn;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('keyboard_sound', _soundOn);

    // Update the system keyboard haptic feedback
    if (_isSystemKeyboardEnabled) {
      await _keyboardService.setHapticFeedback(_soundOn);
    }

    notifyListeners();
  }

  void setSound(bool value) async {
    _soundOn = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('keyboard_sound', _soundOn);

    // Update the system keyboard haptic feedback
    if (_isSystemKeyboardEnabled) {
      await _keyboardService.setHapticFeedback(_soundOn);
    }

    notifyListeners();
  }

  // Open keyboard settings regardless of current status
  Future<void> openKeyboardSettings() async {
    // Always open the keyboard settings
    await _keyboardService.openKeyboardSettings();

    // Check if keyboard status changed after settings were closed
    final isEnabled = await _keyboardService.isKeyboardEnabled();
    if (isEnabled != _isSystemKeyboardEnabled) {
      _isSystemKeyboardEnabled = isEnabled;

      // Save to preferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('system_keyboard_enabled', isEnabled);

      // If keyboard was just enabled, apply settings
      if (isEnabled) {
        await applyAllKeyboardSettings();
      }

      notifyListeners();
    }
  }

  // Toggle system keyboard
  void toggleSystemKeyboard() async {
    if (_isSystemKeyboardEnabled) {
      // If already enabled, do nothing
      return;
    }

    // Open keyboard settings
    await _keyboardService.openKeyboardSettings();

    // Check if keyboard was enabled after settings were closed
    final isEnabled = await _keyboardService.isKeyboardEnabled();
    if (isEnabled != _isSystemKeyboardEnabled) {
      _isSystemKeyboardEnabled = isEnabled;

      // Save to preferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('system_keyboard_enabled', isEnabled);

      // If keyboard was just enabled, apply settings
      if (isEnabled) {
        await applyAllKeyboardSettings();
      }

      notifyListeners();
    }

    if (navigatorKey.currentContext != null) {
      ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
        SnackBar(
            content: Text(_isSystemKeyboardEnabled
                ? 'System keyboard enabled'
                : 'System keyboard disabled')),
      );
    }
  }

  // Toggle paraphraser feature
  void toggleParaphraser() async {
    _isParaphraserEnabled = !_isParaphraserEnabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('paraphraser_enabled', _isParaphraserEnabled);

    // If paraphraser is disabled, clear all selected personas
    if (!_isParaphraserEnabled) {
      _selectedKeyboardPersonas = [];
      // Save empty persona list to preferences
      await prefs.setStringList('selected_keyboard_personas', []);
      // Update the keyboard service
      await _keyboardService.setKeyboardPersonas([]);
      print('Paraphraser disabled: Cleared all selected personas');
    }

    notifyListeners();
    if (navigatorKey.currentContext != null) {
      ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
        SnackBar(
            content: Text(_isParaphraserEnabled
                ? 'Paraphraser persona enabled'
                : 'Paraphraser persona disabled')),
      );
    }
  }

  // Show the system keyboard overlay
  void showSystemKeyboardOverlay(BuildContext context) {
    if (_isSystemKeyboardEnabled) {
      _keyboardService.showSystemKeyboardOverlay(context);
    }
  }

  // Hide the system keyboard overlay
  void hideSystemKeyboardOverlay() {
    if (_isSystemKeyboardEnabled) {
      _keyboardService.hideSystemKeyboardOverlay();
    }
  }

  // Check if the system keyboard overlay is visible
  bool get isSystemKeyboardOverlayVisible =>
      _isSystemKeyboardEnabled && _keyboardService.isOverlayVisible;

  // Update suggestions based on current text
  void updateSuggestions(String text) {
    if (text.isEmpty) {
      _suggestions = [];
    } else {
      // Simple suggestion algorithm - in a real app, this would be more sophisticated
      _suggestions = ['${text}ing', '${text}ed', '${text}s'];
    }

    notifyListeners();
  }

  // Clear suggestions
  void clearSuggestions() {
    _suggestions = [];
    notifyListeners();
  }

  // Add a new persona
  void addPersona(String name, String description) async {
    // Check if persona with this name already exists
    if (_personas.any((p) => p.name.toLowerCase() == name.toLowerCase())) {
      if (navigatorKey.currentContext != null) {
        ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
          SnackBar(content: Text('A persona with this name already exists')),
        );
      }
      return;
    }

    // Add the new persona
    final newPersona = Persona(name: name, description: description);
    _personas.add(newPersona);

    // Save to shared preferences
    await _savePersonas();

    // Set as active persona
    _activePersona = name;

    notifyListeners();

    if (navigatorKey.currentContext != null) {
      ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
        SnackBar(content: Text('Added and selected "$name" persona')),
      );
    }
  }

  // Set the active persona
  void setActivePersona(String personaName) async {
    // Check if persona exists
    if (_personas.any((p) => p.name == personaName)) {
      _activePersona = personaName;

      // Save to shared preferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('active_persona', personaName);

      // Update the keyboard service if enabled
      if (_isSystemKeyboardEnabled) {
        await _keyboardService.setPersona(personaName);
      }

      notifyListeners();

      if (navigatorKey.currentContext != null) {
        ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
          SnackBar(content: Text('Selected "$personaName" persona')),
        );
      }
    }
  }

  // Toggle a persona for the keyboard (max 3)
  void toggleKeyboardPersona(String personaName) async {
    // Check if persona exists
    if (!_personas.any((p) => p.name == personaName)) {
      return;
    }

    // If already selected, remove it (unless it's the last one)
    if (_selectedKeyboardPersonas.contains(personaName)) {
      if (_selectedKeyboardPersonas.length > 1) {
        _selectedKeyboardPersonas.remove(personaName);
      } else {
        // Don't allow removing the last persona
        if (navigatorKey.currentContext != null) {
          ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
            const SnackBar(
                content: Text('At least one persona must be selected')),
          );
        }
        return;
      }
    } else {
      // Add it if we have less than 3
      if (_selectedKeyboardPersonas.length < 3) {
        _selectedKeyboardPersonas.add(personaName);
      } else {
        // Replace the oldest one if we already have 3
        _selectedKeyboardPersonas.removeAt(0);
        _selectedKeyboardPersonas.add(personaName);

        if (navigatorKey.currentContext != null) {
          ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
            const SnackBar(
                content:
                    Text('Maximum 3 personas can be selected for keyboard')),
          );
        }
      }
    }

    // Print for debugging
    print('Selected keyboard personas: $_selectedKeyboardPersonas');

    // Save to shared preferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('keyboard_personas', _selectedKeyboardPersonas);

    // Always update the keyboard service regardless of whether it's enabled
    // This ensures the preferences are saved for when the keyboard is enabled
    try {
      final result =
          await _keyboardService.setKeyboardPersonas(_selectedKeyboardPersonas);
      print('Updated keyboard personas: $result');

      // Force a reload of the keyboard if it's enabled
      if (_isSystemKeyboardEnabled) {
        // Multiple aggressive refresh attempts
        await RewordiumKeyboardService.refreshKeyboard();
        await Future.delayed(const Duration(milliseconds: 300));
        await RewordiumKeyboardService.refreshKeyboard();
        await Future.delayed(const Duration(milliseconds: 300));
        await RewordiumKeyboardService.refreshKeyboard();

        // Show the iOS-style dialog if we have a context
        if (navigatorKey.currentContext != null) {
          _showIOSStyleKeyboardActivationDialog(navigatorKey.currentContext!);
        }
      }
    } catch (e) {
      print('Error updating keyboard personas: $e');
    }

    notifyListeners();
  }

  // Delete a persona
  void deletePersona(String personaName) async {
    // Don't allow deleting default personas
    if (['Happy', 'Sad', 'Humor'].contains(personaName)) {
      if (navigatorKey.currentContext != null) {
        ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
          const SnackBar(content: Text('Cannot delete default personas')),
        );
      }
      return;
    }

    // Remove the persona
    _personas.removeWhere((p) => p.name == personaName);

    // If the active persona was deleted, set to default
    if (_activePersona == personaName) {
      _activePersona = 'Happy';
    }

    // Save to shared preferences
    await _savePersonas();

    notifyListeners();

    if (navigatorKey.currentContext != null) {
      ScaffoldMessenger.of(navigatorKey.currentContext!).showSnackBar(
        SnackBar(content: Text('Deleted "$personaName" persona')),
      );
    }
  }

  // Save personas to shared preferences
  Future<void> _savePersonas() async {
    final prefs = await SharedPreferences.getInstance();

    // Only save custom personas (not the default ones)
    final customPersonas = _personas
        .where((p) => !['Happy', 'Sad', 'Humor'].contains(p.name))
        .toList();

    final jsonList = customPersonas.map((p) => p.toJson()).toList();
    final jsonString = jsonEncode(jsonList);

    await prefs.setString('custom_personas', jsonString);
  }

  // Load personas from shared preferences
  Future<void> _loadPersonas() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString('custom_personas');

    if (jsonString != null && jsonString.isNotEmpty) {
      try {
        final jsonList = jsonDecode(jsonString) as List;
        final customPersonas = jsonList
            .map((json) => Persona.fromJson(json as Map<String, dynamic>))
            .toList();

        // Add custom personas to the default ones
        _personas.addAll(customPersonas);
      } catch (e) {
        debugPrint('Error loading custom personas: $e');
      }
    }

    // Load active persona
    _activePersona = prefs.getString('active_persona') ?? 'Happy';

    // Load selected keyboard personas
    final keyboardPersonas = prefs.getStringList('keyboard_personas');
    if (keyboardPersonas != null && keyboardPersonas.isNotEmpty) {
      // Validate that the personas exist
      _selectedKeyboardPersonas = keyboardPersonas
          .where((name) => _personas.any((p) => p.name == name))
          .toList();

      // If we lost all of them, reset to defaults
      if (_selectedKeyboardPersonas.isEmpty) {
        _selectedKeyboardPersonas = ['Happy', 'Sad', 'Humor'];
      }

      // Ensure we have at most 3
      if (_selectedKeyboardPersonas.length > 3) {
        _selectedKeyboardPersonas = _selectedKeyboardPersonas.sublist(0, 3);
      }
    }
  }

  // Load swipe gesture settings from SharedPreferences
  Future<void> _loadSwipeGestureSettings() async {
    final prefs = await SharedPreferences.getInstance();

    _swipeGesturesEnabled = prefs.getBool('swipe_gestures_enabled') ?? false;
    _swipeSensitivity = prefs.getDouble('swipe_sensitivity') ?? 0.8;
    _deleteSwipeEnabled = prefs.getBool('delete_swipe_enabled') ?? true;
    _spaceSwipeEnabled = prefs.getBool('space_swipe_enabled') ?? true;
    _directionalSwipeEnabled =
        prefs.getBool('directional_swipe_enabled') ?? true;
  }

  // Initialize swipe gestures in the native Android service
  Future<void> _initializeSwipeGestures() async {
    try {
      await SwipeGestureService.initialize();
      await SwipeGestureService.setSwipeGesturesEnabled(_swipeGesturesEnabled);
      await SwipeGestureService.setSwipeSensitivity(_swipeSensitivity);

      // Configure special gestures
      await SwipeGestureService.configureSpecialGestures(
        spaceDeleteEnabled: _deleteSwipeEnabled,
        cursorMovementEnabled: _spaceSwipeEnabled,
        capsToggleEnabled: _directionalSwipeEnabled,
        symbolModeEnabled: true,
      );

      // Start performance monitoring
      _startPerformanceMonitoring();

      debugPrint('Swipe gestures initialized successfully');
    } catch (e) {
      debugPrint('Error initializing swipe gestures: $e');
    }
  }

  // Set swipe gestures enabled/disabled
  Future<void> setSwipeGesturesEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    _swipeGesturesEnabled = enabled;
    await prefs.setBool('swipe_gestures_enabled', enabled);

    try {
      await SwipeGestureService.setSwipeGesturesEnabled(enabled);
      if (enabled && _isSystemKeyboardEnabled) {
        await _initializeSwipeGestures();
      }
    } catch (e) {
      debugPrint('Error setting swipe gestures enabled: $e');
    }

    notifyListeners();
  }

  // Set swipe sensitivity
  Future<void> setSwipeSensitivity(double sensitivity) async {
    final prefs = await SharedPreferences.getInstance();
    _swipeSensitivity = sensitivity;
    await prefs.setDouble('swipe_sensitivity', sensitivity);

    try {
      if (_swipeGesturesEnabled) {
        await SwipeGestureService.setSwipeSensitivity(sensitivity);
      }
    } catch (e) {
      debugPrint('Error setting swipe sensitivity: $e');
    }

    notifyListeners();
  }

  // Set special gesture enabled/disabled
  Future<void> setSpecialGestureEnabled(String gesture, bool enabled) async {
    final prefs = await SharedPreferences.getInstance();

    switch (gesture) {
      case 'deleteSwipe':
        _deleteSwipeEnabled = enabled;
        await prefs.setBool('delete_swipe_enabled', enabled);
        break;
      case 'spaceSwipe':
        _spaceSwipeEnabled = enabled;
        await prefs.setBool('space_swipe_enabled', enabled);
        break;
      case 'directionalSwipe':
        _directionalSwipeEnabled = enabled;
        await prefs.setBool('directional_swipe_enabled', enabled);
        break;
    }

    try {
      if (_swipeGesturesEnabled) {
        await SwipeGestureService.configureSpecialGestures(
          spaceDeleteEnabled: _deleteSwipeEnabled,
          cursorMovementEnabled: _spaceSwipeEnabled,
          capsToggleEnabled: _directionalSwipeEnabled,
          symbolModeEnabled: true,
        );
      }
    } catch (e) {
      debugPrint('Error setting special gesture: $e');
    }

    notifyListeners();
  }

  // Start performance monitoring for swipe gestures
  void _startPerformanceMonitoring() {
    _performanceTimer?.cancel();
    _performanceTimer = Timer.periodic(const Duration(seconds: 5), (_) async {
      try {
        if (_swipeGesturesEnabled) {
          final metrics = await SwipeGestureService.getPerformanceMetrics();
          _performanceMetrics = metrics ?? {};
          notifyListeners();
        }
      } catch (e) {
        debugPrint('Error getting performance metrics: $e');
      }
    });
  }
}
