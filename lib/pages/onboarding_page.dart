import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../main.dart';
import '../models/advanced_ai_settings.dart';
import '../services/ai_settings_bridge.dart';
import '../services/keyboard_service.dart';
import '../services/news_subscription_service.dart';
import '../services/rewordium_keyboard_service.dart';
import '../screens/accessibility_disclosure_screen.dart';
import '../theme/theme_provider.dart';

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

enum _SystemSetupStage { idle, keyboardEnable, keyboardSelect, accessibility }

class _OnboardingPageState extends State<OnboardingPage>
    with WidgetsBindingObserver {
  static const _accessibilityChannel =
      MethodChannel('com.noxquill.rewordium/accessibility');

  final KeyboardService _keyboardService = KeyboardService();
  final TextEditingController _apiKeyController = TextEditingController();
  final TextEditingController _modelController = TextEditingController();
  final TextEditingController _endpointController = TextEditingController();

  static const List<String> _stepTitles = [
    'Welcome',
    'Assistant Mode',
    'Theme Preference',
    'AI Provider',
    'News Updates',
    'Keyboard Preferences',
  ];

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
    if (_step == 3 && _llmMode == _LlmMode.bringYourOwn) {
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
    if (_step == 1) {
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
                TextButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  child: const Text('Skip'),
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
      await AISettingsBridge.syncSettingsToAndroid();
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
    await AISettingsBridge.syncSettingsToAndroid();
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

    await RewordiumKeyboardService.setHapticFeedback(_keyboardHapticsEnabled);
    final aiApplied =
        await _keyboardService.setAiSuggestions(_keyboardAiDefaultEnabled);
    await prefs.setBool('paraphraser_enabled', _keyboardAiDefaultEnabled);
    await prefs.setBool('onboarding_keyboard_ai_applied', aiApplied);
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _applyThemeSelection(bool dynamicEnabled) async {
    if (dynamicEnabled == _dynamicColorsEnabled) return;
    if (mounted) {
      setState(() => _dynamicColorsEnabled = dynamicEnabled);
    }
    await context.read<ThemeProvider>().setDynamicColorsEnabled(dynamicEnabled);
  }

  @override
  Widget build(BuildContext context) {
    final baseTheme = Theme.of(context);
    final themedData = baseTheme.copyWith(
      textTheme: GoogleFonts.ibmPlexSansTextTheme(baseTheme.textTheme).apply(
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

          return Scaffold(
            body: Stack(
              children: [
                _buildBackground(cs),
                SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Rewordium Setup',
                                  style: _navTitleStyle(context),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'STEP ${_step + 1} OF ${_stepTitles.length}',
                                  style: _stepLabelStyle(context),
                                ),
                              ],
                            ),
                            const Spacer(),
                            TextButton(
                              onPressed: _isSaving ? null : _onSkipPressed,
                              style: TextButton.styleFrom(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 6,
                                ),
                                foregroundColor: cs.onSurface,
                                textStyle: _stepLabelStyle(context),
                              ),
                              child: const Text('Skip'),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        _buildProgressBar(progress, cs),
                        const SizedBox(height: 14),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            _stepTitles[_step],
                            style: _headlineStyle(context),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Expanded(
                          child: AnimatedSwitcher(
                            duration: const Duration(milliseconds: 240),
                            switchInCurve: Curves.easeOutCubic,
                            switchOutCurve: Curves.easeInCubic,
                            child: SingleChildScrollView(
                              key: ValueKey<int>(_step),
                              child: _buildStepBody(context),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        _buildBottomBar(),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildBackground(ColorScheme cs) {
    return Stack(
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  cs.surface,
                  cs.surfaceContainerLow,
                  cs.surface,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: Transform.rotate(
            angle: -0.12,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Colors.transparent,
                    cs.primaryContainer.withValues(alpha: 0.14),
                    Colors.transparent,
                  ],
                  stops: const [0.2, 0.5, 0.8],
                ),
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(0.85, -0.9),
                radius: 1.05,
                colors: [
                  cs.secondaryContainer.withValues(alpha: 0.32),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned(
          bottom: -120,
          left: -60,
          child: Container(
            width: 240,
            height: 240,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: cs.tertiaryContainer.withValues(alpha: 0.22),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildProgressBar(double progress, ColorScheme cs) {
    final safeProgress = progress.clamp(0.0, 1.0) as double;
    return Container(
      height: 6,
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: cs.outlineVariant.withValues(alpha: 0.6)),
        boxShadow: [
          BoxShadow(
            color: cs.shadow.withValues(alpha: 0.06),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(999),
        child: Stack(
          children: [
            Positioned.fill(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      cs.primary.withValues(alpha: 0.12),
                      cs.secondary.withValues(alpha: 0.12),
                    ],
                  ),
                ),
              ),
            ),
            FractionallySizedBox(
              widthFactor: safeProgress,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                    colors: [
                      cs.primary,
                      cs.secondary,
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  TextStyle _navTitleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.titleMedium?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
    );
  }

  TextStyle _stepLabelStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.ibmPlexSans(
      textStyle: theme.textTheme.labelSmall?.copyWith(
        color: cs.onSurfaceVariant,
        fontWeight: FontWeight.w600,
        letterSpacing: 1.1,
      ),
    );
  }

  TextStyle _headlineStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.headlineSmall?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
      ),
    );
  }

  TextStyle _heroTitleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.titleLarge?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
    );
  }

  TextStyle _cardTitleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.spaceGrotesk(
      textStyle: theme.textTheme.titleSmall?.copyWith(
        color: cs.onSurface,
        fontWeight: FontWeight.w600,
      ),
    );
  }

  TextStyle _bodyStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.ibmPlexSans(
      textStyle: theme.textTheme.bodyMedium?.copyWith(
        color: cs.onSurface,
        height: 1.45,
      ),
    );
  }

  TextStyle _bodySubtleStyle(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    return GoogleFonts.ibmPlexSans(
      textStyle: theme.textTheme.bodySmall?.copyWith(
        color: cs.onSurfaceVariant,
        height: 1.4,
      ),
    );
  }

  Widget _buildStepBody(BuildContext context) {
    switch (_step) {
      case 0:
        return _buildWelcomeStep(context);
      case 1:
        return _buildAssistantModeStep(context);
      case 2:
        return _buildThemeStep(context);
      case 3:
        return _buildLlmStep(context);
      case 4:
        return _buildNewsStep(context);
      case 5:
        return _buildKeyboardStep(context);
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildBottomBar() {
    final cs = Theme.of(context).colorScheme;
    return Row(
      children: [
        if (_step > 0)
          OutlinedButton.icon(
            onPressed: _isSaving ? null : () => setState(() => _step -= 1),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 10,
              ),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              side: BorderSide(color: cs.outlineVariant),
            ),
            icon: const Icon(Icons.arrow_back),
            label: const Text('Back'),
          )
        else
          const SizedBox.shrink(),
        const Spacer(),
        FilledButton.icon(
          onPressed: _isSaving ? null : _onContinuePressed,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(
              horizontal: 18,
              vertical: 10,
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          icon: _isSaving
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Icon(_isFinalStep ? Icons.check : Icons.arrow_forward),
          label: Text(_isFinalStep ? 'Finish Setup' : 'Continue'),
        ),
      ],
    );
  }

  Widget _buildWelcomeStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            color: cs.surfaceContainerHigh,
            border: Border.all(color: cs.outlineVariant),
            boxShadow: [
              BoxShadow(
                color: cs.shadow.withValues(alpha: 0.08),
                blurRadius: 18,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 4,
                    height: 46,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(6),
                      color: cs.primary,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Welcome to Rewordium',
                          style: _heroTitleStyle(context),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Set up your assistant in about a minute.',
                          style: _bodySubtleStyle(context),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                'Choose how Rewordium assists you, confirm accessibility usage, and set keyboard defaults tailored to your workflow.',
                style: _bodyStyle(context),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _metaTag(context, 'Setup ~60s'),
                  _metaTag(context, 'Change anytime'),
                  _metaTag(context, 'Privacy-first'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _featureBullet(
          context,
          icon: Icons.accessibility_new,
          title: 'Assistant modes',
          subtitle: 'Choose keyboard, overlay, or both based on your workflow.',
        ),
        _featureBullet(
          context,
          icon: Icons.security,
          title: 'Permission clarity',
          subtitle: 'Disclosure is shown before accessibility setup.',
        ),
        _featureBullet(
          context,
          icon: Icons.palette_outlined,
          title: 'Theme preference',
          subtitle: 'Select standard colors or enable dynamic theming.',
        ),
        _featureBullet(
          context,
          icon: Icons.memory,
          title: 'AI provider',
          subtitle: 'Use managed Groq or connect your own provider.',
        ),
      ],
    );
  }

  Widget _featureBullet(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: cs.surfaceContainerLow,
        border: Border.all(color: cs.outlineVariant),
        boxShadow: [
          BoxShadow(
            color: cs.shadow.withValues(alpha: 0.06),
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              gradient: LinearGradient(
                colors: [
                  cs.primaryContainer,
                  cs.secondaryContainer,
                ],
              ),
            ),
            child: Icon(icon, size: 20, color: cs.onPrimaryContainer),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: cs.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _metaTag(BuildContext context, String label) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: cs.surfaceContainerHighest,
        border: Border.all(color: cs.outlineVariant),
      ),
      child: Text(
        label,
        style: _stepLabelStyle(context).copyWith(
          color: cs.onSurfaceVariant,
          letterSpacing: 0.2,
        ),
      ),
    );
  }

  Widget _buildAssistantModeStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _modeCard(
          context,
          icon: Icons.keyboard,
          title: 'Keyboard First',
          subtitle:
              'Use ReBoard keyboard features without accessibility overlay.',
          selected: _assistantMode == _AssistantMode.keyboardOnly,
          onTap: () =>
              setState(() => _assistantMode = _AssistantMode.keyboardOnly),
        ),
        _modeCard(
          context,
          icon: Icons.layers,
          title: 'Accessibility Overlay',
          subtitle:
              'Assistant bubble over supported apps for fast rewrite actions.',
          selected: _assistantMode == _AssistantMode.accessibilityOverlay,
          onTap: () => setState(
              () => _assistantMode = _AssistantMode.accessibilityOverlay),
        ),
        _modeCard(
          context,
          icon: Icons.hub,
          title: 'Both',
          subtitle: 'Enable keyboard and overlay paths together.',
          selected: _assistantMode == _AssistantMode.both,
          onTap: () => setState(() => _assistantMode = _AssistantMode.both),
        ),
        const SizedBox(height: 12),
        if (_usesAccessibility)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              color: cs.surfaceContainerLow,
              border: Border.all(color: cs.outlineVariant),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.policy_outlined, color: cs.primary),
                    const SizedBox(width: 8),
                    Text(
                      'Accessibility Prompt',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  'When you continue, you will see a required consent prompt before Android Accessibility settings open.',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 8),
                _disclosureLine(context,
                    'Uses visible text context to provide rewrite and drafting actions.'),
                _disclosureLine(context,
                    'Processes content only when you trigger assistant actions.'),
                _disclosureLine(context,
                    'You can revoke permission anytime in Android settings.'),
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    color: _accessibilityDisclosureAccepted
                        ? cs.tertiaryContainer
                        : cs.surfaceContainerHighest,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        _accessibilityDisclosureAccepted
                            ? Icons.check_circle
                            : Icons.info_outline,
                        color: _accessibilityDisclosureAccepted
                            ? cs.onTertiaryContainer
                            : cs.onSurfaceVariant,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _accessibilityDisclosureAccepted
                              ? 'Disclosure accepted. Continue to open accessibility settings now.'
                              : 'Consent will be requested when you tap Continue.',
                          style:
                              Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: _accessibilityDisclosureAccepted
                                        ? cs.onTertiaryContainer
                                        : cs.onSurfaceVariant,
                                  ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        const SizedBox(height: 10),
        if (_usesAccessibility)
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('Open accessibility settings when continuing'),
            value: _openAccessibilitySettingsAfterFinish,
            onChanged: (value) {
              setState(() => _openAccessibilitySettingsAfterFinish = value);
            },
          ),
        if (_usesKeyboard)
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('Open keyboard settings after setup'),
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
      padding: const EdgeInsets.only(bottom: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Icon(Icons.circle, size: 8, color: cs.primary),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        ],
      ),
    );
  }

  Widget _modeCard(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: selected ? cs.surface : cs.surfaceContainerLow,
            border: Border.all(
              color: selected ? cs.primary : cs.outlineVariant,
              width: selected ? 1.6 : 1,
            ),
            boxShadow: selected
                ? [
                    BoxShadow(
                      color: cs.shadow.withValues(alpha: 0.1),
                      blurRadius: 16,
                      offset: const Offset(0, 8),
                    ),
                  ]
                : [],
          ),
          child: Row(
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 3,
                height: 46,
                decoration: BoxDecoration(
                  color: selected ? cs.primary : cs.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              const SizedBox(width: 10),
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  color: cs.surfaceContainerHighest,
                  border: Border.all(color: cs.outlineVariant),
                ),
                child: Icon(
                  icon,
                  color: selected ? cs.primary : cs.onSurfaceVariant,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                selected ? Icons.check_circle : Icons.circle_outlined,
                color: selected ? cs.primary : cs.outline,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildThemeStep(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _themeChoiceCard(
          context,
          title: 'Normal Theme (Recommended first install)',
          subtitle:
              'Stable app colors with predictable contrast. This is the default for first launch.',
          selected: !_dynamicColorsEnabled,
          swatchColor: Colors.blueGrey,
          onTap: () => _applyThemeSelection(false),
        ),
        _themeChoiceCard(
          context,
          title: 'Dynamic Colors',
          subtitle:
              'Use device-derived colors for a personalized Material 3 style.',
          selected: _dynamicColorsEnabled,
          swatchColor: Colors.teal,
          onTap: () => _applyThemeSelection(true),
        ),
        const SizedBox(height: 8),
        Text(
          'This preference affects the Flutter app theme only and does not modify ReBoard theme internals.',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      ],
    );
  }

  Widget _themeChoiceCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required bool selected,
    required Color swatchColor,
    required VoidCallback onTap,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: selected ? cs.surface : cs.surfaceContainerLow,
            border: Border.all(
              color: selected ? cs.secondary : cs.outlineVariant,
              width: selected ? 1.6 : 1,
            ),
            boxShadow: selected
                ? [
                    BoxShadow(
                      color: cs.shadow.withValues(alpha: 0.1),
                      blurRadius: 16,
                      offset: const Offset(0, 8),
                    ),
                  ]
                : [],
          ),
          child: Row(
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 3,
                height: 48,
                decoration: BoxDecoration(
                  color: selected ? cs.secondary : cs.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              const SizedBox(width: 10),
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [
                      swatchColor.withValues(alpha: 0.9),
                      swatchColor.withValues(alpha: 0.55),
                    ],
                  ),
                  border: Border.all(color: cs.outlineVariant),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                selected ? Icons.check_circle : Icons.circle_outlined,
                color: selected ? cs.secondary : cs.outline,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLlmStep(BuildContext context) {
    final llmError = _step == 3 ? _currentStepError() : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _modeCard(
          context,
          icon: Icons.auto_awesome,
          title: 'Use Rewordium managed default (Groq)',
          subtitle: 'Best for quick start. No custom API key needed.',
          selected: _llmMode == _LlmMode.managedDefault,
          onTap: () => setState(() => _llmMode = _LlmMode.managedDefault),
        ),
        _modeCard(
          context,
          icon: Icons.tune,
          title: 'Bring your own provider',
          subtitle: 'Choose endpoint/provider and use your own API key.',
          selected: _llmMode == _LlmMode.bringYourOwn,
          onTap: () => setState(() => _llmMode = _LlmMode.bringYourOwn),
        ),
        if (_llmMode == _LlmMode.bringYourOwn)
          Container(
            width: double.infinity,
            margin: const EdgeInsets.only(top: 6),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: Theme.of(context).colorScheme.outlineVariant,
              ),
              color: Theme.of(context).colorScheme.surfaceContainerLow,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                DropdownButtonFormField<AIProvider>(
                  initialValue: _selectedProvider,
                  decoration: const InputDecoration(
                    labelText: 'Provider',
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
                    hintText: 'Enter your provider API key',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _modelController,
                  decoration: const InputDecoration(
                    labelText: 'Model (optional)',
                    hintText: 'Leave empty to use provider default',
                  ),
                ),
                if (_selectedProvider == AIProvider.custom) ...[
                  const SizedBox(height: 12),
                  TextField(
                    controller: _endpointController,
                    decoration: const InputDecoration(
                      labelText: 'Custom endpoint URL',
                      hintText: 'https://api.example.com/v1/chat/completions',
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                Text(
                  _selectedProvider.description,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        if (llmError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              llmError,
              style: TextStyle(
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildNewsStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: cs.surfaceContainerLow,
            border: Border.all(color: cs.outlineVariant),
          ),
          child: SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('Subscribe to product news and updates'),
            subtitle: const Text(
              'Get feature launches, release notes, and improvement highlights.',
            ),
            value: _subscribeToNews,
            onChanged: (value) {
              setState(() => _subscribeToNews = value);
            },
          ),
        ),
        const SizedBox(height: 10),
        Text(
          'You can change this anytime in Settings > News & Updates.',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: cs.onSurfaceVariant,
              ),
        ),
      ],
    );
  }

  Widget _buildKeyboardStep(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: cs.surfaceContainerLow,
            border: Border.all(color: cs.outlineVariant),
          ),
          child: Column(
            children: [
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('Show number row'),
                subtitle: const Text(
                    'Prefer visible number keys above the alphabet.'),
                value: _numberRowEnabled,
                onChanged: (value) {
                  setState(() => _numberRowEnabled = value);
                },
              ),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enable clipboard suggestions'),
                subtitle: const Text(
                    'Suggest recently copied text inside candidate row.'),
                value: _clipboardSuggestionsEnabled,
                onChanged: (value) {
                  setState(() => _clipboardSuggestionsEnabled = value);
                },
              ),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enable AI on homescreen'),
                subtitle: const Text(
                  'Maps to the app home AI toggle and keyboard AI quick actions.',
                ),
                value: _keyboardAiDefaultEnabled,
                onChanged: (value) {
                  setState(() => _keyboardAiDefaultEnabled = value);
                },
              ),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enable key haptics'),
                subtitle: const Text('Vibrate feedback while typing.'),
                value: _keyboardHapticsEnabled,
                onChanged: (value) {
                  setState(() => _keyboardHapticsEnabled = value);
                },
              ),
            ],
          ),
        ),
        if (_keyboardHapticsEnabled) ...[
          const SizedBox(height: 10),
          Text(
            'Haptics output mode',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ChoiceChip(
                label: const Text('System haptic interface'),
                selected:
                    _keyboardHapticsMode == _KeyboardHapticsMode.followSystem,
                onSelected: (_) {
                  setState(() {
                    _keyboardHapticsMode = _KeyboardHapticsMode.followSystem;
                  });
                },
              ),
              ChoiceChip(
                label: const Text('Vibrator mode'),
                selected:
                    _keyboardHapticsMode == _KeyboardHapticsMode.alwaysVibrate,
                onSelected: (_) {
                  setState(() {
                    _keyboardHapticsMode = _KeyboardHapticsMode.alwaysVibrate;
                  });
                },
              ),
            ],
          ),
        ],
        const SizedBox(height: 8),
        SwitchListTile.adaptive(
          contentPadding: EdgeInsets.zero,
          title: const Text('Open ReBoard settings after finish'),
          subtitle: const Text(
            'Apply advanced keyboard preferences immediately after onboarding.',
          ),
          value: _openReboardSettingsAfterFinish,
          onChanged: (value) {
            setState(() => _openReboardSettingsAfterFinish = value);
          },
        ),
        const SizedBox(height: 8),
        Text(
          'Your keyboard preferences are saved during onboarding and can always be adjusted later.',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: cs.onSurfaceVariant,
              ),
        ),
      ],
    );
  }
}
