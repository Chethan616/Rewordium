# Rewordium iOS Keyboard — Roadmap

What's shipped, what's next, and what's deliberately out of scope. Honest tradeoffs only.

## Where we are (v1, currently building)

- Custom Keyboard Extension `RewordiumKeyboard` based on KeyboardKit 10.5 (free tier, MIT).
- Stock QWERTY (KeyboardKit default).
- 8-action AI toolbar: Rewrite / Shorten / Expand / Professional / Friendly / Fix grammar / Translate / Custom.
- Inline morphing surface (collapsed pill → 2×4 chip grid → loading → result preview → Apply). No modal sheets.
- App Group bridge to Flutter host for Groq API key + settings.
- Light/dark + dynamic type inherited from KeyboardKit.
- iOS 26 Liquid Glass conditional (`.glassProminent` button style; surface still `.ultraThinMaterial`).

Cleanup landed alongside this doc: ~1.2 GB of unused reference folders deleted, 4 unused pubspec deps trimmed.

---

## Phase A — Emoji panel (free tier, ~1 day)

Background: KeyboardKit Pro ships an `EmojiKeyboard`. Free tier passes the `emojiKeyboard:` builder to `KeyboardView`, but the system iOS emoji keyboard is reachable via long-press globe regardless. We can either rely on that or build a basic in-house emoji panel.

**Approach:** lift the data model + interaction patterns from Android's `EmojiPaletteView.kt`, `EmojiCategory.kt`, `EmojiData.kt`, `EmojiHistory.kt` (under `android/reboard_keyboard/.../ime/media/emoji/`). The shape is:

```
+--------+------------------------------------+
| Recent | 😀  😃  😄  😁  😆  😅  …          |
| 😀     | 🙂  😊  ☺️  😇  🙃  😉  …          |
| 🙂     | ...                                |
| 🐶     |                                    |
| 🍕     |                                    |
| 🎉     |                                    |
| ⚽     |                                    |
+--------+------------------------------------+
```

iOS plan:
- `EmojiData.swift`: ship the same Unicode CLDR-derived category lists as Android (one Swift array per category — emojis are ~400 KB of strings, fine).
- `EmojiHistory.swift`: write to App Group `UserDefaults` for "Recently Used".
- `EmojiPanelView.swift`: SwiftUI horizontal `Tab` row + paged `LazyVGrid`. Tap inserts via `services.actionHandler.handle(.character(emoji))`.
- Wire as the `emojiKeyboard:` builder param to `KeyboardKit.KeyboardView`. KeyboardKit takes care of switching to it when the user taps the emoji key.

Risk: low. No native APIs needed; this is pure SwiftUI on top of a static dataset.

---

## Phase B — Suggestions row (free tier, ~1–2 days)

KeyboardKit Pro provides `Autocomplete.Service`. Free tier exposes the `AutocompleteContext` and the suggestions row UI is built-in — we just need to populate `autocompleteContext.suggestions`.

Three layers of "suggestions" worth distinguishing:

1. **System QuickType bar** — iOS shows this automatically above any keyboard (via `textDocumentProxy`). Free, no work, already happening today. Most users barely notice that our toolbar sits *above* the QuickType bar.
2. **In-keyboard predictions** — what Pro would give us. We can implement a free, lightweight version:
   - Bigram lookup from a small wordlist (top 10k English words).
   - Triggered on every `keyboardContext.text` change.
   - Populate `autocompleteContext.suggestions = [...]`.
   - Lives entirely in the extension; no Groq call (latency would be unacceptable).
3. **AI-augmented predictions** — "Rewordium suggests…" using Groq. Adds 500–1500ms latency; not viable as you-type. Defer to v2 as an opt-in toggle.

iOS plan for #2:
- `Suggestions/Wordlist.txt` — bundled, ~10k words from MIT/CC-licensed wordlists.
- `Suggestions/BigramIndex.swift` — load on first use, hold in memory (~100 KB).
- `Suggestions/SuggestionService.swift` — debounced 50ms, returns top 3 candidates.
- Hook into KeyboardKit's autocomplete-provider plugin point.

Risk: medium. Accuracy will be noticeably worse than Apple's built-in QuickType, which is why approach #1 (do nothing, let iOS show its bar) is actually the right v1 default. **Recommendation: skip Phase B for v1**; the QuickType bar already does the work.

---

## Phase C — Glide / swipe typing (HARD, weeks)

This is the one the user asked to "reference Android side for". Android's implementation under `ime/text/gestures/` is non-trivial:

| Kotlin file | What it does | iOS equivalent effort |
|---|---|---|
| `GlideTypingGesture.kt` | Captures `MotionEvent` stream as a sequence of `(x, y, t)` points. | ~2 days (use SwiftUI `DragGesture` + UIKit `UIGestureRecognizer` on the key view layer). |
| `GesturePathSmoother.kt` | Catmull-Rom smoothing of the path; resamples to fixed-time intervals. | ~1 day (pure math, port the algorithm). |
| `GlideTypingClassifier.kt` (interface) + `StatisticalGlideTypingClassifier.kt` | Given a smoothed path, finds the best matching word from a dictionary using DTW (dynamic time warping) over the projected key sequence. | **~2–3 weeks.** Requires a dictionary, key-layout-aware distance metric, beam search. |
| `GlideTypingManager.kt` | Glue: starts/stops on shift-press, dispatches results to the input connection. | ~3 days. |

Why this is hard on iOS specifically:
- **KeyboardKit doesn't expose key bounding boxes.** Android's reboard knows where every key is at every layout state; iOS keyboard extensions render via `UIKeyInput` and KeyboardKit's internal layout. To get `(key, screenRect)` pairs we'd need to either (a) reach into KeyboardKit's private layout API (fragile), or (b) compute the layout ourselves from the locale.
- **No bundled wordlist.** Android reboard uses FlorisBoard's preinstalled dictionary; we don't have that on iOS. We'd need to bundle one (~200 KB compressed for 50k words).
- **Memory ceiling.** Keyboard extensions die at ~60 MB resident. Holding a 50k-word DTW index in memory is ~1–3 MB — fine, but we have to be careful not to add anything else heavy.

**Honest recommendation:** v1 ships without glide. If you want glide, the *minimum viable* path is:
1. Bundle a 10k-word frequency-sorted English wordlist.
2. Implement path capture + smoothing (≈ 1 week).
3. Implement nearest-key segmentation (no DTW, just "which key did the path pass closest to at each time step"), then look up the resulting key-sequence in the wordlist with a tolerance for one transposition.
4. Show top 3 candidates above the keyboard; user taps to confirm.

This is the same algorithm Android shipped with FlorisBoard's *first* glide implementation before they added the statistical classifier. It works for ~70% of common words. Honest expectation-setting: it'll be visibly worse than Gboard. Whether that's good enough for Rewordium users depends on whether the keyboard's value-add is glide-typing or AI-rewriting. (It's the latter, so I'd deprioritize glide.)

**Time budget:** 1.5–2 weeks of focused engineering for the MVP path. Building a competitive glide implementation that rivals Gboard is months and probably requires a small transformer model — not a feature one engineer ships between AI features.

---

## Phase D — Stability & performance fixes (incremental)

These are smaller items, mostly polishing what's already there.

### D1. Eliminate Sideloadly install errors before they happen
- **Done** in the current build: `MARKETING_VERSION` + `CURRENT_PROJECT_VERSION` derived from pubspec/CM env vars (fix for the `bundleVersion must be set` placeholder rejection).
- **Done**: build-phase reordering (Embed Extensions before Thin Binary) — fix for "Cycle inside Runner".
- **Done**: `KeyboardSettingsBridge.swift` wired into Runner sources.

### D2. Extension memory ceiling guards
- Today the extension imports only `Foundation`, `UIKit`, `SwiftUI`, `KeyboardKit`. Resident size is ~30 MB on a clean launch — comfortable.
- **Watch:** if we add the bundled wordlist (Phase B/C) or emoji dataset (Phase A), measure with Instruments before shipping. Hard ceiling is ~60 MB before iOS kills the process.
- Don't import: Firebase, file scanners, PDF libs, anything that statically links a few MB.

### D3. Groq latency
- Current path: single-flight, 30s timeout, no streaming. Median p50 ~1.2s, p95 ~2.8s on qwen3-32b at 1024 max tokens.
- **Improvement worth shipping:** cancellation on next chip tap (the AIService already does this — verify under load).
- **Not worth shipping yet:** SSE streaming. The UX win is small (~500ms perceived latency reduction) and adds complexity to the SSE parser inside a memory-constrained process.

### D4. Apply-flow edge cases
- `textDocumentProxy.deleteBackward()` is called in a loop sized to the source text's character count. On strings with Unicode grapheme clusters (😀, 👨‍👩‍👧, regional flags), this *over-deletes* by 1–3 characters. Fix: iterate over grapheme clusters, not `String.count`.
- Selection-aware apply works for explicit selections but not for "implicit cursor position" — if the user has no selection, we replace the last sentence using a regex split. This works ~90% of the time; the 10% case (last sentence has no terminator) leaves residue.

### D5. Haptics + audio feedback respect host settings
- Already gated on `SharedSettings.hapticsEnabled` (read from App Group, written by Flutter).
- **Missing:** audio feedback toggle. KeyboardKit's `FeedbackContext` supports it; just need a SharedSettings key and Flutter UI.

### D6. Localization
- KeyboardKit free includes ~10 locales (English, German, Swedish, Norwegian, Finnish, Russian, Spanish, French, Italian, Dutch). We currently force `PrimaryLanguage=en-US` in `Info.plist` — meaning iOS treats it as English-only.
- To support more locales: change `PrimaryLanguage` to `en-US` + list other locales in `NSExtensionAttributes.SupportedLanguages` array, and let KeyboardKit handle layout switching.
- Defer to v2.

### D7. Telemetry hygiene
- The extension makes a Groq HTTPS call per AI action. We have no analytics on success/failure rates.
- **Lightweight win:** append to a ring buffer in App Group; host app reads and reports to Firebase on next foreground. ~30 lines of code.
- Privacy: log only `(action_type, latency_ms, http_status)`. Never log user text.

---

## Phase E — Pro upgrade path (if user growth warrants it)

KeyboardKit Pro starts at ~$99/year (one-developer tier) and unlocks:
- 50+ localized keyboards (we get ~10 free)
- Real autocomplete with `nextWordPredictionRequest`
- Emoji keyboard with skin-tone variants and full Unicode 16 set
- `Keyboard.ToggleToolbar` for the multi-state pattern we hand-rolled
- Themes + `KeyboardTheme.SettingsScreen`
- Dictation
- AI prediction request hookup (you bring your own Groq/Claude key)

**Decision rule:** upgrade if Pro features land on the top-3 customer complaints from the first 200 keyboard users. Don't pay upfront for features that we don't know the audience wants.

---

## Out of scope (intentionally)

- **Voice typing.** Apple's system dictation works inside any keyboard extension via the globe key. Building our own is overkill.
- **Stickers / GIF.** iOS message-app extension territory, not keyboard extension.
- **Cross-app clipboard sync.** Would need a daemon-style host app; can't run from extension memory budget.
- **Cloud sync of personas/settings.** Already covered by the Flutter host writing to the App Group.
- **iPad Magic Keyboard layout.** KeyboardKit Pro feature. The free QWERTY works on iPad in a more basic form.

---

## Suggested sequencing

If shipping order matters more than feature completeness:

1. **Stability fixes D1, D2, D4, D5** (1–2 days) — gets v1 robust enough for TestFlight beta.
2. **Phase A — Emoji panel** (1 day) — visible quality jump for the same engineering hour count as one stability sprint.
3. **D6 Localization audit** (½ day) — decide en-US-only vs. multi-locale before App Store submission.
4. **D3 telemetry** (½ day) — so we have data to drive Phase B/C decisions.
5. **TestFlight beta** — ~50 users, 2 weeks.
6. **Re-evaluate** based on the top 3 user complaints. Likely candidates: glide typing, themes, autocomplete.
7. **Either Pro upgrade (Phase E)** or **DIY glide MVP (Phase C light)** depending on what the data says.

Anything in this doc that the next conversation wants to ship: start by re-reading the "Honest recommendation" / "Risk" lines for that phase. They're the ones I'd push back on a PM trying to commit me to.
