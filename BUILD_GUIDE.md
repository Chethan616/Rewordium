# Rewordium — Build Guide

## SDK & Toolchain Versions (March 2026 Update)

### What Changed

| Component | Previous | Current |
|---|---|---|
| **Android Gradle Plugin (AGP)** | 8.7.0 | **8.13.2** |
| **Gradle Wrapper** | 8.10.2 | **8.14.4** |
| **Kotlin** | 2.1.0 | **2.1.21** |
| **KSP (Kotlin Symbol Processing)** | 2.1.0-1.0.29 | **2.1.21-2.0.2** |
| **NDK** | 27.0.12077973 (r27) | **28.2.13676358 (r28)** |
| **Android Build Tools** | 35.0.0 | **36.1.0** |
| **CMake** | 4.0.2 | **4.1.2** |
| **Rust Toolchain** | 1.83.0 | **1.85.0** |
| **compileSdk** | 36 | 36 (unchanged) |
| **targetSdk (app)** | 35 | **36** |
| **targetSdk (ReBoard)** | 36 | 36 (unchanged) |
| **minSdk** | 26 | 26 (unchanged) |
| **Compose BOM** | 2024.10.00 | **2026.02.01** |
| **Firebase BOM** | 33.14.0 | **34.10.0** |
| **Room** | 2.6.1 | **2.8.4** |
| **iOS Minimum Deployment Target** | 12.0 (mismatched) | **15.5** (aligned) |
| **flutter.minSdkVersion** (gradle.properties) | 21 (stale) | **26** (aligned) |
| **flutter.targetSdkVersion** (gradle.properties) | 33 (stale) | **36** (aligned) |
| **flutter.compileSdkVersion** (gradle.properties) | 33 (stale) | **36** (aligned) |

### What Was Removed

| Removed | Reason |
|---|---|
| `android.enableJetifier=true` | Deprecated — all dependencies are AndroidX |
| `android.suppressUnsupportedCompileSdk=36` | No longer needed with AGP 8.13.2 |
| `flutter.buildMode=debug` in gradle.properties | Was conflicting with `--release` CLI flag |
| `composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }` | Dead code — Compose Compiler Plugin handles this automatically |

---

## Media-panel feature configuration

### KLIPY GIF API key

The GIF tab uses the [KLIPY v1 REST API](https://klipy.com/developers). We originally targeted Tenor v2, but Google announced its sunset (new sign-ups closed **2026-01-13**, full shutdown **2026-06-30**), so all GIF traffic is on KLIPY — the ex-Tenor team's drop-in replacement that Discord and WhatsApp are also moving to.

1. Sign up at **klipy.com/developers** and create an app to get an API key.
2. Pass it at build time via a Gradle property:

```bash
./gradlew :reboard_keyboard:assembleDebug -PklipyApiKey=your_key_here
```

Or set it in your project-local `gradle.properties` (do **not** commit a real key — add to `.gitignore` if not already excluded):

```properties
klipyApiKey=your_key_here
```

When the key is missing or blank, the GIF tab renders an inline "Set KLIPY_API_KEY" hint instead of crashing — useful for fresh checkouts and CI builds.

KLIPY's free tier is lifetime; the API key embeds in the URL path (no header auth) and supports trending, search, and category endpoints — all wired in `KlipyClient.kt`.

### WhatsApp sticker packs

The Sticker tab reads installed packs from WhatsApp's public sticker content provider (`com.whatsapp.provider.sticker_content_provider`). No API key or permission prompt is needed — WhatsApp exports the provider for any installed app that lists `com.whatsapp` in its `<queries>` block (we do, see `AndroidManifest.xml`).

WhatsApp Business (`com.whatsapp.w4b`) is also queried. If neither app is installed, the panel falls back to the "User" tab only (stickers the user imported themselves via the `+` tile or share-sheet).

User-imported stickers live in `filesDir/user_stickers/` with a manifest at `filesDir/user_stickers/manifest.json` — atomic writes (tmp + rename) protect against partial writes on a process kill.

---

## Building on a New Computer

### Prerequisites (All Platforms)

| Tool | Version | Download |
|---|---|---|
| **Flutter SDK** | 3.41.x (stable) | https://docs.flutter.dev/get-started/install |
| **JDK** | 17 | https://adoptium.net/ |
| **Android SDK** | via Android Studio or cmdline-tools | https://developer.android.com/studio |
| **Rust** | 1.85.0+ | https://rustup.rs/ |

> **Why Rust?** The ReBoard keyboard module includes a native Rust library (`reboard_lib/native`) that cross-compiles to Android ARM64.

---

### Windows

#### 1. Install Flutter

```powershell
# Download Flutter SDK from https://docs.flutter.dev/get-started/install/windows/mobile
# Extract to C:\flutter (or any path without spaces)
# Add to PATH:
[Environment]::SetEnvironmentVariable("PATH", "C:\flutter\bin;$env:PATH", "User")
```

#### 2. Install JDK 17

Download from https://adoptium.net/ and install. Verify:

```powershell
java -version
# Should show: openjdk version "17.x.x"
```

#### 3. Install Android SDK

Install Android Studio **or** just the command-line tools. Flutter will auto-download the required SDK components (NDK 28, CMake 4.1.2, Build Tools 36.1.0, Platform 36) on first build.

Accept licenses:

```powershell
flutter doctor --android-licenses
```

#### 4. Install Rust Toolchain

```powershell
# Download and run https://win.rustup.rs/x86_64
# Or use winget:
winget install Rustlang.Rustup

# Install the GNU toolchain (does NOT require Visual Studio):
rustup toolchain install 1.85.0-x86_64-pc-windows-gnu
rustup default 1.85.0-x86_64-pc-windows-gnu

# Add Android cross-compilation target:
rustup target add aarch64-linux-android
```

> **Important:** Use the `-gnu` toolchain, not `-msvc`. The MSVC toolchain requires Visual Studio Build Tools with C++ workload. The GNU toolchain includes its own linker.

#### 5. Clone & Build

```powershell
git clone <repo-url>
cd YC_startup-main

# Get Flutter packages
flutter pub get

# Verify setup
flutter doctor

# Build debug APK
flutter build apk --debug

# Build release APK
flutter build apk --release

# Build App Bundle (for Play Store)
flutter build appbundle --release
```

Output: `build/app/outputs/flutter-apk/app-release.apk`

#### Troubleshooting (Windows)

| Problem | Fix |
|---|---|
| `Rust toolchain not found` | Ensure `%USERPROFILE%\.cargo\bin` is in PATH. Restart terminal after installing Rust. |
| `link.exe not found` | You're using the MSVC toolchain. Switch to GNU: `rustup default 1.85.0-x86_64-pc-windows-gnu` |
| `CMake not found in SDK` | Run: `sdkmanager "cmake;4.1.2"` |
| `"parent" is null` during build | Run `flutter clean` then delete the `build/` and `android/.gradle/` folders. |
| Symlink warning | Enable Developer Mode: `start ms-settings:developers` |

---

### macOS

#### 1. Install Flutter

```bash
# Using Homebrew (recommended):
brew install flutter

# Or download manually from https://docs.flutter.dev/get-started/install/macos
# Add to PATH in ~/.zshrc:
export PATH="$HOME/flutter/bin:$PATH"
```

#### 2. Install JDK 17

```bash
brew install openjdk@17

# Add to PATH:
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### 3. Install Android SDK

```bash
# Option A: Install Android Studio from https://developer.android.com/studio
# Option B: Command-line only:
brew install --cask android-commandlinetools

# Accept licenses:
flutter doctor --android-licenses
```

#### 4. Install Xcode (for iOS builds)

```bash
# Install from Mac App Store, then:
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -runFirstLaunch

# Install CocoaPods:
brew install cocoapods
```

#### 5. Install Rust Toolchain

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env

# Install specific version:
rustup toolchain install 1.85.0
rustup default 1.85.0

# Add Android cross-compilation target:
rustup target add aarch64-linux-android
```

#### 6. Clone & Build

```bash
git clone <repo-url>
cd YC_startup-main

flutter pub get
flutter doctor
```

**Build Android APK:**

```bash
flutter build apk --release
# Output: build/app/outputs/flutter-apk/app-release.apk

flutter build appbundle --release
# Output: build/app/outputs/bundle/release/app-release.aab
```

**Build iOS:**

```bash
# Debug (on simulator):
flutter build ios --debug --simulator

# Release (requires Apple Developer account + signing):
flutter build ios --release

# Archive for App Store:
flutter build ipa --release
# Output: build/ios/ipa/rewordium.ipa
```

> **Note:** iOS release builds require an Apple Developer account ($99/year) and proper code signing configured in Xcode.

#### Troubleshooting (macOS)

| Problem | Fix |
|---|---|
| `Rust toolchain not found` | Run `source $HOME/.cargo/env` or restart terminal |
| CocoaPods issues | Run `cd ios && pod install --repo-update` |
| Xcode command-line tools missing | Run `xcode-select --install` |
| iOS signing errors | Open `ios/Runner.xcworkspace` in Xcode, go to Signing & Capabilities, select your team |
| `CMake not found` | Run: `sdkmanager "cmake;4.1.2"` |

---

## Project Architecture

```
YC_startup-main/
├── lib/                        # Flutter/Dart app code
├── android/
│   ├── app/                    # Main Android app module
│   ├── reboard_keyboard/       # ReBoard keyboard module (Kotlin + Compose)
│   ├── reboard_lib/
│   │   ├── android/            # Android utilities library
│   │   ├── color/              # Material color library
│   │   ├── compose/            # Compose UI components
│   │   ├── kotlin/             # Pure Kotlin utilities (JVM)
│   │   ├── native/             # Native module (Rust + C via CMake)
│   │   └── snygg/              # Theming/styling library
│   ├── reboard_libnative/      # Rust dummy crate
│   └── gradle/
│       ├── libs.versions.toml  # Dependency version catalog
│       └── tools.versions.toml # Build tool versions (NDK, CMake, Rust)
├── ios/                        # iOS runner
├── web/                        # Web support
├── windows/                    # Windows desktop support
├── linux/                      # Linux desktop support
├── macos/                      # macOS desktop support
└── pubspec.yaml                # Flutter dependencies
```

## Key Configuration Files

| File | Purpose |
|---|---|
| `android/settings.gradle.kts` | Plugin versions (AGP, Kotlin, KSP) |
| `android/gradle.properties` | SDK versions, build flags, ReBoard config |
| `android/gradle/libs.versions.toml` | Dependency version catalog (Compose, Room, etc.) |
| `android/gradle/tools.versions.toml` | Build tool versions (NDK, CMake, Rust, Build Tools) |
| `android/app/build.gradle.kts` | App module config (compileSdk, targetSdk, dependencies) |
| `android/gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper version |
| `pubspec.yaml` | Flutter SDK constraint and Dart dependencies |

## Quick Reference

```bash
# Clean everything
flutter clean
cd android && ./gradlew clean && cd ..

# Full rebuild
flutter pub get
flutter build apk --release

# Run on connected device
flutter run

# Check setup
flutter doctor -v
```
