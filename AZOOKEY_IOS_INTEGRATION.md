# AzooKey iOS Integration Plan

**Status:** Phases 0 → 2 implemented file-wise. Awaiting first CI build with the extension target wired.

Source lives at `ios/Packages/azooKey/` (vendored, read-only). Wired into Runner via `ios/scripts/setup_keyboard_target.rb`. Phases 3 → 5 are not started.

The guiding rule: *get it building first, then customize.* Each phase compiles, runs, and ships. We do not translate, strip, or refactor AzooKey internals until after the first successful TestFlight build that embeds it.

---

## 0. What's already on disk

```
rewordium/
├─ ios/                                     ← Flutter iOS host
└─ azooKey-main/azooKey-main/                ← upstream snapshot (preserve)
   ├─ azooKey.xcodeproj                      ← original Xcode project
   ├─ AzooKeyCore/                           ← local SPM package (5 libraries)
   │   └─ Sources/
   │       ├─ KeyboardViews/                 ← the actual SwiftUI keyboard
   │       ├─ KeyboardThemes/
   │       ├─ KeyboardExtensionUtils/
   │       ├─ AzooKeyUtils/
   │       └─ SwiftUIUtils/
   ├─ Keyboard/                              ← Custom Keyboard extension sources
   │   ├─ KeyboardViewController.swift       ← UIInputViewController entry
   │   ├─ Info.plist
   │   └─ Keyboard.entitlements
   ├─ MainApp/                               ← AzooKey's own host app (we WON'T ship this)
   ├─ Resources/                             ← icon font, designs, strings
   ├─ azooKey_dictionary_storage/            ← Japanese dictionary
   └─ azooKey_emoji_dictionary_storage/      ← emoji dictionary
```

Upstream remote dependencies (declared in `AzooKeyCore/Package.swift`):
* `https://github.com/azooKey/AzooKeyKanaKanjiConverter` — the Japanese engine
* `https://github.com/azooKey/CustardKit`

Both are fetched by SwiftPM at build time. We don't vendor them.

---

## 1. Constraints we have to accept

| Concern | Constraint | Why it matters |
|---|---|---|
| Swift tools | 6.2 (Xcode 16+) | `AzooKeyCore/Package.swift` declares `swift-tools-version: 6.2`. Codemagic must run with `xcode: latest`. |
| iOS minimum | 17.0 | `AzooKeyCore` declares `platforms: [.iOS(.v17)]`. Rewordium currently targets 15.5 — must bump. |
| C++ interop | `.interoperabilityMode(.Cxx)` | The package builds against `KanaKanjiConverter` which uses C++. Don't add Obj-C bridging headers to the extension target without testing. |
| Dictionary size | ~20–30 MB | Bundled as resources; counts against the 200 MB compressed app limit but is fine. |
| Extension RAM | iOS hard-kills custom keyboards at ~48–60 MB resident | AzooKey's Zenzai (neural converter) is heavy; we keep its default Japanese-only activation. Don't add a second engine. |

Mismatched constraints we'll resolve in Phase 0.

---

## 2. Phase plan (matches user spec)

### Phase 0 — Stabilize current iOS app + Codemagic builds (where we are now)

**Goal:** iOS build is green on Codemagic without any AzooKey code.

Already done in earlier commits:
* `codemagic.yaml` configured for iOS Sideloadly build
* Camera / photo library / non-exempt encryption descriptions in `ios/Runner/Info.plist`
* App icon set populated with the Rewordium logo
* Onboarding flow adapted for iOS (no assistant mode, no keyboard step)
* Settings screen hides Android-only IME row on iOS

Outstanding before Phase 1:
* Get one successful Codemagic IPA build that installs via Sideloadly on a real device
* Confirm Firebase iOS push notifications work end-to-end
* Bump iOS deployment target → 17.0 in `ios/Podfile` and Xcode project (required for AzooKey)

**Exit criterion:** Rewordium iOS app launches on a physical iPhone, signs in, paraphrases a sentence, and shuts down without crashing. No AzooKey involvement yet.

---

### Phase 1 — Import AzooKey source into `ios/Packages/` ✅ DONE

**Goal:** AzooKey source lives inside the iOS project as a local SPM package. Runner still builds without referencing it. No extension target yet.

Mechanical steps:

1. Move (don't copy) the unpacked tree from `rewordium/azooKey-main/azooKey-main/` to `rewordium/ios/Packages/azooKey/`. Keep the *exact* internal layout — `Package.swift` paths are relative.
   ```
   ios/Packages/azooKey/
     ├─ AzooKeyCore/Package.swift     ← the one Xcode will resolve
     ├─ Keyboard/...                  ← extension sources (used in Phase 2)
     ├─ Resources/...
     ├─ azooKey_dictionary_storage/
     └─ azooKey_emoji_dictionary_storage/
   ```

2. Add the local package to `Runner.xcworkspace`:
   * In Xcode: **File → Add Package Dependencies → Add Local…** → pick `ios/Packages/azooKey/AzooKeyCore`.
   * Do **not** add any package products to a target yet. We just want Xcode to resolve it and SwiftPM to download `AzooKeyKanaKanjiConverter` + `CustardKit`.

3. Update `ios/Podfile`:
   ```ruby
   platform :ios, '17.0'   # was 15.5
   ```
   Then in the Runner Xcode project settings, set `IPHONEOS_DEPLOYMENT_TARGET = 17.0` for Debug / Release / Profile.

4. In `codemagic.yaml`, add a pre-build step to resolve SwiftPM dependencies so the first build doesn't time out:
   ```yaml
   - name: Resolve SwiftPM packages
     script: |
       xcodebuild -resolvePackageDependencies \
         -workspace ios/Runner.xcworkspace \
         -scheme Runner
   ```

5. Run `flutter build ios --release --no-codesign` locally and on Codemagic. Both must succeed.

**Exit criterion:** Same IPA as Phase 0 (no behavior change), but `ios/Packages/azooKey/` is checked in and SwiftPM resolves cleanly on a fresh clone.

**Do not touch:** any `.swift` file inside `ios/Packages/azooKey/`. Treat it as read-only.

---

### Phase 2 — Lightweight `RewordiumKeyboard` extension target, AzooKey UI unchanged 🟡 SCAFFOLDED (awaiting first CI build)

**Goal:** A Custom Keyboard extension that the user can enable in iOS Settings and that renders the original AzooKey keyboard — Japanese candidates, conversion bar, kana/romaji, all of it. No Rewordium UI added. No English-only behavior.

Mechanical steps:

1. Create a new target in `ios/Runner.xcodeproj`:
   * Template: **iOS → Application Extension → Custom Keyboard Extension**
   * Name: `RewordiumKeyboard`
   * Bundle identifier: `com.noxquill.rewordium.keyboard`
   * Embed in: `Runner`
   * iOS deployment target: 17.0

2. Replace the generated `RewordiumKeyboard/KeyboardViewController.swift` with a one-line shim that delegates to AzooKey's controller:
   ```swift
   import UIKit
   import KeyboardViews
   import AzooKeyUtils

   @MainActor
   final class KeyboardViewController: UIInputViewController {
       private var hosted: UIViewController?

       override func viewDidLoad() {
           super.viewDidLoad()
           // Instantiate AzooKey's controller via its public API. Copy the
           // initializer call from azooKey-main/Keyboard/KeyboardViewController.swift
           // exactly — that file remains the source of truth.
           // …
       }
   }
   ```
   In practice, the cleanest move is to **add `azooKey-main/Keyboard/*.swift` to the `RewordiumKeyboard` target's Compile Sources** (Build Phases → Compile Sources → add the files from `ios/Packages/azooKey/Keyboard/`), then point the target's `Info.plist` and `.entitlements` at the AzooKey originals or copy them verbatim. The principal class stays `KeyboardViewController` from the AzooKey package.

3. Link the extension target against these AzooKey package products (Target → General → Frameworks, Libraries, and Embedded Content):
   * `KeyboardViews`
   * `KeyboardThemes`
   * `KeyboardExtensionUtils`
   * `AzooKeyUtils`
   * `SwiftUIUtils`
   * (transitively) `KanaKanjiConverterModule` from the remote package

4. Copy dictionary resources into the extension bundle:
   * Drag `ios/Packages/azooKey/azooKey_dictionary_storage/` and `azooKey_emoji_dictionary_storage/` into the target's **Copy Bundle Resources** phase. AzooKey's runtime looks for them at the bundle root.

5. Entitlements: create `ios/RewordiumKeyboard/RewordiumKeyboard.entitlements` based on `Keyboard.entitlements` from AzooKey. Add the App Group identifier we'll need in Phase 3 even if we don't use it yet:
   ```xml
   <key>com.apple.security.application-groups</key>
   <array>
     <string>group.com.noxquill.rewordium</string>
   </array>
   ```

6. Set `RequestsOpenAccess = true` in `RewordiumKeyboard/Info.plist`. Required to read the App Group from the extension (and later to talk to Groq). This triggers a one-time iOS warning when the user enables full access — disclose it in onboarding.

7. Build for device. Install, open Settings → General → Keyboard → Keyboards → Add New Keyboard → **Rewordium**. Enable "Allow Full Access". Long-press the globe in any text field and switch to Rewordium. **You should see the AzooKey keyboard, in Japanese, exactly as upstream ships it.**

**Exit criterion:** Original AzooKey keyboard renders inside Rewordium. Conversion, candidates, themes, settings (managed from AzooKey's own `MainApp` UI — we don't ship it, so users get defaults) all behave like the upstream app. No crashes when switching keyboards.

**Do not touch:** the keyboard UI, layouts, candidate engine, or themes. If something doesn't look right, the fix is upstream in AzooKey, not in our fork.

---

### Phase 3 — App Group + Flutter ↔ Extension settings bridge

**Goal:** The Flutter app can read and write a small, well-defined set of settings that the extension respects, without modifying AzooKey internals.

Architecture:

```
Flutter app (Runner)            Extension (RewordiumKeyboard)
    │                                │
    │  write to UserDefaults          │  read on viewWillAppear
    │  suiteName: "group.com.noxquill.rewordium"
    ▼                                ▼
            shared App Group container
```

Mechanical steps:

1. Add the App Group capability to **both** `Runner` and `RewordiumKeyboard` targets (must match exactly: `group.com.noxquill.rewordium`).

2. On the Runner side, add a tiny Swift bridge file `ios/Runner/KeyboardSettingsBridge.swift` exposing a `FlutterMethodChannel` named `com.noxquill.rewordium/keyboard_settings` with one method `write(Map<String, dynamic>)`. The Flutter side calls it from `RewordiumKeyboardService` (already exists for Android — add an iOS-aware path).

3. On the extension side, add `ios/Packages/azooKey/...` — **no**, do not edit AzooKey. Instead, in the `RewordiumKeyboard` target (our shim layer), add a `SharedSettings.swift` that reads from `UserDefaults(suiteName: "group.com.noxquill.rewordium")` and applies values to AzooKey via its existing public settings APIs (`KeyboardViews.KeyboardLayoutType`, `KeyboardThemes` selection, etc.). If AzooKey doesn't expose a public setter for something we need, that's a flag to either (a) skip the setting for now or (b) upstream a PR — never a local fork edit.

4. Settings to bridge in v1 (parity with Android `getKeyboardSettings`):
   | Key | Type | Notes |
   |---|---|---|
   | `darkMode` | Bool | maps to AzooKey theme |
   | `hapticFeedback` | Bool | AzooKey already supports this |
   | `themeColor` | String (hex) | mapped to a custom AzooKey theme |
   | `aiSuggestions` | Bool | gates Phase 4 toolbar |

   Skip until Phase 4 or 5: `autoCapitalize`, `doubleSpacePeriod`, `personas`. They need deeper AzooKey integration than the public API offers today.

5. Live reload: post a Darwin notification (`CFNotificationCenterPostNotification`) from the app when settings change; the extension listens and re-reads. Avoids waiting for the user to dismiss and re-open the keyboard.

**Exit criterion:** Toggling dark mode from Rewordium app changes the keyboard appearance on the next character typed. No restart of the extension required.

**Do not touch:** AzooKey source. All glue lives in `RewordiumKeyboard/` target files we wrote.

---

### Phase 4 — Rewordium AI actions (paraphraser) inside the extension

**Goal:** A single Rewordium-branded button in the keyboard toolbar that takes the current text-field content, sends it to Groq, and replaces the selection with the result. Memory-safe and App Store-safe.

Constraints:

* **Memory.** Custom keyboards die at ~48–60 MB resident. Do not initialize Firebase, do not load image scanners, do not import `cunning_document_scanner` or `syncfusion_pdf` here. The extension does one thing: HTTPS POST to Groq.
* **Auth.** The extension cannot use Firebase Auth. Instead, the host app writes a short-lived (1 hour) Groq proxy token into the App Group; the extension uses it. Token refresh happens app-side; extension treats expiry as a soft error and prompts the user to "open the Rewordium app to refresh."
* **No long secrets.** Do not embed the Groq API key in the extension binary. App Store binary scans flag this and reject the build. The proxy token pattern above is the only safe path.
* **UI placement.** Add a slim row above AzooKey's candidate bar — implement as a SwiftUI overlay in our shim layer, not by patching AzooKey. AzooKey already supports custom tabs via `CustardKit`; investigate using that mechanism so the integration stays upstream-clean.

Build order inside this phase:
1. Toolbar row UI (static button, no logic). Verify AzooKey still works underneath.
2. Read selected text via `textDocumentProxy.documentContextBeforeInput` / `selectedText`.
3. Call Groq through the existing `UnifiedAIService.paraphraseText`-equivalent Swift code (port the minimum API call — no shared code with Flutter).
4. Replace text via `textDocumentProxy.deleteBackward()` × N + `insertText(...)`.
5. Error states: rate-limited, no token, network error → inline toast above the keyboard. No alerts.

**Exit criterion:** User types a sentence in any app, taps the Rewordium button on the keyboard toolbar, sees the paraphrase replace the original within ~2 seconds.

**Do not touch:** AzooKey's input pipeline, candidate engine, conversion logic, or theme system. The paraphraser is additive only.

---

### Phase 5 — Gradual customization (English UX, persona injection)

**Goal (deferred):** Optional English-first UX on top of the Japanese-default keyboard, persona-aware paraphrasing, telemetry, FlorisBoard-style number row toggle. **Japanese support stays on.** Anything that disables AzooKey's default language must be a user-controlled toggle, never a code-level removal.

Out of scope for this document. Open as a separate plan once Phase 4 is in TestFlight.

---

## 3. Upstream compatibility rule

All Rewordium-specific code lives in:
* `ios/RewordiumKeyboard/` — the extension target's own files (controller shim, SharedSettings, paraphraser UI/logic)
* `ios/Runner/KeyboardSettingsBridge.swift` — the Flutter method channel

The vendored `ios/Packages/azooKey/` tree is **read-only with one documented exception**:

### Documented local modifications to vendored AzooKey

| File | Change | Why |
|---|---|---|
| `ios/Packages/azooKey/AzooKeyCore/Package.swift` | Removed `traits: ["ZenzaiCPU"]` from the `AzooKeyKanaKanjiConverter` dependency declaration. | Zenzai (neural Japanese converter) requires a `llama.cpp` binary XCFramework. At the pinned upstream revision, that artifact zip's internal layout doesn't match SwiftPM's expected name → `binary target 'llama.cpp' could not be mapped`. The same resolver failure cascades to `swiftymarisa`. Removing the trait skips Zenzai entirely; AzooKey falls back to its statistical converter. Restore the trait when we either upgrade AzooKeyKanaKanjiConverter to a revision with a fixed binary target, or host the xcframework ourselves. |

Each upstream rebase must re-apply this diff. The comment block in `Package.swift` marks the exact line.

To upgrade AzooKey:
```
cd ios/Packages/azooKey
rm -rf *
unzip <newer azooKey-main.zip>
git diff      # should be additive only, with the rest of the tree replaced
```
If a phase ever requires editing an AzooKey file, stop and ask whether it can be done from the shim layer instead. If not, the change should land upstream first; only after rejection do we consider a fork branch (and we document the diff explicitly in this file).

---

## 4. Codemagic notes — current state

`codemagic.yaml` now runs three new steps before `flutter build ios`:

1. **Wire RewordiumKeyboard extension target** — `ruby ios/scripts/setup_keyboard_target.rb`. Idempotent. Adds the extension target, links the seven SPM products (5 local + 2 remote: `KanaKanjiConverterModule`, `SwiftUtils`), embeds the `.appex` into Runner. Re-running on a project that already has the target is safe.
2. **Resolve SwiftPM packages** — fetches `AzooKeyKanaKanjiConverter` (revision pinned to match upstream) and `CustardKit` into `build/ios/SourcePackages`. Cached across builds via `$HOME/Library/Caches/org.swift.swiftpm`.
3. **Build iOS app (no codesign)** — unchanged structurally, just consumes the wired extension target.

Until you have a paid Apple Developer account:

* The build still **succeeds** because `CODE_SIGNING_ALLOWED=NO` is passed everywhere. The output IPA contains `Runner.app` with `PlugIns/RewordiumKeyboard.appex` nested inside it.
* Sideloadly using a **free** Apple ID can install the app but **cannot enable** `RequestsOpenAccess=true` on the keyboard extension. The user can still add the keyboard from Settings, but Japanese candidates that rely on App Group I/O may fail silently. This is an iOS limitation, not a code bug.
* Once the paid account is in place: connect it to Codemagic, add `IOS_PROVISIONING_PROFILE_*` env vars for both bundle IDs (`com.noxquill.rewordium` and `com.noxquill.rewordium.keyboard`), flip the build flags to sign, and re-run.

---

## 5. Verification checklist for the next CI run

After Codemagic kicks off with the new pipeline, check the build log in this order. Any failure points at a fixable wiring problem, not a code bug.

1. **`flutter pub get`** — green.
2. **Wire RewordiumKeyboard extension target** — should print "Created target: RewordiumKeyboard", added sources count, linked SPM products. If it crashes with "uninitialized constant XCLocalSwiftPackageReference", the `xcodeproj` gem is too old — add `sudo gem install xcodeproj --no-document` explicitly.
3. **Resolve SwiftPM packages** — fetches ~500 MB on the first run, mostly the converter dictionary blobs. Subsequent runs hit the cache.
4. **flutter build ios** — generates Podfile + runs `pod install`. Should not touch the extension target.
5. **xcodebuild …Runner** — compiles AzooKey sources into `RewordiumKeyboard.appex`, then embeds it into `Runner.app/PlugIns/`. The first build is slow (~10 min) because of the Zenzai C++ neural converter; subsequent builds are ~2 min.
6. **Artifact** — `rewordium-unsigned.ipa` should contain `Payload/Runner.app/PlugIns/RewordiumKeyboard.appex`. Use `unzip -l rewordium-unsigned.ipa | grep PlugIns` to verify.

## 6. Resolved + outstanding questions

* **iOS 17 minimum** — confirmed, project bumped from 15.5 → 17.0.
* **Apple Developer paid account** — user will purchase once app is otherwise ready. Until then: unsigned builds via Sideloadly; extension installs but can't enable Full Access. Acceptable for the integration-stability phase.
* **App Review for Custom Keyboard** — keyboards get extra scrutiny; plan ~2 weeks lead time before any deadline.
* **Privacy nutrition label** — needs an "Other User Content" or "Diagnostic" entry once Phase 4 (network calls in the extension) ships. Draft before submission.

None of these block the current Phase 2 work.
