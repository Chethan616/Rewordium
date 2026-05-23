import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'rewordium_toast.dart';

/// Full-screen text editor pushed when the user taps an input field on a tool
/// screen (paraphraser, grammar, translator, etc.).
///
/// Why this exists: the inline tool inputs share vertical space with mode
/// pickers, persona chips, and a result pane. On small phones you end up
/// writing inside a ~120pt slot above an open keyboard with no breathing
/// room. Lifting the editor into its own screen gives the user the full
/// viewport (modulo the IME), a quiet chrome, and a one-tap commit back to
/// the tool screen. Same affordance as Notes / iA Writer's full-screen mode.
///
/// Usage:
/// ```dart
/// final updated = await FocusedEditor.open(
///   context,
///   initialValue: _controller.text,
///   title: 'Paraphrase',
///   hint: 'Paste or type text to paraphrase…',
/// );
/// if (updated != null) _controller.text = updated;
/// ```
class FocusedEditor {
  FocusedEditor._();

  static Future<String?> open(
    BuildContext context, {
    required String initialValue,
    required String title,
    String hint = 'Type here…',
  }) {
    return Navigator.of(context).push<String>(
      PageRouteBuilder(
        opaque: true,
        // Fade + slight slide — feels like the editor lifts into focus
        // rather than the brutal default page push.
        transitionDuration: const Duration(milliseconds: 240),
        reverseTransitionDuration: const Duration(milliseconds: 200),
        pageBuilder: (_, __, ___) => _FocusedEditorScreen(
          initialValue: initialValue,
          title: title,
          hint: hint,
        ),
        transitionsBuilder: (_, animation, __, child) {
          final curved = CurvedAnimation(
            parent: animation,
            curve: Curves.easeOutCubic,
            reverseCurve: Curves.easeInCubic,
          );
          return FadeTransition(
            opacity: curved,
            child: SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0, 0.04),
                end: Offset.zero,
              ).animate(curved),
              child: child,
            ),
          );
        },
      ),
    );
  }
}

class _FocusedEditorScreen extends StatefulWidget {
  final String initialValue;
  final String title;
  final String hint;

  const _FocusedEditorScreen({
    required this.initialValue,
    required this.title,
    required this.hint,
  });

  @override
  State<_FocusedEditorScreen> createState() => _FocusedEditorScreenState();
}

class _FocusedEditorScreenState extends State<_FocusedEditorScreen> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialValue);
    _focusNode = FocusNode();
    // Defer focus so the transition can settle — focusing during the slide-in
    // makes the IME race the animation and the surface tears for one frame.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  int get _wordCount {
    final t = _controller.text.trim();
    if (t.isEmpty) return 0;
    return t.split(RegExp(r'\s+')).length;
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (text == null || text.isEmpty) return;
    final selection = _controller.selection;
    final start = selection.isValid ? selection.start : _controller.text.length;
    final end = selection.isValid ? selection.end : _controller.text.length;
    final newText = _controller.text.replaceRange(start, end, text);
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: start + text.length),
    );
    setState(() {});
  }

  void _clear() {
    _controller.clear();
    setState(() {});
  }

  void _done() {
    HapticFeedback.selectionClick();
    Navigator.of(context).pop(_controller.text);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      // The editor itself owns the bottom inset — we let the IME push the
      // toolbar instead of leaving an empty band.
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        backgroundColor: cs.surface,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: _RoundChevron(
          onTap: () => Navigator.of(context).pop(),
          icon: CupertinoIcons.chevron_back,
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
            ),
            const SizedBox(height: 2),
            AnimatedBuilder(
              animation: _controller,
              builder: (_, __) => Text(
                '$_wordCount ${_wordCount == 1 ? "word" : "words"}',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: cs.onSurfaceVariant,
                    ),
              ),
            ),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: GestureDetector(
                onTap: _done,
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                  decoration: BoxDecoration(
                    color: cs.primary,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    'Done',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: cs.onPrimary,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
          child: TextField(
            controller: _controller,
            focusNode: _focusNode,
            maxLines: null,
            expands: true,
            textAlignVertical: TextAlignVertical.top,
            keyboardType: TextInputType.multiline,
            textCapitalization: TextCapitalization.sentences,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  height: 1.55,
                  fontSize: 16.5,
                ),
            decoration: InputDecoration(
              hintText: widget.hint,
              hintStyle: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color: cs.onSurfaceVariant.withValues(alpha: 0.6),
                    fontSize: 16.5,
                  ),
              border: InputBorder.none,
              contentPadding: EdgeInsets.zero,
            ),
          ),
        ),
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        minimum: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          children: [
            _ToolbarButton(
              icon: CupertinoIcons.doc_on_clipboard,
              label: 'Paste',
              onTap: _paste,
            ),
            const SizedBox(width: 8),
            _ToolbarButton(
              icon: CupertinoIcons.delete,
              label: 'Clear',
              onTap: _controller.text.isEmpty
                  ? null
                  : () {
                      HapticFeedback.selectionClick();
                      _clear();
                    },
            ),
            const Spacer(),
            _ToolbarButton(
              icon: CupertinoIcons.checkmark_alt,
              label: 'Done',
              onTap: _done,
              emphasized: true,
            ),
          ],
        ),
      ),
    );
  }
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final bool emphasized;

  const _ToolbarButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.emphasized = false,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final enabled = onTap != null;
    final bg = emphasized
        ? cs.primary
        : cs.surfaceContainerHigh;
    final fg = emphasized
        ? cs.onPrimary
        : (enabled ? cs.onSurface : cs.onSurfaceVariant);

    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(12),
            border: emphasized
                ? null
                : Border.all(
                    color: cs.outlineVariant.withValues(alpha: 0.5),
                    width: 0.5,
                  ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 16, color: fg),
              const SizedBox(width: 8),
              Text(
                label,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: fg,
                      fontWeight: FontWeight.w500,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoundChevron extends StatefulWidget {
  final VoidCallback onTap;
  final IconData icon;
  const _RoundChevron({required this.onTap, required this.icon});

  @override
  State<_RoundChevron> createState() => _RoundChevronState();
}

class _RoundChevronState extends State<_RoundChevron> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(left: 8),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => setState(() => _pressed = true),
        onTapCancel: () => setState(() => _pressed = false),
        onTapUp: (_) => setState(() => _pressed = false),
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: _pressed
                ? cs.onSurface.withValues(alpha: 0.10)
                : cs.surfaceContainerHighest.withValues(alpha: 0.55),
            shape: BoxShape.circle,
          ),
          child: Icon(widget.icon, size: 18, color: cs.onSurface),
        ),
      ),
    );
  }
}

// Re-export the toast extension so call sites can flash a quick notice when
// committing edits, e.g. context.showToast('Updated', variant: .success).
typedef FocusedEditorToast = RewordiumToastContext;
