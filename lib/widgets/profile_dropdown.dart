import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:animate_do/animate_do.dart';
import 'package:url_launcher/url_launcher.dart';

import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/custom_button.dart';

class ProfileDropdown extends StatelessWidget {
  const ProfileDropdown({super.key});

  // Launch web portal for payment/subscription management
  Future<void> _launchWebPortal() async {
    const url =
        'https://www.rewordium.tech/payments'; // Rewordium payments portal
    if (await canLaunchUrl(Uri.parse(url))) {
      await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
    } else {
      throw 'Could not launch $url';
    }
  }

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
          color: AppTheme.cardColor,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.1),
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
                    color: AppTheme.primaryColor.withOpacity(0.1),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    Icons.person,
                    color: AppTheme.primaryColor,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        userName,
                        style: AppTheme.bodyMedium.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        isPro ? 'Pro User' : 'Normal User',
                        style: AppTheme.bodySmall.copyWith(
                          color: isPro
                              ? Colors.green
                              : AppTheme.textSecondaryColor,
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
            // Upgrade button for non-pro users
            // Inside the build method in profile_dropdown.dart
            if (!isPro)
              CustomButton(
                text: 'Upgrade to Pro',
                onPressed: () {
                  Navigator.pop(context); // Close the dropdown first
                  _launchWebPortal();
                },
                type: ButtonType.primary,
                width: double.infinity,
              ),
            const SizedBox(height: 8),
            // Sign out button
            CustomButton(
              text: 'Sign Out',
              onPressed: () {
                authProvider.signOut();
                Navigator.pop(context);
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
