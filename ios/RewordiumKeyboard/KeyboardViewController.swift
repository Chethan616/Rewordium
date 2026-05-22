import UIKit
import SwiftUI
import KeyboardKit

/// Entry point for the Rewordium Custom Keyboard Extension.
///
/// KeyboardKit handles 99% of the keyboard mechanics — layout, button
/// rendering, dark mode, dynamic type, haptics, callouts. We supply:
///   * a `KeyboardApp` so the framework knows our App Group + identity
///   * a custom root view that prepends our `AIToolbar` above the keyboard
///
/// Anything else (autocomplete, emoji keyboard, themes) is intentionally
/// left at KeyboardKit defaults so a v1 ships before we get distracted.
final class KeyboardViewController: KeyboardInputViewController {

    /// Lives for the lifetime of the keyboard. The SwiftUI hierarchy
    /// observes it via `@Bindable` so status transitions animate cleanly.
    private let aiService = AIService()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        setup(for: rewordiumApp) { _ in
            // Result intentionally unused. The free tier of KeyboardKit
            // doesn't validate a license key, so success/failure here is
            // a no-op for us. When/if we move to Pro, plug license-check
            // telemetry in here.
        }
    }

    /// Called by KeyboardKit each time the keyboard view needs (re-)building,
    /// including locale changes and orientation flips. We rebuild from the
    /// current controller state every time — cheap, and avoids stale views.
    override func viewWillSetupKeyboardView() {
        super.viewWillSetupKeyboardView()
        setupKeyboardView { [unowned self] controller in
            RewordiumKeyboardView(controller: controller, aiService: self.aiService)
        }
    }

    // MARK: - KeyboardApp

    /// Identity for the framework: name shown in any default UI, App Group
    /// for state sync with the Flutter host.
    private var rewordiumApp: KeyboardApp {
        KeyboardApp(
            name: "Rewordium",
            appGroupId: SharedSettings.appGroupID
        )
    }
}
