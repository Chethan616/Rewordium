import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_sign_in/google_sign_in.dart';

class AdminPanel extends StatefulWidget {
  const AdminPanel({Key? key}) : super(key: key);

  @override
  _AdminPanelState createState() => _AdminPanelState();
}

class _AdminPanelState extends State<AdminPanel> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _bodyController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false;
  bool _isSignedIn = false;
  bool _isAuthenticated = false;
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn();

  // Backend server URL - use your computer's local IP address for mobile testing
  // Example: 'http://192.168.1.100:3000' (replace with your actual IP)
  static const String backendUrl = 'http://172.17.92.238:8080';

  // Admin password for authentication
  static const String adminPassword = 'sendpushnotis';

  @override
  void dispose() {
    _titleController.dispose();
    _bodyController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _checkPassword() async {
    if (_passwordController.text == adminPassword) {
      setState(() {
        _isAuthenticated = true;
      });
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Incorrect password'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _signInWithGoogle() async {
    try {
      // Sign out first to ensure a clean state
      await _googleSignIn.signOut();
      await _auth.signOut();

      // Start the sign in flow
      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();
      if (googleUser == null) {
        throw Exception('Sign in was canceled');
      }

      // Obtain the auth details from the request
      final GoogleSignInAuthentication googleAuth =
          await googleUser.authentication;

      if (googleAuth.idToken == null) {
        throw Exception('Failed to get ID token from Google');
      }

      // Create a new credential
      final AuthCredential credential = GoogleAuthProvider.credential(
        accessToken: googleAuth.accessToken,
        idToken: googleAuth.idToken,
      );

      // Once signed in, sign in to Firebase with the credential
      final UserCredential userCredential =
          await _auth.signInWithCredential(credential);

      // Check if user is signed in
      if (userCredential.user != null) {
        // Here you should add your admin check logic
        // For example, check if the user's email is in your admin list

        setState(() {
          _isSignedIn = true;
        });

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Successfully signed in as admin'),
              backgroundColor: Colors.green,
            ),
          );
        }

        // Print user info for debugging
        print('Signed in as: ${userCredential.user!.email}');
        print('User UID: ${userCredential.user!.uid}');
      } else {
        throw Exception('Failed to sign in to Firebase');
      }
    } catch (e) {
      print('Error in _signInWithGoogle: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to sign in: ${e.toString()}'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
      }

      // Reset sign in state on error
      setState(() {
        _isSignedIn = false;
      });

      // Re-throw the error to be handled by the caller if needed
      rethrow;
    }
  }

  Future<void> _signOut() async {
    try {
      // Sign out from Google and Firebase
      await _googleSignIn.signOut();
      await _auth.signOut();

      // Update the UI state
      setState(() {
        _isSignedIn = false;
      });

      // Show success message
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Successfully signed out'),
            backgroundColor: Colors.green,
          ),
        );
      }

      print('User signed out successfully');
    } catch (e) {
      print('Error signing out: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error signing out: ${e.toString()}'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
      }
    }
  }

  Future<void> _sendNotification() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
    });

    try {
      // Ensure user is signed in
      if (!_isSignedIn) {
        await _signInWithGoogle();
        if (!_isSignedIn) {
          throw Exception('Please sign in to send notifications');
        }
      }

      print('Sending notification via backend API');

      // Prepare the notification payload
      final Map<String, dynamic> payload = {
        'title': _titleController.text,
        'body': _bodyController.text,
        'topic': 'all_users',
      };

      print('Sending request to backend: ${jsonEncode(payload)}');

      // Send the notification using our secure backend
      final response = await http.post(
        Uri.parse('$backendUrl/api/send-notification'),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer $adminPassword',
        },
        body: jsonEncode(payload),
      );

      print('Backend Response: ${response.statusCode} - ${response.body}');

      final responseData = jsonDecode(response.body);

      if (response.statusCode == 200 && responseData['success'] == true) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Notification sent successfully!'),
              backgroundColor: Colors.green,
              duration: Duration(seconds: 3),
            ),
          );
        }
        _titleController.clear();
        _bodyController.clear();
      } else {
        final errorMessage =
            responseData['error'] ?? 'Failed to send notification';
        print('Backend Error: $errorMessage');
        throw Exception(errorMessage);
      }
    } catch (e) {
      print('Error sending notification: $e');
      String errorMessage = 'Failed to send notification';

      // Check for connection timeout or refused errors
      if (e is SocketException) {
        errorMessage = 'Rewordium notification server is offline';
      } else if (e.toString().contains('timed out')) {
        errorMessage = 'Connection to notification server timed out';
      } else if (e is ClientException) {
        errorMessage = 'Could not connect to the notification server';
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isAuthenticated) {
      return Scaffold(
        appBar: AppBar(title: const Text('Admin Login')),
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text(
                'Enter Admin Password',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 20),
              TextField(
                controller: _passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Password',
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _checkPassword(),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _checkPassword,
                child: const Text('Submit'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Panel'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              setState(() {
                _isAuthenticated = false;
                _isSignedIn = false;
                _passwordController.clear();
              });
            },
            tooltip: 'Logout',
          ),
        ],
      ),
      body: _isSignedIn ? _buildAdminForm() : _buildSignInButton(),
    );
  }

  Widget _buildSignInButton() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text(
            'Admin Sign In',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 20),
          ElevatedButton.icon(
            onPressed: _signInWithGoogle,
            icon: Image.asset(
              'assets/images/google_logo.png',
              height: 24,
            ),
            label: const Text('Sign in with Google'),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAdminForm() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Send Notification to All Users',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            TextFormField(
              controller: _titleController,
              decoration: const InputDecoration(
                labelText: 'Notification Title',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.title),
              ),
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Please enter a title';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _bodyController,
              decoration: const InputDecoration(
                labelText: 'Notification Message',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.message),
              ),
              maxLines: 4,
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Please enter a message';
                }
                return null;
              },
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _isLoading ? null : _sendNotification,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                textStyle: const TextStyle(fontSize: 16),
              ),
              child: _isLoading
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Text('Send Notification'),
            ),
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 16),
            const Text(
              'Instructions:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 8),
            _buildInstructionItem(
                '1. Enter a title and message for your notification'),
            _buildInstructionItem(
                '2. Click "Send Notification" to broadcast to all users'),
            _buildInstructionItem(
                '3. Users will receive the notification in real-time'),
          ],
        ),
      ),
    );
  }

  Widget _buildInstructionItem(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('• '),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}
