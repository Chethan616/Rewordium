# Keyboard Performance and Gesture Improvements

## Performance
- Reduce status polling frequency and use event-driven updates from the native keyboard service when possible.
- Debounce applyAllKeyboardSettings to avoid repeated MethodChannel calls during rapid preference changes.
- Cache SharedPreferences reads in memory during a session to avoid repeated disk reads.
- Move suggestion generation and heavy text processing to a background isolate.
- Avoid verbose logging in release builds to reduce UI thread churn.

## Swipe Gestures
- Apply smoothing to pointer velocity and angle to reduce false direction changes.
- Use a minimum distance threshold before classifying a swipe as a gesture.
- Tune delete and space swipe thresholds independently based on error rate.
- Add a short cooldown after a gesture fires to prevent accidental double triggers.

## Suggestions
- Cache the last N token predictions and reuse when the prefix changes slightly.
- Use incremental updates instead of full recomputation on each keystroke.
- Add a lightweight user dictionary and recent words cache for faster lookups.
- Track hit rate and time-to-suggest to tune models and thresholds.

## Measurement
- Instrument latency for key events, gesture recognition, and suggestion generation.
- Track dropped frames when keyboard overlay is visible.
- Add a simple debug panel to surface timing stats during testing.

---

## Adaptive Swipe Learning (Gboard-Style)

> **Goal**: Learn from the user's swipe patterns to improve accuracy over time — without any perceptible lag. The user should feel like the keyboard gets smarter the more they use it.

### Strategy

The key principle: **learn offline, apply instantly**. Never block the gesture recognition path. All adaptation logic runs asynchronously in the background; the real-time recognizer always uses the last-known calibration values that are pre-computed and held in memory.

### 1. Per-User Gesture Profile (stored locally)
- Maintain a `GestureProfile` data class in a persistent `SharedPreferences` or a lightweight SQLite table (one row per direction: `swipe_left`, `swipe_right`, `swipe_up`, `swipe_down`).
- Track per-direction: `meanVelocity`, `meanAngle`, `meanDistance`, `activationCount`, and an `errorRate` (manual correction rate: if the user immediately deletes the word after a swipe, count it as a missed gesture).
- Keep a circular buffer of the last **200 gesture events** per direction to compute rolling stats without growing unboundedly.

### 2. Background Calibration Loop
- After **every 20 accepted gestures** (not every gesture), dispatch a background coroutine to:
  1. Pull the rolling buffer.
  2. Recompute per-direction thresholds: `triggerDistance = mean(distance) * 0.8`, `triggerVelocity = mean(velocity) * 0.85` (80–85% of mean acts as a confident lower bound without overcounting).
  3. Write the updated thresholds back to the in-memory calibration object.
- This calibration run is fully off the UI thread. The recognizer reads from an `@Volatile`-annotated copy of the threshold object; no locking is needed in the hot path.

### 3. Real-Time Recognizer (Zero-Lag Path)
- The gesture recognizer checks only: `currentVelocity >= calibrated.triggerVelocity && currentDistance >= calibrated.triggerDistance && angle within calibrated.cone`.
- All values are primitive floats loaded from memory — no allocations, no I/O.
- If no calibrated profile exists yet (first launch), fall back to the static hardcoded defaults immediately.

### 4. Error Signal Collection
- When the user performs **undo** (backspace immediately after a swipe-to-space or swipe-to-delete fires), increment an `errorCount` for that direction.
- After 5 consecutive errors on one direction: automatically widen the threshold by 10% (the gesture was being triggered too easily). After 10 consecutive successes: tighten by 5%.
- This adjustment is immediate (in-memory update), then persisted asynchronously.

### 5. Personalization Scope
- Calibration is per-app-language, per-user (tied to the user ID in SharedPreferences partition).
- No data ever leaves the device. This is fully local, offline learning.
- On factory reset or app reinstall, the profile resets to defaults.

### 6. What NOT to Do
- **Never** retrain a model on every keystroke. That would cause stuttering.
- **Never** block the main gesture handler on SharedPreferences I/O.
- **Never** use a neural network for this — simple rolling mean + exponential smoothing is fast and accurate enough, and has zero cold-start penalty.
