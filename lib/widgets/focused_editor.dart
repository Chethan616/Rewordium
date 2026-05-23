import 'dart:ui';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_quill/flutter_quill.dart' as quill;
import '../services/document_service.dart';
import '../widgets/url_import_dialog.dart';

/// Full-screen text editor backed by flutter_quill (11.5.x).
///
/// Wrapped in our blur-glass header + import sheet + toolbar shell, the
/// Quill editor gives us a richer writing surface (paste-styling, multi-
/// paragraph layout, scroll behavior) than a plain TextField while keeping
/// the API a simple `Future<String?>` of plain text in/out for callers.
class FocusedEditor extends StatefulWidget {
  final String initialValue;
  final String title;
  final String? hint;

  const FocusedEditor({
    super.key,
    required this.initialValue,
    required this.title,
    this.hint,
  });

  static Future<String?> open(
    BuildContext context, {
    String initialValue = '',
    String title = 'Edit Text',
    String? hint,
  }) {
    return Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => FocusedEditor(
          initialValue: initialValue,
          title: title,
          hint: hint,
        ),
      ),
    );
  }

  @override
  State<FocusedEditor> createState() => _FocusedEditorState();
}

class _FocusedEditorState extends State<FocusedEditor> {
  late final quill.QuillController _controller;
  final FocusNode _focusNode = FocusNode();
  final ScrollController _scrollController = ScrollController();
  bool _isImporting = false;

  @override
  void initState() {
    super.initState();
    _controller = quill.QuillController.basic(
      config: const quill.QuillControllerConfig(),
    );
    if (widget.initialValue.isNotEmpty) {
      // Document.insert at offset 0 seeds the doc with the caller's text.
      _controller.document = quill.Document()..insert(0, widget.initialValue);
    }
    _controller.addListener(_onTextChanged);
    // Defer focus so the page-transition can settle before the IME slides
    // up — focusing during the slide makes the surface tear for a frame.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onTextChanged);
    _controller.dispose();
    _focusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    setState(() {}); // Rebuild word/char count
  }

  /// Quill's `Document.toPlainText()` always appends a trailing `\n` to
  /// represent the document terminator. Strip it before we measure or
  /// return text to callers.
  String get _plainText {
    final raw = _controller.document.toPlainText();
    return raw.endsWith('\n') ? raw.substring(0, raw.length - 1) : raw;
  }

  int get _wordCount {
    final t = _plainText.trim();
    if (t.isEmpty) return 0;
    return t.split(RegExp(r'\s+')).length;
  }

  int get _charCount => _plainText.length;

  void _done() {
    Navigator.of(context).pop(_plainText.trim());
  }

  /// Inserts [text] into the editor at the current selection (replacing any
  /// selected range), or appends with a blank-line separator if the doc has
  /// content, or replaces the doc entirely if empty.
  void _insertText(String text) {
    if (text.isEmpty) return;
    final currentPlain = _plainText.trim();
    if (currentPlain.isEmpty) {
      _controller.document = quill.Document()..insert(0, text);
      _controller.updateSelection(
        TextSelection.collapsed(offset: text.length),
        quill.ChangeSource.local,
      );
    } else {
      final selection = _controller.selection;
      if (selection.isValid && !selection.isCollapsed) {
        // Replace the selected range.
        final start = selection.start;
        final length = selection.end - selection.start;
        _controller.replaceText(
          start,
          length,
          text,
          TextSelection.collapsed(offset: start + text.length),
        );
      } else {
        // Append at the end, with a blank-line separator.
        // document.length includes the trailing \n terminator, so we insert
        // just before it (length - 1).
        final insertAt = _controller.document.length - 1;
        final raw = _controller.document.toPlainText();
        final separator = raw.endsWith('\n\n') ? '' : '\n\n';
        _controller.replaceText(
          insertAt,
          0,
          '$separator$text',
          TextSelection.collapsed(offset: insertAt + separator.length + text.length),
        );
      }
    }
    setState(() {});
  }

  /// Shows the import bottom sheet with File and URL options.
  void _showImportSheet() {
    HapticFeedback.selectionClick();
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetCtx) => _ImportBottomSheet(
        onFileImport: () async {
          Navigator.of(sheetCtx).pop();
          setState(() => _isImporting = true);
          try {
            final result = await DocumentService.pickFile();
            if (result != null && result.text.isNotEmpty) {
              _insertText(result.text);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                  content: Text('Imported: ${result.title ?? 'document'}'),
                  duration: const Duration(seconds: 2),
                ));
              }
            } else if (result != null && mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                content: Text('No text could be extracted from this file'),
              ));
            }
          } finally {
            if (mounted) setState(() => _isImporting = false);
          }
        },
        onUrlImport: () {
          Navigator.of(sheetCtx).pop();
          Future.delayed(const Duration(milliseconds: 200), () {
            if (!mounted) return;
            showModalBottomSheet(
              context: context,
              backgroundColor: Colors.transparent,
              isScrollControlled: true,
              builder: (_) => UrlImportDialog(
                onImported: (result) {
                  _insertText(result.text);
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text('Imported: ${result.title ?? result.sourceUrl ?? 'URL'}'),
                      duration: const Duration(seconds: 2),
                    ));
                  }
                },
              ),
            );
          });
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      extendBodyBehindAppBar: true,
      extendBody: true,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(70),
        child: ClipRRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(
              color: cs.surface.withValues(alpha: 0.7),
              padding: EdgeInsets.only(
                  top: MediaQuery.of(context).padding.top + 8,
                  left: 16,
                  right: 16,
                  bottom: 8),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: cs.surfaceContainerHighest.withValues(alpha: 0.5),
                        shape: BoxShape.circle,
                      ),
                      child: Icon(CupertinoIcons.chevron_back,
                          color: cs.onSurface, size: 20),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          widget.title,
                          style: Theme.of(context)
                              .textTheme
                              .titleMedium
                              ?.copyWith(fontWeight: FontWeight.w600),
                        ),
                        Text(
                          '$_wordCount words · $_charCount chars',
                          style: Theme.of(context)
                              .textTheme
                              .labelSmall
                              ?.copyWith(color: cs.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                  GestureDetector(
                    onTap: _done,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
                      decoration: BoxDecoration(
                        color: cs.primary,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        'Done',
                        style: Theme.of(context).textTheme.labelMedium?.copyWith(
                              color: cs.onPrimary,
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      body: Stack(
        children: [
          SafeArea(
            top: false,
            bottom: false,
            child: quill.QuillEditor(
              controller: _controller,
              focusNode: _focusNode,
              scrollController: _scrollController,
              config: quill.QuillEditorConfig(
                placeholder: widget.hint ?? 'Start typing…',
                padding: EdgeInsets.fromLTRB(
                    20, MediaQuery.of(context).padding.top + 80, 20, 16),
                customStyles: quill.DefaultStyles(
                  paragraph: quill.DefaultTextBlockStyle(
                    Theme.of(context).textTheme.bodyLarge!.copyWith(
                          fontSize: 17,
                          height: 1.6,
                          color: cs.onSurface,
                        ),
                    const quill.HorizontalSpacing(0, 0),
                    const quill.VerticalSpacing(0, 0),
                    const quill.VerticalSpacing(0, 0),
                    null,
                  ),
                ),
              ),
            ),
          ),
          // Importing overlay
          if (_isImporting)
            Positioned.fill(
              child: Container(
                color: cs.surface.withValues(alpha: 0.75),
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: cs.primary),
                      const SizedBox(height: 16),
                      Text('Extracting text…',
                          style: Theme.of(context).textTheme.bodyMedium),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        minimum: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: [
            _ToolbarButton(
              icon: CupertinoIcons.doc_on_clipboard,
              label: 'Paste',
              onTap: () async {
                final data = await Clipboard.getData(Clipboard.kTextPlain);
                final text = data?.text;
                if (text == null || text.isEmpty) return;
                final selection = _controller.selection;
                final start = selection.isValid
                    ? selection.start
                    : _controller.document.length - 1;
                final length = selection.isValid
                    ? selection.end - selection.start
                    : 0;
                _controller.replaceText(
                  start,
                  length,
                  text,
                  TextSelection.collapsed(offset: start + text.length),
                );
              },
            ),
            const SizedBox(width: 8),
            _ToolbarButton(
              icon: CupertinoIcons.delete,
              label: 'Clear',
              onTap: _plainText.trim().isEmpty
                  ? null
                  : () {
                      HapticFeedback.selectionClick();
                      _controller.clear();
                    },
            ),
            const Spacer(),
            // ── Import button ──────────────────────────
            _ImportButton(
              onTap: _isImporting ? null : _showImportSheet,
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────
// Import Bottom Sheet
// ─────────────────────────────────────────────────────────

class _ImportBottomSheet extends StatelessWidget {
  final VoidCallback onFileImport;
  final VoidCallback onUrlImport;

  const _ImportBottomSheet({
    required this.onFileImport,
    required this.onUrlImport,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 20,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Handle
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: cs.onSurfaceVariant.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'Import Content',
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(
            'Choose a source to import text from',
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: cs.onSurfaceVariant),
          ),
          const SizedBox(height: 20),
          // File option
          _ImportOptionTile(
            icon: CupertinoIcons.doc_fill,
            iconColor: cs.primary,
            title: 'From File',
            subtitle: 'PDF · DOCX · TXT · MD',
            onTap: onFileImport,
          ),
          const SizedBox(height: 10),
          // URL option
          _ImportOptionTile(
            icon: CupertinoIcons.link_circle_fill,
            iconColor: const Color(0xFF0891B2),
            title: 'From URL',
            subtitle: 'Web pages · Online PDFs · Google Docs',
            onTap: onUrlImport,
          ),
        ],
      ),
    );
  }
}

class _ImportOptionTile extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _ImportOptionTile({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          decoration: BoxDecoration(
            color: iconColor.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: iconColor.withValues(alpha: 0.15)),
          ),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: iconColor,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: Colors.white, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                            color: iconColor,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: cs.onSurfaceVariant,
                            fontSize: 11,
                          ),
                    ),
                  ],
                ),
              ),
              Icon(CupertinoIcons.chevron_right,
                  size: 16, color: cs.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────
// Import FAB-style button (bottom-right)
// ─────────────────────────────────────────────────────────

class _ImportButton extends StatefulWidget {
  final VoidCallback? onTap;
  const _ImportButton({this.onTap});

  @override
  State<_ImportButton> createState() => _ImportButtonState();
}

class _ImportButtonState extends State<_ImportButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final enabled = widget.onTap != null;

    return GestureDetector(
      onTapDown: enabled ? (_) => setState(() => _pressed = true) : null,
      onTapUp: enabled ? (_) => setState(() => _pressed = false) : null,
      onTapCancel: enabled ? () => setState(() => _pressed = false) : null,
      onTap: widget.onTap,
      child: Opacity(
        opacity: enabled ? 1.0 : 0.4,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
          decoration: BoxDecoration(
            color: _pressed
                ? cs.primary.withValues(alpha: 0.85)
                : cs.primary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: cs.primary.withValues(alpha: 0.3)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                CupertinoIcons.arrow_down_doc_fill,
                size: 16,
                color: _pressed ? cs.onPrimary : cs.primary,
              ),
              const SizedBox(width: 6),
              Text(
                'Import',
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      color: _pressed ? cs.onPrimary : cs.primary,
                      fontWeight: FontWeight.w700,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────
// Generic toolbar icon button
// ─────────────────────────────────────────────────────────

class _ToolbarButton extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  const _ToolbarButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  State<_ToolbarButton> createState() => _ToolbarButtonState();
}

class _ToolbarButtonState extends State<_ToolbarButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final enabled = widget.onTap != null;

    return GestureDetector(
      onTapDown: enabled ? (_) => setState(() => _pressed = true) : null,
      onTapUp: enabled ? (_) => setState(() => _pressed = false) : null,
      onTapCancel: enabled ? () => setState(() => _pressed = false) : null,
      onTap: widget.onTap,
      child: Opacity(
        opacity: enabled ? 1.0 : 0.4,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: _pressed
                ? cs.onSurface.withValues(alpha: 0.10)
                : cs.surfaceContainerHighest.withValues(alpha: 0.55),
            shape: BoxShape.circle,
          ),
          child: Icon(widget.icon, size: 18, color: cs.onSurface),
        ),
      ),
    );
  }
}
