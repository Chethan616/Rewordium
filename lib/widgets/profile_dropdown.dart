import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:animate_do/animate_do.dart';

import '../providers/auth_provider.dart';
import '../widgets/custom_button.dart';
import '../widgets/upgrade_dialog.dart';
import '../screens/auth/login_screen.dart';

class ProfileDropdown extends StatelessWidget {
  const ProfileDropdown({super.key});

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    final userName = authProvider.userName ?? 'User';
    final isPro = authProvider.isPro;

    return FadeInDown(
      duration: const Duration(milliseconds: 200),
      child: Container(
        width: 200,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.1),
              blurRadius: 10,
              offset: const Offset(0, 5),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // User info
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    Icons.person,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        userName,
                        style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        isPro ? 'Pro User' : 'Normal User',
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: isPro
                              ? Colors.green
                              : Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            // Upgrade button for non-pro users - uses Google Play Billing
            if (!isPro)
              CustomButton(
                text: 'Upgrade to Pro',
                onPressed: () {
                  Navigator.pop(context); // Close the dropdown first
                  showUpgradeDialog(context); // Show Google Play Billing dialog
                },
                type: ButtonType.primary,
                width: double.infinity,
              ),
            const SizedBox(height: 8),
            // Sign out button
            CustomButton(
              text: 'Sign Out',
              onPressed: () async {
                Navigator.pop(context); // Close the dropdown first
                await authProvider.signOut();
                if (context.mounted) {
                  // Navigate to login screen and clear all routes
                  Navigator.of(context).pushAndRemoveUntil(
                    MaterialPageRoute(builder: (context) => const LoginScreen()),
                    (route) => false,
                  );
                }
              },
              type: ButtonType.secondary,
              width: double.infinity,
            ),
          ],
        ),
      ),
    );
  }
}
