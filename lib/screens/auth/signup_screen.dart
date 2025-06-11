import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';

import '../../providers/auth_provider.dart';
import '../../theme/app_theme.dart';
import '../../widgets/custom_button.dart';

class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _confirmPasswordController =
      TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _passwordVisible = false;
  bool _confirmPasswordVisible = false;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  // Name validation
  String? _validateName(String? value) {
    if (value == null || value.isEmpty) {
      return 'Please enter your name';
    }
    return null;
  }

  // Email validation
  String? _validateEmail(String? value) {
    if (value == null || value.isEmpty) {
      return 'Please enter your email';
    }
    final emailRegex = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$');
    if (!emailRegex.hasMatch(value)) {
      return 'Please enter a valid email';
    }
    return null;
  }

  // Password validation
  String? _validatePassword(String? value) {
    if (value == null || value.isEmpty) {
      return 'Please enter your password';
    }
    if (value.length < 6) {
      return 'Password must be at least 6 characters';
    }
    return null;
  }

  // Confirm password validation
  String? _validateConfirmPassword(String? value) {
    if (value == null || value.isEmpty) {
      return 'Please confirm your password';
    }
    if (value != _passwordController.text) {
      return 'Passwords do not match';
    }
    return null;
  }

  // Handle sign up
  Future<void> _signUp() async {
    if (_formKey.currentState!.validate()) {
      final authProvider = Provider.of<AuthProvider>(context, listen: false);
      final success = await authProvider.signUpWithEmailAndPassword(
        _emailController.text.trim(),
        _passwordController.text,
        _nameController.text.trim(),
      );

      if (!success && mounted && authProvider.error != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(authProvider.error!)),
        );
      } else if (success && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Account created successfully!')),
        );
        Navigator.pop(context); // Go back to login screen
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    final isLoading = authProvider.isLoading;

    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        iconTheme: IconThemeData(color: AppTheme.textPrimaryColor),
        title: Text(
          'Create Account',
          style: AppTheme.headingSmall,
        ),
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Name field
                TextFormField(
                  controller: _nameController,
                  validator: _validateName,
                  keyboardType: TextInputType.name,
                  textCapitalization: TextCapitalization.words,
                  style: AppTheme.bodyMedium,
                  decoration: InputDecoration(
                    labelText: 'Full Name',
                    labelStyle: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.textSecondaryColor,
                    ),
                    prefixIcon: Icon(
                      CupertinoIcons.person,
                      color: AppTheme.primaryColor,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor.withOpacity(0.3),
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor,
                        width: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Email field
                TextFormField(
                  controller: _emailController,
                  validator: _validateEmail,
                  keyboardType: TextInputType.emailAddress,
                  style: AppTheme.bodyMedium,
                  decoration: InputDecoration(
                    labelText: 'Email',
                    labelStyle: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.textSecondaryColor,
                    ),
                    prefixIcon: Icon(
                      CupertinoIcons.mail,
                      color: AppTheme.primaryColor,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor.withOpacity(0.3),
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor,
                        width: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Password field
                TextFormField(
                  controller: _passwordController,
                  validator: _validatePassword,
                  obscureText: !_passwordVisible,
                  style: AppTheme.bodyMedium,
                  decoration: InputDecoration(
                    labelText: 'Password',
                    labelStyle: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.textSecondaryColor,
                    ),
                    prefixIcon: Icon(
                      CupertinoIcons.lock,
                      color: AppTheme.primaryColor,
                    ),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _passwordVisible
                            ? CupertinoIcons.eye_slash
                            : CupertinoIcons.eye,
                        color: AppTheme.textSecondaryColor,
                      ),
                      onPressed: () {
                        setState(() {
                          _passwordVisible = !_passwordVisible;
                        });
                      },
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor.withOpacity(0.3),
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor,
                        width: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Confirm password field
                TextFormField(
                  controller: _confirmPasswordController,
                  validator: _validateConfirmPassword,
                  obscureText: !_confirmPasswordVisible,
                  style: AppTheme.bodyMedium,
                  decoration: InputDecoration(
                    labelText: 'Confirm Password',
                    labelStyle: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.textSecondaryColor,
                    ),
                    prefixIcon: Icon(
                      CupertinoIcons.lock,
                      color: AppTheme.primaryColor,
                    ),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _confirmPasswordVisible
                            ? CupertinoIcons.eye_slash
                            : CupertinoIcons.eye,
                        color: AppTheme.textSecondaryColor,
                      ),
                      onPressed: () {
                        setState(() {
                          _confirmPasswordVisible = !_confirmPasswordVisible;
                        });
                      },
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor.withOpacity(0.3),
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(
                        color: AppTheme.primaryColor,
                        width: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                // Sign up button
                CustomButton(
                  text: 'Create Account',
                  onPressed: isLoading ? null : _signUp,
                  isLoading: isLoading,
                  type: ButtonType.primary,
                ),
                const SizedBox(height: 24),

                // Login option
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Already have an account?',
                      style: AppTheme.bodyMedium,
                    ),
                    TextButton(
                      onPressed: () {
                        Navigator.pop(context);
                      },
                      child: Text(
                        'Login',
                        style: AppTheme.bodyMedium.copyWith(
                          color: AppTheme.primaryColor,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
