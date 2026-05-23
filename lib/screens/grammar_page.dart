import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'dart:async';
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
import '../models/document_result.dart';
import '../widgets/document_input_widget.dart';
import '../screens/document_viewer_screen.dart';
import '../utils/responsive.dart';
import 'auth/login_screen.dart';

class GrammarPage extends StatefulWidget {
  const GrammarPage({super.key});

  @override
  State<GrammarPage> createState() => _GrammarPageState();
}

class _GrammarPageState extends State<GrammarPage>
    with AutomaticKeepAliveClientMixin {
  static const String _draftInputKey = 'grammar_draft_input';
  static const String _draftCorrectedKey = 'grammar_draft_corrected';
  static const String _draftErrorCountKey = 'grammar_draft_error_count';
  static const String _draftErrorsKey = 'grammar_draft_errors';
  static final Map<String, Map<String, dynamic>> _sessionGrammarCache =
      <String, Map<String, dynamic>>{};

  final TextEditingController _textController = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  final FocusNode _textFocusNode = FocusNode();
  int _wordCount = 0;
  int _errorCount = 0;
  bool _isChecking = false;
  String _correctedText = '';
  List<Map<String, dynamic>> _errors = [];
  DocumentResult? _loadedDocument;
  String? _lastErrorMessage;
  bool _isRestoringDraft = false;
  bool _isInputEmpty = true;
  Timer? _wordCountDebounce;

  @override
  bool get wantKeepAlive => true;

  String _grammarCacheKey(String text) => 'grammar|$text';

  void _cacheGrammarResult(String key, Map<String, dynamic> value) {
    if (_sessionGrammarCache.length >= 30) {
      _sessionGrammarCache.remove(_sessionGrammarCache.keys.first);
    }
    _sessionGrammarCache[key] = value;
  }

  Future<void> _persistDraft() async {
    if (_isRestoringDraft) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_draftInputKey, _textController.text);
      await prefs.setString(_draftCorrectedKey, _correctedText);
      await prefs.setInt(_draftErrorCountKey, _errorCount);
      await prefs.setString(_draftErrorsKey, jsonEncode(_errors));
    } catch (_) {}
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final text = prefs.getString(_draftInputKey) ?? '';
      final corrected = prefs.getString(_draftCorrectedKey) ?? '';
      final errorCount = prefs.getInt(_draftErrorCountKey) ?? 0;
      final errorsRaw = prefs.getString(_draftErrorsKey);

      List<Map<String, dynamic>> restoredErrors = <Map<String, dynamic>>[];
      if (errorsRaw != null && errorsRaw.isNotEmpty) {
        try {
          final decoded = jsonDecode(errorsRaw);
          if (decoded is List) {
            restoredErrors = decoded
                .whereType<Map>()
                .map((e) => Map<String, dynamic>.from(e))
                .toList();
          }
        } catch (_) {}
      }

      if (!mounted) return;
      setState(() {
        _textController.text = text;
        _correctedText = corrected;
        _resultController.text = corrected;
        _errorCount = errorCount;
        _errors = restoredErrors;
        _wordCount =
            text.trim().isEmpty ? 0 : text.trim().split(RegExp(r'\s+')).length;
        _isInputEmpty = text.trim().isEmpty;
      });
    } catch (_) {
      // Ignore restore failures to keep screen startup stable.
    } finally {
      _isRestoringDraft = false;
    }
  }

  void _scheduleWordCountUpdate(String text) {
    _wordCountDebounce?.cancel();
    _wordCountDebounce = Timer(const Duration(milliseconds: 250), () {
      if (!mounted) return;
      setState(() {
        _wordCount =
            text.trim().isEmpty ? 0 : text.trim().split(RegExp(r'\s+')).length;
      });
    });
  }

  Map<String, dynamic> _sanitizeGrammarResult(
    Map<String, dynamic> result,
    String originalText,
  ) {
    if (result.containsKey('error') || result.containsKey('errorType')) {
      return result;
    }

    final correctedText = result['corrected_text']?.toString() ?? originalText;
    final rawErrors = result['errors'];
    final normalizedErrors = <Map<String, dynamic>>[];

    if (rawErrors is List) {
      for (final item in rawErrors) {
        if (item is! Map) continue;
        final map = Map<String, dynamic>.from(item);
        final original = map['original']?.toString().trim() ?? '';
        final correction = map['correction']?.toString().trim() ?? '';
        final explanation = map['explanation']?.toString().trim() ??
            'Suggested grammar correction';
        if (original.isEmpty || correction.isEmpty) continue;

        normalizedErrors.add({
          'original': original,
          'correction': correction,
          'explanation': explanation,
        });
      }
    }

    var errorCount = 0;
    final rawErrorCount = result['error_count'];
    if (rawErrorCount is int) {
      errorCount = rawErrorCount;
    } else if (rawErrorCount is num) {
      errorCount = rawErrorCount.toInt();
    }

    if (errorCount == 0 && normalizedErrors.isNotEmpty) {
      errorCount = normalizedErrors.length;
    }

    return {
      'corrected_text': correctedText,
      'error_count': errorCount,
      'errors': normalizedErrors,
    };
  }

  Future<void> _retryGrammarCheck() async {
    await _checkGrammar();
  }

  void _onDocumentTextExtracted(String text, DocumentResult doc) {
    setState(() {
      _textController.text = text;
      _updateWordCount(text);
      _loadedDocument = doc;
      _isInputEmpty = text.trim().isEmpty;
    });
    _persistDraft();
  }

  void _clearDocument() {
    setState(() => _loadedDocument = null);
    _persistDraft();
  }

  @override
  void initState() {
    super.initState();
    // Initialize Unified AI service
    UnifiedAIService.initialize();
    _restoreDraft();
  }

  @override
  void dispose() {
    _wordCountDebounce?.cancel();
    _textController.dispose();
    _resultController.dispose();
    _textFocusNode.dispose();
    super.dispose();
  }

  void _updateWordCount(String text) {
    setState(() {
      _wordCount =
          text.trim().isEmpty ? 0 : text.trim().split(RegExp(r'\s+')).length;
    });
  }

  Future<void> _checkGrammar() async {
    final text = _textController.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to check')),
      );
      return;
    }

    setState(() {
      _isChecking = true;
      _lastErrorMessage = null;
    });

    try {
      final cacheKey = _grammarCacheKey(text);
      final cachedResult = _sessionGrammarCache[cacheKey];
      final result = cachedResult ??
          (DocumentChunkingService.needsChunking(text)
              ? await DocumentChunkingService.checkGrammarLarge(text)
              : await UnifiedAIService.checkGrammar(text));

      final normalizedResult = _sanitizeGrammarResult(result, text);

      // Check for API errors first
      if (normalizedResult.containsKey('error') ||
          normalizedResult.containsKey('errorType')) {
        setState(() => _isChecking = false);
        final errorType = normalizedResult['errorType'] as String? ?? 'UNKNOWN';
        String errorMessage;
        switch (errorType) {
          case 'MISSING_API_KEY':
            errorMessage =
                'API key not configured. Please set up in Advanced AI Settings.';
            break;
          case 'RATE_LIMIT':
            errorMessage = 'Rate limit exceeded. Please wait and try again.';
            break;
          case 'INVALID_API_KEY':
            errorMessage = 'Invalid API key. Please check your settings.';
            break;
          default:
            errorMessage =
                normalizedResult['error']?.toString() ?? 'An error occurred';
        }
        _lastErrorMessage = errorMessage;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(errorMessage)),
        );
        return;
      }

      if (cachedResult == null) {
        _cacheGrammarResult(cacheKey, normalizedResult);
      }

      setState(() {
        _correctedText = normalizedResult['corrected_text'] ?? text;
        _resultController.text = _correctedText;
        _errorCount = normalizedResult['error_count'] ?? 0;
        _errors =
            List<Map<String, dynamic>>.from(normalizedResult['errors'] ?? []);
        _isChecking = false;
        _lastErrorMessage = null;
      });
      _persistDraft();

      if (_errorCount > 0) {
        _showErrorsDialog();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No grammar errors found!')),
        );
      }
    } catch (e) {
      setState(() {
        _isChecking = false;
        _lastErrorMessage = 'Error checking grammar: $e';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error checking grammar: $e')),
      );
    }
  }

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
            onPressed: _isChecking ? null : _retryGrammarCheck,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  void _showErrorsDialog() {
    // Results now shown inline, no dialog needed
  }

  Widget _buildErrorItem(Map<String, dynamic> error) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(14),
        border: Border(
          left: BorderSide(color: colorScheme.error, width: 3),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Original text with strikethrough
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  color: colorScheme.error.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Icon(Icons.close_rounded,
                    size: 14, color: colorScheme.error),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${error['original']}',
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        decoration: TextDecoration.lineThrough,
                        color: colorScheme.error,
                      ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          // Correction
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  color: const Color(0xFF10B981).withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Icon(Icons.check_rounded,
                    size: 14, color: Color(0xFF10B981)),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${error['correction']}',
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFF10B981),
                      ),
                ),
              ),
            ],
          ),
          if (error['explanation'] != null) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: colorScheme.primary.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(CupertinoIcons.lightbulb,
                      size: 14, color: colorScheme.primary),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      '${error['explanation']}',
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: colorScheme.onSurfaceVariant,
                          ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final r = Responsive.of(context);
    final authProvider = Provider.of<AuthProvider>(context);
    final bool isLoggedIn = authProvider.isLoggedIn;
    final colorScheme = Theme.of(context).colorScheme;
    final hasResult = _correctedText.isNotEmpty;

    return Scaffold(
      backgroundColor: colorScheme.surfaceContainerLowest,
      body: SafeArea(
        child: Column(
          children: [
            CustomAppBar(
              title: "Grammar Check",
              leadingIcon:
                  Icon(CupertinoIcons.checkmark_seal, color: colorScheme.error),
              actions: [
                if (!isLoggedIn)
                  CustomButton(
                    text: "Log in",
                    onPressed: () {
                      Navigator.push(
                          context,
                          MaterialPageRoute(
                              builder: (_) => const LoginScreen()));
                    },
                    height: r.h(36),
                    type: ButtonType.primary,
                  ),
                if (!isLoggedIn) SizedBox(width: r.w(8)),
              ],
            ),
            if (_isChecking)
              LinearProgressIndicatorM3E(
                shape: ProgressM3EShape.wavy,
                size: LinearProgressM3ESize.s,
                activeColor: colorScheme.error,
              ),
            _buildErrorBanner(colorScheme),
            Padding(
              padding: r.fromLTRB(16, 8, 16, 0),
              child: DocumentInputWidget(
                onTextExtracted: _onDocumentTextExtracted,
                currentDocument: _loadedDocument,
                onClear: _clearDocument,
                accentColor: colorScheme.error,
                initialToolForViewer: 'grammar',
                onViewDocument: (doc) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: doc,
                        initialTool: 'grammar',
                        onUseText: (text) {
                          _textController.text = text;
                          _updateWordCount(text);
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

  Widget _buildInputArea(ColorScheme colorScheme) {
    final r = Responsive.of(context);
    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(r.r(16)),
      ),
      // readOnly + onTap routes editing through FocusedEditor (full-screen).
      // Inline TextField still renders + selects + copies; typing happens
      // in the dedicated editor screen.
      child: TextField(
        controller: _textController,
        focusNode: _textFocusNode,
        maxLines: null,
        expands: true,
        readOnly: true,
        showCursor: false,
        textAlignVertical: TextAlignVertical.top,
        style: Theme.of(context).textTheme.bodyMedium!,
        decoration: InputDecoration(
          hintText: "Tap to check grammar…",
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
                    _textController.clear();
                    _updateWordCount('');
                    _isInputEmpty = true;
                    _persistDraft();
                  }),
                )
              : IconButton(
                  icon: Icon(Icons.content_paste, size: r.r(18)),
                  onPressed: () async {
                    final data = await Clipboard.getData(Clipboard.kTextPlain);
                    if (data?.text != null) {
                      _textController.text = data!.text!;
                      _updateWordCount(data.text!);
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
            initialValue: _textController.text,
            title: 'Grammar Check',
            hint: 'Paste or type the text you want to grammar-check…',
          );
          if (result != null) {
            _textController.text = result;
            _updateWordCount(result);
            setState(() {
              _isInputEmpty = result.trim().isEmpty;
            });
            _persistDraft();
          }
        },
      ),
    );
  }

  Widget _buildResultView(ColorScheme colorScheme) {
    final r = Responsive.of(context);
    return Column(
      children: [
        // Header with score badge
        Container(
          padding: r.all(12),
          decoration: BoxDecoration(
            color: _errorCount > 0
                ? colorScheme.error.withValues(alpha: 0.08)
                : const Color(0xFF10B981).withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(r.r(14)),
          ),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: _errorCount > 0
                      ? colorScheme.error
                      : const Color(0xFF10B981),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  _errorCount > 0
                      ? Icons.warning_amber_rounded
                      : Icons.check_circle_rounded,
                  size: 22,
                  color: Colors.white,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _errorCount > 0
                          ? '$_errorCount errors found'
                          : 'Perfect!',
                      style: Theme.of(context).textTheme.titleSmall!.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    Text(
                      _errorCount > 0
                          ? 'Tap ✓ to apply all corrections'
                          : 'No grammar errors detected',
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: colorScheme.onSurfaceVariant,
                          ),
                    ),
                  ],
                ),
              ),
              IconButtonM3E(
                icon: const Icon(Icons.check_rounded, size: 18),
                onPressed: () {
                  _textController.text = _correctedText;
                  _updateWordCount(_correctedText);
                  setState(() {
                    _correctedText = '';
                    _errors.clear();
                    _errorCount = 0;
                    _isInputEmpty = _textController.text.trim().isEmpty;
                  });
                  _persistDraft();
                },
                variant: IconButtonM3EVariant.filled,
                size: IconButtonM3ESize.sm,
              ),
              const SizedBox(width: 8),
              IconButtonM3E(
                icon: const Icon(Icons.close_rounded, size: 18),
                onPressed: () {
                  setState(() {
                    _correctedText = '';
                    _resultController.text = '';
                    _errors.clear();
                    _errorCount = 0;
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
        // Errors list + corrected text
        Expanded(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_errors.isNotEmpty) ...[
                  ..._errors.map((e) => _buildErrorItem(e)),
                  const SizedBox(height: 12),
                  Text(
                    'Corrected Text',
                    style: Theme.of(context).textTheme.labelMedium!.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 8),
                ],
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: colorScheme.primaryContainer.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                        color: colorScheme.primary.withValues(alpha: 0.2)),
                  ),
                  child: TextField(
                    controller: _resultController,
                    maxLines: null,
                    readOnly: true,
                    showCursor: false,
                    textAlignVertical: TextAlignVertical.top,
                    style: Theme.of(context).textTheme.bodyMedium!,
                    decoration: const InputDecoration(
                      border: InputBorder.none,
                      contentPadding: EdgeInsets.zero,
                    ),
                    onTap: () async {
                      final result = await FocusedEditor.open(
                        context,
                        initialValue: _correctedText,
                        title: 'Result',
                      );
                      if (result != null) {
                        setState(() {
                          _correctedText = result;
                          _resultController.text = result;
                        });
                        _persistDraft();
                      }
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildBottomBar(ResponsiveData r, ColorScheme colorScheme) {
    return Container(
      padding: r.fromLTRB(16, 10, 16, 8),
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
          // Stat chips
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _buildStatChip("$_wordCount Words", Icons.text_fields, r),
              SizedBox(width: r.w(8)),
              _buildStatChip(
                "$_errorCount Errors",
                Icons.error_outline,
                r,
                _errorCount > 0 ? colorScheme.error : null,
              ),
              if (_correctedText.isNotEmpty) ...[
                SizedBox(width: r.w(8)),
                _buildStatChip(
                    "Copy", Icons.copy_rounded, r, colorScheme.primary),
              ],
            ],
          ),
          SizedBox(height: r.h(10)),
          CustomButton(
            text: _correctedText.isNotEmpty ? "Check Again" : "Check Grammar",
            onPressed: _isChecking ? null : _checkGrammar,
            width: double.infinity,
            isLoading: _isChecking,
            icon: _isChecking ? null : Icons.spellcheck,
          ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }

  Widget _buildStatChip(String label, IconData icon, ResponsiveData r,
      [Color? accentColor]) {
    final cs = Theme.of(context).colorScheme;
    final chipColor = accentColor ?? cs.onSurfaceVariant;
    final isActionChip = label == 'Copy';
    return GestureDetector(
      onTap: isActionChip
          ? () {
              Clipboard.setData(ClipboardData(text: _correctedText));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Copied to clipboard')),
              );
            }
          : null,
      child: Container(
        padding: r.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: chipColor.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(r.r(20)),
          border: isActionChip
              ? Border.all(color: chipColor.withValues(alpha: 0.2))
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: r.r(15), color: chipColor),
            SizedBox(width: r.w(4)),
            Text(
              label,
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    fontSize: r.sp(11),
                    fontWeight: FontWeight.w600,
                    color: chipColor,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
