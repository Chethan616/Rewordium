import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:firebase_auth/firebase_auth.dart';

import '../models/advanced_ai_settings.dart';
import 'firebase_service.dart';
import 'groq_service.dart';
import 'in_app_review_service.dart';
import 'usage_analytics_service.dart';

/// Unified AI Service that supports multiple LLM providers
/// Falls back to Groq + LLaMA if no custom API key is configured
class UnifiedAIService {
  static Map<String, dynamic> _outOfCreditsResponse() {
    return {
      'error': 'OUT_OF_CREDITS',
      'errorType': 'OUT_OF_CREDITS',
      'content':
          '⚠️ You are out of credits. Upgrade to Pro or connect your own API in Advanced AI Settings.',
    };
  }

  /// Make an AI request using the configured provider
  static Future<Map<String, dynamic>> makeRequest({
    required String systemPrompt,
    required String userMessage,
    double temperature = 0.7,
    bool requireJson = false,
  }) async {
    // Gate: require login for all AI features
    final currentUser = FirebaseAuth.instance.currentUser;
    if (currentUser == null) {
      return {
        'error': 'NOT_LOGGED_IN',
        'errorType': 'NOT_LOGGED_IN',
        'content': '⚠️ Please log in to use AI features.',
      };
    }

    final uid = currentUser.uid;
    String provider = 'unknown';
    bool usesExternalApi = false;

    try {
      final config = await AdvancedAISettingsService.getAPIConfig();
      provider = config['provider'] as String;
      usesExternalApi = config['usesExternalApi'] == true;

      Map<String, dynamic> result;

      if (provider == 'groq') {
        // Use default Groq service
        result = await _makeGroqRequest(
          systemPrompt: systemPrompt,
          userMessage: userMessage,
          temperature: temperature,
          requireJson: requireJson,
        );
      } else {
        // Use custom provider
        switch (provider) {
          case 'gemini':
            result = await _makeGeminiRequest(
              config: config,
              systemPrompt: systemPrompt,
              userMessage: userMessage,
              temperature: temperature,
            );
            break;
          case 'openai':
            result = await _makeOpenAIRequest(
              config: config,
              systemPrompt: systemPrompt,
              userMessage: userMessage,
              temperature: temperature,
              requireJson: requireJson,
            );
            break;
          case 'anthropic':
            result = await _makeClaudeRequest(
              config: config,
              systemPrompt: systemPrompt,
              userMessage: userMessage,
              temperature: temperature,
            );
            break;
          case 'custom':
            result = await _makeCustomRequest(
              config: config,
              systemPrompt: systemPrompt,
              userMessage: userMessage,
              temperature: temperature,
              requireJson: requireJson,
            );
            break;
          default:
            throw Exception('Unknown AI provider: $provider');
        }
      }

      final isSuccess = result['success'] == true && result['error'] == null;

      int creditsUsed = 0;

      // Charge app credits only for successful default-provider requests.
      if (isSuccess && !usesExternalApi) {
        final consumed = await FirebaseService.consumeCredit(
          uid,
          source: 'unified_ai_service',
        );

        if (!consumed) {
          await UsageAnalyticsService.recordApiCall(
            feature: 'ai_request',
            provider: provider,
            success: false,
            errorType: 'OUT_OF_CREDITS',
            creditsUsed: 0,
          );
          return _outOfCreditsResponse();
        }

        creditsUsed = 1;
      }

      await UsageAnalyticsService.recordApiCall(
        feature: 'ai_request',
        provider: provider,
        success: isSuccess,
        errorType: isSuccess ? null : result['errorType']?.toString(),
        creditsUsed: creditsUsed,
      );

      await UsageAnalyticsService.touchUserActivity();

      // Track successful generation for in-app review prompts
      if (isSuccess) {
        InAppReviewService.onSuccessfulGeneration();
      }

      return result;
    } catch (e) {
      print('UnifiedAIService error: $e');
      await UsageAnalyticsService.recordApiCall(
        feature: 'ai_request',
        provider: provider,
        success: false,
        errorType: 'UNKNOWN',
        creditsUsed: 0,
      );
      final errorString = e.toString().toLowerCase();

      // Check if error is related to missing API key
      if (e
          .toString()
          .contains('Advanced AI Settings enabled but no valid API key')) {
        return {
          'error': 'MISSING_API_KEY',
          'errorType': 'MISSING_API_KEY',
          'content':
              '⚠️ Advanced AI Settings are enabled but no API key is provided.\n\nPlease go to Settings → Advanced AI Settings and either:\n• Enter a valid API key, or\n• Disable Advanced AI Settings to use the default Groq service',
        };
      }

      // Check for rate limit / token exhaustion errors
      if (errorString.contains('rate') ||
          errorString.contains('limit') ||
          errorString.contains('quota') ||
          errorString.contains('429') ||
          errorString.contains('exhausted') ||
          errorString.contains('tokens')) {
        return {
          'error': 'RATE_LIMIT',
          'errorType': 'RATE_LIMIT',
          'content':
              '⚠️ API rate limit or token quota exceeded.\n\nYour AI provider has reached its usage limit. Please wait a moment and try again, or check your API plan.',
        };
      }

      // Check for invalid API key errors
      if (errorString.contains('401') ||
          errorString.contains('unauthorized') ||
          errorString.contains('invalid') && errorString.contains('key')) {
        return {
          'error': 'INVALID_API_KEY',
          'errorType': 'INVALID_API_KEY',
          'content':
              '⚠️ Invalid API key.\n\nThe API key you provided is not valid. Please check your API key in Settings → Advanced AI Settings.',
        };
      }

      // Check for missing endpoint
      if (errorString.contains('endpoint') ||
          errorString.contains('url') ||
          errorString.contains('host')) {
        return {
          'error': 'MISSING_ENDPOINT',
          'errorType': 'MISSING_ENDPOINT',
          'content':
              '⚠️ Custom endpoint URL is missing or invalid.\n\nPlease provide a valid API endpoint URL in Settings → Advanced AI Settings.',
        };
      }

      return {
        'error': e.toString(),
        'errorType': 'UNKNOWN',
        'content':
            'I apologize, but I encountered an error: ${e.toString()}\n\nPlease try again or check your settings.',
      };
    }
  }

  /// Make request to Groq (default)
  static Future<Map<String, dynamic>> _makeGroqRequest({
    required String systemPrompt,
    required String userMessage,
    required double temperature,
    required bool requireJson,
  }) async {
    try {
      await GroqService.initialize();
      final apiKey = await GroqService.getApiKey();

      if (apiKey.isEmpty) {
        throw Exception('Groq API key is not configured');
      }

      final response = await http
          .post(
            Uri.parse('https://api.groq.com/openai/v1/chat/completions'),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $apiKey',
            },
            body: jsonEncode({
              'model': 'llama-3.1-8b-instant',
              'messages': [
                {'role': 'system', 'content': systemPrompt},
                {'role': 'user', 'content': userMessage},
              ],
              'temperature': temperature,
              if (requireJson) 'response_format': {'type': 'json_object'},
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];

        return {
          'success': true,
          'content': content,
          'provider': 'groq',
        };
      } else if (response.statusCode == 429) {
        // Rate limit exceeded
        return {
          'error': 'RATE_LIMIT',
          'errorType': 'RATE_LIMIT',
          'content':
              '⚠️ Groq API rate limit exceeded. Please wait a moment and try again.',
        };
      } else if (response.statusCode == 401) {
        return {
          'error': 'INVALID_API_KEY',
          'errorType': 'INVALID_API_KEY',
          'content':
              '⚠️ Invalid Groq API key. Please check your configuration.',
        };
      } else {
        throw Exception(
            'Groq API error: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('Groq request error: $e');
      return {
        'error': e.toString(),
        'content': 'Error connecting to Groq. Please check your connection.',
      };
    }
  }

  /// Make request to Google Gemini
  static Future<Map<String, dynamic>> _makeGeminiRequest({
    required Map<String, dynamic> config,
    required String systemPrompt,
    required String userMessage,
    required double temperature,
  }) async {
    try {
      final apiKey = config['apiKey'] as String;
      final model = config['model'] as String;
      final maxTokens = config['maxTokens'] as int;

      // Gemini uses a different API structure
      final response = await http
          .post(
            Uri.parse(
                'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'contents': [
                {
                  'parts': [
                    {'text': '$systemPrompt\n\n$userMessage'}
                  ]
                }
              ],
              'generationConfig': {
                'temperature': temperature,
                'maxOutputTokens': maxTokens,
              },
            }),
          )
          .timeout(const Duration(seconds: 60));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['candidates'][0]['content']['parts'][0]['text'];

        return {
          'success': true,
          'content': content,
          'provider': 'gemini',
        };
      } else {
        throw Exception(
            'Gemini API error: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('Gemini request error: $e');
      return {
        'error': e.toString(),
        'content': 'Error connecting to Gemini. Please check your API key.',
      };
    }
  }

  /// Make request to OpenAI
  static Future<Map<String, dynamic>> _makeOpenAIRequest({
    required Map<String, dynamic> config,
    required String systemPrompt,
    required String userMessage,
    required double temperature,
    required bool requireJson,
  }) async {
    try {
      final apiKey = config['apiKey'] as String;
      final model = config['model'] as String;
      final maxTokens = config['maxTokens'] as int;

      final response = await http
          .post(
            Uri.parse('https://api.openai.com/v1/chat/completions'),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $apiKey',
            },
            body: jsonEncode({
              'model': model,
              'messages': [
                {'role': 'system', 'content': systemPrompt},
                {'role': 'user', 'content': userMessage},
              ],
              'temperature': temperature,
              'max_tokens': maxTokens,
              if (requireJson) 'response_format': {'type': 'json_object'},
            }),
          )
          .timeout(const Duration(seconds: 60));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];

        return {
          'success': true,
          'content': content,
          'provider': 'openai',
        };
      } else {
        throw Exception(
            'OpenAI API error: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('OpenAI request error: $e');
      return {
        'error': e.toString(),
        'content': 'Error connecting to OpenAI. Please check your API key.',
      };
    }
  }

  /// Make request to Anthropic Claude
  static Future<Map<String, dynamic>> _makeClaudeRequest({
    required Map<String, dynamic> config,
    required String systemPrompt,
    required String userMessage,
    required double temperature,
  }) async {
    try {
      final apiKey = config['apiKey'] as String;
      final model = config['model'] as String;
      final maxTokens = config['maxTokens'] as int;

      final response = await http
          .post(
            Uri.parse('https://api.anthropic.com/v1/messages'),
            headers: {
              'Content-Type': 'application/json',
              'x-api-key': apiKey,
              'anthropic-version': '2023-06-01',
            },
            body: jsonEncode({
              'model': model,
              'max_tokens': maxTokens,
              'temperature': temperature,
              'system': systemPrompt,
              'messages': [
                {'role': 'user', 'content': userMessage},
              ],
            }),
          )
          .timeout(const Duration(seconds: 60));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['content'][0]['text'];

        return {
          'success': true,
          'content': content,
          'provider': 'anthropic',
        };
      } else {
        throw Exception(
            'Claude API error: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('Claude request error: $e');
      return {
        'error': e.toString(),
        'content': 'Error connecting to Claude. Please check your API key.',
      };
    }
  }

  /// Make request to custom endpoint
  static Future<Map<String, dynamic>> _makeCustomRequest({
    required Map<String, dynamic> config,
    required String systemPrompt,
    required String userMessage,
    required double temperature,
    required bool requireJson,
  }) async {
    try {
      final apiKey = config['apiKey'] as String;
      final model = config['model'] as String;
      final maxTokens = config['maxTokens'] as int;
      final endpoint = config['endpoint'] as String? ?? '';

      if (endpoint.isEmpty) {
        throw Exception('Custom endpoint URL is not configured');
      }

      // Assume OpenAI-compatible format for custom endpoints
      final response = await http
          .post(
            Uri.parse(endpoint),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $apiKey',
            },
            body: jsonEncode({
              'model': model,
              'messages': [
                {'role': 'system', 'content': systemPrompt},
                {'role': 'user', 'content': userMessage},
              ],
              'temperature': temperature,
              'max_tokens': maxTokens,
              if (requireJson) 'response_format': {'type': 'json_object'},
            }),
          )
          .timeout(const Duration(seconds: 60));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];

        return {
          'success': true,
          'content': content,
          'provider': 'custom',
        };
      } else {
        throw Exception(
            'Custom API error: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('Custom endpoint request error: $e');
      return {
        'error': e.toString(),
        'content':
            'Error connecting to custom endpoint. Please check your configuration.',
      };
    }
  }

  /// Get paraphrased text using configured provider
  static Future<Map<String, dynamic>> paraphraseText(
    String text,
    String tone,
  ) async {
    final systemPrompt =
        'You are a helpful paraphrasing assistant. Your task is to completely rewrite the following text in a $tone tone while preserving the original meaning but using different words and sentence structures. Return a JSON response with: {"paraphrased_text": "the rewritten text"}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.8,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return result;
    }

    try {
      final content = result['content'] as String;
      final parsed = jsonDecode(content);
      return {
        'success': true,
        'paraphrased_text': parsed['paraphrased_text'] ?? text,
      };
    } catch (e) {
      return {
        'success': true,
        'paraphrased_text': result['content'],
      };
    }
  }

  /// Get grammar check using configured provider
  static Future<Map<String, dynamic>> checkGrammar(String text) async {
    final systemPrompt =
        'You are a grammar assistant. Analyze the text for errors and return JSON: {"corrected_text": "corrected version", "error_count": number, "errors": [{"original": "", "correction": "", "explanation": ""}]}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.3,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return result;
    }

    try {
      final content = result['content'] as String;
      return jsonDecode(content);
    } catch (e) {
      return {
        'corrected_text': text,
        'error_count': 0,
        'errors': [],
      };
    }
  }

  /// Chat with custom prompt using configured provider
  static Future<Map<String, dynamic>> chatWithCustomPrompt(
    String message,
    String systemPrompt,
  ) async {
    return await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: message,
      temperature: 0.7,
      requireJson: false,
    );
  }

  /// Translate text using configured provider
  static Future<Map<String, dynamic>> translateText(
    String text,
    String targetLanguage,
  ) async {
    final systemPrompt =
        'You are a professional translator. Translate the following text to $targetLanguage. Return a JSON response with: {"translated_text": "the translated text", "source_language": "detected language", "confidence": 0.95}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.3,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return {
        'translated_text': text,
        'source_language': 'unknown',
        'confidence': 0,
        'error': result['error'],
      };
    }

    try {
      final content = result['content'] as String;
      return jsonDecode(content);
    } catch (e) {
      return {
        'translated_text': text,
        'source_language': 'unknown',
        'confidence': 0,
        'error': e.toString(),
      };
    }
  }

  /// Detect AI-generated text using configured provider
  static Future<Map<String, dynamic>> detectAIText(String text) async {
    final systemPrompt =
        'You are an AI text detection expert. Analyze the following text and determine if it was likely written by AI or a human. '
        'For each sentence, assign an ai_score from 0.0 (human) to 1.0 (AI). '
        'Return JSON: {"is_ai_generated": true/false, "confidence": 0.85, '
        '"sentences": [{"text": "exact sentence from input", "ai_score": 0.9}], '
        '"indicators": ["specific indicators"], "explanation": "detailed explanation"}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.3,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return {
        'is_ai_generated': false,
        'confidence': 0,
        'indicators': [],
        'explanation': 'Error analyzing text',
        'error': result['error'],
      };
    }

    try {
      final content = result['content'] as String;
      return jsonDecode(content);
    } catch (e) {
      return {
        'is_ai_generated': false,
        'confidence': 0,
        'indicators': [],
        'explanation': 'Error analyzing text',
        'error': e.toString(),
      };
    }
  }

  /// Summarize text using configured provider
  static Future<Map<String, dynamic>> summarizeText(
    String text, {
    String length = 'medium',
  }) async {
    final systemPrompt =
        'You are a summarization expert. Create a $length length summary of the following text. Return JSON: {"summary": "the summary", "key_points": ["point 1", "point 2"], "word_count_original": 500, "word_count_summary": 100}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.3,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return {
        'summary': text,
        'key_points': [],
        'word_count_original': text.split(' ').length,
        'word_count_summary': text.split(' ').length,
        'error': result['error'],
      };
    }

    try {
      final content = result['content'] as String;
      return jsonDecode(content);
    } catch (e) {
      return {
        'summary': text,
        'key_points': [],
        'word_count_original': text.split(' ').length,
        'word_count_summary': text.split(' ').length,
        'error': e.toString(),
      };
    }
  }

  /// Edit tone of text using configured provider
  static Future<Map<String, dynamic>> editTone(
    String text,
    String targetTone,
  ) async {
    final systemPrompt =
        'You are a tone editing expert. Rewrite the following text to match a $targetTone tone, while preserving the original meaning. Return JSON: {"edited_text": "the rewritten text", "original_tone": "assessment", "changes_made": ["change 1"]}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.5,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return {
        'edited_text': text,
        'original_tone': 'unknown',
        'changes_made': [],
        'error': result['error'],
      };
    }

    try {
      final content = result['content'] as String;
      return jsonDecode(content);
    } catch (e) {
      return {
        'edited_text': text,
        'original_tone': 'unknown',
        'changes_made': [],
        'error': e.toString(),
      };
    }
  }

  /// Paraphrase with persona using configured provider
  static Future<Map<String, dynamic>> paraphraseWithPersona(
    String text,
    String personaPrompt,
  ) async {
    final systemPrompt =
        'You are a paraphrasing assistant with a specific persona. $personaPrompt. Rewrite the text while maintaining this persona. Return JSON: {"paraphrased_text": "the rewritten text", "alternatives": ["alt 1", "alt 2"]}';

    final result = await makeRequest(
      systemPrompt: systemPrompt,
      userMessage: text,
      temperature: 0.8,
      requireJson: true,
    );

    if (result.containsKey('error')) {
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': result['error'],
      };
    }

    try {
      final content = result['content'] as String;
      final parsed = jsonDecode(content);
      return {
        'success': true,
        'paraphrased_text': parsed['paraphrased_text'] ?? text,
        'alternatives': parsed['alternatives'] ?? [],
      };
    } catch (e) {
      return {
        'success': true,
        'paraphrased_text': result['content'],
        'alternatives': [],
      };
    }
  }

  /// Paraphrase with custom prompt using configured provider
  static Future<Map<String, dynamic>> paraphraseWithCustomPrompt(
    String text,
    String customPrompt,
  ) async {
    final result = await makeRequest(
      systemPrompt: customPrompt,
      userMessage: text,
      temperature: 0.8,
      requireJson: false,
    );

    if (result.containsKey('error')) {
      return {
        'paraphrased_text': text,
        'error': result['error'],
      };
    }

    return {
      'success': true,
      'paraphrased_text': result['content'],
    };
  }

  /// Initialize the service (for compatibility with GroqService)
  static Future<void> initialize() async {
    await GroqService.initialize();
  }
}
