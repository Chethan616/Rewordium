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

class AIDetectorPage extends StatefulWidget {
  const AIDetectorPage({super.key});

  @override
  State<AIDetectorPage> createState() => _AIDetectorPageState();
}

class _AIDetectorPageState extends State<AIDetectorPage> {
  final TextEditingController _controller = TextEditingController();
  bool _isLoading = false;
  Map<String, dynamic>? _result;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  // Detect AI in the text using OpenAI
  Future<void> _detectAIText() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to analyze')),
      );
      return;
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final result = await GroqService.detectAIText(text);

      setState(() {
        _result = result;
        _isLoading = false;
      });

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error analyzing text: $e')),
      );
    }
  }

  // Show the detection result
  void _showResultDialog() {
    if (_result == null) return;

    final double probability = _result!['ai_probability'] ?? 0.5;
    final String confidence = _result!['confidence'] ?? 'low';
    final String reasoning = _result!['reasoning'] ?? 'No reasoning provided';
    final List<dynamic> humanIndicators = _result!['human_indicators'] ?? [];
    final List<dynamic> aiIndicators = _result!['ai_indicators'] ?? [];

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('AI Detection Results'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildProbabilityMeter(probability),
                SizedBox(height: 16),
                Text('Confidence: $confidence',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                SizedBox(height: 8),
                Text('Analysis:',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.grey.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.grey.withOpacity(0.3)),
                  ),
                  child: Text(reasoning),
                ),
                SizedBox(height: 16),
                if (humanIndicators.isNotEmpty) ...[
                  Text('Human Authorship Indicators:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  ...humanIndicators.map((indicator) => Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('• ', style: TextStyle(color: Colors.green)),
                            Expanded(
                                child: Text(indicator.toString(),
                                    style: TextStyle(color: Colors.green))),
                          ],
                        ),
                      )),
                  SizedBox(height: 8),
                ],
                if (aiIndicators.isNotEmpty) ...[
                  Text('AI Authorship Indicators:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  ...aiIndicators.map((indicator) => Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('• ', style: TextStyle(color: Colors.red)),
                            Expanded(
                                child: Text(indicator.toString(),
                                    style: TextStyle(color: Colors.red))),
                          ],
                        ),
                      )),
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
              Clipboard.setData(ClipboardData(
                  text:
                      'AI Probability: ${(probability * 100).toStringAsFixed(1)}%\nConfidence: $confidence\nAnalysis: $reasoning'));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Results copied to clipboard')),
              );
            },
            child: Text('Copy Results'),
          ),
        ],
      ),
    );
  }

  Widget _buildProbabilityMeter(double probability) {
    final Color meterColor = probability < 0.4
        ? Colors.green
        : probability < 0.7
            ? Colors.orange
            : Colors.red;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('AI Probability:',
                style: TextStyle(fontWeight: FontWeight.bold)),
            Text('${(probability * 100).toStringAsFixed(1)}%',
                style: TextStyle(
                    fontWeight: FontWeight.bold, color: meterColor)),
          ],
        ),
        SizedBox(height: 8),
        Container(
          height: 20,
          decoration: BoxDecoration(
            color: Colors.grey.withOpacity(0.3),
            borderRadius: BorderRadius.circular(10),
          ),
          child: FractionallySizedBox(
            widthFactor: probability,
            alignment: Alignment.centerLeft,
            child: Container(
              decoration: BoxDecoration(
                color: meterColor,
                borderRadius: BorderRadius.circular(10),
              ),
            ),
          ),
        ),
        SizedBox(height: 4),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Human-written',
                style: TextStyle(fontSize: 12, color: Colors.green)),
            Text('AI-written',
                style: TextStyle(fontSize: 12, color: Colors.red)),
          ],
        ),
      ],
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
              title: "AI Detector",
              leadingIcon: Icon(
                CupertinoIcons.device_phone_portrait,
                color: Colors.purple,
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
                  height: screenSize.height * 0.3,
                  child: TextField(
                    controller: _controller,
                    maxLines: null,
                    expands: true,
                    textAlignVertical: TextAlignVertical.top,
                    style: AppTheme.bodyMedium,
                    decoration: InputDecoration(
                      hintText: "Enter text to analyze for AI detection...",
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
                          baseColor: Colors.purple.shade300,
                          highlightColor: Colors.purple.shade100,
                          period: const Duration(seconds: 3),
                          child: LottieAssets.getAIDetectorAnimation(
                            height: isSmallScreen ? 80 : 100,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Enter or paste text to check if it was written by AI",
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
                  text: "Detect AI",
                  onPressed: _isLoading ? null : _detectAIText,
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
