import 'dart:math';

/// Represents a single diff operation between original and modified text.
enum DiffOp { equal, insert, delete }

class DiffSegment {
  final DiffOp op;
  final String text;
  const DiffSegment(this.op, this.text);
}

/// Word-level diff service for comparing original and processed text.
/// Used by the annotated text widget to show paraphrase changes.
class TextDiffService {
  /// Compute a word-level diff between [original] and [modified].
  /// Returns a list of [DiffSegment]s (equal / insert / delete).
  static List<DiffSegment> computeWordDiff(String original, String modified) {
    final origWords = _tokenize(original);
    final modWords = _tokenize(modified);

    // Myers-like LCS-based diff on word tokens
    final lcs = _lcs(origWords, modWords);

    final result = <DiffSegment>[];
    int oi = 0, mi = 0, li = 0;

    while (oi < origWords.length || mi < modWords.length) {
      if (li < lcs.length &&
          oi < origWords.length &&
          mi < modWords.length &&
          origWords[oi] == lcs[li] &&
          modWords[mi] == lcs[li]) {
        // Equal
        _addSegment(result, DiffOp.equal, origWords[oi]);
        oi++;
        mi++;
        li++;
      } else if (li < lcs.length &&
          oi < origWords.length &&
          origWords[oi] != lcs[li]) {
        // Deleted from original
        _addSegment(result, DiffOp.delete, origWords[oi]);
        oi++;
      } else if (li < lcs.length &&
          mi < modWords.length &&
          modWords[mi] != lcs[li]) {
        // Inserted in modified
        _addSegment(result, DiffOp.insert, modWords[mi]);
        mi++;
      } else if (li >= lcs.length && oi < origWords.length) {
        _addSegment(result, DiffOp.delete, origWords[oi]);
        oi++;
      } else if (li >= lcs.length && mi < modWords.length) {
        _addSegment(result, DiffOp.insert, modWords[mi]);
        mi++;
      } else {
        break;
      }
    }

    return _mergeSegments(result);
  }

  /// Find character offsets in [text] matching [fragment].
  /// Returns list of (start, end) pairs. Used for grammar error highlighting.
  static List<(int, int)> findFragmentOffsets(String text, String fragment) {
    final results = <(int, int)>[];
    final lowerText = text.toLowerCase();
    final lowerFrag = fragment.toLowerCase();
    int start = 0;
    while (true) {
      final idx = lowerText.indexOf(lowerFrag, start);
      if (idx == -1) break;
      results.add((idx, idx + fragment.length));
      start = idx + 1;
    }
    return results;
  }

  /// Split text into word tokens preserving whitespace as separate tokens.
  static List<String> _tokenize(String text) {
    final tokens = <String>[];
    final regex = RegExp(r'(\S+|\s+)');
    for (final match in regex.allMatches(text)) {
      tokens.add(match.group(0)!);
    }
    return tokens;
  }

  /// Compute Longest Common Subsequence of two token lists.
  static List<String> _lcs(List<String> a, List<String> b) {
    final m = a.length;
    final n = b.length;

    // For very large texts, use a simplified approach
    if (m > 2000 || n > 2000) {
      return _lcsGreedy(a, b);
    }

    // Standard DP-based LCS
    final dp = List.generate(m + 1, (_) => List.filled(n + 1, 0));
    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (a[i - 1] == b[j - 1]) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }

    // Backtrack to find LCS
    final result = <String>[];
    int i = m, j = n;
    while (i > 0 && j > 0) {
      if (a[i - 1] == b[j - 1]) {
        result.add(a[i - 1]);
        i--;
        j--;
      } else if (dp[i - 1][j] > dp[i][j - 1]) {
        i--;
      } else {
        j--;
      }
    }
    return result.reversed.toList();
  }

  /// Greedy LCS for very large texts — O(n*m) worst case but fast in practice.
  static List<String> _lcsGreedy(List<String> a, List<String> b) {
    final bIndex = <String, List<int>>{};
    for (int j = 0; j < b.length; j++) {
      bIndex.putIfAbsent(b[j], () => []).add(j);
    }

    final result = <String>[];
    int lastJ = -1;
    for (int i = 0; i < a.length; i++) {
      final positions = bIndex[a[i]];
      if (positions == null) continue;
      // Binary search for first position > lastJ
      int lo = 0, hi = positions.length;
      while (lo < hi) {
        final mid = (lo + hi) >> 1;
        if (positions[mid] <= lastJ) {
          lo = mid + 1;
        } else {
          hi = mid;
        }
      }
      if (lo < positions.length) {
        result.add(a[i]);
        lastJ = positions[lo];
      }
    }
    return result;
  }

  static void _addSegment(List<DiffSegment> result, DiffOp op, String token) {
    result.add(DiffSegment(op, token));
  }

  /// Merge consecutive segments with same operation.
  static List<DiffSegment> _mergeSegments(List<DiffSegment> segments) {
    if (segments.isEmpty) return segments;
    final merged = <DiffSegment>[];
    var currentOp = segments.first.op;
    final buffer = StringBuffer(segments.first.text);

    for (int i = 1; i < segments.length; i++) {
      if (segments[i].op == currentOp) {
        buffer.write(segments[i].text);
      } else {
        merged.add(DiffSegment(currentOp, buffer.toString()));
        currentOp = segments[i].op;
        buffer.clear();
        buffer.write(segments[i].text);
      }
    }
    merged.add(DiffSegment(currentOp, buffer.toString()));
    return merged;
  }
}
