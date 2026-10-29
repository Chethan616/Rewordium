# Rewordium Android

Rewordium is a native Android writing assistant with an on-device keyboard and an optional accessibility service for writing actions in other apps.

## App features

- Kotlin and Jetpack Compose with Material 3 Expressive components and spring-based motion.
- Firebase email and Google sign-in, Firestore credits, account management, and Firebase notifications.
- Google Play subscriptions, in-app updates, review prompts, and Play Integrity integration.
- Jade chat, rewriting, grammar, summaries, translation, tone editing, AI writing checks, and document polishing. Jade can also change any keyboard setting for you.
- ReBoard keyboard settings, typing tools, and persona selection inside the Rewordium settings flow.
- Backup and restore of chats, app settings and keyboard data (never account state or API keys).
- Accessibility and keyboard setup are managed through Android system settings.

## The keyboard

The keyboard (`android/reboard_keyboard`) runs entirely on the phone.

- **Next-word prediction** from English n-gram models (up to five words of context) with stupid backoff, sentence-start predictions, and whole-phrase suggestions when every word of the phrase is likely. Picking one prediction after another keeps the strip filled.
- **Autocorrect** re-ranks the AOSP decoder's candidates with a noisy-channel model (where each tap landed on the keyboard) plus the language model. It only replaces a correctly spelled word when the context makes another word far more likely, and leaves names, brands and slang alone.
- **Glide typing** with a statistical path decoder plus a light previous-word bonus.
- **Editing**: picking a suggestion in the middle of a word replaces the whole word (no more "lovev").
- **Privacy**: nothing is learned in private mode, in apps that ask for no personalised learning, or in password, e-mail, URL and number fields. *Keys → Forget learned words* clears everything the keyboard learned. A single learned word can be removed by long-pressing its suggestion.

### Measured results

Next-word prediction on 60,018 held-out positions in conversational sentences the models never saw (`tools/eval_predictions.py`):

| | top-1 | top-3 (on the strip) | no suggestion |
|---|---|---|---|
| Previous list (83 words) | 4.7% | 8.6% | 57.3% |
| Current models | 22.6% | 37.0% | 2.5% |

On held-out web text, top-3 is 25.6%. On the device, chaining 168 predictions never left the strip empty, and a prediction takes about 0.5 ms (emulator).

Autocorrect on 1,209 words of simulated noisy typing (`AutocorrectBenchmark`): typo words corrected went from 70.3% to 83.9%, and wrong corrections from 5.1% to 0.8%. No correctly typed word was changed (0 of 1,091), and 0 of 30 names, brands and slang words were touched (previously 2).

Glide on 2,000 real human swipes from the swipe.futo.org test split (`RealSwipeBenchmark`): 84.3% top-1 (86.4% for words in the dictionary). Without the word-pair context it is 83.3%.

### Language models

`tools/build_english_ngrams.py` builds `bigrams.bin`, `trigrams.bin`, `fourgrams.bin` and `fivegrams.bin` from:

- Tatoeba (weight 3)
- the swipe.futo.org sentence lists (weight 3)
- two shards of C4 (weight 0.1)

`tools/eval_predictions.py` measures them on held-out sentences. Commands are in the scripts' headers.

### Benchmarks

Instrumented benchmarks live in `reboard_keyboard/src/androidTest/.../bench` (`AutocorrectBenchmark`, `PredictionBenchmark`, `GlideBenchmark`, `RealSwipeBenchmark`). Run one on a device or emulator with:

```powershell
.\gradlew.bat :reboard_keyboard:assembleDebugAndroidTest
adb install -r -t build\reboard_keyboard\outputs\apk\androidTest\debug\reboard_keyboard-debug-androidTest.apk
adb shell am instrument -w -e class com.noxquill.rewordium.keyboard.bench.PredictionBenchmark com.noxquill.rewordium.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

Results are written to logcat and to the test app's external files directory.

## Build

Use JDK 17, Android SDK platform 37, Android NDK `28.2.13676358`, and CMake. Android Studio can install the SDK components.

From the repository root on Windows:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Build a signed release APK with:

```powershell
.\gradlew.bat :app:assembleRelease
```

The APK is written to `build/app/outputs/apk/release/app-release.apk`.

Build a Play Store bundle with:

```powershell
.\gradlew.bat :app:bundleRelease
```

The release build uses `android/app/google-services.json` for Firebase and `android/key.properties` plus the upload keystore for signing. Keep these files out of version control. CI receives Firebase configuration and signing credentials through repository secrets.

The application ID remains `com.noxquill.rewordium`. `versionCode` and `versionName` are read from `projectVersionCode` and `projectVersionName` in the local Gradle properties, with the current release values as fallback.

## Supporting services

Firebase Cloud Functions remain in `functions/`. The public site and account deletion pages remain under `website/` and `website2.0/`.

## Attribution

The keyboard includes code from the FlorisBoard/ReBoard projects and AOSP LatinIME. Its language models are derived from:

- Tatoeba sentences (CC BY 2.0 FR)
- the FUTO Swipe dataset's sentence lists (MIT, © 2025 FUTO; drawn from Mozilla Common Voice, CC0)
- C4 (allenai/c4, ODC-BY 1.0)

Real swipes from the FUTO Swipe dataset are used only for testing.

Open-source notices are available in the app under **Settings → Rewordium → Open-source licenses**, and in [NOTICE](NOTICE) and [LICENSE.md](LICENSE.md).
