import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import '../../theme/app_theme.dart';

class TermsConditionsScreen extends StatelessWidget {
  const TermsConditionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
        leading: IconButton(
          icon: Icon(
            Icons.arrow_back_ios_new_rounded,
            color: AppTheme.textPrimaryColor,
            size: 20,
          ),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Terms & Conditions',
          style: AppTheme.headingMedium.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Terms and Conditions',
              style: AppTheme.headingLarge.copyWith(
                fontWeight: FontWeight.w900,
                color: AppTheme.primaryColor,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Last updated: ${DateTime.now().toString().split(' ')[0]}',
              style: AppTheme.bodySmall.copyWith(
                color: AppTheme.textSecondaryColor,
              ),
            ),
            const SizedBox(height: 24),
            _buildSection(
              title: '1. Acceptance of Terms',
              content:
                  '''By downloading, installing, or using Rewordium ("the App"), you agree to be bound by these Terms and Conditions. If you do not agree to these terms, please do not use the App.

Rewordium is an AI-powered writing assistant that helps users rewrite, enhance, and improve their text content through artificial intelligence and natural language processing.''',
            ),
            _buildSection(
              title: '2. Description of Service',
              content: '''Rewordium provides the following services:
• AI-powered text rewriting and enhancement
• Grammar and style suggestions
• Text paraphrasing and rephrasing
• Writing tone adjustment (Casual, Academic, Poetry, Custom personas)
• Real-time accessibility service for text improvement
• Keyboard integration for seamless writing assistance

The App uses advanced AI models to analyze and improve your text while maintaining the original meaning and intent.''',
            ),
            _buildSection(
              title: '3. User Accounts and Registration',
              content:
                  '''To access certain features of Rewordium, you may need to create an account. You agree to:
• Provide accurate and complete information during registration
• Maintain the security of your account credentials
• Notify us immediately of any unauthorized use of your account
• Accept responsibility for all activities that occur under your account

You may register using your email address or through Google Sign-In integration.''',
            ),
            _buildSection(
              title: '4. Acceptable Use Policy',
              content:
                  '''You agree to use Rewordium only for lawful purposes and in accordance with these Terms. You shall not:
• Use the App to create harmful, offensive, or illegal content
• Attempt to reverse engineer, modify, or distribute the App
• Use the service to violate any applicable laws or regulations
• Submit content that infringes on intellectual property rights
• Use the App to generate spam, malware, or malicious content
• Attempt to bypass any usage limits or restrictions''',
            ),
            _buildSection(
              title: '5. Accessibility Service',
              content:
                  '''Rewordium includes an accessibility service that helps improve text across various applications on your device. By enabling this service:
• You grant permission for the App to read and modify text in supported applications
• The service operates locally on your device for privacy protection
• You can disable the accessibility service at any time through device settings
• The service only processes text when explicitly requested by you''',
            ),
            _buildSection(
              title: '6. Subscription and Payments',
              content:
                  '''Rewordium offers both free and premium subscription tiers:
• Free users receive limited daily credits for text processing
• Premium subscribers enjoy unlimited usage and advanced features
• Subscriptions and payments are processed through our secure web portal
• Payment processing is handled by third-party payment providers (Stripe/Razorpay)
• Subscriptions automatically renew unless cancelled through the web portal
• Refunds may be available according to payment provider policies
• Pricing may change with advance notice to existing subscribers
• The app does not directly process payments to comply with platform policies''',
            ),
            _buildSection(
              title: '7. Intellectual Property',
              content:
                  '''The Rewordium App, including its design, functionality, and underlying technology, is owned by us and protected by intellectual property laws. While you retain rights to your original content, our AI-generated suggestions and improvements are provided as a service.

You grant us a limited license to process your text for the purpose of providing our services.''',
            ),
            _buildSection(
              title: '8. Data Privacy and Security',
              content:
                  '''We are committed to protecting your privacy and data security:
• Text processing occurs locally when possible to minimize data transmission
• We implement industry-standard security measures
• Your personal information is handled according to our Privacy Policy
• You can delete your account and associated data at any time
• We do not store your text content longer than necessary to provide the service''',
            ),
            _buildSection(
              title: '9. Service Availability',
              content:
                  '''We strive to maintain high service availability, but cannot guarantee uninterrupted access. The service may be temporarily unavailable due to:
• Scheduled maintenance and updates
• Technical difficulties or server issues
• Third-party service dependencies
• Force majeure events beyond our control

We will make reasonable efforts to notify users of planned maintenance.''',
            ),
            _buildSection(
              title: '10. Limitation of Liability',
              content:
                  '''To the maximum extent permitted by law, Rewordium and its developers shall not be liable for:
• Any indirect, incidental, or consequential damages
• Loss of data, profits, or business opportunities
• Damages resulting from use or inability to use the service
• Any content generated by the AI that may be inaccurate or inappropriate

Your sole remedy for dissatisfaction with the service is to stop using the App.''',
            ),
            _buildSection(
              title: '11. AI Content Disclaimer',
              content:
                  '''Rewordium uses artificial intelligence to generate text suggestions and improvements. Please note:
• AI-generated content may not always be accurate or appropriate
• Users are responsible for reviewing and validating all AI suggestions
• The AI may occasionally produce unexpected or unintended results
• We do not guarantee the accuracy, completeness, or reliability of AI-generated content
• Users should exercise judgment when using AI suggestions in professional or academic contexts''',
            ),
            _buildSection(
              title: '12. Updates and Modifications',
              content:
                  '''We reserve the right to modify these Terms and Conditions at any time. When we make changes:
• Updated terms will be posted in the App
• Users will be notified of significant changes
• Continued use of the App constitutes acceptance of new terms
• If you disagree with changes, you may discontinue using the service''',
            ),
            _buildSection(
              title: '13. Termination',
              content: '''Either party may terminate this agreement at any time:
• You may stop using the App and delete your account
• We may suspend or terminate accounts for violations of these terms
• Upon termination, your right to use the App ceases immediately
• Certain provisions of these terms will survive termination''',
            ),
            _buildSection(
              title: '14. Contact Information',
              content:
                  '''If you have questions about these Terms and Conditions, please contact us at:

Email: noxquilltech@gmail.com
Website: rewordium.tech

We will respond to inquiries within 48 hours during business days.''',
            ),
            const SizedBox(height: 32),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.primaryColor.withOpacity(0.1),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: AppTheme.primaryColor.withOpacity(0.2),
                ),
              ),
              child: Text(
                'By using Rewordium, you acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions.',
                style: AppTheme.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppTheme.primaryColor,
                ),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildSection({required String title, required String content}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: AppTheme.headingSmall.copyWith(
            fontWeight: FontWeight.w700,
            color: AppTheme.primaryColor,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          content,
          style: AppTheme.bodyMedium.copyWith(
            height: 1.6,
            color: AppTheme.textPrimaryColor,
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}
