import 'dart:io';
import 'dart:typed_data';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:file_picker/file_picker.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:beautiful_soup_dart/beautiful_soup.dart';
import 'package:archive/archive.dart';
import 'package:xml/xml.dart' as xml;
import 'package:cunning_document_scanner/cunning_document_scanner.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:syncfusion_flutter_pdf/pdf.dart' as spdf;
import '../models/document_result.dart';
import '../utils/app_logger.dart';

/// Central service for document import, text extraction, and scanning.
///
/// Supports:
/// - File picker (PDF, DOCX, TXT)
/// - Camera document scanning with ML Kit OCR (mobile only)
/// - URL content extraction (web pages and online PDFs)
class DocumentService {
  static bool get _isMobile =>
      defaultTargetPlatform == TargetPlatform.android ||
      defaultTargetPlatform == TargetPlatform.iOS;

  // ──────────────────────────────────────────────
  //  FILE PICKER
  // ──────────────────────────────────────────────

  /// Pick a document file from the device.
  /// Returns null if the user cancels.
  static Future<DocumentResult?> pickFile({
    List<String>? allowedExtensions,
  }) async {
    try {
      final result = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: allowedExtensions ?? ['pdf', 'docx', 'doc', 'txt'],
        allowMultiple: false,
      );

      if (result == null || result.files.isEmpty) return null;

      final file = result.files.first;
      final filePath = file.path;
      if (filePath == null) return null;

      final ext = file.extension?.toLowerCase() ?? '';
      final fileName = file.name;

      switch (ext) {
        case 'pdf':
          return await extractFromPdf(filePath, fileName);
        case 'docx':
        case 'doc':
          return await extractFromDocx(filePath, fileName);
        case 'txt':
          return await _extractFromTxt(filePath, fileName);
        default:
          return await _extractFromTxt(filePath, fileName);
      }
    } catch (e) {
      AppLogger.error('DocumentService.pickFile error', e);
      return null;
    }
  }

  /// Pick specifically a PDF file.
  static Future<DocumentResult?> pickPdf() async {
    return pickFile(allowedExtensions: ['pdf']);
  }

  /// Pick specifically a DOCX file.
  static Future<DocumentResult?> pickDocx() async {
    return pickFile(allowedExtensions: ['docx', 'doc']);
  }

  // ──────────────────────────────────────────────
  //  CAMERA DOCUMENT SCANNING (mobile only)
  // ──────────────────────────────────────────────

  /// Scan a physical document using the camera and extract text via OCR.
  /// Returns null if scanning is cancelled or fails.
  /// Only available on Android/iOS.
  static Future<DocumentResult?> scanDocument() async {
    if (!_isMobile) {
      AppLogger.warning('Document scanning is only available on mobile');
      return null;
    }

    try {
      final List<String> imagePaths = await CunningDocumentScanner.getPictures(
            isGalleryImportAllowed: true,
          ) ??
          [];

      if (imagePaths.isEmpty) return null;

      final textRecognizer = TextRecognizer();
      final extractedTexts = <String>[];

      try {
        for (final path in imagePaths) {
          final inputImage = InputImage.fromFilePath(path);
          final recognizedText = await textRecognizer.processImage(inputImage);
          if (recognizedText.text.isNotEmpty) {
            extractedTexts.add(recognizedText.text);
          }
        }
      } finally {
        textRecognizer.close();
      }

      final fullText = extractedTexts.join('\n\n');

      return DocumentResult(
        text: fullText,
        type: DocumentType.scan,
        pageCount: imagePaths.length,
        imagePaths: imagePaths,
        title:
            'Scanned Document (${imagePaths.length} page${imagePaths.length > 1 ? 's' : ''})',
      );
    } catch (e) {
      AppLogger.error('DocumentService.scanDocument error', e);
      return null;
    }
  }

  // ──────────────────────────────────────────────
  //  URL IMPORT
  // ──────────────────────────────────────────────

  /// Import content from a URL.
  /// Handles both web pages (HTML extraction) and direct PDF links.
  static Future<DocumentResult?> importFromUrl(String url) async {
    try {
      if (url.trim().isEmpty) return null;

      var cleanUrl = url.trim();
      Uri? uri = Uri.tryParse(cleanUrl);
      if (uri == null) return null;
      if (!uri.hasScheme) {
        uri = Uri.parse('https://$cleanUrl');
      }

      // HEAD request to check content type
      http.Response? headResponse;
      try {
        headResponse = await http.head(uri).timeout(
              const Duration(seconds: 10),
            );
      } catch (_) {
        // HEAD may not be supported — fall through to GET
      }

      final contentType =
          headResponse?.headers['content-type']?.toLowerCase() ?? '';

      if (contentType.contains('application/pdf') ||
          cleanUrl.toLowerCase().endsWith('.pdf')) {
        return await _importPdfFromUrl(uri);
      }

      return await _importHtmlFromUrl(uri);
    } catch (e) {
      AppLogger.error('DocumentService.importFromUrl error', e);
      return null;
    }
  }

  // ──────────────────────────────────────────────
  //  PDF EXTRACTION
  // ──────────────────────────────────────────────

  /// Extract text from a local PDF file using Syncfusion PDF library.
  /// Handles FlateDecode, CIDFont/CMap, ToUnicode, and all standard encodings.
  static Future<DocumentResult?> extractFromPdf(
      String filePath, String fileName) async {
    try {
      final file = File(filePath);
      final bytes = await file.readAsBytes();

      final document = spdf.PdfDocument(inputBytes: bytes);
      final pageCount = document.pages.count;

      // Extract text from all pages
      final extractor = spdf.PdfTextExtractor(document);
      final text = extractor.extractText();
      document.dispose();

      return DocumentResult(
        text: text,
        filePath: filePath,
        type: DocumentType.pdf,
        pageCount: pageCount,
        title: fileName,
      );
    } catch (e) {
      AppLogger.error('PDF extraction error', e);
      return DocumentResult(
        text: '',
        filePath: filePath,
        type: DocumentType.pdf,
        title: fileName,
      );
    }
  }

  // ──────────────────────────────────────────────
  //  DOCX EXTRACTION
  // ──────────────────────────────────────────────

  /// Extract text from a DOCX file.
  /// DOCX is a ZIP archive; we read word/document.xml and extract text nodes.
  static Future<DocumentResult?> extractFromDocx(
      String filePath, String fileName) async {
    try {
      final file = File(filePath);
      final bytes = await file.readAsBytes();

      // Decode the ZIP archive
      final archive = ZipDecoder().decodeBytes(bytes);

      // Find word/document.xml
      final docFile = archive.files.firstWhere(
        (f) => f.name == 'word/document.xml',
        orElse: () => archive.files.firstWhere(
          (f) => f.name.endsWith('document.xml'),
          orElse: () => throw Exception('No document.xml found in DOCX'),
        ),
      );

      final xmlContent = String.fromCharCodes(docFile.content as List<int>);
      final document = xml.XmlDocument.parse(xmlContent);

      // Extract text from w:t elements (Word text runs)
      final textBuffer = StringBuffer();
      final body = document.findAllElements('w:body').firstOrNull;
      if (body != null) {
        for (final paragraph in body.findAllElements('w:p')) {
          final paragraphText =
              paragraph.findAllElements('w:t').map((t) => t.innerText).join();
          if (paragraphText.isNotEmpty) {
            textBuffer.writeln(paragraphText);
          }
        }
      } else {
        // Fallback: extract all w:t elements
        final allText =
            document.findAllElements('w:t').map((t) => t.innerText).join(' ');
        textBuffer.write(allText);
      }

      final text = textBuffer.toString().trim();

      return DocumentResult(
        text: text,
        filePath: filePath,
        type: DocumentType.docx,
        title: fileName,
      );
    } catch (e) {
      AppLogger.error('DOCX extraction error', e);
      return DocumentResult(
        text: '',
        filePath: filePath,
        type: DocumentType.docx,
        title: fileName,
      );
    }
  }

  // ──────────────────────────────────────────────
  //  TXT EXTRACTION
  // ──────────────────────────────────────────────

  static Future<DocumentResult?> _extractFromTxt(
      String filePath, String fileName) async {
    try {
      final file = File(filePath);
      final text = await file.readAsString();
      return DocumentResult(
        text: text,
        filePath: filePath,
        type: DocumentType.txt,
        title: fileName,
      );
    } catch (e) {
      AppLogger.error('TXT read error', e);
      return null;
    }
  }

  // ──────────────────────────────────────────────
  //  URL IMPORT HELPERS
  // ──────────────────────────────────────────────

  static Future<DocumentResult?> _importPdfFromUrl(Uri uri) async {
    try {
      final response = await http.get(uri).timeout(const Duration(seconds: 30));
      if (response.statusCode != 200) return null;

      final tempDir = await getTemporaryDirectory();
      final tempFile = File(
          '${tempDir.path}/import_${DateTime.now().millisecondsSinceEpoch}.pdf');
      await tempFile.writeAsBytes(response.bodyBytes);

      final fileName = uri.pathSegments.isNotEmpty
          ? uri.pathSegments.last
          : 'web_document.pdf';

      final result = await extractFromPdf(tempFile.path, fileName);
      if (result != null) {
        return DocumentResult(
          text: result.text,
          filePath: result.filePath,
          type: DocumentType.url,
          pageCount: result.pageCount,
          title: fileName,
          sourceUrl: uri.toString(),
        );
      }
      return null;
    } catch (e) {
      AppLogger.error('PDF URL import error', e);
      return null;
    }
  }

  static Future<DocumentResult?> _importHtmlFromUrl(Uri uri) async {
    try {
      final response = await http.get(uri, headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; Rewordium/1.0)',
      }).timeout(const Duration(seconds: 15));

      if (response.statusCode != 200) return null;

      final soup = BeautifulSoup(response.body);

      // Extract title
      final titleTag = soup.find('title');
      final pageTitle = titleTag?.text.trim() ?? uri.host;

      // Remove non-content elements
      for (final tag in [
        'script',
        'style',
        'nav',
        'footer',
        'header',
        'aside',
        'noscript',
        'iframe',
        'form',
      ]) {
        soup.findAll(tag).forEach((el) => el.extract());
      }

      // Try common article containers
      String articleText = '';
      final articleSelectors = [
        'article',
        'main',
        '[role="main"]',
        '.post-content',
        '.article-content',
        '.entry-content',
        '#content',
        '.content',
      ];

      for (final selector in articleSelectors) {
        final element = soup.find('', selector: selector);
        if (element != null) {
          articleText = element.getText().trim();
          if (articleText.length > 100) break;
        }
      }

      // Fallback: body text
      if (articleText.length < 100) {
        final body = soup.find('body');
        articleText = body?.getText().trim() ?? soup.getText().trim();
      }

      // Clean up whitespace
      articleText = articleText
          .replaceAll(RegExp(r'\n{3,}'), '\n\n')
          .replaceAll(RegExp(r' {2,}'), ' ')
          .trim();

      if (articleText.isEmpty) return null;

      return DocumentResult(
        text: articleText,
        type: DocumentType.url,
        title: pageTitle,
        sourceUrl: uri.toString(),
      );
    } catch (e) {
      AppLogger.error('HTML URL import error', e);
      return null;
    }
  }
}
