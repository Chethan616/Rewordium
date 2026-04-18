import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:m3e_collection/m3e_collection.dart';
import '../models/document_result.dart';
import '../services/document_service.dart';
import '../widgets/url_import_dialog.dart';
import '../screens/document_viewer_screen.dart';
import '../utils/doc_gate.dart';

/// A reusable widget providing document import options (file picker, camera scan, URL).
/// Drop this into any tool screen between instruction text and text input.
///
/// When a document is imported, behaviour depends on [initialToolForViewer]:
///  • If set, navigates to [DocumentViewerScreen] with the tool pre-selected.
///  • Otherwise, calls [onTextExtracted] with the extracted text.
class DocumentInputWidget extends StatefulWidget {
  /// Called when text is successfully extracted from a document.
  final void Function(String text, DocumentResult document) onTextExtracted;

  /// Called when the user taps the document info chip to view it.
  final void Function(DocumentResult document)? onViewDocument;

  /// The currently loaded document (if any). Used to show the info chip.
  final DocumentResult? currentDocument;

  /// Called when the user clears the loaded document.
  final VoidCallback? onClear;

  /// Accent color for the widget (matches the tool's theme color).
  final Color accentColor;

  /// When set, importing a document opens [DocumentViewerScreen] with this
  /// tool pre-selected (e.g. 'grammar', 'paraphrase', 'ai_detect').
  /// The "Use This Text" button in the viewer sends text back via [onTextExtracted].
  final String? initialToolForViewer;

  const DocumentInputWidget({
    super.key,
    required this.onTextExtracted,
    this.onViewDocument,
    this.currentDocument,
    this.onClear,
    this.accentColor = const Color(0xFF009B6E),
    this.initialToolForViewer,
  });

  @override
  State<DocumentInputWidget> createState() => _DocumentInputWidgetState();
}

class _DocumentInputWidgetState extends State<DocumentInputWidget> {
  bool _isLoading = false;
  String? _loadingLabel;

  static bool get _isMobile =>
      defaultTargetPlatform == TargetPlatform.android ||
      defaultTargetPlatform == TargetPlatform.iOS;

  Future<void> _pickFile() async {
    if (!await DocGate.check(context)) return;

    setState(() {
      _isLoading = true;
      _loadingLabel = 'Opening file picker...';
    });

    try {
      final result = await DocumentService.pickFile();
      if (result != null && result.text.isNotEmpty) {
        _handleImportResult(result);
      } else if (result != null && result.text.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Could not extract text from this file'),
              duration: Duration(seconds: 3),
            ),
          );
        }
      }
    } catch (e) {
      debugPrint('DocumentInputWidget._pickFile error: $e');
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
        _loadingLabel = null;
      });
    }
  }

  Future<void> _scanDocument() async {
    if (!_isMobile) return;
    if (!await DocGate.check(context)) return;

    setState(() {
      _isLoading = true;
      _loadingLabel = 'Scanning document...';
    });

    try {
      final result = await DocumentService.scanDocument();
      if (result != null && result.text.isNotEmpty) {
        _handleImportResult(result);
      } else if (result != null && result.text.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('No text found in scanned document'),
              duration: Duration(seconds: 3),
            ),
          );
        }
      }
    } catch (e) {
      debugPrint('DocumentInputWidget._scanDocument error: $e');
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
        _loadingLabel = null;
      });
    }
  }

  void _importUrl() async {
    if (!await DocGate.check(context)) return;

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => UrlImportDialog(
        onImported: (result) {
          _handleImportResult(result);
        },
      ),
    );
  }

  /// Central handler for import results. If [initialToolForViewer] is set,
  /// navigates to [DocumentViewerScreen]; otherwise calls [onTextExtracted].
  void _handleImportResult(DocumentResult result) {
    if (widget.initialToolForViewer != null && mounted) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => DocumentViewerScreen(
            document: result,
            initialTool: widget.initialToolForViewer,
            onUseText: (text) {
              widget.onTextExtracted(text, result);
            },
          ),
        ),
      );
    } else {
      widget.onTextExtracted(result.text, result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final doc = widget.currentDocument;

    // If a document is loaded, show the info chip
    if (doc != null) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: widget.accentColor.withOpacity(0.06),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: widget.accentColor.withOpacity(0.2),
          ),
        ),
        child: Row(
          children: [
            // Document type icon
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: widget.accentColor.withOpacity(0.12),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                _getDocTypeIcon(doc.type),
                color: widget.accentColor,
                size: 18,
              ),
            ),
            const SizedBox(width: 10),
            // Document info
            Expanded(
              child: GestureDetector(
                onTap: doc.type == DocumentType.pdf &&
                        widget.onViewDocument != null
                    ? () => widget.onViewDocument!(doc)
                    : null,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      doc.title ?? doc.typeLabel,
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                            fontWeight: FontWeight.w600,
                            fontSize: 13,
                          ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      doc.summary,
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant,
                            fontSize: 11,
                          ),
                    ),
                  ],
                ),
              ),
            ),
            // View button (for PDFs)
            if (doc.type == DocumentType.pdf &&
                widget.onViewDocument != null) ...[
              GestureDetector(
                onTap: () => widget.onViewDocument!(doc),
                child: Container(
                  padding: const EdgeInsets.all(6),
                  child: Icon(
                    CupertinoIcons.eye,
                    size: 18,
                    color: widget.accentColor,
                  ),
                ),
              ),
            ],
            // Clear button
            GestureDetector(
              onTap: widget.onClear,
              child: Container(
                padding: const EdgeInsets.all(6),
                child: Icon(
                  CupertinoIcons.xmark_circle_fill,
                  size: 18,
                  color: Theme.of(context)
                      .colorScheme
                      .onSurfaceVariant
                      .withValues(alpha: 0.5),
                ),
              ),
            ),
          ],
        ),
      );
    }

    // Loading state
    if (_isLoading) {
      return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        decoration: BoxDecoration(
          color: widget.accentColor.withOpacity(0.04),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: widget.accentColor.withOpacity(0.12),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 18,
              height: 18,
              child: LoadingIndicatorM3E(
                constraints: BoxConstraints(maxWidth: 18, maxHeight: 18),
              ),
            ),
            const SizedBox(width: 10),
            Text(
              _loadingLabel ?? 'Processing...',
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      );
    }

    // Collapsed state: 3 action cards (visually rich)
    final cs = Theme.of(context).colorScheme;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          // File picker card
          Expanded(
            child: _buildActionCard(
              icon: CupertinoIcons.doc_fill,
              label: 'File',
              subtitle: 'PDF, DOCX',
              color: cs.primary,
              onTap: _pickFile,
            ),
          ),
          // Camera scan card (mobile only)
          if (_isMobile) ...[
            const SizedBox(width: 8),
            Expanded(
              child: _buildActionCard(
                icon: CupertinoIcons.camera_fill,
                label: 'Scan',
                subtitle: 'Camera',
                color: cs.secondary,
                onTap: _scanDocument,
              ),
            ),
          ],
          const SizedBox(width: 8),
          // URL import card
          Expanded(
            child: _buildActionCard(
              icon: CupertinoIcons.link_circle_fill,
              label: 'URL',
              subtitle: 'Web link',
              color: cs.tertiary,
              onTap: _importUrl,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionCard({
    required IconData icon,
    required String label,
    required String subtitle,
    required Color color,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: color.withValues(alpha: 0.2),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  icon,
                  color: Theme.of(context).brightness == Brightness.dark
                      ? Colors.black
                      : Colors.white,
                  size: 20,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: color,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 10,
                  color: color.withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  IconData _getDocTypeIcon(DocumentType type) {
    switch (type) {
      case DocumentType.pdf:
        return CupertinoIcons.doc_richtext;
      case DocumentType.docx:
        return CupertinoIcons.doc_text;
      case DocumentType.txt:
        return CupertinoIcons.doc_plaintext;
      case DocumentType.scan:
        return CupertinoIcons.camera_viewfinder;
      case DocumentType.url:
        return CupertinoIcons.link;
    }
  }
}
