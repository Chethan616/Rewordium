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
      if (connectivityResult == ConnectivityResult.none) {
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
      final connectivityResult = await _connectivity.checkConnectivity();

      switch (connectivityResult) {
        case ConnectivityResult.wifi:
          return 'WiFi';
        case ConnectivityResult.mobile:
          return 'Mobile Data';
        case ConnectivityResult.ethernet:
          return 'Ethernet';
        case ConnectivityResult.bluetooth:
          return 'Bluetooth';
        case ConnectivityResult.vpn:
          return 'VPN';
        case ConnectivityResult.none:
          return 'No Connection';
        default:
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
      final connectivityResult = await _connectivity.checkConnectivity();
      final status = await getConnectivityStatus();
      final hasInternet = await hasInternetConnection();

      return ConnectivityInfo(
        connectivityResult: connectivityResult,
        status: status,
        hasInternet: hasInternet,
      );
    } catch (e) {
      debugPrint('Error getting detailed connectivity info: $e');
      return ConnectivityInfo(
        connectivityResult: ConnectivityResult.none,
        status: 'Error',
        hasInternet: false,
      );
    }
  }

  /// Stream to listen for connectivity changes
  static Stream<ConnectivityResult> get onConnectivityChanged =>
      _connectivity.onConnectivityChanged;
}

class ConnectivityInfo {
  final ConnectivityResult connectivityResult;
  final String status;
  final bool hasInternet;

  ConnectivityInfo({
    required this.connectivityResult,
    required this.status,
    required this.hasInternet,
  });

  bool get isConnected => connectivityResult != ConnectivityResult.none;

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
