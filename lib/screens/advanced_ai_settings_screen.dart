import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:animate_do/animate_do.dart';
import 'package:provider/provider.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../models/advanced_ai_settings.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/animated_card.dart';
import '../services/ai_settings_bridge.dart';

class AdvancedAISettingsScreen extends StatefulWidget {
  const AdvancedAISettingsScreen({super.key});

  @override
  State<AdvancedAISettingsScreen> createState() =>
      _AdvancedAISettingsScreenState();
}

class _AdvancedAISettingsScreenState extends State<AdvancedAISettingsScreen> {
  bool _isLoading = true;
  late AdvancedAISettings _settings;

  final _apiKeyController = TextEditingController();
  final _modelNameController = TextEditingController();
  final _maxTokensController = TextEditingController();
  final _customEndpointController = TextEditingController();

  bool _showApiKey = false;
  bool _hasUnsavedChanges = false;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    _modelNameController.dispose();
    _maxTokensController.dispose();
    _customEndpointController.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    setState(() => _isLoading = true);

    try {
      final settings = await AdvancedAISettingsService.loadSettings();
      setState(() {
        _settings = settings;
        _apiKeyController.text = settings.apiKey;
        _modelNameController.text = settings.modelName.isEmpty
            ? settings.getDefaultModelName()
            : settings.modelName;
        _maxTokensController.text = settings.maxTokens.toString();
        _customEndpointController.text = settings.customEndpoint;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _settings = const AdvancedAISettings();
        _isLoading = false;
      });
    }
  }

  Future<void> _saveSettings() async {
    HapticFeedback.mediumImpact();

    setState(() => _isLoading = true);

    try {
      final newSettings = _settings.copyWith(
        apiKey: _apiKeyController.text.trim(),
        modelName: _modelNameController.text.trim(),
        maxTokens: int.tryParse(_maxTokensController.text) ?? 8000,
        customEndpoint: _customEndpointController.text.trim(),
      );

      await AdvancedAISettingsService.saveSettings(newSettings);

      // Sync settings to Android native services (Accessibility & Keyboard)
      await AISettingsBridge.syncSettingsToAndroid();

      setState(() {
        _settings = newSettings;
        _hasUnsavedChanges = false;
        _isLoading = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
                '✅ Settings saved and synced! Your API key never leaves your device.'),
            backgroundColor: Colors.green,
            duration: Duration(seconds: 3),
          ),
        );
      }
    } catch (e) {
      setState(() => _isLoading = false);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ Error saving settings: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  void _onSettingsChanged() {
    if (!_hasUnsavedChanges) {
      setState(() => _hasUnsavedChanges = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final isDarkMode = themeProvider.isDarkMode;

    if (_isLoading) {
      return Scaffold(
        backgroundColor: AppTheme.backgroundColor,
        body: Center(
          child: CircularProgressIndicator(
            color: AppTheme.primaryColor,
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: CustomAppBar(
        title: "Advanced AI Settings",
        showBackButton: true,
        onLeadingTap: () {
          if (_hasUnsavedChanges) {
            _showUnsavedChangesDialog();
          } else {
            Navigator.pop(context);
          }
        },
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildInfoCard(),
          const SizedBox(height: 16),
          _buildEnableCard(),
          const SizedBox(height: 16),
          if (_settings.enabled) ...[
            _buildProviderCard(),
            const SizedBox(height: 16),
            _buildApiKeyCard(),
            const SizedBox(height: 16),
            _buildModelConfigCard(),
            const SizedBox(height: 16),
            _buildSecurityCard(),
            const SizedBox(height: 16),
          ] else ...[
            _buildDefaultInfoCard(),
            const SizedBox(height: 16),
          ],
          // Always show save and reset buttons
          _buildSaveButton(),
          const SizedBox(height: 12),
          _buildResetButton(),
          const SizedBox(height: 16),
          _buildComparisonCard(),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildInfoCard() {
    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppTheme.primaryColor,
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.psychology,
                color: Colors.white,
                size: 24,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Advanced AI Settings',
                    style: AppTheme.headingSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Use your own LLM API key for better quality',
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEnableCard() {
    return AnimatedCard(
      child: SwitchListTile(
        value: _settings.enabled,
        onChanged: (value) {
          HapticFeedback.selectionClick();
          setState(() {
            _settings = _settings.copyWith(enabled: value);
            _onSettingsChanged();
          });
        },
        title: Text(
          'Use Custom AI Provider',
          style: AppTheme.bodyLarge.copyWith(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(
          _settings.enabled
              ? 'Using your own API key'
              : 'Using default Groq + LLaMA 3',
          style: AppTheme.bodySmall,
        ),
        activeColor: AppTheme.primaryColor,
      ),
    );
  }

  Widget _buildProviderCard() {
    return AnimatedCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              'AI Provider',
              style: AppTheme.headingSmall,
            ),
          ),
          const Divider(height: 1),
          ...AIProvider.values.map((provider) {
            final isSelected = _settings.provider == provider;
            return ListTile(
              leading: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: isSelected
                      ? provider.brandColor.withOpacity(0.15)
                      : Colors.grey.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Center(
                  child: SvgPicture.asset(
                    provider.iconPath,
                    width: 24,
                    height: 24,
                    colorFilter: isSelected
                        ? null // Use original colors when selected
                        : ColorFilter.mode(Colors.grey, BlendMode.srcIn),
                  ),
                ),
              ),
              title: Text(
                provider.displayName,
                style: AppTheme.bodyMedium.copyWith(
                  fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                ),
              ),
              subtitle: Text(
                provider.description,
                style: AppTheme.bodySmall,
              ),
              trailing: isSelected
                  ? Icon(Icons.check_circle, color: provider.brandColor)
                  : null,
              selected: isSelected,
              onTap: () {
                HapticFeedback.selectionClick();
                setState(() {
                  _settings = _settings.copyWith(provider: provider);
                  _modelNameController.text = _settings.getDefaultModelName();
                  _maxTokensController.text =
                      _settings.getDefaultMaxTokens().toString();
                  _onSettingsChanged();
                });
              },
            );
          }).toList(),
        ],
      ),
    );
  }

  Widget _buildApiKeyCard() {
    final isValid = _settings.isApiKeyValid() || _apiKeyController.text.isEmpty;

    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'API Key',
              style: AppTheme.headingSmall,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _apiKeyController,
              obscureText: !_showApiKey,
              onChanged: (_) => _onSettingsChanged(),
              style: AppTheme.bodyMedium,
              decoration: InputDecoration(
                hintText:
                    'Enter your ${_settings.provider.displayName} API key',
                prefixIcon: const Icon(Icons.key),
                suffixIcon: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (!isValid && _apiKeyController.text.isNotEmpty)
                      const Padding(
                        padding: EdgeInsets.only(right: 8),
                        child: Icon(Icons.error, color: Colors.red, size: 20),
                      ),
                    IconButton(
                      icon: Icon(
                        _showApiKey ? Icons.visibility_off : Icons.visibility,
                      ),
                      onPressed: () {
                        setState(() => _showApiKey = !_showApiKey);
                      },
                    ),
                  ],
                ),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(
                    color: isValid
                        ? Colors.grey.withOpacity(0.3)
                        : Colors.red.withOpacity(0.5),
                  ),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(
                    color: isValid ? AppTheme.primaryColor : Colors.red,
                    width: 2,
                  ),
                ),
              ),
            ),
            if (!isValid && _apiKeyController.text.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  'Invalid API key format for ${_settings.provider.displayName}',
                  style: AppTheme.bodySmall.copyWith(color: Colors.red),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildModelConfigCard() {
    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Model Configuration',
              style: AppTheme.headingSmall,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _modelNameController,
              onChanged: (_) => _onSettingsChanged(),
              style: AppTheme.bodyMedium,
              decoration: InputDecoration(
                labelText: 'Model Name',
                hintText: _settings.getDefaultModelName(),
                prefixIcon: const Icon(Icons.smart_toy),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _maxTokensController,
              keyboardType: TextInputType.number,
              onChanged: (_) => _onSettingsChanged(),
              style: AppTheme.bodyMedium,
              decoration: InputDecoration(
                labelText: 'Max Tokens',
                hintText: _settings.getDefaultMaxTokens().toString(),
                prefixIcon: const Icon(Icons.list_alt),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            if (_settings.provider == AIProvider.custom) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _customEndpointController,
                onChanged: (_) => _onSettingsChanged(),
                style: AppTheme.bodyMedium,
                decoration: InputDecoration(
                  labelText: 'Custom Endpoint URL',
                  hintText: 'https://your-api-endpoint.com/v1/chat',
                  prefixIcon: const Icon(Icons.link),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSecurityCard() {
    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.green.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.lock,
                color: Colors.green,
                size: 24,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Secure Storage',
                    style: AppTheme.bodyMedium.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Your API keys never leave your device. Stored securely using Android Keystore / iOS Keychain.',
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDefaultInfoCard() {
    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Icon(
              Icons.flash_on,
              size: 48,
              color: AppTheme.primaryColor,
            ),
            const SizedBox(height: 16),
            Text(
              'Using Default Groq + LLaMA 3',
              style: AppTheme.headingSmall,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'Fast, free, and reliable AI responses. Enable advanced settings above to use your own API key.',
              textAlign: TextAlign.center,
              style: AppTheme.bodyMedium,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildComparisonCard() {
    return AnimatedCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Provider Comparison',
              style: AppTheme.headingSmall,
            ),
            const SizedBox(height: 16),
            _buildComparisonRow('Groq (Default)', 'Fast', 'Free', '8K tokens'),
            _buildComparisonRow('Gemini', 'Medium', '\$\$', '100K tokens'),
            _buildComparisonRow('OpenAI', 'Medium', '\$\$\$', '128K tokens'),
            _buildComparisonRow('Claude', 'Slower', '\$\$\$', '200K tokens'),
          ],
        ),
      ),
    );
  }

  Widget _buildComparisonRow(
      String provider, String speed, String cost, String context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            flex: 2,
            child: Text(provider, style: AppTheme.bodyMedium),
          ),
          Expanded(
            child: Text(speed, style: AppTheme.bodySmall),
          ),
          Expanded(
            child: Text(cost, style: AppTheme.bodySmall),
          ),
          Expanded(
            child: Text(context, style: AppTheme.bodySmall),
          ),
        ],
      ),
    );
  }

  Widget _buildSaveButton() {
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: _hasUnsavedChanges ? _saveSettings : null,
        style: ElevatedButton.styleFrom(
          backgroundColor: AppTheme.primaryColor,
          foregroundColor: Colors.white,
          disabledBackgroundColor: Colors.grey.withOpacity(0.3),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: Text(
          _hasUnsavedChanges ? 'Save Settings' : 'No Changes',
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }

  Widget _buildResetButton() {
    return SizedBox(
      width: double.infinity,
      height: 48,
      child: OutlinedButton(
        onPressed: () => _showResetDialog(),
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.red,
          side: const BorderSide(color: Colors.red),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: const Text(
          'Reset to Default',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }

  void _showUnsavedChangesDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Unsaved Changes'),
        content: const Text(
          'You have unsaved changes. Do you want to save them before leaving?',
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context); // Close dialog
              Navigator.pop(context); // Close settings screen
            },
            child: const Text('Discard'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context); // Close dialog
              await _saveSettings();
              if (mounted) {
                Navigator.pop(context); // Close settings screen
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _showResetDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reset to Default?'),
        content: const Text(
          'This will clear all your advanced AI settings and revert to the default Groq + LLaMA 3. Your API keys will be permanently deleted from this device.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await AdvancedAISettingsService.clearSettings();
              await _loadSettings();

              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('✅ Reset to default Groq + LLaMA 3'),
                    backgroundColor: Colors.green,
                  ),
                );
              }
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Reset'),
          ),
        ],
      ),
    );
  }
}
