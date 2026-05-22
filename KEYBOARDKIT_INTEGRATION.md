# KeyboardKit iOS Integration

iOS Custom Keyboard Extension for Rewordium, built on [KeyboardKit](https://github.com/KeyboardKit/KeyboardKit) v10.5.x (MIT-licensed free tier).

Replaces the earlier AzooKey-based attempt. AzooKey was Japanese-first and we hit four CI failures (llama.cpp binary target, SwiftyMarisa resolution, `PRODUCT_NAME=""`, Podfile path) trying to bend it into an English keyboard for an AI writing app. KeyboardKit is purpose-built for the architecture we wanted: a clean keyboard shell + your own toolbar.

## Architecture

```
┌──────────────────────────────────────────┐
│  Flutter host app (Runner)               │
│  ─ AuthProvider, KeyboardProvider, etc.  │
│  ─ KeyboardSettingsBridge.swift          │ ← MethodChannel handler
│  ─ ios_keyboard_bridge.dart (Dart side)  │
└────────────────┬─────────────────────────┘
                 │ writes to App Group
                 ▼
   UserDefaults(suiteName: "group.com.noxquill.rewordium")
                 │ reads
                 ▲
┌────────────────┴─────────────────────────┐
│  RewordiumKeyboard.appex                 │
│  ─ KeyboardViewController                │
│  ─ SwiftUI: AIToolbar + KeyboardView     │
│  ─ Services: SharedSettings / GroqClient │
│  ─ KeyboardKit (SPM, remote)             │
└──────────────────────────────────────────┘
```

## Build flow on Codemagic

1. `flutter pub get` — generates `ios/Flutter/Generated.xcconfig`.
2. Restore Firebase config from base64 secret.
3. Set host bundle ID via PlistBuddy.
4. **Wire extension target** — `ruby ios/scripts/setup_keyboard_target.rb`. Idempotent. Adds KeyboardKit SPM ref, creates `RewordiumKeyboard` target, links the SPM product, embeds the `.appex` into Runner, attaches App Group entitlements to both targets.
5. **Resolve SwiftPM packages** — `xcodebuild -resolvePackageDependencies`. Fetches KeyboardKit (~30 MB) into `build/ios/SourcePackages` on cold runs; cached otherwise.
6. **Build** — `flutter build ios` (which runs `pod install`) then `xcodebuild -workspace ... build` with `CODE_SIGNING_ALLOWED=NO`.
7. Package unsigned IPA for Sideloadly.

No git submodules, no binary targets to wrangle, no dictionary downloads.

## Files

```
ios/
├─ Podfile                           ← iOS 17.0 platform; standard Flutter template
├─ Runner/
│  ├─ AppDelegate.swift               ← registers KeyboardSettingsBridge
│  ├─ KeyboardSettingsBridge.swift    ← MethodChannel "com.noxquill.rewordium/keyboard_settings"
│  ├─ Runner.entitlements             ← App Group membership
│  └─ Info.plist                      ← (existing)
├─ RewordiumKeyboard/
│  ├─ KeyboardViewController.swift    ← entry point, subclass of KeyboardInputViewController
│  ├─ Info.plist                      ← extension manifest, RequestsOpenAccess=true
│  ├─ RewordiumKeyboard.entitlements  ← App Group membership
│  ├─ PrivacyInfo.xcprivacy           ← privacy manifest
│  ├─ Models/
│  │  └─ AIAction.swift               ← enum + prompt templates
│  ├─ Services/
│  │  ├─ SharedSettings.swift         ← App Group reader
│  │  ├─ GroqClient.swift             ← bare URLSession Groq client
│  │  └─ AIService.swift              ← @Observable façade
│  └─ Views/
│     ├─ RewordiumKeyboardView.swift  ← VStack(AIToolbar, KeyboardView)
│     ├─ AIToolbar.swift              ← morphing surface (collapsed/grid/loading/result)
│     └─ RewordiumMaterial.swift      ← spacing/radius/animation tokens
└─ scripts/
   └─ setup_keyboard_target.rb         ← pbxproj wiring (xcodeproj gem)

lib/services/
└─ ios_keyboard_bridge.dart            ← Dart wrapper for the MethodChannel
```

## How settings flow from Flutter to the keyboard

Flutter side (in your settings or auth code):

```dart
import 'services/ios_keyboard_bridge.dart';

await IosKeyboardBridge.writeSettings(
  groqAPIKey: currentUser.groqProxyToken,   // short-lived, server-issued
  groqModel:  'qwen/qwen3-32b',
  hapticsEnabled: true,
  aiEnabled: true,
);
```

Native side (the extension), via `SharedSettings`:

```swift
let key = SharedSettings.groqAPIKey
let model = SharedSettings.groqModel
```

Call `IosKeyboardBridge.writeSettings(...)`:
- on login (after Groq token is minted),
- on settings changes (model, haptics, etc.),
- on sign-out (`IosKeyboardBridge.clear()`).

## What's in v1

* 8 AI actions: Rewrite, Shorten, Expand, Professional, Friendly, Fix grammar, Translate, Custom.
* Inline morphing toolbar — no modal sheets.
* Selected-text aware: acts on selection if there is one, else falls back to the last sentence before the cursor.
* Spring animations matching the system keyboard's predictive bar feel.
* Light/dark/dynamic-type inherited from KeyboardKit's stock keyboard view.
* Haptics on chip taps and apply.

## What's deferred (with reasons)

| Item | Why deferred | When to revisit |
|---|---|---|
| **Autocomplete / next-word predictions** | KeyboardKit Pro only ($99–499/yr depending on tier). iOS's system suggestions still appear in our text proxy. | If users complain that typing feels worse than the system keyboard. |
| **Emoji keyboard** | Pro only. Users can long-press the globe key for iOS's emoji keyboard. | Same trigger as above. |
| **Themes** | Pro only. Stock keyboard already inherits dark/light. | Phase 5 once we know what users actually want themed. |
| **Glide / swipe typing** | Not in KeyboardKit. Implementing it well requires a touch-path tracker + an n-gram or transformer model. ~weeks of work, not a hackathon feature. | Likely never — system keyboard glide-types into our textDocumentProxy anyway when the user switches keyboards. |
| **Streaming Groq responses** | Groq returns full responses in <2s at our token budget; streaming adds complexity to the SSE parser without a clear UX win. | If we move to larger models that take >5s. |
| **iOS 26 Liquid Glass material** | The `.glassEffect()` modifier is iOS 26 only. We're using `.ultraThinMaterial` for now. | When iOS 26 release/adoption justifies the gated path. Code already centralized in `RewordiumMaterial.swift` — one place to flip. |
| **Firebase / Auth in the extension** | Hard 60 MB memory ceiling; Firebase init alone is ~30 MB. | Don't — the host app writes a short-lived proxy token into the App Group instead. |

## Constraints worth remembering

* **Extension memory ceiling ≈ 60 MB resident.** iOS terminates keyboards that exceed it. Don't import Firebase, image scanners, PDF parsers, or anything that ships large static data. The extension's only network call is to Groq.
* **`RequestsOpenAccess=true`** is required so the extension can read the App Group and reach Groq. This triggers a user-visible "Allow Full Access" prompt when they enable the keyboard. Disclose this in onboarding.
* **No secrets in the binary.** Even though Sideloadly builds are unsigned right now, anything in the `.appex` is extractable. The Groq API key lives in the App Group container which is per-device — that's the floor for v1. The right answer once we have a paid Apple Dev account is a server-issued short-lived proxy token.
* **App Store review** for Custom Keyboards adds ~1 week to standard review times. Allow buffer.

## Apple Developer account: you don't need it yet

Codemagic produces unsigned IPAs (`CODE_SIGNING_ALLOWED=NO`). Sideloadly with a free Apple ID can install the host app. The keyboard extension installs alongside, but:

* iOS lets the user **add** the keyboard in Settings → Keyboards → Add New Keyboard → Rewordium.
* iOS will **not** let a keyboard with `RequestsOpenAccess=true` actually enable Full Access on a free dev account. The user can use the keyboard, but the AI button will report `missingAPIKey` because the App Group container isn't accessible from the extension's sandbox without that toggle.

Once you buy the paid Apple Developer Program ($99/yr):

1. Connect it to Codemagic.
2. Provision two profiles: `com.noxquill.rewordium` (host) and `com.noxquill.rewordium.keyboard` (extension). Both need the `com.apple.security.application-groups` entitlement.
3. Flip the codemagic.yaml build args to use the signing identity.

Nothing in the codebase changes — the bridge code, the entitlements, and the App Group ID are all in place already.

## Verification checklist for the next CI build

After Codemagic kicks off:

1. **flutter pub get** — green.
2. **Wire RewordiumKeyboard extension target** — prints "Created target", "Added N Swift sources", "Linked SPM product: KeyboardKit", "Embedded RewordiumKeyboard.appex into Runner".
3. **Resolve SwiftPM packages** — fetches KeyboardKit. ~30 MB on first run.
4. **flutter build ios** — generates Generated.xcconfig, runs pod install, then xcodebuild compiles everything. First build ~5 min; cached subsequent builds ~2 min.
5. **xcodebuild** — produces `Runner.app` with `PlugIns/RewordiumKeyboard.appex` inside.
6. **Package** — IPA at `rewordium-unsigned.ipa`. Verify with `unzip -l rewordium-unsigned.ipa | grep PlugIns`.

If a step fails, paste the log — most likely failure modes are KeyboardKit API surface shifts (we pinned `upToNextMinor` from 10.5.0, so 10.5.x patches are fine; 10.6+ would need re-checking the `KeyboardApp` initializer and `KeyboardView` parameter list) or missing setup methods that I guessed at without local Xcode.
