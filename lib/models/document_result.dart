/// Document types supported by the app
enum DocumentType {
  pdf,
  docx,
  txt,
  scan,
  url,
}

/// Result of importing/scanning a document
class DocumentResult {
  final String text;
  final String? filePath;
  final DocumentType type;
  final int pageCount;
  final String? title;
  final String? sourceUrl;
  final int wordCount;
  final DateTime importedAt;

  DocumentResult({
    required this.text,
    this.filePath,
    required this.type,
    this.pageCount = 1,
    this.title,
    this.sourceUrl,
    int? wordCount,
    DateTime? importedAt,
  })  : wordCount = wordCount ?? _countWords(text),
        importedAt = importedAt ?? DateTime.now();

  static int _countWords(String text) {
    if (text.trim().isEmpty) return 0;
    return text.trim().split(RegExp(r'\s+')).length;
  }

  /// Human-readable file size description
  String get typeLabel {
    switch (type) {
      case DocumentType.pdf:
        return 'PDF';
      case DocumentType.docx:
        return 'DOCX';
      case DocumentType.txt:
        return 'TXT';
      case DocumentType.scan:
        return 'Scanned';
      case DocumentType.url:
        return 'Web Import';
    }
  }

  /// Short summary for display chips
  String get summary {
    final parts = <String>[];
    if (title != null && title!.isNotEmpty) {
      parts.add(title!);
    }
    if (pageCount > 1) {
      parts.add('$pageCount pages');
    }
    parts.add('$wordCount words');
    return parts.join(' · ');
  }

  /// Whether this document is large enough to require chunked processing
  bool get isLargeDocument => wordCount > 5000;
}
