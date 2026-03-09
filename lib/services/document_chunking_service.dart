import 'dart:math';
import 'unified_ai_service.dart';

/// Handles large document processing by splitting into chunks,
/// processing each through the AI, and recombining.
class DocumentChunkingService {
  /// Maximum characters per chunk (conservative for most LLM context windows).
  static const int _maxChunkChars = 3000;

  /// Overlap chars between chunks to preserve context at boundaries.
  static const int _overlapChars = 200;

  /// Process a large document through any AI tool.
  ///
  /// [text] — the full document text.
  /// [processor] — async function that processes a single chunk and returns the result.
  /// [onProgress] — optional callback with (completedChunks, totalChunks).
  ///
  /// Returns the combined result string.
  static Future<String> processLargeDocument({
    required String text,
    required Future<String> Function(String chunk) processor,
    void Function(int completed, int total)? onProgress,
  }) async {
    final chunks = _splitIntoChunks(text);
    if (chunks.length == 1) {
      onProgress?.call(1, 1);
      return processor(chunks.first);
    }

    final results = <String>[];
    for (int i = 0; i < chunks.length; i++) {
      final result = await processor(chunks[i]);
      results.add(result);
      onProgress?.call(i + 1, chunks.length);
    }

    return results.join('\n\n');
  }

  /// Grammar check a large document chunk-by-chunk.
  static Future<Map<String, dynamic>> checkGrammarLarge(
    String text, {
    void Function(int, int)? onProgress,
  }) async {
    final chunks = _splitIntoChunks(text);
    if (chunks.length == 1) {
      return UnifiedAIService.checkGrammar(text);
    }

    final allErrors = <Map<String, dynamic>>[];
    final correctedParts = <String>[];
    int totalErrorCount = 0;

    for (int i = 0; i < chunks.length; i++) {
      final result = await UnifiedAIService.checkGrammar(chunks[i]);
      if (result.containsKey('error')) return result; // Bail on API error

      correctedParts.add(result['corrected_text'] ?? chunks[i]);
      totalErrorCount += (result['error_count'] as int? ?? 0);
      final errors = result['errors'] as List? ?? [];
      allErrors.addAll(errors.cast<Map<String, dynamic>>());
      onProgress?.call(i + 1, chunks.length);
    }

    return {
      'corrected_text': correctedParts.join('\n\n'),
      'error_count': totalErrorCount,
      'errors': allErrors,
    };
  }

  /// Paraphrase a large document chunk-by-chunk.
  static Future<Map<String, dynamic>> paraphraseLarge(
    String text,
    String tone, {
    void Function(int, int)? onProgress,
  }) async {
    final combined = await processLargeDocument(
      text: text,
      processor: (chunk) async {
        final result = await UnifiedAIService.paraphraseText(chunk, tone);
        if (result.containsKey('error')) {
          throw Exception(result['error']);
        }
        return result['paraphrased_text'] ?? chunk;
      },
      onProgress: onProgress,
    );
    return {'paraphrased_text': combined};
  }

  /// Translate a large document chunk-by-chunk.
  static Future<Map<String, dynamic>> translateLarge(
    String text,
    String targetLanguage, {
    void Function(int, int)? onProgress,
  }) async {
    final combined = await processLargeDocument(
      text: text,
      processor: (chunk) async {
        final result =
            await UnifiedAIService.translateText(chunk, targetLanguage);
        if (result.containsKey('error')) {
          throw Exception(result['error']);
        }
        return result['translated_text'] ?? chunk;
      },
      onProgress: onProgress,
    );
    return {'translated_text': combined};
  }

  /// Summarize a large document using chain-summarization.
  /// First summarizes each chunk, then summarizes the summaries.
  static Future<Map<String, dynamic>> summarizeLarge(
    String text, {
    String length = 'medium',
    void Function(int, int)? onProgress,
  }) async {
    final chunks = _splitIntoChunks(text);
    if (chunks.length == 1) {
      return UnifiedAIService.summarizeText(text, length: length);
    }

    // Phase 1: Summarize each chunk
    final chunkSummaries = <String>[];
    final totalSteps = chunks.length + 1; // +1 for final merge

    for (int i = 0; i < chunks.length; i++) {
      final result =
          await UnifiedAIService.summarizeText(chunks[i], length: 'short');
      if (result.containsKey('error')) return result;
      chunkSummaries.add(result['summary'] ?? chunks[i]);
      onProgress?.call(i + 1, totalSteps);
    }

    // Phase 2: Summarize the combined summaries
    final combinedSummaries = chunkSummaries.join('\n\n');
    final finalResult = await UnifiedAIService.summarizeText(
      combinedSummaries,
      length: length,
    );
    onProgress?.call(totalSteps, totalSteps);

    // Preserve original word count
    final originalWordCount =
        text.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length;
    finalResult['word_count_original'] = originalWordCount;

    return finalResult;
  }

  /// Edit tone of a large document chunk-by-chunk.
  static Future<Map<String, dynamic>> editToneLarge(
    String text,
    String targetTone, {
    void Function(int, int)? onProgress,
  }) async {
    final combined = await processLargeDocument(
      text: text,
      processor: (chunk) async {
        final result = await UnifiedAIService.editTone(chunk, targetTone);
        if (result.containsKey('error')) {
          throw Exception(result['error']);
        }
        return result['edited_text'] ?? chunk;
      },
      onProgress: onProgress,
    );
    return {'edited_text': combined, 'changes_made': []};
  }

  /// Detect AI in a large document chunk-by-chunk, then aggregate.
  static Future<Map<String, dynamic>> detectAILarge(
    String text, {
    void Function(int, int)? onProgress,
  }) async {
    final chunks = _splitIntoChunks(text);
    if (chunks.length == 1) {
      return UnifiedAIService.detectAIText(text);
    }

    final probabilities = <double>[];
    final allIndicators = <String>[];

    for (int i = 0; i < chunks.length; i++) {
      final result = await UnifiedAIService.detectAIText(chunks[i]);
      if (result.containsKey('error')) return result;

      // Extract probability
      final rawProb = result['confidence'] ?? result['ai_probability'];
      double prob = 0.5;
      if (rawProb is double) {
        prob = rawProb;
      } else if (rawProb is int) {
        prob = rawProb.toDouble();
      } else if (rawProb is String) {
        prob = double.tryParse(rawProb) ?? 0.5;
      } else if (result['is_ai_generated'] == true) {
        prob = 0.85;
      }
      if (prob > 1) prob = prob / 100;
      probabilities.add(prob.clamp(0.0, 1.0));

      final indicators = result['indicators'] as List? ?? [];
      allIndicators.addAll(indicators.map((e) => e.toString()));
      onProgress?.call(i + 1, chunks.length);
    }

    // Average probability across chunks
    final avgProb =
        probabilities.reduce((a, b) => a + b) / probabilities.length;

    return {
      'ai_probability': avgProb,
      'confidence': avgProb,
      'is_ai_generated': avgProb > 0.5,
      'indicators': allIndicators.toSet().toList(),
      'explanation':
          'Analysis based on ${chunks.length} document segments. Average AI probability: ${(avgProb * 100).toStringAsFixed(1)}%.',
    };
  }

  /// Whether the text is large enough to need chunking.
  static bool needsChunking(String text) => text.length > _maxChunkChars;

  /// Split text into chunks at sentence boundaries.
  static List<String> _splitIntoChunks(String text) {
    if (text.length <= _maxChunkChars) return [text];

    final chunks = <String>[];
    int start = 0;

    while (start < text.length) {
      int end = min(start + _maxChunkChars, text.length);

      // If not at the end, try to split at a sentence boundary
      if (end < text.length) {
        final segment = text.substring(start, end);
        // Look for last sentence-ending punctuation
        final lastPeriod = segment.lastIndexOf('. ');
        final lastExclaim = segment.lastIndexOf('! ');
        final lastQuestion = segment.lastIndexOf('? ');
        final lastNewline = segment.lastIndexOf('\n');
        final bestBreak = [lastPeriod, lastExclaim, lastQuestion, lastNewline]
            .where((i) => i > _maxChunkChars ~/ 3) // Don't break too early
            .fold(-1, max);
        if (bestBreak > 0) {
          end = start + bestBreak + 1; // Include the punctuation
        }
      }

      chunks.add(text.substring(start, end).trim());

      // Move start with overlap for context
      start = max(start + 1, end - _overlapChars);
      if (start >= text.length) break;
    }

    return chunks.where((c) => c.isNotEmpty).toList();
  }
}
