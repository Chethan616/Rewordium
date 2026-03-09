import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

/// A utility class to control frame rate and ensure smooth 60 FPS animations
class FrameRateController {
  // Singleton instance
  static final FrameRateController _instance = FrameRateController._internal();
  static FrameRateController get instance => _instance;
  FrameRateController._internal();

  // Target frame rate settings
  static const int targetFPS = 60;
  static const Duration targetFrameDuration = Duration(milliseconds: 16);
  
  // Performance tracking
  bool _isMonitoring = false;
  final List<Duration> _lastFrameTimes = [];
  int _droppedFrames = 0;
  int _totalFrames = 0;
  
  // Start monitoring frame rates
  void startMonitoring() {
    if (_isMonitoring) return;
    _isMonitoring = true;
    _lastFrameTimes.clear();
    _droppedFrames = 0;
    _totalFrames = 0;
    
    // Register a frame callback to track performance
    SchedulerBinding.instance.addPostFrameCallback(_onFrameCompleted);
  }
  
  // Stop monitoring frame rates
  void stopMonitoring() {
    _isMonitoring = false;
  }
  
  // Callback for each frame completion
  void _onFrameCompleted(Duration timestamp) {
    if (!_isMonitoring) return;
    
    // Add this frame to our measurements
    if (_lastFrameTimes.isNotEmpty) {
      final frameDuration = timestamp - _lastFrameTimes.last;
      _totalFrames++;
      
      // Consider a frame "dropped" if it took more than 1.5x the target time
      if (frameDuration > targetFrameDuration * 1.5) {
        _droppedFrames++;
      }
      
      // Keep our history limited to avoid memory issues
      if (_lastFrameTimes.length > 120) { // 2 seconds of frames at 60fps
        _lastFrameTimes.removeAt(0);
      }
    }
    
    _lastFrameTimes.add(timestamp);
    
    // Register for the next frame
    if (_isMonitoring) {
      SchedulerBinding.instance.addPostFrameCallback(_onFrameCompleted);
    }
  }
  
  // Get the current frame rate
  double getCurrentFPS() {
    if (_lastFrameTimes.length < 2) return targetFPS.toDouble();
    
    final firstTimestamp = _lastFrameTimes.first;
    final lastTimestamp = _lastFrameTimes.last;
    final duration = lastTimestamp - firstTimestamp;
    
    // Calculate FPS based on frame count and duration
    if (duration.inMicroseconds > 0) {
      return (_lastFrameTimes.length - 1) / duration.inSeconds;
    }
    
    return targetFPS.toDouble();
  }
  
  // Check if we need to optimize for performance
  bool shouldOptimizePerformance() {
    // If we have less than 30 measurements, assume we need optimization
    if (_totalFrames < 30) return true;
    
    // If more than 10% of frames are dropped, we need optimization
    return _droppedFrames / _totalFrames > 0.1;
  }
  
  // Wrap a widget with performance optimizations if needed
  Widget wrapWithOptimizations(Widget child, {bool forceOptimize = false}) {
    final needsOptimization = forceOptimize || shouldOptimizePerformance();
    
    if (needsOptimization) {
      // Apply multiple optimization techniques
      return RepaintBoundary(
        child: child,
      );
    }
    
    return child;
  }
}
