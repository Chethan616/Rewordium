import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import 'package:m3e_collection/m3e_collection.dart';

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
    final photoUrl = authProvider.user?.photoURL;
    final email = authProvider.user?.email;
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Handle bar
              Container(
                width: 36,
                height: 5,
                decoration: BoxDecoration(
                  color: colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              const SizedBox(height: 24),
              // Profile photo
              Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: colorScheme.primary,
                ),
                child: CircleAvatar(
                  radius: 40,
                  backgroundColor: colorScheme.surface,
                  backgroundImage: photoUrl != null ? NetworkImage(photoUrl) : null,
                  child: photoUrl == null
                      ? Icon(CupertinoIcons.person_fill, size: 36, color: colorScheme.primary)
                      : null,
                ),
              ),
              const SizedBox(height: 16),
              // Name
              Text(
                userName,
                style: Theme.of(context).textTheme.titleLarge!.copyWith(
                  fontWeight: FontWeight.bold,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
              ),
              if (email != null) ...[
                const SizedBox(height: 4),
                Text(
                  email,
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                ),
              ],
              const SizedBox(height: 12),
              // Pro badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                decoration: BoxDecoration(
                  color: isPro ? colorScheme.primary : colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  isPro ? '⭐ Pro Member' : 'Free Plan',
                  style: Theme.of(context).textTheme.labelMedium!.copyWith(
                    color: isPro ? Colors.white : colorScheme.onSurfaceVariant,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(height: 24),
              // Upgrade button for non-pro users
              if (!isPro) ...[
                CustomButton(
                  text: 'Upgrade to Pro',
                  onPressed: () {
                    Navigator.pop(context);
                    showUpgradeDialog(context);
                  },
                  type: ButtonType.primary,
                  width: double.infinity,
                ),
                const SizedBox(height: 12),
              ],
              // Sign out button
              CustomButton(
                text: 'Sign Out',
                onPressed: () {
                  Navigator.pop(context);
                  _showSignOutConfirmation(context, authProvider);
                },
                type: ButtonType.secondary,
                width: double.infinity,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showSignOutConfirmation(BuildContext context, AuthProvider authProvider) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        final colorScheme = Theme.of(ctx).colorScheme;
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 36, height: 5,
                  decoration: BoxDecoration(
                    color: colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
                const SizedBox(height: 20),
                Icon(Icons.logout_rounded, size: 40, color: colorScheme.error),
                const SizedBox(height: 12),
                Text('Sign Out?', style: Theme.of(ctx).textTheme.titleLarge),
                const SizedBox(height: 8),
                Text(
                  'You will need to sign in again to access your account.',
                  style: Theme.of(ctx).textTheme.bodyMedium!.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                Row(
                  children: [
                    Expanded(
                      child: CustomButton(
                        text: 'Cancel',
                        onPressed: () => Navigator.pop(ctx),
                        type: ButtonType.secondary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: CustomButton(
                        text: 'Sign Out',
                        onPressed: () async {
                          Navigator.pop(ctx);
                          await authProvider.signOut();
                          if (context.mounted) {
                            Navigator.of(context).pushAndRemoveUntil(
                              MaterialPageRoute(builder: (_) => const LoginScreen()),
                              (route) => false,
                            );
                          }
                        },
                        type: ButtonType.primary,
                        customColor: colorScheme.error,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
