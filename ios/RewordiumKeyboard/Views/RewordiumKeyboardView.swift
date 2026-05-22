import SwiftUI
import KeyboardKit

/// Root SwiftUI hierarchy for the keyboard.
///
/// Structure (top-to-bottom):
///   1. AIToolbar     — morphing surface (collapsed pill / action grid / result)
///   2. KeyboardView  — KeyboardKit's stock QWERTY
///
/// API surface matches the upstream demo: we receive `services` + `state`
/// from `setupKeyboardView`, plus the controller (for `textDocumentProxy`
/// access we need in the Apply flow).
struct RewordiumKeyboardView: View {

    let services: Keyboard.Services
    let state: Keyboard.State
    unowned let controller: KeyboardInputViewController
    let aiService: AIService

    var body: some View {
        VStack(spacing: 0) {
            AIToolbar(aiService: aiService, controller: controller)
            keyboard
        }
        .background(Color.clear)
    }

    /// KeyboardKit's stock keyboard view. The v10.5 initializer takes a
    /// `services` value (not the controller) and ViewBuilder closures for the
    /// customizable surfaces. Passing `$0.view` from each builder means "use
    /// the default" — we only override `toolbar` because our AIToolbar lives
    /// above the keyboard, not inside KeyboardKit's toolbar slot.
    private var keyboard: some View {
        KeyboardView(
            layout: nil,
            services: services,
            buttonContent: { $0.view },
            buttonView:    { $0.view },
            collapsedView: { $0.view },
            emojiKeyboard: { $0.view },
            toolbar:       { _ in EmptyView() }   // our AIToolbar replaces it
        )
    }
}
