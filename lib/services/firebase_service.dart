import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'dart:async';

import '../firebase_options.dart';

class FirebaseService {
  static final FirebaseAuth _auth = FirebaseAuth.instance;
  static final GoogleSignIn _googleSignIn = GoogleSignIn();
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  static final Map<String, Map<String, dynamic>> _userDataCache = {};
  static bool _isInitialized = false;
  static Completer<void>? _initCompleter;

  // Defines the number of credits a new, free user receives upon signing up and on refresh.
  static const int initialFreeCredits = 20;

  // For backward compatibility
  static Future<void> initializeFirebase() async {
    return initialize();
  }

  static Future<void> initialize() async {
    if (_initCompleter != null && !_initCompleter!.isCompleted) {
      return _initCompleter!.future;
    }
    if (_isInitialized) {
      return;
    }
    _initCompleter = Completer<void>();
    try {
      if (Firebase.apps.isNotEmpty) {
        _isInitialized = true;
        _initCompleter!.complete();
        return;
      }
      await Firebase.initializeApp(
        options: DefaultFirebaseOptions.currentPlatform,
      );
      _isInitialized = true;
      _initCompleter!.complete();
    } catch (e) {
      _isInitialized = true;
      _initCompleter!.complete();
    }
    return _initCompleter!.future;
  }

  // --- CHANGED: This function now also sets the initial `lastCreditRefresh` timestamp.
  static Future<void> createUserDocument(User user, String name) async {
    try {
      // Use a server-generated timestamp for accuracy.
      final now = FieldValue.serverTimestamp();
      await _firestore.collection('users').doc(user.uid).set({
        'name': name,
        'email': user.email,
        'isPro': false,
        'createdAt': now,
        'credits': initialFreeCredits,
        'lastCreditRefresh': now,
        'signInMethod': 'email',
      });
    } catch (e) {
      rethrow;
    }
  }

  // Helper function to create user document for Google sign-in users
  static Future<void> createGoogleUserDocument(User user) async {
    try {
      // Use a server-generated timestamp for accuracy.
      final now = FieldValue.serverTimestamp();
      final userName =
          user.displayName ?? user.email?.split('@')[0] ?? 'Google User';

      await _firestore.collection('users').doc(user.uid).set({
        'name': userName,
        'email': user.email,
        'isPro': false,
        'createdAt': now,
        'credits': initialFreeCredits,
        'lastCreditRefresh': now,
        'signInMethod': 'google',
      });
    } catch (e) {
      rethrow;
    }
  }

  // <-- ADDED: A new function to handle the credit refresh database transaction. -->
  /// Atomically updates a user's credits back to the initial amount and updates
  /// the refresh timestamp to the current time.
  static Future<void> refreshFreeCredits(String uid) async {
    try {
      await _firestore.collection('users').doc(uid).update({
        'credits': initialFreeCredits,
        'lastCreditRefresh': FieldValue.serverTimestamp(),
      });
      clearUserCache(uid);
    } catch (e) {
      rethrow;
    }
  }

  // Update user credits in Firestore
  static Future<void> updateUserCredits(String uid, int newCredits) async {
    try {
      await _firestore.collection('users').doc(uid).update({
        'credits': newCredits,
      });
      clearUserCache(uid);
    } catch (e) {
      rethrow;
    }
  }

  // Consume a credit for free users
  static Future<bool> consumeCredit(String uid) async {
    try {
      final userDoc = await _firestore.collection('users').doc(uid).get();
      final userData = userDoc.data();

      if (userData == null) {
        return false;
      }

      final isPro = userData['isPro'] as bool? ?? false;
      if (isPro) {
        return true;
      }

      final currentCredits = userData['credits'] as int? ?? 0;
      if (currentCredits <= 0) {
        return false;
      }

      await updateUserCredits(uid, currentCredits - 1);
      return true;
    } catch (e) {
      return false;
    }
  }

  // Get user data with caching for better performance
  static Future<Map<String, dynamic>?> getUserData(String uid) async {
    try {
      if (_userDataCache.containsKey(uid)) {
        return _userDataCache[uid];
      }
      final doc = await _firestore.collection('users').doc(uid).get();
      final data = doc.data();
      if (data != null) {
        _userDataCache[uid] = data;
      }
      return data;
    } catch (e) {
      return null;
    }
  }

  // Clear user cache when data might be stale
  static void clearUserCache(String? uid) {
    if (uid != null) {
      _userDataCache.remove(uid);
    } else {
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
      if (!_isInitialized) {
        await initializeFirebase();
      }

      if (email.trim().isEmpty) {
        throw FirebaseAuthException(
            code: 'invalid-email', message: 'Email cannot be empty');
      }

      if (password.length < 6) {
        throw FirebaseAuthException(
            code: 'weak-password',
            message: 'Password must be at least 6 characters');
      }

      if (name.trim().isEmpty) {
        throw FirebaseAuthException(
            code: 'invalid-name', message: 'Name cannot be empty');
      }

      final UserCredential userCredential =
          await _auth.createUserWithEmailAndPassword(
              email: email.trim(), password: password);

      if (userCredential.user != null) {
        await createUserDocument(userCredential.user!, name.trim());
      }

      return userCredential.user;
    } on FirebaseAuthException {
      rethrow;
    } catch (e) {
      rethrow;
    }
  }

  // Sign in with email and password
  static Future<User?> signInWithEmailAndPassword(
    String email,
    String password,
  ) async {
    try {
      if (!_isInitialized) {
        await initializeFirebase();
      }

      if (email.trim().isEmpty) {
        throw FirebaseAuthException(
            code: 'invalid-email', message: 'Email cannot be empty');
      }

      if (password.isEmpty) {
        throw FirebaseAuthException(
            code: 'wrong-password', message: 'Password cannot be empty');
      }

      final UserCredential userCredential = await _auth
          .signInWithEmailAndPassword(email: email.trim(), password: password);

      return userCredential.user;
    } on FirebaseAuthException {
      rethrow;
    } catch (e) {
      rethrow;
    }
  }

  // Sign in with Google
  static Future<User?> signInWithGoogle() async {
    try {
      if (!_isInitialized) {
        await initializeFirebase();
      }

      try {
        final bool isSignedIn = await _googleSignIn.isSignedIn();
        if (isSignedIn) {
          await _googleSignIn.signOut();
        }
      } catch (e) {
        // Continue with sign-in even if logout fails
      }

      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();

      if (googleUser == null) {
        return null;
      }

      final GoogleSignInAuthentication googleAuth =
          await googleUser.authentication;

      if (googleAuth.accessToken == null || googleAuth.idToken == null) {
        throw Exception('Failed to get Google authentication tokens');
      }

      final OAuthCredential credential = GoogleAuthProvider.credential(
        accessToken: googleAuth.accessToken,
        idToken: googleAuth.idToken,
      );

      final UserCredential userCredential =
          await _auth.signInWithCredential(credential);

      if (userCredential.user != null) {
        final userDoc = await _firestore
            .collection('users')
            .doc(userCredential.user!.uid)
            .get();

        if (!userDoc.exists) {
          await createGoogleUserDocument(userCredential.user!);
        }
      }

      return userCredential.user;
    } catch (e) {
      return null;
    }
  }

  // Sign out
  static Future<void> signOut() async {
    try {
      await _googleSignIn.signOut();
      await _auth.signOut();
    } catch (e) {
      // Continue even if sign out fails
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
      await _firestore.collection('users').doc(user.uid).delete();
      await user.delete();
    } catch (e) {
      rethrow;
    }
  }
}
