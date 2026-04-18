import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:m3e_collection/m3e_collection.dart';
import '../widgets/custom_button.dart';
import '../models/document_result.dart';
import '../services/export_service.dart';
import '../services/unified_ai_service.dart';
import '../services/text_diff_service.dart';
import '../services/document_chunking_service.dart';
import '../widgets/annotated_text_widget.dart';

/// Rendered document screen — CamScanner-style view for scans,
/// rich formatted text for PDFs/DOCX/TXT/URL.
/// AI tools run in-place with inline annotations on the rendered text.
class DocumentViewerScreen extends StatefulWidget {
  final DocumentResult document;

  /// Called when user taps "Use This Text" — sends text back to calling tool screen.
  final void Function(String text)? onUseText;

  /// If opened from a specific tool, pre-select that tool.
  final String? initialTool;

  const DocumentViewerScreen({
    super.key,
    required this.document,
    this.onUseText,
    this.initialTool,
  });

  @override
  State<DocumentViewerScreen> createState() => _DocumentViewerScreenState();
}

class _DocumentViewerScreenState extends State<DocumentViewerScreen> {
  // ── State ─────────────────────────────────────
  String? _selectedTool;
  bool _isProcessing = false;
  String _processingLabel = '';

  // Annotation state
  AnnotationMode _annotationMode = AnnotationMode.none;
  List<GrammarAnnotation> _grammarAnnotations = [];
  List<AIDetectionAnnotation> _aiAnnotations = [];
  List<DiffSegment> _diffSegments = [];

  // Result data
  String? _resultText;
  Map<String, dynamic>? _resultData;
  int _errorCount = 0;
  double _aiScore = 0.0;

  // Scan page view
  int _currentPage = 0;
  late PageController _pageController;

  // Split view tab (0 = original, 1 = result)
  int _splitTab = 1;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    if (widget.initialTool != null) {
      _selectedTool = widget.initialTool;
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  // ── AI Processing ─────────────────────────────

  Future<void> _runTool(String tool) async {
    if (_isProcessing) return;

    setState(() {
      _selectedTool = tool;
      _isProcessing = true;
      _processingLabel = _toolProcessingLabel(tool);
      // Clear previous annotations
      _annotationMode = AnnotationMode.none;
      _grammarAnnotations = [];
      _aiAnnotations = [];
      _diffSegments = [];
      _resultText = null;
      _resultData = null;
    });

    try {
      final text = widget.document.text;
      final useChunking = DocumentChunkingService.needsChunking(text);

      switch (tool) {
        case 'grammar':
          final result = useChunking
              ? await DocumentChunkingService.checkGrammarLarge(text)
              : await UnifiedAIService.checkGrammar(text);
          if (!mounted) return;
          if (result.containsKey('error')) {
            _showError(result['error'].toString());
            break;
          }
          _resultText = result['corrected_text'] as String? ?? text;
          _errorCount = result['error_count'] as int? ?? 0;
          _grammarAnnotations = _buildGrammarAnnotations(
            text,
            (result['errors'] as List?)?.cast<Map<String, dynamic>>() ?? [],
          );
          _annotationMode = AnnotationMode.grammar;
          break;

        case 'paraphrase':
          final result = useChunking
              ? await DocumentChunkingService.paraphraseLarge(text, 'standard')
              : await UnifiedAIService.paraphraseText(text, 'standard');
          if (!mounted) return;
          if (result.containsKey('error')) {
            _showError(result['error'].toString());
            break;
          }
          _resultText = result['paraphrased_text'] as String? ?? text;
          _diffSegments = TextDiffService.computeWordDiff(text, _resultText!);
          _annotationMode = AnnotationMode.paraphraseDiff;
          break;

        case 'translate':
          final targetLang = await _showLanguagePicker();
          if (targetLang == null || !mounted) {
            setState(() {
              _isProcessing = false;
              _selectedTool = null;
            });
            return;
          }
          final result = useChunking
              ? await DocumentChunkingService.translateLarge(text, targetLang)
              : await UnifiedAIService.translateText(text, targetLang);
          if (!mounted) return;
          if (result.containsKey('error')) {
            _showError(result['error'].toString());
            break;
          }
          _resultText = result['translated_text'] as String? ?? text;
          _annotationMode = AnnotationMode.none;
          break;

        case 'summarize':
          final result = useChunking
              ? await DocumentChunkingService.summarizeLarge(text)
              : await UnifiedAIService.summarizeText(text);
          if (!mounted) return;
          if (result.containsKey('error')) {
            _showError(result['error'].toString());
            break;
          }
          _resultText = result['summary'] as String? ?? text;
          _resultData = result;
          _annotationMode = AnnotationMode.none;
          break;

        case 'tone':
          final result = useChunking
              ? await DocumentChunkingService.editToneLarge(
                  text, 'professional')
              : await UnifiedAIService.editTone(text, 'professional');
          if (!mounted) return;
          if (result.containsKey('error')) {
            _showError(result['error'].toString());
            break;
          }
          _resultText = result['edited_text'] as String? ?? text;
          _diffSegments = TextDiffService.computeWordDiff(text, _resultText!);
          _annotationMode = AnnotationMode.paraphraseDiff;
          break;

        case 'ai_detect':
          final result = useChunking
              ? await DocumentChunkingService.detectAILarge(text)
              : await UnifiedAIService.detectAIText(text);
          if (!mounted) return;
          if (result.containsKey('error') &&
              !result.containsKey('confidence')) {
            _showError(result['error'].toString());
            break;
          }
          _resultData = result;
          _aiScore = _parseAIScore(result);
          _aiAnnotations = _buildAIAnnotations(text, result);
          _annotationMode = AnnotationMode.aiDetection;
          break;
      }
    } catch (e) {
      if (mounted) _showError(e.toString());
    }

    if (mounted) {
      setState(() => _isProcessing = false);
    }
  }

  void _showError(String msg) {
    setState(() => _isProcessing = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
          content: Text('Error: $msg'), duration: const Duration(seconds: 3)),
    );
  }

  // ── Annotation Builders ───────────────────────

  List<GrammarAnnotation> _buildGrammarAnnotations(
    String text,
    List<Map<String, dynamic>> errors,
  ) {
    final annotations = <GrammarAnnotation>[];
    for (final err in errors) {
      final original = err['original'] as String? ?? '';
      final correction = err['correction'] as String? ?? '';
      final explanation = err['explanation'] as String? ?? '';
      if (original.isEmpty) continue;

      final offsets = TextDiffService.findFragmentOffsets(text, original);
      for (final (start, end) in offsets) {
        annotations.add(GrammarAnnotation(
          start: start,
          end: end,
          original: original,
          correction: correction,
          explanation: explanation,
        ));
      }
    }
    return annotations;
  }

  List<AIDetectionAnnotation> _buildAIAnnotations(
    String text,
    Map<String, dynamic> result,
  ) {
    // Try sentence-level data first
    final sentences = result['sentences'] as List?;
    if (sentences != null && sentences.isNotEmpty) {
      return sentences
          .map((s) {
            final sentText = s['text'] as String? ?? '';
            final score = (s['ai_score'] as num?)?.toDouble() ?? 0.5;
            final offsets = TextDiffService.findFragmentOffsets(text, sentText);
            if (offsets.isEmpty) return null;
            return AIDetectionAnnotation(
              start: offsets.first.$1,
              end: offsets.first.$2,
              aiScore: score,
              text: sentText,
            );
          })
          .whereType<AIDetectionAnnotation>()
          .toList();
    }

    // Fallback: highlight entire text with document-level score
    final score = _parseAIScore(result);
    if (score > 0.3) {
      // Split into sentences and highlight all with same score
      final sentenceRegex = RegExp(r'[^.!?]+[.!?]+\s*|[^.!?]+$');
      return sentenceRegex.allMatches(text).map((m) {
        return AIDetectionAnnotation(
          start: m.start,
          end: m.end,
          aiScore: score,
          text: m.group(0) ?? '',
        );
      }).toList();
    }

    return [];
  }

  double _parseAIScore(Map<String, dynamic> result) {
    final raw = result['confidence'] ?? result['ai_probability'] ?? 0;
    double score;
    if (raw is double) {
      score = raw;
    } else if (raw is int) {
      score = raw.toDouble();
    } else if (raw is String) {
      score = double.tryParse(raw) ?? 0;
    } else {
      score = result['is_ai_generated'] == true ? 0.85 : 0.15;
    }
    if (score > 1) score = score / 100;
    return score.clamp(0.0, 1.0);
  }

  String _toolProcessingLabel(String tool) {
    switch (tool) {
      case 'grammar':
        return 'Checking grammar...';
      case 'paraphrase':
        return 'Paraphrasing...';
      case 'translate':
        return 'Translating...';
      case 'summarize':
        return 'Summarizing...';
      case 'tone':
        return 'Editing tone...';
      case 'ai_detect':
        return 'Analyzing for AI...';
      default:
        return 'Processing...';
    }
  }

  Future<String?> _showLanguagePicker() async {
    const languages = [
      'Spanish',
      'French',
      'German',
      'Italian',
      'Portuguese',
      'Russian',
      'Japanese',
      'Chinese',
      'Korean',
      'Arabic',
      'Hindi',
      'Dutch',
      'Swedish',
      'Greek',
      'Turkish',
      'Polish',
      'Vietnamese',
      'Thai',
      'Indonesian',
      'Hebrew',
    ];
    return showModalBottomSheet<String>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        final colorScheme = Theme.of(ctx).colorScheme;
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 12),
              Container(
                width: 36,
                height: 5,
                decoration: BoxDecoration(
                  color: colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              const SizedBox(height: 16),
              Text('Translate To', style: Theme.of(ctx).textTheme.titleMedium),
              const SizedBox(height: 12),
              SizedBox(
                height: 300,
                child: ListView.builder(
                  itemCount: languages.length,
                  itemBuilder: (_, i) => ListTile(
                    title: Text(languages[i]),
                    leading: const Icon(Icons.translate),
                    onTap: () => Navigator.pop(ctx, languages[i]),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showExportSheet() {
    if (!mounted) return;
    ExportService.showExportSheet(
      context,
      title: widget.document.title ?? 'Document',
      content: _resultText ?? widget.document.text,
      originalText: _resultText != null ? widget.document.text : null,
    );
  }

  void _clearAnnotations() {
    setState(() {
      _annotationMode = AnnotationMode.none;
      _grammarAnnotations = [];
      _aiAnnotations = [];
      _diffSegments = [];
      _resultText = null;
      _resultData = null;
      _selectedTool = null;
    });
  }

  // ── BUILD ─────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final isScan = widget.document.type == DocumentType.scan &&
        widget.document.imagePaths != null &&
        widget.document.imagePaths!.isNotEmpty;

    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLowest,
      appBar: _buildAppBar(),
      body: Column(
        children: [
          // Result banner (if AI processed)
          if (_annotationMode != AnnotationMode.none) _buildResultBanner(),
          // Processing indicator
          if (_isProcessing) _buildProcessingBar(),
          // Document content
          Expanded(
            child: isScan ? _buildScanView() : _buildRichTextView(),
          ),
          // AI tool bar
          _buildToolBar(),
        ],
      ),
      // "Use This Text" button
      bottomNavigationBar: widget.onUseText != null && !_isProcessing
          ? _buildUseTextButton()
          : null,
    );
  }

  PreferredSizeWidget _buildAppBar() {
    final colorScheme = Theme.of(context).colorScheme;
    final docType = widget.document.type;
    final typeIcon = docType == DocumentType.scan
        ? CupertinoIcons.camera
        : docType == DocumentType.pdf
            ? CupertinoIcons.doc_fill
            : CupertinoIcons.doc_text_fill;

    return AppBar(
      backgroundColor: colorScheme.surface,
      elevation: 0,
      scrolledUnderElevation: 1,
      leading: IconButtonM3E(
        icon: const Icon(CupertinoIcons.back),
        onPressed: () => Navigator.of(context).pop(),
        variant: IconButtonM3EVariant.standard,
      ),
      title: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(8),
            ),
            child:
                Icon(typeIcon, size: 16, color: colorScheme.onPrimaryContainer),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.document.title ?? 'Document',
                  style: Theme.of(context).textTheme.bodyLarge!.copyWith(
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                      ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  '${widget.document.wordCount} words · ${widget.document.pageCount} pg',
                  style: Theme.of(context).textTheme.bodySmall!.copyWith(
                        color: colorScheme.onSurfaceVariant,
                        fontSize: 11,
                      ),
                ),
              ],
            ),
          ),
        ],
      ),
      actions: [
        if (widget.document.type == DocumentType.pdf &&
            widget.document.filePath != null)
          IconButtonM3E(
            icon: const Icon(CupertinoIcons.arrow_up_doc, size: 20),
            onPressed: () => OpenFilex.open(widget.document.filePath!),
            variant: IconButtonM3EVariant.standard,
          ),
        IconButtonM3E(
          icon: const Icon(CupertinoIcons.share, size: 20),
          onPressed: _showExportSheet,
          variant: IconButtonM3EVariant.standard,
        ),
        if (_annotationMode != AnnotationMode.none)
          IconButtonM3E(
            icon: const Icon(CupertinoIcons.xmark_circle, size: 20),
            onPressed: _clearAnnotations,
            variant: IconButtonM3EVariant.outlined,
            size: IconButtonM3ESize.sm,
          ),
      ],
    );
  }

  // ── SCAN VIEW (CamScanner-style page images) ─

  Widget _buildScanView() {
    final paths = widget.document.imagePaths!;
    return Column(
      children: [
        // Page indicator
        Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          color: Theme.of(context).colorScheme.surface,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(CupertinoIcons.doc_richtext,
                  size: 14,
                  color: Theme.of(context).colorScheme.onSurfaceVariant),
              const SizedBox(width: 6),
              Text(
                'Page ${_currentPage + 1} of ${paths.length}',
                style: Theme.of(context).textTheme.bodySmall!.copyWith(
                      fontWeight: FontWeight.w600,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
        // Page view of scanned images
        Expanded(
          child: PageView.builder(
            controller: _pageController,
            itemCount: paths.length,
            onPageChanged: (i) => setState(() => _currentPage = i),
            itemBuilder: (context, index) {
              return Container(
                margin: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surface,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: [
                    BoxShadow(
                      color:
                          Theme.of(context).shadowColor.withValues(alpha: 0.1),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: InteractiveViewer(
                    minScale: 0.5,
                    maxScale: 4.0,
                    child: Image.file(
                      File(paths[index]),
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  // ── RICH TEXT VIEW (formatted document pages) ─

  Widget _buildRichTextView() {
    final text = widget.document.text;
    if (text.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(CupertinoIcons.doc_text,
                size: 48,
                color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 12),
            Text(
              'No text could be extracted',
              style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      );
    }

    // For translate/summarize with result but no diff, show split view
    if (_resultText != null &&
        _annotationMode == AnnotationMode.none &&
        _selectedTool != null) {
      return _buildSplitView();
    }

    // For annotated modes, show the annotated text in a document-like card
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: _buildDocumentCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Word count + copy bar
            _buildDocHeader(text),
            const SizedBox(height: 12),
            // The text content (annotated or plain)
            AnnotatedTextWidget(
              text: text,
              mode: _annotationMode,
              grammarAnnotations: _grammarAnnotations,
              aiAnnotations: _aiAnnotations,
              diffSegments: _diffSegments,
              onGrammarTap: (ann) {
                showGrammarCorrectionPopup(context, ann);
              },
            ),
          ],
        ),
      ),
    );
  }

  // ── SPLIT VIEW (translation/summary) ─────────

  Widget _buildSplitView() {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      children: [
        // Tab toggle
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          child: Row(
            children: [
              Expanded(
                child: GestureDetector(
                  onTap: () => setState(() => _splitTab = 0),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: _splitTab == 0
                          ? colorScheme.primaryContainer
                          : colorScheme.surfaceContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(CupertinoIcons.doc_text,
                            size: 14,
                            color: _splitTab == 0
                                ? colorScheme.onPrimaryContainer
                                : colorScheme.onSurfaceVariant),
                        const SizedBox(width: 6),
                        Text(
                          'Original',
                          style:
                              Theme.of(context).textTheme.labelMedium!.copyWith(
                                    fontWeight: _splitTab == 0
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                    color: _splitTab == 0
                                        ? colorScheme.onPrimaryContainer
                                        : colorScheme.onSurfaceVariant,
                                  ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: GestureDetector(
                  onTap: () => setState(() => _splitTab = 1),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: _splitTab == 1
                          ? colorScheme.primaryContainer
                          : colorScheme.surfaceContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(CupertinoIcons.sparkles,
                            size: 14,
                            color: _splitTab == 1
                                ? colorScheme.onPrimaryContainer
                                : colorScheme.onSurfaceVariant),
                        const SizedBox(width: 6),
                        Text(
                          _toolResultLabel(_selectedTool ?? ''),
                          style:
                              Theme.of(context).textTheme.labelMedium!.copyWith(
                                    fontWeight: _splitTab == 1
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                    color: _splitTab == 1
                                        ? colorScheme.onPrimaryContainer
                                        : colorScheme.onSurfaceVariant,
                                  ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        // Content
        Expanded(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 250),
            child: _splitTab == 0
                ? SingleChildScrollView(
                    key: const ValueKey('original'),
                    padding: const EdgeInsets.all(16),
                    child: _buildDocumentCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildDocHeader(widget.document.text),
                          const SizedBox(height: 12),
                          SelectableText(
                            widget.document.text,
                            style: Theme.of(context)
                                .textTheme
                                .bodyMedium!
                                .copyWith(
                                  fontSize: 13,
                                  height: 1.6,
                                  color: colorScheme.onSurfaceVariant,
                                ),
                          ),
                        ],
                      ),
                    ),
                  )
                : SingleChildScrollView(
                    key: const ValueKey('result'),
                    padding: const EdgeInsets.all(16),
                    child: _buildDocumentCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildDocHeader(_resultText!),
                          const SizedBox(height: 12),
                          SelectableText(
                            _resultText!,
                            style: Theme.of(context)
                                .textTheme
                                .bodyMedium!
                                .copyWith(
                                  fontSize: 14,
                                  height: 1.7,
                                ),
                          ),
                        ],
                      ),
                    ),
                  ),
          ),
        ),
      ],
    );
  }

  // ── RESULT BANNER ─────────────────────────────

  Widget _buildResultBanner() {
    final colorScheme = Theme.of(context).colorScheme;
    Color bannerColor;
    IconData bannerIcon;
    String bannerText;

    switch (_annotationMode) {
      case AnnotationMode.grammar:
        bannerColor = _errorCount > 0 ? colorScheme.error : Colors.green;
        bannerIcon = _errorCount > 0
            ? CupertinoIcons.exclamationmark_circle
            : CupertinoIcons.checkmark_circle;
        bannerText = _errorCount > 0
            ? '$_errorCount grammar issue${_errorCount > 1 ? 's' : ''} found — tap to see corrections'
            : 'No grammar issues found!';
        break;
      case AnnotationMode.aiDetection:
        final pct = (_aiScore * 100).toStringAsFixed(0);
        bannerColor = _aiScore >= 0.5 ? colorScheme.error : Colors.green;
        bannerIcon = CupertinoIcons.sparkles;
        bannerText = '$pct% likely AI-generated';
        break;
      case AnnotationMode.paraphraseDiff:
        bannerColor = colorScheme.primary;
        bannerIcon = CupertinoIcons.text_badge_checkmark;
        bannerText = 'Changes highlighted — red = removed, green = added';
        break;
      case AnnotationMode.none:
        return const SizedBox.shrink();
    }

    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: bannerColor.withValues(alpha: 0.08),
        border: Border(
            bottom: BorderSide(color: bannerColor.withValues(alpha: 0.15))),
      ),
      child: Row(
        children: [
          Icon(bannerIcon, size: 16, color: bannerColor),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              bannerText,
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    color: bannerColor,
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                  ),
            ),
          ),
          if (_resultText != null)
            IconButtonM3E(
              icon: Icon(CupertinoIcons.doc_on_clipboard,
                  size: 16, color: bannerColor),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: _resultText!));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                      content: Text('Result copied'),
                      duration: Duration(seconds: 1)),
                );
              },
              variant: IconButtonM3EVariant.standard,
              size: IconButtonM3ESize.sm,
            ),
        ],
      ),
    );
  }

  // ── PROCESSING BAR ────────────────────────────

  Widget _buildProcessingBar() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.06),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: LoadingIndicatorM3E(
              constraints: BoxConstraints(maxWidth: 14, maxHeight: 14),
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          const SizedBox(width: 10),
          Text(
            _processingLabel,
            style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.w600,
                  fontSize: 12,
                ),
          ),
        ],
      ),
    );
  }

  // ── TOOL BAR ──────────────────────────────────

  Widget _buildToolBar() {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Theme.of(context).shadowColor.withValues(alpha: 0.06),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              _buildToolChip('grammar', 'Grammar', CupertinoIcons.textformat,
                  const Color(0xFFEF4444)),
              _buildToolChip(
                  'paraphrase',
                  'Paraphrase',
                  CupertinoIcons.arrow_2_squarepath,
                  Theme.of(context).colorScheme.primary),
              _buildToolChip('translate', 'Translate', CupertinoIcons.globe,
                  const Color(0xFF3B82F6)),
              _buildToolChip('summarize', 'Summarize',
                  CupertinoIcons.text_justify, const Color(0xFFF59E0B)),
              _buildToolChip('tone', 'Tone', CupertinoIcons.slider_horizontal_3,
                  const Color(0xFF8B5CF6)),
              _buildToolChip('ai_detect', 'AI Detect', CupertinoIcons.sparkles,
                  const Color(0xFFEC4899)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildToolChip(
    String id,
    String label,
    IconData icon,
    Color color,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    final isSelected = _selectedTool == id;
    final isActive = isSelected && _annotationMode != AnnotationMode.none;

    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: _isProcessing ? null : () => _runTool(id),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: isActive
                ? color.withValues(alpha: 0.15)
                : isSelected
                    ? colorScheme.surfaceContainerHighest
                    : colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: isActive
                  ? color
                  : colorScheme.outlineVariant.withValues(alpha: 0.5),
              width: isActive ? 1.5 : 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon,
                  size: 15,
                  color: isActive ? color : colorScheme.onSurfaceVariant),
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: isActive ? FontWeight.w700 : FontWeight.w500,
                  color: isActive ? color : colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── "USE THIS TEXT" BUTTON ────────────────────

  Widget _buildUseTextButton() {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: CustomButton(
          text: _resultText != null ? 'Use Processed Text' : 'Use This Text',
          onPressed: () {
            final text = _resultText ?? widget.document.text;
            widget.onUseText!(text);
            Navigator.of(context).pop();
          },
          width: double.infinity,
          icon: CupertinoIcons.text_cursor,
          type: ButtonType.primary,
        ),
      ),
    );
  }

  // ── HELPER WIDGETS ────────────────────────────

  Widget _buildDocumentCard({required Widget child}) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: colorScheme.outlineVariant.withValues(alpha: 0.3)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: child,
    );
  }

  Widget _buildDocHeader(String text) {
    final wc = text.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length;
    return Row(
      children: [
        Icon(CupertinoIcons.text_alignleft,
            size: 14, color: Theme.of(context).colorScheme.onSurfaceVariant),
        const SizedBox(width: 6),
        Text(
          '$wc words',
          style: Theme.of(context).textTheme.bodySmall!.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
                fontSize: 12,
              ),
        ),
        const Spacer(),
        GestureDetector(
          onTap: () {
            Clipboard.setData(ClipboardData(text: text));
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                  content: Text('Copied'), duration: Duration(seconds: 1)),
            );
          },
          child: Row(
            children: [
              Icon(CupertinoIcons.doc_on_clipboard,
                  size: 14, color: Theme.of(context).colorScheme.primary),
              const SizedBox(width: 4),
              Text('Copy',
                  style: TextStyle(
                    fontSize: 12,
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  )),
            ],
          ),
        ),
      ],
    );
  }

  String _toolResultLabel(String tool) {
    switch (tool) {
      case 'translate':
        return 'Translated';
      case 'summarize':
        return 'Summary';
      default:
        return 'Result';
    }
  }
}
