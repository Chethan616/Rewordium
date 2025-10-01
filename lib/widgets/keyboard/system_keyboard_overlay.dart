import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'dart:async';
import '../../providers/keyboard_provider.dart';
import '../../theme/app_theme.dart';
import '../../screens/paraphraser_page.dart';
import '../../services/groq_service.dart';

class SystemKeyboardOverlay extends StatefulWidget {
  final Function(bool) onVisibilityChanged;
  
  const SystemKeyboardOverlay({
    Key? key,
    required this.onVisibilityChanged,
  }) : super(key: key);

  @override
  State<SystemKeyboardOverlay> createState() => _SystemKeyboardOverlayState();
}

class _SystemKeyboardOverlayState extends State<SystemKeyboardOverlay> {
  bool _isExpanded = false;
  
  // Text controller and focus node for the keyboard
  final TextEditingController _textController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  
  @override
  void initState() {
    super.initState();
    widget.onVisibilityChanged(true);
  }
  
  @override
  void dispose() {
    widget.onVisibilityChanged(false);
    super.dispose();
  }
  
  // Toggle keyboard visibility
  void _toggleKeyboard() {
    setState(() {
      _isExpanded = !_isExpanded;
    });
  }
  
  // Paraphrase the text in the text controller
  Future<void> _paraphraseText() async {
    final text = _textController.text;
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No text to paraphrase')),
      );
      return;
    }
    
    setState(() {
      // Show loading indicator
    });
    
    try {
      // Use Groq service for paraphrasing (it has its own timeout handling)
      final result = await GroqService.paraphraseText(text, 'natural');
      
      // Check if there was an error in the response
      if (result.containsKey('error')) {
        throw Exception(result['error']);
      }
      
      // Apply the paraphrased text
      final paraphrasedText = result['paraphrased_text'] ?? text;
      _textController.text = paraphrasedText;
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Text paraphrased successfully')),
        );
      }
    } catch (e) {
      print('Paraphrasing error: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error paraphrasing text: $e')),
        );
      }
    }
  }
  
  // Open the full paraphraser page
  void _openParaphraserPage() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const ParaphraserPage(),
      ),
    );
  }
  
  @override
  Widget build(BuildContext context) {
    final keyboardProvider = Provider.of<KeyboardProvider>(context);
    // Use the keyboard provider to check if the system keyboard is enabled
    if (!keyboardProvider.isSystemKeyboardEnabled) {
      // If not enabled, hide the overlay
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.onVisibilityChanged(false);
      });
      return const SizedBox.shrink();
    }
    
    return Positioned(
      top: 0,
      left: 0,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // System keyboard floating button - always visible at the top left
            Container(
              margin: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppTheme.primaryColor,
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.2),
                    blurRadius: 5,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: _toggleKeyboard,
                  borderRadius: BorderRadius.circular(20),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.keyboard,
                          color: Colors.white,
                          size: 16,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Keyboard',
                          style: AppTheme.bodySmall.copyWith(
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            
            // Expanded keyboard view
            if (_isExpanded)
              Container(
                width: MediaQuery.of(context).size.width,
                margin: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Theme.of(context).cardColor,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: [
                    BoxShadow(
                      color: Color.fromRGBO(0, 0, 0, 0.1),
                      blurRadius: 10,
                      offset: const Offset(0, 5),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        children: [
                          Text(
                            'System Keyboard',
                            style: AppTheme.bodyLarge.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const Spacer(),
                          IconButton(
                            icon: const Icon(Icons.close),
                            onPressed: _toggleKeyboard,
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 1),
                    // Text input field
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: TextField(
                        controller: _textController,
                        focusNode: _focusNode,
                        maxLines: 3,
                        decoration: InputDecoration(
                          hintText: 'Type here...',
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          contentPadding: const EdgeInsets.all(12),
                        ),
                      ),
                    ),
                    // Custom keyboard
                    // SystemKeyboard(
                    //   controller: _textController,
                    //   focusNode: _focusNode,
                    //   showSuggestions: true,
                    // ),
                  ],
                ),
              ),
            
            // Paraphraser button at the bottom
            if (_isExpanded) 
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ElevatedButton.icon(
                      onPressed: _paraphraseText,
                      icon: const Icon(Icons.autorenew),
                      label: const Text('Paraphrase Text'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: _openParaphraserPage,
                      child: const Text('More Options'),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
