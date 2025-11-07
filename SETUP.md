# 🛠️ Setup Guide - Rewordium AI Keyboard

Welcome to Rewordium! This guide will help you set up your development environment and build the project.

---

## 📋 Prerequisites

### Required Software

1. **Flutter SDK** (v3.24.0 or higher)
   - Download: https://flutter.dev/docs/get-started/install
   - Verify installation: `flutter --version`
   - Run: `flutter doctor` to check for issues

2. **Android Studio** (Recommended) or VS Code
   - Android Studio: https://developer.android.com/studio
   - VS Code with Flutter extension: https://code.visualstudio.com/

3. **Android SDK**
   - Minimum SDK: API 24 (Android 7.0)
   - Target SDK: API 35 (Android 15)
   - Compile SDK: API 36
   - NDK: r28+ (for 16 KB page size support)

4. **JDK** (Java Development Kit)
   - JDK 11 or higher required
   - Verify: `java -version`

5. **Gradle** (Included with Flutter, but can be installed separately)
   - Version: 8.7.0 or higher
   - Bundled with project in `android/gradle/wrapper/`

6. **Git**
   - Download: https://git-scm.com/downloads
   - Verify: `git --version`

---

## 🔐 Required Configuration Files

Before building, you **must** create these files (they're excluded from version control for security):

### 1. Android Signing Keys (`android/key.properties`)

Create this file with your signing credentials:

```properties
storePassword=YOUR_STORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=YOUR_KEY_ALIAS
storeFile=path/to/your/keystore.jks
```

⚠️ **Never commit this file to Git!**

### 2. Environment Variables (`android/local.properties`)

Create this file in the `android/` folder:

```properties
sdk.dir=/path/to/your/android/sdk
flutter.sdk=/path/to/your/flutter/sdk
GROQ_API_KEY=your_groq_api_key_here
```

### 3. Firebase Configuration

You'll need your own Firebase project:

1. Create a Firebase project at https://console.firebase.google.com
2. Download `google-services.json` (Android) → place in `android/app/`
3. Download `GoogleService-Info.plist` (iOS) → place in `ios/Runner/`
4. Service account JSON (for Cloud Functions) → place in `functions/` (rename to match your project)

⚠️ **Never commit Firebase credentials to Git!**

### 4. Flutter Environment (`.env` file)

Create a `.env` file in the project root:

```env
GROQ_API_KEY=your_groq_api_key
# Add other environment variables here
```

---

## 📦 Installation Steps

### 1. Clone the Repository

```bash
git clone https://github.com/Chethan616/YC_startup.git
cd YC_startup
```

### 2. Install Flutter Dependencies

```bash
flutter pub get
```

### 3. Install Android Dependencies

```bash
cd android
./gradlew build
cd ..
```

### 4. Verify Setup

```bash
flutter doctor -v
```

Fix any issues reported by `flutter doctor`.

---

## 🏗️ Building the Project

### Development Build (Debug)

```bash
# Android
flutter run

# Or with specific device
flutter run -d <device_id>
```

### Release Build

#### Android APK

```bash
flutter build apk --release
```

Output: `build/app/outputs/flutter-apk/app-release.apk`

#### Android App Bundle (for Play Store)

```bash
flutter build appbundle --release
```

Output: `build/app/outputs/bundle/release/app-release.aab`

#### Build with Size Analysis

```bash
# For specific architecture
flutter build appbundle --release --target-platform android-arm64 --analyze-size
```

This generates an HTML report showing what's taking up space.

---

## 🧪 Running Tests

### Unit Tests

```bash
flutter test
```

### Integration Tests

```bash
flutter test integration_test/
```

### Generate Test Coverage

```bash
flutter test --coverage
genhtml coverage/lcov.info -o coverage/html
```

---

## 🔧 Common Issues & Solutions

### Issue: "SDK location not found"

**Solution:** Create `android/local.properties`:
```properties
sdk.dir=/path/to/Android/sdk
```

### Issue: "Gradle build failed"

**Solution:**
```bash
cd android
./gradlew clean
./gradlew build --stacktrace
```

### Issue: "Flutter pub get failed"

**Solution:**
```bash
flutter clean
flutter pub cache repair
flutter pub get
```

### Issue: "Firebase initialization failed"

**Solution:**
- Ensure `google-services.json` is in `android/app/`
- Verify Firebase project configuration
- Check package name matches in Firebase console

### Issue: "Signing key not found"

**Solution:**
- Create `android/key.properties` (see configuration section above)
- Generate a keystore if you don't have one:
```bash
keytool -genkey -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

---

## 🚀 Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Changes

Edit code, run `flutter run` to test.

### 3. Format Code

```bash
flutter format .
```

### 4. Analyze Code

```bash
flutter analyze
```

### 5. Commit Changes

```bash
git add .
git commit -m "feat: your feature description"
```

### 6. Push and Create PR

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

---

## 📱 Testing on Physical Device

### Android

1. Enable Developer Options on your device
2. Enable USB Debugging
3. Connect device via USB
4. Run: `flutter devices`
5. Run: `flutter run -d <device_id>`

### Wireless Debugging (Android 11+)

```bash
adb pair <ip>:<port>
adb connect <ip>:<port>
flutter run
```

---

## 🧹 Clean Build

If you encounter issues, try a clean build:

```bash
flutter clean
cd android && ./gradlew clean && cd ..
flutter pub get
flutter run
```

---

## 📚 Additional Resources

- **Flutter Documentation:** https://flutter.dev/docs
- **Android Developer Guide:** https://developer.android.com
- **Firebase Documentation:** https://firebase.google.com/docs
- **Kotlin Documentation:** https://kotlinlang.org/docs

---

## 🆘 Getting Help

- **Issues:** https://github.com/Chethan616/YC_startup/issues
- **Discussions:** https://github.com/Chethan616/YC_startup/discussions
- **Contact:** [Your contact method]

---

## ⚖️ License

This project is licensed under [Your License]. See `LICENSE` file for details.

Contributions must follow the [Contributor License Agreement](CONTRIBUTING.md).
