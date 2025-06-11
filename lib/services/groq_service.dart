import 'dart:convert';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'dart:async';

// Class to store cached responses with expiration
class _CachedResponse {
  final Map<String, dynamic> data;
  final DateTime expiresAt;
  
  _CachedResponse(this.data, {Duration expiration = const Duration(minutes: 10)})
      : expiresAt = DateTime.now().add(expiration);
  
  bool get isExpired => DateTime.now().isAfter(expiresAt);
}

class GroqService {
  static const String _baseUrl = 'https://api.groq.com/openai/v1';
  static const String _apiKeyStorageKey = 'groq_api_key';
  static const storage = FlutterSecureStorage();
  
  // Cache for API responses to reduce redundant calls
  static final Map<String, _CachedResponse> _responseCache = {};
  static String? _cachedApiKey;
  static bool _isInitialized = false;
  
  // Default API key from .env file
  static String get _defaultApiKey => dotenv.env['GROQ_API_KEY'] ?? ''; //api key
  
  // Initialize the service by loading .env and storing the API key securely
  static Future<void> initialize() async {
    if (_isInitialized) {
      print('Groq service already initialized, skipping');
      return;
    }
    
    try {
      // Add a timeout to prevent hanging
      Timer(const Duration(seconds: 5), () {
        if (!_isInitialized) {
          print('Groq service initialization timed out');
          _isInitialized = true;
        }
      });
      
      // Load .env file
      await dotenv.load(fileName: '.env');
      
      // Get the API key from .env
      final envApiKey = dotenv.env['GROQ_API_KEY'] ?? '';
      print('Initializing Groq service with key starting with: ${envApiKey.isNotEmpty ? envApiKey.substring(0, 5) : "empty"}...');
      
      // Store API key securely
      final existingKey = await storage.read(key: _apiKeyStorageKey);
      if (existingKey == null || existingKey.isEmpty || existingKey != _defaultApiKey) {
        // Update the stored key if it's different from the .env key
        await storage.write(key: _apiKeyStorageKey, value: _defaultApiKey);
        print('Updated Groq API key in secure storage');
        // Clear cached API key when it changes
        _cachedApiKey = null;
      }
      
      // Validate API key works
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        print('Warning: Groq API key is empty');
      } else if (!apiKey.startsWith('gsk_')) {
        print('Warning: Groq API key format may be incorrect. Keys should start with "gsk_"');
      }
      
      // Pre-warm the cache with the API key
      _cachedApiKey = apiKey;
      _isInitialized = true;
    } catch (e) {
      print('Error initializing Groq service: $e');
      // Mark as initialized even on error to prevent repeated init attempts
      _isInitialized = true;
    }
  }
  
  // Get the API key from cache or secure storage
  static Future<String> getApiKey() async {
    // Return cached API key if available for better performance
    if (_cachedApiKey != null) {
      return _cachedApiKey!;
    }
    
    try {
      final apiKey = await storage.read(key: _apiKeyStorageKey);
      // Cache the API key for future use
      _cachedApiKey = apiKey ?? _defaultApiKey;
      return _cachedApiKey!;
    } catch (e) {
      print('Error getting API key: $e');
      _cachedApiKey = _defaultApiKey;
      return _defaultApiKey;
    }
  }
  
  // Update the API key
  static Future<void> updateApiKey(String newApiKey) async {
    try {
      await storage.write(key: _apiKeyStorageKey, value: newApiKey);
      // Clear cached API key when it changes
      _cachedApiKey = null;
    } catch (e) {
      print('Error updating API key: $e');
    }
  }
  
  // Clear response cache to free memory
  static void clearResponseCache() {
    // Remove expired cache entries
    _responseCache.removeWhere((_, value) => value.isExpired);
    
    // If cache is still large, clear older entries
    if (_responseCache.length > 20) {
      print('Clearing Groq response cache (${_responseCache.length} entries)');
      _responseCache.clear();
    }
  }
  
  // Grammar check using Groq
  static Future<Map<String, dynamic>> checkGrammar(String text) async {
    try {
      // Generate a cache key based on the text
      final cacheKey = 'grammar_${text.hashCode}';
      
      // Check if we have a cached response that's not expired
      if (_responseCache.containsKey(cacheKey) && !_responseCache[cacheKey]!.isExpired) {
        print('Using cached grammar check result');
        return _responseCache[cacheKey]!.data;
      }
      
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending grammar check request to Groq...');
      print('Using API key: ${apiKey.substring(0, 5)}...');
      
      // Use a timeout to prevent hanging requests
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          // Use a more reliable model
          'model': 'llama3-8b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a helpful grammar assistant. Analyze the following text for grammar, spelling, and punctuation errors. Return a JSON response with the following structure: {"corrected_text": "the corrected version", "error_count": number of errors found, "errors": [{"original": "original text with error", "correction": "corrected text", "explanation": "brief explanation"}]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.3,
          'response_format': {'type': 'json_object'}
        }),
      ).timeout(const Duration(seconds: 30), onTimeout: () {
        throw Exception('Request timed out. Please check your internet connection.');
      });
      
      print('Grammar check response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Grammar check content: $content');
        try {
          // Parse the JSON response
          final parsedResponse = jsonDecode(content);
          
          // Cache the successful response for future use
          final cacheKey = 'grammar_${text.hashCode}';
          _responseCache[cacheKey] = _CachedResponse(parsedResponse);
          
          return parsedResponse;
        } catch (e) {
          print('Error parsing JSON response: $e');
          return {
            'corrected_text': text,
            'error_count': 0,
            'errors': [],
            'error': 'Error parsing response: $e'
          };
        }
      } else if (response.statusCode == 401) {
        print('Grammar check error: Unauthorized - API key may be invalid');
        throw Exception('Authentication failed. Please check your API key.');
      } else if (response.statusCode == 403) {
        print('Grammar check error: Access denied');
        throw Exception('Access denied. Please check your API key permissions.');
      } else if (response.statusCode >= 500) {
        print('Grammar check error: Groq server error');
        throw Exception('Groq server error. Please try again later.');
      } else {
        print('Grammar check error: ${response.body}');
        throw Exception('Failed to check grammar: ${response.statusCode}');
      }
    } catch (e) {
      print('Grammar check exception: $e');
      String errorMessage = e.toString();
      
      // Provide more user-friendly error messages
      if (errorMessage.contains('SocketException') || 
          errorMessage.contains('Connection refused') ||
          errorMessage.contains('network') ||
          errorMessage.contains('Access denied')) {
        errorMessage = 'Network connection error. Please check your internet connection and try again.';
      } else if (errorMessage.contains('timed out')) {
        errorMessage = 'Request timed out. Please try again later.';
      } else if (errorMessage.contains('API key')) {
        errorMessage = 'API key error. Please check your Groq API key in settings.';
      }
      
      return {
        'corrected_text': text,
        'error_count': 0,
        'errors': [],
        'error': errorMessage
      };
    }
  }
  
  // Paraphrase text using Groq with a specific tone
  static Future<Map<String, dynamic>> paraphraseText(String text, String tone) async {
    try {
      // Generate a cache key based on the text and tone
      final cacheKey = 'paraphrase_${text.hashCode}_$tone';
      
      // Check if we have a cached response that's not expired
      if (_responseCache.containsKey(cacheKey) && !_responseCache[cacheKey]!.isExpired) {
        print('Using cached paraphrase result for tone: $tone');
        return _responseCache[cacheKey]!.data;
      }
      
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to Groq with tone: $tone');
      print('Using API key: ${apiKey.substring(0, 5)}...');
      
      // Use a timeout to prevent hanging requests
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          // Use a more reliable model
          'model': 'llama3-8b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a helpful paraphrasing assistant. Your task is to completely rewrite the following text in a $tone tone while preserving the original meaning but using different words and sentence structures. The rewritten text should be noticeably different from the original. Return a JSON response with the following structure: {"paraphrased_text": "the rewritten text", "alternatives": ["alternative 1", "alternative 2"]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.8,
          'response_format': {'type': 'json_object'}
        }),
      ).timeout(const Duration(seconds: 30), onTimeout: () {
        throw Exception('Request timed out. Please check your internet connection.');
      });
      
      print('Paraphrase response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Paraphrase content: $content');
        try {
          // Parse the JSON response
          final parsedResponse = jsonDecode(content);
          
          // Cache the successful response for future use
          final cacheKey = 'paraphrase_${text.hashCode}_$tone';
          _responseCache[cacheKey] = _CachedResponse(parsedResponse);
          
          return parsedResponse;
        } catch (e) {
          print('Error parsing JSON response: $e');
          return {
            'paraphrased_text': text,
            'alternatives': [],
            'error': 'Error parsing response: $e'
          };
        }
      } else if (response.statusCode == 401) {
        print('Paraphrase error: Unauthorized - API key may be invalid');
        throw Exception('Authentication failed. Please check your API key.');
      } else if (response.statusCode == 403) {
        print('Paraphrase error: Access denied');
        throw Exception('Access denied. Please check your API key permissions.');
      } else if (response.statusCode >= 500) {
        print('Paraphrase error: Groq server error');
        throw Exception('Groq server error. Please try again later.');
      } else {
        print('Paraphrase error: ${response.body}');
        throw Exception('Failed to paraphrase text: ${response.statusCode}');
      }
    } catch (e) {
      print('Paraphrase exception: $e');
      String errorMessage = e.toString();
      
      // Provide more user-friendly error messages
      if (errorMessage.contains('SocketException') || 
          errorMessage.contains('Connection refused') ||
          errorMessage.contains('network') ||
          errorMessage.contains('Access denied')) {
        errorMessage = 'Network connection error. Please check your internet connection and try again.';
      } else if (errorMessage.contains('timed out')) {
        errorMessage = 'Request timed out. Please try again later.';
      } else if (errorMessage.contains('API key')) {
        errorMessage = 'API key error. Please check your Groq API key in settings.';
      }
      
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': errorMessage
      };
    }
  }
  
  // Paraphrase text using Groq with a specific persona
  static Future<Map<String, dynamic>> paraphraseWithPersona(String text, String personaPrompt) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to Groq with persona prompt');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a helpful paraphrasing assistant. $personaPrompt Return a JSON response with the following structure: {"paraphrased_text": "the rewritten text", "alternatives": ["alternative 1", "alternative 2"]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.8,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('Persona paraphrase response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Persona paraphrase content: $content');
        return jsonDecode(content);
      } else {
        print('Persona paraphrase error: ${response.body}');
        throw Exception('Failed to paraphrase text with persona: ${response.body}');
      }
    } catch (e) {
      print('Persona paraphrase exception: $e');
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': e.toString()
      };
    }
  }
  
  // Paraphrase text using Groq with a custom user-provided prompt
  static Future<Map<String, dynamic>> paraphraseWithCustomPrompt(String text, String customPrompt) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to Groq with custom prompt');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a helpful paraphrasing assistant. Your task is to rewrite the text according to the following instructions: $customPrompt. Return a JSON response with the following structure: {"paraphrased_text": "the rewritten text", "alternatives": ["alternative 1", "alternative 2"]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.8,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('Custom paraphrase response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Custom paraphrase content: $content');
        return jsonDecode(content);
      } else {
        print('Custom paraphrase error: ${response.body}');
        throw Exception('Failed to paraphrase text with custom prompt: ${response.body}');
      }
    } catch (e) {
      print('Custom paraphrase exception: $e');
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': e.toString()
      };
    }
  }
  
  // Detect if text was written by AI
  static Future<Map<String, dynamic>> detectAIText(String text) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending AI detection request to Groq');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are an AI content detector. Analyze the following text and determine the likelihood it was written by AI. Return a JSON response with the following structure: {"ai_probability": number between 0-1, "confidence": "high/medium/low", "reasoning": "explanation of your analysis", "human_indicators": ["list of features suggesting human authorship"], "ai_indicators": ["list of features suggesting AI authorship"]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.2,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('AI detection response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('AI detection content: $content');
        return jsonDecode(content);
      } else {
        print('AI detection error: ${response.body}');
        throw Exception('Failed to detect AI text: ${response.body}');
      }
    } catch (e) {
      print('AI detection exception: $e');
      return {
        'ai_probability': 0.5,
        'confidence': 'low',
        'reasoning': 'Error analyzing text: ${e.toString()}',
        'human_indicators': [],
        'ai_indicators': [],
        'error': e.toString()
      };
    }
  }
  
  // Translate text to a target language
  static Future<Map<String, dynamic>> translateText(String text, String targetLanguage) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending translation request to Groq for language: $targetLanguage');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a professional translator. Translate the following text into $targetLanguage, maintaining the original meaning, tone, and style as closely as possible. Return a JSON response with the following structure: {"translated_text": "the translated text", "detected_source_language": "detected source language", "notes": "any relevant translation notes or cultural adaptations made"}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.3,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('Translation response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Translation content: $content');
        return jsonDecode(content);
      } else {
        print('Translation error: ${response.body}');
        throw Exception('Failed to translate text: ${response.body}');
      }
    } catch (e) {
      print('Translation exception: $e');
      return {
        'translated_text': text,
        'detected_source_language': 'unknown',
        'notes': '',
        'error': e.toString()
      };
    }
  }
  
  // Summarize text
  static Future<Map<String, dynamic>> summarizeText(String text, {String length = 'medium'}) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending summarization request to Groq with length: $length');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a text summarization expert. Create a $length summary of the following text, capturing the main points and key information. Return a JSON response with the following structure: {"summary": "the summarized text", "key_points": ["key point 1", "key point 2"], "word_count_original": original word count, "word_count_summary": summary word count}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.3,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('Summarization response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Summarization content: $content');
        return jsonDecode(content);
      } else {
        print('Summarization error: ${response.body}');
        throw Exception('Failed to summarize text: ${response.body}');
      }
    } catch (e) {
      print('Summarization exception: $e');
      return {
        'summary': text,
        'key_points': [],
        'word_count_original': text.split(' ').length,
        'word_count_summary': text.split(' ').length,
        'error': e.toString()
      };
    }
  }
  
  // Edit tone of text
  static Future<Map<String, dynamic>> editTone(String text, String targetTone) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending tone editing request to Groq with tone: $targetTone');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'llama3-70b-8192',
          'messages': [
            {
              'role': 'system',
              'content': 'You are a tone editing expert. Rewrite the following text to match a $targetTone tone, while preserving the original meaning and content. Return a JSON response with the following structure: {"edited_text": "the rewritten text with new tone", "original_tone": "assessment of the original tone", "changes_made": ["description of tone changes made"]}'
            },
            {
              'role': 'user',
              'content': text
            }
          ],
          'temperature': 0.5,
          'response_format': {'type': 'json_object'}
        }),
      );
      
      print('Tone editing response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Tone editing content: $content');
        return jsonDecode(content);
      } else {
        print('Tone editing error: ${response.body}');
        throw Exception('Failed to edit tone: ${response.body}');
      }
    } catch (e) {
      print('Tone editing exception: $e');
      return {
        'edited_text': text,
        'original_tone': 'unknown',
        'changes_made': [],
        'error': e.toString()
      };
    }
  }
}
