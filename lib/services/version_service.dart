import 'package:package_info_plus/package_info_plus.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:flutter/material.dart';

class VersionService {
  static const String _versionCollection = 'app_config';
  static const String _versionDoc = 'version_control';

  /// Get current app version
  static Future<String> getCurrentVersion() async {
    final packageInfo = await PackageInfo.fromPlatform();
    return packageInfo.version;
  }

  /// Get current build number
  static Future<String> getCurrentBuildNumber() async {
    final packageInfo = await PackageInfo.fromPlatform();
    return packageInfo.buildNumber;
  }

  /// Check if app update is required
  static Future<UpdateStatus> checkForUpdate() async {
    try {
      final currentVersion = await getCurrentVersion();
      final currentBuildNumber = await getCurrentBuildNumber();

      // Get version info from Firestore with timeout
      final doc = await FirebaseFirestore.instance
          .collection(_versionCollection)
          .doc(_versionDoc)
          .get()
          .timeout(
        const Duration(seconds: 10),
        onTimeout: () {
          throw Exception('Timeout: Unable to connect to update service');
        },
      );

      if (!doc.exists) {
        debugPrint('Version document not found in Firestore');
        throw Exception('Update configuration not found');
      }

      final data = doc.data();
      if (data == null || data.isEmpty) {
        debugPrint('Version document exists but contains no data');
        throw Exception('Update configuration is invalid');
      }

      final minimumVersion = data['minimum_version'] as String?;
      final latestVersion = data['latest_version'] as String?;
      final minimumBuildNumber = data['minimum_build_number'] as String?;
      final latestBuildNumber = data['latest_build_number'] as String?;
      final forceUpdate = data['force_update'] as bool? ?? false;
      final updateMessage =
          data['update_message'] as String? ?? 'A new version is available';
      final playStoreUrl = data['play_store_url'] as String? ?? '';

      debugPrint(
          'Current version: $currentVersion (build $currentBuildNumber)');
      debugPrint(
          'Minimum version: $minimumVersion (build $minimumBuildNumber)');
      debugPrint('Latest version: $latestVersion (build $latestBuildNumber)');
      debugPrint('Force update: $forceUpdate');
      debugPrint('Update message: $updateMessage');
      debugPrint('Play Store URL: $playStoreUrl');

      // Validate that we have the required fields
      if (minimumVersion == null || latestVersion == null) {
        debugPrint('Missing required version fields in Firestore document');
        throw Exception('Update configuration is incomplete');
      }

      // Check if current version is below minimum required (this always forces update)
      if (minimumBuildNumber != null) {
        final currentBuild = int.tryParse(currentBuildNumber) ?? 0;
        final minBuild = int.tryParse(minimumBuildNumber) ?? 0;

        if (currentBuild < minBuild ||
            _isVersionLower(currentVersion, minimumVersion)) {
          return UpdateStatus.forceUpdate;
        }
      }

      // Check if there's a newer version available
      bool hasNewerVersion = false;
      if (latestBuildNumber != null) {
        final currentBuild = int.tryParse(currentBuildNumber) ?? 0;
        final latestBuild = int.tryParse(latestBuildNumber) ?? 0;

        if (currentBuild < latestBuild ||
            _isVersionLower(currentVersion, latestVersion)) {
          hasNewerVersion = true;
        }
      }

      // Check if force update is enabled AND there's actually a newer version
      if (forceUpdate && hasNewerVersion) {
        return UpdateStatus.forceUpdate;
      }

      // Return optional update if there's a newer version (but force update is disabled)
      if (hasNewerVersion) {
        return UpdateStatus.optionalUpdate;
      }

      return UpdateStatus.noUpdateNeeded;
    } on Exception catch (e) {
      debugPrint('Specific error checking for update: $e');
      return UpdateStatus.error;
    } catch (e) {
      debugPrint('General error checking for update: $e');
      return UpdateStatus.error;
    }
  }

  /// Compare version strings (e.g., "1.0.3" vs "1.0.4")
  static bool _isVersionLower(String current, String target) {
    final currentParts = current.split('.').map(int.tryParse).toList();
    final targetParts = target.split('.').map(int.tryParse).toList();

    for (int i = 0; i < 3; i++) {
      final currentPart = currentParts.length > i ? (currentParts[i] ?? 0) : 0;
      final targetPart = targetParts.length > i ? (targetParts[i] ?? 0) : 0;

      if (currentPart < targetPart) return true;
      if (currentPart > targetPart) return false;
    }

    return false;
  }

  /// Get update configuration from Firestore
  static Future<UpdateConfig> getUpdateConfig() async {
    try {
      final doc = await FirebaseFirestore.instance
          .collection(_versionCollection)
          .doc(_versionDoc)
          .get()
          .timeout(
        const Duration(seconds: 10),
        onTimeout: () {
          throw Exception('Timeout: Unable to connect to update service');
        },
      );

      if (!doc.exists) {
        debugPrint('Update config document not found, using defaults');
        return UpdateConfig.defaultConfig();
      }

      final data = doc.data();
      if (data == null || data.isEmpty) {
        debugPrint(
            'Update config document exists but contains no data, using defaults');
        return UpdateConfig.defaultConfig();
      }

      return UpdateConfig.fromFirestore(data);
    } catch (e) {
      debugPrint('Error getting update config: $e');
      return UpdateConfig.defaultConfig();
    }
  }

  /// Launch Play Store for update
  static Future<bool> launchPlayStore() async {
    try {
      final config = await getUpdateConfig();
      final url = config.playStoreUrl.isNotEmpty
          ? config.playStoreUrl
          : 'https://play.google.com/store/apps/details?id=com.noxquill.rewordium';

      debugPrint('Attempting to launch Play Store URL: $url');
      final uri = Uri.parse(url);

      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
        debugPrint('Successfully launched Play Store');
        return true;
      } else {
        debugPrint('Could not launch Play Store URL: $url');
        return false;
      }
    } catch (e) {
      debugPrint('Error launching Play Store: $e');
      return false;
    }
  }

  /// Show update dialog
  static Future<void> showUpdateDialog(
      BuildContext context, UpdateStatus status) async {
    try {
      final config = await getUpdateConfig();

      if (!context.mounted) {
        debugPrint('Context not mounted, skipping update dialog');
        return;
      }

      showDialog(
        context: context,
        barrierDismissible: status != UpdateStatus.forceUpdate,
        builder: (context) => UpdateDialog(
          status: status,
          config: config,
        ),
      );
    } catch (e) {
      debugPrint('Error showing update dialog: $e');
    }
  }
}

enum UpdateStatus {
  noUpdateNeeded,
  optionalUpdate,
  forceUpdate,
  error,
}

class UpdateConfig {
  final String minimumVersion;
  final String latestVersion;
  final String minimumBuildNumber;
  final String latestBuildNumber;
  final bool forceUpdate;
  final String updateMessage;
  final String playStoreUrl;
  final String forceUpdateTitle;
  final String optionalUpdateTitle;

  UpdateConfig({
    required this.minimumVersion,
    required this.latestVersion,
    required this.minimumBuildNumber,
    required this.latestBuildNumber,
    required this.forceUpdate,
    required this.updateMessage,
    required this.playStoreUrl,
    required this.forceUpdateTitle,
    required this.optionalUpdateTitle,
  });

  factory UpdateConfig.fromFirestore(Map<String, dynamic> data) {
    return UpdateConfig(
      minimumVersion: data['minimum_version'] as String? ?? '1.0.0',
      latestVersion: data['latest_version'] as String? ?? '1.0.0',
      minimumBuildNumber: data['minimum_build_number'] as String? ?? '1',
      latestBuildNumber: data['latest_build_number'] as String? ?? '1',
      forceUpdate: data['force_update'] as bool? ?? false,
      updateMessage: data['update_message'] as String? ??
          'A new version is available with improvements and bug fixes.',
      playStoreUrl: data['play_store_url'] as String? ?? '',
      forceUpdateTitle:
          data['force_update_title'] as String? ?? 'Update Required',
      optionalUpdateTitle:
          data['optional_update_title'] as String? ?? 'Update Available',
    );
  }

  factory UpdateConfig.defaultConfig() {
    return UpdateConfig(
      minimumVersion: '1.0.0',
      latestVersion: '1.0.0',
      minimumBuildNumber: '1',
      latestBuildNumber: '1',
      forceUpdate: false,
      updateMessage:
          'A new version is available with improvements and bug fixes.',
      playStoreUrl: '',
      forceUpdateTitle: 'Update Required',
      optionalUpdateTitle: 'Update Available',
    );
  }
}

class UpdateDialog extends StatelessWidget {
  final UpdateStatus status;
  final UpdateConfig config;

  const UpdateDialog({
    super.key,
    required this.status,
    required this.config,
  });

  @override
  Widget build(BuildContext context) {
    final isForceUpdate = status == UpdateStatus.forceUpdate;

    return PopScope(
      canPop: !isForceUpdate,
      onPopInvokedWithResult: (didPop, result) {
        if (isForceUpdate && !didPop) {
          // For force update, prevent going back
          debugPrint('Force update dialog - preventing back navigation');
        }
      },
      child: AlertDialog(
        title: Text(
          isForceUpdate ? config.forceUpdateTitle : config.optionalUpdateTitle,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        content: Text(config.updateMessage),
        actions: [
          if (!isForceUpdate)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Later'),
            ),
          ElevatedButton(
            onPressed: () async {
              final success = await VersionService.launchPlayStore();
              if (!success && context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text(
                        'Unable to open Play Store. Please update manually.'),
                    backgroundColor: Colors.red,
                  ),
                );
              }
              if (!isForceUpdate && context.mounted) {
                Navigator.of(context).pop();
              }
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: isForceUpdate ? Colors.red : null,
            ),
            child: Text(isForceUpdate ? 'Update Now' : 'Update'),
          ),
        ],
      ),
    );
  }
}
