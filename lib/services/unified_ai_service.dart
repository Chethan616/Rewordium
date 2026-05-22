import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:firebase_auth/firebase_auth.dart';

import '../models/advanced_ai_settings.dart';
import 'firebase_service.dart';
import 'groq_service.dart';
import 'in_app_review_service.dart';
import 'usage_analytics_service.dart';

/// Unified AI Service that supports multiple LLM providers
/// Falls back to Groq + Qwen when no custom API key is configured
class UnifiedAIService {
  /// Hard cap on user-provided text to keep requests within model context and
  /// avoid runaway token spend. Anything longer is truncated client-side.
  static const int _maxUserChars = 8000;

  /// Strips qwen3's chain-of-thought `<think>...</think>` blocks. Keeps the
  /// regex DOTALL-style so multiline thought blocks are removed cleanly.
  static String _stripThinkTags(String text) {
    return text
        .replaceAll(RegExp(r'<think>[\s\S]*?</think>', caseSensitive: false), '')
        // Strip a leading `<think>` left orphaned by a max_tokens cutoff.
        .replaceFirst(RegExp(r'^\s*<think>[\s\S]*$'), '')
        .trim();
  }

  /// Trim and bound user input before it crosses the wire. Strips control
  /// chars that Groq rejects (NUL, lone surrogates) without touching the
  /// visible text.
  static String _sanitizeUserText(String text) {
    final cleaned = text
        // strip C0 control chars except \t \n \r — they break OpenAI-style JSON validators
        .replaceAll(RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]'), '')
        .trim();
    if (cleaned.length <= _maxUserChars) return cleaned;
    return '${cleaned.substring(0, _maxUserChars)}\n\n[truncated]';
  }

  /// Extract the first JSON object from a string, tolerating leading/trailing
  /// noise (whitespace, stray `<think>` debris, markdown fences) that some
  /// models emit even with response_format=json_object. Returns null if no
  /// `{...}` block is found.
  static Map<String, dynamic>? _extractJson(String raw) {
    if (raw.isEmpty) return null;
    final trimmed = _stripThinkTags(raw)
        // strip ```json fences if the model wrapped output despite json mode
        .replaceAll(RegExp(r'```(?:json)?\s*', caseSensitive: false), '')
        .replaceAll('```', '')
        .trim();
    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is Map<String, dynamic>) return decoded;
    } catch (_) {/* fall through */}

    final start = trimmed.indexOf('{');
    final end = trimmed.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    try {
      final decoded = jsonDecode(trimmed.substring(start, end + 1));
      if (decoded is Map<String, dynamic>) return decoded;
    } catch (_) {/* fall through */}
    return null;
  }

  /// Redact Bearer tokens before logging. Keeps the first 5 chars so the
  /// developer can sanity-check which key was used.
  static String _redact(String s) =>
      s.replaceAllMapped(RegExp(r'(gsk_)[A-Za-z0-9_-]+'),
          (m) => '${m[1]}***');

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
              '⚠️ Advanced AI Settings are enabled but no API key is provided.\n\nPlease go to Settings →’ Advanced AI Settings and either:\n• Enter a valid API key, or\n• Disable Advanced AI Settings to use the default Groq service',
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
              '⚠️ Invalid API key.\n\nThe API key you provided is not valid. Please check your API key in Settings →’ Advanced AI Settings.',
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
              '⚠️ Custom endpoint URL is missing or invalid.\n\nPlease provide a valid API endpoint URL in Settings →’ Advanced AI Settings.',
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

  /// Make request to Groq (default).
  ///
  /// Reliability notes for qwen3-32b on Groq:
  ///   * qwen3 emits `<think>...</think>` chain-of-thought BEFORE the answer.
  ///     With `response_format=json_object`, Groq validates the model output
  ///     as JSON and rejects anything that has reasoning prefixed — that
  ///     surfaces as `400 Failed to validate JSON`. We send
  ///     `reasoning_effort: "none"` and append `/no_think` to the system
  ///     prompt to disable it at both API and model level.
  ///   * Default `max_tokens` is 1024. Short caps (300) used to truncate JSON
  ///     strings mid-value, which surfaces as the same 400.
  static Future<Map<String, dynamic>> _makeGroqRequest({
    required String systemPrompt,
    required String userMessage,
    required double temperature,
    required bool requireJson,
    String model = 'qwen/qwen3-32b',
    int? maxTokens,
  }) async {
    try {
      await GroqService.initialize();
      final apiKey = await GroqService.getApiKey();

      if (apiKey.isEmpty) {
        throw Exception('Groq API key is not configured');
      }

      final sanitizedUser = _sanitizeUserText(userMessage);
      final isQwen3 = model.startsWith('qwen/qwen3');
      // Belt-and-suspenders: API-level + model-level disable of thinking.
      final effectiveSystemPrompt = isQwen3
          ? (requireJson
              ? '$systemPrompt\n\nReturn ONLY valid JSON. /no_think'
              : '$systemPrompt\n\n/no_think')
          : systemPrompt;

      final body = <String, dynamic>{
        'model': model,
        'messages': [
          {'role': 'system', 'content': effectiveSystemPrompt},
          {'role': 'user', 'content': sanitizedUser},
        ],
        'temperature': temperature,
        'top_p': 0.95,
        'presence_penalty': 0.3,
        'max_tokens': maxTokens ?? 1024,
        if (requireJson) 'response_format': {'type': 'json_object'},
        if (isQwen3) 'reasoning_effort': 'none',
      };

      final response = await http
          .post(
            Uri.parse('https://api.groq.com/openai/v1/chat/completions'),
            headers: {
              'Content-Type': 'application/json; charset=utf-8',
              'Authorization': 'Bearer $apiKey',
            },
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        // Decode as UTF-8 so emojis/em-dashes round-trip correctly.
        final data = jsonDecode(utf8.decode(response.bodyBytes));
        final rawContent =
            (data['choices'][0]['message']['content'] as String?) ?? '';
        final content = _stripThinkTags(rawContent);

        return {
          'success': true,
          'content': content,
          'provider': 'groq',
        };
      } else if (response.statusCode == 429) {
        if (kDebugMode) _logGroqFailure(response, 'rate_limit');
        return {
          'error': 'RATE_LIMIT',
          'errorType': 'RATE_LIMIT',
          'content':
              '⚠️ Groq API rate limit exceeded. Please wait a moment and try again.',
        };
      } else if (response.statusCode == 401) {
        if (kDebugMode) _logGroqFailure(response, 'auth');
        return {
          'error': 'INVALID_API_KEY',
          'errorType': 'INVALID_API_KEY',
          'content':
              '⚠️ Invalid Groq API key. Please check your configuration.',
        };
      } else if (response.statusCode == 400 &&
          response.body.contains('Failed to validate JSON') &&
          requireJson) {
        // Groq's strict JSON validator rejected the model output. Retry once
        // without response_format and rely on _extractJson at the call site
        // to recover. Avoids hard-failing on transient model quirks.
        if (kDebugMode) _logGroqFailure(response, 'json_validation_retry');
        return _makeGroqRequest(
          systemPrompt:
              '$systemPrompt\n\nReturn only a single JSON object. No prose, no markdown.',
          userMessage: userMessage,
          temperature: temperature,
          requireJson: false,
          model: model,
          maxTokens: maxTokens,
        );
      } else {
        if (kDebugMode) _logGroqFailure(response, 'http_${response.statusCode}');
        throw Exception(
            'Groq API error: ${response.statusCode} - ${_truncate(response.body, 240)}');
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Groq request error: ${_redact(e.toString())}');
      }
      return {
        'error': e.toString(),
        'content': 'Error connecting to Groq. Please check your connection.',
      };
    }
  }

  static String _truncate(String s, int max) =>
      s.length <= max ? s : '${s.substring(0, max)}…';

  static void _logGroqFailure(http.Response response, String reason) {
    debugPrint(
        '[Groq] $reason status=${response.statusCode} body=${_truncate(response.body, 400)}');
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
        final content = _stripThinkTags(data['choices'][0]['message']['content']);

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
        final content = _stripThinkTags(data['choices'][0]['message']['content']);

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
    final config = await AdvancedAISettingsService.getAPIConfig();
    final isDefaultGroq = config['provider'] == 'groq';

    const systemPromptTemplate =
        'You are an expert email writer and editor with years of professional writing experience. Rewrite the text in a {TONE} tone, keeping the meaning intact but using fresh, natural language that sounds like a real person wrote it. Return a JSON response with: {"paraphrased_text": "the rewritten text"}';
    final systemPrompt = systemPromptTemplate.replaceAll('{TONE}', tone);

    final result = isDefaultGroq
        ? await _makeGroqRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
            requireJson: true,
            model: 'qwen/qwen3-32b',
            maxTokens: 1024,
          )
        : await makeRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
            requireJson: true,
          );

    if (result.containsKey('error')) {
      return result;
    }

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null && parsed['paraphrased_text'] is String) {
      return {
        'success': true,
        'paraphrased_text': parsed['paraphrased_text'] as String,
      };
    }
    return {
      'success': true,
      'paraphrased_text': (result['content'] as String?)?.trim() ?? text,
    };
  }

  /// Get grammar check using configured provider
  static Future<Map<String, dynamic>> checkGrammar(String text) async {
    const systemPrompt =
        'You are an expert editor and proofreader. Fix grammar, spelling, and punctuation errors in the text below, keeping the writer\'s voice intact. Return JSON: {"corrected_text": "corrected version", "error_count": number, "errors": [{"original": "", "correction": "", "explanation": ""}]}';

    final config = await AdvancedAISettingsService.getAPIConfig();
    final isDefaultGroq = config['provider'] == 'groq';

    final result = isDefaultGroq
        ? await _makeGroqRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.3,
            requireJson: true,
            model: 'qwen/qwen3-32b',
            maxTokens: 1024,
          )
        : await makeRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.3,
            requireJson: true,
          );

    if (result.containsKey('error')) {
      return result;
    }

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) return parsed;
    return {
      'corrected_text': text,
      'error_count': 0,
      'errors': [],
    };
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
    final systemPromptTemplate =
        'You are a professional translator with native-level fluency. Translate the text into {LANG}, preserving the original meaning, tone, and style naturally — avoid literal or robotic phrasing. Return a JSON response with: {"translated_text": "the translated text", "source_language": "detected language", "confidence": 0.95}';
    final systemPrompt = systemPromptTemplate.replaceAll('{LANG}', targetLanguage);

    final config = await AdvancedAISettingsService.getAPIConfig();
    final isDefaultGroq = config['provider'] == 'groq';

    final result = isDefaultGroq
        ? await _makeGroqRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.3,
            requireJson: true,
            model: 'qwen/qwen3-32b',
            maxTokens: 1024,
          )
        : await makeRequest(
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

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) return parsed;
    return {
      'translated_text': text,
      'source_language': 'unknown',
      'confidence': 0,
    };
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

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) return parsed;
    return {
      'is_ai_generated': false,
      'confidence': 0,
      'indicators': [],
      'explanation': 'Could not parse model response.',
    };
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

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) return parsed;
    return {
      'summary': text,
      'key_points': [],
      'word_count_original': text.split(' ').length,
      'word_count_summary': text.split(' ').length,
    };
  }

  /// Edit tone of text using configured provider
  static Future<Map<String, dynamic>> editTone(
    String text,
    String targetTone,
  ) async {
    final systemPromptTemplate =
        'You are an expert email writer and editor. Rewrite the text to match a {TONE} tone, keeping the original meaning and making it sound natural — not corporate or robotic. Return JSON: {"edited_text": "the rewritten text", "original_tone": "assessment", "changes_made": ["change 1"]}';
    final systemPrompt = systemPromptTemplate.replaceAll('{TONE}', targetTone);

    final config = await AdvancedAISettingsService.getAPIConfig();
    final isDefaultGroq = config['provider'] == 'groq';

    final result = isDefaultGroq
        ? await _makeGroqRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
            requireJson: true,
            model: 'qwen/qwen3-32b',
            maxTokens: 1024,
          )
        : await makeRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
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

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) return parsed;
    return {
      'edited_text': text,
      'original_tone': 'unknown',
      'changes_made': [],
    };
  }

  /// Paraphrase with persona using configured provider
  static Future<Map<String, dynamic>> paraphraseWithPersona(
    String text,
    String personaPrompt,
  ) async {
    final systemPrompt =
        '$personaPrompt Write naturally and keep the original meaning. Return JSON: {"paraphrased_text": "the rewritten text", "alternatives": ["alt 1", "alt 2"]}';

    final config = await AdvancedAISettingsService.getAPIConfig();
    final isDefaultGroq = config['provider'] == 'groq';

    final result = isDefaultGroq
        ? await _makeGroqRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
            requireJson: true,
            model: 'qwen/qwen3-32b',
            maxTokens: 1024,
          )
        : await makeRequest(
            systemPrompt: systemPrompt,
            userMessage: text,
            temperature: 0.9,
            requireJson: true,
          );

    if (result.containsKey('error')) {
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': result['error'],
      };
    }

    final parsed = _extractJson(result['content'] as String? ?? '');
    if (parsed != null) {
      return {
        'success': true,
        'paraphrased_text': parsed['paraphrased_text'] ?? text,
        'alternatives': parsed['alternatives'] ?? [],
      };
    }
    return {
        'success': true,
        'paraphrased_text': result['content'],
        'alternatives': [],
      };
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

