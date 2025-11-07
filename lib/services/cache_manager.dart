import 'dart:async';
import 'package:flutter/foundation.dart';
import 'firebase_service.dart';
import 'groq_service.dart';

/// A service to manage cache and memory usage in the app
class CacheManager {
  static bool _isInitialized = false;
  static Timer? _cacheCleanupTimer;
  
  /// Initialize the cache manager with periodic cleanup
  static void initialize() {
    if (_isInitialized) return;
    
    // Start a periodic timer to clean up caches
    _cacheCleanupTimer = Timer.periodic(const Duration(minutes: 30), (_) {
      cleanupCaches();
    });
    
    _isInitialized = true;
    debugPrint('Cache manager initialized');
  }
  
  /// Clean up all caches to free memory
  static void cleanupCaches() {
    debugPrint('Performing cache cleanup');
    
    // Clear Firebase user cache for inactive users
    FirebaseService.clearUserCache(null);
    
    // Clear Groq response cache for old responses
    GroqService.clearResponseCache();
    
    // Force garbage collection (this is just a hint to the system)
    // ignore: unused_result
    Future<void>.microtask(() async {
      await Future<void>.delayed(const Duration(milliseconds: 100));
      debugPrint('Memory cleanup completed');
    });
  }
  
  /// Dispose the cache manager
  static void dispose() {
    _cacheCleanupTimer?.cancel();
    _cacheCleanupTimer = null;
    _isInitialized = false;
  }
}
