import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionHandler {
  // Request camera permission
  Future<bool> requestCameraPermission() async {
    final status = await Permission.camera.status;

    if (status.isGranted) {
      return true;
    }

    if (status.isDenied) {
      final result = await Permission.camera.request();
      return result.isGranted;
    }

    return false;
  }

  // Request microphone permission
  Future<bool> requestMicrophonePermission() async {
    final status = await Permission.microphone.status;

    if (status.isGranted) {
      return true;
    }

    if (status.isDenied) {
      final result = await Permission.microphone.request();
      return result.isGranted;
    }

    return false;
  }

  // Request photos permission
  Future<bool> requestPhotosPermission() async {
    final status = await Permission.photos.status;

    if (status.isGranted) {
      return true;
    }

    if (status.isDenied) {
      final result = await Permission.photos.request();
      return result.isGranted;
    }

    return false;
  }
}
