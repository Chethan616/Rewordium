# Plan: iOS keyboard switching + Liquid Glass, plus Reboard adaptive learned swipe typing

## Context

Two unrelated items in one ask:

**(A) iOS keyboard won't switch via globe unless every other keyboard is removed.** Users have to delete Apple's English keyboard to land on Rewordium. Cause: `IsASCIICapable: false` in the extension's `Info.plist` makes iOS skip Rewordium when the focused field is ASCII-coded (the common case). The user also wants Liquid Glass applied automatically on iOS 26+.

**(B) Reboard's glide typing doesn't learn from the user.** Glide decoding works, suggestions work, but personal vocabulary (names like "chethan", slang like "frfr", transliterated words like "bagunnava") never enters the swipe lexicon — so the user has to type those words manually every time. We need Gboard-style "swipe a path → predict your own word" with frequency + recency ranking, persisted locally, with zero added typing/swipe lag.

Both fit inside the existing architecture; no engine rewrites, no native changes.

---

## (A) iOS — globe switching + Liquid Glass

**Status: SHIPPED** in this session. Verify the diffs are still present before moving on.

### Changes
1. **`ios/RewordiumKeyboard/Info.plist`** — `IsASCIICapable` flipped to `true`. Rewordium is a Latin/QWERTY keyboard via KeyboardKit; lying that we can't handle ASCII was what made iOS exclude us from the globe cycle on ASCII fields.

2. **`ios/RewordiumKeyboard/Views/RewordiumMaterial.swift`** — `RewordiumSurface` wraps content in a `GlassyBackground` that branches on `if #available(iOS 26.0, *)` → `.glassEffect()` over `.regularMaterial`, falling back to `.ultraThinMaterial` on iOS 15–25. Also `rewordiumProminentStyle()` extension uses `.glassProminent` on iOS 26+. This is automatic; no runtime toggle.

### Verification
- Install on an iOS 17/18 device with Apple English + Rewordium both enabled → globe cycles into Rewordium without removing anything.
- Install on iOS 26 simulator/device → toolbar background visibly uses Liquid Glass refraction; on iOS 25 same build degrades to flat `.ultraThinMaterial`.

---

## (B) Reboard — adaptive learned swipe typing

**Status: PLANNED, not yet implemented.** Execute this in a fresh session. Everything below is designed to be self-contained — no prior conversation context needed.

### 0. Quick orientation (read this first if you're a fresh agent)

The Reboard keyboard is a FlorisBoard fork living at:
```
android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/
```
Plus a sibling library module:
```
android/reboard_lib/
```

The keyboard is an Android `InputMethodService` registered as `FlorisImeService` (`android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/FlorisImeService.kt`). The user types on a Compose UI; gestures are processed by a pure-Kotlin classifier; suggestion candidates come from per-locale NLP providers. The whole NLP layer is suspend-function based and runs on a `CoroutineScope(Dispatchers.Default + SupervisorJob())` owned by `NlpManager`.

### 1. How the existing system fits together (file:line references, all verified)

- **Glide decoder:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt`
  - `interface GlideTypingClassifier` (line 26)
  - `addGesturePoint(...)` — pointer events stream in here
  - `setWordData(subtype: Subtype)` (line 216) — loads `nlpManager.getListOfWords(subtype)` + `nlpManager.getFrequencyMap(subtype)` into local fields `this.words` + `this.wordFrequencies` (line 222–225). Early-exit `if (wordDataSubtype == subtype) return` at line 218.
  - `initializePruner(invalidateCache: Boolean)` (line 237) — builds and caches `Pruner` per subtype in `prunerCache` (LruCache, line 244). Pruner pre-computes "ideal" gesture paths for all words.
  - `getSuggestions(...)` (line 259) — LRU-cached on `(gesture, maxSuggestionCount)`. Calls `unCachedGetSuggestions` on miss (line 273).
  - `unCachedGetSuggestions` (line 273) — uses `pruner.pruneByExtremities` + `pruneByLength`, then `shapeProb × locationProb × frequency` ranking.

- **Lexicon source (the dictionary):** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/latin/LatinLanguageProvider.kt`
  - `wordData: GuardedByLock<MutableMap<String, Int>>` — the in-memory word→freq (0–255) store.
  - `getListOfWords(subtype)` (line 560) returns `wordData.withLock { it.keys.toList() }`.
  - `getFrequencyMap(subtype)` (line 568) returns `wordData.withLock { data -> data.mapValues { it.value / 255.0 } }`.
  - `notifySuggestionAccepted(subtype, candidate)` (line 523) already bumps `wordData[accepted] += 2` (coerced to 255), updates `recentWords` deque, learns bigrams via `lastCommittedWord`. **This is the existing in-memory learning pattern** — we extend it.
  - `recentWords: ArrayDeque<String>` capped at `MAX_RECENCY_WORDS`.
  - `bigramData` + `learnedBigrams: GuardedByLock<...>` — bigram learning already in place.

- **NLP orchestrator:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/NlpManager.kt`
  - `scope = CoroutineScope(Dispatchers.Default + SupervisorJob())` (line 65) — use this for all background NLP work.
  - `getListOfWords(subtype)` / `getFrequencyForWord` / `getFrequencyMap` (lines 266–280) — `runBlocking` wrappers around the active suggestion provider.
  - `getSuggestionProvider(subtype)` (line 130) — returns the per-subtype provider (`LatinLanguageProvider` for Latin scripts).
  - `commitCandidate` flow goes: `KeyboardManager.commitCandidate` → `provider.notifySuggestionAccepted` (line 292 in KeyboardManager).

- **Editor commit hooks:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/editor/EditorInstance.kt`
  - `commitChar(char)` (line 218) — called for ALL single-character commits, including `SPACE` and punctuation. **This is the word-boundary signal we hook for manually-typed words.**
  - `commitText(text)` (line 256) — used for completion of multi-char strings (paste, etc.).
  - `commitGesture(text)` (line 317) — called from `KeyboardManager.commitGesture` after a glide gesture resolves. **Hook for gesture-typed words.**
  - `activeContent: EditorContent` exposes `composingText: CharSequence` — the in-progress word.
  - `activeContent.composing: EditorRange` — composing region; check `.isValid` before reading composingText.

- **Keyboard input loop:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/keyboard/KeyboardManager.kt`
  - `commitCandidate(candidate)` (line 290) — pipes through `notifySuggestionAccepted` already.
  - `commitGesture(word)` (line 300) — calls `editorInstance.commitGesture(fixCase(word))`.
  - `subtypeManager.activeSubtype` — current subtype, has `.primaryLocale: FlorisLocale`.

- **Subtype model:** `Subtype` class with `primaryLocale: FlorisLocale`, `locales(): List<FlorisLocale>`. `FlorisLocale` lives in `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/lib/FlorisLocale.kt` and has `.languageTag()` (e.g., "en-US").

- **Existing UserDictionary infrastructure (not used by glide):** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/dictionary/UserDictionary.kt` — Room-backed (`FlorisUserDictionaryDatabase`) + Android-system (`SystemUserDictionaryDatabase`) databases for user-curated words. **Do NOT reuse.** This is for explicit-add-via-settings entries; auto-learned words should be a separate store.

- **Prefs:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/app/AppPrefs.kt`
  - `dictionary` inner class (line ~220) — already has `enableSystemUserDictionary` and `enableFlorisUserDictionary` boolean prefs. Add the new pref here.
  - Prefs use `dev.patrickgold.jetpref` DataStore. Pattern: `val foo = boolean(key = "...", default = X)`.
  - Observe via `.asFlow()` or `.observeAsState()` (Compose).

- **Glide manager that wires the classifier into the keyboard:** likely at `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/text/gestures/GlideTypingManager.kt`. **Verify path with Glob `**/GlideTypingManager.kt` before editing.** This is the only file location not fully confirmed during planning.

### 2. Design — five surgical changes, no architecture rewrite

The guiding principle: **piggyback on existing hooks**. The classifier already weighs by `frequency`, so we just need (a) to bump frequencies for words the user actually types, (b) to persist those bumps, and (c) to refresh the classifier's snapshot when enough new words accumulate.

---

#### Change 1: New file — `LearnedWordsStore`

**Path:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/dictionary/LearnedWordsStore.kt`

**Purpose:** Per-locale JSON-backed cache of `(word → freq, lastUsedEpochSec)`. Loaded once at provider preload, merged into `wordData`. Mutations are non-blocking; serialization is debounced.

**File layout in `context.filesDir`:** `learned_words.json` (single file, all locales).

**JSON schema:**
```json
{
  "en-US": { "chethan": [12, 1763824819], "frfr": [4, 1763824900] },
  "te-IN": { "bagunnava": [7, 1763820000] }
}
```

**Public API:**
```kotlin
class LearnedWordsStore(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val perLocale = ConcurrentHashMap<String, ConcurrentHashMap<String, LearnedEntry>>()
    private val dirtyChannel = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private var loaded = false

    data class LearnedEntry(var freq: Int, var lastUsedEpochSec: Long)

    /** Idempotent. Loads the full file once into memory on first call. */
    suspend fun ensureLoaded()

    /** Returns a *copy* of the locale's submap for merging into wordData. */
    suspend fun snapshot(locale: FlorisLocale): Map<String, LearnedEntry>

    /**
     * Non-suspending. Updates the in-memory map and signals the writer.
     * Safe to call from any dispatcher, very hot path (every word commit).
     */
    fun bump(locale: FlorisLocale, word: String, freqDelta: Int = 1)

    init {
        // Background writer: debounce 2s, then serialize + atomic-rename.
        scope.launch {
            for (signal in dirtyChannel) {
                delay(2_000)
                // drain any further signals during the debounce window
                while (dirtyChannel.tryReceive().isSuccess) { /* coalesce */ }
                writeToDisk()
            }
        }
    }

    private suspend fun writeToDisk()
}
```

**Implementation notes:**
- Use `org.json.JSONObject` (Android stdlib) — avoids pulling in `kotlinx.serialization` for this single file. Check `pubspec/build.gradle` first; if `kotlinx.serialization-json` is already a dep, prefer it for type safety.
- Atomic write: write to `learned_words.json.tmp`, then `File.renameTo(target)`. Don't truncate the live file mid-write.
- On load failure (corrupt JSON), log via `flogError` and start with an empty map — never crash the IME.
- Eviction (when locale submap > 5000 entries): on every bump that pushes count above the cap, drop the lowest-scoring 10% in one pass. Score = `freq * exp(-(now - lastUsed) / 30 days)`. Done inside `bump` synchronously; the 5000 cap means worst-case sort is O(5000 log 5000), fine.
- `bump` does NOT block. `freq` saturates at 255 to match the dictionary's `Int 0–255` range.
- `ensureLoaded` uses `mutex.withLock` + `if (loaded) return` guard so concurrent providers don't double-load.

---

#### Change 2: Extend `LatinLanguageProvider`

**File:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/latin/LatinLanguageProvider.kt`

**Edits:**

1. **Field additions (top of class):**
   ```kotlin
   private val learnedStore = LearnedWordsStore(context)
   private val learnedSinceLastRefresh = AtomicInteger(0)
   private val _wordDataDirtyFlow = MutableSharedFlow<Subtype>(replay = 0, extraBufferCapacity = 1)
   val wordDataDirtyFlow: SharedFlow<Subtype> = _wordDataDirtyFlow.asSharedFlow()
   ```

2. **In `preload(subtype)` (find existing method, augment at the end):**
   ```kotlin
   learnedStore.ensureLoaded()
   val learned = learnedStore.snapshot(subtype.primaryLocale)
   wordData.withLock { data ->
       for ((word, entry) in learned) {
           val current = data[word] ?: 0
           // maxOf to avoid clobbering the static dictionary's high-frequency
           // common-word entries with low-freq learned counterparts.
           data[word] = maxOf(current, entry.freq.coerceIn(0, 255))
       }
   }
   ```

3. **New public method:**
   ```kotlin
   suspend fun learnWord(subtype: Subtype, rawWord: String) {
       // Centralized validation — call sites pass raw composing text.
       val word = rawWord.trim().lowercase()
       if (word.length < 2 || word.length > 40) return
       // Allow letters + apostrophes (don't / O'Brien). Skip URLs, numbers, mixed.
       if (!word.all { it.isLetter() || it == '\'' }) return
       if (!prefs.dictionary.learnPersonalWords.get()) return

       wordData.withLock { data ->
           val current = data[word] ?: 0
           data[word] = (current + 1).coerceAtMost(255)
       }
       learnedStore.bump(subtype.primaryLocale, word)

       if (learnedSinceLastRefresh.incrementAndGet() >= 10) {
           learnedSinceLastRefresh.set(0)
           _wordDataDirtyFlow.tryEmit(subtype)
       }
   }
   ```

4. **In existing `notifySuggestionAccepted` (line 523), after the existing `wordData.withLock { ... data[accepted] = ... }` block, add:**
   ```kotlin
   learnedStore.bump(subtype.primaryLocale, accepted, freqDelta = 2)
   ```
   This persists the +2 bump the in-memory map already applies. Skip if `accepted` isn't a clean letters-only word (URLs, numbers, etc.) — reuse the same validation as `learnWord`.

---

#### Change 3: NlpManager facade

**File:** `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/NlpManager.kt`

Add two members:

```kotlin
/**
 * Forwards a freshly-committed word (manual or gesture) to the active suggestion
 * provider so it can update personal-vocabulary frequencies. Safe no-op on
 * providers that don't support personal learning.
 */
fun learnWord(subtype: Subtype, word: String) {
    scope.launch {
        val provider = getSuggestionProvider(subtype)
        if (provider is LatinLanguageProvider) {
            provider.learnWord(subtype, word)
        }
    }
}

/**
 * Emitted when personal-vocabulary additions warrant a glide-classifier
 * rebuild. The collector should call StatisticalGlideTypingClassifier
 * .setWordData(subtype, force = true). Conflated so rapid commits coalesce.
 */
val wordDataDirtyFlow: SharedFlow<Subtype>
    get() = (getSuggestionProviderUnchecked(subtypeManager.activeSubtype) as? LatinLanguageProvider)
        ?.wordDataDirtyFlow ?: MutableSharedFlow<Subtype>().asSharedFlow()
```

Note: `getSuggestionProviderUnchecked` is the non-suspending variant — if it doesn't exist, expose a property `currentProvider: SuggestionProvider?` that mirrors the result of the last `getSuggestionProvider` call. Avoid `runBlocking` here.

---

#### Change 4: Hook the commit sites

**File A:** `EditorInstance.kt` line 218 (`commitChar`):

```kotlin
override fun commitChar(char: String): Boolean {
    // …existing setup code through line 231…

    // BEFORE calling super.commitChar, capture the composing word so we can
    // learn it once the commit succeeds. We only care about word-terminator
    // chars (space, newline, punctuation) — letters extend the composing region.
    val terminatesWord = effectiveChar == SPACE
        || effectiveChar == "\n"
        || effectiveChar.length == 1 && !effectiveChar[0].isLetter()
    val composingBeforeCommit = if (terminatesWord) {
        activeContent.composingText.toString()
    } else ""

    val result = super.commitChar(
        char = effectiveChar,
        deletePreviousSpace = isDeletePreviousSpace,
        insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive,
        insertSpaceAfterChar = isInsertAutoSpaceAfterChar,
    )

    if (result && composingBeforeCommit.isNotBlank()) {
        // Fire-and-forget: validation happens inside the provider.
        nlpManager.learnWord(subtypeManager.activeSubtype, composingBeforeCommit)
    }

    // …existing post-commit code (ghostText etc.) unchanged…
    return result
}
```

`nlpManager` and `subtypeManager` are accessed via the context-extensions pattern used elsewhere in this file — find existing references like `context.nlpManager()` near the top of the file.

**File B:** `KeyboardManager.kt` line 300 (`commitGesture`):

```kotlin
fun commitGesture(word: String) {
    editorInstance.commitGesture(fixCase(word))
    nlpManager.learnWord(subtypeManager.activeSubtype, word)
}
```

---

#### Change 5: Refresh the glide classifier

**File A:** `StatisticalGlideTypingClassifier.kt` line 216 — add `force` param:

```kotlin
override fun setWordData(subtype: Subtype, force: Boolean = false) {
    if (!force && wordDataSubtype == subtype) {
        return
    }
    this.words = nlpManager.getListOfWords(subtype)
    this.wordFrequencies = nlpManager.getFrequencyMap(subtype)
    this.wordDataSubtype = subtype
    if (force) {
        // Invalidate the pruner cache so initializePruner rebuilds with the
        // newly-merged vocabulary instead of returning the stale cached one.
        prunerCache.remove(subtype)
        lruSuggestionCache.evictAll()
    }
    if (wordDataSubtype == layoutSubtype) {
        initializePruner(invalidateCache = force)
    }
}
```

Update the `GlideTypingClassifier` interface signature to match.

**File B:** `GlideTypingManager.kt` (verify path with Glob) — in its `init` or wherever it owns the classifier, collect the dirty flow:

```kotlin
init {
    // …existing init…
    scope.launch {
        nlpManager.wordDataDirtyFlow.collect { subtype ->
            classifier.setWordData(subtype, force = true)
        }
    }
}
```

If `GlideTypingManager` doesn't have a `scope`, attach to the IME service's lifecycle scope. Pattern is shown in `NlpManager.kt` line 99 with `collectLatestIn(scope)`.

---

#### Change 6: Pref toggle

**File:** `AppPrefs.kt` — inside the existing `Dictionary` inner class:

```kotlin
val learnPersonalWords = boolean(
    key = "dictionary__learn_personal_words",
    default = true,
)
```

**Optional UI:** In `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/app/settings/dictionary/UserDictionaryScreen.kt`, add a switch row pointing at `prefs.dictionary.learnPersonalWords`. Use the existing `SwitchPreference(...)` composable already used in that screen. **Not required for v1** — the default-on behavior is correct out of the box.

---

### 3. Ranking semantics (no code change)

`unCachedGetSuggestions` already ranks by `1 / (shapeProb × locationProb × frequency)`. Because `learnWord` bumps `wordData[learnedWord]` and the next `getFrequencyMap` call reflects those bumps, learned words automatically get the frequency boost. After ~5 manual types of a word it reaches freq ~5–10/255 → competitive with mid-tier dictionary words. After ~20 commits it saturates at 255 → top-tier.

Recency is implicit: every commit pushes freq up; the `LearnedWordsStore` ages out old entries on eviction.

### 4. Performance budget (must hold)

| Operation | Cost | Where |
|---|---|---|
| `learnWord` | 1 map mutation + 1 atomic increment + 1 conflated channel offer | IME scope, background — never UI thread |
| Persistence write | JSONObject serialize + atomic rename of ~10–50KB | IO dispatcher, debounced 2s |
| Classifier rebuild (`setWordData(force=true)`) | full Pruner rebuild over ~3k words | At most 1 per 10 commits, background |
| LRU suggestion cache clear | O(cache size) ~ 64 entries | Trivial |
| Cold start preload | JSON read (~10–50KB) + map merge | Provider preload, off main thread |

Glide latency: **unchanged** in the common case. The only added overhead during a swipe is `learnWord` after `commitGesture`, which is microseconds.

### 5. Critical files (exhaustive list)

**New:**
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/dictionary/LearnedWordsStore.kt`

**Edit:**
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/latin/LatinLanguageProvider.kt`
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/nlp/NlpManager.kt`
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/editor/EditorInstance.kt`
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/keyboard/KeyboardManager.kt`
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/ime/text/gestures/StatisticalGlideTypingClassifier.kt`
- `android/reboard_keyboard/src/main/kotlin/com/noxquill/rewordium/keyboard/app/AppPrefs.kt`

**Verify path before editing:**
- `android/reboard_keyboard/src/main/kotlin/**/GlideTypingManager.kt` (or whatever owns the classifier — search with Glob)

### 6. Implementation order (do strictly in this order)

1. Write `LearnedWordsStore.kt` — completely standalone, easiest to unit-test mentally.
2. Add the pref to `AppPrefs.kt`.
3. Edit `LatinLanguageProvider.kt` — fields, `preload` merge, `learnWord` method, `notifySuggestionAccepted` augmentation.
4. Edit `NlpManager.kt` — `learnWord` facade + `wordDataDirtyFlow` exposure.
5. Edit `EditorInstance.kt` `commitChar` to call `nlpManager.learnWord`.
6. Edit `KeyboardManager.kt` `commitGesture` to call `nlpManager.learnWord`.
7. Edit `StatisticalGlideTypingClassifier.kt` + interface to accept `force` param.
8. Find and edit `GlideTypingManager.kt` to collect `wordDataDirtyFlow`.
9. Build, install on device, run verification §7.

### 7. Verification

1. **Manual learning:** Type "chethan" five times with space after each. Restart IME (close + reopen any text field). Swipe a "chethan"-shaped path on the keyboard → "chethan" appears in the candidates row, ideally in top-3.
2. **Gesture learning:** Glide-type a learned word. The next swipe to a similar path → it's the top candidate.
3. **Persistence:** `adb shell run-as com.noxquill.rewordium cat files/learned_words.json` → JSON contains the new entries with freq + lastUsed.
4. **Toggle:** Set `dictionary__learn_personal_words` to false via the Reboard settings UI (or via adb-poked DataStore). Type new novel words → JSON file deltas stop appearing.
5. **Perf:** Hook up Android Studio CPU sampler. Glide-type 5 long words (>15 chars) in succession. Confirm: zero `learnWord` or persistence frames on the main thread; `setWordData(force=true)` invoked at most once across the burst.
6. **Cold-start:** Force-stop the IME process (`adb shell am force-stop`). Open a new text field, swipe a previously-learned path → predicted on the first attempt (proves persistence load + merge ran during preload).
7. **No regression:** Glide-type a common dictionary word (e.g., "hello") — still works, no extra latency.

### 8. Risks + edge cases

- **Provider not a `LatinLanguageProvider`:** for Han/CJK subtypes the cast fails silently and `learnWord` no-ops. Correct behavior — Han doesn't use frequency-weighted swipe the same way.
- **Disk write race:** the conflated channel + 2s debounce + atomic rename together guarantee one writer at a time, even under rapid commit bursts.
- **Glide manager file location:** if Glob doesn't find `GlideTypingManager.kt`, search for the class that wires `StatisticalGlideTypingClassifier` into the IME (`Grep "StatisticalGlideTypingClassifier(" --type kotlin`). The hookup is wherever this constructor is called.
- **App-data clear:** if the user clears app data the learned-words JSON is wiped. Acceptable — the on-disk dictionary asset is intact, so the IME still works. The user re-learns over time.
- **Capitalization:** `learnWord` lowercases everything. Glide rendering applies `fixCase(word)` already, so output capitalization is unchanged.
- **Bilingual users:** entries are partitioned per `primaryLocale`. Switching subtypes loads the right submap into wordData via `preload`. No cross-pollination.

---

## Out of scope for this plan
- No multi-word phrase learning (just single tokens).
- No cloud sync of learned vocabulary.
- No "Personal Dictionary" UI showing learned words (use the existing UserDictionary settings screen if needed).
- No swipe-classifier algorithmic changes (Gaussian shape/location ranking is untouched).
