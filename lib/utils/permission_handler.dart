import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PermissionHandler {
  static const String _prefKeyPrefix = 'permission_asked_';

  /// Check if we have already asked for a specific permission.
  Future<bool> _hasAlreadyAsked(String permissionKey) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool('$_prefKeyPrefix$permissionKey') ?? false;
  }

  /// Mark a permission as having been asked.
  Future<void> _markAsAsked(String permissionKey) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('$_prefKeyPrefix$permissionKey', true);
  }

  /// Generic permission request that only asks once.
  /// Returns true if granted, false otherwise.
  Future<bool> _requestPermissionOnce(
    Permission permission,
    String permissionKey,
  ) async {
    final status = await permission.status;

    // Already granted — nothing to do
    if (status.isGranted || status.isLimited) {
      return true;
    }

    // Already permanently denied — don't ask again
    if (status.isPermanentlyDenied) {
      return false;
    }

    // Check if we've already asked before
    final alreadyAsked = await _hasAlreadyAsked(permissionKey);
    if (alreadyAsked) {
      // We asked before and user denied — don't pester them
      return false;
    }

    // First time asking — request the permission
    final result = await permission.request();
    await _markAsAsked(permissionKey);
    return result.isGranted || result.isLimited;
  }

  // Request notification permission (only once)
  Future<bool> requestNotificationPermission() async {
    return _requestPermissionOnce(Permission.notification, 'notification');
  }
}
