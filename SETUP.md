# Rewordium — Developer Setup Guide

This document describes how to set up the project for local development after cloning the repository. Several secret / environment-specific files are excluded from version control and must be created locally from the provided `.example` templates.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Flutter | 3.x (stable channel) |
| Android SDK | compileSdk 36, NDK 28 |
| Java | JDK 17+ |
| Rust | 1.85.0+ (GNU toolchain, **not** MSVC) |
| Node.js | 18+ (for Firebase Functions) |
| Firebase CLI | Latest (`npm install -g firebase-tools`) |

### Rust Setup (required for the Reboard keyboard native library)

The keyboard module (`reboard_lib/native`) contains a Rust JNI library that cross-compiles to Android ARM64. It builds **automatically** during `flutter build` via CMake — no manual `cargo build` needed — but the toolchain must be installed first:

```powershell
# Install Rust GNU toolchain (Windows)
rustup toolchain install 1.85.0-x86_64-pc-windows-gnu

# Add Android ARM64 cross-compilation target
rustup target add aarch64-linux-android

# Verify
cargo --version
rustup target list --installed   # must include aarch64-linux-android
```

---

## 1. Clone & Install Dependencies

```bash
git clone <repo-url>
cd rewordium
flutter pub get
```

---

## 2. Secret Files Setup

The table below lists every file that needs to be created from a template. Each `.example` file contains placeholder values — copy it and fill in your real credentials.

| # | File to Create | Template | Purpose |
|---|----------------|----------|---------|
| 1 | `.env` | `.env.example` | Flutter-side Groq API key (loaded via `flutter_dotenv`) |
| 2 | `android/local.properties` | `android/local.properties.example` | Android SDK paths + Groq API key for native builds |
| 3 | `android/key.properties` | `android/key.properties.example` | Release signing keystore credentials |
| 4 | `android/app/google-services.json` | `android/app/google-services.json.example` | Firebase config for Android |
| 5 | `lib/firebase_options.dart` | `lib/firebase_options.dart.example` | Firebase config for Flutter |
| 6 | `ios/Runner/GoogleService-Info.plist` | `ios/Runner/GoogleService-Info.plist.example` | Firebase config for iOS |
| 7 | `config/service-account-key.json` | *(download from Firebase Console)* | Firebase Admin SDK service account |
| 8 | `functions/rewordium-4a89181f09b0.json` | *(download from Firebase Console)* | Cloud Functions service account |

### Step-by-step

```bash
# 1. Environment variables
cp .env.example .env
# Edit .env — add your Groq API key from https://console.groq.com/keys

# 2. Android local properties
cp android/local.properties.example android/local.properties
# Edit — set sdk.dir, flutter.sdk paths, and GROQ_API_KEY

# 3. Signing key
cp android/key.properties.example android/key.properties
# Edit — set your keystore password, key alias, and storeFile path
# You also need an upload-keystore.jks in android/app/

# 4. Firebase Android config
cp android/app/google-services.json.example android/app/google-services.json
# Replace placeholder values with your Firebase project config
# Download from: Firebase Console → Project Settings → Android app

# 5. Firebase Flutter config
cp lib/firebase_options.dart.example lib/firebase_options.dart
# Or generate fresh: flutterfire configure

# 6. Firebase iOS config
cp ios/Runner/GoogleService-Info.plist.example ios/Runner/GoogleService-Info.plist
# Download from: Firebase Console → Project Settings → iOS app

# 7 & 8. Service account keys
# Download from: Firebase Console → Project Settings → Service Accounts → Generate New Private Key
# Save as config/service-account-key.json
# Save a copy as functions/rewordium-4a89181f09b0.json
```

---

## 3. Firebase Functions

```bash
cd functions
npm install
cd ..
```

To deploy:
```bash
firebase deploy --only functions
```

---

## 4. Build & Run

```bash
# Debug
flutter run

# Release APK
flutter build apk --release

# Release App Bundle
flutter build appbundle --release
```

---

## 5. AI Configuration

The app uses a multi-provider AI architecture:

- **Default**: Groq (Qwen 2.5 32B Instruct) — requires `GROQ_API_KEY` in `.env` and `android/local.properties`
- **Advanced**: Users can configure OpenAI, Gemini, Anthropic Claude, or a custom OpenAI-compatible endpoint via the in-app Advanced AI Settings screen

The Groq API key flows through two paths:
1. **Flutter tools** (Paraphraser, Grammar, Translator, Summarizer): `.env` → `flutter_dotenv` → `GroqService`
2. **Android native** (Accessibility overlay, Keyboard): `local.properties` → `BuildConfig.GROQ_API_KEY` → `AIConfigProvider`

When a user enables Advanced AI Settings, both paths are overridden via SharedPreferences synced from Flutter to Android.

---

## 6. Security Notes

- **Never commit** `.env`, `local.properties`, `key.properties`, `google-services.json`, `firebase_options.dart`, or any `*-service-account*.json` file
- All are listed in `.gitignore` — verify with `git status` before pushing
- If you accidentally commit a secret, rotate the key immediately
- The `.example` templates are safe to commit — they contain only placeholder values
