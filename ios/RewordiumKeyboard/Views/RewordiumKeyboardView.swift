import SwiftUI
import KeyboardKit

/// Root SwiftUI hierarchy for the keyboard.
///
/// Structure (top-to-bottom):
///   1. AIToolbar     — morphing surface (collapsed pill / action grid / result)
///   2. KeyboardView  — KeyboardKit's stock QWERTY, untouched
///
/// We pass everything through from the controller so KeyboardKit handles
/// input, layout, themes, dark mode, and dynamic type automatically.
struct RewordiumKeyboardView: View {

    unowned var controller: KeyboardInputViewController
    let aiService: AIService

    var body: some View {
        VStack(spacing: 0) {
            AIToolbar(aiService: aiService, controller: controller)
            keyboard
        }
        .background(Color.clear)
    }

    /// KeyboardKit's default keyboard. We don't customize button content,
    /// callouts, or layout for v1 — the stock keyboard is already the gold
    /// standard for an iOS QWERTY experience. Phase 5 may layer in our own
    /// number row toggle / theme tweaks.
    private var keyboard: some View {
        KeyboardView(
            controller: controller,
            buttonContent: { params in params.view },
            buttonView:    { params in params.view },
            collapsedView: { params in params.view },
            emojiKeyboard: { _ in EmptyView() },        // emoji kbd is Pro
            toolbar:       { _ in EmptyView() }         // our AIToolbar replaces it
        )
    }
}
