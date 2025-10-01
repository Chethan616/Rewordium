import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:ui';
import '../../services/rewordium_keyboard_service.dart';
import '../../services/swipe_gesture_service.dart';

class KeyboardSettingsPopup extends StatefulWidget {
  final bool isDarkMode;
  final VoidCallback onClose;

  const KeyboardSettingsPopup({
    Key? key,
    required this.isDarkMode,
    required this.onClose,
  }) : super(key: key);

  @override
  _KeyboardSettingsPopupState createState() => _KeyboardSettingsPopupState();
}

class _KeyboardSettingsPopupState extends State<KeyboardSettingsPopup>
    with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  late Animation<double> _scaleAnimation;
  late Animation<double> _opacityAnimation;

  // Settings
  bool _isHapticFeedbackEnabled = true;
  bool _isAutoCapitalizeEnabled = true;
  bool _isDoubleSpacePeriodEnabled = true;
  bool _isAutocorrectEnabled = true;
  bool _swipeGesturesEnabled = true; // Enable by default
  double _swipeSensitivity = 0.8;
  bool _deleteSwipeEnabled = true;
  bool _spaceSwipeEnabled = true;
  bool _directionalSwipeEnabled = true;

  @override
  void initState() {
    super.initState();
    _setupAnimation();
    _loadSettings();
  }

  void _setupAnimation() {
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );

    _scaleAnimation = Tween<double>(
      begin: 0.8,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeOutBack,
    ));

    _opacityAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeOut,
    ));

    _animationController.forward();
  }

  Future<void> _loadSettings() async {
    try {
      final settings = await RewordiumKeyboardService.getKeyboardSettings();
      final prefs = await SharedPreferences.getInstance();

      setState(() {
        _isHapticFeedbackEnabled = settings['hapticFeedback'] ?? true;
        _isAutoCapitalizeEnabled = settings['autoCapitalize'] ?? true;
        _isDoubleSpacePeriodEnabled = settings['doubleSpacePeriod'] ?? true;
        _isAutocorrectEnabled = settings['autocorrect'] ?? true;

        // Load swipe gesture settings (enable by default)
        _swipeGesturesEnabled = prefs.getBool('swipe_gestures_enabled') ?? true;
        _swipeSensitivity = prefs.getDouble('swipe_sensitivity') ?? 0.8;
        _deleteSwipeEnabled = prefs.getBool('delete_swipe_enabled') ?? true;
        _spaceSwipeEnabled = prefs.getBool('space_swipe_enabled') ?? true;
        _directionalSwipeEnabled =
            prefs.getBool('directional_swipe_enabled') ?? true;
      });
    } catch (e) {
      print('Error loading keyboard settings: $e');
    }
  }

  void _close() async {
    await _animationController.reverse();
    widget.onClose();
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
    }
  }

  void _toggleSwipeGestures(bool value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('swipe_gestures_enabled', value);

      if (value) {
        final initialized = await SwipeGestureService.initialize();
        if (!initialized) {
          await Future.delayed(Duration(milliseconds: 500));
          await SwipeGestureService.initialize();
        }

        await SwipeGestureService.setSwipeGesturesEnabled(value);
        await SwipeGestureService.setSwipeSensitivity(_swipeSensitivity);
        await SwipeGestureService.configureSpecialGestures(
          spaceDeleteEnabled: _deleteSwipeEnabled,
          cursorMovementEnabled: _spaceSwipeEnabled,
          capsToggleEnabled: _directionalSwipeEnabled,
          symbolModeEnabled: true,
        );
      } else {
        await SwipeGestureService.setSwipeGesturesEnabled(value);
      }

      setState(() {
        _swipeGesturesEnabled = value;
      });
    } catch (e) {
      print('Error toggling swipe gestures: $e');
    }
  }

  void _updateSwipeSensitivity(double value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble('swipe_sensitivity', value);

      if (_swipeGesturesEnabled) {
        await SwipeGestureService.setSwipeSensitivity(value);
      }

      setState(() {
        _swipeSensitivity = value;
      });
    } catch (e) {
      print('Error updating swipe sensitivity: $e');
    }
  }

  void _toggleSpecialGesture(String gestureType, bool value) async {
    try {
      final prefs = await SharedPreferences.getInstance();

      switch (gestureType) {
        case 'delete':
          await prefs.setBool('delete_swipe_enabled', value);
          _deleteSwipeEnabled = value;
          break;
        case 'space':
          await prefs.setBool('space_swipe_enabled', value);
          _spaceSwipeEnabled = value;
          break;
        case 'directional':
          await prefs.setBool('directional_swipe_enabled', value);
          _directionalSwipeEnabled = value;
          break;
      }

      if (_swipeGesturesEnabled) {
        await SwipeGestureService.configureSpecialGestures(
          spaceDeleteEnabled: _deleteSwipeEnabled,
          cursorMovementEnabled: _spaceSwipeEnabled,
          capsToggleEnabled: _directionalSwipeEnabled,
          symbolModeEnabled: true,
        );
      }

      setState(() {});
    } catch (e) {
      print('Error toggling special gesture: $e');
    }
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animationController,
      builder: (context, child) {
        return Opacity(
          opacity: _opacityAnimation.value,
          child: Transform.scale(
            scale: _scaleAnimation.value,
            child: child,
          ),
        );
      },
      child: Material(
        color: Colors.transparent,
        child: Container(
          width: 320,
          height: 480,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: widget.isDarkMode
                ? Colors.black.withOpacity(0.85)
                : Colors.white.withOpacity(0.95),
            border: Border.all(
              color: widget.isDarkMode
                  ? Colors.white.withOpacity(0.2)
                  : Colors.black.withOpacity(0.1),
              width: 0.5,
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
              child: Column(
                children: [
                  // Header
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 12),
                    decoration: BoxDecoration(
                      border: Border(
                        bottom: BorderSide(
                          color: widget.isDarkMode
                              ? Colors.white.withOpacity(0.1)
                              : Colors.black.withOpacity(0.05),
                          width: 0.5,
                        ),
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          CupertinoIcons.gear_alt_fill,
                          color:
                              widget.isDarkMode ? Colors.white : Colors.black87,
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Keyboard Settings',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            color: widget.isDarkMode
                                ? Colors.white
                                : Colors.black87,
                          ),
                        ),
                        const Spacer(),
                        GestureDetector(
                          onTap: _close,
                          child: Container(
                            padding: const EdgeInsets.all(4),
                            decoration: BoxDecoration(
                              color: widget.isDarkMode
                                  ? Colors.white.withOpacity(0.1)
                                  : Colors.black.withOpacity(0.05),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Icon(
                              CupertinoIcons.xmark,
                              color: widget.isDarkMode
                                  ? Colors.white70
                                  : Colors.black54,
                              size: 16,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // Content
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // Behavior Section
                          _buildSectionHeader('Behavior'),
                          const SizedBox(height: 8),

                          _buildSettingTile(
                            icon: CupertinoIcons.device_phone_portrait,
                            title: 'Haptic Feedback',
                            subtitle: 'Vibrate when keys are pressed',
                            value: _isHapticFeedbackEnabled,
                            onChanged: _toggleHapticFeedback,
                          ),

                          _buildSettingTile(
                            icon: CupertinoIcons.textformat_abc,
                            title: 'Auto-Capitalize',
                            subtitle: 'Capitalize first word in sentences',
                            value: _isAutoCapitalizeEnabled,
                            onChanged: _toggleAutoCapitalize,
                          ),

                          _buildSettingTile(
                            icon: CupertinoIcons.circle_fill,
                            title: 'Double-Space for Period',
                            subtitle:
                                'Insert period when space is tapped twice',
                            value: _isDoubleSpacePeriodEnabled,
                            onChanged: _toggleDoubleSpacePeriod,
                          ),

                          _buildSettingTile(
                            icon: CupertinoIcons.checkmark_seal,
                            title: 'Autocorrect',
                            subtitle: 'Automatically correct misspelled words',
                            value: _isAutocorrectEnabled,
                            onChanged: (value) {
                              setState(() {
                                _isAutocorrectEnabled = value;
                              });
                            },
                          ),

                          const SizedBox(height: 24),

                          // Swipe Gestures Section
                          _buildSectionHeader('Swipe Gestures'),
                          const SizedBox(height: 8),

                          _buildSettingTile(
                            icon: CupertinoIcons.hand_draw,
                            title: 'Enable Swipe Gestures',
                            subtitle: 'Allow swipe typing and shortcuts',
                            value: _swipeGesturesEnabled,
                            onChanged: _toggleSwipeGestures,
                          ),

                          if (_swipeGesturesEnabled) ...[
                            const SizedBox(height: 16),

                            // Sensitivity Slider
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: widget.isDarkMode
                                    ? Colors.white.withOpacity(0.05)
                                    : Colors.black.withOpacity(0.03),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Sensitivity',
                                    style: TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w500,
                                      color: widget.isDarkMode
                                          ? Colors.white
                                          : Colors.black87,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  SliderTheme(
                                    data: SliderTheme.of(context).copyWith(
                                      trackHeight: 2,
                                      thumbShape: const RoundSliderThumbShape(
                                          enabledThumbRadius: 8),
                                      overlayShape:
                                          const RoundSliderOverlayShape(
                                              overlayRadius: 16),
                                      activeTrackColor: const Color(0xFF007AFF),
                                      inactiveTrackColor: widget.isDarkMode
                                          ? Colors.white.withOpacity(0.3)
                                          : Colors.black.withOpacity(0.2),
                                      thumbColor: const Color(0xFF007AFF),
                                    ),
                                    child: Slider(
                                      value: _swipeSensitivity,
                                      min: 0.1,
                                      max: 1.0,
                                      divisions: 9,
                                      onChanged: _updateSwipeSensitivity,
                                    ),
                                  ),
                                  Text(
                                    '${(_swipeSensitivity * 100).round()}% - Higher = faster recognition',
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: widget.isDarkMode
                                          ? Colors.white60
                                          : Colors.black45,
                                    ),
                                  ),
                                ],
                              ),
                            ),

                            const SizedBox(height: 8),

                            _buildSettingTile(
                              icon: CupertinoIcons.delete_left,
                              title: 'Space & Delete Swipes',
                              subtitle:
                                  'Swipe left on backspace, right on spacebar',
                              value: _deleteSwipeEnabled,
                              onChanged: (value) =>
                                  _toggleSpecialGesture('delete', value),
                            ),

                            _buildSettingTile(
                              icon: CupertinoIcons.cursor_rays,
                              title: 'Cursor Movement',
                              subtitle: 'Swipe to move text cursor',
                              value: _spaceSwipeEnabled,
                              onChanged: (value) =>
                                  _toggleSpecialGesture('space', value),
                            ),

                            _buildSettingTile(
                              icon: CupertinoIcons.arrow_up_down_square,
                              title: 'Directional Controls',
                              subtitle:
                                  'Advanced directional gesture shortcuts',
                              value: _directionalSwipeEnabled,
                              onChanged: (value) =>
                                  _toggleSpecialGesture('directional', value),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: widget.isDarkMode ? Colors.white : Colors.black87,
      ),
    );
  }

  Widget _buildSettingTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: widget.isDarkMode
            ? Colors.white.withOpacity(0.05)
            : Colors.black.withOpacity(0.03),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => onChanged(!value),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: widget.isDarkMode
                        ? Colors.white.withOpacity(0.1)
                        : Colors.black.withOpacity(0.06),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    icon,
                    size: 16,
                    color: widget.isDarkMode ? Colors.white70 : Colors.black54,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                          color:
                              widget.isDarkMode ? Colors.white : Colors.black87,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        subtitle,
                        style: TextStyle(
                          fontSize: 12,
                          color: widget.isDarkMode
                              ? Colors.white60
                              : Colors.black54,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                CupertinoSwitch(
                  value: value,
                  onChanged: onChanged,
                  activeColor: const Color(0xFF007AFF),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
