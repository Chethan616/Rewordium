# Android Build Guide

## Toolchain

- JDK 17
- Android Gradle Plugin 9.1.1 with the Gradle 9.3.1 wrapper
- Kotlin 2.2.10
- Android SDK platform 37 and build tools 36.0.0
- Android NDK `28.2.13676358`
- CMake installed through Android Studio

The Gradle wrapper downloads Gradle automatically. Android Studio can install the Android SDK, NDK, and CMake versions listed above.

## Local configuration

1. Copy `android/local.properties.example` to `android/local.properties` and set `sdk.dir` to your Android SDK path.
2. Add `android/app/google-services.json` from Firebase Console.
3. For release builds, add `android/key.properties` from `android/key.properties.example` and place the upload keystore at its configured path.
4. Add `GROQ_API_KEY` and `KLIPY_API_KEY` to `android/local.properties` or the environment if you want the default AI provider and keyboard GIF search enabled.

Do not commit API keys, Firebase configuration, signing properties, or keystore files.

## Build commands

Run these from the `android` directory.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleRelease
```

On macOS or Linux, use `./gradlew` instead of `gradlew.bat`.

The Android application module builds the native Compose app, the ReBoard keyboard, and the accessibility service in one package. The Play Store bundle is written to `app/build/outputs/bundle/release/`.

## Play Store release

- Keep `applicationId` as `com.noxquill.rewordium` to preserve updates to the published app.
- `versionCode` and `versionName` are read from `projectVersionCode` and `projectVersionName` in `local.properties`.
- Release signing reads `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from the environment, or from `android/key.properties` for local builds.
- CI restores `google-services.json` and the signing keystore from encrypted repository secrets, then uploads the APK and AAB as workflow artifacts.
- Keep the target SDK at the value configured in `app/build.gradle.kts` unless the Play release requirements are reviewed and updated.

## Firebase Functions

The Android build does not require the Firebase Functions toolchain. To work on the backend, install Node.js and the Firebase CLI, then use the documented scripts in `functions/package.json`.
