import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../services/ios_keyboard_bridge.dart';
import '../../utils/lottie_assets.dart';
import '../animated_card.dart';
import '../custom_button.dart';

/// Counterpart to [KeyboardStatusCard] for iOS users.
///
/// iOS doesn't let extensions report "I'm enabled" back to the host, so the
/// status here is advisory only. We treat the extension as "looks set up" if
/// either:
///   * the user has tapped "Open Settings" in the last 30 days, OR
///   * the extension has ever written `last_synced_at` into the App Group
///     (i.e. iOS has activated it at least once and the runtime read settings).
///
/// On non-iOS this widget is `SizedBox.shrink()` so it can sit safely alongside
/// the Android cards in the same column.
class IosKeyboardStatusCard extends StatefulWidget {
  const IosKeyboardStatusCard({super.key});

  @override
  State<IosKeyboardStatusCard> createState() => _IosKeyboardStatusCardState();
}

class _IosKeyboardStatusCardState extends State<IosKeyboardStatusCard>
    with WidgetsBindingObserver {
  static const _openSettingsTappedKey = 'ios_keyboard_open_settings_at';
  static const _aiEnabledPrefKey = 'ios_keyboard_ai_enabled_mirror';

  static bool get _isIOS => defaultTargetPlatform == TargetPlatform.iOS;

  bool _looksSetUp = false;
  bool _aiEnabled = true;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    if (!_isIOS) return;
    WidgetsBinding.instance.addObserver(this);
    _refreshStatus();
  }

  @override
  void dispose() {
    if (_isIOS) WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // When the app returns from iOS Settings, re-probe the status. The user
    // may have just enabled the keyboard.
    if (_isIOS && state == AppLifecycleState.resumed) {
      _refreshStatus();
    }
  }

  Future<void> _refreshStatus() async {
    final prefs = await SharedPreferences.getInstance();
    final tappedAtMs = prefs.getInt(_openSettingsTappedKey) ?? 0;
    final aiMirror = prefs.getBool(_aiEnabledPrefKey) ?? true;
    final read = await IosKeyboardBridge.readSettings();
    // `last_synced_at` lands in the App Group as a double (epoch seconds).
    final lastSynced = (read?['last_synced_at'] as num?)?.toDouble() ?? 0.0;
    final tappedRecently = tappedAtMs > 0 &&
        DateTime.fromMillisecondsSinceEpoch(tappedAtMs)
                .isAfter(DateTime.now().subtract(const Duration(days: 30)));
    if (!mounted) return;
    setState(() {
      _looksSetUp = tappedRecently || lastSynced > 0;
      _aiEnabled = aiMirror;
    });
  }

  Future<void> _openSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(
        _openSettingsTappedKey, DateTime.now().millisecondsSinceEpoch);
    // iOS 18 deprecated App-Prefs:root=Keyboards, so we land on app-settings.
    // The user finishes navigation in the Settings app — we can't deeplink
    // deeper than the host app's settings page on modern iOS.
    final candidates = [
      Uri.parse('App-Prefs:'),
      Uri.parse('app-settings:'),
    ];
    for (final uri in candidates) {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
        return;
      }
    }
  }

  Future<void> _toggleAi(bool value) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _aiEnabled = value;
    });
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_aiEnabledPrefKey, value);
    await IosKeyboardBridge.writeSettings(aiEnabled: value);
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    if (!_isIOS) return const SizedBox.shrink();

    final size = MediaQuery.of(context).size;
    final isSmall = size.width < 360;
    final scheme = Theme.of(context).colorScheme;

    return AnimatedCard(
      animationDelay: 300,
      padding: EdgeInsets.all(isSmall ? 12 : 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: _looksSetUp ? Colors.green : Colors.amber,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                'Keyboard Status',
                style: Theme.of(context)
                    .textTheme
                    .bodyLarge
                    ?.copyWith(fontWeight: FontWeight.w600),
              ),
              const Spacer(),
              Text(
                _looksSetUp ? 'Looks set up' : 'Not set up',
                style: TextStyle(
                  color: _looksSetUp ? Colors.green : Colors.amber,
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
                      _looksSetUp
                          ? 'Rewordium Keyboard ready'
                          : 'Set up Rewordium Keyboard',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _looksSetUp
                          ? 'Tap the globe key in any app to switch to it.'
                          : 'Open Settings → Keyboards → Add Rewordium.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 4),
              CustomButton(
                text: 'Open Settings',
                onPressed: _openSettings,
                width: isSmall ? 110 : 130,
                height: isSmall ? 40 : 48,
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
                      'AI rewrite shortcuts',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Show the sparkle toolbar above the keyboard.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 4),
              Switch(
                value: _aiEnabled,
                onChanged: _busy ? null : _toggleAi,
                activeColor: scheme.primary,
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
}
