import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import '../theme/app_theme.dart';
import 'installer_verification_screen.dart';

/// Full-screen blocking page shown when Play Integrity check fails.
/// Uses the same design language as the rest of the app (AppTheme).
/// This screen cannot be dismissed or navigated away from.
class IntegrityBlockedScreen extends StatelessWidget {
  final String reason;

  const IntegrityBlockedScreen({
    super.key,
    this.reason = 'This copy was not installed from Google Play Store.',
  });

  static const String _playStoreUrl =
      'https://play.google.com/store/apps/details?id=com.noxquill.rewordium';

  Future<void> _openPlayStore() async {
    final uri = Uri.parse(_playStoreUrl);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: AppTheme.backgroundColor,
        body: SafeArea(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(
                children: [
                  const SizedBox(height: 60),

                  // Shield icon in a circle
                  Container(
                    width: 96,
                    height: 96,
                    decoration: BoxDecoration(
                      color: AppTheme.errorColor.withOpacity(0.1),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      CupertinoIcons.shield_slash,
                      size: 48,
                      color: AppTheme.errorColor,
                    ),
                  ),

                  const SizedBox(height: 28),

                  // Title
                  Text(
                    'Unauthorized Copy',
                    style: AppTheme.headingLarge.copyWith(
                      color: AppTheme.textPrimaryColor,
                    ),
                  ),

                  const SizedBox(height: 12),

                  // Subtitle
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      'This app was not installed from the Google Play Store and cannot be used.',
                      textAlign: TextAlign.center,
                      style: AppTheme.bodyMedium.copyWith(
                        color: AppTheme.textSecondaryColor,
                        height: 1.5,
                      ),
                    ),
                  ),

                  const SizedBox(height: 32),

                  // Info card (matching app's card style)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: AppTheme.cardDecoration,
                    child: Column(
                      children: [
                        _buildInfoRow(
                          CupertinoIcons.arrow_down_circle,
                          'Install from Play Store',
                          'Only official installs are allowed',
                          AppTheme.primaryColor,
                        ),
                        Divider(
                          height: 28,
                          color: AppTheme.textSecondaryColor.withOpacity(0.15),
                        ),
                        _buildInfoRow(
                          CupertinoIcons.shield_fill,
                          'Your data stays safe',
                          'This prevents tampered versions',
                          Colors.blue,
                        ),
                        Divider(
                          height: 28,
                          color: AppTheme.textSecondaryColor.withOpacity(0.15),
                        ),
                        _buildInfoRow(
                          CupertinoIcons.delete,
                          'Uninstall this copy',
                          'Then download from Play Store',
                          Colors.orange,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 32),

                  // Open Play Store button (primary - green)
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: ElevatedButton.icon(
                      onPressed: _openPlayStore,
                      icon: const Icon(CupertinoIcons.play_rectangle_fill, size: 20),
                      label: const Text(
                        'Open Play Store',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryColor,
                        foregroundColor: Colors.white,
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 12),

                  // Retry Verification button
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Navigator.of(context).pushReplacement(
                          MaterialPageRoute(
                            builder: (_) => const InstallerVerificationScreen(),
                          ),
                        );
                      },
                      icon: Icon(CupertinoIcons.arrow_counterclockwise, size: 16, color: AppTheme.primaryColor),
                      label: Text(
                        'Retry Verification',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.primaryColor,
                        ),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          color: AppTheme.primaryColor.withOpacity(0.3),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 12),

                  // Close App button (secondary - outlined)
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: OutlinedButton.icon(
                      onPressed: () {
                        SystemNavigator.pop();
                      },
                      icon: Icon(CupertinoIcons.xmark, size: 16, color: AppTheme.textSecondaryColor),
                      label: Text(
                        'Close App',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textSecondaryColor,
                        ),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          color: AppTheme.textSecondaryColor.withOpacity(0.3),
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 40),

                  // Small footer
                  Text(
                    'Rewordium Security',
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.textSecondaryColor.withOpacity(0.5),
                      letterSpacing: 1.0,
                      fontSize: 11,
                    ),
                  ),

                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoRow(
    IconData icon,
    String title,
    String subtitle,
    Color iconColor,
  ) {
    return Row(
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: iconColor.withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: iconColor, size: 20),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: AppTheme.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: AppTheme.bodySmall.copyWith(
                  color: AppTheme.textSecondaryColor,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
