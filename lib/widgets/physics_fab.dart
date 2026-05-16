import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/physics.dart';

import '../screens/paraphraser_page.dart';
import '../screens/grammar_page.dart';
import '../screens/ai_detector_page.dart';
import '../screens/translator_page.dart';
import '../screens/summarizer_page.dart';
import '../screens/tone_editor_page.dart';
import '../screens/jade_chat_screen.dart';
import '../screens/document_viewer_screen.dart';
import '../services/document_service.dart';
import '../utils/doc_gate.dart';

// ─── FAB entry point ────────────────────────────────────────────────────────

class PhysicsFab extends StatefulWidget {
  final ValueChanged<int>? onSelectHomeTab;
  final Widget closedIcon;
  final String tooltip;

  const PhysicsFab({
    super.key,
    this.onSelectHomeTab,
    this.closedIcon = const Icon(CupertinoIcons.square_grid_2x2),
    this.tooltip = 'Tools',
  });

  @override
  State<PhysicsFab> createState() => _PhysicsFabState();
}

class _PhysicsFabState extends State<PhysicsFab>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _rotateAnim;
  bool _open = false;

  final OverlayPortalController _overlayController = OverlayPortalController();
  final LayerLink _layerLink = LayerLink();

  // Capture the home-tree BuildContext so overlay callbacks can navigate correctly.
  // This is set/refreshed inside build() which always runs in the correct tree.
  late BuildContext _homeContext;

  static const _openSpring  = SpringDescription(mass: 1, stiffness: 180, damping: 14);
  static const _closeSpring = SpringDescription(mass: 1, stiffness: 380, damping: 26);

  // FAB is 56dp tall; first child sits just above it, then stacked upward.
  static const double _fabHeight   = 56.0;
  static const double _gapAboveFab = 10.0;
  static const double _itemSpacing = 52.0;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _controller.addStatusListener((status) {
      if (status == AnimationStatus.dismissed && mounted) {
        _overlayController.hide();
      }
    });
    _rotateAnim = Tween<double>(begin: 0, end: math.pi / 4).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOutCubic),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggle() {
    if (_open) {
      _controller.animateWith(
          SpringSimulation(_closeSpring, _controller.value, 0, -4));
    } else {
      _overlayController.show();
      _controller.animateWith(
          SpringSimulation(_openSpring, _controller.value, 1, 4));
    }
    setState(() => _open = !_open);
  }

  void _close() {
    if (_open) _toggle();
  }

  /// Close the overlay, then run [action] in the home-tree context.
  void _closeAndRun(VoidCallback action) {
    if (_open) {
      setState(() => _open = false);
      _controller.animateWith(
          SpringSimulation(_closeSpring, _controller.value, 0, -4));
    }
    // Use a post-frame callback so the overlay finishes collapsing before
    // navigation occurs, preventing jank or hero-animation conflicts.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      action();
    });
  }

  // ─── Navigation ────────────────────────────────────────────────────────────
  // Always uses _homeContext (set in build()) which has the correct Navigator.

  void _navigateToTool(String toolName) {
    final ctx = _homeContext;
    if (!ctx.mounted) return;
    final navigator = Navigator.of(ctx, rootNavigator: true);

    switch (toolName.toLowerCase()) {
      case 'ai detector':
        navigator.push(MaterialPageRoute(builder: (_) => const AIDetectorPage()));
        break;
      case 'translator':
        navigator.push(MaterialPageRoute(builder: (_) => const TranslatorPage()));
        break;
      case 'paraphraser':
        if (widget.onSelectHomeTab != null) {
          widget.onSelectHomeTab!(1);
        } else {
          navigator.push(MaterialPageRoute(builder: (_) => const ParaphraserPage()));
        }
        break;
      case 'grammar':
        if (widget.onSelectHomeTab != null) {
          widget.onSelectHomeTab!(2);
        } else {
          navigator.push(MaterialPageRoute(builder: (_) => const GrammarPage()));
        }
        break;
      case 'summarizer':
        navigator.push(MaterialPageRoute(builder: (_) => const SummarizerPage()));
        break;
      case 'tone editor':
        navigator.push(MaterialPageRoute(builder: (_) => const ToneEditorPage()));
        break;
      case 'jade ai':
        navigator.push(MaterialPageRoute(builder: (_) => const JadeChatScreen()));
        break;
      case 'scan document':
        _scanDocument(ctx, navigator);
        break;
      case 'import file':
        _importFile(ctx, navigator);
        break;
    }
  }

  void _scanDocument(BuildContext ctx, NavigatorState navigator) async {
    if (!await DocGate.check(ctx)) return;
    try {
      final result = await DocumentService.scanDocument();
      if (result != null && result.text.isNotEmpty && ctx.mounted) {
        navigator.push(
          MaterialPageRoute(
            builder: (_) => DocumentViewerScreen(
              document: result,
              onUseText: (text) {},
            ),
          ),
        );
      }
    } catch (e) {
      debugPrint('Scan error: $e');
    }
  }

  void _importFile(BuildContext ctx, NavigatorState navigator) async {
    if (!await DocGate.check(ctx)) return;
    try {
      final result = await DocumentService.pickFile();
      if (result != null && result.text.isNotEmpty && ctx.mounted) {
        navigator.push(
          MaterialPageRoute(
            builder: (_) => DocumentViewerScreen(
              document: result,
              onUseText: (text) {},
            ),
          ),
        );
      }
    } catch (e) {
      debugPrint('Import error: $e');
    }
  }

  // ─── Action list ───────────────────────────────────────────────────────────

  List<_FabActionData> _buildActions(ColorScheme cs, bool isDark) {
    return [
      _FabActionData(
        label: 'Jade AI',
        icon: CupertinoIcons.chat_bubble_2_fill,
        color: isDark ? cs.tertiaryContainer : const Color(0xFFEDE9FE),
        onColor: isDark ? cs.onTertiaryContainer : const Color(0xFF4C1D95),
        toolName: 'jade ai',
      ),
      _FabActionData(
        label: 'AI Detector',
        icon: CupertinoIcons.sparkles,
        color: cs.primaryContainer,
        onColor: cs.onPrimaryContainer,
        toolName: 'ai detector',
      ),
      _FabActionData(
        label: 'Translator',
        icon: CupertinoIcons.globe,
        color: cs.secondaryContainer,
        onColor: cs.onSecondaryContainer,
        toolName: 'translator',
      ),
      _FabActionData(
        label: 'Paraphraser',
        icon: CupertinoIcons.text_badge_checkmark,
        color: cs.tertiaryContainer,
        onColor: cs.onTertiaryContainer,
        toolName: 'paraphraser',
      ),
      _FabActionData(
        label: 'Grammar',
        icon: CupertinoIcons.checkmark_seal_fill,
        color: cs.errorContainer,
        onColor: cs.onErrorContainer,
        toolName: 'grammar',
      ),
      _FabActionData(
        label: 'Summarizer',
        icon: CupertinoIcons.doc_text_search,
        color: isDark ? cs.primaryContainer : const Color(0xFFFEF3C7),
        onColor: isDark ? cs.onPrimaryContainer : const Color(0xFF92400E),
        toolName: 'summarizer',
      ),
      _FabActionData(
        label: 'Tone Editor',
        icon: CupertinoIcons.waveform_path,
        color: isDark ? cs.secondaryContainer : const Color(0xFFCCFBF1),
        onColor: isDark ? cs.onSecondaryContainer : const Color(0xFF0F766E),
        toolName: 'tone editor',
      ),
      _FabActionData(
        label: 'Scan Document',
        icon: CupertinoIcons.camera_viewfinder,
        color: isDark ? cs.surfaceContainerHigh : const Color(0xFFDBEAFE),
        onColor: isDark ? cs.onSurface : const Color(0xFF1E3A8A),
        toolName: 'scan document',
      ),
      _FabActionData(
        label: 'Import File',
        icon: CupertinoIcons.doc_on_doc,
        color: isDark ? cs.inverseSurface : const Color(0xFFDCFCE7),
        onColor: isDark ? cs.onInverseSurface : const Color(0xFF14532D),
        toolName: 'import file',
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    // Always refresh _homeContext from the real widget-tree context.
    _homeContext = context;

    final cs = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final actions = _buildActions(cs, isDark);
    final n = actions.length;

    return CompositedTransformTarget(
      link: _layerLink,
      child: Stack(
        alignment: Alignment.center,
        clipBehavior: Clip.none,
        children: [
          OverlayPortal(
            controller: _overlayController,
            overlayChildBuilder: (_) {
              // NOTE: we intentionally do NOT use this overlay context for
              // navigation – we use _homeContext captured above.
              return Stack(
                children: [
                  // Dismiss barrier
                  Positioned.fill(
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: _close,
                      child: const ColoredBox(color: Colors.transparent),
                    ),
                  ),

                  // Action buttons anchored to FAB position
                  Positioned(
                    child: CompositedTransformFollower(
                      link: _layerLink,
                      targetAnchor: Alignment.bottomRight,
                      followerAnchor: Alignment.bottomRight,
                      child: SizedBox(
                        width: 0,
                        height: 0,
                        child: Stack(
                          alignment: Alignment.bottomRight,
                          clipBehavior: Clip.none,
                          children: List.generate(n, (i) {
                            final slot  = n - 1 - i;
                            final start = (slot * 0.10).clamp(0.0, 0.70);
                            final anim  = CurvedAnimation(
                              parent: _controller,
                              curve: Interval(start, 1.0, curve: Curves.elasticOut),
                            );
                            final bottomPos =
                                _fabHeight + _gapAboveFab + (n - 1 - i) * _itemSpacing;
                            final action = actions[i];

                            return Positioned(
                              bottom: bottomPos,
                              right: 5,
                              child: AnimatedBuilder(
                                animation: anim,
                                builder: (_, child) {
                                  final t = anim.value.clamp(0.0, 1.0);
                                  return IgnorePointer(
                                    ignoring: !_open,
                                    child: Opacity(
                                      opacity: t,
                                      child: Transform.translate(
                                        offset: Offset(0, 20 * (1 - t)),
                                        child: Transform.scale(
                                          scale: 0.72 + 0.28 * t,
                                          child: child,
                                        ),
                                      ),
                                    ),
                                  );
                                },
                                child: _ActionButton(
                                  label: action.label,
                                  icon: action.icon,
                                  color: action.color,
                                  onColor: action.onColor,
                                  onTap: () => _closeAndRun(
                                    () => _navigateToTool(action.toolName),
                                  ),
                                ),
                              ),
                            );
                          }),
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),

          // Main FAB
          FloatingActionButton(
            heroTag: 'physics_fab_main',
            tooltip: _open ? 'Close' : widget.tooltip,
            backgroundColor: cs.primaryContainer,
            foregroundColor: cs.onPrimaryContainer,
            elevation: 3,
            highlightElevation: 4,
            onPressed: _toggle,
            shape: const CircleBorder(),
            child: AnimatedBuilder(
              animation: _rotateAnim,
              builder: (_, __) => Transform.rotate(
                angle: _rotateAnim.value,
                child: _open
                    ? const Icon(CupertinoIcons.xmark, size: 20)
                    : widget.closedIcon,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Action button widget ─────────────────────────────────────────────────────

class _ActionButton extends StatefulWidget {
  final String label;
  final IconData icon;
  final Color color;
  final Color onColor;
  final VoidCallback onTap;

  const _ActionButton({
    required this.label,
    required this.icon,
    required this.color,
    required this.onColor,
    required this.onTap,
  });

  @override
  State<_ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<_ActionButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _press;

  @override
  void initState() {
    super.initState();
    _press = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 80),
      lowerBound: 0.92,
      upperBound: 1.0,
      value: 1.0,
    );
  }

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final tt = Theme.of(context).textTheme;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown:  (_) => _press.reverse(),
      onTap:       ()  { _press.forward(); widget.onTap(); },
      onTapCancel: ()  => _press.forward(),
      child: ScaleTransition(
        scale: _press,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Label pill
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: cs.surface,
                  borderRadius: BorderRadius.circular(22),
                  border: Border.all(color: cs.outlineVariant, width: 0.75),
                ),
                child: Text(
                  widget.label,
                  style: tt.labelLarge?.copyWith(
                    color: cs.onSurface,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              // Icon chip
              Material(
                color: widget.color,
                shape: const CircleBorder(),
                elevation: 2,
                child: SizedBox(
                  width: 46,
                  height: 46,
                  child: Icon(widget.icon, color: widget.onColor, size: 22),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Internal data class ──────────────────────────────────────────────────────

class _FabActionData {
  final String label;
  final IconData icon;
  final Color color;
  final Color onColor;
  final String toolName;

  const _FabActionData({
    required this.label,
    required this.icon,
    required this.color,
    required this.onColor,
    required this.toolName,
  });
}
