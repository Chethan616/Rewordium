import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:flutter/services.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';

import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../utils/lottie_assets.dart';
import '../providers/auth_provider.dart';
import '../services/unified_ai_service.dart';
import '../services/document_chunking_service.dart';
import '../utils/ai_error_handler.dart';
import '../models/document_result.dart';
import '../widgets/document_input_widget.dart';
import '../screens/document_viewer_screen.dart';
import '../utils/responsive.dart';

// Import your login screen here; adjust path as needed
import 'auth/login_screen.dart';

class TranslatorPage extends StatefulWidget {
  const TranslatorPage({super.key});

  @override
  State<TranslatorPage> createState() => _TranslatorPageState();
}

class _TranslatorPageState extends State<TranslatorPage> {
  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  bool _isLoading = false;
  String _selectedLanguage = "Spanish";
  Map<String, dynamic>? _translationResult;
  DocumentResult? _loadedDocument;

  void _onDocumentTextExtracted(String text, DocumentResult doc) {
    setState(() {
      _controller.text = text;
      _loadedDocument = doc;
    });
  }

  void _clearDocument() {
    setState(() => _loadedDocument = null);
  }

  final List<String> _languages = [
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
    "Custom"
  ];

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    super.dispose();
  }

  // Translate the text using Groq
  Future<void> _translateText() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to translate')),
      );
      return;
    }

    // Handle custom language selection
    if (_selectedLanguage == "Custom") {
      await _showCustomLanguageDialog();
      if (_selectedLanguage == "Custom") {
        return; // User canceled
      }
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final result = DocumentChunkingService.needsChunking(text)
          ? await DocumentChunkingService.translateLarge(
              text, _selectedLanguage)
          : await UnifiedAIService.translateText(text, _selectedLanguage);

      // Handle API errors with snackbar
      if (result.containsKey('error')) {
        setState(() {
          _isLoading = false;
        });
        if (mounted) {
          AIErrorHandler.showErrorSnackBar(context, result);
        }
        return;
      }

      setState(() {
        _translationResult = result;
        _resultController.text = result['translated_text'] ?? text;
        _isLoading = false;
      });

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error translating text: $e')),
      );
    }
  }

  // Show custom language selection dialog
  Future<void> _showCustomLanguageDialog() async {
    final TextEditingController customLangController = TextEditingController();

    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Enter Target Language'),
        content: TextField(
          controller: customLangController,
          decoration: InputDecoration(
            hintText: 'e.g., Swahili, Bengali, Tagalog',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              // Keep "Custom" selected but don't proceed
            },
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final customLanguage = customLangController.text.trim();
              if (customLanguage.isNotEmpty) {
                setState(() {
                  _selectedLanguage = customLanguage;
                });
                Navigator.pop(context);
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a language')),
                );
              }
            },
            child: Text('Translate'),
          ),
        ],
      ),
    );
  }

  // Show the translation result

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
                // Handle bar
                Container(
                  margin: const EdgeInsets.only(top: 12, bottom: 8),
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: cs.onSurfaceVariant.withOpacity(0.4),
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
                      return ListTile(
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

  void _showResultDialog() {
    if (_translationResult == null) return;

    final String sourceLanguage =
        _translationResult!['detected_source_language'] ?? 'Unknown';
    final String notes = _translationResult!['notes'] ?? '';

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Translation Result'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('From: $sourceLanguage',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Text('To: $_selectedLanguage',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                SizedBox(height: 16),
                Text('Translation:',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context)
                        .colorScheme
                        .primaryContainer
                        .withOpacity(0.3),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                        color: Theme.of(context)
                            .colorScheme
                            .primary
                            .withOpacity(0.3)),
                  ),
                  child: Text(_resultController.text),
                ),
                if (notes.isNotEmpty) ...[
                  SizedBox(height: 16),
                  Text('Notes:', style: TextStyle(fontWeight: FontWeight.bold)),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context)
                          .colorScheme
                          .surfaceContainerHighest
                          .withOpacity(0.5),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                          color: Theme.of(context).colorScheme.outlineVariant),
                    ),
                    child: Text(notes),
                  ),
                ],
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Close'),
          ),
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: _resultController.text));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                    content: Text('Translation copied to clipboard')),
              );
            },
            child: Text('Copy Translation'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final Size screenSize = MediaQuery.of(context).size;
    final r = Responsive.of(context);
    final authProvider = Provider.of<AuthProvider>(context);
    final bool isLoggedIn = authProvider.isLoggedIn;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            CustomAppBar(
              title: "Translator",
              leadingIcon: Icon(
                CupertinoIcons.globe,
                color: Colors.blue,
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
                    width: r.w(80),
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
                activeColor: Colors.blue,
              ),
            Padding(
              padding: r.fromLTRB(16, 12, 16, 0),
              child: DocumentInputWidget(
                onTextExtracted: _onDocumentTextExtracted,
                currentDocument: _loadedDocument,
                onClear: _clearDocument,
                accentColor: Colors.blue,
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
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
            Padding(
              padding: r.fromLTRB(16, 8, 16, 8),
              child: Material(
                elevation: 2,
                borderRadius: BorderRadius.circular(r.r(16)),
                child: SizedBox(
                  height: r.inputFieldHeight * 0.65,
                  child: TextField(
                    controller: _controller,
                    maxLines: null,
                    expands: true,
                    textAlignVertical: TextAlignVertical.top,
                    style: Theme.of(context).textTheme.bodyMedium!,
                    decoration: InputDecoration(
                      hintText: "Enter text to translate...",
                      contentPadding: const EdgeInsets.symmetric(
                          vertical: 16, horizontal: 16),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                      filled: true,
                      fillColor: Theme.of(context).colorScheme.surface,
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Text("Translate to:",
                      style: Theme.of(context)
                          .textTheme
                          .bodySmall!
                          .copyWith(fontWeight: FontWeight.w600)),
                  SizedBox(width: 8),
                  Expanded(
                    child: InkWell(
                      borderRadius: BorderRadius.circular(10),
                      onTap: () => _showLanguageBottomSheet(),
                      child: Container(
                        padding:
                            EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .primaryContainer
                              .withOpacity(0.3),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                            color: Theme.of(context)
                                .colorScheme
                                .primary
                                .withOpacity(0.2),
                          ),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                _selectedLanguage,
                                style: Theme.of(context)
                                    .textTheme
                                    .bodyMedium!
                                    .copyWith(
                                      color:
                                          Theme.of(context).colorScheme.primary,
                                      fontWeight: FontWeight.w500,
                                    ),
                              ),
                            ),
                            Icon(
                              CupertinoIcons.chevron_down,
                              color: Theme.of(context).colorScheme.primary,
                              size: 16,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: FadeIn(
                child: SingleChildScrollView(
                  physics: const BouncingScrollPhysics(),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const SizedBox(height: 12),
                      SizedBox(
                        height: r.h(110),
                        child: Shimmer.fromColors(
                          baseColor: Colors.blue.shade300,
                          highlightColor: Colors.blue.shade100,
                          period: const Duration(seconds: 3),
                          child: LottieAssets.getTranslatorAnimation(
                            height: r.h(90),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Enter or paste text and select a language",
                          textAlign: TextAlign.center,
                          style:
                              Theme.of(context).textTheme.bodyMedium!.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurfaceVariant,
                                  ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            CustomButton(
                              text: "Paste Text",
                              onPressed: () async {
                                try {
                                  final ClipboardData? clipboardData =
                                      await Clipboard.getData(
                                          Clipboard.kTextPlain);
                                  if (clipboardData != null &&
                                      clipboardData.text != null) {
                                    _controller.text = clipboardData.text!;
                                  }
                                } catch (e) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Paste failed: $e')),
                                  );
                                }
                              },
                              icon: Icons.content_paste,
                              type: ButtonType.secondary,
                              width: 140,
                            ),
                            const SizedBox(width: 16),
                            CustomButton(
                              text: "Clear",
                              onPressed: () {
                                _controller.clear();
                              },
                              icon: CupertinoIcons.clear,
                              type: ButtonType.secondary,
                              width: 140,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                  ),
                ),
              ),
            ),
            FadeInUp(
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: CustomButton(
                  text: "Translate",
                  onPressed: _isLoading ? null : _translateText,
                  width: screenSize.width * 0.8,
                  isLoading: _isLoading,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
