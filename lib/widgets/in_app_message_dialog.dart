import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:url_launcher/url_launcher.dart';

enum InAppMessageType { card, modal, banner }

/// Material 3 Expressive In-App Message Dialog / Banner Widget
class InAppMessageDialog extends StatelessWidget {
  final String title;
  final String body;
  final String? imageUrl;
  final IconData? icon;
  final String? buttonText;
  final String? actionUrl;
  final InAppMessageType type;
  final VoidCallback? onDismissed;

  const InAppMessageDialog({
    super.key,
    required this.title,
    required this.body,
    this.imageUrl,
    this.icon,
    this.buttonText,
    this.actionUrl,
    this.type = InAppMessageType.card,
    this.onDismissed,
  });

  static Future<void> showModal(
    BuildContext context, {
    required String title,
    required String body,
    String? imageUrl,
    IconData? icon,
    String? buttonText,
    String? actionUrl,
    InAppMessageType type = InAppMessageType.card,
  }) {
    return showDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: InAppMessageDialog(
          title: title,
          body: body,
          imageUrl: imageUrl,
          icon: icon,
          buttonText: buttonText,
          actionUrl: actionUrl,
          type: type,
          onDismissed: () => Navigator.of(ctx).pop(),
        ),
      ),
    );
  }

  static void showTopBanner(
    BuildContext context, {
    required String title,
    required String body,
    String? actionUrl,
    String? buttonText,
  }) {
    ScaffoldMessenger.of(context).showMaterialBanner(
      MaterialBanner(
        elevation: 6,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        leading: const Icon(CupertinoIcons.sparkles, color: Color(0xFF10B981)),
        content: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
            ),
            const SizedBox(height: 2),
            Text(
              body,
              style: const TextStyle(fontSize: 12),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              ScaffoldMessenger.of(context).hideCurrentMaterialBanner();
              if (actionUrl != null && actionUrl.isNotEmpty) {
                _launchUrl(actionUrl);
              }
            },
            child: Text(buttonText ?? 'View'),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 18),
            onPressed: () => ScaffoldMessenger.of(context).hideCurrentMaterialBanner(),
          ),
        ],
      ),
    );
  }

  static void _launchUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Container(
      constraints: const BoxConstraints(maxWidth: 400),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.18),
            blurRadius: 24,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header Image or Icon Banner
            if (imageUrl != null && imageUrl!.isNotEmpty)
              Image.network(
                imageUrl!,
                height: 160,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _buildIconHeader(cs),
              )
            else
              _buildIconHeader(cs),

            // Content
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    body,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: cs.onSurfaceVariant,
                          height: 1.4,
                        ),
                  ),
                  const SizedBox(height: 24),

                  // Actions
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton(
                        onPressed: onDismissed ?? () => Navigator.of(context).pop(),
                        child: const Text('Dismiss'),
                      ),
                      if (buttonText != null && buttonText!.isNotEmpty) ...[
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: () {
                            onDismissed?.call();
                            if (actionUrl != null && actionUrl!.isNotEmpty) {
                              _launchUrl(actionUrl!);
                            }
                          },
                          style: FilledButton.styleFrom(
                            backgroundColor: cs.primary,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: Text(buttonText!),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIconHeader(ColorScheme cs) {
    return Container(
      height: 100,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            cs.primaryContainer,
            cs.secondaryContainer,
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: Center(
        child: Icon(
          icon ?? CupertinoIcons.sparkles,
          size: 48,
          color: cs.primary,
        ),
      ),
    );
  }
}
