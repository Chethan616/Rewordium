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

class TranslatorPage extends StatefulWidget {
  const TranslatorPage({super.key});

  @override
  State<TranslatorPage> createState() => _TranslatorPageState();
}

class _TranslatorPageState extends State<TranslatorPage>
    with AutomaticKeepAliveClientMixin {
  // ─── Draft keys ──────────────────────────────────────────────────
  static const String _draftInputKey = 'translator_draft_input';
  static const String _draftResultKey = 'translator_draft_result';
  static const String _draftLangKey = 'translator_draft_language';
  static const String _draftNotesKey = 'translator_draft_notes';
  static const String _draftSourceKey = 'translator_draft_source_lang';

  static final Map<String, Map<String, dynamic>> _sessionCache =
      <String, Map<String, dynamic>>{};

  // ─── Accent ──────────────────────────────────────────────────────
  static const Color _accent = Color(0xFF3B82F6); // Blue 500

  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  bool _isLoading = false;
  String _selectedLanguage = "Spanish";
  String? _detectedSourceLanguage;
  String? _translationNotes;
  DocumentResult? _loadedDocument;
  String? _lastErrorMessage;
  bool _isRestoringDraft = false;
  bool _isInputEmpty = true;

  @override
  bool get wantKeepAlive => true;

  // ─── Languages ───────────────────────────────────────────────────
  static const _languageFlags = <String, String>{
    'Spanish': '🇪🇸',
    'French': '🇫🇷',
    'German': '🇩🇪',
    'Italian': '🇮🇹',
    'Portuguese': '🇵🇹',
    'Russian': '🇷🇺',
    'Japanese': '🇯🇵',
    'Chinese': '🇨🇳',
    'Korean': '🇰🇷',
    'Arabic': '🇸🇦',
    'Hindi': '🇮🇳',
    'Dutch': '🇳🇱',
    'Swedish': '🇸🇪',
    'Greek': '🇬🇷',
    'Turkish': '🇹🇷',
    'Polish': '🇵🇱',
    'Vietnamese': '🇻🇳',
    'Thai': '🇹🇭',
    'Indonesian': '🇮🇩',
    'Hebrew': '🇮🇱',
    'Custom': '🌐',
  };

  final List<String> _languages = const [
    "Spanish",
    "French",
    "German",
    "Italian",
    "Portuguese",
    "Russian",
    "Japanese",
    "Chinese",
    "Korean",
    "Arabic",
    "Hindi",
    "Dutch",
    "Swedish",
    "Greek",
    "Turkish",
    "Polish",
    "Vietnamese",
    "Thai",
    "Indonesian",
    "Hebrew",
    "Custom",
  ];

  // ─── Cache ───────────────────────────────────────────────────────
  String _cacheKey(String text) => 'translate|$_selectedLanguage|$text';

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
      await prefs.setString(_draftLangKey, _selectedLanguage);
      await prefs.setString(_draftNotesKey, _translationNotes ?? '');
      await prefs.setString(
          _draftSourceKey, _detectedSourceLanguage ?? '');
    } catch (_) {}
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final input = prefs.getString(_draftInputKey) ?? '';
      final result = prefs.getString(_draftResultKey) ?? '';
      final lang = prefs.getString(_draftLangKey) ?? 'Spanish';
      final notes = prefs.getString(_draftNotesKey) ?? '';
      final source = prefs.getString(_draftSourceKey) ?? '';

      if (!mounted) return;
      setState(() {
        _controller.text = input;
        _resultController.text = result;
        _selectedLanguage = lang;
        _translationNotes = notes.isEmpty ? null : notes;
        _detectedSourceLanguage = source.isEmpty ? null : source;
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

  // ─── Custom language dialog ──────────────────────────────────────
  Future<void> _showCustomLanguageDialog() async {
    final customLangController = TextEditingController();
    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Enter Language'),
        content: TextField(
          controller: customLangController,
          decoration: InputDecoration(
            hintText: 'e.g., Swahili, Bengali, Tagalog',
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final lang = customLangController.text.trim();
              if (lang.isNotEmpty) {
                setState(() => _selectedLanguage = lang);
                Navigator.pop(context);
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a language')),
                );
              }
            },
            child: const Text('Translate'),
          ),
        ],
      ),
    );
  }

  // ─── Language bottom sheet ───────────────────────────────────────
  void _showLanguageBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        final cs = Theme.of(ctx).colorScheme;
        return DraggableScrollableSheet(
          initialChildSize: 0.55,
          minChildSize: 0.3,
          maxChildSize: 0.85,
          expand: false,
          builder: (_, scrollController) {
            return Column(
              children: [
                Container(
                  margin: const EdgeInsets.only(top: 12, bottom: 8),
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: cs.onSurfaceVariant.withValues(alpha: 0.4),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Text(
                    'Select Language',
                    style: Theme.of(ctx).textTheme.titleMedium,
                  ),
                ),
                const Divider(height: 1),
                Expanded(
                  child: ListView.builder(
                    controller: scrollController,
                    itemCount: _languages.length,
                    itemBuilder: (_, i) {
                      final lang = _languages[i];
                      final isSelected = lang == _selectedLanguage;
                      final flag = _languageFlags[lang] ?? '🌐';
                      return ListTile(
                        leading: Text(flag, style: const TextStyle(fontSize: 20)),
                        title: Text(
                          lang,
                          style: TextStyle(
                            color: isSelected ? cs.primary : cs.onSurface,
                            fontWeight: isSelected
                                ? FontWeight.w600
                                : FontWeight.normal,
                          ),
                        ),
                        trailing: isSelected
                            ? Icon(Icons.check_rounded, color: cs.primary)
                            : null,
                        onTap: () {
                          Navigator.pop(ctx);
                          if (lang == 'Custom') {
                            _showCustomLanguageDialog();
                          } else {
                            setState(() => _selectedLanguage = lang);
                            _persistDraft();
                          }
                        },
                      );
                    },
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // ─── Translate ───────────────────────────────────────────────────
  Future<void> _translateText() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to translate')),
      );
      return;
    }

    if (_selectedLanguage == "Custom") {
      await _showCustomLanguageDialog();
      if (_selectedLanguage == "Custom") return;
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
              ? await DocumentChunkingService.translateLarge(
                  text, _selectedLanguage)
              : await UnifiedAIService.translateText(
                  text, _selectedLanguage));

      if (result.containsKey('error') || result.containsKey('errorType')) {
        setState(() => _isLoading = false);
        _lastErrorMessage =
            result['error']?.toString() ?? 'Failed to translate';
        if (mounted) AIErrorHandler.showErrorSnackBar(context, result);
        setState(() {});
        return;
      }

      if (cachedResult == null) _cacheResult(key, result);

      setState(() {
        _resultController.text = result['translated_text'] ?? text;
        _detectedSourceLanguage =
            result['detected_source_language']?.toString();
        _translationNotes = result['notes']?.toString();
        _isLoading = false;
        _lastErrorMessage = null;
      });
      _persistDraft();
    } catch (e) {
      setState(() {
        _isLoading = false;
        _lastErrorMessage = 'Error translating text: $e';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error translating text: $e')),
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
              title: "Translator",
              leadingIcon: Icon(
                CupertinoIcons.globe,
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
                initialToolForViewer: 'translate',
                onViewDocument: (doc) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: doc,
                        initialTool: 'translate',
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
            onPressed: _isLoading ? null : _translateText,
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
          hintText: "Tap to translate…",
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
            title: 'Translate',
            hint: 'Paste or type the text you want to translate…',
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
    final flag = _languageFlags[_selectedLanguage] ?? '🌐';
    return Column(
      children: [
        // Header
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
                child: Center(
                  child: Text(flag,
                      style: const TextStyle(fontSize: 20)),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _selectedLanguage,
                      style:
                          Theme.of(context).textTheme.titleSmall!.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                    ),
                    if (_detectedSourceLanguage != null)
                      Text(
                        'From: $_detectedSourceLanguage',
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
                    _translationNotes = null;
                    _detectedSourceLanguage = null;
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
                    _translationNotes = null;
                    _detectedSourceLanguage = null;
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
        // Translation text
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
                      }
                    },
                  ),
                ),
                if (_translationNotes != null && _translationNotes!.isNotEmpty)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: _accent.withValues(alpha: 0.06),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                          color: _accent.withValues(alpha: 0.12)),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(CupertinoIcons.info_circle,
                            size: 14, color: _accent),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _translationNotes!,
                            style: Theme.of(context)
                                .textTheme
                                .bodySmall!
                                .copyWith(
                                  color: colorScheme.onSurfaceVariant,
                                ),
                          ),
                        ),
                      ],
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
    final flag = _languageFlags[_selectedLanguage] ?? '🌐';
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
          // Language picker chip
          GestureDetector(
            onTap: _showLanguageBottomSheet,
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: _accent.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                    color: _accent.withValues(alpha: 0.2)),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(flag, style: const TextStyle(fontSize: 16)),
                  const SizedBox(width: 8),
                  Text(
                    'Translate to: $_selectedLanguage',
                    style:
                        Theme.of(context).textTheme.labelMedium!.copyWith(
                              color: _accent,
                              fontWeight: FontWeight.w600,
                            ),
                  ),
                  const SizedBox(width: 4),
                  Icon(CupertinoIcons.chevron_down,
                      size: 12, color: _accent),
                ],
              ),
            ),
          ),
          SizedBox(height: r.h(10)),
          // CTA button
          Padding(
            padding: r.symmetric(horizontal: 16),
            child: CustomButton(
              text: _resultController.text.isNotEmpty
                  ? "Translate Again"
                  : "Translate",
              onPressed: _isLoading ? null : _translateText,
              width: double.infinity,
              isLoading: _isLoading,
              icon: _isLoading ? null : CupertinoIcons.globe,
            ),
          ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }
}
