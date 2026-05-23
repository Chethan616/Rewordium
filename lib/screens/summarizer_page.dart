import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'dart:convert';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../widgets/focused_editor.dart';
import '../providers/auth_provider.dart';
import '../services/unified_ai_service.dart';
import '../services/document_chunking_service.dart';
import '../utils/ai_error_handler.dart';
import '../models/document_result.dart';
import '../widgets/document_input_widget.dart';
import '../screens/document_viewer_screen.dart';
import '../utils/responsive.dart';

import 'auth/login_screen.dart';

class SummarizerPage extends StatefulWidget {
  const SummarizerPage({super.key});

  @override
  State<SummarizerPage> createState() => _SummarizerPageState();
}

class _SummarizerPageState extends State<SummarizerPage>
    with AutomaticKeepAliveClientMixin {
  // ─── Draft keys ──────────────────────────────────────────────────
  static const String _draftInputKey = 'summarizer_draft_input';
  static const String _draftResultKey = 'summarizer_draft_result';
  static const String _draftLengthKey = 'summarizer_draft_length';
  static const String _draftKeyPointsKey = 'summarizer_draft_keypoints';

  static final Map<String, Map<String, dynamic>> _sessionCache =
      <String, Map<String, dynamic>>{};

  // ─── Accent ──────────────────────────────────────────────────────
  static const Color _accent = Color(0xFFF59E0B); // Amber 500

  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  bool _isLoading = false;
  String _selectedLength = "Medium";
  List<String> _keyPoints = [];
  int? _originalWordCount;
  int? _summaryWordCount;
  DocumentResult? _loadedDocument;
  String? _lastErrorMessage;
  bool _isRestoringDraft = false;
  bool _isInputEmpty = true;

  @override
  bool get wantKeepAlive => true;

  // ─── Length options ──────────────────────────────────────────────
  static const _lengthIcons = <String, IconData>{
    'Very Short': CupertinoIcons.text_alignleft,
    'Short': CupertinoIcons.doc_plaintext,
    'Medium': CupertinoIcons.doc_text,
    'Long': CupertinoIcons.doc_richtext,
    'Detailed': CupertinoIcons.doc_append,
  };

  static const List<String> _summaryLengths = [
    "Very Short",
    "Short",
    "Medium",
    "Long",
    "Detailed",
  ];

  // ─── Cache ───────────────────────────────────────────────────────
  String _cacheKey(String text) => 'summarize|$_selectedLength|$text';

  void _cacheResult(String key, Map<String, dynamic> value) {
    if (_sessionCache.length >= 30) {
      _sessionCache.remove(_sessionCache.keys.first);
    }
    _sessionCache[key] = value;
  }

  // ─── Draft persistence ───────────────────────────────────────────
  Future<void> _persistDraft() async {
    if (_isRestoringDraft) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_draftInputKey, _controller.text);
      await prefs.setString(_draftResultKey, _resultController.text);
      await prefs.setString(_draftLengthKey, _selectedLength);
      await prefs.setString(_draftKeyPointsKey, jsonEncode(_keyPoints));
    } catch (_) {}
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final input = prefs.getString(_draftInputKey) ?? '';
      final result = prefs.getString(_draftResultKey) ?? '';
      final length = prefs.getString(_draftLengthKey) ?? 'Medium';
      final keyPointsRaw = prefs.getString(_draftKeyPointsKey);

      List<String> restoredKeyPoints = <String>[];
      if (keyPointsRaw != null && keyPointsRaw.isNotEmpty) {
        try {
          final decoded = jsonDecode(keyPointsRaw);
          if (decoded is List) {
            restoredKeyPoints = decoded.map((e) => e.toString()).toList();
          }
        } catch (_) {}
      }

      if (!mounted) return;
      setState(() {
        _controller.text = input;
        _resultController.text = result;
        _selectedLength = length;
        _keyPoints = restoredKeyPoints;
        _isInputEmpty = input.trim().isEmpty;
      });
    } catch (_) {
    } finally {
      _isRestoringDraft = false;
    }
  }

  // ─── Lifecycle ───────────────────────────────────────────────────
  @override
  void initState() {
    super.initState();
    UnifiedAIService.initialize();
    _restoreDraft();
  }

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    super.dispose();
  }

  // ─── Document input ──────────────────────────────────────────────
  void _onDocumentTextExtracted(String text, DocumentResult doc) {
    setState(() {
      _controller.text = text;
      _loadedDocument = doc;
      _isInputEmpty = text.trim().isEmpty;
    });
    _persistDraft();
  }

  void _clearDocument() {
    setState(() => _loadedDocument = null);
    _persistDraft();
  }

  // ─── Summarize ───────────────────────────────────────────────────
  Future<void> _summarizeText() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to summarize')),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _lastErrorMessage = null;
    });

    try {
      final key = _cacheKey(text);
      final cachedResult = _sessionCache[key];
      final result = cachedResult ??
          (DocumentChunkingService.needsChunking(text)
              ? await DocumentChunkingService.summarizeLarge(text,
                  length: _selectedLength.toLowerCase())
              : await UnifiedAIService.summarizeText(text,
                  length: _selectedLength.toLowerCase()));

      if (result.containsKey('error') || result.containsKey('errorType')) {
        setState(() => _isLoading = false);
        _lastErrorMessage =
            result['error']?.toString() ?? 'Failed to summarize';
        if (mounted) AIErrorHandler.showErrorSnackBar(context, result);
        setState(() {});
        return;
      }

      if (cachedResult == null) _cacheResult(key, result);

      setState(() {
        _resultController.text = result['summary'] ?? text;
        _keyPoints = List<String>.from(result['key_points'] ?? []);
        _originalWordCount = (result['word_count_original'] as num?)?.toInt();
        _summaryWordCount = (result['word_count_summary'] as num?)?.toInt();
        _isLoading = false;
        _lastErrorMessage = null;
      });
      _persistDraft();
    } catch (e) {
      setState(() {
        _isLoading = false;
        _lastErrorMessage = 'Error summarizing text: $e';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error summarizing text: $e')),
      );
    }
  }

  // ─── Build ───────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    super.build(context);
    final r = Responsive.of(context);
    final authProvider = Provider.of<AuthProvider>(context);
    final bool isLoggedIn = authProvider.isLoggedIn;
    final colorScheme = Theme.of(context).colorScheme;
    final hasResult = _resultController.text.isNotEmpty;

    return Scaffold(
      backgroundColor: colorScheme.surfaceContainerLowest,
      body: SafeArea(
        child: Column(
          children: [
            CustomAppBar(
              title: "Summarizer",
              leadingIcon: Icon(
                CupertinoIcons.doc_text_search,
                color: _accent,
              ),
              actions: [
                if (!isLoggedIn)
                  CustomButton(
                    text: "Log in",
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const LoginScreen(),
                        ),
                      );
                    },
                    height: r.h(36),
                    type: ButtonType.primary,
                  ),
                if (!isLoggedIn) SizedBox(width: r.w(8)),
              ],
            ),
            if (_isLoading)
              LinearProgressIndicatorM3E(
                shape: ProgressM3EShape.wavy,
                size: LinearProgressM3ESize.s,
                activeColor: _accent,
              ),
            _buildErrorBanner(colorScheme),
            Padding(
              padding: r.fromLTRB(16, 8, 16, 0),
              child: DocumentInputWidget(
                onTextExtracted: _onDocumentTextExtracted,
                currentDocument: _loadedDocument,
                onClear: _clearDocument,
                accentColor: _accent,
                initialToolForViewer: 'summarize',
                onViewDocument: (doc) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: doc,
                        initialTool: 'summarize',
                        onUseText: (text) {
                          _controller.text = text;
                          setState(() =>
                              _isInputEmpty = text.trim().isEmpty);
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
            // Main content
            Expanded(
              child: Padding(
                padding: r.fromLTRB(16, 8, 16, 0),
                child: hasResult
                    ? _buildResultView(colorScheme)
                    : _buildInputArea(colorScheme),
              ),
            ),
            // Bottom bar
            _buildBottomBar(r, colorScheme),
          ],
        ),
      ),
    );
  }

  // ─── Error banner ────────────────────────────────────────────────
  Widget _buildErrorBanner(ColorScheme colorScheme) {
    if (_lastErrorMessage == null) return const SizedBox.shrink();
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: colorScheme.errorContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.warning_amber_rounded,
              size: 18, color: colorScheme.onErrorContainer),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              _lastErrorMessage!,
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    color: colorScheme.onErrorContainer,
                  ),
            ),
          ),
          TextButton(
            onPressed: _isLoading ? null : _summarizeText,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  // ─── Input area ──────────────────────────────────────────────────
  Widget _buildInputArea(ColorScheme colorScheme) {
    final r = Responsive.of(context);
    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(r.r(16)),
      ),
      child: TextField(
        controller: _controller,
        maxLines: null,
        expands: true,
        readOnly: true,
        showCursor: false,
        textAlignVertical: TextAlignVertical.top,
        style: Theme.of(context).textTheme.bodyMedium!,
        decoration: InputDecoration(
          hintText: "Tap to summarize…",
          contentPadding: r.all(16),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(r.r(16)),
            borderSide: BorderSide.none,
          ),
          filled: true,
          fillColor: Colors.transparent,
          suffixIcon: !_isInputEmpty
              ? IconButton(
                  icon: Icon(Icons.clear, size: r.r(18)),
                  onPressed: () => setState(() {
                    _controller.clear();
                    _isInputEmpty = true;
                    _persistDraft();
                  }),
                )
              : IconButton(
                  icon: Icon(Icons.content_paste, size: r.r(18)),
                  onPressed: () async {
                    final data =
                        await Clipboard.getData(Clipboard.kTextPlain);
                    if (data?.text != null) {
                      _controller.text = data!.text!;
                      setState(() {
                        _isInputEmpty = data.text!.trim().isEmpty;
                      });
                      _persistDraft();
                    }
                  },
                ),
        ),
        onTap: () async {
          final result = await FocusedEditor.open(
            context,
            initialValue: _controller.text,
            title: 'Summarize',
            hint: 'Paste or type the text you want to summarize…',
          );
          if (result != null) {
            _controller.text = result;
            setState(() {
              _isInputEmpty = result.trim().isEmpty;
            });
            _persistDraft();
          }
        },
      ),
    );
  }

  // ─── Result view ─────────────────────────────────────────────────
  Widget _buildResultView(ColorScheme colorScheme) {
    final r = Responsive.of(context);
    // Compute reduction percentage
    String reductionText = '';
    if (_originalWordCount != null &&
        _summaryWordCount != null &&
        _originalWordCount! > 0) {
      final pct = ((_originalWordCount! - _summaryWordCount!) /
              _originalWordCount! *
              100)
          .clamp(0, 100)
          .toStringAsFixed(0);
      reductionText = '$pct% shorter';
    }

    return Column(
      children: [
        // Header with stats
        Container(
          padding: r.all(12),
          decoration: BoxDecoration(
            color: _accent.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(r.r(14)),
          ),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: _accent,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(CupertinoIcons.doc_text_search,
                    size: 20, color: Colors.white),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Summary ($_selectedLength)',
                      style:
                          Theme.of(context).textTheme.titleSmall!.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                    ),
                    if (reductionText.isNotEmpty)
                      Text(
                        reductionText,
                        style:
                            Theme.of(context).textTheme.bodySmall!.copyWith(
                                  color: _accent,
                                  fontWeight: FontWeight.w600,
                                ),
                      ),
                  ],
                ),
              ),
              IconButtonM3E(
                icon: const Icon(Icons.copy_rounded, size: 16),
                onPressed: () {
                  String textToCopy = _resultController.text;
                  if (_keyPoints.isNotEmpty) {
                    textToCopy += '\n\nKey Points:\n';
                    for (int i = 0; i < _keyPoints.length; i++) {
                      textToCopy += '${i + 1}. ${_keyPoints[i]}\n';
                    }
                  }
                  Clipboard.setData(ClipboardData(text: textToCopy));
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Copied to clipboard')),
                  );
                },
                variant: IconButtonM3EVariant.outlined,
                size: IconButtonM3ESize.sm,
              ),
              const SizedBox(width: 6),
              IconButtonM3E(
                icon: const Icon(Icons.edit_note_rounded, size: 16),
                onPressed: () {
                  _controller.text = _resultController.text;
                  setState(() {
                    _resultController.clear();
                    _keyPoints.clear();
                    _originalWordCount = null;
                    _summaryWordCount = null;
                    _isInputEmpty = _controller.text.trim().isEmpty;
                  });
                  _persistDraft();
                },
                variant: IconButtonM3EVariant.outlined,
                size: IconButtonM3ESize.sm,
              ),
              const SizedBox(width: 6),
              IconButtonM3E(
                icon: const Icon(Icons.close_rounded, size: 16),
                onPressed: () {
                  setState(() {
                    _resultController.clear();
                    _keyPoints.clear();
                    _originalWordCount = null;
                    _summaryWordCount = null;
                  });
                  _persistDraft();
                },
                variant: IconButtonM3EVariant.outlined,
                size: IconButtonM3ESize.sm,
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        // Word count stats row
        if (_originalWordCount != null && _summaryWordCount != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              children: [
                _buildStatChip(
                    '$_originalWordCount → $_summaryWordCount words',
                    Icons.text_fields,
                    r),
              ],
            ),
          ),
        // Summary text + key points
        Expanded(
          child: Container(
            width: double.infinity,
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                  color: _accent.withValues(alpha: 0.15)),
            ),
            child: Column(
              children: [
                Expanded(
                  child: TextField(
                    controller: _resultController,
                    maxLines: null,
                    expands: true,
                    readOnly: true,
                    showCursor: false,
                    textAlignVertical: TextAlignVertical.top,
                    style: Theme.of(context).textTheme.bodyMedium!,
                    decoration: InputDecoration(
                      contentPadding: const EdgeInsets.all(16),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                      filled: true,
                      fillColor: Colors.transparent,
                    ),
                    onTap: () async {
                      final result = await FocusedEditor.open(
                        context,
                        initialValue: _resultController.text,
                        title: 'Result',
                      );
                      if (result != null) {
                        setState(() {
                          _resultController.text = result;
                        });
                        _persistDraft();
                      }
                    },
                  ),
                ),
                if (_keyPoints.isNotEmpty)
                  Expanded(
                    child: SingleChildScrollView(
                      physics: const BouncingScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: _accent.withValues(alpha: 0.08),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              'Key Points',
                              style: Theme.of(context)
                                  .textTheme
                                  .labelMedium!
                                  .copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: _accent,
                                  ),
                            ),
                          ),
                          const SizedBox(height: 8),
                          ...List.generate(_keyPoints.length, (i) {
                            return Container(
                              width: double.infinity,
                              margin: const EdgeInsets.only(bottom: 8),
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: colorScheme.surface,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                    color: colorScheme.outlineVariant),
                              ),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    width: 22,
                                    height: 22,
                                    decoration: BoxDecoration(
                                      color:
                                          _accent.withValues(alpha: 0.12),
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    child: Center(
                                      child: Text(
                                        '${i + 1}',
                                        style: Theme.of(context)
                                            .textTheme
                                            .labelSmall!
                                            .copyWith(
                                              color: _accent,
                                              fontWeight: FontWeight.bold,
                                            ),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      _keyPoints[i],
                                      style: Theme.of(context)
                                          .textTheme
                                          .bodySmall!,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStatChip(String label, IconData icon, ResponsiveData r) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: r.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: _accent.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(r.r(20)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: r.r(13), color: _accent),
          SizedBox(width: r.w(4)),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  fontSize: r.sp(11),
                  fontWeight: FontWeight.w600,
                  color: cs.onSurfaceVariant,
                ),
          ),
        ],
      ),
    );
  }

  // ─── Bottom bar ──────────────────────────────────────────────────
  Widget _buildBottomBar(ResponsiveData r, ColorScheme colorScheme) {
    return Container(
      padding: r.fromLTRB(8, 10, 8, 4),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(r.r(20))),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 12,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Length chips
          SizedBox(
            height: r.h(38),
            child: _buildLengthChips(colorScheme),
          ),
          SizedBox(height: r.h(10)),
          // CTA button
          Padding(
            padding: r.symmetric(horizontal: 16),
            child: CustomButton(
              text: _resultController.text.isNotEmpty
                  ? "Summarize Again"
                  : "Summarize",
              onPressed: _isLoading ? null : _summarizeText,
              width: double.infinity,
              isLoading: _isLoading,
              icon: _isLoading ? null : CupertinoIcons.doc_text_search,
            ),
          ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }

  Widget _buildLengthChips(ColorScheme colorScheme) {
    return ListView.separated(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      itemCount: _summaryLengths.length,
      separatorBuilder: (_, __) => const SizedBox(width: 6),
      itemBuilder: (_, i) {
        final length = _summaryLengths[i];
        final selected = _selectedLength == length;
        return GestureDetector(
          onTap: () {
            setState(() => _selectedLength = length);
            _persistDraft();
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: selected
                  ? _accent
                  : colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(20),
              border: selected
                  ? null
                  : Border.all(color: colorScheme.outlineVariant),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  _lengthIcons[length] ?? CupertinoIcons.doc_text,
                  size: 14,
                  color: selected
                      ? Colors.white
                      : colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  length,
                  style:
                      Theme.of(context).textTheme.labelMedium!.copyWith(
                            color: selected
                                ? Colors.white
                                : colorScheme.onSurfaceVariant,
                            fontWeight: selected
                                ? FontWeight.bold
                                : FontWeight.normal,
                          ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
