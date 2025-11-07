import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import '../../theme/app_theme.dart';

class PrivacyPolicyScreen extends StatelessWidget {
  const PrivacyPolicyScreen({super.key});

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
          'Privacy Policy',
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
              'Privacy Policy',
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
              title: '1. Introduction',
              content:
                  '''Welcome to Rewordium. We are committed to protecting your privacy and ensuring the security of your personal information. This Privacy Policy explains how we collect, use, store, and protect your data when you use our AI-powered writing assistant application.

Rewordium is designed with privacy in mind, processing text locally whenever possible and minimizing data collection to provide you with the best writing assistance experience.''',
            ),
            _buildSection(
              title: '2. Information We Collect',
              content: '''2.1 Account Information:
• Email address (for account creation and communication)
• Name (optional, for personalization)
• Authentication tokens (Google Sign-In integration)
• Account preferences and settings

2.2 Usage Data:
• App usage statistics and feature interactions
• Error logs and crash reports (anonymized)
• Performance metrics for service improvement
• Subscription and billing information

2.3 Text Processing Data:
• Text content you choose to process (temporarily, for AI analysis)
• User-selected writing personas and preferences
• Generated suggestions and improvements (not permanently stored)

2.4 Device Information:
• Device type, operating system, and version
• App version and installation details
• Accessibility service permissions and status''',
            ),
            _buildSection(
              title: '3. How We Use Your Information',
              content: '''We use your information exclusively to:
• Provide AI-powered text rewriting and enhancement services
• Maintain and improve your user account
• Process subscription payments and manage billing
• Send important service updates and communications
• Analyze usage patterns to improve our services
• Provide customer support and technical assistance
• Ensure security and prevent fraudulent activities

We do not use your personal information for advertising or marketing to third parties.''',
            ),
            _buildSection(
              title: '4. Data Processing and AI Services',
              content: '''4.1 Local Processing:
• Most text processing occurs directly on your device
• Accessibility service operates locally for privacy protection
• No text content is transmitted unnecessarily to external servers

4.2 Cloud AI Processing:
• Complex AI operations may require secure cloud processing
• Text is encrypted during transmission and processing
• Processed text is immediately deleted after generating suggestions
• We use enterprise-grade AI services (OpenAI, Groq) with strict privacy controls

4.3 Data Retention:
• Text content is not permanently stored on our servers
• Account information is retained until account deletion
• Usage analytics are aggregated and anonymized after 90 days''',
            ),
            _buildSection(
              title: '5. Accessibility Service Privacy',
              content: '''Rewordium's accessibility service:
• Only activates when you explicitly request text assistance
• Processes text locally on your device when possible
• Does not continuously monitor or record your activities
• Can be disabled at any time through device settings
• Only accesses text in supported applications with your permission
• Does not store or transmit personal conversations or sensitive information''',
            ),
            _buildSection(
              title: '6. Data Sharing and Third Parties',
              content:
                  '''We do not sell, rent, or share your personal information with third parties except:

6.1 Service Providers:
• Cloud AI services for text processing (with privacy agreements)
• Payment processors for subscription management
• Analytics services (with anonymized data only)
• Customer support platforms

6.2 Legal Requirements:
• When required by law or legal process
• To protect our rights, property, or safety
• To prevent fraud or security threats

All third-party services are carefully vetted for privacy compliance.''',
            ),
            _buildSection(
              title: '7. Data Security',
              content: '''We implement comprehensive security measures:
• End-to-end encryption for sensitive data transmission
• Secure cloud infrastructure with regular security audits
• Limited access controls for employee data access
• Regular security updates and vulnerability assessments
• Secure authentication and session management
• Automatic logout and session expiration
• Device-level security recommendations for users''',
            ),
            _buildSection(
              title: '8. Your Privacy Rights',
              content: '''You have the right to:
• Access your personal information and account data
• Correct or update inaccurate information
• Delete your account and associated data
• Export your account information
• Withdraw consent for data processing
• Disable the accessibility service at any time
• Request information about data processing activities

To exercise these rights, contact us at noxquilltech@gmail.com''',
            ),
            _buildSection(
              title: '9. Children\'s Privacy',
              content:
                  '''Rewordium is not intended for users under 13 years of age. We do not knowingly collect personal information from children under 13. If we discover that we have collected information from a child under 13, we will delete that information immediately.

Parents and guardians should monitor their children's online activities and app usage.''',
            ),
            _buildSection(
              title: '10. International Data Transfers',
              content:
                  '''Your information may be processed and stored in countries other than your own. We ensure that:
• All data transfers comply with applicable privacy laws
• Adequate protection measures are in place
• Third-party processors meet international privacy standards
• You are informed of any cross-border data processing

We primarily use servers located in secure, privacy-compliant jurisdictions.''',
            ),
            _buildSection(
              title: '11. Cookies and Tracking',
              content: '''Rewordium uses minimal tracking technologies:
• Essential cookies for app functionality and user sessions
• Analytics cookies to understand app performance (anonymized)
• No advertising or tracking cookies
• No cross-app or cross-site tracking
• Users can disable analytics through app settings

We prioritize functionality over data collection.''',
            ),
            _buildSection(
              title: '12. Privacy Policy Updates',
              content:
                  '''We may update this Privacy Policy occasionally to reflect:
• Changes in our data practices
• New features or services
• Legal or regulatory requirements
• User feedback and suggestions

When we make significant changes:
• Users will be notified through the app
• Updated policy will be posted with revision date
• Continued use implies acceptance of changes
• Users may delete their accounts if they disagree with updates''',
            ),
            _buildSection(
              title: '13. Contact Us',
              content: '''For privacy-related questions, concerns, or requests:

Email: noxquilltech@gmail.com
Data Protection Officer: noxquilltech@gmail.com
Support: noxquilltech@gmail.com
Website: rewordium.tech

We are committed to addressing privacy concerns promptly and transparently. Responses are typically provided within 48 hours.''',
            ),
            _buildSection(
              title: '14. Regional Privacy Rights',
              content: '''14.1 GDPR (European Union):
EU users have additional rights under GDPR, including data portability, right to be forgotten, and the right to object to processing.

14.2 CCPA (California):
California residents have rights to know about personal information collection, deletion rights, and opt-out rights.

14.3 Other Jurisdictions:
We comply with applicable privacy laws in all regions where Rewordium is available.''',
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
              child: Column(
                children: [
                  Icon(
                    CupertinoIcons.shield_fill,
                    color: AppTheme.primaryColor,
                    size: 32,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Your Privacy Matters',
                    style: AppTheme.headingSmall.copyWith(
                      fontWeight: FontWeight.w700,
                      color: AppTheme.primaryColor,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'We are committed to protecting your privacy and being transparent about our data practices. If you have any questions or concerns, please don\'t hesitate to contact us.',
                    style: AppTheme.bodyMedium.copyWith(
                      color: AppTheme.primaryColor,
                      height: 1.5,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
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
