import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

class TermsConditionsScreen extends StatelessWidget {
  const TermsConditionsScreen({super.key});

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
          'Terms & Conditions',
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
              'Terms and Conditions',
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
              title: '1. Acceptance of Terms',
              content:
                  '''Welcome to Rewordium. These Terms and Conditions ("Terms" or "Agreement") constitute a legally binding contract between you ("User," "you," or "your") and Noxquill Tech ("we," "our," "us," or "Developers") governing your access to and use of the Rewordium mobile application, custom keyboard, floating AI Assistant Bubble, and all associated services, websites, and APIs.

By downloading, installing, registering an account, enabling keyboard or accessibility permissions, or interacting with the Service, you explicitly acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions and our Privacy Policy. If you do not agree to these Terms, you must immediately deactivate, uninstall, and cease all use of our Service.''',
            ),
            
            _buildSection(
              context: context,
              title: '2. Bring Your Own Key (BYOK) Agreement',
              content:
                  '''To accommodate advanced writing loads and developers, Rewordium provides a Bring Your Own Key (BYOK) feature inside the settings drawer. By inputting your personal API keys (Google Gemini, OpenAI, Anthropic Claude, or custom OpenAI-compatible endpoints):

2.1 Billing and Charge Waiver:
• Credit Exemption: Active BYOK connections bypass all standard daily credit refill limitations. You will not be charged credits by Rewordium for writing processing.
• Direct Developer Billing: All generations under BYOK are billed directly and exclusively by the third-party providers (e.g. OpenAI, Google, or Anthropic) to your private developer account.
• Absolute Financial Exemption: You acknowledge that you are solely and fully responsible for any charges, fees, rate caps, API costs, or financial transactions incurred on your third-party provider accounts. Noxquill Tech is completely exempt from all financial liabilities and will not reimburse, credit, or offset any third-party developer bills.

2.2 Provider Terms Compliance & Liability:
• You represent and warrant that your inputted API keys are lawfully obtained and belong exclusively to you.
• You agree to comply strictly with all third-party developer terms (including OpenAI Service Terms, Google Gemini terms of service, and Anthropic Developer Agreements).
• Service Suspensions: We hold zero control over your third-party accounts. We are not liable for any API key suspensions, billing freezes, account terminations, or rate-limiting enforced on your profiles by those providers.

2.3 Secure Local Key Storage:
• Your API keys are saved strictly locally on your device's secure hardware partition. We use hardware-backed local keystores (Android Keystore / iOS Keychain) with AES-256 encryption via FlutterSecureStorage.''',
            ),
            
            _buildSection(
              context: context,
              title: '3. Data Sovereignty & Infrastructure Disclosures (Qwen via Groq)',
              content:
                  '''To guarantee text data protection compliance and sovereignty:
• US Infrastructure (Groq): The default cloud language model utilized by Rewordium is the open-source Qwen 3 (32B). You agree and acknowledge that 100% of these default completions are routed exclusively through Groq’s secure, US-based server networks.
• No Chinese Endpoints: We guarantee that standard writing requests never transit through or contact Chinese domestic networks, servers, or Chinese government-governed API endpoints. Your data remains fully protected under secure Western frameworks.
• Stateless Transit: You acknowledge and agree that default completions are processed statelessly inside secure, volatile RAM and deleted immediately upon output. No permanent logging or database archives are maintained.''',
            ),
            
            _buildSection(
              context: context,
              title: '4. Android Permissions & Virtual Inputs',
              content:
                  '''By activating Rewordium's custom keyboard or floating Accessibility Bubble:
• System Warning Acknowledgment: You acknowledge that Android displays standard security warnings when activating any third-party keyboard. You explicitly waive all liability in connection with these boilerplate operating system prompts.
• Keystroke Log Protection: We represent that the keyboard typing loop runs strictly offline in local RAM and does not log, track, or stream your keystrokes or private conversations.
• Accessibility Limits: The Accessibility Service only reads and modifies text inside the active focused text input field (or contextually reads surrounding visual text if the active field is blank, to suggest replies) when you actively tap our bubble. The Service automatically suspends suggestions whenever a secure password or pin entry input field is active.''',
            ),
            
            _buildSection(
              context: context,
              title: '5. Tampering, Reverse Engineering, & Signature Locks',
              content:
                  '''To protect users against modified copies of Rewordium which could be repackaged with spyware or keylogger payloads:
• Anti-Tampering Integrity: Rewordium contains cryptographic signature verification modules that automatically lock and disable the application if it detects a modified, cracked, or unauthorized APK package.
• Prohibited Actions: You are strictly prohibited from:
  1. Reverse engineering, decompiling, disassembling, or attempting to extract the source code of the Application.
  2. Modifying, bypassing, cracking, or disabling the cryptographic signature integrity check modules.
  3. Distributing modified, cracked, or unauthorized copies of the Application.
• Liability Exemption: You assume full and exclusive liability for any data breaches, keylogger exposures, or malware installations resulting from your installation of modified or unauthorized APK copies of Rewordium.''',
            ),
            
            _buildSection(
              context: context,
              title: '6. AI Output & No Professional Advice Disclaimer',
              content:
                  '''You acknowledge and agree that:
• AI Content Nature: AI-generated text suggestions, translation edits, or summarizations may occasionally be inaccurate, structurally flawed, contextually inappropriate, or outdated.
• No Professional Advice: AI suggestions do not constitute professional, legal, academic, medical, or financial advice.
• User Validation Mandatory: You are solely and fully responsible for reviewing, validating, and verifying the accuracy and legality of all AI-generated content before sending, publishing, or utilizing it. You assume all professional and legal liability for the content you generate and distribute.''',
            ),
            
            _buildSection(
              context: context,
              title: '7. Limitation of Liability',
              content:
                  '''TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE JURISDICTIONAL LAWS:
• Disclaimer of Warranties: THE SERVICE IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED.
• Liability Cap: IN NO EVENT SHALL NOXQUILL TECH, ITS OFFICERS, DIRECTORS, DEVELOPERS, EMPLOYEES, OR AGENTS BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR EXEMPLARY DAMAGES (INCLUDING LOSS OF PROFITS, DATA, SYSTEM DOWNTIME, OR BUSINESS INTERRUPTION) ARISING OUT OF OR IN CONNECTION WITH YOUR USE OR INABILITY TO USE THE SERVICE.
• Sole Remedy: YOUR SOLE AND EXCLUSIVE REMEDY FOR DISSATISFACTION WITH THE SERVICE IS TO DEACTIVATE YOUR ACCOUNT AND UNINSTALL THE APPLICATION. OUR AGGREGATE LIABILITY FOR ALL CLAIMS SHALL NOT EXCEED THE TOTAL FEES PAID BY YOU TO US FOR THE ACTIVE SUBSCRIPTION SERVICE IN THE THREE (3) MONTHS IMMEDIATELY PRECEDING THE EVENT GIVING RISE TO LIABILITY.''',
            ),
            
            _buildSection(
              context: context,
              title: '8. Acceptable Use Policy',
              content:
                  '''You agree not to use the Service to:
• Generate harmful, offensive, hateful, defamatory, or highly illegal text content.
• Submit content that infringes upon the copyright, trademark, or intellectual property rights of any third party.
• Generate high-speed automated spam, bulk phishing campaigns, or malicious scripting text.
• Attempt to overload, flood, or bypass standard cloud daily credit limits.''',
            ),
            
            _buildSection(
              context: context,
              title: '9. Open Source Components',
              content:
                  '''Rewordium incorporates open source software components, which are used in accordance with their respective licenses:

• FlorisBoard Keyboard: The keyboard component is based on FlorisBoard, an open-source keyboard for Android developed by Patrick Goldinger and contributors. FlorisBoard is licensed under the Apache License 2.0.

• Apache License 2.0: You may obtain a copy of this license at https://www.apache.org/licenses/LICENSE-2.0

We acknowledge and thank the open source community for their contributions. A full list of open source licenses is available within the App under Settings > Open Source Licenses.''',
            ),
            
            _buildSection(
              context: context,
              title: '10. Service Availability',
              content:
                  '''We strive to maintain high service availability, but cannot guarantee uninterrupted access. The service may be temporarily unavailable due to scheduled maintenance, server updates, technical difficulties, or third-party service dependencies.''',
            ),
            
            _buildSection(
              context: context,
              title: '11. Subscription, Credits, & Google Play Billing',
              content:
                  '''Rewordium offers both free and premium subscription tiers:
• Free users receive limited daily credits for text processing.
• Premium subscribers enjoy advanced features and higher limits.

Payments and subscriptions are processed securely through Google Play Billing:
• Subscriptions are managed by Google Play, including payments, renewals, cancellations, and refunds.
• We do not collect or store any payment information.
• Subscriptions automatically renew unless cancelled via Google Play settings.
• Pricing may change for future billing periods, with advance notice.
• Refunds are subject to Google Play’s refund policies.''',
            ),
            
            _buildSection(
              context: context,
              title: '12. Governing Law & Dispute Resolution',
              content:
                  '''This Agreement and any dispute arising out of or in connection with the Service shall be governed by, construed, and enforced in accordance with the laws of the jurisdiction in which Noxquill Tech is registered and operated.

You explicitly agree that any legal action or proceeding arising under this Agreement shall be brought exclusively in the courts of such jurisdiction, and you waive any objection to such venues on the grounds of inconvenient forum.''',
            ),
            
            _buildSection(
              context: context,
              title: '13. Termination',
              content:
                  '''Either party may terminate this agreement at any time:
• You may stop using the App and delete your account.
• We may suspend or terminate accounts for violations of these terms immediately, without prior notice.
• Upon termination, your right to use the App ceases immediately.''',
            ),
            
            _buildSection(
              context: context,
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
              child: Text(
                'By using Rewordium, you acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions.',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: Theme.of(context).colorScheme.primary,
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
