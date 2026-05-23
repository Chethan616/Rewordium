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
import '../models/persona_model.dart';
import '../utils/ai_error_handler.dart';
import '../models/document_result.dart';
import '../widgets/document_input_widget.dart';
import '../screens/document_viewer_screen.dart';
import '../utils/responsive.dart';

// Import your login screen here; adjust path as needed
import 'auth/login_screen.dart';

enum _ParaphraserRetryAction { mode, persona }

class ParaphraserPage extends StatefulWidget {
  const ParaphraserPage({super.key});

  @override
  State<ParaphraserPage> createState() => _ParaphraserPageState();
}

class _ParaphraserPageState extends State<ParaphraserPage>
    with AutomaticKeepAliveClientMixin {
  static const String _draftInputKey = 'paraphraser_draft_input';
  static const String _draftResultKey = 'paraphraser_draft_result';
  static const String _draftModeKey = 'paraphraser_draft_mode';
  static const String _draftUsePersonaKey = 'paraphraser_draft_use_persona';
  static const String _draftPersonaNameKey = 'paraphraser_draft_persona';
  static const String _draftCustomPromptKey = 'paraphraser_draft_custom_prompt';
  static const String _draftAlternativesKey = 'paraphraser_draft_alternatives';

  static final Map<String, Map<String, dynamic>> _sessionResultCache =
      <String, Map<String, dynamic>>{};

  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  final TextEditingController _customPromptController = TextEditingController();
  String _selectedMode = "Standard"; // Track the selected mode
  bool _isLoading = false;
  List<String> _alternatives = [];
  Persona? _selectedPersona;
  bool _usePersona = false; // Whether to use persona or mode
  String? _customPrompt; // Store custom prompt for custom mode
  DocumentResult? _loadedDocument;
  String? _lastErrorMessage;
  _ParaphraserRetryAction? _lastRetryAction;
  bool _isRestoringDraft = false;

  @override
  bool get wantKeepAlive => true;

  String _buildCacheKeyForMode(String text) {
    final prompt = (_customPrompt ?? '').trim();
    return 'mode|$_selectedMode|$prompt|$text';
  }

  String _buildCacheKeyForPersona(String text) {
    final personaName = _selectedPersona?.name ?? 'none';
    final personaPrompt = _selectedPersona?.prompt ?? '';
    return 'persona|$personaName|$personaPrompt|$text';
  }

  void _cacheResult(String key, Map<String, dynamic> result) {
    if (_sessionResultCache.length >= 30) {
      _sessionResultCache.remove(_sessionResultCache.keys.first);
    }
    _sessionResultCache[key] = result;
  }

  Future<void> _persistDraft() async {
    if (_isRestoringDraft) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_draftInputKey, _controller.text);
      await prefs.setString(_draftResultKey, _resultController.text);
      await prefs.setString(_draftModeKey, _selectedMode);
      await prefs.setBool(_draftUsePersonaKey, _usePersona);
      await prefs.setString(_draftPersonaNameKey, _selectedPersona?.name ?? '');
      await prefs.setString(_draftCustomPromptKey, _customPrompt ?? '');
      await prefs.setString(_draftAlternativesKey, jsonEncode(_alternatives));
    } catch (_) {}
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final input = prefs.getString(_draftInputKey) ?? '';
      final result = prefs.getString(_draftResultKey) ?? '';
      final mode = prefs.getString(_draftModeKey) ?? 'Standard';
      final usePersona = prefs.getBool(_draftUsePersonaKey) ?? false;
      final personaName = prefs.getString(_draftPersonaNameKey) ?? '';
      final customPrompt = prefs.getString(_draftCustomPromptKey);
      final alternativesRaw = prefs.getString(_draftAlternativesKey);

      List<String> restoredAlternatives = <String>[];
      if (alternativesRaw != null && alternativesRaw.isNotEmpty) {
        try {
          final decoded = jsonDecode(alternativesRaw);
          if (decoded is List) {
            restoredAlternatives = decoded.map((e) => e.toString()).toList();
          }
        } catch (_) {}
      }

      Persona? restoredPersona;
      if (personaName.isNotEmpty) {
        for (final persona in PersonaManager.allPersonas) {
          if (persona.name == personaName) {
            restoredPersona = persona;
            break;
          }
        }
      }

      if (!mounted) return;
      setState(() {
        _controller.text = input;
        _resultController.text = result;
        _selectedMode = mode;
        _usePersona = usePersona;
        _selectedPersona = restoredPersona;
        _customPrompt = customPrompt;
        _alternatives = restoredAlternatives;
      });
    } catch (_) {
      // Ignore restore failures to keep startup resilient.
    } finally {
      _isRestoringDraft = false;
    }
  }

  void _setErrorState(String message, _ParaphraserRetryAction retryAction) {
    if (!mounted) return;
    setState(() {
      _lastErrorMessage = message;
      _lastRetryAction = retryAction;
    });
  }

  Future<void> _retryLastAction() async {
    final retryAction = _lastRetryAction;
    if (retryAction == null) return;

    switch (retryAction) {
      case _ParaphraserRetryAction.mode:
        await _paraphraseText();
        break;
      case _ParaphraserRetryAction.persona:
        await _paraphraseWithPersona();
        break;
    }
  }

  void _onDocumentTextExtracted(String text, DocumentResult doc) {
    setState(() {
      _controller.text = text;
      _loadedDocument = doc;
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
    // Initialize Groq service
    UnifiedAIService.initialize();
    _restoreDraft();
  }

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    _customPromptController.dispose();
    super.dispose();
  }

  // Convert mode to tone for API
  String _getToneFromMode(String mode) {
    switch (mode.toLowerCase()) {
      case 'fluency':
        return 'fluent and natural';
      case 'academic':
        return 'academic and scholarly';
      case 'humanize':
        return 'conversational and human-like';
      case 'formal':
        return 'formal and professional';
      case 'simple':
        return 'simple and easy to understand';
      case 'creative':
        return 'creative and imaginative';
      case 'expand':
        return 'detailed and expanded';
      case 'shorten':
        return 'concise and shortened';
      case 'custom':
        return 'unique and distinctive';
      default:
        return 'standard and clear';
    }
  }

  // Show persona selection dialog
  void _showPersonaDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Select a Persona'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ...PersonaManager.allPersonas
                    .map((persona) => ListTile(
                          title: Text(persona.name),
                          subtitle: Text(persona.description),
                          onTap: () {
                            setState(() {
                              _selectedPersona = persona;
                            });
                            Navigator.pop(context);

                            // Show a sample text if the text field is empty
                            if (_controller.text.isEmpty) {
                              _controller.text =
                                  "The quick brown fox jumps over the lazy dog. This is a sample text that demonstrates how the paraphraser works with different personas.";
                            }

                            // Paraphrase with the selected persona
                            _paraphraseWithPersona();
                          },
                        ))
                    .toList(),
                Divider(),
                ListTile(
                  title: Text('Create Custom Persona'),
                  subtitle: Text('Define your own paraphrasing instructions'),
                  onTap: () {
                    Navigator.pop(context);
                    _showCustomPersonaDialog();
                  },
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
        ],
      ),
    );
  }

  // Show custom persona creation dialog
  void _showCustomPersonaDialog() {
    _customPromptController.text = '';

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Create Custom Persona'),
        content: Container(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Enter instructions for how to paraphrase your text:'),
              SizedBox(height: 8),
              TextField(
                controller: _customPromptController,
                maxLines: 5,
                decoration: InputDecoration(
                  hintText:
                      'E.g., Rewrite this as if it were written by Shakespeare, using archaic English and poetic structure',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final prompt = _customPromptController.text.trim();
              if (prompt.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                      content: Text('Please enter custom instructions')),
                );
                return;
              }

              // Create and set custom persona
              PersonaManager.setCustomPersona(prompt);
              setState(() {
                _selectedPersona = PersonaManager.customPersona;
                _usePersona = true;
              });

              Navigator.pop(context);

              // Show a sample text if the text field is empty
              if (_controller.text.isEmpty) {
                _controller.text =
                    "The quick brown fox jumps over the lazy dog. This is a sample text that demonstrates how the paraphraser works with different personas.";
              }

              // Paraphrase with the custom persona
              _paraphraseWithPersona();
            },
            child: Text('Create & Use'),
          ),
        ],
      ),
    );
  }

  // Show custom mode dialog
  Future<void> _showCustomModeDialog() async {
    _customPromptController.text = _customPrompt ?? '';

    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Custom Paraphrasing Mode'),
        content: Container(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Enter instructions for how to paraphrase your text:'),
              SizedBox(height: 8),
              TextField(
                controller: _customPromptController,
                maxLines: 5,
                decoration: InputDecoration(
                  hintText:
                      'E.g., Make this text more persuasive and engaging for a marketing audience',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final prompt = _customPromptController.text.trim();
              if (prompt.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                      content: Text('Please enter custom instructions')),
                );
                return;
              }

              setState(() {
                _customPrompt = prompt;
              });

              Navigator.pop(context);
            },
            child: Text('Apply'),
          ),
        ],
      ),
    );
  }

  // Paraphrase with selected persona
  Future<void> _paraphraseWithPersona() async {
    if (_selectedPersona == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select a persona first')),
      );
      return;
    }

    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to paraphrase')),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _lastErrorMessage = null;
      _lastRetryAction = _ParaphraserRetryAction.persona;
    });

    try {
      final cacheKey = _buildCacheKeyForPersona(text);
      final cachedResult = _sessionResultCache[cacheKey];
      final result = cachedResult ??
          await UnifiedAIService.paraphraseWithPersona(
            text,
            _selectedPersona!.prompt,
          );

      // Handle API errors with snackbar
      if (result.containsKey('error')) {
        setState(() {
          _isLoading = false;
        });
        _setErrorState(
          result['error']?.toString() ?? 'Failed to paraphrase with persona',
          _ParaphraserRetryAction.persona,
        );
        if (mounted) {
          AIErrorHandler.showErrorSnackBar(context, result);
        }
        return;
      }

      if (cachedResult == null) {
        _cacheResult(cacheKey, result);
      }

      setState(() {
        _resultController.text = result['paraphrased_text'] ?? text;
        _alternatives = List<String>.from(result['alternatives'] ?? []);
        _isLoading = false;
        _lastErrorMessage = null;
      });
      _persistDraft();

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      _setErrorState(
          'Error paraphrasing text: $e', _ParaphraserRetryAction.persona);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error paraphrasing text: $e')),
      );
    }
  }

  // Paraphrase the text using Groq with selected mode or persona
  Future<void> _paraphraseText() async {
    // If using persona, call the persona paraphraser instead
    if (_usePersona && _selectedPersona != null) {
      await _paraphraseWithPersona();
      return;
    }
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to paraphrase')),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _lastErrorMessage = null;
      _lastRetryAction = _ParaphraserRetryAction.mode;
    });

    try {
      // Check if custom mode is selected
      if (_selectedMode == "Custom") {
        if (_customPrompt == null || _customPrompt!.isEmpty) {
          // Show dialog to get custom prompt
          await _showCustomModeDialog();
          if (_customPrompt == null || _customPrompt!.isEmpty) {
            setState(() {
              _isLoading = false;
            });
            return;
          }
        }

        // Use custom prompt for paraphrasing
        final cacheKey = _buildCacheKeyForMode(text);
        final cachedResult = _sessionResultCache[cacheKey];
        final result = cachedResult ??
            await UnifiedAIService.paraphraseWithCustomPrompt(
                text, _customPrompt!);

        // Handle API errors with snackbar
        if (result.containsKey('error')) {
          setState(() {
            _isLoading = false;
          });
          _setErrorState(
            result['error']?.toString() ?? 'Failed to paraphrase text',
            _ParaphraserRetryAction.mode,
          );
          if (mounted) {
            AIErrorHandler.showErrorSnackBar(context, result);
          }
          return;
        }

        if (cachedResult == null) {
          _cacheResult(cacheKey, result);
        }

        setState(() {
          _resultController.text = result['paraphrased_text'] ?? text;
          _alternatives = List<String>.from(result['alternatives'] ?? []);
          _isLoading = false;
          _lastErrorMessage = null;
        });
        _persistDraft();
      } else {
        // Use standard mode paraphrasing
        final tone = _getToneFromMode(_selectedMode);
        final cacheKey = _buildCacheKeyForMode(text);
        final cachedResult = _sessionResultCache[cacheKey];
        final result = cachedResult ??
            (DocumentChunkingService.needsChunking(text)
                ? await DocumentChunkingService.paraphraseLarge(text, tone)
                : await UnifiedAIService.paraphraseText(text, tone));

        // Handle API errors with snackbar
        if (result.containsKey('error')) {
          setState(() {
            _isLoading = false;
          });
          _setErrorState(
            result['error']?.toString() ?? 'Failed to paraphrase text',
            _ParaphraserRetryAction.mode,
          );
          if (mounted) {
            AIErrorHandler.showErrorSnackBar(context, result);
          }
          return;
        }

        if (cachedResult == null) {
          _cacheResult(cacheKey, result);
        }

        setState(() {
          _resultController.text = result['paraphrased_text'] ?? text;
          _alternatives = List<String>.from(result['alternatives'] ?? []);
          _isLoading = false;
          _lastErrorMessage = null;
        });
        _persistDraft();
      }

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      _setErrorState(
          'Error paraphrasing text: $e', _ParaphraserRetryAction.mode);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error paraphrasing text: $e')),
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
          Icon(Icons.error_outline,
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
            onPressed: _isLoading ? null : _retryLastAction,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  // Show the paraphrased result — now stored in _resultController, shown inline
  void _showResultDialog() {
    // Results are now shown inline, no dialog needed
  }

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
              title: "Paraphraser",
              leadingIcon: Icon(
                CupertinoIcons.pencil_outline,
                color: colorScheme.primary,
              ),
              actions: [
                if (!isLoggedIn)
                  CustomButton(
                    text: "Log in",
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const LoginScreen(),
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
                activeColor: colorScheme.primary,
              ),
            _buildErrorBanner(colorScheme),
            Padding(
              padding: r.fromLTRB(16, 8, 16, 0),
              child: DocumentInputWidget(
                onTextExtracted: _onDocumentTextExtracted,
                currentDocument: _loadedDocument,
                onClear: _clearDocument,
                accentColor: colorScheme.primary,
                initialToolForViewer: 'paraphrase',
                onViewDocument: (doc) {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => DocumentViewerScreen(
                        document: doc,
                        initialTool: 'paraphrase',
                        onUseText: (text) {
                          _controller.text = text;
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
            // Input / Result area
            if (hasResult)
              Expanded(
                child: Padding(
                  padding: r.fromLTRB(16, 8, 16, 0),
                  child: _buildResultView(colorScheme),
                ),
              )
            else
              Expanded(
                child: Padding(
                  padding: r.fromLTRB(16, 8, 16, 0),
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: ConstrainedBox(
                      constraints:
                          BoxConstraints(maxHeight: r.inputFieldHeight),
                      child: SizedBox(
                        width: double.infinity,
                        child: _buildInputView(colorScheme),
                      ),
                    ),
                  ),
                ),
              ),
            // Bottom bar: modes + button
            _buildBottomBar(r, colorScheme),
          ],
        ),
      ),
    );
  }

  Widget _buildInputView(ColorScheme colorScheme) {
    final r = Responsive.of(context);
    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(r.r(16)),
      ),
      // readOnly + onTap routes editing through FocusedEditor (full-screen).
      // Inline TextField still renders the current value with selection +
      // copy support; typing happens in the dedicated editor screen.
      child: TextField(
        controller: _controller,
        maxLines: null,
        expands: true,
        readOnly: true,
        showCursor: false,
        textAlignVertical: TextAlignVertical.top,
        style: Theme.of(context).textTheme.bodyMedium!,
        decoration: InputDecoration(
          hintText: "Tap to paraphrase…",
          contentPadding: r.all(16),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(r.r(16)),
            borderSide: BorderSide.none,
          ),
          filled: true,
          fillColor: Colors.transparent,
          suffixIcon: _controller.text.isNotEmpty
              ? IconButton(
                  icon: Icon(Icons.clear, size: r.r(18)),
                  onPressed: () => setState(() {
                    _controller.clear();
                    _persistDraft();
                  }),
                )
              : IconButton(
                  icon: Icon(Icons.content_paste, size: r.r(18)),
                  onPressed: () async {
                    final data = await Clipboard.getData(Clipboard.kTextPlain);
                    if (data?.text != null) {
                      _controller.text = data!.text!;
                      setState(() {});
                      _persistDraft();
                    }
                  },
                ),
        ),
        onTap: () async {
          final result = await FocusedEditor.open(
            context,
            initialValue: _controller.text,
            title: 'Paraphrase',
            hint: 'Paste or type the text you want to paraphrase…',
          );
          if (result != null) {
            _controller.text = result;
            setState(() {});
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
        // Header bar with actions
        Container(
          padding: r.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: colorScheme.primary.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(r.r(14)),
          ),
          child: Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: colorScheme.primary,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.auto_awesome,
                    size: 16, color: Colors.white),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Paraphrased ($_selectedMode)',
                  style: Theme.of(context).textTheme.titleSmall!.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
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
                    _alternatives.clear();
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
                    _alternatives.clear();
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
        Expanded(
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                  color: colorScheme.primary.withValues(alpha: 0.15)),
            ),
            child: SingleChildScrollView(
              physics: const BouncingScrollPhysics(),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    _resultController.text,
                    style: Theme.of(context).textTheme.bodyMedium!,
                  ),
                  if (_alternatives.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: colorScheme.primary.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        'Alternatives',
                        style:
                            Theme.of(context).textTheme.labelMedium!.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: colorScheme.primary,
                                ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    ...List.generate(_alternatives.length, (i) {
                      return GestureDetector(
                        onTap: () {
                          setState(() {
                            final temp = _resultController.text;
                            _resultController.text = _alternatives[i];
                            _alternatives[i] = temp;
                          });
                        },
                        child: Container(
                          width: double.infinity,
                          margin: const EdgeInsets.only(bottom: 8),
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: colorScheme.surface,
                            borderRadius: BorderRadius.circular(12),
                            border:
                                Border.all(color: colorScheme.outlineVariant),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                width: 24,
                                height: 24,
                                decoration: BoxDecoration(
                                  color: colorScheme.primary
                                      .withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Center(
                                  child: Text(
                                    '${i + 1}',
                                    style: Theme.of(context)
                                        .textTheme
                                        .labelSmall!
                                        .copyWith(
                                          color: colorScheme.primary,
                                          fontWeight: FontWeight.bold,
                                        ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(_alternatives[i],
                                    style:
                                        Theme.of(context).textTheme.bodySmall!),
                              ),
                            ],
                          ),
                        ),
                      );
                    }),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

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
          // Modes / Personas toggle
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(24),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildTabButton(
                    'Modes',
                    !_usePersona,
                    () => setState(() {
                          _usePersona = false;
                          _persistDraft();
                        })),
                _buildTabButton(
                    'Personas',
                    _usePersona,
                    () => setState(() {
                          _usePersona = true;
                          _persistDraft();
                        })),
              ],
            ),
          ),
          const SizedBox(height: 10),
          // Scrollable chips
          SizedBox(
            height: r.h(38),
            child: !_usePersona
                ? _buildModeChips(colorScheme)
                : _buildPersonaChips(colorScheme),
          ),
          SizedBox(height: r.h(10)),
          // Paraphrase button
          Padding(
            padding: r.symmetric(horizontal: 16),
            child: CustomButton(
              text: _resultController.text.isNotEmpty
                  ? "Paraphrase Again"
                  : "Paraphrase",
              onPressed: _isLoading ? null : _paraphraseText,
              width: double.infinity,
              isLoading: _isLoading,
            ),
          ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }

  Widget _buildTabButton(String label, bool active, VoidCallback onTap) {
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        decoration: BoxDecoration(
          color: active ? colorScheme.primary : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelMedium!.copyWith(
                fontWeight: active ? FontWeight.bold : FontWeight.normal,
                color: active
                    ? colorScheme.onPrimary
                    : colorScheme.onSurfaceVariant,
              ),
        ),
      ),
    );
  }

  static const _modeIcons = <String, IconData>{
    'Standard': Icons.text_format,
    'Fluency': Icons.water_drop_outlined,
    'Academic': Icons.school_outlined,
    'Humanize': Icons.person_outline,
    'Formal': Icons.work_outline,
    'Simple': Icons.lightbulb_outline,
    'Creative': Icons.palette_outlined,
    'Expand': Icons.unfold_more,
    'Shorten': Icons.unfold_less,
    'Custom': Icons.tune,
  };

  Widget _buildModeChips(ColorScheme colorScheme) {
    final modes = [
      "Standard",
      "Fluency",
      "Academic",
      "Humanize",
      "Formal",
      "Simple",
      "Creative",
      "Expand",
      "Shorten",
      "Custom",
    ];
    return ListView.separated(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      itemCount: modes.length,
      separatorBuilder: (_, __) => const SizedBox(width: 6),
      itemBuilder: (_, i) {
        final mode = modes[i];
        final selected = _selectedMode == mode;
        return GestureDetector(
          onTap: () {
            setState(() => _selectedMode = mode);
            _persistDraft();
            if (mode == 'Custom') _showCustomModeDialog();
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color:
                  selected ? colorScheme.primary : colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(20),
              border: selected
                  ? null
                  : Border.all(color: colorScheme.outlineVariant),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  _modeIcons[mode] ?? Icons.text_format,
                  size: 14,
                  color: selected
                      ? colorScheme.onPrimary
                      : colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  mode,
                  style: Theme.of(context).textTheme.labelMedium!.copyWith(
                        color: selected
                            ? colorScheme.onPrimary
                            : colorScheme.onSurfaceVariant,
                        fontWeight:
                            selected ? FontWeight.bold : FontWeight.normal,
                      ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildPersonaChips(ColorScheme colorScheme) {
    final personas = PersonaManager.personas;
    return ListView.separated(
      scrollDirection: Axis.horizontal,
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      itemCount: personas.length,
      separatorBuilder: (_, __) => const SizedBox(width: 6),
      itemBuilder: (_, i) {
        final persona = personas[i];
        final selected = _selectedPersona?.name == persona.name;
        return GestureDetector(
          onTap: () => setState(() {
            _selectedPersona = persona;
            _persistDraft();
          }),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: selected
                  ? colorScheme.secondary
                  : colorScheme.surfaceContainer,
              borderRadius: BorderRadius.circular(20),
              border: selected
                  ? null
                  : Border.all(color: colorScheme.outlineVariant),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  persona.name,
                  style: Theme.of(context).textTheme.labelMedium!.copyWith(
                        color: selected
                            ? colorScheme.onSecondary
                            : colorScheme.onSurfaceVariant,
                        fontWeight:
                            selected ? FontWeight.bold : FontWeight.normal,
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
