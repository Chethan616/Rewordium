import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'dart:async';

import '../firebase_options.dart';

class FirebaseService {
  static final FirebaseAuth _auth = FirebaseAuth.instance;
  static final GoogleSignIn _googleSignIn = GoogleSignIn();
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  
  // Cache for user data to reduce Firestore reads
  static final Map<String, Map<String, dynamic>> _userDataCache = {};
  static bool _isInitialized = false;
  static Completer<void>? _initCompleter;

  // Initialize Firebase with performance optimizations
  static Future<void> initializeFirebase() async {
    // If already initializing, return the existing completer
    if (_initCompleter != null && !_initCompleter!.isCompleted) {
      return _initCompleter!.future;
    }
    
    // If already initialized, return immediately
    if (_isInitialized) {
      debugPrint('Firebase already initialized, skipping');
      return;
    }
    
    // Create a new completer to track initialization
    _initCompleter = Completer<void>();
    
    try {
      // Check if Firebase is already initialized
      if (Firebase.apps.isNotEmpty) {
        debugPrint('Firebase already initialized, using existing app');
        _isInitialized = true;
        _initCompleter!.complete();
        return;
      }
      
      // Initialize Firebase only if not already initialized
      await Firebase.initializeApp(
        options: DefaultFirebaseOptions.currentPlatform,
      );
      
      debugPrint('Firebase initialized successfully');
      _isInitialized = true;
      _initCompleter!.complete();
    } catch (e) {
      debugPrint('Error initializing Firebase: $e');
      // Mark as initialized even on error to prevent repeated init attempts
      _isInitialized = true;
      _initCompleter!.complete();
    }
    
    return _initCompleter!.future;
  }

  // Create user document with name and pro status
  static Future<void> createUserDocument(User user, String name) async {
    try {
      await _firestore.collection('users').doc(user.uid).set({
        'name': name,
        'email': user.email,
        'isPro': false,
        'createdAt': FieldValue.serverTimestamp(),
      });
    } catch (e) {
      debugPrint('Error creating user document: $e');
      rethrow;
    }
  }

  // Get user data with caching for better performance
  static Future<Map<String, dynamic>?> getUserData(String uid) async {
    try {
      // Check if we have cached data
      if (_userDataCache.containsKey(uid)) {
        debugPrint('Using cached user data for uid: $uid');
        return _userDataCache[uid];
      }
      
      // If not in cache, fetch from Firestore
      final doc = await _firestore.collection('users').doc(uid).get();
      final data = doc.data();
      
      // Cache the data if it exists
      if (data != null) {
        _userDataCache[uid] = data;
      }
      
      return data;
    } catch (e) {
      debugPrint('Error getting user data: $e');
      return null;
    }
  }
  
  // Clear user cache when data might be stale
  static void clearUserCache(String? uid) {
    if (uid != null) {
      // Clear specific user cache
      _userDataCache.remove(uid);
    } else {
      // Clear all user cache
      _userDataCache.clear();
    }
  }

  // Update user pro status
  static Future<void> updateProStatus(String uid, bool isPro) async {
    try {
      await _firestore.collection('users').doc(uid).update({
        'isPro': isPro,
      });
    } catch (e) {
      debugPrint('Error updating pro status: $e');
      rethrow;
    }
  }

  // Sign up with email and password
  static Future<User?> signUpWithEmailAndPassword(
    String email,
    String password,
    String name,
  ) async {
    try {
      final UserCredential userCredential = await _auth
          .createUserWithEmailAndPassword(email: email, password: password);

      // Create user document with default pro status
      await createUserDocument(userCredential.user!, name);

      return userCredential.user;
    } on FirebaseAuthException catch (e) {
      debugPrint('Sign up error: ${e.message}');
      rethrow;
    }
  }

  // Sign in with email and password
  static Future<User?> signInWithEmailAndPassword(
    String email,
    String password,
  ) async {
    try {
      final UserCredential userCredential = await _auth
          .signInWithEmailAndPassword(email: email, password: password);
      return userCredential.user;
    } on FirebaseAuthException catch (e) {
      debugPrint('Sign in error: ${e.message}');
      rethrow;
    }
  }

  // Sign in with Google
  static Future<User?> signInWithGoogle() async {
    try {
      // Begin interactive sign-in process
      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();
      if (googleUser == null) return null;

      // Obtain auth details from the request
      final GoogleSignInAuthentication googleAuth =
          await googleUser.authentication;

      // Create a new credential for Firebase
      final OAuthCredential credential = GoogleAuthProvider.credential(
        accessToken: googleAuth.accessToken,
        idToken: googleAuth.idToken,
      );

      // Sign in to Firebase with the Google credential
      final UserCredential userCredential =
          await _auth.signInWithCredential(credential);
      return userCredential.user;
    } catch (e) {
      debugPrint('Google sign in error: $e');
      return null;
    }
  }

  // Sign out
  static Future<void> signOut() async {
    try {
      await _googleSignIn.signOut();
      await _auth.signOut();
    } catch (e) {
      debugPrint('Sign out error: $e');
    }
  }

  // Get current user
  static User? getCurrentUser() {
    return _auth.currentUser;
  }

  // Check if user is signed in
  static bool isUserSignedIn() {
    return _auth.currentUser != null;
  }

  // Listen to auth state changes
  static Stream<User?> get authStateChanges => _auth.authStateChanges();

  // Delete user account and Firestore document
  static Future<void> deleteAccount(User user) async {
    try {
      // Delete Firestore user document
      await _firestore.collection('users').doc(user.uid).delete();
      // Delete FirebaseAuth user
      await user.delete();
    } catch (e) {
      debugPrint('Error deleting account: $e');
      rethrow;
    }
  }
}
