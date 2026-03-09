import 'dart:async';
import 'dart:io';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';

class ConnectivityService {
  static final Connectivity _connectivity = Connectivity();

  /// Check if device has internet connectivity
  static Future<bool> hasInternetConnection() async {
    try {
      final connectivityResult = await _connectivity.checkConnectivity();

      // If no connectivity, return false immediately
      if (connectivityResult.contains(ConnectivityResult.none)) {
        debugPrint('No network connectivity detected');
        return false;
      }

      // Even if connected to WiFi/Mobile, verify actual internet access
      return await _hasActualInternetAccess();
    } catch (e) {
      debugPrint('Error checking connectivity: $e');
      return false;
    }
  }

  /// Check actual internet access by attempting to reach Google DNS
  static Future<bool> _hasActualInternetAccess() async {
    try {
      final result = await InternetAddress.lookup('google.com')
          .timeout(const Duration(seconds: 5));

      if (result.isNotEmpty && result[0].rawAddress.isNotEmpty) {
        debugPrint('Internet connectivity verified');
        return true;
      }
      return false;
    } on SocketException catch (_) {
      debugPrint('No internet access - socket exception');
      return false;
    } on TimeoutException catch (_) {
      debugPrint('No internet access - timeout');
      return false;
    } catch (e) {
      debugPrint('Error verifying internet access: $e');
      return false;
    }
  }

  /// Get connectivity status description
  static Future<String> getConnectivityStatus() async {
    try {
      final connectivityResults = await _connectivity.checkConnectivity();

      if (connectivityResults.contains(ConnectivityResult.wifi)) {
        return 'WiFi';
      } else if (connectivityResults.contains(ConnectivityResult.mobile)) {
        return 'Mobile Data';
      } else if (connectivityResults.contains(ConnectivityResult.ethernet)) {
        return 'Ethernet';
      } else if (connectivityResults.contains(ConnectivityResult.bluetooth)) {
        return 'Bluetooth';
      } else if (connectivityResults.contains(ConnectivityResult.vpn)) {
        return 'VPN';
      } else if (connectivityResults.contains(ConnectivityResult.none)) {
        return 'No Connection';
      } else {
        return 'Unknown';
      }
    } catch (e) {
      debugPrint('Error getting connectivity status: $e');
      return 'Unknown';
    }
  }

  /// Check connectivity with detailed information
  static Future<ConnectivityInfo> getDetailedConnectivityInfo() async {
    try {
      final connectivityResults = await _connectivity.checkConnectivity();
      final status = await getConnectivityStatus();
      final hasInternet = await hasInternetConnection();

      return ConnectivityInfo(
        connectivityResults: connectivityResults,
        status: status,
        hasInternet: hasInternet,
      );
    } catch (e) {
      debugPrint('Error getting detailed connectivity info: $e');
      return ConnectivityInfo(
        connectivityResults: [ConnectivityResult.none],
        status: 'Error',
        hasInternet: false,
      );
    }
  }

  /// Stream to listen for connectivity changes
  static Stream<List<ConnectivityResult>> get onConnectivityChanged =>
      _connectivity.onConnectivityChanged;
}

class ConnectivityInfo {
  final List<ConnectivityResult> connectivityResults;
  final String status;
  final bool hasInternet;

  ConnectivityInfo({
    required this.connectivityResults,
    required this.status,
    required this.hasInternet,
  });

  bool get isConnected => !connectivityResults.contains(ConnectivityResult.none);

  String get description {
    if (!isConnected) {
      return 'No network connection detected';
    } else if (!hasInternet) {
      return 'Connected to $status but no internet access';
    } else {
      return 'Connected to $status with internet access';
    }
  }
}
