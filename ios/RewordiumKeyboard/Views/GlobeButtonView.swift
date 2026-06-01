import SwiftUI
import UIKit
import KeyboardKit

/// Custom globe button that explicitly wires into UIKit's next-keyboard APIs.
///
/// Why this exists:
///   The framework default renders a globe and posts an action, but on some
///   iOS 26 builds the chained dispatch through KeyboardKit's action handler
///   misses the `advanceToNextInputMode()` call — the user taps the button
///   and nothing happens. Calling the controller method directly avoids
///   that whole indirection.
///
/// Behavior matches the system keyboard's globe key:
///   • Tap         → `advanceToNextInputMode()` (cycle to next enabled keyboard)
///   • Long-press  → `handleInputModeList(from:with:)` (system picker)
struct GlobeButtonView: View {

    let defaultView: AnyView
    unowned let controller: KeyboardInputViewController

    var body: some View {
        // Layer over the framework's default chrome so we inherit the right
        // sizing, background material, and press-feedback. The transparent
        // overlay catches gestures first; if it does nothing, the
        // underlying view still won't fire because we consume the tap.
        defaultView
            .overlay(
                GlobeGestureCatcher(controller: controller)
                    .accessibilityLabel("Next keyboard")
                    .accessibilityHint("Double tap to switch keyboards. Long press to choose.")
            )
    }
}

/// UIView shim that owns the tap + long-press gesture recognizers wired to
/// the controller. UIKit owns next-keyboard plumbing — calling
/// `handleInputModeList(from:with:)` requires a real `UIView` for the
/// presentation anchor, which SwiftUI's gesture modifiers can't supply.
private struct GlobeGestureCatcher: UIViewRepresentable {
    unowned let controller: KeyboardInputViewController

    func makeCoordinator() -> Coordinator {
        Coordinator(controller: controller)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = true

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.didTap(_:))
        )
        view.addGestureRecognizer(tap)

        let long = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.didLongPress(_:))
        )
        long.minimumPressDuration = 0.35
        view.addGestureRecognizer(long)

        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    final class Coordinator: NSObject {
        unowned let controller: KeyboardInputViewController
        init(controller: KeyboardInputViewController) {
            self.controller = controller
        }

        @objc func didTap(_ recognizer: UITapGestureRecognizer) {
            controller.advanceToNextInputMode()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }

        @objc func didLongPress(_ recognizer: UILongPressGestureRecognizer) {
            guard recognizer.state == .began, let view = recognizer.view else { return }
            controller.handleInputModeList(from: view, with: UIEvent())
        }
    }
}
