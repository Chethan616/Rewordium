import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:flutter/services.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';

import '../theme/app_theme.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../utils/lottie_assets.dart';
import '../providers/auth_provider.dart';
import '../services/groq_service.dart';

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
      final result = await GroqService.translateText(text, _selectedLanguage);

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
  void _showResultDialog() {
    if (_translationResult == null) return;

    final String sourceLanguage = _translationResult!['detected_source_language'] ?? 'Unknown';
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
                    color: Colors.blue.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.blue.withOpacity(0.3)),
                  ),
                  child: Text(_resultController.text),
                ),
                if (notes.isNotEmpty) ...[
                  SizedBox(height: 16),
                  Text('Notes:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.grey.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.grey.withOpacity(0.3)),
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
                const SnackBar(content: Text('Translation copied to clipboard')),
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
    final bool isSmallScreen = screenSize.width < 360;
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
                    width: isSmallScreen ? 70 : 90,
                    height: 36,
                    type: ButtonType.primary,
                  ),
                if (!isLoggedIn) const SizedBox(width: 8),
              ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Material(
                elevation: 2,
                borderRadius: BorderRadius.circular(16),
                child: SizedBox(
                  height: screenSize.height * 0.25,
                  child: TextField(
                    controller: _controller,
                    maxLines: null,
                    expands: true,
                    textAlignVertical: TextAlignVertical.top,
                    style: AppTheme.bodyMedium,
                    decoration: InputDecoration(
                      hintText: "Enter text to translate...",
                      contentPadding: const EdgeInsets.symmetric(
                          vertical: 16, horizontal: 16),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                      filled: true,
                      fillColor: AppTheme.cardColor,
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Text("Translate to:", style: AppTheme.bodySmall.copyWith(fontWeight: FontWeight.w600)),
                  SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.blue.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.blue.withOpacity(0.2)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.blue.withOpacity(0.05),
                            blurRadius: 4,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _selectedLanguage,
                          isExpanded: true,
                          icon: Icon(
                            CupertinoIcons.chevron_down,
                            color: Colors.blue,
                            size: 16,
                          ),
                          borderRadius: BorderRadius.circular(10),
                          dropdownColor: AppTheme.cardColor,
                          style: AppTheme.bodyMedium.copyWith(
                            color: Colors.blue.shade700,
                            fontWeight: FontWeight.w500,
                          ),
                          items: _languages.map((String language) {
                            return DropdownMenuItem<String>(
                              value: language,
                              child: Text(
                                language,
                                style: TextStyle(
                                  color: language == _selectedLanguage
                                      ? Colors.blue
                                      : AppTheme.textPrimaryColor,
                                ),
                              ),
                            );
                          }).toList(),
                          onChanged: (String? newValue) {
                            if (newValue != null) {
                              setState(() {
                                _selectedLanguage = newValue;
                              });
                            }
                          },
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
                        height: isSmallScreen ? 100 : 120,
                        child: Shimmer.fromColors(
                          baseColor: Colors.blue.shade300,
                          highlightColor: Colors.blue.shade100,
                          period: const Duration(seconds: 3),
                          child: LottieAssets.getTranslatorAnimation(
                            height: isSmallScreen ? 80 : 100,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Enter or paste text and select a language",
                          textAlign: TextAlign.center,
                          style: AppTheme.bodyMedium.copyWith(
                            color: AppTheme.textSecondaryColor,
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
                                    SnackBar(
                                        content: Text('Paste failed: $e')),
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
