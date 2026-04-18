import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import '../services/text_diff_service.dart';

// ──────────────────────────────────────────────────
//  ANNOTATION MODELS
// ──────────────────────────────────────────────────

/// A grammar error annotation with position and correction info.
class GrammarAnnotation {
  final int start;
  final int end;
  final String original;
  final String correction;
  final String explanation;

  const GrammarAnnotation({
    required this.start,
    required this.end,
    required this.original,
    required this.correction,
    required this.explanation,
  });
}

/// An AI detection annotation for a sentence/span.
class AIDetectionAnnotation {
  final int start;
  final int end;
  final double aiScore;
  final String text;

  const AIDetectionAnnotation({
    required this.start,
    required this.end,
    required this.aiScore,
    required this.text,
  });
}

/// The type of annotation layer to display.
enum AnnotationMode { none, grammar, aiDetection, paraphraseDiff }

// ──────────────────────────────────────────────────
//  ANNOTATED TEXT WIDGET
// ──────────────────────────────────────────────────

/// Displays text with inline annotations — grammar error underlines,
/// AI detection highlights, or paraphrase diff view.
class AnnotatedTextWidget extends StatelessWidget {
  final String text;
  final AnnotationMode mode;
  final List<GrammarAnnotation> grammarAnnotations;
  final List<AIDetectionAnnotation> aiAnnotations;
  final List<DiffSegment> diffSegments;
  final void Function(GrammarAnnotation)? onGrammarTap;

  const AnnotatedTextWidget({
    super.key,
    required this.text,
    this.mode = AnnotationMode.none,
    this.grammarAnnotations = const [],
    this.aiAnnotations = const [],
    this.diffSegments = const [],
    this.onGrammarTap,
  });

  @override
  Widget build(BuildContext context) {
    switch (mode) {
      case AnnotationMode.grammar:
        return _buildGrammarView(context);
      case AnnotationMode.aiDetection:
        return _buildAIDetectionView(context);
      case AnnotationMode.paraphraseDiff:
        return _buildDiffView(context);
      case AnnotationMode.none:
        return _buildPlainView(context);
    }
  }

  // ── PLAIN VIEW ──────────────────────────────────

  Widget _buildPlainView(BuildContext context) {
    return SelectableText(
      text,
      style: _baseStyle(context),
    );
  }

  // ── GRAMMAR VIEW ────────────────────────────────

  Widget _buildGrammarView(BuildContext context) {
    if (grammarAnnotations.isEmpty) return _buildPlainView(context);

    // Sort annotations by start position
    final sorted = List<GrammarAnnotation>.from(grammarAnnotations)
      ..sort((a, b) => a.start.compareTo(b.start));

    final spans = <InlineSpan>[];
    int lastEnd = 0;

    for (final ann in sorted) {
      // Avoid overlapping / out-of-bounds
      if (ann.start < lastEnd || ann.start >= text.length) continue;
      final end = ann.end.clamp(ann.start, text.length);

      // Text before this annotation
      if (ann.start > lastEnd) {
        spans.add(TextSpan(
          text: text.substring(lastEnd, ann.start),
          style: _baseStyle(context),
        ));
      }

      // The annotated span with red wavy underline
      spans.add(WidgetSpan(
        child: GestureDetector(
          onTap: () => onGrammarTap?.call(ann),
          child: Container(
            decoration: const BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: Color(0xFFEF4444),
                  width: 2,
                  style: BorderStyle.solid,
                ),
              ),
            ),
            child: Text(
              text.substring(ann.start, end),
              style: _baseStyle(context).copyWith(
                backgroundColor: const Color(0x18EF4444),
              ),
            ),
          ),
        ),
      ));

      lastEnd = end;
    }

    // Remaining text after last annotation
    if (lastEnd < text.length) {
      spans.add(TextSpan(
        text: text.substring(lastEnd),
        style: _baseStyle(context),
      ));
    }

    return SelectableText.rich(
      TextSpan(children: spans),
    );
  }

  // ── AI DETECTION VIEW ───────────────────────────

  Widget _buildAIDetectionView(BuildContext context) {
    if (aiAnnotations.isEmpty) return _buildPlainView(context);

    final sorted = List<AIDetectionAnnotation>.from(aiAnnotations)
      ..sort((a, b) => a.start.compareTo(b.start));

    final spans = <TextSpan>[];
    int lastEnd = 0;

    for (final ann in sorted) {
      if (ann.start < lastEnd || ann.start >= text.length) continue;
      final end = ann.end.clamp(ann.start, text.length);

      // Text before
      if (ann.start > lastEnd) {
        spans.add(TextSpan(
          text: text.substring(lastEnd, ann.start),
          style: _baseStyle(context),
        ));
      }

      // Highlighted span — color intensity based on AI score
      final color = _aiScoreColor(ann.aiScore);
      spans.add(TextSpan(
        text: text.substring(ann.start, end),
        style: _baseStyle(context).copyWith(
          backgroundColor: color,
        ),
      ));

      lastEnd = end;
    }

    if (lastEnd < text.length) {
      spans.add(TextSpan(
        text: text.substring(lastEnd),
        style: _baseStyle(context),
      ));
    }

    return SelectableText.rich(
      TextSpan(children: spans),
    );
  }

  Color _aiScoreColor(double score) {
    if (score >= 0.8) return const Color(0x40EF4444); // High AI — red
    if (score >= 0.5) return const Color(0x40F59E0B); // Medium — amber
    return const Color(0x2022C55E); // Low — green tint
  }

  // ── DIFF VIEW (PARAPHRASE) ──────────────────────

  Widget _buildDiffView(BuildContext context) {
    if (diffSegments.isEmpty) return _buildPlainView(context);

    final spans = <TextSpan>[];

    for (final seg in diffSegments) {
      switch (seg.op) {
        case DiffOp.equal:
          spans.add(TextSpan(text: seg.text, style: _baseStyle(context)));
          break;
        case DiffOp.delete:
          spans.add(TextSpan(
            text: seg.text,
            style: _baseStyle(context).copyWith(
              decoration: TextDecoration.lineThrough,
              color: const Color(0xFFEF4444),
              decorationColor: const Color(0xFFEF4444),
              backgroundColor: const Color(0x14EF4444),
            ),
          ));
          break;
        case DiffOp.insert:
          spans.add(TextSpan(
            text: seg.text,
            style: _baseStyle(context).copyWith(
              color: const Color(0xFF16A34A),
              backgroundColor: const Color(0x1416A34A),
              fontWeight: FontWeight.w500,
            ),
          ));
          break;
      }
    }

    return SelectableText.rich(
      TextSpan(children: spans),
    );
  }

  TextStyle _baseStyle(BuildContext context) =>
      Theme.of(context).textTheme.bodyMedium!.copyWith(
            fontSize: 14,
            height: 1.7,
            color: Theme.of(context).colorScheme.onSurface,
          );
}

// ──────────────────────────────────────────────────
//  GRAMMAR CORRECTION POPUP
// ──────────────────────────────────────────────────

/// Shows a popup card with grammar correction details.
void showGrammarCorrectionPopup(
  BuildContext context,
  GrammarAnnotation annotation, {
  VoidCallback? onApply,
}) {
  showDialog(
    context: context,
    barrierColor: Colors.black26,
    builder: (ctx) => Align(
      alignment: Alignment.center,
      child: Material(
        color: Colors.transparent,
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 32),
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Theme.of(ctx).colorScheme.surface,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.12),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0x18EF4444),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      CupertinoIcons.exclamationmark_circle,
                      color: Color(0xFFEF4444),
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    'Grammar Issue',
                    style: Theme.of(ctx).textTheme.bodyLarge!.copyWith(
                          fontWeight: FontWeight.w700,
                          fontSize: 16,
                        ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Original
              Text('Original',
                  style: Theme.of(ctx).textTheme.bodySmall!.copyWith(
                        color: Theme.of(ctx).colorScheme.onSurfaceVariant,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      )),
              const SizedBox(height: 4),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0x0CEF4444),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0x30EF4444)),
                ),
                child: Text(
                  annotation.original,
                  style: Theme.of(ctx).textTheme.bodyMedium!.copyWith(
                        fontSize: 13,
                        decoration: TextDecoration.lineThrough,
                        color: const Color(0xFFEF4444),
                      ),
                ),
              ),
              const SizedBox(height: 10),

              // Correction
              Text('Correction',
                  style: Theme.of(ctx).textTheme.bodySmall!.copyWith(
                        color: Theme.of(ctx).colorScheme.onSurfaceVariant,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      )),
              const SizedBox(height: 4),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0x0C22C55E),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0x3022C55E)),
                ),
                child: Text(
                  annotation.correction,
                  style: Theme.of(ctx).textTheme.bodyMedium!.copyWith(
                        fontSize: 13,
                        color: const Color(0xFF16A34A),
                        fontWeight: FontWeight.w500,
                      ),
                ),
              ),

              // Explanation
              if (annotation.explanation.isNotEmpty) ...[
                const SizedBox(height: 10),
                Text('Why',
                    style: Theme.of(ctx).textTheme.bodySmall!.copyWith(
                          color: Theme.of(ctx).colorScheme.onSurfaceVariant,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        )),
                const SizedBox(height: 4),
                Text(
                  annotation.explanation,
                  style: Theme.of(ctx).textTheme.bodySmall!.copyWith(
                        fontSize: 12,
                        color: Theme.of(ctx).colorScheme.onSurfaceVariant,
                        height: 1.4,
                      ),
                ),
              ],

              const SizedBox(height: 16),
              // Actions
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(ctx),
                    child: Text('Dismiss',
                        style: TextStyle(
                          color: Theme.of(ctx).colorScheme.onSurfaceVariant,
                          fontSize: 13,
                        )),
                  ),
                  if (onApply != null) ...[
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () {
                        Navigator.pop(ctx);
                        onApply();
                      },
                      style: TextButton.styleFrom(
                        backgroundColor: Theme.of(ctx).colorScheme.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 8),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: const Text('Apply Fix',
                          style: TextStyle(fontSize: 13)),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
