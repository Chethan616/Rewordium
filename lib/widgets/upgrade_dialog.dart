import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';

class UpgradeDialog extends StatelessWidget {
  const UpgradeDialog({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthProvider>(
      builder: (context, authProvider, child) {
        return AlertDialog(
          title: const Text('Upgrade to Pro'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Unlock unlimited features with Rewordium Pro:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              const Text('• Unlimited AI rewrites'),
              const Text('• Advanced grammar checking'),
              const Text('• Premium templates'),
              const Text('• Priority support'),
              const Text('• No ads'),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.green.shade200),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Text(
                      'One-time payment: ',
                      style: TextStyle(fontSize: 16),
                    ),
                    Text(
                      '\$9.99',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Colors.green.shade700,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Maybe Later'),
            ),
            ElevatedButton(
              onPressed: () async {
                final upgradeUrl = authProvider.getUpgradeUrl();
                final uri = Uri.parse(upgradeUrl);

                if (await canLaunchUrl(uri)) {
                  await launchUrl(
                    uri,
                    mode: LaunchMode.externalApplication,
                  );
                  Navigator.of(context).pop();
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Could not open upgrade page'),
                    ),
                  );
                }
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                foregroundColor: Colors.white,
              ),
              child: const Text('Upgrade Now'),
            ),
          ],
        );
      },
    );
  }
}

// Helper function to show the upgrade dialog
void showUpgradeDialog(BuildContext context) {
  showDialog(
    context: context,
    builder: (context) => const UpgradeDialog(),
  );
}

// Example usage in your app
class ExampleUsage extends StatelessWidget {
  const ExampleUsage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthProvider>(
      builder: (context, authProvider, child) {
        return Scaffold(
          appBar: AppBar(
            title: const Text('Rewordium'),
            actions: [
              if (!authProvider.isPro)
                TextButton.icon(
                  onPressed: () => showUpgradeDialog(context),
                  icon: const Icon(Icons.star, color: Colors.amber),
                  label: const Text(
                    'Upgrade',
                    style: TextStyle(color: Colors.amber),
                  ),
                ),
            ],
          ),
          body: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (authProvider.isPro) ...[
                  const Icon(
                    Icons.star,
                    color: Colors.amber,
                    size: 48,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Pro User',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Colors.amber,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text('Unlimited access to all features'),
                ] else ...[
                  const Icon(
                    Icons.star_border,
                    size: 48,
                    color: Colors.grey,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Free User',
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  Text('${authProvider.credits ?? 0} credits remaining'),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: () => showUpgradeDialog(context),
                    icon: const Icon(Icons.star),
                    label: const Text('Upgrade to Pro'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
