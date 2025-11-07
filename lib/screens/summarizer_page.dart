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

class SummarizerPage extends StatefulWidget {
  const SummarizerPage({super.key});

  @override
  State<SummarizerPage> createState() => _SummarizerPageState();
}

class _SummarizerPageState extends State<SummarizerPage> {
  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  bool _isLoading = false;
  String _selectedLength = "Medium";
  Map<String, dynamic>? _summaryResult;
  List<String> _keyPoints = [];

  final List<String> _summaryLengths = [
    "Very Short",
    "Short",
    "Medium",
    "Long",
    "Detailed"
  ];

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    super.dispose();
  }

  // Summarize the text using Groq
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
    });

    try {
      final result = await GroqService.summarizeText(text, length: _selectedLength.toLowerCase());

      setState(() {
        _summaryResult = result;
        _resultController.text = result['summary'] ?? text;
        _keyPoints = List<String>.from(result['key_points'] ?? []);
        _isLoading = false;
      });

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error summarizing text: $e')),
      );
    }
  }

  // Show the summary result
  void _showResultDialog() {
    if (_summaryResult == null) return;

    final int originalWordCount = _summaryResult!['word_count_original'] ?? 0;
    final int summaryWordCount = _summaryResult!['word_count_summary'] ?? 0;
    final double reductionPercentage = originalWordCount > 0 
        ? ((originalWordCount - summaryWordCount) / originalWordCount * 100) 
        : 0;

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Summary'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Original: $originalWordCount words',
                        style: TextStyle(fontSize: 12, color: Colors.grey)),
                    Text('Summary: $summaryWordCount words',
                        style: TextStyle(fontSize: 12, color: Colors.grey)),
                  ],
                ),
                Text('Reduced by ${reductionPercentage.toStringAsFixed(1)}%',
                    style: TextStyle(fontSize: 12, color: Colors.orange)),
                SizedBox(height: 16),
                Text('Summary:',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.orange.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.orange.withOpacity(0.3)),
                  ),
                  child: Text(_resultController.text),
                ),
                if (_keyPoints.isNotEmpty) ...[
                  SizedBox(height: 16),
                  Text('Key Points:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  ...List.generate(_keyPoints.length, (index) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('${index + 1}. ', 
                              style: TextStyle(fontWeight: FontWeight.bold, color: Colors.orange)),
                          Expanded(child: Text(_keyPoints[index])),
                        ],
                      ),
                    );
                  }),
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
              String textToCopy = _resultController.text;
              
              if (_keyPoints.isNotEmpty) {
                textToCopy += '\n\nKey Points:\n';
                for (int i = 0; i < _keyPoints.length; i++) {
                  textToCopy += '${i + 1}. ${_keyPoints[i]}\n';
                }
              }
              
              Clipboard.setData(ClipboardData(text: textToCopy));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Summary copied to clipboard')),
              );
            },
            child: Text('Copy Summary'),
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
              title: "Summarizer",
              leadingIcon: Icon(
                CupertinoIcons.doc_text_search,
                color: Colors.orange,
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
                      hintText: "Enter text to summarize...",
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
                  Text("Summary Length:", style: AppTheme.bodySmall.copyWith(fontWeight: FontWeight.w600)),
                  SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.orange.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.orange.withOpacity(0.2)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.orange.withOpacity(0.05),
                            blurRadius: 4,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _selectedLength,
                          isExpanded: true,
                          icon: Icon(
                            CupertinoIcons.chevron_down,
                            color: Colors.orange,
                            size: 16,
                          ),
                          borderRadius: BorderRadius.circular(10),
                          dropdownColor: AppTheme.cardColor,
                          style: AppTheme.bodyMedium.copyWith(
                            color: Colors.orange.shade700,
                            fontWeight: FontWeight.w500,
                          ),
                          items: _summaryLengths.map((String length) {
                            return DropdownMenuItem<String>(
                              value: length,
                              child: Text(
                                length,
                                style: TextStyle(
                                  color: length == _selectedLength
                                      ? Colors.orange
                                      : AppTheme.textPrimaryColor,
                                ),
                              ),
                            );
                          }).toList(),
                          onChanged: (String? newValue) {
                            if (newValue != null) {
                              setState(() {
                                _selectedLength = newValue;
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
                          baseColor: Colors.orange.shade300,
                          highlightColor: Colors.orange.shade100,
                          period: const Duration(seconds: 3),
                          child: LottieAssets.getSummarizerAnimation(
                            height: isSmallScreen ? 80 : 100,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Enter or paste text to create a concise summary",
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
                  text: "Summarize",
                  onPressed: _isLoading ? null : _summarizeText,
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
