# Developer Setup

This guide sets up the native Android app. Firebase Functions and the websites have separate toolchains.

## Requirements

- JDK 17
- Android SDK platform 37
- Android NDK `28.2.13676358` and CMake
- Node.js and Firebase CLI only when changing Cloud Functions

## Android configuration

1. Copy `android/local.properties.example` to `android/local.properties` and set `sdk.dir` to your Android SDK directory.
2. Copy `android/app/google-services.json.example` to `android/app/google-services.json`, then download the actual Android configuration from Firebase Console.
3. For signed release builds, copy `android/key.properties.example` to `android/key.properties` and use the private upload keystore supplied by the project owner.
4. Optionally set `GROQ_API_KEY` and `KLIPY_API_KEY` in `local.properties` or the build environment.

Keep local properties, Firebase configuration, API keys, signing properties, and keystores out of commits.

Build a debug APK from `android/`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Build a Play Store bundle:

```powershell
.\gradlew.bat :app:bundleRelease
```

## Firebase Functions

From `functions/`, install dependencies with `npm install`. Use the scripts defined in `functions/package.json` and the service account provided for the project. Never commit service account credentials.

## Release workflow

The Android GitHub Actions workflow builds both release formats. It expects encrypted repository secrets for the Firebase Android config (`GOOGLE_SERVICES_JSON_BASE64`) and release signing values (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`). Optional AI and GIF keys are read from `GROQ_API_KEY` and `KLIPY_API_KEY`.
