import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'dart:convert';

/// Model for advanced AI settings
class AdvancedAISettings {
  final bool enabled;
  final AIProvider provider;
  final String apiKey;
  final String modelName;
  final int maxTokens;
  final String customEndpoint;

  const AdvancedAISettings({
    this.enabled = false,
    this.provider = AIProvider.groq,
    this.apiKey = '',
    this.modelName = '',
    this.maxTokens = 8000,
    this.customEndpoint = '',
  });

  factory AdvancedAISettings.fromJson(Map<String, dynamic> json) {
    return AdvancedAISettings(
      enabled: json['enabled'] ?? false,
      provider: AIProvider.values.firstWhere(
        (e) => e.toString() == json['provider'],
        orElse: () => AIProvider.groq,
      ),
      apiKey: json['apiKey'] ?? '',
      modelName: json['modelName'] ?? '',
      maxTokens: json['maxTokens'] ?? 8000,
      customEndpoint: json['customEndpoint'] ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'provider': provider.toString(),
      'apiKey': apiKey,
      'modelName': modelName,
      'maxTokens': maxTokens,
      'customEndpoint': customEndpoint,
    };
  }

  AdvancedAISettings copyWith({
    bool? enabled,
    AIProvider? provider,
    String? apiKey,
    String? modelName,
    int? maxTokens,
    String? customEndpoint,
  }) {
    return AdvancedAISettings(
      enabled: enabled ?? this.enabled,
      provider: provider ?? this.provider,
      apiKey: apiKey ?? this.apiKey,
      modelName: modelName ?? this.modelName,
      maxTokens: maxTokens ?? this.maxTokens,
      customEndpoint: customEndpoint ?? this.customEndpoint,
    );
  }

  /// Get default model name for a provider
  String getDefaultModelName() {
    switch (provider) {
      case AIProvider.gemini:
        return 'gemini-1.5-pro';
      case AIProvider.openai:
        return 'gpt-4-turbo-preview';
      case AIProvider.anthropic:
        return 'claude-3-opus-20240229';
      case AIProvider.groq:
        return 'llama3-70b-8192';
      case AIProvider.custom:
        return '';
    }
  }

  /// Get default max tokens for a provider
  int getDefaultMaxTokens() {
    switch (provider) {
      case AIProvider.gemini:
        return 100000;
      case AIProvider.openai:
        return 128000;
      case AIProvider.anthropic:
        return 200000;
      case AIProvider.groq:
        return 8192;
      case AIProvider.custom:
        return 8000;
    }
  }

  /// Validate API key format
  bool isApiKeyValid() {
    if (apiKey.isEmpty) return false;

    switch (provider) {
      case AIProvider.gemini:
        return apiKey.startsWith('AIzaSy');
      case AIProvider.openai:
        return apiKey.startsWith('sk-');
      case AIProvider.anthropic:
        return apiKey.startsWith('sk-ant-');
      case AIProvider.groq:
        return apiKey.startsWith('gsk_');
      case AIProvider.custom:
        return apiKey.isNotEmpty;
    }
  }
}

/// AI Provider enum
enum AIProvider {
  groq,
  gemini,
  openai,
  anthropic,
  custom;

  String get displayName {
    switch (this) {
      case AIProvider.groq:
        return 'Groq (LLaMA 3 - Default)';
      case AIProvider.gemini:
        return 'Google Gemini';
      case AIProvider.openai:
        return 'OpenAI';
      case AIProvider.anthropic:
        return 'Anthropic Claude';
      case AIProvider.custom:
        return 'Custom Endpoint';
    }
  }

  String get description {
    switch (this) {
      case AIProvider.groq:
        return 'Fast, free, and reliable (Default)';
      case AIProvider.gemini:
        return 'Best for long content & high quality';
      case AIProvider.openai:
        return 'Industry-leading AI with GPT-4';
      case AIProvider.anthropic:
        return 'Advanced reasoning with Claude';
      case AIProvider.custom:
        return 'Your own API endpoint';
    }
  }

  IconData get icon {
    switch (this) {
      case AIProvider.groq:
        return Icons.flash_on;
      case AIProvider.gemini:
        return Icons.auto_awesome;
      case AIProvider.openai:
        return Icons.psychology;
      case AIProvider.anthropic:
        return Icons.smart_toy;
      case AIProvider.custom:
        return Icons.settings_ethernet;
    }
  }
}

/// Service for managing advanced AI settings with secure storage
class AdvancedAISettingsService {
  static const _storage = FlutterSecureStorage();
  static const _settingsKey = 'advanced_ai_settings';
  static AdvancedAISettings? _cachedSettings;

  /// Load settings from secure storage
  static Future<AdvancedAISettings> loadSettings() async {
    if (_cachedSettings != null) {
      return _cachedSettings!;
    }

    try {
      final jsonString = await _storage.read(key: _settingsKey);
      if (jsonString == null || jsonString.isEmpty) {
        _cachedSettings = const AdvancedAISettings();
        return _cachedSettings!;
      }

      final json = jsonDecode(jsonString) as Map<String, dynamic>;
      _cachedSettings = AdvancedAISettings.fromJson(json);
      return _cachedSettings!;
    } catch (e) {
      print('Error loading advanced AI settings: $e');
      _cachedSettings = const AdvancedAISettings();
      return _cachedSettings!;
    }
  }

  /// Save settings to secure storage
  static Future<void> saveSettings(AdvancedAISettings settings) async {
    try {
      final jsonString = jsonEncode(settings.toJson());
      await _storage.write(key: _settingsKey, value: jsonString);
      _cachedSettings = settings;
      print(
          '✅ Advanced AI settings saved securely (API key never leaves device)');
    } catch (e) {
      print('Error saving advanced AI settings: $e');
    }
  }

  /// Clear all settings
  static Future<void> clearSettings() async {
    try {
      await _storage.delete(key: _settingsKey);
      _cachedSettings = const AdvancedAISettings();
      print('Advanced AI settings cleared');
    } catch (e) {
      print('Error clearing advanced AI settings: $e');
    }
  }

  /// Check if custom LLM should be used
  static Future<bool> shouldUseCustomLLM() async {
    final settings = await loadSettings();
    return settings.enabled && settings.isApiKeyValid();
  }

  /// Get API configuration for current settings
  static Future<Map<String, dynamic>> getAPIConfig() async {
    final settings = await loadSettings();

    // If advanced settings are enabled but API key is invalid, throw error
    if (settings.enabled && !settings.isApiKeyValid()) {
      throw Exception(
          'Advanced AI Settings enabled but no valid API key provided. Please enter a valid API key or disable advanced settings.');
    }

    // If advanced settings are disabled, use default Groq
    if (!settings.enabled) {
      return {
        'provider': 'groq',
        'apiKey': '', // Will use default from .env
        'model': 'llama3-70b-8192',
        'maxTokens': 8192,
      };
    }

    // Return custom config (enabled + valid key)
    return {
      'provider': settings.provider.name,
      'apiKey': settings.apiKey,
      'model': settings.modelName.isEmpty
          ? settings.getDefaultModelName()
          : settings.modelName,
      'maxTokens': settings.maxTokens,
    };
  }
}
