import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import '../services/firebase_service.dart';
import '../services/credit_service.dart';

class AuthProvider extends ChangeNotifier {
  User? _user;
  bool _isLoading = false;
  String? _error;
  String? _userName;
  bool _isPro = false;
  String? _planType;
  int? _credits;

  static const _platform = MethodChannel('com.noxquill.rewordium/user_status');
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  User? get user => _user;
  bool get isLoggedIn => _user != null;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get userName => _userName;
  bool get isPro => _isPro;
  String? get planType => _planType;
  int? get credits => _credits;

  bool get canPerformAction => isPro || (_credits ?? 0) > 0;

  // Updates the native service with the current user status
  Future<void> _updateNativeServiceStatus() async {
    try {
      await _platform.invokeMethod('updateUserStatus', {
        'isLoggedIn': isLoggedIn,
        'isPro': _isPro,
        'credits': _credits ?? 0,
      });
    } on PlatformException catch (e) {
      debugPrint('⚠️ Failed to sync with native service: ${e.message}');
      // This is not a critical error, authentication can still work without native sync
    } on MissingPluginException catch (e) {
      debugPrint('⚠️ Native service sync not available: ${e.message}');
      // This happens when the method channel is not properly configured, but it's not critical
    } catch (e) {
      debugPrint('⚠️ Unexpected error syncing with native service: $e');
      // Log but don't fail authentication
    }
  }

  AuthProvider() {
    // Set up method channel handler for credit consumption from Android
    _platform.setMethodCallHandler(_handleAndroidMethodCalls);

    FirebaseService.authStateChanges.listen((User? user) async {
      _user = user;
      if (user != null) {
        await _loadUserData();
      } else {
        _userName = null;
        _isPro = false;
        _planType = null;
        _credits = null;
        await _updateNativeServiceStatus(); // Ensure we await the status update
        notifyListeners();
      }
    });

    _user = FirebaseService.getCurrentUser();
    if (_user != null) {
      _loadUserData();
    }
  }

  // Handle method calls from Android side
  Future<dynamic> _handleAndroidMethodCalls(MethodCall call) async {
    switch (call.method) {
      case 'consumeCredit':
        return await consumeCredit();
      default:
        throw PlatformException(
          code: 'UNIMPLEMENTED',
          message: 'Method ${call.method} not implemented',
        );
    }
  }

  // --- CHANGED: This function now contains the monthly refresh logic ---
  Future<void> _loadUserData() async {
    if (_user == null) return;

    try {
      FirebaseService.clearUserCache(_user!.uid);
      final userData = await FirebaseService.getUserData(_user!.uid);
      if (userData == null) {
        await _updateNativeServiceStatus();
        return;
      }

      _userName = userData['name'];
      final bool isCurrentlyPro = userData['isPro'] ?? false;
      _isPro = isCurrentlyPro;
      _credits = userData['credits'] ?? 0;
      await _updateNativeServiceStatus();

      // Load current credits without any automatic refresh logic
      if (!isCurrentlyPro) {
        _credits = userData['credits'] as int? ?? 0;
      } else {
        // Pro users have unlimited credits
        _credits = null;
      }

      // Pro Subscription Expiration Check
      if (isCurrentlyPro) {
        final subData = userData['subscription'] as Map<String, dynamic>?;
        _planType = subData?['planType'] as String?;

        // Check if subscription has expired (except for onetime/lifetime plans)
        if (_planType != 'onetime' && subData?['expiryDate'] != null) {
          final expiryDate = (subData!['expiryDate'] as Timestamp).toDate();
          if (DateTime.now().isAfter(expiryDate)) {
            debugPrint('🔄 Subscription expired, revoking Pro status');
            await _handleExpiredSubscription();
            _isPro = false;
            _planType = 'free';
          }
        }
      }

      _updateNativeServiceStatus();
      notifyListeners();
    } catch (e) {
      _userName = _user?.displayName ?? _user?.email ?? 'Unknown';
      _isPro = false;
      _credits = 0;
      await _updateNativeServiceStatus();
      notifyListeners();
    }
  }

  // --- All other functions remain unchanged ---
  Future<void> _handleExpiredSubscription() async {
    if (_user == null) return;
    try {
      await _firestore.collection('users').doc(_user!.uid).update({
        'isPro': false,
        'planType': 'free',
        'credits': 20, // Reset to free credits
        'subscription.status': 'expired',
        'expiredAt': FieldValue.serverTimestamp(),
      });
      debugPrint('🔄 Pro status revoked due to subscription expiration');
    } catch (e) {
      debugPrint('❌ Error handling expired subscription: $e');
    }
  }

  Future<bool> signUpWithEmailAndPassword(
      String email, String password, String name) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      if (email.trim().isEmpty ||
          password.trim().isEmpty ||
          name.trim().isEmpty) {
        _error = 'All fields are required';
        _isLoading = false;
        notifyListeners();
        return false;
      }

      if (password.length < 6) {
        _error = 'Password must be at least 6 characters';
        _isLoading = false;
        notifyListeners();
        return false;
      }

      _user = await FirebaseService.signUpWithEmailAndPassword(
          email.trim(), password, name.trim());

      if (_user != null) {
        await _loadUserData();
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _error = 'Sign-up failed. Please try again.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } on FirebaseAuthException catch (e) {
      switch (e.code) {
        case 'weak-password':
          _error = 'Password is too weak. Please use at least 6 characters.';
          break;
        case 'email-already-in-use':
          _error = 'An account already exists for this email.';
          break;
        case 'invalid-email':
          _error = 'Please enter a valid email address.';
          break;
        case 'operation-not-allowed':
          _error = 'Email/password accounts are not enabled.';
          break;
        default:
          _error = e.message ?? 'Sign-up failed. Please try again.';
      }

      _isLoading = false;
      notifyListeners();
      return false;
    } catch (e) {
      debugPrint('❌ AuthProvider: Unexpected error: $e');

      // Don't show MissingPluginException errors to user
      if (e is MissingPluginException) {
        _error =
            'Authentication service temporarily unavailable. Please try again.';
      } else {
        _error = 'An unexpected error occurred. Please try again.';
      }

      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> signInWithEmailAndPassword(String email, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      debugPrint('🔍 AuthProvider: Starting email sign-in');

      if (email.trim().isEmpty || password.trim().isEmpty) {
        _error = 'Email and password are required';
        _isLoading = false;
        notifyListeners();
        return false;
      }

      _user = await FirebaseService.signInWithEmailAndPassword(
          email.trim(), password);

      if (_user != null) {
        debugPrint('✅ AuthProvider: Sign-in successful, loading user data');
        await _loadUserData();
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _error = 'Sign-in failed. Please try again.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } on FirebaseAuthException catch (e) {
      debugPrint('❌ AuthProvider: Firebase Auth error: ${e.code}');

      // User-friendly error messages
      switch (e.code) {
        case 'user-not-found':
          _error = 'No account found for this email.';
          break;
        case 'wrong-password':
          _error = 'Incorrect password.';
          break;
        case 'invalid-email':
          _error = 'Please enter a valid email address.';
          break;
        case 'user-disabled':
          _error = 'This account has been disabled.';
          break;
        case 'too-many-requests':
          _error = 'Too many failed attempts. Please try again later.';
          break;
        default:
          _error = e.message ?? 'Sign-in failed. Please try again.';
      }

      _isLoading = false;
      notifyListeners();
      return false;
    } catch (e) {
      debugPrint('❌ AuthProvider: Unexpected error: $e');

      // Don't show MissingPluginException errors to user
      if (e is MissingPluginException) {
        _error =
            'Authentication service temporarily unavailable. Please try again.';
      } else {
        _error = 'An unexpected error occurred. Please try again.';
      }

      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> signInWithGoogle() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      debugPrint('🔍 AuthProvider: Starting Google Sign-In');

      _user = await FirebaseService.signInWithGoogle();

      if (_user != null) {
        debugPrint(
            '✅ AuthProvider: Google Sign-In successful, loading user data');
        // Note: FirebaseService.signInWithGoogle() already handles checking if user exists
        // and only creates a new document for truly new users. We don't need to check again here.
        await _loadUserData();
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        debugPrint('❌ AuthProvider: Google Sign-In returned null user');
        _error = 'Google Sign-In was cancelled or failed';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      debugPrint('❌ AuthProvider: Google Sign-In error: $e');

      // Don't show MissingPluginException errors to user for Google Sign-In
      if (e is MissingPluginException) {
        _error =
            'Authentication service temporarily unavailable. Please try again.';
      } else {
        // Provide more specific error messages based on common issues
        if (e.toString().contains('DEVELOPER_ERROR')) {
          _error = 'Configuration error. Please contact support.';
          debugPrint(
              '🔧 REQUIRED ACTION: Add SHA-1 fingerprint 70:EF:49:70:E7:01:3B:AC:4D:DB:4F:71:1D:AF:37:CE:F8:BF:E4:BD to Firebase Console');
        } else if (e.toString().contains('SIGN_IN_FAILED')) {
          _error =
              'Google Sign-In configuration error. Please contact support.';
        } else if (e.toString().contains('network')) {
          _error = 'Network error. Please check your internet connection.';
        } else if (e.toString().contains('cancelled')) {
          _error = 'Sign-in was cancelled.';
        } else {
          _error =
              'Google Sign-In failed. Please try again or use email/password.';
        }
      }

      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> signOut() async {
    _isLoading = true;
    notifyListeners();
    await FirebaseService.signOut();
    _isLoading = false;
    notifyListeners();
  }

  Future<bool> handleActionAndConsumeCredit() async {
    if (isPro) {
      return true;
    }
    if ((_credits ?? 0) > 0) {
      if (_user == null) return false;
      final success = await CreditService.consumeCredit(_user!.uid);
      if (success) {
        _credits = (_credits ?? 1) - 1;
        _updateNativeServiceStatus();
        notifyListeners();
        return true;
      } else {
        _credits = await CreditService.getCredits(_user!.uid);
        _updateNativeServiceStatus();
        notifyListeners();
        return false;
      }
    } else {
      return false;
    }
  }

  // Consume a credit for an action (production method)
  Future<bool> consumeCredit() async {
    if (_user == null) {
      debugPrint('Cannot consume credit: no user logged in');
      return false;
    }

    if (_isPro) {
      // Pro users have unlimited credits
      return true;
    }

    if ((_credits ?? 0) <= 0) {
      debugPrint('Cannot consume credit: no credits remaining');
      return false;
    }

    try {
      final success = await FirebaseService.consumeCredit(_user!.uid);
      if (success) {
        _credits = (_credits ?? 0) - 1;
        await _updateNativeServiceStatus();
        notifyListeners();
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Error consuming credit: $e');
      return false;
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }

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

  // Method to refresh user's premium status from Firestore
  Future<void> refreshPremiumStatus() async {
    if (_user == null) return;
    try {
      final doc = await _firestore.collection('users').doc(_user!.uid).get();
      if (doc.exists) {
        final data = doc.data()!;
        _isPro = data['isPro'] ?? false;
        _planType = data['subscription']?['planType'] ?? 'free';

        // Check if subscription is still valid
        if (_isPro && data['subscription'] != null) {
          final expiryDate = data['subscription']['expiryDate'];
          if (expiryDate != null && _planType != 'onetime') {
            DateTime expiry;
            if (expiryDate is String) {
              expiry = DateTime.parse(expiryDate);
            } else if (expiryDate is Timestamp) {
              expiry = expiryDate.toDate();
            } else {
              expiry = DateTime.now()
                  .subtract(const Duration(days: 1)); // Force expiry
            }

            if (DateTime.now().isAfter(expiry)) {
              debugPrint(
                  '🔄 Subscription expired during refresh, updating status');
              await _handleExpiredSubscription();
              _isPro = false;
              _planType = 'free';
            }
          }
        }

        await _updateNativeServiceStatus();
        notifyListeners();
      }
    } catch (e) {
      debugPrint('Error refreshing premium status: $e');
    }
  }

  // Get plan display name for UI
  String getPlanDisplayName() {
    switch (_planType) {
      case 'monthly':
        return 'Monthly Pro';
      case 'yearly':
        return 'Yearly Pro';
      case 'onetime':
        return 'Lifetime Pro';
      default:
        return 'Free Plan';
    }
  }

  // Check if subscription is expiring soon (within 7 days)
  bool get isSubscriptionExpiringSoon {
    if (!_isPro || _planType == 'onetime') return false;

    // This would need subscription data to be loaded
    // Implementation depends on how you store subscription data
    return false; // Placeholder
  }

  // Get days until subscription expires
  int? get daysUntilExpiry {
    if (!_isPro || _planType == 'onetime') return null;

    // This would calculate days from subscription expiry date
    // Implementation depends on how you store subscription data
    return null; // Placeholder
  }

  // Get the payment portal URL for upgrading to Pro
  String getPaymentPortalUrl() {
    // Production URL for Rewordium payments
    return 'https://www.rewordium.tech/payments';
  }

  // Method to open payment portal (can be used with url_launcher)
  String getUpgradeUrl() {
    return getPaymentPortalUrl();
  }
}
