import UIKit
import SwiftUI
import KeyboardKit

/// Entry point for the Rewordium Custom Keyboard Extension.
///
/// Follows the KeyboardKit v10.5 lifecycle exactly as the upstream demo does:
///   * `viewWillSetupKeyboardKit` → call `setupKeyboardKit(for:)` with the
///     KeyboardApp value. This wires up services + state and injects all the
///     EnvironmentObjects (KeyboardContext, FeedbackContext, etc.) the SwiftUI
///     hierarchy expects.
///   * `viewWillSetupKeyboardView` → call `setupKeyboardView { controller in … }`
///     returning the SwiftUI view. Don't call super here (per demo comment).
final class KeyboardViewController: KeyboardInputViewController {

    /// Lives for the lifetime of the keyboard. The SwiftUI hierarchy reads it
    /// by reference and observes via `@Observable` macro tracking.
    private let aiService = AIService()

    /// Becomes `true` if the KeyboardKit boot threw. The view layer reads this
    /// and renders a minimal QWERTY fallback so iOS still sees a working
    /// keyboard — extensions that show a broken view get pulled from the
    /// globe-cycle rotation on the next launch.
    private var setupDidFail: Bool = false

    // MARK: - Lifecycle

    /// Called once when the keyboard extension launches. Configure services
    /// and state here. Anything view-related belongs in viewWillSetupKeyboardView.
    override func viewWillSetupKeyboardKit() {
        setupKeyboardKit(for: rewordiumApp) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                self.applyDefaultStateTweaks()
            case .failure(let error):
                // Don't silently swallow — iOS will still ask us to render
                // a view, and if the SwiftUI hierarchy assumes KeyboardKit
                // services are wired we'd crash. Flag the failure so the
                // view path can fall back to a stock layout.
                NSLog("[RewordiumKeyboard] setup failed: \(error)")
                self.setupDidFail = true
            }
        }
    }

    /// Called whenever KeyboardKit needs to (re)build the keyboard view —
    /// includes orientation changes, locale changes, dark/light flips.
    override func viewWillSetupKeyboardView() {
        // Per demo: don't call super; we replace the view entirely.
        setupKeyboardView { [unowned self] controller in
            RewordiumKeyboardView(
                services: controller.services,
                state: controller.state,
                controller: controller,
                aiService: self.aiService,
                setupDidFail: self.setupDidFail
            )
        }
    }

    /// Wired to the in-keyboard globe button. Tap = advance to next keyboard;
    /// long-press = show the system picker. Mirrors the system convention.
    func handleNextKeyboardTap(from view: UIView, with event: UIEvent?) {
        advanceToNextInputMode()
    }

    func handleNextKeyboardLongPress(from view: UIView, with event: UIEvent?) {
        handleInputModeList(from: view, with: event ?? UIEvent())
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

        // Show locale name in the spacebar's trailing menu when there are
        // multiple locales (no-op for single-locale setups).
        state.keyboardContext.settings.spacebarMenuTrailing = .locale

        // Honor the haptics preference written by the Flutter host app.
        if !SharedSettings.hapticsEnabled {
            state.feedbackContext.settings.isHapticFeedbackEnabled = false
        }
    }
}
