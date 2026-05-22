# AzooKey iOS Integration Plan

Status: planning only. No code in this repo touches AzooKey yet.

This document is the iOS counterpart to the FlorisBoard-derived `reboard_keyboard` / `reboard_lib:*` Gradle modules wired into `android/settings.gradle.kts`. The goal is the same architecture on iOS: a single forked keyboard codebase, vendored as a module of the Flutter project, that the main Runner target embeds as a Keyboard Extension and configures over a platform channel.

---

## 1. Why AzooKey

| | FlorisBoard (Android) | AzooKey (iOS) |
|---|---|---|
| License | Apache-2.0 | Apache-2.0 |
| Native lang | Kotlin + Rust (NLP) | Swift + SwiftUI (+ Swift kana converter) |
| Architecture | IME service in app process | App Extension (KeyboardExtension target) |
| Glide / swipe | yes | yes |
| Theming | snygg / json | Swift theme structs |
| Status | mature, active | mature, active (Japanese-first; English & extensibility shipping) |

AzooKey is the closest iOS analogue: open source, App Store-shipped, no UIKit-only legacy, and the input-engine layer is already decoupled from the UI. The Swift module structure makes it embeddable without rewriting it.

Repo to fork (you said you already did): `azooKey/azooKey`. Keep your fork on a long-lived branch (e.g. `rewordium-main`) so you can rebase from upstream without touching `main`.

---

## 2. Target Architecture

Mirror the Android module split. On iOS the unit is an Xcode target inside `ios/Runner.xcworkspace`, not a Gradle subproject, but the mental model is identical.

```
ios/
├─ Runner/                        ← existing Flutter container (app, lives in iOS_secrets group)
├─ RewordiumKeyboard/             ← NEW. Custom Keyboard App Extension target.
│   ├─ Info.plist                 ← NSExtension declaration (see §5)
│   ├─ KeyboardViewController.swift  ← thin shim, inherits AzooKey's KeyboardViewController
│   └─ KeyboardEntry.swift        ← imports the AzooKey package(s), boots the UI
├─ RewordiumKeyboardCore/         ← optional internal Swift package (see §3.2)
└─ Packages/
    └─ azooKey/                   ← submodule pointing at your AzooKey fork
```

Why this layout:

* `Packages/azooKey/` is the vendored source — `Packages/` is the conventional Xcode path for local SPM packages and stays out of CocoaPods' way.
* `RewordiumKeyboard/` is the App Extension target that the App Store accepts as a "Custom Keyboard". It is *thin*: it imports types from the AzooKey package and instantiates the controller. All logic lives in the package, not the target.
* `RewordiumKeyboardCore/` is optional. Use it if you want a place for Rewordium-specific glue (telemetry, persona injection, paraphraser button wiring) that isn't part of AzooKey upstream — keeps the fork lean and rebase-able.

Equivalent on Android (today):

| Android module                       | iOS counterpart                       |
|---                                   |---                                    |
| `:reboard_keyboard` (the IME app)    | `RewordiumKeyboard/` (App Extension)  |
| `:reboard_lib:compose` / `:snygg` UI | AzooKey UI Swift package products     |
| `:reboard_lib:native` (Rust)         | AzooKey's Swift `KanaKanjiConverter` etc. (no Rust on iOS) |
| `:reboard_libnative` (Rust prebuilt) | n/a (AzooKey is pure Swift)           |

---

## 3. Vendoring AzooKey

### 3.1 Submodule (recommended)

```
git submodule add https://github.com/<your-user>/azooKey ios/Packages/azooKey
cd ios/Packages/azooKey
git checkout rewordium-main      # your long-lived branch
```

Add `ios/Packages/azooKey` to the workspace by dropping the folder onto `Runner.xcworkspace` in Xcode — Xcode resolves it as a local SPM package. The package products you'll consume:

* `KeyboardViews` (the SwiftUI keyboard UI)
* `KanaKanjiConverterModule` (the IME engine; you may not need this for English-only — see §6)
* `SwiftUtils`, `KeyboardThemes`, etc., as transitive deps

Avoid `pod`-ing AzooKey. CocoaPods is already used by Flutter plugins; piling a second source-of-truth on top causes Pod/SPM cache fights on Codemagic.

### 3.2 Rewordium-side glue package

Create `ios/RewordiumKeyboardCore/Package.swift` (one library product) and depend on it from the App Extension target. Keep upstream AzooKey as the only thing your fork modifies; everything Rewordium-specific (paraphraser channel, persona config decoder, FCM-driven settings reload) lives here and consumes AzooKey via its public API. This is the same separation as `:reboard_keyboard` vs. `:reboard_lib:*` on Android — you can rebase AzooKey from upstream without touching Rewordium code.

---

## 4. Talking to Flutter — Platform Channel + App Group

The Android `MethodChannel("com.noxquill.rewordium/rewordium_keyboard")` does two jobs: live RPC (open settings, refresh) and settings push (theme, haptics, AI on/off). iOS extensions cannot receive `MethodChannel` calls directly because they run in a separate process from the Flutter engine, so split those:

1. **Settings push → App Group `UserDefaults` (`group.com.noxquill.rewordium`)**
   * Add the App Groups entitlement to *both* `Runner` and `RewordiumKeyboard` targets.
   * Flutter writes via a tiny Swift bridge method on the existing `com.noxquill.rewordium/rewordium_keyboard` channel; the extension reads via `UserDefaults(suiteName:)`.
   * Settings to mirror today: `themeColor`, `darkMode`, `hapticFeedback`, `aiSuggestions`, `autoCapitalize`, `doubleSpacePeriod`, `personas` (list).
   * AzooKey already has a settings reload mechanism; trigger it from `viewWillAppear` or via `Darwin notify_post` from the app side for instant-apply.

2. **Live RPC from Flutter → Runner side only**
   * `isKeyboardEnabled` / `isKeyboardSelectedAsDefault`: there is no public iOS API for either. Best you can do is "has the user ever launched the extension?" — store a flag from the extension into the App Group on first `viewDidLoad`, and read it from Flutter. Treat the answer as advisory, not authoritative.
   * `openKeyboardSettings`: open `UIApplication.openSettingsURLString`. iOS does not deep-link to the Keyboards pane; the user lands on the Rewordium app settings page and taps **Keyboards** themselves. Show a one-shot coachmark in onboarding explaining the two taps.
   * `showInputMethodPicker`: no iOS equivalent. Long-press the globe key is the user gesture. Replace the call with a tooltip in onboarding.

3. **Reverse channel (extension → Flutter)**
   * Use `URL(string: "rewordium://...")` and `extensionContext?.open(_:completionHandler:)`. You already have the `rewordium://` scheme registered in `Info.plist`.
   * Wire it through the existing `DeepLinkService` on the Flutter side.

---

## 5. App Extension Plist (sketch — do not commit yet)

```xml
<key>NSExtension</key>
<dict>
  <key>NSExtensionAttributes</key>
  <dict>
    <key>IsASCIICapable</key>           <false/>
    <key>PrefersRightToLeft</key>       <false/>
    <key>PrimaryLanguage</key>          <string>en-US</string>
    <key>RequestsOpenAccess</key>       <true/>   <!-- needed for App Group + network -->
  </dict>
  <key>NSExtensionPointIdentifier</key> <string>com.apple.keyboard-service</string>
  <key>NSExtensionPrincipalClass</key>  <string>$(PRODUCT_MODULE_NAME).KeyboardViewController</string>
</dict>
```

`RequestsOpenAccess=true` is the price of letting the keyboard read App Group settings, talk to Firebase, or call Groq. It triggers a one-time iOS warning; the App Store reviews this — be ready to explain in the privacy nutrition label (see §8).

---

## 6. Engine + Language Scope

AzooKey ships the Japanese kana-kanji converter by default. For Rewordium's English-first audience you have two choices:

* **Embed but disable.** Ship `KanaKanjiConverterModule`, but only run it when the user picks Japanese. Negligible binary cost (~few MB after stripping), zero risk. Recommended for v1.
* **Strip it.** Modify the SPM `Package.swift` in your fork to make the converter a separate product and exclude it from the keyboard target. Saves binary but creates a rebase pain point with upstream forever.

Either way, the *English* path uses AzooKey's predictive layer (`SwiftUtils` n-gram + system text replacements). You do **not** need a separate engine; the Rust `:reboard_libnative` on Android exists because FlorisBoard's English engine wasn't good enough — AzooKey's is.

Glide typing: AzooKey has it. Keep it on by default; mirror the Android `setGlideTypingEnabled` flag through the App Group.

---

## 7. Build Wiring (Codemagic)

Add to `codemagic.yaml` *only when the extension exists*:

```yaml
- name: Resolve SPM packages
  script: xcodebuild -resolvePackageDependencies -workspace ios/Runner.xcworkspace -scheme Runner

- name: Build extension target as part of Runner
  # The Runner scheme should already depend on the RewordiumKeyboard target via "Embed Foundation Extensions"
  # If not, add it under: Runner target → General → Frameworks, Libraries, and Embedded Content
```

CocoaPods does not own the extension target, so `pod install` does not need changes — but the Podfile should explicitly target only Runner:

```ruby
target 'Runner' do
  use_frameworks!
  flutter_install_all_ios_pods File.dirname(File.realpath(__FILE__))
end
# do NOT add a target 'RewordiumKeyboard' do … end block — Flutter plugins aren't extension-safe.
```

Memory ceiling: Custom Keyboard extensions are killed at ~48–60 MB resident. Anything heavy (Firebase init, AI model downloads, image scanning) must stay in the app, not the extension. The extension speaks to Groq directly over HTTPS with a short-lived token written to the App Group by the app — never embed long-lived secrets in the extension binary, App Store scans for them.

---

## 8. App Store Submission Notes

* **Open Access prompt.** Required disclosure in App Store description: "The Rewordium Keyboard requests Allow Full Access to provide AI suggestions; text is sent to Groq only when you tap Paraphrase." Mirror in onboarding.
* **Privacy Nutrition Label.** Add a "Other Diagnostic Data" or "User Content" entry under "Data Linked to You" if you log paraphrases. If you don't log, declare nothing for the extension.
* **Encryption.** Already set: `ITSAppUsesNonExemptEncryption=false` in `ios/Runner/Info.plist`. The extension inherits.
* **Background.** Extensions cannot run in background. All Android background flows (FCM-driven persona sync, force-update prompts) stay in the host app.

---

## 9. Phasing

| Phase | Scope | Deliverable |
|---|---|---|
| 0 (today) | Plan only | This doc. No targets created. |
| 1 | Skeleton extension | Empty `RewordiumKeyboard` target that bridges App Group settings and shows a blank keyboard. Verifies entitlements + Codemagic build. |
| 2 | Vendor AzooKey | Submodule, SPM resolution green, AzooKey UI renders inside the extension. No Rewordium customisation yet. |
| 3 | Settings bridge | Mirror the Android `setHapticFeedback` / `setAiSuggestions` / `updateThemeColor` flow through App Group; verify live-apply works without restarting the extension. |
| 4 | Paraphraser button | Custom toolbar row from `RewordiumKeyboardCore` that calls Groq and inserts a replacement; matches the Android paraphraser UX. |
| 5 | Persona + AI settings | Personas array in App Group; paraphraser uses active persona prompt. Mirror Android `updateKeyboardPersonas`. |
| 6 | Submit | TestFlight → App Store with Open Access disclosure. |

Hard rule: do **not** start phase 1 until the iOS app ships to TestFlight without the extension. The current build pipeline is fragile enough; don't bundle a custom keyboard into the first iOS release.

---

## 10. Open Questions

* Do you want Glide on by default on iOS, or behind the same toggle as Android? (Default: on, parity with Android.)
* Should the iOS keyboard support all current personas, or start with one (Default) for v1?
* Telemetry: do you want extension-side `usage_analytics_service` events, or only app-side? (App-only avoids the memory + privacy surface area.)
* Onboarding copy: the iOS "enable keyboard" flow has two taps (Settings → Keyboard → Keyboards → Add Rewordium → enable Allow Full Access). Worth a dedicated illustration step.
