import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:animate_do/animate_do.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';

import '../theme/app_theme.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../utils/lottie_assets.dart';
import '../providers/auth_provider.dart';
import '../services/groq_service.dart';
import 'auth/login_screen.dart';

class GrammarPage extends StatefulWidget {
  const GrammarPage({super.key});

  @override
  State<GrammarPage> createState() => _GrammarPageState();
}

class _GrammarPageState extends State<GrammarPage> {
  final TextEditingController _textController = TextEditingController();
  final FocusNode _textFocusNode = FocusNode();
  int _wordCount = 0;
  int _errorCount = 0;
  bool _isChecking = false;
  String _correctedText = '';
  List<Map<String, dynamic>> _errors = [];

  @override
  void initState() {
    super.initState();
    // Initialize Groq service
    GroqService.initialize();
  }

  @override
  void dispose() {
    _textController.dispose();
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
    });

    try {
      final result = await GroqService.checkGrammar(text);
      setState(() {
        _correctedText = result['corrected_text'] ?? text;
        _errorCount = result['error_count'] ?? 0;
        _errors = List<Map<String, dynamic>>.from(result['errors'] ?? []);
        _isChecking = false;
      });

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
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error checking grammar: $e')),
      );
    }
  }

  void _showErrorsDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Grammar Check Results'),
        content: Container(
          width: double.maxFinite,
          child: ListView(
            shrinkWrap: true,
            children: [
              Text('Found $_errorCount errors'),
              const SizedBox(height: 16),
              ..._errors.map((error) => _buildErrorItem(error)),
              const SizedBox(height: 16),
              Text('Corrected Text:'),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.green.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.green.withOpacity(0.3)),
                ),
                child: Text(_correctedText),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Close'),
          ),
          TextButton(
            onPressed: () {
              _textController.text = _correctedText;
              Navigator.pop(context);
            },
            child: Text('Apply Corrections'),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorItem(Map<String, dynamic> error) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.red.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.red.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Original: ${error['original']}',
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 4),
          Text('Correction: ${error['correction']}'),
          const SizedBox(height: 4),
          Text('Explanation: ${error['explanation']}'),
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

    return Material(
      child: Column(
        children: [
        CustomAppBar(
          title: "Grammar Check",
          leadingIcon:
              const Icon(CupertinoIcons.checkmark_seal, color: Colors.red),
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
        Expanded(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: LayoutBuilder(
              builder: (context, constraints) {
                return SingleChildScrollView(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight: constraints.maxHeight,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        FadeIn(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [
                              // Lottie animation (restored but with smaller size)
                              SizedBox(
                                height: isSmallScreen ? 80 : 100,
                                child: Shimmer.fromColors(
                                  baseColor: Colors.red.shade300,
                                  highlightColor: Colors.red.shade100,
                                  period: const Duration(seconds: 3),
                                  child:
                                      LottieAssets.getGrammarCheckAnimation(),
                                ),
                              ),
                              const SizedBox(height: 16),
                              Text(
                                "Enter or paste your text to check grammar",
                                textAlign: TextAlign.center,
                                style: AppTheme.bodyMedium.copyWith(
                                  color: AppTheme.textSecondaryColor,
                                ),
                              ),
                              const SizedBox(height: 20),
                              Row(
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
                                          _textController.text =
                                              clipboardData.text!;
                                          _updateWordCount(clipboardData.text!);
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
                                  const SizedBox(width: 12),
                                  CustomButton(
                                    text: "Check Grammar",
                                    onPressed: _isChecking ? null : _checkGrammar,
                                    icon: _isChecking ? null : Icons.spellcheck,
                                    type: ButtonType.primary,
                                    width: 160,
                                    isLoading: _isChecking,
                                  ),
                                ],
                              ),
                              const SizedBox(height: 16),
                            ],
                          ),
                        ),
                        _buildTextInputField(),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ),
        FadeInUp(
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
            decoration: BoxDecoration(
              color: AppTheme.cardColor,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.03),
                  blurRadius: 10,
                  offset: const Offset(0, -5),
                ),
              ],
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatChip(
                    "$_wordCount Words", Icons.text_fields, isSmallScreen),
                _buildStatChip(
                    "$_errorCount Errors", Icons.error_outline, isSmallScreen, _errorCount > 0 ? Colors.red : null),
                if (!isSmallScreen)
                  _buildStatChip(
                    "0 Improvements",
                    Icons.lightbulb_outline,
                    isSmallScreen,
                  ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
  }

  Widget _buildStatChip(String label, IconData icon, bool isSmallScreen, [Color? accentColor]) {
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: isSmallScreen ? 8 : 12,
        vertical: 8,
      ),
      decoration: BoxDecoration(
        color: AppTheme.backgroundColor,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            size: isSmallScreen ? 14 : 16,
            color: accentColor ?? AppTheme.textSecondaryColor,
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: AppTheme.bodySmall.copyWith(
              fontSize: isSmallScreen ? 10 : 12,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTextInputField() {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 100, maxHeight: 300),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.red.withOpacity(0.1),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
        border: Border.all(color: Colors.red.withOpacity(0.3)),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: TextField(
            controller: _textController,
            focusNode: _textFocusNode,
            maxLines: null,
            keyboardType: TextInputType.multiline,
            decoration: InputDecoration(
              border: InputBorder.none,
              hintText: "Type or paste your text here...",
              hintStyle: AppTheme.bodyMedium.copyWith(
                color: AppTheme.textSecondaryColor,
              ),
              suffixIcon: IconButton(
                icon: Icon(Icons.edit, color: Colors.red.withOpacity(0.6)),
                onPressed: () => _textFocusNode.requestFocus(),
              ),
            ),
            onChanged: (text) => _updateWordCount(text),
          ),
        ),
      ),
    );
  }
}
