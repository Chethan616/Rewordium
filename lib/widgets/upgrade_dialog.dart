import 'package:flutter/material.dart';
import '../screens/pro_upgrade_screen.dart';

/// Legacy upgrade dialog - now redirects to full-screen experience
/// Kept for backward compatibility
class UpgradeDialog extends StatelessWidget {
  const UpgradeDialog({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    // Immediately redirect to full-screen upgrade
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Navigator.of(context).pop();
      showProUpgradeScreen(context);
    });

    return const SizedBox.shrink();
  }
}

/// Show upgrade dialog - redirects to full-screen Pro upgrade experience
void showUpgradeDialog(BuildContext context) {
  showProUpgradeScreen(context);
}
