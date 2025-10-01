import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:vibration/vibration.dart';
import '../../providers/keyboard_provider.dart';
import '../../theme/app_theme.dart';

// Base class for all keyboard layouts
abstract class KeyboardBase extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode? focusNode;
  final Function(String)? onTextInput;
  final Function()? onBackspace;
  final bool showSuggestions;

  const KeyboardBase({
    Key? key,
    required this.controller,
    this.focusNode,
    this.onTextInput,
    this.onBackspace,
    this.showSuggestions = true,
  }) : super(key: key);

  // Common method to handle text input
  void textInputHandler(String text, BuildContext context) {
    final keyboardProvider = Provider.of<KeyboardProvider>(context, listen: false);
    
    // Vibrate if haptic feedback is enabled
    if (keyboardProvider.soundOn) {
      Vibration.hasVibrator().then((hasVibrator) {
        if (hasVibrator ?? false) {
          Vibration.vibrate(duration: 10, amplitude: 20);
        }
      });
    }
    
    if (onTextInput != null) {
      onTextInput!(text);
    } else {
      final currentText = controller.text;
      final textSelection = controller.selection;
      final newText = currentText.replaceRange(
        textSelection.start,
        textSelection.end,
        text,
      );
      
      final newSelection = TextSelection.collapsed(
        offset: textSelection.start + text.length,
      );
      
      controller.text = newText;
      controller.selection = newSelection;
      
      // Update suggestions based on current word
      final currentWord = _getCurrentWord(newText, newSelection.start);
      keyboardProvider.updateSuggestions(currentWord);
    }
  }

  // Common method to handle backspace
  void backspaceHandler(BuildContext context) {
    final keyboardProvider = Provider.of<KeyboardProvider>(context, listen: false);
    
    // Vibrate if haptic feedback is enabled
    if (keyboardProvider.soundOn) {
      Vibration.hasVibrator().then((hasVibrator) {
        if (hasVibrator ?? false) {
          Vibration.vibrate(duration: 10, amplitude: 20);
        }
      });
    }
    
    if (onBackspace != null) {
      onBackspace!();
    } else {
      final currentText = controller.text;
      final textSelection = controller.selection;
      final selectionStart = textSelection.start;
      final selectionEnd = textSelection.end;
      
      if (selectionStart == selectionEnd && selectionStart > 0) {
        // Delete the last character
        final newText = currentText.replaceRange(
          selectionStart - 1,
          selectionEnd,
          '',
        );
        
        controller.text = newText;
        controller.selection = TextSelection.collapsed(
          offset: selectionStart - 1,
        );
      } else if (selectionEnd > selectionStart) {
        // Delete the selected text
        final newText = currentText.replaceRange(
          selectionStart,
          selectionEnd,
          '',
        );
        
        controller.text = newText;
        controller.selection = TextSelection.collapsed(
          offset: selectionStart,
        );
      }
      
      // Update suggestions based on current word
      final newText = controller.text;
      final newSelection = controller.selection;
      final currentWord = _getCurrentWord(newText, newSelection.start);
      keyboardProvider.updateSuggestions(currentWord);
    }
  }

  // Helper method to get the current word being typed
  String _getCurrentWord(String text, int cursorPosition) {
    if (text.isEmpty || cursorPosition <= 0) return '';
    
    final beforeCursor = text.substring(0, cursorPosition);
    final lastSpaceIndex = beforeCursor.lastIndexOf(' ');
    
    if (lastSpaceIndex == -1) {
      return beforeCursor;
    } else {
      return beforeCursor.substring(lastSpaceIndex + 1);
    }
  }

  // Build suggestion bar
  Widget buildSuggestionBar(BuildContext context) {
    final keyboardProvider = Provider.of<KeyboardProvider>(context);
    final suggestions = keyboardProvider.suggestions;
    
    if (!showSuggestions || suggestions.isEmpty) {
      return const SizedBox(height: 0);
    }
    
    return Container(
      height: 40,
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        border: Border(
          bottom: BorderSide(
            color: Colors.grey.withOpacity(0.3),
            width: 1,
          ),
        ),
      ),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 8),
        itemCount: suggestions.length,
        separatorBuilder: (context, index) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          return InkWell(
            onTap: () {
              // Replace current word with suggestion
              final currentText = controller.text;
              final textSelection = controller.selection;
              final cursorPosition = textSelection.start;
              final beforeCursor = currentText.substring(0, cursorPosition);
              final afterCursor = currentText.substring(cursorPosition);
              
              final lastSpaceIndex = beforeCursor.lastIndexOf(' ');
              final prefix = lastSpaceIndex == -1 
                  ? '' 
                  : beforeCursor.substring(0, lastSpaceIndex + 1);
              
              final newText = prefix + suggestions[index] + afterCursor;
              controller.text = newText;
              controller.selection = TextSelection.collapsed(
                offset: prefix.length + suggestions[index].length,
              );
              
              keyboardProvider.clearSuggestions();
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              margin: const EdgeInsets.symmetric(vertical: 6),
              decoration: BoxDecoration(
                color: AppTheme.cardColor,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: Colors.grey.withOpacity(0.3),
                ),
              ),
              child: Text(
                suggestions[index],
                style: TextStyle(
                  color: AppTheme.textPrimaryColor,
                  fontSize: 14,
                ),
              ),
            ),
          );
        },
      ),
    );
  }
  
  // Build keyboard key
  Widget buildKey({
    required String text,
    required VoidCallback onTap,
    double width = 1.0,
    double height = 1.0,
    Color? backgroundColor,
    Color? textColor,
    IconData? icon,
    BorderRadius? borderRadius,
  });
  
  // Build keyboard row
  Widget buildRow({
    required List<Widget> children,
    double spacing = 6.0,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: children,
      ),
    );
  }
  
  // Build keyboard layout - to be implemented by subclasses
  Widget buildKeyboardLayout(BuildContext context);
  
  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (showSuggestions) buildSuggestionBar(context),
        buildKeyboardLayout(context),
      ],
    );
  }
}
