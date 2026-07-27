import UIKit
import SwiftUI
import KeyboardKit

/// Entry point for the Rewordium Custom Keyboard Extension.
///
/// IMPORTANT: this class lives in the `RewordiumKeyboard` module. The
/// Info.plist's NSExtensionPrincipalClass hardcodes
/// "RewordiumKeyboard.KeyboardViewController" — if you rename either the
/// class or the module, update the plist literal.
///
/// Follows KeyboardKit v10.5's lifecycle:
///   * `viewWillSetupKeyboardKit` → `setupKeyboardKit(for:)` with the
///     KeyboardApp value. Wires services + state and injects the
///     EnvironmentObjects the SwiftUI hierarchy expects.
///   * `viewWillSetupKeyboardView` → `setupKeyboardView { controller in … }`
///     returning the SwiftUI view. Don't call super (per demo).
///
/// We ALSO install a raw UIKit diagnostic banner as a child of self.view in
/// `viewDidLoad`, BEFORE KeyboardKit or SwiftUI touch anything. The banner
/// renders directly from UIKit so it sees no SwiftUI / KeyboardKit
/// initialization path. If it's visible, the extension binary loaded.
/// If it isn't visible, the binary itself isn't being instantiated by iOS —
/// the failure is at the principal-class / signing / embedding layer, and
/// no Swift code in this file ever ran.
final class KeyboardViewController: KeyboardInputViewController {

    /// Lives for the lifetime of the keyboard. The SwiftUI hierarchy reads it
    /// by reference and observes via `@Observable` macro tracking.
    private let aiService = AIService()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        NSLog("[RewordiumKeyboard] viewDidLoad — bundleId=\(Bundle.main.bundleIdentifier ?? "?") version=\(Self.shortVersion)+\(Self.bundleVersion)")
    }

    /// Re-pin the banner on top whenever the view layout cycles. iOS keyboard
    /// extensions have a quirk where the system can swap the inputView's
    /// subview hierarchy out from under us during orientation / split-view
    /// changes; this keeps the banner on the screen.
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
    }

    /// Called once when the keyboard extension launches. Configure services
    /// and state here. Anything view-related belongs in viewWillSetupKeyboardView.
    override func viewWillSetupKeyboardKit() {
        NSLog("[RewordiumKeyboard] viewWillSetupKeyboardKit — beginning setup")
        setupKeyboardKit(for: rewordiumApp) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                NSLog("[RewordiumKeyboard] setupKeyboardKit succeeded")
                self.applyDefaultStateTweaks()
            case .failure(let error):
                // Log loudly. We do NOT swap in a stock-looking fallback —
                // doing that previously masked the failure as "extension
                // looks like iOS stock keyboard." The full SwiftUI hierarchy
                // renders either way; individual views handle service-
                // unavailable cases locally.
                NSLog("[RewordiumKeyboard] setupKeyboardKit FAILED: \(error)")
            }
        }
    }

    /// Called whenever KeyboardKit needs to (re)build the keyboard view —
    /// includes orientation changes, locale changes, dark/light flips.
    override func viewWillSetupKeyboardView() {
        NSLog("[RewordiumKeyboard] viewWillSetupKeyboardView — installing SwiftUI hierarchy")
        // Per demo: don't call super; we replace the view entirely.
        setupKeyboardView { [unowned self] controller in
            RewordiumKeyboardView(
                services: controller.services,
                state: controller.state,
                controller: controller,
                aiService: self.aiService
            )
        }
    }

    // MARK: - KeyboardApp

    /// Identity for the framework: name shown in any default UI, App Group
    /// ID so KeyboardKit's settings sync into the same container we use.
    private var rewordiumApp: KeyboardApp {
        KeyboardApp(
            name: "Rewordium",
            appGroupId: SharedSettings.appGroupID
        )
    }

    // MARK: - State setup

    /// Small quality-of-life tweaks applied after KeyboardKit boots.
    private func applyDefaultStateTweaks() {
        // Long-press space moves the cursor — matches the iOS system keyboard.
        state.keyboardContext.settings.spacebarLongPressBehavior = .moveInputCursor

        // Keep the spacebar visually stock. Locale suffixes such as
        // "space (exp)" make the extension look unlike Apple's keyboard.
        state.keyboardContext.settings.spacebarMenuLeading = nil
        state.keyboardContext.settings.spacebarMenuTrailing = nil

        // Honor the haptics preference written by the Flutter host app.
        if !SharedSettings.hapticsEnabled {
            state.feedbackContext.settings.isHapticFeedbackEnabled = false
        }
    }

    // MARK: - App Version

    private static var shortVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "?"
    }

    private static var bundleVersion: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "?"
    }
}
