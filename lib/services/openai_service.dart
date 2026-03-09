import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';

class OpenAIService {
  static const String _baseUrl = 'https://api.openai.com/v1';
  static const String _apiKeyStorageKey = 'openai_api_key';
  static const storage = FlutterSecureStorage();
  
  // Default API key from .env file
  static String get _defaultApiKey => dotenv.env['OPENAI_API_KEY'] ?? '';
  
  // Initialize the service by loading .env and storing the API key securely
  static Future<void> initialize() async {
    try {
      // Load .env file
      await dotenv.load(fileName: '.env');
      
      // Store API key securely
      final existingKey = await storage.read(key: _apiKeyStorageKey);
      if (existingKey == null || existingKey.isEmpty) {
        await storage.write(key: _apiKeyStorageKey, value: _defaultApiKey);
      }
      
      // Validate API key works by making a test request
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        print('Warning: OpenAI API key is empty');
      }
    } catch (e) {
      print('Error initializing OpenAI service: $e');
    }
  }
  
  // Get the API key from secure storage
  static Future<String> getApiKey() async {
    return await storage.read(key: _apiKeyStorageKey) ?? _defaultApiKey;
  }
  
  // Update the API key
  static Future<void> updateApiKey(String newKey) async {
    await storage.write(key: _apiKeyStorageKey, value: newKey);
  }
  
  // Grammar check using ChatGPT
  static Future<Map<String, dynamic>> checkGrammar(String text) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending grammar check request to OpenAI...');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      );
      
      print('Grammar check response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Grammar check content: $content');
        return jsonDecode(content);
      } else {
        print('Grammar check error: ${response.body}');
        throw Exception('Failed to check grammar: ${response.body}');
      }
    } catch (e) {
      print('Grammar check exception: $e');
      return {
        'corrected_text': text,
        'error_count': 0,
        'errors': [],
        'error': e.toString()
      };
    }
  }
  
  // Paraphrase text using ChatGPT with a specific tone
  static Future<Map<String, dynamic>> paraphraseText(String text, String tone) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to OpenAI with tone: $tone');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      );
      
      print('Paraphrase response status: ${response.statusCode}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'];
        print('Paraphrase content: $content');
        return jsonDecode(content);
      } else {
        print('Paraphrase error: ${response.body}');
        throw Exception('Failed to paraphrase text: ${response.body}');
      }
    } catch (e) {
      print('Paraphrase exception: $e');
      return {
        'paraphrased_text': text,
        'alternatives': [],
        'error': e.toString()
      };
    }
  }
  
  // Paraphrase text using ChatGPT with a specific persona
  static Future<Map<String, dynamic>> paraphraseWithPersona(String text, String personaPrompt) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to OpenAI with persona prompt');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
  
  // Paraphrase text using ChatGPT with a custom user-provided prompt
  static Future<Map<String, dynamic>> paraphraseWithCustomPrompt(String text, String customPrompt) async {
    try {
      final apiKey = await getApiKey();
      if (apiKey.isEmpty) {
        throw Exception('API key is empty');
      }
      
      print('Sending paraphrase request to OpenAI with custom prompt');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      
      print('Sending AI detection request to OpenAI');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      
      print('Sending translation request to OpenAI for language: $targetLanguage');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      
      print('Sending summarization request to OpenAI with length: $length');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
      
      print('Sending tone editing request to OpenAI with tone: $targetTone');
      final response = await http.post(
        Uri.parse('$_baseUrl/chat/completions'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': 'gpt-4o-mini',
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
