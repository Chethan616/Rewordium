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

class ToneEditorPage extends StatefulWidget {
  const ToneEditorPage({super.key});

  @override
  State<ToneEditorPage> createState() => _ToneEditorPageState();
}

class _ToneEditorPageState extends State<ToneEditorPage>
    with AutomaticKeepAliveClientMixin {
  // ─── Draft persistence keys ──────────────────────────────────────
  static const String _draftInputKey = 'tone_draft_input';
  static const String _draftResultKey = 'tone_draft_result';
  static const String _draftToneKey = 'tone_draft_tone';
  static const String _draftChangesKey = 'tone_draft_changes';

  static final Map<String, Map<String, dynamic>> _sessionCache =
      <String, Map<String, dynamic>>{};

  // ─── Accent color ────────────────────────────────────────────────
  static const Color _accent = Color(0xFF009688); // Material Teal 500

  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  final TextEditingController _customToneController = TextEditingController();
  bool _isLoading = false;
  String _selectedTone = "Professional";
  List<String> _changesMade = [];
  String? _originalTone;
  DocumentResult? _loadedDocument;
  String? _lastErrorMessage;
  bool _isRestoringDraft = false;
  bool _isInputEmpty = true;

  @override
  bool get wantKeepAlive => true;

  // ─── Tones ───────────────────────────────────────────────────────
  static const _toneIcons = <String, IconData>{
    'Professional': CupertinoIcons.briefcase,
    'Casual': CupertinoIcons.chat_bubble_text,
    'Friendly': CupertinoIcons.hand_thumbsup,
    'Formal': CupertinoIcons.doc_text,
    'Academic': CupertinoIcons.book,
    'Enthusiastic': CupertinoIcons.sparkles,
    'Confident': CupertinoIcons.shield,
    'Empathetic': CupertinoIcons.heart,
    'Persuasive': CupertinoIcons.lightbulb,
    'Humorous': CupertinoIcons.smiley,
    'Inspirational': CupertinoIcons.star,
    'Diplomatic': CupertinoIcons.hand_raised,
    'Urgent': CupertinoIcons.exclamationmark_triangle,
    'Custom': CupertinoIcons.slider_horizontal_3,
  };

  static const List<String> _tones = [
    "Professional",
    "Casual",
    "Friendly",
    "Formal",
    "Academic",
    "Enthusiastic",
    "Confident",
    "Empathetic",
    "Persuasive",
    "Humorous",
    "Inspirational",
    "Diplomatic",
    "Urgent",
    "Custom",
  ];

  // ─── Cache ───────────────────────────────────────────────────────
  String _cacheKey(String text) =>
      'tone|$_selectedTone|${_customToneController.text.trim()}|$text';

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
      await prefs.setString(_draftToneKey, _selectedTone);
      await prefs.setString(_draftChangesKey, jsonEncode(_changesMade));
    } catch (_) {}
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final input = prefs.getString(_draftInputKey) ?? '';
      final result = prefs.getString(_draftResultKey) ?? '';
      final tone = prefs.getString(_draftToneKey) ?? 'Professional';
      final changesRaw = prefs.getString(_draftChangesKey);

      List<String> restoredChanges = <String>[];
      if (changesRaw != null && changesRaw.isNotEmpty) {
        try {
          final decoded = jsonDecode(changesRaw);
          if (decoded is List) {
            restoredChanges = decoded.map((e) => e.toString()).toList();
          }
        } catch (_) {}
      }

      if (!mounted) return;
      setState(() {
        _controller.text = input;
        _resultController.text = result;
        _selectedTone = tone;
        _changesMade = restoredChanges;
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
    _customToneController.dispose();
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

  // ─── Custom tone dialog ──────────────────────────────────────────
  Future<void> _showCustomToneDialog() async {
    _customToneController.text = '';
    final cs = Theme.of(context).colorScheme;
    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Custom Tone'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Describe the tone you want:',
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _customToneController,
              maxLines: 3,
              decoration: InputDecoration(
                hintText:
                    'e.g., Sarcastic, Poetic, Professional but humorous…',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              if (_customToneController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                      content: Text('Please enter a tone description')),
                );
                return;
              }
              Navigator.pop(context);
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
  }

  // ─── Tone edit ───────────────────────────────────────────────────
  Future<void> _editTone() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to edit')),
      );
      return;
    }

    if (_selectedTone == "Custom") {
      await _showCustomToneDialog();
      if (_customToneController.text.trim().isEmpty) return;
    }

    setState(() {
      _isLoading = true;
      _lastErrorMessage = null;
    });

    try {
      final targetTone = _selectedTone == 'Custom'
          ? _customToneController.text.trim()
          : _selectedTone.toLowerCase();

      final key = _cacheKey(text);
      final cachedResult = _sessionCache[key];
      final result = cachedResult ??
          (DocumentChunkingService.needsChunking(text)
              ? await DocumentChunkingService.editToneLarge(text, targetTone)
              : await UnifiedAIService.editTone(text, targetTone));

      if (result.containsKey('error') || result.containsKey('errorType')) {
        setState(() => _isLoading = false);
        _lastErrorMessage =
            result['error']?.toString() ?? 'Failed to edit tone';
        if (mounted) AIErrorHandler.showErrorSnackBar(context, result);
        setState(() {});
        return;
      }

      if (cachedResult == null) _cacheResult(key, result);

      setState(() {
        _resultController.text = result['edited_text'] ?? text;
        _changesMade = List<String>.from(result['changes_made'] ?? []);
        _originalTone = result['original_tone']?.toString();
        _isLoading = false;
        _lastErrorMessage = null;
      });
      _persistDraft();
    } catch (e) {
      setState(() {
        _isLoading = false;
        _lastErrorMessage = 'Error editing tone: $e';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error editing tone: $e')),
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
              title: "Tone Editor",
              leadingIcon: Icon(
                CupertinoIcons.waveform,
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
                initialToolForViewer: 'tone',
                onViewDocument: (doc) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: doc,
                        initialTool: 'tone',
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
            onPressed: _isLoading ? null : _editTone,
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
          hintText: "Tap to edit tone…",
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
            title: 'Tone Editor',
            hint: 'Paste or type the text you want to change the tone of…',
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
    return Column(
      children: [
        // Header with tone info
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
                child: const Icon(CupertinoIcons.waveform,
                    size: 20, color: Colors.white),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Tone: $_selectedTone',
                      style:
                          Theme.of(context).textTheme.titleSmall!.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                    ),
                    if (_originalTone != null)
                      Text(
                        'From: $_originalTone',
                        style:
                            Theme.of(context).textTheme.bodySmall!.copyWith(
                                  color: colorScheme.onSurfaceVariant,
                                ),
                      ),
                  ],
                ),
              ),
              IconButtonM3E(
                icon: const Icon(Icons.copy_rounded, size: 16),
                onPressed: () {
                  Clipboard.setData(
                      ClipboardData(text: _resultController.text));
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
                    _changesMade.clear();
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
                    _changesMade.clear();
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
        // Result text + changes
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
                if (_changesMade.isNotEmpty)
                  Expanded(
                    child: SingleChildScrollView(
                      physics: const BouncingScrollPhysics(),
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: _accent.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        'Changes Made',
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
                    ...List.generate(_changesMade.length, (i) {
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
                                color: _accent.withValues(alpha: 0.12),
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
                                _changesMade[i],
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
          // Scrollable tone chips
          SizedBox(
            height: r.h(38),
            child: _buildToneChips(colorScheme),
          ),
          SizedBox(height: r.h(10)),
          // CTA button
          Padding(
            padding: r.symmetric(horizontal: 16),
            child: CustomButton(
              text: _resultController.text.isNotEmpty
                  ? "Edit Again"
                  : "Edit Tone",
              onPressed: _isLoading ? null : _editTone,
              width: double.infinity,
              isLoading: _isLoading,
              icon: _isLoading ? null : CupertinoIcons.waveform,
            ),
          ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }

  Widget _buildToneChips(ColorScheme colorScheme) {
    return ListView.separated(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      itemCount: _tones.length,
      separatorBuilder: (_, __) => const SizedBox(width: 6),
      itemBuilder: (_, i) {
        final tone = _tones[i];
        final selected = _selectedTone == tone;
        return GestureDetector(
          onTap: () {
            setState(() => _selectedTone = tone);
            _persistDraft();
            if (tone == 'Custom') _showCustomToneDialog();
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
                  _toneIcons[tone] ?? CupertinoIcons.waveform,
                  size: 14,
                  color: selected
                      ? Colors.white
                      : colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  tone,
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
