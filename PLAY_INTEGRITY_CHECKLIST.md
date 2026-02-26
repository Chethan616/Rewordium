# Play Integrity API - Quick Setup Checklist

## ✅ Completed Steps

✓ **Dependencies Added**
  - `com.google.android.play:integrity:1.4.0` in build.gradle.kts

✓ **Flutter Service Created**
  - `lib/services/play_integrity_service.dart`
  - Provides easy-to-use methods for integrity checks

✓ **Kotlin Implementation**
  - `android/app/src/main/kotlin/com/noxquill/rewordium/integrity/PlayIntegrityHandler.kt`
  - Handles native Play Integrity API calls

✓ **Integration Complete**
  - Method channel configured in MainActivity
  - Service initialized in main.dart

✓ **Documentation**
  - PLAY_INTEGRITY_SETUP.md - Full setup guide
  - play_integrity_examples.dart - Usage examples

---

## 🎯 Required Action (IMPORTANT!)

### Update Your Cloud Project Number

**File:** `android/app/src/main/kotlin/com/noxquill/rewordium/integrity/PlayIntegrityHandler.kt`

**Line 28:**
```kotlin
private const val CLOUD_PROJECT_NUMBER = 0L // ← CHANGE THIS!
```

**Where to find your Cloud Project Number:**
1. Go to https://console.cloud.google.com/
2. Select your project
3. Click "Project Settings" (gear icon)
4. Copy the **Project Number** (not Project ID)

**Update to:**
```kotlin
private const val CLOUD_PROJECT_NUMBER = 123456789012L // Your actual number
```

---

## 🧪 Testing

### 1. Build Your App
```bash
cd android
./gradlew clean
cd ..
flutter build apk --release
```

### 2. Install & Test
Upload the APK to Play Console Internal Testing track, then:

```dart
import 'package:rewordium/services/play_integrity_service.dart';

// Test integrity check
final result = await PlayIntegrityService.checkIntegrity();
print('Integrity check: $result');
```

### 3. Check Logs
```bash
adb logcat | grep -i "PlayIntegrity\|integrity"
```

---

## 📋 Verification Checklist

Before going to production, verify:

- [ ] Cloud Project Number updated in PlayIntegrityHandler.kt
- [ ] App linked to Google Cloud project in Play Console
- [ ] Play Integrity API enabled in Google Cloud Console
- [ ] App enrolled in Play App Signing (Play Console → App Signing)
- [ ] Tested on real device via Play Store (Internal Testing)
- [ ] Backend verification implemented for critical operations (recommended)
- [ ] Error handling tested (airplane mode, device without Play Services)

---

## 🚀 Ready to Use!

Your app now includes:

### Available Methods:
```dart
// Initialize (already called in main.dart)
await PlayIntegrityService.initialize();

// Basic integrity check
bool isValid = await PlayIntegrityService.checkIntegrity();

// Device security check
bool isSecure = await PlayIntegrityService.isDeviceSecure();

// Get detailed verdict
Map<String, dynamic>? verdict = await PlayIntegrityService.getIntegrityVerdict();
```

### Example Usage:
See `lib/services/play_integrity_examples.dart` for complete examples.

---

## 📚 Resources

- **Setup Guide:** [PLAY_INTEGRITY_SETUP.md](./PLAY_INTEGRITY_SETUP.md)
- **Usage Examples:** `lib/services/play_integrity_examples.dart`
- **Google Docs:** https://developer.android.com/google/play/integrity

---

## ⚠️ Important Notes

1. **Testing:** Only works with signed APKs installed from Play Store
2. **Debug Mode:** May return different verdicts than production
3. **Graceful Degradation:** Service returns `true` on errors to not block app
4. **Backend Verification:** For critical operations, verify tokens on your backend

---

## 🎉 Next Steps

1. ✏️ Update `CLOUD_PROJECT_NUMBER` 
2. 🔨 Build and test your app
3. 🧪 Upload to Internal Testing track
4. ✅ Verify integrity checks work
5. 🚀 Deploy to production

**Need help?** Check PLAY_INTEGRITY_SETUP.md for detailed instructions!
