import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

class PrivacyPolicyScreen extends StatelessWidget {
  const PrivacyPolicyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
        elevation: 0,
        leading: IconButton(
          icon: Icon(
            Icons.arrow_back_ios_new_rounded,
            color: Theme.of(context).colorScheme.onSurface,
            size: 20,
          ),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Privacy Policy',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
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
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                    color: Theme.of(context).colorScheme.primary,
                  ),
            ),
            const SizedBox(height: 8),
            Text(
              'Last updated: May 18, 2026',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 24),
            
            _buildSection(
              context: context,
              title: '🚨 Android Accessibility Service API Disclosure',
              content:
                  '''Rewordium integrates Android’s standard AccessibilityService API to provide its core real-time AI writing companion features. This integration is optional but critical for the floating Assistant Bubble to function.

Scope and Purpose:
• Inline Text Reading: When you actively focus a text input field in a third-party application and tap the floating Rewordium Assistant Bubble, the service safely reads the active draft inside that input field.
• Context-Aware Replied Suggestion: If you tap the Assistant Bubble when your active text input box is completely blank, the service contextually scans surrounding visible page elements (such as an incoming email subject or previous chat line) strictly to draft a relevant response.
• Direct Inline Text Replacement: After our secure AI completion completes, the service automatically replaces your active draft in the text field with the refined, polished version. This eliminates manual copy-pasting.

Operational Limitations & Boundaries:
• Automatic Password Suspension: The Accessibility Service automatically suspends all monitoring, reading, and AI suggest layers whenever a secure password, credit card, or pin entry input field is active on your device.
• No Background Surveillance: The Accessibility Service does not run passive background tracking. It does not monitor apps, catalog browsing history, inspect personal photo files, capture device screens, or record calls.
• Opt-In Control: Enabling the Accessibility Service is 100% voluntary. You must explicitly authorize it in Android Settings (Settings > Accessibility > Installed Services > Rewordium). You can revoke this permission at any moment.''',
            ),
            
            _buildSection(
              context: context,
              title: '1. Zero-Data-Collection & Privacy-First Architecture',
              content:
                  '''Rewordium is engineered on a strict privacy-by-design framework. Our business model does not rely on gathering, profiling, mining, or selling user typing content, communications, or personal metadata.

• No Telemetry Keylogging: Our keyboard input engine runs completely locally in offline RAM. We do not maintain any logs of your keystrokes, personal chats, custom dictionaries, or search queries.
• Smart Clipboard Privacy: The clipboard recommendation panel processes copied text segments strictly locally. It automatically ignores and discards passwords, security pins, credit card details, and raw phone numbers from showing up as suggestions.''',
            ),
            
            _buildSection(
              context: context,
              title: '2. Information We Collect and Process',
              content:
                  '''To run account-based billing, standard credit daily allocations, and active security systems, we process the following categories of data:

2.1 Account and Security Information:
• Authentication Credentials: Secure sign-in tokens generated via Google Sign-In or Firebase Authentication.
• Profile Data: Your email address, account identifier, and billing status.
• Account Preferences: Personal parameters, including custom writer personas, saved settings, and theme choices.

2.2 Billing & Subscription Data:
• Transaction Logs: Secure subscription statuses, transaction IDs, and credit tallies. All credit card billing details are processed securely and exclusively by Google Play Billing; Rewordium never collects, views, or stores financial card details on its servers.

2.3 Transient Text Processing Data (Stateless Cloud Transit):
• Draft Refinements: When you submit a block of text for Tone Shift, Summarization, Translation, or Grammar Check using our default cloud AI, the raw draft is securely transmitted over HTTPS.
• Stateless RAM Processing: The draft is processed transiently inside secure, volatile RAM. The text is immediately deleted upon completion and is never archived in databases, logged to disk, or utilized to retrain AI language models.

2.4 Secure Local Storage (Encryption Specs):
• AES-256 Key Encryption: Your private API keys (OpenAI, Google Gemini, Anthropic Claude, custom endpoints) and custom settings are stored locally on your device's secure hardware partition.
• Keystore / Keychain Integration: We utilize FlutterSecureStorage interfacing directly with the device's hardware-backed Android Keystore (and iOS Keychain on Apple platforms) via 256-bit AES cryptographic encryption. This data never touches our cloud servers.''',
            ),
            
            _buildSection(
              context: context,
              title: '3. Data Sovereignty & AI Infrastructure (Qwen via Groq)',
              content:
                  '''Many users are concerned about where their private writing transits when using modern language models. We have implemented strict geographical and infrastructural boundaries to guarantee your security:

• Default Cloud AI Engine: Rewordium's standard cloud writing refinements utilize the state-of-the-art open-source Qwen 3 (32B) model.
• Western Infrastructure Isolation (Groq): 100% of these default model completions are routed directly through Groq’s secure, ultra-low latency server networks located in the United States.
• No Chinese Endpoints: We do not use, transit through, or connect to any Chinese domestic endpoints, Alibaba Cloud networks, or third-party servers subject to foreign surveillance. Your text remains fully protected under secure Western data frameworks.''',
            ),
            
            _buildSection(
              context: context,
              title: '4. Bring Your Own Key (BYOK) Data Processing',
              content:
                  '''If you enable Bring Your Own Key (BYOK) in the advanced settings to bypass standard daily credit caps:
• Direct Secure Dispatch: Your custom API keys are loaded locally from your encrypted device Keystore and dispatched directly over secure HTTPS tunnels to the respective provider's endpoints (e.g. api.openai.com, generativelanguage.googleapis.com, or api.anthropic.com).
• No Intermediary Transit: Your custom keys and text drafts completely bypass Rewordium's servers in BYOK mode, transiting directly to your selected provider.
• Billing Responsibility: All interactions under BYOK mode are governed strictly by the respective third-party provider's privacy policies and terms of service. You are solely responsible for reviewing and managing your API key usage logs.''',
            ),
            
            _buildSection(
              context: context,
              title: '5. Tampering, Safety, & APK Integrity Safeguards',
              content:
                  '''To prevent malicious modified copies of Rewordium (which could easily have spyware or keyloggers injected by bad actors on unofficial forums) from harvesting your typing inputs:
• Signature Integrity Locking: Rewordium contains cryptographic installation checks that cross-reference the app's signature against official store certificates.
• Automated Disabling: If modified, cracked, or tampered APK packages are detected, the Service immediately stops running and locks all features to safeguard your device's keystream.
• Third-Party Telemetry: In modified copies, all privacy guarantees are null and void. We strongly advise users to only install the App from the official Google Play Store.''',
            ),
            
            _buildSection(
              context: context,
              title: '6. Open Source Software',
              content:
                  '''Rewordium incorporates open source software components:

• FlorisBoard: The keyboard component is based on FlorisBoard, an open-source Android keyboard by Patrick Goldinger and contributors, licensed under Apache License 2.0.

Open source components are used in accordance with their respective licenses. The use of these components does not affect your privacy rights. Open source code processes data locally on your device and does not transmit data independently of the App.

For a full list of open source licenses, see Settings > Open Source Licenses within the App.''',
            ),
            
            _buildSection(
              context: context,
              title: '7. Data Security Measures',
              content:
                  '''We implement comprehensive security measures:
• End-to-end encryption for sensitive data transmission (TLS/HTTPS).
• Secure cloud infrastructure with role-based credential restrictions.
• Regular security updates and vulnerability assessments.
• Device-level secure authentication and hardware keystore encryption.
• Anonymized aggregated analytics logs deleted automatically after 90 days.''',
            ),
            
            _buildSection(
              context: context,
              title: '8. Your Privacy Rights & Control',
              content:
                  '''You have the right to:
• Access your personal information and account data.
• Correct or update inaccurate information.
• Delete your account and associated data.
• Withdraw consent for data processing.
• Disable the accessibility service at any time.
• Purge your private keys from the Keystore.

To exercise these rights, contact us at noxquilltech@gmail.com''',
            ),
            
            _buildSection(
              context: context,
              title: '9. Children\'s Privacy',
              content:
                  '''Rewordium is not intended for users under the minimum age required by applicable laws in their region. We do not knowingly collect personal information from children who do not meet the minimum age requirement. If we discover that we have collected information from a user below the applicable age threshold, we will delete that information immediately.''',
            ),
            
            _buildSection(
              context: context,
              title: 'No Professional Advice Disclaimer',
              content:
                  '''AI-generated content is provided for assistance purposes only and should not be considered professional, legal, academic, medical, or financial advice. Users are responsible for reviewing and validating AI-generated suggestions before use.''',
            ),
            
            _buildSection(
              context: context,
              title: '10. Regional Privacy Rights',
              content:
                  '''10.1 GDPR (European Union):
EU users have additional rights under GDPR, including data portability, right to be forgotten, and the right to object to processing. The lawful basis for transient text processing is the performance of a contract.

10.2 CCPA (California):
California residents have rights to know about personal information collection, deletion rights, and are protected under a "Do Not Sell My Info" guarantee. Rewordium sells zero user data.

10.3 Other Jurisdictions:
We comply with applicable privacy laws in all regions where Rewordium is available.''',
            ),
            
            _buildSection(
              context: context,
              title: '11. Contact Us',
              content:
                  '''For privacy-related questions, concerns, or requests:

Email: noxquilltech@gmail.com
Data Protection Officer: noxquilltech@gmail.com
Support: noxquilltech@gmail.com
Website: rewordium.tech

We are committed to addressing privacy concerns promptly and transparently. Responses are typically provided within 48 hours.''',
            ),
            
            const SizedBox(height: 32),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Theme.of(context)
                    .colorScheme
                    .primary
                    .withAlpha(25),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: Theme.of(context)
                      .colorScheme
                      .primary
                      .withAlpha(51),
                ),
              ),
              child: Column(
                children: [
                  Icon(
                    CupertinoIcons.shield_fill,
                    color: Theme.of(context).colorScheme.primary,
                    size: 32,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Your Privacy Matters',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'We are committed to protecting your privacy and being transparent about our data practices. If you have any questions or concerns, please don\'t hesitate to contact us.',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context).colorScheme.primary,
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

  Widget _buildSection(
      {required BuildContext context,
      required String title,
      required String content}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
                color: Theme.of(context).colorScheme.primary,
              ),
        ),
        const SizedBox(height: 12),
        Text(
          content,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                height: 1.6,
                color: Theme.of(context).colorScheme.onSurface,
              ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}
