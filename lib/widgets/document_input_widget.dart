import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:animate_do/animate_do.dart';
import 'package:m3e_collection/m3e_collection.dart';
import '../models/document_result.dart';
import '../services/document_service.dart';
import '../widgets/url_import_dialog.dart';
import '../screens/document_viewer_screen.dart';

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

  void _importUrl() {
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
      return FadeInUp(
        duration: const Duration(milliseconds: 300),
        child: Container(
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
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
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
                    color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.5),
                  ),
                ),
              ),
            ],
          ),
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
              child: CircularProgressIndicatorM3E(
                shape: ProgressM3EShape.wavy,
                size: CircularProgressM3ESize.s,
                activeColor: widget.accentColor,
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

    // Collapsed state: 3 action buttons
    return FadeIn(
      duration: const Duration(milliseconds: 400),
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.12),
          ),
        ),
        child: Row(
          children: [
            // File picker button
            Expanded(
              child: _buildActionButton(
                icon: CupertinoIcons.doc,
                label: 'File',
                color: const Color(0xFF1E3A8A),
                onTap: _pickFile,
              ),
            ),
            // Camera scan button (mobile only)
            if (_isMobile) ...[
              const SizedBox(width: 8),
              Expanded(
                child: _buildActionButton(
                  icon: CupertinoIcons.camera,
                  label: 'Scan',
                  color: const Color(0xFF7C3AED),
                  onTap: _scanDocument,
                ),
              ),
            ],
            const SizedBox(width: 8),
            // URL import button
            Expanded(
              child: _buildActionButton(
                icon: CupertinoIcons.link,
                label: 'URL',
                color: const Color(0xFF0891B2),
                onTap: _importUrl,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: color.withOpacity(0.06),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(height: 4),
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: color,
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
