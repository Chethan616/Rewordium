import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:animate_do/animate_do.dart';
import 'package:flutter/services.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';
import 'package:lottie/lottie.dart';

import '../theme/app_theme.dart';
import '../widgets/custom_app_bar.dart';
import '../widgets/custom_button.dart';
import '../providers/auth_provider.dart';
import '../services/groq_service.dart';

// Import your login screen here; adjust path as needed
import 'auth/login_screen.dart';

class ToneEditorPage extends StatefulWidget {
  const ToneEditorPage({super.key});

  @override
  State<ToneEditorPage> createState() => _ToneEditorPageState();
}

class _ToneEditorPageState extends State<ToneEditorPage> {
  final TextEditingController _controller = TextEditingController();
  final TextEditingController _resultController = TextEditingController();
  final TextEditingController _customToneController = TextEditingController();
  bool _isLoading = false;
  String _selectedTone = "Professional";
  Map<String, dynamic>? _toneResult;
  List<String> _changesMade = [];

  final List<String> _tones = [
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
    "Authoritative",
    "Inspirational",
    "Diplomatic",
    "Respectful",
    "Urgent",
    "Compassionate",
    "Optimistic",
    "Serious",
    "Custom"
  ];

  @override
  void dispose() {
    _controller.dispose();
    _resultController.dispose();
    _customToneController.dispose();
    super.dispose();
  }

  // Edit the tone of text using OpenAI
  Future<void> _editTone() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter some text to edit')),
      );
      return;
    }

    // Handle custom tone selection
    if (_selectedTone == "Custom") {
      await _showCustomToneDialog();
      if (_selectedTone == "Custom" && _customToneController.text.isEmpty) {
        return; // User canceled or didn't enter a custom tone
      }
    }

    setState(() {
      _isLoading = true;
    });

    try {
      final targetTone = _selectedTone == 'Custom' 
          ? _customToneController.text 
          : _selectedTone.toLowerCase();
          
      final result = await GroqService.editTone(text, targetTone);

      setState(() {
        _toneResult = result;
        _resultController.text = result['edited_text'] ?? text;
        _changesMade = List<String>.from(result['changes_made'] ?? []);
        _isLoading = false;
      });

      // Show the result dialog
      _showResultDialog();
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error editing tone: $e')),
      );
    }
  }

  // Show custom tone selection dialog
  Future<void> _showCustomToneDialog() async {
    return showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Enter Custom Tone'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Describe the tone you want for your text:'),
            SizedBox(height: 8),
            TextField(
              controller: _customToneController,
              decoration: InputDecoration(
                hintText: 'e.g., Sarcastic, Poetic, Technical, Childlike',
                border: OutlineInputBorder(),
              ),
            ),
            SizedBox(height: 12),
            Text(
              'You can also describe the tone in more detail, like "professional but with a touch of humor" or "poetic with Victorian-era language".',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
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
              final customTone = _customToneController.text.trim();
              if (customTone.isNotEmpty) {
                Navigator.pop(context);
              } else {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a tone description')),
                );
              }
            },
            child: Text('Apply'),
          ),
        ],
      ),
    );
  }

  // Show the tone editing result
  void _showResultDialog() {
    if (_toneResult == null) return;

    final String originalTone = _toneResult!['original_tone'] ?? 'Unknown';

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Tone Edited Text'),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Original Tone: $originalTone',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Text('New Tone: ${_selectedTone == "Custom" ? _customToneController.text : _selectedTone}',
                    style: TextStyle(fontWeight: FontWeight.bold, color: Colors.teal)),
                SizedBox(height: 16),
                Text('Edited Text:',
                    style: TextStyle(fontWeight: FontWeight.bold)),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.teal.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.teal.withOpacity(0.3)),
                  ),
                  child: Text(_resultController.text),
                ),
                if (_changesMade.isNotEmpty) ...[
                  SizedBox(height: 16),
                  Text('Changes Made:',
                      style: TextStyle(fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  ...List.generate(_changesMade.length, (index) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('• ', 
                              style: TextStyle(color: Colors.teal)),
                          Expanded(
                            child: Text(_changesMade[index], 
                                style: TextStyle(color: Colors.teal.shade700)),
                          ),
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
              Clipboard.setData(ClipboardData(text: _resultController.text));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Edited text copied to clipboard')),
              );
            },
            child: Text('Copy Text'),
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
              title: "Tone Editor",
              leadingIcon: Icon(
                CupertinoIcons.waveform,
                color: Colors.teal,
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
                      hintText: "Enter text to adjust its tone...",
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
                  Text("Target Tone:", style: AppTheme.bodySmall.copyWith(fontWeight: FontWeight.w600)),
                  SizedBox(width: 8),
                  Expanded(
                    child: Container(
                      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.teal.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: Colors.teal.withOpacity(0.2)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.teal.withOpacity(0.05),
                            blurRadius: 4,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _selectedTone,
                          isExpanded: true,
                          icon: Icon(
                            CupertinoIcons.chevron_down,
                            color: Colors.teal,
                            size: 16,
                          ),
                          borderRadius: BorderRadius.circular(10),
                          dropdownColor: AppTheme.cardColor,
                          style: AppTheme.bodyMedium.copyWith(
                            color: Colors.teal.shade700,
                            fontWeight: FontWeight.w500,
                          ),
                          items: _tones.map((String tone) {
                            return DropdownMenuItem<String>(
                              value: tone,
                              child: Text(
                                tone,
                                style: TextStyle(
                                  color: tone == _selectedTone
                                      ? Colors.teal
                                      : AppTheme.textPrimaryColor,
                                ),
                              ),
                            );
                          }).toList(),
                          onChanged: (String? newValue) {
                            if (newValue != null) {
                              setState(() {
                                _selectedTone = newValue;
                                if (newValue == "Custom") {
                                  _showCustomToneDialog();
                                }
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
                      Container(
                        height: isSmallScreen ? 200 : 250,  // Even larger container
                        padding: const EdgeInsets.all(16),
                        child: Shimmer.fromColors(
                          baseColor: Colors.teal.shade300,
                          highlightColor: Colors.teal.shade100,
                          period: const Duration(seconds: 3),
                          child: Center(
                            child: Lottie.asset(
                              'assets/lottie/toneEditor.json',
                              height: isSmallScreen ? 180 : 220,  // Larger animation
                              width: isSmallScreen ? 180 : 220,   // Make it square
                              fit: BoxFit.contain,
                              repeat: true,
                              animate: true,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Text(
                          "Enter or paste text and select a tone to apply",
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
                  text: "Edit Tone",
                  onPressed: _isLoading ? null : _editTone,
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
