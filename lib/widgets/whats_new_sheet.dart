import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../screens/keyboard_quick_settings_screen.dart';

/// Data class representing a single "What's New" feature entry.
class _WhatsNewItem {
  final IconData icon;
  final Color color;
  final String title;
  final String description;
  final void Function(BuildContext)? onTryIt;

  const _WhatsNewItem({
    required this.icon,
    required this.color,
    required this.title,
    required this.description,
    this.onTryIt,
  });
}

/// Bottom sheet that shows new features after an app update.
///
/// Call [WhatsNewSheet.showIfNeeded] from your home screen's initState
/// (via addPostFrameCallback) to automatically show this once per version.
class WhatsNewSheet {
  static const String _prefKey = 'last_seen_whats_new_version';

  // ──────────────────────────────────────────────
  //  FEATURE LIST PER VERSION
  // ──────────────────────────────────────────────
  // Add a new entry for each release. Older entries are kept for reference
  // but only the *current* version's items are displayed.
  static final Map<String, List<_WhatsNewItem>> _features = {
    '3.0.1+121': [
      const _WhatsNewItem(
        icon: CupertinoIcons.sparkles,
        color: Color(0xFF10B981),
        title: 'Enhanced AI Reliability',
        description:
            'Fixed model response limits, resolved 404 errors, and improved AI generation consistency across keyboard and accessibility features.',
      ),
    ],
    '3.0.0+120': [
      _WhatsNewItem(
        icon: CupertinoIcons.app_badge,
        color: const Color(0xFF10B981),
        title: 'Rounded Keyboard Smartbar',
        description:
            'A fresh new look! You can now apply an experimental rounded design to the keyboard smartbar.',
        onTryIt: (context) {
          Navigator.of(context).pop();
          Navigator.push(context, MaterialPageRoute(builder: (_) => const KeyboardQuickSettingsScreen()));
        },
      ),
      _WhatsNewItem(
        icon: CupertinoIcons.arrow_up_down_square,
        color: const Color(0xFF3B82F6),
        title: 'Custom Keyboard Sizing',
        description:
            'Adjust keyboard height and width for both portrait and landscape modes to fit your hands perfectly.',
        onTryIt: (context) {
          Navigator.of(context).pop();
          Navigator.push(context, MaterialPageRoute(builder: (_) => const KeyboardQuickSettingsScreen()));
        },
      ),
      _WhatsNewItem(
        icon: CupertinoIcons.scribble,
        color: const Color(0xFFF59E0B),
        title: 'Glide Typing Adjustments',
        description:
            'You can now control the width and appearance of the glide typing trail.',
        onTryIt: (context) {
          Navigator.of(context).pop();
          Navigator.push(context, MaterialPageRoute(builder: (_) => const KeyboardQuickSettingsScreen()));
        },
      ),
    ],
    '2.9.6+80': [
      const _WhatsNewItem(
        icon: CupertinoIcons.sparkles,
        color: Color(0xFF10B981),
        title: 'Better AI Responses',
        description:
            'Enjoy significantly improved, more natural, and context-aware AI-generated responses.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.search_circle_fill,
        color: Color(0xFF8B5CF6),
        title: 'GIF & Sticker Search',
        description:
            'Easily find the perfect reaction with our new search UI for the GIF and Sticker panels. (Beta)',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.smiley_fill,
        color: Color(0xFFF59E0B),
        title: 'New Android Emojis',
        description:
            'Express yourself better with full support for the latest Android emojis.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.square_fill_on_square_fill,
        color: Color(0xFF3B82F6),
        title: 'Massive Sticker Library',
        description:
            'Explore a large sticker community powered by Klipy. (Beta)',
      ),
    ],
    '2.9.6+80': [
      const _WhatsNewItem(
        icon: CupertinoIcons.search_circle_fill,
        color: Color(0xFF8B5CF6),
        title: 'Modernized Emoji Search',
        description:
            'Completely overhauled the emoji search panel with fluid, Gboard-style morphing animations and row-first filling.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.sparkles,
        color: Color(0xFF10B981),
        title: 'Seamless UI Transitions',
        description:
            'Fixed an issue where the keyboard window would jump or tear at the bottom when opening or closing the emoji panel. The keyboard now glides into place seamlessly.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.paintbrush_fill,
        color: Color(0xFFF59E0B),
        title: 'Harmonized Themes',
        description:
            'Emoji text and search pill colors now automatically adapt to maintain perfect readability across all day and night themes.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.keyboard,
        color: Color(0xFFEF4444),
        title: 'Keyboard Upgrades',
        description:
            'Improved keyboard performance, smarter emoji search, contacts as suggestions, enhanced glide typing, and new emoticons support.',
      ),
    ],
    '2.9.1+75': [
      const _WhatsNewItem(
        icon: CupertinoIcons.bolt_fill,
        color: Color(0xFF22C55E),
        title: 'AI Preferences Shortcut Fixed',
        description:
            'App shortcut routing now opens Advanced AI Settings without restarting the app.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.clock_fill,
        color: Color(0xFF3B82F6),
        title: 'Pro Days Remaining',
        description:
            'Settings now shows the days remaining on your Pro subscription.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.tag_fill,
        color: Color(0xFFF59E0B),
        title: 'Discount Pricing Details',
        description:
            'Plan cards show the original price alongside discounted offers.',
      ),
    ],
    '2.9.0+74': [
      const _WhatsNewItem(
        icon: CupertinoIcons.checkmark_seal_fill,
        color: Color(0xFF10B981),
        title: 'Keyboard Suggestions Added',
        description:
            'The keyboard now provides smarter in-flow suggestions to help you write faster with less back-and-forth.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.keyboard_chevron_compact_down,
        color: Color(0xFF3B82F6),
        title: 'Better Keyboard UX',
        description:
            'Refined keyboard interactions, smoother panel behavior, and cleaner actions for a more reliable typing experience.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.chat_bubble_2,
        color: Color(0xFFF59E0B),
        title: 'Context-Aware AI Panel',
        description:
            'AI panel now supports context-aware mode to preserve your intent, tone, and conversation flow while improving text.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.pencil_outline,
        color: Color(0xFF8B5CF6),
        title: 'Rewrite Prompt Enhancements',
        description:
            'Rewrite prompts are now more structured and precise, resulting in cleaner outputs that better match requested style and intent.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.arrow_down_circle,
        color: Color(0xFFEF4444),
        title: 'AI Append Enhancements',
        description:
            'Append mode now creates stronger continuations with improved context carry-over, tone consistency, and fewer repetitive outputs.',
      ),
    ],
    '2.1.6': [
      const _WhatsNewItem(
        icon: CupertinoIcons.moon_stars_fill,
        color: Color(0xFF6366F1),
        title: 'Perfect Dark Mode',
        description:
            'Fixed icon visibility issues in dark mode for AI settings and accessibility features. Now every element looks crisp and clear.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.person_badge_minus,
        color: Color(0xFFEF4444),
        title: 'Smooth Sign Out Experience',
        description:
            'Added loading indicator when signing out and improved navigation flow to ensure you\'re always taken to the right screen.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.heart_circle_fill,
        color: Color(0xFFF59E0B),
        title: 'Native App Rating',
        description:
            'Rate Us now uses Google Play\'s built-in review system for a seamless experience. We\'ll ask for your feedback after just 2 writing generations.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.lock_shield_fill,
        color: Color(0xFF10B981),
        title: 'Enhanced Security',
        description:
            'All AI features now require login for better security and personalization. This applies to both the main app and keyboard writing assistant.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.arrow_right_circle_fill,
        color: Color(0xFF06B6D4),
        title: 'Easy App Navigation',
        description:
            'Replaced the launch shortcut with a dedicated Go Back action in keyboard settings for smoother in-app navigation.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.keyboard_chevron_compact_down,
        color: Color(0xFF22C55E),
        title: 'Better Predictive Typing Base',
        description:
            'Started production integration of FlorisBoard-based autocorrect, suggestions, and glide typing improvements for more accurate typing.',
      ),
    ],
    '2.1.5': [
      const _WhatsNewItem(
        icon: CupertinoIcons.paintbrush_fill,
        color: Color(0xFF4F46E5),
        title: 'Vibrant New Color Scheme',
        description:
            'New Expressive UI/UX and vibrant color palette across the app for a fresher, more engaging experience.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.person_crop_circle,
        color: Color(0xFFFF6B6B),
        title: 'Keyboard Is Now Better Than Ever',
        description:
            'Wallpaper-based automatic color extraction for keyboard themes, plus improved key shapes and haptics for a more tactile typing experience.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.doc_plaintext,
        color: Color(0xFF06B6D4),
        title: 'Now for Documents Too',
        description:
            'Introducing Document Paraphrasing, Summarization, Grammar, Translation, and AI Detection. We are aware of the PDF and other file-type export issues — they will be fixed soon. This feature is not fully polished yet and we are not charging for it (free for all).',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.doc_on_clipboard,
        color: Color(0xFF10B981),
        title: 'Better Prompting',
        description:
            'All AI services now have improved prompting capabilities.',
      ),
      const _WhatsNewItem(
        icon: CupertinoIcons.sparkles,
        color: Color(0xFFF59E0B),
        title: 'Polished Grammar & Paraphraser',
        description:
            'Refined UI/UX for grammar checking and paraphrasing, with better visual feedback.',
      ),
    ],
  };

  /// Show the What's New sheet if the user hasn't seen the current version.
  static Future<void> showIfNeeded(BuildContext context) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final info = await PackageInfo.fromPlatform();
      final buildVersion = '${info.version}+${info.buildNumber}';
      final currentVersion =
          _features.containsKey(buildVersion) ? buildVersion : info.version;
      final lastSeen = prefs.getString(_prefKey);

      if (lastSeen == currentVersion) return;
      // Only show if we have content for this version
      if (!_features.containsKey(currentVersion)) return;

      await prefs.setString(_prefKey, currentVersion);

      if (!context.mounted) return;
      showModalBottomSheet(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => _WhatsNewBottomSheet(
          version: currentVersion,
          items: _features[currentVersion]!,
        ),
      );
    } catch (e) {
      debugPrint('WhatsNewSheet.showIfNeeded error: $e');
    }
  }

  /// Always show the What's New sheet (e.g. from Settings).
  static Future<void> show(BuildContext context) async {
    try {
      final info = await PackageInfo.fromPlatform();
      final buildVersion = '${info.version}+${info.buildNumber}';
      // Find current build/version entry, otherwise fall back to latest declared entry.
      final version = _features.containsKey(buildVersion)
          ? buildVersion
          : (_features.containsKey(info.version)
              ? info.version
              : _features.keys.first);
      final items = _features[version]!;

      if (!context.mounted) return;
      showModalBottomSheet(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => _WhatsNewBottomSheet(
          version: version,
          items: items,
        ),
      );
    } catch (e) {
      debugPrint('WhatsNewSheet.show error: $e');
    }
  }
}

class _WhatsNewBottomSheet extends StatelessWidget {
  final String version;
  final List<_WhatsNewItem> items;

  const _WhatsNewBottomSheet({
    required this.version,
    required this.items,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.75,
      ),
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Handle
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Container(
              width: 36,
              height: 5,
              decoration: BoxDecoration(
                color: cs.onSurfaceVariant.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          ),
          const SizedBox(height: 20),
          // Version badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: cs.primary.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              'v$version',
              style: Theme.of(context).textTheme.labelMedium!.copyWith(
                    color: cs.primary,
                    fontWeight: FontWeight.w700,
                  ),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            "What's New",
            style: Theme.of(context).textTheme.titleLarge!.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            'Here\'s what we\'ve been working on',
            style: Theme.of(context).textTheme.bodySmall!.copyWith(
                  color: cs.onSurfaceVariant,
                ),
          ),
          const SizedBox(height: 20),
          // Feature list
          Flexible(
            child: ListView.separated(
              shrinkWrap: true,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              itemCount: items.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                final item = items[index];
                return _buildFeatureCard(context, item);
              },
            ),
          ),
          const SizedBox(height: 20),
          // Got it button
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
            child: SizedBox(
              width: double.infinity,
              height: 48,
              child: FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                style: FilledButton.styleFrom(
                  backgroundColor: cs.primary,
                  foregroundColor: cs.onPrimary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                child: const Text(
                  'Got it',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFeatureCard(BuildContext context, _WhatsNewItem item) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cs.surfaceContainer,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: item.color.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(item.icon, color: item.color, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.title,
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  item.description,
                  style: Theme.of(context).textTheme.bodySmall!.copyWith(
                        color: cs.onSurfaceVariant,
                      ),
                ),
                if (item.onTryIt != null) ...[
                  const SizedBox(height: 10),
                  SizedBox(
                    height: 32,
                    child: OutlinedButton(
                      onPressed: () => item.onTryIt!(context),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        side: BorderSide(color: cs.primary),
                        foregroundColor: cs.primary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                      child: const Text('Try it', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
