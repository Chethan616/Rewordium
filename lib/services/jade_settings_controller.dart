import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../theme/theme_provider.dart';
import '../services/rewordium_keyboard_service.dart';

class JadeSettingsController {
  // Advanced natural language patterns for comprehensive AI understanding
  static final Map<String, RegExp> _naturalLanguagePatterns = {
    // Theme patterns - comprehensive natural language understanding
    'dark_mode_on': RegExp(
        r'\b(dark|night|black|dim)\s*(mode|theme|ui|interface|appearance)?\b|\b(enable|turn on|switch to|go to|activate|set|make it|change to)\s*(dark|night|black|dim|darker)\b|\b(i want|can you|please)\s*.*(dark|night|black)\b',
        caseSensitive: false),
    'light_mode_on': RegExp(
        r'\b(light|day|white|bright|clean)\s*(mode|theme|ui|interface|appearance)?\b|\b(enable|turn on|switch to|go to|activate|set|make it|change to)\s*(light|day|bright|white|brighter|lighter)\b|\b(i want|can you|please)\s*.*(light|bright|white)\b',
        caseSensitive: false),
    'theme_switch': RegExp(
        r'\b(switch|change|toggle|flip|turn)\s*(to|on|off)?\s*(theme|mode|appearance)\b',
        caseSensitive: false),

    // Haptic feedback patterns with natural variations
    'haptic_on': RegExp(
        r'\b(enable|turn on|activate|start|allow)\s*(haptic|vibration|feedback|buzz|vibrate|tactile)\b|\b(haptic|vibration|feedback|vibrate)\s*(on|enabled|please)\b|\b(i want|make it|let it)\s*vibrate\b|\bvibrate\s*(when|on|please)\b',
        caseSensitive: false),
    'haptic_off': RegExp(
        r'\b(disable|turn off|deactivate|stop|prevent)\s*(haptic|vibration|feedback|buzz|vibrate|tactile)\b|\b(haptic|vibration|feedback|vibrate)\s*(off|disabled)\b|\b(stop|no|dont)\s*vibrat\w+\b|\bno\s*(haptic|vibration)\b',
        caseSensitive: false),

    // Notification patterns with contextual understanding
    'notifications_on': RegExp(
        r'\b(enable|turn on|activate|allow|start|show)\s*(notifications?|alerts?|messages?|pings?)\b|\b(notifications?|alerts?)\s*(on|enabled|please)\b|\b(notify|alert|ping)\s*me\b|\b(i want|show me)\s*(notifications?|alerts?)\b',
        caseSensitive: false),
    'notifications_off': RegExp(
        r'\b(disable|turn off|deactivate|block|silence|mute|stop|hide)\s*(notifications?|alerts?|messages?|pings?)\b|\b(notifications?|alerts?)\s*(off|disabled)\b|\b(no|stop|dont)\s*(notifications?|alerts?|pings?)\b|\bsilence\s*(all|everything)\b',
        caseSensitive: false),

    // Text size patterns with natural expressions
    'text_bigger': RegExp(
        r'\b(increase|bigger|larger|make.*big|enlarge|boost|up|raise|grow)\s*(text|font|size|letters)?\b|\b(text|font|letters)\s*(bigger|larger|size.*up)\b|\b(i cant see|too small|make.*readable)\b|\bbigger\s*(text|font|letters)?\b',
        caseSensitive: false),
    'text_smaller': RegExp(
        r'\b(decrease|smaller|reduce|make.*small|shrink|down|lower|tiny)\s*(text|font|size|letters)?\b|\b(text|font|letters)\s*(smaller|size.*down|tiny)\b|\b(too big|too large)\s*(text|font)?\b|\bsmaller\s*(text|font|letters)?\b',
        caseSensitive: false),

    // Keyboard patterns
    'keyboard_settings': RegExp(
        r'\b(open|show|access|go to|navigate to|take me to)\s*(keyboard|input|typing)\s*(settings?|options?|preferences?|config|panel)\b|\bkeyboard\s*(settings?|options?|config)\b|\b(settings?|options?)\s*for\s*keyboard\b',
        caseSensitive: false),
    'auto_correct_on': RegExp(
        r'\b(enable|turn on|activate|start|allow)\s*(auto.*correct|spell.*check|correction|autocorrect)\b|\b(auto.*correct|autocorrect|spell.*check)\s*(on|enabled)\b|\b(fix|correct)\s*my\s*(spelling|typing)\b',
        caseSensitive: false),
    'auto_correct_off': RegExp(
        r'\b(disable|turn off|deactivate|stop|prevent)\s*(auto.*correct|spell.*check|correction|autocorrect)\b|\b(auto.*correct|autocorrect|spell.*check)\s*(off|disabled)\b|\b(no|dont|stop)\s*(auto.*correct|correction)\b',
        caseSensitive: false),

    // Sound patterns
    'sound_on': RegExp(
        r'\b(enable|turn on|activate|unmute|start|allow)\s*(sound|audio|beep|click|tone)\s*(feedback|effects?)?\b|\b(sound|audio|beep)\s*(on|enabled)\b|\b(i want|make it|let it)\s*(beep|sound|click)\b|\bunmute\b',
        caseSensitive: false),
    'sound_off': RegExp(
        r'\b(disable|turn off|deactivate|mute|silence|stop|quiet)\s*(sound|audio|beep|click|tone)\s*(feedback|effects?)?\b|\b(sound|audio|beep)\s*(off|disabled)\b|\b(no|dont|stop)\s*(sound|beep|click)\b|\b(mute|silence)\s*(all|everything)?\b',
        caseSensitive: false),
  };

  // Enhanced action handlers for better UX
  static Future<String> _handleTheme(
      BuildContext context, String message) async {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);

    // Determine intent from natural language
    bool wantsDark =
        _naturalLanguagePatterns['dark_mode_on']!.hasMatch(message);
    bool wantsLight =
        _naturalLanguagePatterns['light_mode_on']!.hasMatch(message);

    if (wantsDark && !themeProvider.isDarkMode) {
      themeProvider.toggleTheme();
      HapticFeedback.selectionClick();
      return "🌙 Dark mode activated! Perfect for nighttime use. Your eyes will thank you! ✨";
    } else if (wantsLight && themeProvider.isDarkMode) {
      themeProvider.toggleTheme();
      HapticFeedback.selectionClick();
      return "☀️ Light mode activated! Bright, clean, and perfect for daytime use! 🌟";
    } else if (wantsDark && themeProvider.isDarkMode) {
      return "🌙 You're already in dark mode! Looking good! ✨";
    } else if (wantsLight && !themeProvider.isDarkMode) {
      return "☀️ You're already in light mode! Bright and beautiful! 🌟";
    } else {
      // General toggle
      themeProvider.toggleTheme();
      HapticFeedback.selectionClick();
      return "🎨 Theme switched to ${themeProvider.isDarkMode ? 'dark' : 'light'} mode! Perfect! ✨";
    }
  }

  static Future<String> _handleHapticFeedback(
      BuildContext context, String message) async {
    final prefs = await SharedPreferences.getInstance();
    final currentValue = prefs.getBool('haptic_feedback') ?? true;

    bool wantsOn = _naturalLanguagePatterns['haptic_on']!.hasMatch(message);
    bool wantsOff = _naturalLanguagePatterns['haptic_off']!.hasMatch(message);

    bool newValue = wantsOn
        ? true
        : wantsOff
            ? false
            : !currentValue;

    await prefs.setBool('haptic_feedback', newValue);

    if (newValue) {
      HapticFeedback.mediumImpact();
      return "📳 Haptic feedback enabled! You'll feel those satisfying vibrations now! ✨";
    } else {
      return "🔇 Haptic feedback disabled. No more vibrations for a quieter experience! 😌";
    }
  }

  static Future<String> _handleNotifications(
      BuildContext context, String message) async {
    final prefs = await SharedPreferences.getInstance();
    final currentValue = prefs.getBool('notifications_enabled') ?? true;

    bool wantsOn =
        _naturalLanguagePatterns['notifications_on']!.hasMatch(message);
    bool wantsOff =
        _naturalLanguagePatterns['notifications_off']!.hasMatch(message);

    bool newValue = wantsOn
        ? true
        : wantsOff
            ? false
            : !currentValue;

    await prefs.setBool('notifications_enabled', newValue);

    if (newValue) {
      return "🔔 Notifications enabled! I'll keep you updated with important alerts! 📢";
    } else {
      return "🔕 Notifications disabled. Enjoy the peaceful, distraction-free experience! 😴";
    }
  }

  static Future<String> _handleTextSize(
      BuildContext context, String message) async {
    final prefs = await SharedPreferences.getInstance();
    double currentSize = prefs.getDouble('text_size') ?? 16.0;

    bool wantsBigger =
        _naturalLanguagePatterns['text_bigger']!.hasMatch(message);
    bool wantsSmaller =
        _naturalLanguagePatterns['text_smaller']!.hasMatch(message);

    if (wantsBigger) {
      currentSize = (currentSize + 2).clamp(12.0, 24.0);
      await prefs.setDouble('text_size', currentSize);
      HapticFeedback.lightImpact();
      return "📝 Text size increased to ${currentSize}px! Much better readability! 👀✨";
    } else if (wantsSmaller) {
      currentSize = (currentSize - 2).clamp(12.0, 24.0);
      await prefs.setDouble('text_size', currentSize);
      HapticFeedback.lightImpact();
      return "📝 Text size decreased to ${currentSize}px! More compact and clean! 🎯";
    } else {
      return "📏 Current text size is ${currentSize}px. Say 'bigger text' or 'smaller text' to adjust! 📝";
    }
  }

  static Future<String> _handleSoundFeedback(
      BuildContext context, String message) async {
    final prefs = await SharedPreferences.getInstance();
    final currentValue = prefs.getBool('sound_feedback') ?? false;

    bool wantsOn = _naturalLanguagePatterns['sound_on']!.hasMatch(message);
    bool wantsOff = _naturalLanguagePatterns['sound_off']!.hasMatch(message);

    bool newValue = wantsOn
        ? true
        : wantsOff
            ? false
            : !currentValue;

    await prefs.setBool('sound_feedback', newValue);

    if (newValue) {
      return "🔊 Sound feedback enabled! You'll hear satisfying clicks and beeps! 🎵";
    } else {
      return "🔇 Sound feedback disabled. Silent and smooth operation! 🤫";
    }
  }

  static Future<String> _handleAutoCorrection(
      BuildContext context, String message) async {
    final prefs = await SharedPreferences.getInstance();
    final currentValue = prefs.getBool('auto_correction') ?? true;

    bool wantsOn =
        _naturalLanguagePatterns['auto_correct_on']!.hasMatch(message);
    bool wantsOff =
        _naturalLanguagePatterns['auto_correct_off']!.hasMatch(message);

    bool newValue = wantsOn
        ? true
        : wantsOff
            ? false
            : !currentValue;

    await prefs.setBool('auto_correction', newValue);

    if (newValue) {
      return "✅ Auto-correction enabled! I'll help fix those typos automatically! 📝✨";
    } else {
      return "❌ Auto-correction disabled. You have full control over your typing! 🎯";
    }
  }

  static Future<String> _handleKeyboardSettings(
      BuildContext context, String message) async {
    try {
      if (defaultTargetPlatform != TargetPlatform.android) {
        return "⌨️ Keyboard settings are only available on Android! 📱";
      }
      await RewordiumKeyboardService.openKeyboardSettings();
      HapticFeedback.lightImpact();
      return "⌨️ Keyboard settings opened! Customize your typing experience! 🎛️✨";
    } catch (e) {
      return "🔧 Couldn't open keyboard settings automatically. Please go to Settings > Keyboard to customize manually! 📱";
    }
  }

  // Main intelligent command processor with advanced NLP
  static Future<String> processCommand(
      String message, BuildContext context) async {
    // First check if this is likely a question vs a command
    // Questions about settings should NOT trigger settings changes
    if (_isQuestionNotCommand(message)) {
      return ''; // Not a command, let AI handle it
    }

    // Enhanced pattern matching with priority order
    final patterns = [
      {
        'pattern': _naturalLanguagePatterns['dark_mode_on']!,
        'handler': _handleTheme
      },
      {
        'pattern': _naturalLanguagePatterns['light_mode_on']!,
        'handler': _handleTheme
      },
      {
        'pattern': _naturalLanguagePatterns['theme_switch']!,
        'handler': _handleTheme
      },
      {
        'pattern': _naturalLanguagePatterns['haptic_on']!,
        'handler': _handleHapticFeedback
      },
      {
        'pattern': _naturalLanguagePatterns['haptic_off']!,
        'handler': _handleHapticFeedback
      },
      {
        'pattern': _naturalLanguagePatterns['notifications_on']!,
        'handler': _handleNotifications
      },
      {
        'pattern': _naturalLanguagePatterns['notifications_off']!,
        'handler': _handleNotifications
      },
      {
        'pattern': _naturalLanguagePatterns['text_bigger']!,
        'handler': _handleTextSize
      },
      {
        'pattern': _naturalLanguagePatterns['text_smaller']!,
        'handler': _handleTextSize
      },
      {
        'pattern': _naturalLanguagePatterns['sound_on']!,
        'handler': _handleSoundFeedback
      },
      {
        'pattern': _naturalLanguagePatterns['sound_off']!,
        'handler': _handleSoundFeedback
      },
      {
        'pattern': _naturalLanguagePatterns['auto_correct_on']!,
        'handler': _handleAutoCorrection
      },
      {
        'pattern': _naturalLanguagePatterns['auto_correct_off']!,
        'handler': _handleAutoCorrection
      },
      {
        'pattern': _naturalLanguagePatterns['keyboard_settings']!,
        'handler': _handleKeyboardSettings
      },
    ];

    // Process with pattern matching
    for (final patternData in patterns) {
      final RegExp pattern = patternData['pattern'] as RegExp;
      if (pattern.hasMatch(message)) {
        try {
          final Function handler = patternData['handler'] as Function;
          return await handler(context, message);
        } catch (e) {
          return "⚠️ I encountered an issue while changing that setting. Please try again! 🔄";
        }
      }
    }

    // Fallback - check for basic keyword matching
    return await _fallbackKeywordMatching(context, message);
  }

  // Fallback keyword matching for edge cases
  static Future<String> _fallbackKeywordMatching(
      BuildContext context, String message) async {
    final lowerMessage = message.toLowerCase();

    // Check if it's a question first
    if (_isQuestionNotCommand(message)) {
      return ''; // Not a command
    }

    if (lowerMessage.contains('theme') ||
        lowerMessage.contains('mode') ||
        lowerMessage.contains('appearance')) {
      return await _handleTheme(context, message);
    } else if (lowerMessage.contains('vibrat') ||
        lowerMessage.contains('haptic') ||
        lowerMessage.contains('feedback')) {
      return await _handleHapticFeedback(context, message);
    } else if (lowerMessage.contains('notification') ||
        lowerMessage.contains('alert')) {
      return await _handleNotifications(context, message);
    } else if (lowerMessage.contains('text') &&
        (lowerMessage.contains('size') ||
            lowerMessage.contains('big') ||
            lowerMessage.contains('small'))) {
      return await _handleTextSize(context, message);
    } else if (lowerMessage.contains('sound') ||
        lowerMessage.contains('audio') ||
        lowerMessage.contains('beep')) {
      return await _handleSoundFeedback(context, message);
    } else if (lowerMessage.contains('correct') ||
        lowerMessage.contains('spell')) {
      return await _handleAutoCorrection(context, message);
    } else if (lowerMessage.contains('keyboard') &&
        lowerMessage.contains('setting')) {
      return await _handleKeyboardSettings(context, message);
    }

    return ''; // No settings command found
  }

  /// Determine if the message is a question about settings vs a command to change settings
  static bool _isQuestionNotCommand(String message) {
    final lowerMessage = message.toLowerCase().trim();

    // Question indicators
    final questionWords = [
      'what',
      'how',
      'why',
      'when',
      'where',
      'which',
      'who',
      'is',
      'are',
      'do',
      'does',
      'can',
      'could',
      'would',
      'should',
      'will',
      'tell me',
      'explain',
      'describe',
      'show me',
    ];

    // Check if message starts with question words or contains question patterns
    for (final word in questionWords) {
      if (lowerMessage.startsWith(word)) {
        return true; // It's a question
      }
    }

    // Check for question mark
    if (lowerMessage.contains('?')) {
      return true;
    }

    // Check for specific question patterns
    final questionPatterns = [
      RegExp(r'\b(what|which)\s+(is|are)\s+', caseSensitive: false),
      RegExp(r'\b(tell|show)\s+me\b', caseSensitive: false),
      RegExp(r'\bhow\s+(do|can|to)\b', caseSensitive: false),
      RegExp(r'\bwhat.*about\b', caseSensitive: false),
      RegExp(r'\bexplain\b', caseSensitive: false),
    ];

    for (final pattern in questionPatterns) {
      if (pattern.hasMatch(lowerMessage)) {
        return true;
      }
    }

    // Special case: If they're just mentioning "theme" without action verbs, it's likely a question
    final hasActionVerb = RegExp(
      r'\b(change|switch|turn|enable|disable|set|make|go to|activate|toggle)\b',
      caseSensitive: false,
    ).hasMatch(lowerMessage);

    final hasSettingsKeyword = RegExp(
      r'\b(theme|mode|dark|light|vibration|haptic|notification|sound)\b',
      caseSensitive: false,
    ).hasMatch(lowerMessage);

    if (hasSettingsKeyword && !hasActionVerb) {
      // They mentioned a setting but no action verb, likely a question
      return true;
    }

    return false; // It's a command
  }

  // Legacy method for backward compatibility
  static Future<String> handleSettingsCommand(
      BuildContext context, String message) async {
    return await processCommand(message, context);
  }

  // Enhanced help with examples
  static String getSettingsHelp() {
    return """
🎛️ **I Can Control These Settings With Natural Language:**

**🎨 Theme & Appearance:**
• "Switch to dark mode" / "Make it dark" / "Go to night mode"
• "Change to light mode" / "Make it bright" / "Switch to day mode"
• "Toggle theme" / "Flip the theme"

**📳 Haptic Feedback:**
• "Enable vibration" / "Turn on haptic feedback" / "Make it vibrate"
• "Disable vibration" / "Stop vibrations" / "No haptic feedback"

**🔔 Notifications:**
• "Enable notifications" / "Turn on alerts" / "Notify me"
• "Disable notifications" / "Silence alerts" / "No notifications"

**📝 Text & Font:**
• "Make text bigger" / "Increase font size" / "I can't see well"
• "Make text smaller" / "Decrease font size" / "Text is too big"

**🔊 Sound Effects:**
• "Enable sound feedback" / "Turn on audio" / "Make it beep"
• "Disable sound" / "Mute feedback" / "Silent mode"

**⌨️ Keyboard:**
• "Open keyboard settings" / "Show keyboard options"
• "Enable auto-correction" / "Fix my spelling"
• "Disable auto-correction" / "Stop correcting me"

**💡 Just speak naturally! I understand context and intent! ✨**
""";
  }
}
