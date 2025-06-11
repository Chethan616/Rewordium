import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import '../services/firebase_service.dart';

class AuthProvider extends ChangeNotifier {
  User? _user;
  bool _isLoading = false;
  String? _error;
  String? _userName;
  bool _isPro = false;

  User? get user => _user;
  bool get isLoggedIn => _user != null;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get userName => _userName;
  bool get isPro => _isPro;

  AuthProvider() {
    // Listen to auth state changes
    FirebaseService.authStateChanges.listen((User? user) async {
      _user = user;
      if (user != null) {
        await _loadUserData();
      } else {
        _userName = null;
        _isPro = false;
      }
      notifyListeners();
    });

    // Check if user is already logged in
    _user = FirebaseService.getCurrentUser();
    if (_user != null) {
      _loadUserData();
    }
  }

  Future<void> _loadUserData() async {
    if (_user != null) {
      final userData = await FirebaseService.getUserData(_user!.uid);
      if (userData != null) {
        _userName = userData['name'];
        _isPro = userData['isPro'] ?? false;
        notifyListeners();
      }
    }
  }

  // Sign up with email and password
  Future<bool> signUpWithEmailAndPassword(
      String email, String password, String name) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _user = await FirebaseService.signUpWithEmailAndPassword(
          email, password, name);
      await _loadUserData();
      _isLoading = false;
      notifyListeners();
      return true;
    } on FirebaseAuthException catch (e) {
      _error = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  // Sign in with email and password
  Future<bool> signInWithEmailAndPassword(String email, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _user = await FirebaseService.signInWithEmailAndPassword(email, password);
      await _loadUserData();
      _isLoading = false;
      notifyListeners();
      return true;
    } on FirebaseAuthException catch (e) {
      _error = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  // Sign in with Google
  Future<bool> signInWithGoogle() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      _user = await FirebaseService.signInWithGoogle();
      if (_user != null) {
        // Create user document if it doesn't exist
        final userData = await FirebaseService.getUserData(_user!.uid);
        if (userData == null) {
          await FirebaseService.createUserDocument(
              _user!, _user!.displayName ?? 'User');
        }
        await _loadUserData();
      }
      _isLoading = false;
      notifyListeners();
      return _user != null;
    } catch (e) {
      _error = e.toString();
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  // Sign out
  Future<void> signOut() async {
    _isLoading = true;
    notifyListeners();

    await FirebaseService.signOut();
    _user = null;
    _userName = null;
    _isPro = false;
    _isLoading = false;
    notifyListeners();
  }

  // Clear error
  void clearError() {
    _error = null;
    notifyListeners();
  }

  // Delete account
  Future<bool> deleteAccount() async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_user != null) {
        await FirebaseService.deleteAccount(_user!);
        await signOut();
        _isLoading = false;
        notifyListeners();
        return true;
      }
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (e) {
      _error = e.toString();
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }
}
