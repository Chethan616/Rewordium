# FlorisBoard Feature Integration Plan

## Executive Summary

This document outlines the comprehensive plan to integrate **FlorisBoard's spacebar navigation** and **glide typing** features into the Rewordium AI keyboard, replacing the existing custom implementations.

---

## ✅ FlorisBoard Features Confirmed

### 1. **Spacebar Cursor Navigation** ✓
**Status:** Fully implemented in FlorisBoard

**Implementation Details:**
- **File:** `TextKeyboardLayout.kt`
- **Mechanism:** Swipe gestures on spacebar (left/right/up) trigger cursor movement
- **Action System:** Uses `SwipeAction` enum with predefined actions:
  - `MOVE_CURSOR_LEFT`
  - `MOVE_CURSOR_RIGHT`
  - `MOVE_CURSOR_UP`
  - `MOVE_CURSOR_DOWN`
  - `MOVE_CURSOR_START_OF_LINE`
  - `MOVE_CURSOR_END_OF_LINE`
  - `MOVE_CURSOR_START_OF_PAGE`
  - `MOVE_CURSOR_END_OF_PAGE`

**Key Components:**
- `executeSwipeAction()` in `KeyboardManager.kt` - maps SwipeAction to TextKeyData
- Preferences: `prefs.gestures.spaceBarSwipeLeft/Right/Up`
- Real-time cursor movement with configurable sensitivity

### 2. **Glide Typing (Swipe Typing)** ✓
**Status:** Production-ready statistical implementation

**Implementation Details:**
- **Core Files:**
  - `GlideTypingManager.kt` - Main controller
  - `GlideTypingGesture.kt` - Touch event detector
  - `GlideTypingClassifier.kt` - Word prediction interface
  - `StatisticalGlideTypingClassifier.kt` - Actual prediction engine

**Architecture:**
```
Touch Event → GlideTypingGesture.Detector
            ↓
        Position data
            ↓
    GlideTypingManager (Listener)
            ↓
    StatisticalGlideTypingClassifier
            ↓
    Word predictions → Smartbar suggestions
            ↓
    Best prediction → Commit to editor
```

**Key Features:**
- Statistical prediction using key proximity
- Real-time word preview during swipe
- Multi-key path tracking
- Gesture vs regular touch distinction
- Velocity-based gesture detection (0.10 dp/ms threshold)
- Minimum distance threshold (key_width)
- Excludes special keys (DELETE, SHIFT, SPACE, CJK_SPACE)
- Maximum detection time: 500ms
- Visual trail rendering (optional)

---

## 📋 Integration Phases

### Phase 1: Preparation & Cleanup (1-2 hours)
**Priority:** CRITICAL

#### 1.1 Remove Existing Spacebar Navigation
**Files to Modify:**
- `SwipeGestureEngine.kt`:
  - Remove `handleProfessionalSpacebarMovement()`
  - Remove `finalizeProfessionalSpacebarGesture()`
  - Remove `SPACEBAR_CURSOR` gesture type handling
  - Remove spacebar-specific detection logic in `onTouchEvent()`

- `GestureModels.kt`:
  - Remove `SPACEBAR_CURSOR` from `GestureType` enum

- `RewordiumAIKeyboardService.kt`:
  - Clean up spacebar navigation references in comments

#### 1.2 Remove Existing Glide Typing
**Files to Modify:**
- `SwipeGestureEngine.kt`:
  - Remove `handleAdvancedGlideTyping()`
  - Remove `finalizeGlideTypingGesture()`
  - Remove `GLIDE_TYPING` gesture type handling
  - Remove glide-specific path tracking
  - Remove word prediction logic

- `GestureModels.kt`:
  - Remove `GLIDE_TYPING` from `GestureType` enum
  - Remove glide-related properties from `SwipePath` class

- `RewordiumAIKeyboardService.kt`:
  - Remove glide typing gesture result handling
  - Clean up "Professional glide typing" comments

- `MainActivity.kt`:
  - Remove all `KEY_GLIDE_TYPING_*` preference defaults
  - Remove glide typing settings handlers

- `KeyboardConstants.kt`:
  - Remove glide typing preference keys

#### 1.3 Remove Visual Components
- Remove glide trail rendering code from keyboard view
- Remove path preview overlays
- Remove glide-specific haptic feedback

---

### Phase 2: Core FlorisBoard Integration (3-4 hours)
**Priority:** HIGH

#### 2.1 Copy FlorisBoard Core Classes
**Source Directory:** `florisboard-main/app/src/main/kotlin/dev/patrickgold/florisboard/`

**Files to Copy:**
```
ime/text/gestures/
├── GlideTypingManager.kt
├── GlideTypingGesture.kt
├── GlideTypingClassifier.kt (interface)
├── StatisticalGlideTypingClassifier.kt
└── SwipeAction.kt

ime/keyboard/
└── KeyboardManager.kt (partial - executeSwipeAction method)
```

**Destination:** `android/app/src/main/kotlin/com/noxquill/rewordium/keyboard/florisboard/`

#### 2.2 Adapt Package Structure
1. Update all package declarations:
   ```kotlin
   // FROM
   package dev.patrickgold.florisboard.ime.text.gestures
   
   // TO
   package com.noxquill.rewordium.keyboard.florisboard.gestures
   ```

2. Fix imports to match new structure

#### 2.3 Dependency Resolution
**FlorisBoard Dependencies to Adapt:**
- `FlorisPreferenceStore` → Use existing `SharedPreferences`
- `keyboardManager()` → Reference your `RewordiumAIKeyboardService`
- `nlpManager()` → Integrate with your word suggestion system
- `subtypeManager()` → Use your locale/language settings
- `editorInstance` → Map to `InputConnection` operations

---

### Phase 3: Glide Typing Integration (4-5 hours)
**Priority:** HIGH

#### 3.1 Initialize GlideTypingManager
**In `RewordiumAIKeyboardService.kt`:**

```kotlin
class RewordiumAIKeyboardService : InputMethodService() {
    private lateinit var glideTypingManager: GlideTypingManager
    private lateinit var glideGestureDetector: GlideTypingGesture.Detector
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize glide typing components
        glideTypingManager = GlideTypingManager(this)
        glideGestureDetector = GlideTypingGesture.Detector(this).apply {
            registerListener(glideTypingManager)
        }
    }
}
```

#### 3.2 Hook Touch Events
**In keyboard layout touch handler:**

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    // Check if glide typing is enabled
    val glideEnabled = prefs.getBoolean(KeyboardConstants.KEY_GESTURES_ENABLED, true)
    
    if (glideEnabled) {
        // Get the initial key under touch
        val initialKey = findKeyAt(event.x, event.y)
        
        // Let glide detector process the event
        val consumedByGlide = glideGestureDetector.onTouchEvent(event, initialKey)
        
        if (consumedByGlide) {
            // Gesture is being tracked, don't process as normal key press
            return true
        }
    }
    
    // Fall back to normal key handling
    return super.onTouchEvent(event)
}
```

#### 3.3 Update Keyboard Layout
**In `KeyboardLayoutManager.kt`:**

```kotlin
fun setupGlideTyping() {
    // Pass current keyboard layout to glide manager
    val keys = getCurrentVisibleKeys() // Your method to get key list
    glideTypingManager.setLayout(keys)
}

// Call this whenever keyboard layout changes
fun onKeyboardLayoutChanged() {
    setupGlideTyping()
}
```

#### 3.4 Handle Glide Completions
**In `GlideTypingManager.kt` callback:**

```kotlin
// This happens automatically via the Listener interface
override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
    // Manager will call updateSuggestionsAsync internally
    // Best prediction will be committed via keyboardManager.commitGesture()
}
```

**Adapt `commitGesture()` method:**

```kotlin
fun commitGesture(word: String) {
    val ic = currentInputConnection ?: return
    
    // Apply case transformation if needed
    val finalWord = applyCurrentCase(word)
    
    // Commit to input
    ic.commitText(finalWord, 1)
    
    // Add space if auto-space enabled
    if (prefs.getBoolean(KeyboardConstants.KEY_AUTO_SPACE, true)) {
        ic.commitText(" ", 1)
    }
    
    // Provide haptic feedback
    performHapticFeedback()
}
```

---

### Phase 4: Spacebar Navigation Integration (2-3 hours)
**Priority:** HIGH

#### 4.1 Add SwipeAction Executor
**In `RewordiumAIKeyboardService.kt`:**

```kotlin
fun executeSwipeAction(swipeAction: SwipeAction) {
    val ic = currentInputConnection ?: return
    
    when (swipeAction) {
        SwipeAction.MOVE_CURSOR_LEFT -> {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
        }
        SwipeAction.MOVE_CURSOR_RIGHT -> {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        SwipeAction.MOVE_CURSOR_UP -> {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
        }
        SwipeAction.MOVE_CURSOR_DOWN -> {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        SwipeAction.MOVE_CURSOR_START_OF_LINE -> {
            val text = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            val lastNewline = text.lastIndexOf('\n')
            val charsToMove = text.length - lastNewline - 1
            ic.setSelection(ic.getTextBeforeCursor(charsToMove, 0)?.length ?: 0, 0)
        }
        SwipeAction.MOVE_CURSOR_END_OF_LINE -> {
            val text = ic.getTextAfterCursor(1000, 0)?.toString() ?: ""
            val nextNewline = text.indexOf('\n')
            val charsToMove = if (nextNewline >= 0) nextNewline else text.length
            repeat(charsToMove) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        }
        SwipeAction.DELETE_WORD -> {
            deleteWord()
        }
        SwipeAction.INSERT_SPACE -> {
            ic.commitText(" ", 1)
        }
        // Add other actions as needed
        else -> {
            Log.w(TAG, "Unhandled swipe action: $swipeAction")
        }
    }
    
    performHapticFeedback()
}
```

#### 4.2 Detect Spacebar Swipes
**In keyboard view touch handler:**

```kotlin
// Detect swipe on spacebar
if (isSpacebarKey(initialKey)) {
    when {
        isSwipeLeft(event) -> {
            val action = prefs.getString(
                KeyboardConstants.KEY_SPACEBAR_SWIPE_LEFT,
                SwipeAction.MOVE_CURSOR_LEFT.name
            )
            executeSwipeAction(SwipeAction.valueOf(action!!))
            return true
        }
        isSwipeRight(event) -> {
            val action = prefs.getString(
                KeyboardConstants.KEY_SPACEBAR_SWIPE_RIGHT,
                SwipeAction.MOVE_CURSOR_RIGHT.name
            )
            executeSwipeAction(SwipeAction.valueOf(action!!))
            return true
        }
        isSwipeUp(event) -> {
            val action = prefs.getString(
                KeyboardConstants.KEY_SPACEBAR_SWIPE_UP,
                SwipeAction.HIDE_KEYBOARD.name
            )
            executeSwipeAction(SwipeAction.valueOf(action!!))
            return true
        }
    }
}
```

#### 4.3 Add Gesture Detection Helpers
```kotlin
private fun isSwipeLeft(event: MotionEvent): Boolean {
    val deltaX = event.x - gestureStartX
    val deltaY = abs(event.y - gestureStartY)
    return deltaX < -swipeThreshold && deltaY < swipeThreshold / 2
}

private fun isSwipeRight(event: MotionEvent): Boolean {
    val deltaX = event.x - gestureStartX
    val deltaY = abs(event.y - gestureStartY)
    return deltaX > swipeThreshold && deltaY < swipeThreshold / 2
}

private fun isSwipeUp(event: MotionEvent): Boolean {
    val deltaY = event.y - gestureStartY
    val deltaX = abs(event.x - gestureStartX)
    return deltaY < -swipeThreshold && deltaX < swipeThreshold / 2
}
```

---

### Phase 5: Settings & Preferences (2 hours)
**Priority:** MEDIUM

#### 5.1 Add FlorisBoard Preferences
**In `KeyboardConstants.kt`:**

```kotlin
// Glide Typing Settings
const val KEY_GLIDE_ENABLED = "floris_glide_enabled"
const val KEY_GLIDE_SHOW_TRAIL = "floris_glide_show_trail"
const val KEY_GLIDE_SHOW_PREVIEW = "floris_glide_show_preview"
const val KEY_GLIDE_PREVIEW_REFRESH_DELAY = "floris_glide_preview_refresh_delay"

// Spacebar Gesture Settings
const val KEY_SPACEBAR_SWIPE_LEFT = "floris_spacebar_swipe_left"
const val KEY_SPACEBAR_SWIPE_RIGHT = "floris_spacebar_swipe_right"
const val KEY_SPACEBAR_SWIPE_UP = "floris_spacebar_swipe_up"
const val KEY_SPACEBAR_LONG_PRESS = "floris_spacebar_long_press"
```

#### 5.2 Set Defaults
**In `MainActivity.kt`:**

```kotlin
// FlorisBoard Glide Typing Defaults
if (!prefs.contains(KeyboardConstants.KEY_GLIDE_ENABLED)) {
    editor.putBoolean(KeyboardConstants.KEY_GLIDE_ENABLED, true)
}
if (!prefs.contains(KeyboardConstants.KEY_GLIDE_SHOW_TRAIL)) {
    editor.putBoolean(KeyboardConstants.KEY_GLIDE_SHOW_TRAIL, true)
}
if (!prefs.contains(KeyboardConstants.KEY_GLIDE_SHOW_PREVIEW)) {
    editor.putBoolean(KeyboardConstants.KEY_GLIDE_SHOW_PREVIEW, true)
}
if (!prefs.contains(KeyboardConstants.KEY_GLIDE_PREVIEW_REFRESH_DELAY)) {
    editor.putInt(KeyboardConstants.KEY_GLIDE_PREVIEW_REFRESH_DELAY, 150) // ms
}

// Spacebar Navigation Defaults
if (!prefs.contains(KeyboardConstants.KEY_SPACEBAR_SWIPE_LEFT)) {
    editor.putString(KeyboardConstants.KEY_SPACEBAR_SWIPE_LEFT, SwipeAction.MOVE_CURSOR_LEFT.name)
}
if (!prefs.contains(KeyboardConstants.KEY_SPACEBAR_SWIPE_RIGHT)) {
    editor.putString(KeyboardConstants.KEY_SPACEBAR_SWIPE_RIGHT, SwipeAction.MOVE_CURSOR_RIGHT.name)
}
if (!prefs.contains(KeyboardConstants.KEY_SPACEBAR_SWIPE_UP)) {
    editor.putString(KeyboardConstants.KEY_SPACEBAR_SWIPE_UP, SwipeAction.HIDE_KEYBOARD.name)
}

editor.apply()
```

#### 5.3 Create Settings UI
- Add glide typing enable/disable toggle
- Add spacebar gesture configuration screen
- Add gesture action pickers (dropdown with all SwipeAction options)
- Add glide trail color picker
- Add preview delay slider

---

### Phase 6: Testing & Optimization (3-4 hours)
**Priority:** HIGH

#### 6.1 Unit Tests
- Test glide gesture detection
- Test word prediction accuracy
- Test spacebar swipe detection
- Test cursor movement precision

#### 6.2 Integration Tests
- Test keyboard layout updates
- Test language/locale switching
- Test preference changes
- Test memory usage under heavy glide typing

#### 6.3 Performance Profiling
- Measure glide typing latency
- Optimize key lookup performance
- Profile StatisticalGlideTypingClassifier
- Reduce allocation in hot paths

#### 6.4 User Acceptance Testing
- Test on multiple devices (different screen sizes)
- Test with different keyboard layouts
- Test in various apps (messaging, email, browser)
- Gather feedback on gesture sensitivity

---

## 🔄 Migration Strategy

### Backward Compatibility
1. **Keep existing gesture engine** for other gestures (delete swipe, shift double-tap)
2. **Feature flag** glide typing to allow gradual rollout
3. **Preference migration** for users with existing settings
4. **Fallback behavior** if FlorisBoard features fail

### Rollout Plan
1. **Alpha Release:** Internal testing (1 week)
2. **Beta Release:** Limited users with opt-in (2 weeks)
3. **Staged Rollout:** 10% → 25% → 50% → 100% over 4 weeks
4. **Monitor metrics:** Crash rate, gesture usage, user satisfaction

---

## 📊 Expected Benefits

### Code Quality
- ✅ Remove ~1500 lines of custom gesture code
- ✅ Leverage battle-tested FlorisBoard implementation
- ✅ Reduce maintenance burden
- ✅ Better separation of concerns

### User Experience
- ✅ More accurate word predictions
- ✅ Smoother gesture detection
- ✅ Configurable swipe actions
- ✅ Industry-standard behavior

### Performance
- ✅ Optimized touch event handling
- ✅ Efficient key proximity calculations
- ✅ Reduced memory allocation
- ✅ Better battery life

---

## ⚠️ Risks & Mitigation

### Risk 1: Breaking Existing Users
**Mitigation:**
- Comprehensive testing before release
- Feature flag for gradual rollout
- Easy rollback mechanism
- User notification of changes

### Risk 2: Performance Regression
**Mitigation:**
- Benchmark before/after integration
- Profile hot paths
- Use release builds for testing
- Monitor crash analytics

### Risk 3: Dependency Conflicts
**Mitigation:**
- Copy FlorisBoard code instead of direct dependency
- Adapt to existing architecture
- Isolate FlorisBoard code in separate package
- Version control all changes

### Risk 4: Lost Custom Features
**Mitigation:**
- Document all current features before removal
- Ensure FlorisBoard has equivalent functionality
- Keep custom enhancements if superior
- Provide migration guide for users

---

## 📅 Timeline

| Phase | Duration | Start | End |
|-------|----------|-------|-----|
| Phase 1: Cleanup | 2 hours | Day 1 | Day 1 |
| Phase 2: Core Integration | 4 hours | Day 1 | Day 2 |
| Phase 3: Glide Typing | 5 hours | Day 2 | Day 3 |
| Phase 4: Spacebar Nav | 3 hours | Day 3 | Day 3 |
| Phase 5: Settings | 2 hours | Day 4 | Day 4 |
| Phase 6: Testing | 4 hours | Day 4 | Day 5 |
| **Total** | **20 hours** | | **~1 week** |

---

## ✅ Success Criteria

- [ ] All glide typing tests pass
- [ ] Spacebar navigation works in all 4 directions
- [ ] No performance regression vs baseline
- [ ] Crash rate stays below 0.1%
- [ ] User satisfaction score > 4.0/5.0
- [ ] Zero data loss during gesture operations
- [ ] Haptic feedback feels natural
- [ ] Settings UI is intuitive

---

## 📚 References

- FlorisBoard Repository: https://github.com/florisboard/florisboard
- FlorisBoard Documentation: https://florisboard.org/docs/
- Android Input Method Framework: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- Gesture Detection Best Practices: https://developer.android.com/training/gestures

---

**Next Steps:** Begin Phase 1 cleanup by removing existing implementations.
