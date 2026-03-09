import 'package:flutter/foundation.dart';

/// Application-wide logging utility that respects debug/release mode.
/// 
/// In release mode, most logs are suppressed to avoid cluttering the console
/// and improve performance. Only critical errors are logged in release mode.
class AppLogger {
  /// Log a general info message (debug mode only)
  static void info(String message) {
    if (kDebugMode) {
      debugPrint('ℹ️ $message');
    }
  }

  /// Log a debug message (debug mode only)
  static void debug(String message) {
    if (kDebugMode) {
      debugPrint('🔍 $message');
    }
  }

  /// Log a success message (debug mode only)
  static void success(String message) {
    if (kDebugMode) {
      debugPrint('✅ $message');
    }
  }

  /// Log a warning message (debug mode only)
  static void warning(String message) {
    if (kDebugMode) {
      debugPrint('⚠️ $message');
    }
  }

  /// Log an error message (always logged, even in release mode)
  static void error(String message, [Object? error, StackTrace? stackTrace]) {
    // Always log errors
    debugPrint('❌ $message');
    if (error != null && kDebugMode) {
      debugPrint('Error details: $error');
      if (stackTrace != null) {
        debugPrint('Stack trace: $stackTrace');
      }
    }
  }

  /// Log initialization messages (debug mode only)
  static void init(String serviceName) {
    if (kDebugMode) {
      debugPrint('🚀 $serviceName initialized');
    }
  }

  /// Log only in debug mode - general purpose
  static void log(String message) {
    if (kDebugMode) {
      debugPrint(message);
    }
  }
}
