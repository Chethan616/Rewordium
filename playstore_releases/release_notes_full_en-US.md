# Rewordium — Release Notes (en-US)# Rewordium — Release Notes (en-US)



Version: 1.0.10+10Version: 1.0.9+9

Date: 2025-11-07Date: 2025-10-12



## HighlightsHighlights

- Performance: Major launch-time optimizations and UI caching make the keyboard feel "buttery-smooth" — faster key processing, reduced suggestion latency, and faster panel toggles.

- **Turbo Delete Enhancement**: Completely redesigned backspace acceleration with 5-tier progressive system (400ms → 80ms → 60ms → 45ms → 35ms) that feels natural and smooth like Gboard. The initial delay is gentle, then progressively speeds up based on delete count for optimal user experience.- Haptics: Premium haptic feedback is enabled by default and tuned for minimal latency while preserving battery efficiency.

- Clipboard Sync: A central clipboard manager now ensures pasted/copied content appears instantly in the keyboard's clipboard history panel; favorites, deletes, and clear actions are persisted and updated in real-time.

- **Experimental Feature Dialog**: Added transparent communication popup when users enable the keyboard. Dialog displays:- UI/UX: Cached theme and settings panels for instant toggling; improved popup behavior that mirrors Gboard-quality popups; refined animations with lower frame-time impact.

  - Development status notice (still in active development)

  - Mission statement: Privacy & Security First, Lightning Fast Performance, Advanced AI FeaturesFixes & Improvements

  - Free development acknowledgment- Fixed multiple issues causing double haptics and reduced redundant vibration calls.

  - Support/donation link (https://www.rewordium.tech/donate)- Deferred non-critical startup work to lower TTI and startup jank.

  - Two-line title display for clean presentation on all screen sizes- Fixed clipboard panel touch-pass-through by consuming touch events when the panel is visible.

- Reduced suggestion update delays from 50ms to 30ms for snappier suggestions.

- **UI/UX Improvements**: - Memory optimizations in clipboard history storage to avoid excessive memory growth.

  - Fixed dialog title overflow issues with proper text wrapping

  - Experimental feature dialog appears on both keyboard settings screen and home screen manage buttonNotes for QA / Release Engineers

  - Reusable dialog component eliminates code duplication (reduced 167 lines)- This release includes an updated Android IME implementation (Kotlin + Jetpack Compose mix). Verify the keyboard's accessibility and haptics across common Android versions.

- When testing clipboard sync, ensure the system clipboard additions show up immediately in the keyboard's clipboard panel, and favorites/clear operations persist across restarts.

- **Keyboard Fixes**:- The `playstore_releases/` folder contains the short `release_notes_en-US.txt` suitable for the Play Console and a longer `release_notes_full_en-US.md` for public changelogs or blog posts.

  - Key popup previews no longer get stuck on screen after key release

  - Immediate popup removal for cleaner visual experienceDeveloper credits

  - Added haptic feedback to backspace key press for better tactile response- Engineering: Performance & clipboard sync, UI caching, haptics tuning.

  - Fixed compilation error with duplicate @SuppressLint annotation- QA: Stability tests on Android 11–14.



## Fixes & ImprovementsIf you'd like localized translations, a formatted CSV for Play Console bulk upload, or a prepared GitHub release entry, tell me which locales and I will generate them.

- **Turbo Delete Acceleration**: Replaced 3-tier system with smoother 5-tier progressive curve:
  - First repeat: 400ms (gentle Gboard-like start)
  - Deletes 1-3: 80ms (comfortable speed)
  - Deletes 4-7: 60ms (speeding up)
  - Deletes 8-14: 45ms (faster)
  - Deletes 15+: 35ms (maximum turbo speed)

- **Code Architecture**: Created reusable `experimental_keyboard_dialog.dart` widget with Material Design, color-coded sections (orange development notice, blue mission, green free development, purple support), and url_launcher integration

- **Keyboard Implementation**: Enhanced `KeyPopupPreview.kt` hide() method to use immediate removal instead of delayed animation, preventing stuck popups

- **Haptic Feedback**: Added tactile response in `KeyboardLayoutManager.kt` for backspace ACTION_DOWN event

## Files Modified

- `lib/widgets/experimental_keyboard_dialog.dart` (NEW - 171 lines): Reusable experimental feature popup
- `android/app/src/main/kotlin/.../KeyboardConstants.kt`: Updated turbo delete timing constants
- `android/app/src/main/kotlin/.../RewordiumAIKeyboardService.kt`: Implemented 5-tier acceleration
- `lib/widgets/home/keyboard_status_card.dart`: Added popup to manage button
- `lib/screens/keyboard_settings_screen.dart`: Refactored to use shared dialog component
- `android/app/src/main/kotlin/.../KeyPopupPreview.kt`: Fixed stuck popup issue
- `android/app/src/main/kotlin/.../KeyboardLayoutManager.kt`: Added haptic feedback and cleanup
- `pubspec.yaml`: Updated version to 1.0.10+10

## Notes for QA / Release Engineers

- Test the experimental feature dialog appears correctly on both home screen (Manage button) and keyboard settings screen
- Verify turbo delete acceleration feels smooth and natural when holding backspace
- Confirm dialog title displays cleanly on various screen sizes without overflow
- Test donation link opens external browser correctly
- Verify key popups no longer get stuck after quick typing
- Check haptic feedback works on backspace press

## Build Information

- Build System: Gradle 8.7.0, Android SDK 36, Flutter SDK
- Build Type: Release (App Bundle for Play Store)
- Target Platforms: Android 5.0+ (API 21+)
- Commit: See Git history for detailed change log

## Developer Credits

- Engineering: Turbo delete redesign, experimental dialog implementation, UI overflow fixes, keyboard popup fixes
- Testing: Android build verification, dialog display testing
- Git Workflow: Test branch with proper commit history

## Next Steps

- Device testing on physical Android devices
- User feedback on turbo delete acceleration curve
- PR creation from test → playstore branch
- Play Store submission after successful testing

If you'd like localized translations, a formatted CSV for Play Console bulk upload, or a prepared GitHub release entry, tell me which locales and I will generate them.
