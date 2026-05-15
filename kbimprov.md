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
