import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:m3e_collection/m3e_collection.dart' hide Cubic;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../main.dart';
import '../models/advanced_ai_settings.dart';
import '../services/ai_settings_bridge.dart';
import '../services/ios_keyboard_bridge.dart';
import '../services/keyboard_service.dart';
import '../services/news_subscription_service.dart';
import '../services/rewordium_keyboard_service.dart';
import '../screens/accessibility_disclosure_screen.dart';
import '../theme/theme_provider.dart';
import '../providers/keyboard_provider.dart';
import '../widgets/rewordium_toast.dart';

// Strong custom easing curves (from easings.co / Emil Kowalski's design eng notes).
// Built-in Flutter curves are weaker than these — these have the punch that makes
// transitions feel intentional rather than mushy.
const Cubic _easeOut = Cubic(0.23, 1.0, 0.32, 1.0);

class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  static const String _legacyCompletionKey = 'onboarding_completed';
  static const String _versionedCompletionKey =
      'onboarding_completed_for_version';

  static Future<String> _currentBuildSignature() async {
    final info = await PackageInfo.fromPlatform();
    return '${info.version}+${info.buildNumber}';
  }

  static Future<bool> hasCompleted() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_legacyCompletionKey) ?? false;
  }

  static Future<void> markCompleted() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_legacyCompletionKey, true);
    try {
      final currentSignature = await _currentBuildSignature();
      await prefs.setString(_versionedCompletionKey, currentSignature);
    } catch (_) {
      // Persisting legacy completion still prevents lock-in if version lookup fails.
    }
  }

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

enum _AssistantMode { keyboardOnly, accessibilityOverlay, both }

enum _LlmMode { managedDefault, bringYourOwn }

enum _KeyboardHapticsMode { followSystem, alwaysVibrate }

enum _SystemSetupStage { idle, contacts, keyboardEnable, keyboardSelect, accessibility }

// Logical onboarding step ids. The active list depends on platform — the
// Android variant adds assistant-mode and keyboard steps that map to system
// services that don't exist on iOS yet.
enum _StepKey { welcome, assistantMode, theme, llm, news, keyboard }

class _OnboardingPageState extends State<OnboardingPage>
    with WidgetsBindingObserver {
  static const _accessibilityChannel =
      MethodChannel('com.noxquill.rewordium/accessibility');

  final KeyboardService _keyboardService = KeyboardService();
  final TextEditingController _apiKeyController = TextEditingController();
  final TextEditingController _modelController = TextEditingController();
  final TextEditingController _endpointController = TextEditingController();

  // Sentence-case throughout — feels less SaaS-formal.
  static const Map<_StepKey, String> _stepTitleByKey = {
    _StepKey.welcome: 'Welcome',
    _StepKey.assistantMode: 'Assistant mode',
    _StepKey.theme: 'Theme',
    _StepKey.llm: 'AI provider',
    _StepKey.news: 'News updates',
    _StepKey.keyboard: 'Keyboard',
  };

  static bool get _isAndroid =>
      defaultTargetPlatform == TargetPlatform.android;

  // iOS skips assistantMode (no accessibility-overlay equivalent), but DOES
  // include the keyboard step now — it routes to a separate iOS flow that
  // points the user at Settings → Keyboards → Add Rewordium.
  List<_StepKey> get _activeSteps => _isAndroid
      ? const [
          _StepKey.welcome,
          _StepKey.assistantMode,
          _StepKey.theme,
          _StepKey.llm,
          _StepKey.news,
          _StepKey.keyboard,
        ]
      : const [
          _StepKey.welcome,
          _StepKey.theme,
          _StepKey.llm,
          _StepKey.news,
          _StepKey.keyboard,
        ];

  List<String> get _stepTitles =>
      _activeSteps.map((k) => _stepTitleByKey[k]!).toList(growable: false);

  _StepKey get _currentStepKey => _activeSteps[_step];

  int _step = 0;
  bool _isSaving = false;
  bool _didSeedThemePreference = false;

  _AssistantMode _assistantMode = _AssistantMode.keyboardOnly;
  bool _accessibilityDisclosureAccepted = false;
  bool _openAccessibilitySettingsAfterFinish = true;
  bool _openKeyboardSettingsAfterFinish = true;

  bool _dynamicColorsEnabled = false;

  _LlmMode _llmMode = _LlmMode.managedDefault;
  AIProvider _selectedProvider = AIProvider.groq;

  bool _subscribeToNews = false;

  bool _numberRowEnabled = true;
  bool _contactsSuggestionsEnabled = true;
  bool _clipboardSuggestionsEnabled = true;
  bool _keyboardAiDefaultEnabled = true;
  bool _keyboardHapticsEnabled = true;
  _KeyboardHapticsMode _keyboardHapticsMode = _KeyboardHapticsMode.followSystem;
  bool _openReboardSettingsAfterFinish = false;

  bool _awaitingAssistantModeAccessibility = false;
  bool _awaitingSystemSetup = false;
  _SystemSetupStage _systemSetupStage = _SystemSetupStage.idle;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _modelController.text =
        AdvancedAISettings(provider: _selectedProvider).getDefaultModelName();
    _seedExistingToggleState();
  }

  Future<void> _seedExistingToggleState() async {
    final prefs = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      _openAccessibilitySettingsAfterFinish =
          prefs.getBool('onboarding_open_accessibility_after_finish') ??
              _openAccessibilitySettingsAfterFinish;
      _openKeyboardSettingsAfterFinish =
          prefs.getBool('onboarding_open_keyboard_after_finish') ??
              _openKeyboardSettingsAfterFinish;
      _openReboardSettingsAfterFinish =
          prefs.getBool('onboarding_open_reboard_after_finish') ??
              _openReboardSettingsAfterFinish;
      _keyboardAiDefaultEnabled =
          prefs.getBool('paraphraser_enabled') ?? _keyboardAiDefaultEnabled;
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_didSeedThemePreference) return;
    _dynamicColorsEnabled = context.read<ThemeProvider>().useDynamicColors;
    _didSeedThemePreference = true;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _apiKeyController.dispose();
    _modelController.dispose();
    _endpointController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      return;
    }

    if (_awaitingAssistantModeAccessibility) {
      Future<void>.delayed(const Duration(milliseconds: 350), () async {
        if (!mounted || !_awaitingAssistantModeAccessibility) return;
        await _resumeAssistantModeAccessibilityFlow();
      });
      return;
    }

    if (!_awaitingSystemSetup) {
      return;
    }

    Future<void>.delayed(const Duration(milliseconds: 350), () async {
      if (!mounted || !_awaitingSystemSetup) return;
      await _advanceSystemSetupFlow(openSystemScreens: true, fromResume: true);
    });
  }

  bool get _isFinalStep => _step == _stepTitles.length - 1;

  bool get _usesAccessibility => _assistantMode != _AssistantMode.keyboardOnly;

  bool get _usesKeyboard =>
      _assistantMode != _AssistantMode.accessibilityOverlay;

  String? _currentStepError() {
    if (_currentStepKey == _StepKey.llm && _llmMode == _LlmMode.bringYourOwn) {
      final apiKey = _apiKeyController.text.trim();
      if (apiKey.isEmpty) {
        return 'Enter your API key or switch to the managed default provider.';
      }
      final customEndpoint = _endpointController.text.trim();
      if (_selectedProvider == AIProvider.custom && customEndpoint.isEmpty) {
        return 'Enter a custom endpoint URL for custom provider mode.';
      }

      final probe = AdvancedAISettings(
        provider: _selectedProvider,
        apiKey: apiKey,
      );
      if (!probe.isApiKeyValid()) {
        return 'The API key format does not match the selected provider.';
      }
    }

    return null;
  }

  Future<void> _onContinuePressed() async {
    if (_currentStepKey == _StepKey.assistantMode) {
      await _handleAssistantModeContinue();
      return;
    }

    final error = _currentStepError();
    if (error != null) {
      _showMessage(error);
      return;
    }

    if (_isFinalStep) {
      await _completeOnboarding();
      return;
    }

    setState(() => _step += 1);
  }

  Future<void> _onSkipPressed() async {
    final skip = await showDialog<bool>(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: const Text('Skip setup?'),
              content: const Text(
                'This will finish onboarding with safe defaults. You can change everything later in settings.',
              ),
              actions: [
                ButtonM3E(
                  onPressed: () => Navigator.of(context).pop(false),
                  label: const Text('Cancel'),
                  style: ButtonM3EStyle.outlined,
                  shape: ButtonM3EShape.round,
                  size: ButtonM3ESize.sm,
                ),
                ButtonM3E(
                  onPressed: () => Navigator.of(context).pop(true),
                  label: const Text('Skip'),
                  style: ButtonM3EStyle.filled,
                  shape: ButtonM3EShape.round,
                  size: ButtonM3ESize.sm,
                ),
              ],
            );
          },
        ) ??
        false;

    if (!skip) return;

    setState(() {
      _assistantMode = _AssistantMode.keyboardOnly;
      _accessibilityDisclosureAccepted = false;
      _awaitingAssistantModeAccessibility = false;
      _dynamicColorsEnabled = false;
      _llmMode = _LlmMode.managedDefault;
      _subscribeToNews = false;
      _numberRowEnabled = true;
      _contactsSuggestionsEnabled = true;
      _clipboardSuggestionsEnabled = true;
      _keyboardAiDefaultEnabled = true;
      _keyboardHapticsEnabled = true;
      _keyboardHapticsMode = _KeyboardHapticsMode.followSystem;
      _openReboardSettingsAfterFinish = false;
    });

    await _completeOnboarding();
  }

  Future<void> _completeOnboarding() async {
    if (_isSaving) return;
    final error = _currentStepError();
    if (error != null) {
      _showMessage(error);
      return;
    }

    setState(() => _isSaving = true);

    try {
      await context
          .read<ThemeProvider>()
          .setDynamicColorsEnabled(_dynamicColorsEnabled);

      await _persistAssistantChoices();
      await _persistLlmChoices();
      await _persistNewsChoice();
      await _persistKeyboardChoices();

      _awaitingAssistantModeAccessibility = false;
      _awaitingSystemSetup = true;
      await _advanceSystemSetupFlow(openSystemScreens: true, fromResume: false);
    } catch (e) {
      _awaitingAssistantModeAccessibility = false;
      _awaitingSystemSetup = false;
      _showMessage('Could not finish onboarding: $e');
    } finally {
      if (mounted && !_awaitingSystemSetup) {
        setState(() => _isSaving = false);
      }
    }
  }

  Future<void> _handleAssistantModeContinue() async {
    if (!_usesAccessibility) {
      setState(() {
        _accessibilityDisclosureAccepted = false;
        _awaitingAssistantModeAccessibility = false;
        _step += 1;
      });
      return;
    }

    final accessibilityEnabled = await _isAccessibilityEnabled();
    if (!mounted) return;

    if (accessibilityEnabled) {
      setState(() {
        _accessibilityDisclosureAccepted = true;
        _awaitingAssistantModeAccessibility = false;
        _step += 1;
      });
      return;
    }

    if (!_accessibilityDisclosureAccepted) {
      final bool accepted = await AccessibilityDisclosureScreen.show(context);
      if (!mounted) return;

      if (!accepted) {
        setState(() {
          _assistantMode = _AssistantMode.keyboardOnly;
          _accessibilityDisclosureAccepted = false;
          _awaitingAssistantModeAccessibility = false;
          _step += 1;
        });
        _showMessage(
          'Overlay mode skipped. Keyboard-first mode is enabled and you can turn on accessibility later.',
        );
        return;
      }

      setState(() {
        _accessibilityDisclosureAccepted = true;
      });
    }

    if (!accessibilityEnabled && _openAccessibilitySettingsAfterFinish) {
      setState(() {
        _awaitingAssistantModeAccessibility = true;
      });
      _showMessage(
        'Enable accessibility in Android settings, then return to continue onboarding.',
      );
      await _openAccessibilitySettings(autoReturnToApp: true);
      return;
    }

    setState(() {
      _awaitingAssistantModeAccessibility = false;
      _step += 1;
    });
  }

  Future<void> _resumeAssistantModeAccessibilityFlow() async {
    final accessibilityEnabled = await _isAccessibilityEnabled();
    if (!mounted || !_awaitingAssistantModeAccessibility) return;

    if (accessibilityEnabled) {
      setState(() {
        _awaitingAssistantModeAccessibility = false;
        if (_step == 1) {
          _step += 1;
        }
      });
      _showMessage('Accessibility enabled. Continuing setup.');
      return;
    }

    setState(() {
      _awaitingAssistantModeAccessibility = false;
    });
    _showMessage(
      'Accessibility is still off. You can enable it now or continue and enable it later.',
    );
  }

  Future<void> _persistAssistantChoices() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('onboarding_assistant_mode', _assistantMode.name);
    await prefs.setBool(
      'onboarding_accessibility_disclosure_accepted',
      _accessibilityDisclosureAccepted,
    );
    await prefs.setBool(
      'onboarding_open_accessibility_after_finish',
      _openAccessibilitySettingsAfterFinish,
    );
    await prefs.setBool(
      'onboarding_open_keyboard_after_finish',
      _openKeyboardSettingsAfterFinish,
    );
    await prefs.setBool(
      'onboarding_open_reboard_after_finish',
      _openReboardSettingsAfterFinish,
    );
  }

  Future<bool> _isAccessibilityEnabled() async {
    if (!_isAndroid) return false;
    try {
      final bool enabled = await _accessibilityChannel
          .invokeMethod('isAccessibilityServiceEnabled');
      return enabled;
    } catch (_) {
      return false;
    }
  }

  Future<void> _openAccessibilitySettings(
      {bool autoReturnToApp = false}) async {
    if (!_isAndroid) return;
    try {
      await _accessibilityChannel.invokeMethod('requestAccessibilitySettings', {
        'autoReturnToApp': autoReturnToApp,
      });
    } catch (_) {
      _showMessage('Could not open accessibility settings automatically.');
    }
  }

  Future<void> _advanceSystemSetupFlow({
    required bool openSystemScreens,
    required bool fromResume,
  }) async {
    if (!mounted) return;

    // iOS has no Rewordium IME or accessibility service to enable. Finalize
    // immediately — keyboard module will be added once AzooKey is integrated.
    if (!_isAndroid) {
      await _finalizeOnboardingCompletion();
      return;
    }

    if (_contactsSuggestionsEnabled) {
      final contactsGranted = await RewordiumKeyboardService.hasContactsPermission();
      if (!contactsGranted) {
        final transitioningToContacts = _systemSetupStage != _SystemSetupStage.contacts;
        _systemSetupStage = _SystemSetupStage.contacts;
        if (mounted) {
          setState(() => _isSaving = false);
        }
        _showMessage('Allow ReBoard to access your contacts for personalized suggestions.');
        if (openSystemScreens && (!fromResume || transitioningToContacts)) {
          await RewordiumKeyboardService.requestContactsPermission();
        }
        return;
      }
    }

    if (_usesKeyboard) {
      final keyboardEnabled = await _keyboardService.isKeyboardEnabled();
      if (!keyboardEnabled) {
        final transitioningToKeyboardEnable =
            _systemSetupStage != _SystemSetupStage.keyboardEnable;
        final shouldAutoOpenKeyboardSettings = openSystemScreens &&
            _openKeyboardSettingsAfterFinish &&
            (!fromResume || transitioningToKeyboardEnable);
        _systemSetupStage = _SystemSetupStage.keyboardEnable;
        if (mounted) {
          setState(() => _isSaving = false);
        }
        _showMessage(
          _openKeyboardSettingsAfterFinish
              ? 'Enable Rewordium keyboard in system settings. We will continue automatically when you return.'
              : 'Enable Rewordium keyboard in system settings, then return to continue.',
        );
        if (shouldAutoOpenKeyboardSettings) {
          await RewordiumKeyboardService.openKeyboardSettings(
            autoReturnToApp: _awaitingSystemSetup,
          );
        }
        return;
      }

      final keyboardSelected =
          await _keyboardService.isKeyboardSelectedAsDefault();
      if (!keyboardSelected) {
        final transitioningToKeyboardSelect =
            _systemSetupStage != _SystemSetupStage.keyboardSelect;
        final shouldAutoOpenInputPicker = openSystemScreens &&
            _openKeyboardSettingsAfterFinish &&
            (!fromResume || transitioningToKeyboardSelect);
        _systemSetupStage = _SystemSetupStage.keyboardSelect;
        if (mounted) {
          setState(() => _isSaving = false);
        }
        _showMessage(
          _openKeyboardSettingsAfterFinish
              ? 'Select Rewordium as your default keyboard to continue setup.'
              : 'Select Rewordium as your default keyboard in Android settings, then return to continue.',
        );
        if (shouldAutoOpenInputPicker) {
          await _keyboardService.showInputMethodPicker();
        }
        return;
      }
    }

    if (_usesAccessibility) {
      final accessibilityEnabled = await _isAccessibilityEnabled();
      if (!accessibilityEnabled) {
        final transitioningToAccessibility =
            _systemSetupStage != _SystemSetupStage.accessibility;
        final shouldAutoOpenAccessibility = openSystemScreens &&
            _openAccessibilitySettingsAfterFinish &&
            (!fromResume || transitioningToAccessibility);
        _systemSetupStage = _SystemSetupStage.accessibility;
        if (mounted) {
          setState(() => _isSaving = false);
        }
        _showMessage(
          _openAccessibilitySettingsAfterFinish
              ? (transitioningToAccessibility
                  ? 'Keyboard setup is complete. Enable accessibility to finish setup.'
                  : 'Enable accessibility in system settings, then return to finish setup.')
              : 'Enable accessibility in Android settings, then return to finish setup.',
        );
        if (shouldAutoOpenAccessibility) {
          await _openAccessibilitySettings(
            autoReturnToApp: _awaitingSystemSetup,
          );
        }
        return;
      }
    }

    await _finalizeOnboardingCompletion();
  }

  Future<void> _finalizeOnboardingCompletion() async {
    _awaitingAssistantModeAccessibility = false;
    _awaitingSystemSetup = false;
    _systemSetupStage = _SystemSetupStage.idle;

    if (_openReboardSettingsAfterFinish && _usesKeyboard) {
      await RewordiumKeyboardService.openReboardSettings();
    }

    await OnboardingPage.markCompleted();

    if (!mounted) return;
    setState(() => _isSaving = false);
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const HomePage()),
    );
  }

  Future<void> _persistLlmChoices() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('onboarding_llm_mode', _llmMode.name);

    if (_llmMode == _LlmMode.managedDefault) {
      const settings = AdvancedAISettings(
        enabled: false,
        provider: AIProvider.groq,
        apiKey: '',
        modelName: '',
        maxTokens: 8192,
        customEndpoint: '',
      );
      await AdvancedAISettingsService.saveSettings(settings);
      if (_isAndroid) {
        await AISettingsBridge.syncSettingsToAndroid();
      } else {
        await IosKeyboardBridge.syncCurrentSettings();
      }
      return;
    }

    final settings = AdvancedAISettings(
      enabled: true,
      provider: _selectedProvider,
      apiKey: _apiKeyController.text.trim(),
      modelName: _modelController.text.trim(),
      maxTokens:
          AdvancedAISettings(provider: _selectedProvider).getDefaultMaxTokens(),
      customEndpoint: _endpointController.text.trim(),
    );

    await AdvancedAISettingsService.saveSettings(settings);
    if (_isAndroid) await AISettingsBridge.syncSettingsToAndroid();
  }

  Future<void> _persistNewsChoice() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_news_subscription', _subscribeToNews);
    final synced =
        await NewsSubscriptionService.toggleNewsSubscription(_subscribeToNews);
    if (_subscribeToNews && !synced) {
      _showMessage(
        'News preference saved. We will sync your subscription when your account is ready.',
      );
    }
  }

  Future<void> _persistKeyboardChoices() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_keyboard_number_row', _numberRowEnabled);
    await prefs.setBool(
      'spelling__use_contacts',
      _contactsSuggestionsEnabled,
    );
    await prefs.setBool(
      'onboarding_keyboard_clipboard_suggestions',
      _clipboardSuggestionsEnabled,
    );
    await prefs.setBool(
      'onboarding_keyboard_ai_default_enabled',
      _keyboardAiDefaultEnabled,
    );
    await prefs.setBool(
      'onboarding_keyboard_haptics_enabled',
      _keyboardHapticsEnabled,
    );
    await prefs.setString(
      'onboarding_keyboard_haptics_mode',
      _keyboardHapticsMode.name,
    );

    // The native Rewordium IME only exists on Android today; persist
    // preferences but skip the platform channel calls on iOS.
    if (_isAndroid) {
      await RewordiumKeyboardService.setHapticFeedback(_keyboardHapticsEnabled);
      await RewordiumKeyboardService.setUseContactsForSuggestions(_contactsSuggestionsEnabled);
      final aiApplied =
          await _keyboardService.setAiSuggestions(_keyboardAiDefaultEnabled);
      await prefs.setBool('paraphraser_enabled', _keyboardAiDefaultEnabled);
      await prefs.setBool('onboarding_keyboard_ai_applied', aiApplied);
    } else {
      await prefs.setBool('paraphraser_enabled', _keyboardAiDefaultEnabled);
    }

    // Push the onboarding choice into the live KeyboardProvider so the home
    // screen's keyboard-status card reflects the AI toggle immediately —
    // without waiting for an app restart. The provider already cached
    // `_isParaphraserEnabled` from prefs at startup (before onboarding ran),
    // so a re-read is needed for the change to surface in the UI.
    if (mounted) {
      await context.read<KeyboardProvider>().refreshParaphraserState();
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    context.showToast(message);
  }

  Future<void> _applyThemeSelection(bool dynamicEnabled) async {
    if (dynamicEnabled == _dynamicColorsEnabled) return;
    if (mounted) {
      setState(() => _dynamicColorsEnabled = dynamicEnabled);
    }
    await context.read<ThemeProvider>().setDynamicColorsEnabled(dynamicEnabled);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // BUILD — surface starts here
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final baseTheme = Theme.of(context);
    // Single typeface across the entire flow. Variable weight does what a
    // second display face was doing.
    final themedData = baseTheme.copyWith(
      textTheme: GoogleFonts.plusJakartaSansTextTheme(baseTheme.textTheme).apply(
        bodyColor: baseTheme.colorScheme.onSurface,
        displayColor: baseTheme.colorScheme.onSurface,
      ),
    );

    return Theme(
      data: themedData,
      child: Builder(
        builder: (context) {
          final theme = Theme.of(context);
          final cs = theme.colorScheme;
          final progress = (_step + 1) / _stepTitles.length;
          final reduceMotion = MediaQuery.of(context).disableAnimations;

          return Scaffold(
            backgroundColor: cs.surface,
            body: SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 14, 24, 18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildHeader(context, cs),
                    const SizedBox(height: 18),
                    _buildProgressBar(progress, cs),
                    const SizedBox(height: 22),
                    _buildStepCounter(context, cs),
                    const SizedBox(height: 8),
                    Text(
                      _stepTitles[_step],
                      style: _headlineStyle(context),
                    ),
                    const SizedBox(height: 22),
                    Expanded(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 240),
                        reverseDuration: const Duration(milliseconds: 160),
                        switchInCurve: _easeOut,
                        switchOutCurve: Curves.easeIn,
                        transitionBuilder: (child, anim) {
                          if (reduceMotion) {
                            return FadeTransition(opacity: anim, child: child);
                          }
                          // Asymmetric: in slides up 6px while fading, out fades only.
                          // Never scale(0) — start from near-identity.
                          return FadeTransition(
                            opacity: anim,
                            child: SlideTransition(
                              position: Tween<Offset>(
                                begin: const Offset(0, 0.02),
                                end: Offset.zero,
                              ).animate(anim),
                              child: child,
                            ),
                          );
                        },
                        child: SingleChildScrollView(
                          key: ValueKey<int>(_step),
                          physics: const BouncingScrollPhysics(),
                          child: _buildStepBody(context),
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                    _buildBottomBar(context, cs),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildHeader(BuildContext context, ColorScheme cs) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Text(
          'rewordium',
          style: GoogleFonts.plusJakartaSans(
            fontSize: 15,
            fontWeight: FontWeight.w500,
            letterSpacing: -0.1,
            color: cs.onSurface,
          ),
        ),
        const Spacer(),
        _PressableScale(
          onPressed: _isSaving ? null : _onSkipPressed,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
            child: Text(
              'Skip',
              style: GoogleFonts.plusJakartaSans(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: cs.onSurfaceVariant,
              ),
            ),
          ),
        ),
      ],
    );
  }

  // Solid 2px fill on a hairline track. No gradient. Animated width on step change.
  Widget _buildProgressBar(double progress, ColorScheme cs) {
    final safeProgress = progress.clamp(0.0, 1.0);
    return SizedBox(
      height: 2,
      child: LayoutBuilder(
        builder: (context, constraints) {
          return Stack(
            children: [
              Container(
                color: cs.outlineVariant.withValues(alpha: 0.6),
              ),
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: safeProgress),
                duration: const Duration(milliseconds: 280),
                curve: _easeOut,
                builder: (context, value, _) {
                  return Container(
                    width: constraints.maxWidth * value,
                    color: cs.onSurface,
                  );
                },
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildStepCounter(BuildContext context, ColorScheme cs) {
    final total = _stepTitles.length.toString().padLeft(2, '0');
    final current = (_step + 1).toString().padLeft(2, '0');
    return Text(
      'Step $current · $total',
      style: GoogleFonts.plusJakartaSans(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        letterSpacing: 0.4,
        color: cs.onSurfaceVariant,
        fontFeatures: const [FontFeature.tabularFigures()],
      ),
    );
  }

  TextStyle _headlineStyle(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GoogleFonts.plusJakartaSans(
      fontSize: 30,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.6,
      height: 1.1,
      color: cs.onSurface,
    );
  }

  TextStyle _cardTitleStyle(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GoogleFonts.plusJakartaSans(
      fontSize: 15,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.1,
      color: cs.onSurface,
    );
  }

  TextStyle _bodyStyle(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GoogleFonts.plusJakartaSans(
      fontSize: 14,
      fontWeight: FontWeight.w400,
      height: 1.5,
      color: cs.onSurface,
    );
  }

  TextStyle _bodySubtleStyle(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GoogleFonts.plusJakartaSans(
      fontSize: 13,
      fontWeight: FontWeight.w400,
      height: 1.45,
      color: cs.onSurfaceVariant,
    );
  }

  TextStyle _sectionLabelStyle(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GoogleFonts.plusJakartaSans(
      fontSize: 11,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.8,
      color: cs.onSurfaceVariant,
    );
  }

  Widget _buildStepBody(BuildContext context) {
    switch (_currentStepKey) {
      case _StepKey.welcome:
        return _buildWelcomeStep(context);
      case _StepKey.assistantMode:
        return _buildAssistantModeStep(context);
      case _StepKey.theme:
        return _buildThemeStep(context);
      case _StepKey.llm:
        return _buildLlmStep(context);
      case _StepKey.news:
        return _buildNewsStep(context);
      case _StepKey.keyboard:
        return _isAndroid
            ? _buildKeyboardStep(context)
            : _buildKeyboardStepIOS(context);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP — Keyboard (iOS)
  // Advisory-only. iOS doesn't expose extension-enable state to the host, so
  // we just walk the user through Settings → Keyboards → Add Rewordium. The
  // Continue button is enabled at all times (skippable per product decision).
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildKeyboardStepIOS(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    Widget bullet(int n, String text) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 28,
              child: Text(
                n.toString().padLeft(2, '0'),
                style: GoogleFonts.plusJakartaSans(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: cs.onSurfaceVariant,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ),
            Expanded(child: Text(text, style: _bodyStyle(context))),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'You can install the Rewordium Keyboard from iOS Settings. We can\'t do it for you, but it\'s three taps:',
          style: _bodySubtleStyle(context),
        ),
        const SizedBox(height: 14),
        bullet(1, 'Open Settings → General → Keyboard → Keyboards'),
        bullet(2, 'Tap Add New Keyboard → Rewordium'),
        bullet(3, 'Tap Rewordium and turn on Allow Full Access'),
        const SizedBox(height: 18),
        Row(
          children: [
            Expanded(
              child: _PressableScale(
                onPressed: _openIOSSettings,
                child: Container(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    color: cs.primary,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Center(
                    child: Text(
                      'Open Settings',
                      style: GoogleFonts.plusJakartaSans(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: cs.onPrimary,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Text(
          'You can also do this later from Home → Setup Status, or skip and continue.',
          style: _bodySubtleStyle(context),
        ),
      ],
    );
  }

  Future<void> _openIOSSettings() async {
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

  Widget _buildBottomBar(BuildContext context, ColorScheme cs) {
    return Row(
      children: [
        // Back: text-only, no icon. The progress bar already tells direction.
        if (_step > 0)
          _PressableScale(
            onPressed: _isSaving ? null : () => setState(() => _step -= 1),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
              child: Text(
                'Back',
                style: _cardTitleStyle(context).copyWith(
                  color: cs.onSurfaceVariant,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          )
        else
          const SizedBox.shrink(),
        const Spacer(),
        ButtonM3E(
          onPressed: _isSaving ? null : _onContinuePressed,
          style: ButtonM3EStyle.filled,
          shape: ButtonM3EShape.round,
          size: ButtonM3ESize.md,
          label: _isSaving
              ? SizedBox(
                  width: 22,
                  height: 22,
                  child: LoadingIndicatorM3E(
                    color: cs.onPrimary,
                    constraints:
                        const BoxConstraints(maxWidth: 22, maxHeight: 22),
                  ),
                )
              : Text(_isFinalStep ? 'Finish setup' : 'Continue'),
        ),
      ],
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 0 — Welcome
  // Plain intro + numbered list of what's next. No card chrome, no meta pills,
  // no feature bullets that redundantly describe the next 5 steps.
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildWelcomeStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final upcoming = _stepTitles.sublist(1);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'A short setup. About a minute.',
          style: _bodyStyle(context),
        ),
        const SizedBox(height: 32),
        Text(
          "WHAT YOU'LL SET UP",
          style: _sectionLabelStyle(context),
        ),
        const SizedBox(height: 4),
        for (int i = 0; i < upcoming.length; i++) ...[
          _StaggeredFadeIn(
            delayMs: 40 + i * 45,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 14),
              child: Row(
                children: [
                  SizedBox(
                    width: 32,
                    child: Text(
                      (i + 1).toString().padLeft(2, '0'),
                      style: GoogleFonts.plusJakartaSans(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: cs.onSurfaceVariant,
                        fontFeatures: const [FontFeature.tabularFigures()],
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(upcoming[i], style: _cardTitleStyle(context)),
                  ),
                ],
              ),
            ),
          ),
          if (i < upcoming.length - 1)
            Divider(
              height: 1,
              thickness: 1,
              color: cs.outlineVariant.withValues(alpha: 0.5),
            ),
        ],
      ],
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 1 — Assistant mode
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildAssistantModeStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _selectionCard(
          context: context,
          icon: Icons.keyboard_outlined,
          title: 'Keyboard first',
          subtitle: 'ReBoard keyboard features without the accessibility overlay.',
          selected: _assistantMode == _AssistantMode.keyboardOnly,
          onTap: () =>
              setState(() => _assistantMode = _AssistantMode.keyboardOnly),
        ),
        _selectionCard(
          context: context,
          icon: Icons.layers_outlined,
          title: 'Accessibility overlay',
          subtitle: 'A bubble over supported apps for fast rewrite actions.',
          selected: _assistantMode == _AssistantMode.accessibilityOverlay,
          onTap: () => setState(
              () => _assistantMode = _AssistantMode.accessibilityOverlay),
        ),
        _selectionCard(
          context: context,
          icon: Icons.hub_outlined,
          title: 'Both',
          subtitle: 'Keyboard and overlay together.',
          selected: _assistantMode == _AssistantMode.both,
          onTap: () => setState(() => _assistantMode = _AssistantMode.both),
        ),
        if (_usesAccessibility) ...[
          const SizedBox(height: 18),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              color: cs.surface,
              border: Border.all(color: cs.outlineVariant),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Accessibility prompt',
                  style: _cardTitleStyle(context),
                ),
                const SizedBox(height: 6),
                Text(
                  'When you continue, you will see a required consent prompt before Android Accessibility settings open.',
                  style: _bodySubtleStyle(context),
                ),
                const SizedBox(height: 14),
                _disclosureLine(context,
                    'Uses visible text context to provide rewrite and drafting actions.'),
                _disclosureLine(context,
                    'Processes content only when you trigger assistant actions.'),
                _disclosureLine(context,
                    'You can revoke permission anytime in Android settings.'),
                const SizedBox(height: 14),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 160),
                  curve: _easeOut,
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: _accessibilityDisclosureAccepted
                          ? cs.primary
                          : cs.outlineVariant,
                    ),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        _accessibilityDisclosureAccepted
                            ? Icons.check
                            : Icons.info_outline,
                        size: 16,
                        color: _accessibilityDisclosureAccepted
                            ? cs.primary
                            : cs.onSurfaceVariant,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _accessibilityDisclosureAccepted
                              ? 'Disclosure accepted. Continue to open accessibility settings.'
                              : 'Consent will be requested when you tap Continue.',
                          style: _bodySubtleStyle(context),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 14),
        if (_usesAccessibility)
          _quietSwitch(
            context: context,
            label: 'Open accessibility settings when continuing',
            value: _openAccessibilitySettingsAfterFinish,
            onChanged: (value) {
              setState(() => _openAccessibilitySettingsAfterFinish = value);
            },
          ),
        if (_usesKeyboard)
          _quietSwitch(
            context: context,
            label: 'Open keyboard settings after setup',
            value: _openKeyboardSettingsAfterFinish,
            onChanged: (value) {
              setState(() => _openKeyboardSettingsAfterFinish = value);
            },
          ),
      ],
    );
  }

  Widget _disclosureLine(BuildContext context, String text) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 7),
            child: Container(
              width: 3,
              height: 3,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: cs.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(text, style: _bodySubtleStyle(context)),
          ),
        ],
      ),
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 2 — Theme
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildThemeStep(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _themeChoiceCard(
          context: context,
          title: 'Normal theme',
          subtitle:
              'Stable app colors with predictable contrast. Recommended for first launch.',
          selected: !_dynamicColorsEnabled,
          swatchColor: const Color(0xFF455A64), // blueGrey 700
          onTap: () => _applyThemeSelection(false),
        ),
        // Material You dynamic colors are an Android 12+ concept; iOS has no
        // equivalent system source, so we hide the option instead of offering
        // a setting that would never change anything.
        if (_isAndroid)
          _themeChoiceCard(
            context: context,
            title: 'Dynamic colors',
            subtitle: 'Device-derived colors. Material 3 style.',
            selected: _dynamicColorsEnabled,
            swatchColor: const Color(0xFF00897B), // teal 600
            onTap: () => _applyThemeSelection(true),
          ),
        const SizedBox(height: 14),
        Text(
          _isAndroid
              ? 'This affects the Flutter app theme only. It does not modify ReBoard keyboard theming.'
              : 'You can switch between light and dark, or match the system, anytime from Settings.',
          style: _bodySubtleStyle(context),
        ),
      ],
    );
  }

  Widget _themeChoiceCard({
    required BuildContext context,
    required String title,
    required String subtitle,
    required bool selected,
    required Color swatchColor,
    required VoidCallback onTap,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: _PressableScale(
        onPressed: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          curve: _easeOut,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: selected
                ? cs.primary.withValues(alpha: 0.04)
                : cs.surface,
            border: Border.all(
              color: selected ? cs.primary : cs.outlineVariant,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            children: [
              // Solid color circle. No gradient.
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: swatchColor,
                  border: Border.all(
                    color: cs.outlineVariant,
                    width: 1,
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: _cardTitleStyle(context)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: _bodySubtleStyle(context)),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              AnimatedOpacity(
                duration: const Duration(milliseconds: 160),
                curve: _easeOut,
                opacity: selected ? 1 : 0,
                child: Icon(Icons.check, size: 18, color: cs.primary),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 3 — AI provider
  // Drops Icons.auto_awesome (the literal sparkle).
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildLlmStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final llmError = _currentStepError();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _selectionCard(
          context: context,
          icon: Icons.cloud_outlined,
          title: 'Managed default',
          subtitle: 'Use the Rewordium-hosted Groq provider. No API key needed.',
          selected: _llmMode == _LlmMode.managedDefault,
          onTap: () => setState(() => _llmMode = _LlmMode.managedDefault),
        ),
        _selectionCard(
          context: context,
          icon: Icons.tune,
          title: 'Bring your own',
          subtitle: 'Choose a provider and use your own API key.',
          selected: _llmMode == _LlmMode.bringYourOwn,
          onTap: () => setState(() => _llmMode = _LlmMode.bringYourOwn),
        ),
        AnimatedSize(
          duration: const Duration(milliseconds: 200),
          curve: _easeOut,
          alignment: Alignment.topCenter,
          child: _llmMode == _LlmMode.bringYourOwn
              ? Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(top: 8),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: cs.outlineVariant),
                    color: cs.surface,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      DropdownButtonFormField<AIProvider>(
                        initialValue: _selectedProvider,
                        decoration: const InputDecoration(
                          labelText: 'Provider',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                        items: AIProvider.values
                            .map(
                              (provider) => DropdownMenuItem<AIProvider>(
                                value: provider,
                                child: Text(provider.displayName),
                              ),
                            )
                            .toList(),
                        onChanged: (provider) {
                          if (provider == null) return;
                          setState(() {
                            _selectedProvider = provider;
                            if (_modelController.text.trim().isEmpty) {
                              _modelController.text =
                                  AdvancedAISettings(provider: provider)
                                      .getDefaultModelName();
                            }
                          });
                        },
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _apiKeyController,
                        obscureText: true,
                        decoration: const InputDecoration(
                          labelText: 'API key',
                          hintText: 'Your provider API key',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _modelController,
                        decoration: const InputDecoration(
                          labelText: 'Model (optional)',
                          hintText: 'Leave empty for provider default',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                      ),
                      if (_selectedProvider == AIProvider.custom) ...[
                        const SizedBox(height: 12),
                        TextField(
                          controller: _endpointController,
                          decoration: const InputDecoration(
                            labelText: 'Custom endpoint URL',
                            hintText:
                                'https://api.example.com/v1/chat/completions',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      Text(
                        _selectedProvider.description,
                        style: _bodySubtleStyle(context),
                      ),
                    ],
                  ),
                )
              : const SizedBox.shrink(),
        ),
        if (llmError != null)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(
              llmError,
              style: _bodySubtleStyle(context).copyWith(color: cs.error),
            ),
          ),
      ],
    );
  }


  // ─────────────────────────────────────────────────────────────────────────
  // STEP 4 — News updates
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildNewsStep(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _quietSwitch(
          context: context,
          label: 'Subscribe to product news',
          sublabel:
              'Feature launches, release notes, and improvement highlights.',
          value: _subscribeToNews,
          onChanged: (value) => setState(() => _subscribeToNews = value),
        ),
        const SizedBox(height: 8),
        Text(
          'Change anytime in Settings → News & Updates.',
          style: _bodySubtleStyle(context),
        ),
      ],
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // STEP 5 — Keyboard
  // ─────────────────────────────────────────────────────────────────────────

  Widget _buildKeyboardStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _quietSwitch(
          context: context,
          label: 'Show number row',
          sublabel: 'Visible number keys above the alphabet.',
          value: _numberRowEnabled,
          onChanged: (value) => setState(() => _numberRowEnabled = value),
        ),
        _quietSwitch(
          context: context,
          label: 'Learn from contacts (no data is collected or sent to our servers)',
          sublabel: 'Suggest contact names when typing or swiping.',
          value: _contactsSuggestionsEnabled,
          onChanged: (value) {
            setState(() => _contactsSuggestionsEnabled = value);
          },
        ),
        _quietSwitch(
          context: context,
          label: 'Clipboard suggestions',
          sublabel: 'Suggest recently copied text in the candidate row.',
          value: _clipboardSuggestionsEnabled,
          onChanged: (value) =>
              setState(() => _clipboardSuggestionsEnabled = value),
        ),
        _quietSwitch(
          context: context,
          label: 'Enable AI on home screen',
          sublabel: 'Maps to the home AI toggle and keyboard AI quick actions.',
          value: _keyboardAiDefaultEnabled,
          onChanged: (value) =>
              setState(() => _keyboardAiDefaultEnabled = value),
        ),
        _quietSwitch(
          context: context,
          label: 'Key haptics',
          sublabel: 'Vibration feedback while typing.',
          value: _keyboardHapticsEnabled,
          onChanged: (value) => setState(() => _keyboardHapticsEnabled = value),
        ),
        AnimatedSize(
          duration: const Duration(milliseconds: 200),
          curve: _easeOut,
          alignment: Alignment.topCenter,
          child: _keyboardHapticsEnabled
              ? Padding(
                  padding: const EdgeInsets.only(top: 14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('HAPTICS MODE',
                          style: _sectionLabelStyle(context)),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          _quietChip(
                            context: context,
                            label: 'Follow system',
                            selected: _keyboardHapticsMode ==
                                _KeyboardHapticsMode.followSystem,
                            onTap: () => setState(() => _keyboardHapticsMode =
                                _KeyboardHapticsMode.followSystem),
                          ),
                          _quietChip(
                            context: context,
                            label: 'Always vibrate',
                            selected: _keyboardHapticsMode ==
                                _KeyboardHapticsMode.alwaysVibrate,
                            onTap: () => setState(() => _keyboardHapticsMode =
                                _KeyboardHapticsMode.alwaysVibrate),
                          ),
                        ],
                      ),
                    ],
                  ),
                )
              : const SizedBox.shrink(),
        ),
        const SizedBox(height: 14),
        _quietSwitch(
          context: context,
          label: 'Open ReBoard settings after finish',
          sublabel:
              'Apply advanced keyboard preferences immediately after onboarding.',
          value: _openReboardSettingsAfterFinish,
          onChanged: (value) =>
              setState(() => _openReboardSettingsAfterFinish = value),
        ),
        const SizedBox(height: 10),
        Text(
          'Your keyboard preferences are saved during onboarding and can be adjusted later.',
          style: _bodySubtleStyle(context),
        ),
      ],
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Selection card — used by step 1 (assistant mode) and step 3 (LLM mode).
  // 1px outlineVariant idle, 1.5px primary selected, 4% primary tint selected,
  // no shadow, no inner accent bar, no gradient icon container, no Ink ripple.
  // ─────────────────────────────────────────────────────────────────────────

  Widget _selectionCard({
    required BuildContext context,
    required IconData icon,
    required String title,
    required String subtitle,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: _PressableScale(
        onPressed: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          curve: _easeOut,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color:
                selected ? cs.primary.withValues(alpha: 0.04) : cs.surface,
            border: Border.all(
              color: selected ? cs.primary : cs.outlineVariant,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            children: [
              Icon(
                icon,
                size: 20,
                color: selected ? cs.primary : cs.onSurfaceVariant,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: _cardTitleStyle(context)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: _bodySubtleStyle(context)),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              AnimatedOpacity(
                duration: const Duration(milliseconds: 160),
                curve: _easeOut,
                opacity: selected ? 1 : 0,
                child: Icon(Icons.check, size: 18, color: cs.primary),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // Quiet inline switch row — no card chrome, just a hairline separator above.
  Widget _quietSwitch({
    required BuildContext context,
    required String label,
    String? sublabel,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(color: cs.outlineVariant.withValues(alpha: 0.5)),
        ),
      ),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: SwitchListTile.adaptive(
        contentPadding: EdgeInsets.zero,
        title: Text(label, style: _cardTitleStyle(context)),
        subtitle: sublabel == null
            ? null
            : Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(sublabel, style: _bodySubtleStyle(context)),
              ),
        value: value,
        onChanged: onChanged,
        activeColor: cs.primary,
      ),
    );
  }

  // Chip alternative to ChoiceChip — flatter, with primary border on select.
  Widget _quietChip({
    required BuildContext context,
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final cs = Theme.of(context).colorScheme;
    return _PressableScale(
      onPressed: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        curve: _easeOut,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(999),
          color:
              selected ? cs.primary.withValues(alpha: 0.06) : cs.surface,
          border: Border.all(
            color: selected ? cs.primary : cs.outlineVariant,
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Text(
          label,
          style: GoogleFonts.plusJakartaSans(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: selected ? cs.primary : cs.onSurface,
          ),
        ),
      ),
    );
  }
}

// ───────────────────────────────────────────────────────────────────────────
// Pressable scale wrapper — gives tap targets the tactile feedback that
// Material 3's color state-layer alone doesn't convey. Scale 0.97 on press,
// 100ms ease-out. Respects reduced-motion (scale stays at 1, no transform).
// ───────────────────────────────────────────────────────────────────────────

class _PressableScale extends StatefulWidget {
  final VoidCallback? onPressed;
  final Widget child;
  final double scale;

  const _PressableScale({
    required this.onPressed,
    required this.child,
    this.scale = 0.97,
  });

  @override
  State<_PressableScale> createState() => _PressableScaleState();
}

class _PressableScaleState extends State<_PressableScale> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    final enabled = widget.onPressed != null;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown:
          enabled ? (_) => setState(() => _pressed = true) : null,
      onTapUp:
          enabled ? (_) => setState(() => _pressed = false) : null,
      onTapCancel:
          enabled ? () => setState(() => _pressed = false) : null,
      onTap: enabled ? widget.onPressed : null,
      child: AnimatedScale(
        duration: const Duration(milliseconds: 100),
        curve: _easeOut,
        scale: (_pressed && !reduceMotion) ? widget.scale : 1.0,
        child: AnimatedOpacity(
          duration: const Duration(milliseconds: 150),
          opacity: enabled ? 1.0 : 0.45,
          child: widget.child,
        ),
      ),
    );
  }
}

// Stagger helper for welcome list rows. Each row delays its fade-in by
// `delayMs` to create a soft cascade. Keep delays short (30-80ms between
// items) so the interface never feels held up.
class _StaggeredFadeIn extends StatefulWidget {
  final int delayMs;
  final Widget child;
  const _StaggeredFadeIn({required this.delayMs, required this.child});

  @override
  State<_StaggeredFadeIn> createState() => _StaggeredFadeInState();
}

class _StaggeredFadeInState extends State<_StaggeredFadeIn>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctl;
  late final Animation<double> _opacity;
  late final Animation<Offset> _offset;

  @override
  void initState() {
    super.initState();
    _ctl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
    );
    _opacity = CurvedAnimation(parent: _ctl, curve: _easeOut);
    _offset = Tween<Offset>(
      begin: const Offset(0, 0.15),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _ctl, curve: _easeOut));

    Future.delayed(Duration(milliseconds: widget.delayMs), () {
      if (mounted) _ctl.forward();
    });
  }

  @override
  void dispose() {
    _ctl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    return FadeTransition(
      opacity: _opacity,
      child: reduceMotion
          ? widget.child
          : SlideTransition(position: _offset, child: widget.child),
    );
  }
}
