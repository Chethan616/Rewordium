# Rewordium — Release Notes (en-US)

Version: 1.0.9+9
Date: 2025-10-12

Highlights
- Performance: Major launch-time optimizations and UI caching make the keyboard feel "buttery-smooth" — faster key processing, reduced suggestion latency, and faster panel toggles.
- Haptics: Premium haptic feedback is enabled by default and tuned for minimal latency while preserving battery efficiency.
- Clipboard Sync: A central clipboard manager now ensures pasted/copied content appears instantly in the keyboard's clipboard history panel; favorites, deletes, and clear actions are persisted and updated in real-time.
- UI/UX: Cached theme and settings panels for instant toggling; improved popup behavior that mirrors Gboard-quality popups; refined animations with lower frame-time impact.

Fixes & Improvements
- Fixed multiple issues causing double haptics and reduced redundant vibration calls.
- Deferred non-critical startup work to lower TTI and startup jank.
- Fixed clipboard panel touch-pass-through by consuming touch events when the panel is visible.
- Reduced suggestion update delays from 50ms to 30ms for snappier suggestions.
- Memory optimizations in clipboard history storage to avoid excessive memory growth.

Notes for QA / Release Engineers
- This release includes an updated Android IME implementation (Kotlin + Jetpack Compose mix). Verify the keyboard's accessibility and haptics across common Android versions.
- When testing clipboard sync, ensure the system clipboard additions show up immediately in the keyboard's clipboard panel, and favorites/clear operations persist across restarts.
- The `playstore_releases/` folder contains the short `release_notes_en-US.txt` suitable for the Play Console and a longer `release_notes_full_en-US.md` for public changelogs or blog posts.

Developer credits
- Engineering: Performance & clipboard sync, UI caching, haptics tuning.
- QA: Stability tests on Android 11–14.

If you'd like localized translations, a formatted CSV for Play Console bulk upload, or a prepared GitHub release entry, tell me which locales and I will generate them.