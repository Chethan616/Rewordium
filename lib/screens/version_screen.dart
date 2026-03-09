import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../theme/app_theme.dart';

class VersionScreen extends StatelessWidget {
  const VersionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: Icon(CupertinoIcons.back, color: AppTheme.textPrimaryColor),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Panda image in a big rounded circle
            Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppTheme.primaryColor.withValues(alpha: 0.25),
                  width: 3,
                ),
                image: const DecorationImage(
                  image: AssetImage('assets/images/panda.png'),
                  fit: BoxFit.cover,
                ),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.primaryColor.withValues(alpha: 0.15),
                    blurRadius: 30,
                    spreadRadius: 6,
                  ),
                ],
              ),
            ),

            const SizedBox(height: 32),

            // Version codename
            Text(
              'Panda',
              style: TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.bold,
                color: AppTheme.textPrimaryColor,
                letterSpacing: 1.2,
              ),
            ),

            const SizedBox(height: 8),

            // Version number from package info
            FutureBuilder<PackageInfo>(
              future: PackageInfo.fromPlatform(),
              builder: (context, snapshot) {
                final version = snapshot.hasData
                    ? 'v${snapshot.data!.version} (${snapshot.data!.buildNumber})'
                    : '';

                return Text(
                  version,
                  style: TextStyle(
                    fontSize: 15,
                    color: AppTheme.textSecondaryColor,
                    fontWeight: FontWeight.w500,
                  ),
                );
              },
            ),

            const SizedBox(height: 24),

            // Subtle app name
            Text(
              'Rewordium',
              style: TextStyle(
                fontSize: 14,
                color: AppTheme.textSecondaryColor.withOpacity(0.6),
                fontWeight: FontWeight.w400,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
