# Terms and Conditions

**Last updated: May 18, 2026**

Welcome to Rewordium. These Terms and Conditions ("Terms" or "Agreement") constitute a legally binding contract between you ("User," "you," or "your") and Noxquill Tech ("we," "our," "us," or "Developers") governing your access to and use of the Rewordium mobile application, custom keyboard, floating AI Assistant Bubble, and all associated services, websites, and APIs (collectively, the "Service" or "Application").

By downloading, installing, registering an account, enabling keyboard or accessibility permissions, or interacting with the Service, you explicitly acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions and our Privacy Policy. If you do not agree to these Terms, you must immediately deactivate, uninstall, and cease all use of our Service.

---

## 1. Description of Service

Rewordium is an AI-powered writing companion that enhances text composition through intelligent editing features, including:
- **Tone Shift (Paraphraser)**: Adjusts text drafts into Casual, Concise, Creative, or Professional tones.
- **Smart Grammar Check**: Scans, lists, and fixes grammar mistakes and typos inline.
- **Summarizer**: Condenses articles or uploaded document files (PDFs, TXT, DOCX) into structured summary nodes.
- **Contextual Translator**: Contextual translation across 30+ major global languages.
- **Jade AI Companion**: Interactive conversational writing and drafting assistant.
- **Custom Keyboard & Accessibility Overlay**: Seamless inline integration using standard Android system virtual input mechanisms and AccessibilityService APIs.

---

## 2. Bring Your Own Key (BYOK) & Third-Party API Agreement

To accommodate advanced writing loads and developers, Rewordium provides a **Bring Your Own Key (BYOK)** feature inside the settings drawer. By inputting your personal API keys (Google Gemini, OpenAI, Anthropic Claude, or custom OpenAI-compatible endpoints):

### 2.1 Billing and Charge Waiver
- **Credit Exemption**: Active BYOK connections bypass all standard daily daily credit refill limitations. You will not be charged credits by Rewordium for writing processing.
- **Direct Developer Billing**: All generations under BYOK are billed directly and exclusively by the third-party providers (e.g. OpenAI, Google, or Anthropic) to your private developer account.
- **Absolute Financial Exemption**: You acknowledge that you are solely and fully responsible for any charges, fees, rate caps, API costs, or financial transactions incurred on your third-party provider accounts. **Noxquill Tech is completely exempt from all financial liabilities and will not reimburse, credit, or offset any third-party developer bills.**

### 2.2 Provider Terms Compliance & Liability
- You represent and warrant that your inputted API keys are lawfully obtained and belong exclusively to you.
- You agree to comply strictly with all third-party developer terms (including OpenAI Service Terms, Google Gemini terms of service, and Anthropic Developer Agreements).
- **Service Suspensions**: We hold zero control over your third-party accounts. We are not liable for any API key suspensions, billing freezes, account terminations, or rate-limiting enforced on your profiles by those providers.

### 2.3 Secure Local Key Storage
- Your API keys are saved strictly locally on your device's secure hardware partition. We use hardware-backed local keystores (Android Keystore / iOS Keychain) with AES-256 encryption via `FlutterSecureStorage`.
- You acknowledge that if you root your device, install custom insecure ROMs, or compromise device-level security, your local encrypted keys may become vulnerable. You assume all security risks in relation to your local device security.

---

## 3. Data Sovereignty & Infrastructure Disclosures (Qwen via Groq)

To guarantee text data protection compliance and sovereignty:
- **US Infrastructure (Groq)**: The default cloud language model utilized by Rewordium is the open-source **Qwen 3 (32B)**. You agree and acknowledge that 100% of these default completions are routed exclusively through **Groq’s secure, US-based server networks**.
- **No Chinese Endpoints**: We guarantee that standard writing requests never transit through or contact Chinese domestic networks, servers, or Chinese government-governed API endpoints. Your data remains fully protected under secure Western frameworks.
- **Stateless Transit**: You acknowledge and agree that default completions are processed statelessly inside secure, volatile RAM and deleted immediately upon output. No permanent logging or database archives are maintained.

---

## 4. Android Permissions & Virtual Inputs

By activating Rewordium's custom keyboard or floating Accessibility Bubble:
- **System Warning Acknowledgment**: You acknowledge that Android displays standard security warnings when activating any third-party keyboard. You explicitly waive all liability in connection with these boilerplate operating system prompts.
- **Keystroke Log Protection**: We represent that the keyboard typing loop runs strictly offline in local RAM and does **not** log, track, or stream your keystrokes or private conversations.
- **Accessibility Limits**: The Accessibility Service only reads and modifies text inside the active focused text input field (or contextually reads surrounding visual text if the active field is blank, to suggest replies) when you actively tap our bubble. The Service automatically suspends suggestions whenever a secure password or pin entry input field is active.

---

## 5. Tampering, Reverse Engineering, & Signature Locks

To protect users against modified copies of Rewordium which could be repackaged with spyware or keylogger payloads:
- **Anti-Tampering Integrity**: Rewordium contains cryptographic signature verification modules that automatically lock and disable the application if it detects a modified, cracked, or unauthorized APK package.
- **Prohibited Actions**: You are strictly prohibited from:
  1. Reverse engineering, decompiling, disassembling, or attempting to extract the source code of the Application.
  2. Modifying, bypassing, cracking, or disabling the cryptographic signature integrity check modules.
  3. Distributing modified, cracked, or unauthorized copies of the Application.
- **Liability Exemption**: You assume full and exclusive liability for any data breaches, keylogger exposures, or malware installations resulting from your installation of modified or unauthorized APK copies of Rewordium.

---

## 6. AI Output & No Professional Advice Disclaimer

You acknowledge and agree that:
- **AI Content Nature**: AI-generated text suggestions, translation edits, or summarizations may occasionally be inaccurate, structurally flawed, contextually inappropriate, or outdated.
- **No Professional Advice**: AI suggestions do **not** constitute professional, legal, academic, medical, or financial advice.
- **User Validation Mandatory**: **You are solely and fully responsible for reviewing, validating, and verifying the accuracy and legality of all AI-generated content before sending, publishing, or utilizing it.** You assume all professional and legal liability for the content you generate and distribute.

---

## 7. Limitation of Liability

TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE JURISDICTIONAL LAWS:
- **Disclaimer of Warranties**: THE SERVICE IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR NON-INFRINGEMENT.
- **Liability Cap**: IN NO EVENT SHALL NOXQUILL TECH, ITS OFFICERS, DIRECTORS, DEVELOPERS, EMPLOYEES, OR AGENTS BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR EXEMPLARY DAMAGES (INCLUDING LOSS OF PROFITS, DATA, SYSTEM DOWNTIME, OR BUSINESS INTERRUPTION) ARISING OUT OF OR IN CONNECTION WITH YOUR USE OR INABILITY TO USE THE SERVICE.
- **Sole Remedy**: YOUR SOLE AND EXCLUSIVE REMEDY FOR DISSATISFACTION WITH THE SERVICE IS TO DEACTIVATE YOUR ACCOUNT AND UNINSTALL THE APPLICATION. OUR AGGREGATE LIABILITY FOR ALL CLAIMS SHALL NOT EXCEED THE TOTAL FEES PAID BY YOU TO US FOR THE ACTIVE SUBSCRIPTION SERVICE IN THE THREE (3) MONTHS IMMEDIATELY PRECEDING THE EVENT GIVING RISE TO LIABILITY.

---

## 8. Acceptable Use Policy

You agree not to use the Service to:
- Generate harmful, offensive, hateful, defamatory, or highly illegal text content.
- Submit content that infringes upon the copyright, trademark, or intellectual property rights of any third party.
- Generate high-speed automated spam, bulk phishing campaigns, or malicious scripting text.
- Attempt to overload, flood, or bypass standard cloud daily credit limits.

---

## 9. Governing Law & Dispute Resolution

This Agreement and any dispute arising out of or in connection with the Service shall be governed by, construed, and enforced in accordance with the laws of the jurisdiction in which Noxquill Tech is registered and operated. 

You explicitly agree that any legal action or proceeding arising under this Agreement shall be brought exclusively in the courts of such jurisdiction, and you waive any objection to such venues on the grounds of inconvenient forum.

---

## 10. Subscription, Credits, & Google Play Billing

- **Subscription Management**: Free daily credits are allocated daily. Premium subscriptions are processed securely and exclusively through **Google Play Billing**.
- **Auto-Renewal**: Subscriptions automatically renew at the designated fee unless cancelled via your Google Play account settings at least 24 hours prior to the billing cycle end.
- **Refund Policy**: All fees are non-refundable. Any refund requests must be routed through and processed according to the Google Play Billing refund rules.

---

## 11. Termination

- **User Termination**: You can terminate this Agreement at any time by deleting your account and completely uninstalling the Application.
- **Developer Termination**: We reserve the right to suspend or permanently terminate your account and block your device identifier from accessing our Service immediately, without prior notice, if we determine that you have violated these Terms or engaged in unauthorized tampering.

---

## 12. Contact Information

If you have questions, disputes, or legal notices concerning these Terms and Conditions, please contact us at:

**Email**: noxquilltech@gmail.com  
**Website**: rewordium.tech  
