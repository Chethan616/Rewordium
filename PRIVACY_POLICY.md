# Privacy Policy

**Last updated: May 18, 2026**

Rewordium ("we," "our," or "us") is dedicated to protecting your personal data, secure configurations, and typing privacy. This Privacy Policy details our absolute commitment to transparency, the exact parameters under which we handle user text, and our zero-tolerance policy towards unauthorized data-mining, telemetry tracking, and keylogging.

By downloading, installing, or interacting with the Rewordium mobile application (the "App" or "Service"), you explicitly agree to the data collection, local storage, and stateless processing frameworks described in this Privacy Policy. If you do not consent to these terms, you must immediately deactivate the Service and uninstall the App.

---

## 🚨 MANDATORY ANDROID ACCESSIBILITY SERVICE DISCLOSURE

Rewordium integrates Android’s standard `AccessibilityService` API to provide its core real-time AI writing companion features. This integration is optional but critical for the floating AI Assistant Bubble to function.

### 1. Scope and Purpose of Accessibility Service
- **Inline Text Reading**: When you actively focus a text input field in a third-party application and tap the floating Rewordium Assistant Bubble, the service safely reads the active draft inside that input field.
- **Context-Aware Replied Suggestion**: If you tap the Assistant Bubble when your active text input box is completely blank, the service contextually scans surrounding visible page elements (such as an incoming email subject or previous chat line) strictly to draft a relevant response.
- **Direct Inline Text Replacement**: After our secure AI completion completes, the service automatically replaces your active draft in the text field with the refined, polished version. This eliminates the need for manual copy-paste actions.

### 2. Operational Limitations & Boundaries (What is NOT Accessed)
- **Automatic Password Suspension**: The Accessibility Service automatically suspends all monitoring, reading, and AI suggest layers whenever a secure password, credit card, or pin entry input field is active on your device.
- **No Background Surveillance**: The Accessibility Service does not run passive background tracking. It does not monitor apps, catalog browsing history, inspect personal photo files, capture device screens, or record calls.
- **Opt-In Control**: Enabling the Accessibility Service is 100% voluntary. You must explicitly authorize it in Android Settings (`Settings > Accessibility > Installed Services > Rewordium`). You can revoke this permission at any moment without deleting your account.

---

## 1. Zero-Data-Collection & Privacy-First Architecture

Rewordium is engineered on a strict **privacy-by-design** framework. Our business model does not rely on gathering, profiling, mining, or selling user typing content, communications, or personal metadata.
- **No Telemetry Keylogging**: Our keyboard input engine runs completely locally in offline RAM. We do not maintain any logs of your keystrokes, personal chats, custom dictionaries, or search queries.
- **Smart Clipboard Privacy**: The clipboard recommendation panel processes copied text segments strictly locally. It automatically ignores and discards passwords, security pins, credit card details, and raw phone numbers from showing up as suggestions.

---

## 2. Information We Collect and Process

To run account-based billing, standard credit daily allocations, and active security systems, we process the following categories of data:

### 2.1 Account and Security Information
- **Authentication Credentials**: Secure sign-in tokens generated via Google Sign-In or Firebase Authentication.
- **Profile Data**: Your email address, account identifier, and billing status.
- **Account Preferences**: Personal parameters, including custom writer personas, saved settings, and theme choices.

### 2.2 Billing & Subscription Data
- **Transaction Logs**: Secure subscription statuses, transaction IDs, and credit tallies. All credit card billing details are processed securely and exclusively by Google Play Billing; Rewordium never collects, views, or stores financial card details on its servers.

### 2.3 Transient Text Processing Data (Stateless Cloud Transit)
- **Draft Refinements**: When you submit a block of text for Tone Shift, Summarization, Translation, or Grammar Check using our default cloud AI, the raw draft is securely transmitted over HTTPS.
- **Stateless RAM Processing**: The draft is processed transiently inside secure, volatile RAM. The text is immediately deleted upon completion and is **never** archived in databases, logged to disk, or utilized to retrain AI language models.

### 2.4 Secure Local Storage (Encryption Specs)
- **AES-256 Key Encryption**: Your private API keys (OpenAI, Google Gemini, Anthropic Claude, custom endpoints) and custom settings are stored locally on your device's secure hardware partition.
- **Keystore / Keychain Integration**: We utilize `FlutterSecureStorage` interfacing directly with the device's hardware-backed **Android Keystore** (and iOS Keychain on Apple platforms) via 256-bit AES cryptographic encryption. This data never touches our cloud servers.

---

## 3. Data Sovereignty & AI Infrastructure (Qwen via Groq)

Many users are concerned about where their private writing transits when using modern language models. We have implemented strict geographical and infrastructural boundaries to guarantee your security:

- **Default Cloud AI Engine**: Rewordium's standard cloud writing refinements utilize the state-of-the-art open-source **Qwen 3 (32B)** model.
- **Western Infrastructure Isolation (Groq)**: 100% of these default model completions are routed directly through **Groq’s secure, ultra-low latency server networks located in the United States**.
- **No Chinese Endpoints**: We do **not** use, transit through, or connect to any Chinese domestic endpoints, Alibaba Cloud networks, or third-party servers subject to foreign surveillance. Your text remains fully protected under secure Western data frameworks.

---

## 4. Bring Your Own Key (BYOK) Data Processing

If you enable **Bring Your Own Key (BYOK)** in the advanced settings to bypass standard daily credit caps:
- **Direct Secure Dispatch**: Your custom API keys are loaded locally from your encrypted device Keystore and dispatched directly over secure HTTPS tunnels to the respective provider's endpoints (e.g. `api.openai.com`, `generativelanguage.googleapis.com`, or `api.anthropic.com`).
- **No Intermediary Transit**: Your custom keys and text drafts completely bypass Rewordium's servers in BYOK mode, transiting directly to your selected provider.
- **Billing Responsibility**: All interactions under BYOK mode are governed strictly by the respective third-party provider's privacy policies and terms of service. You are solely responsible for reviewing and managing your API key usage logs.

---

## 5. Tampering, Safety, & APK Integrity Safeguards

To prevent malicious modified copies of Rewordium (which could easily have spyware or keyloggers injected by bad actors on unofficial forums) from harvesting your typing inputs:
- **Signature Integrity Locking**: Rewordium contains cryptographic installation checks that cross-reference the app's signature against official store certificates.
- **Automated Disabling**: If modified, cracked, or tampered APK packages are detected, the Service immediately stops running and locks all features to safeguard your device's keystream.
- **Third-Party Telemetry**: In modified copies, all privacy guarantees are null and void. We strongly advise users to only install the App from the official Google Play Store.

---

## 6. Data Security Measures

We enforce rigorous administrative, technical, and physical security layers:
- **Full Transit Encryption**: 100% of external network communications use secure TLS/HTTPS protocols.
- **Access Control Controls**: Strict role-based credential restrictions preventing unauthorized server administrative entry.
- **Anonymized Aggregated Analytics**: Standard usage metrics and app crash reports are fully stripped of personal identifiers and deleted automatically after 90 days.

---

## 7. Your Data Rights & Control

You hold absolute control over your private information and device integrations:
- **Accessibility Opt-Out**: You can turn off accessibility integration at any moment inside your device's settings menu.
- **Account Deletion**: You can request immediate account deletion in the settings drawer. Upon trigger, all account profile entries and sign-in tokens are instantly wiped from our databases.
- **API Key Removal**: Clearing your private keys in advanced settings completely purges them from your device's secure Keystore storage blocks.

---

## 8. GDPR & CCPA Compliance Disclosures

### 8.1 European Union (GDPR)
For EU users, the lawful basis for transient text processing is the performance of a contract (delivering the AI edits you trigger). You hold the right to portability, data erasure, and to file inquiries with your national data protection agency.

### 8.2 State of California (CCPA)
California residents are entitled to know what data categories are gathered, request deletion, and are explicitly protected under a **"Do Not Sell My Info"** guarantee. Rewordium sells zero user data.

---

## 9. Updates to this Privacy Policy

We reserve the right to modify this policy periodically to align with legal guidelines or operational feature expansions. When significant revisions occur, we will display a prominent notice inside the app. Continuing to use Rewordium after changes are posted constitutes your absolute acceptance of the revised Privacy Policy.

---

## 10. Contact Us

For all privacy questions, data requests, or compliance audits, please contact us at:

**Email**: noxquilltech@gmail.com  
**Developer Group**: Noxquill Tech  
**Support Desk**: noxquilltech@gmail.com  
**Website**: rewordium.tech  
