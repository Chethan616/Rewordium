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

    /// KeyboardKit's stock keyboard view. We override two slots:
    ///   * `toolbar` — our AIToolbar lives above the keyboard, not inside.
    ///   * `emojiKeyboard` — KeyboardKit's free-tier emoji surface is bare
    ///     (no recents persistence, no skin-tone variants, no category
    ///     navigation). `RewordiumEmojiPanel` is our production-grade
    ///     replacement; see that file for the design.
    private var keyboard: some View {
        KeyboardView(
            layout: nil,
            services: services,
            buttonContent: { $0.view },
            buttonView:    { $0.view },
            collapsedView: { $0.view },
            emojiKeyboard: { _ in
                RewordiumEmojiPanel(
                    onInsert: { emoji in
                        controller.textDocumentProxy.insertText(emoji)
                    },
                    onBackspace: {
                        controller.textDocumentProxy.deleteBackward()
                    },
                    onReturnToAlphabetic: {
                        // KeyboardContext owns the current keyboard type;
                        // setting it to .alphabetic flips back to QWERTY
                        // through KeyboardKit's normal redraw path.
                        state.keyboardContext.keyboardType = .alphabetic
                    }
                )
            },
            toolbar:       { _ in EmptyView() }   // our AIToolbar replaces it
        )
    }
}
