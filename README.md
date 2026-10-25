# Rewordium for Android

Rewordium is a native Android writing assistant with an on-device keyboard and an optional accessibility service for writing actions in other apps.

## App features

- Kotlin and Jetpack Compose with Material 3 Expressive components and spring-based motion.
- Firebase email and Google sign-in, Firestore credits, account management, and Firebase notifications.
- Google Play subscriptions, in-app updates, review prompts, and Play Integrity integration.
- Jade chat, rewriting, grammar, summaries, translation, tone editing, AI writing checks, and document polishing.
- ReBoard keyboard settings, typing tools, and persona selection inside the Rewordium settings flow.
- Accessibility and keyboard setup are managed through Android system settings.

## Build

Use JDK 17, Android SDK platform 37, Android NDK `28.2.13676358`, and CMake. Android Studio can install the SDK components.

From the repository root on Windows:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Build a Play Store bundle with:

```powershell
.\gradlew.bat :app:bundleRelease
```

The release build uses `android/app/google-services.json` for Firebase and `android/key.properties` plus the upload keystore for signing. Keep these files out of version control. CI receives Firebase configuration and signing credentials through repository secrets.

The application ID remains `com.noxquill.rewordium`. `versionCode` and `versionName` are read from `projectVersionCode` and `projectVersionName` in the local Gradle properties, with the current release values as fallback.

## Supporting services

Firebase Cloud Functions remain in `functions/`. The public site and account deletion pages remain under `website/` and `website2.0/`.

## Attribution

The keyboard includes code from the FlorisBoard/ReBoard projects. Open-source notices are available in the app under **Settings → Rewordium → Open-source licenses**, and in [NOTICE](NOTICE) and [LICENSE.md](LICENSE.md).
