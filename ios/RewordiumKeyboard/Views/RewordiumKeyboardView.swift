import SwiftUI
import UIKit
import KeyboardKit

/// Root SwiftUI hierarchy for the keyboard.
///
/// Structure (top-to-bottom):
///   1. AIToolbar       — morphing surface (collapsed pill / action grid / result)
///   2. SuggestionStrip — predictive bar (UITextChecker + recent words)
///   3. KeyboardView    — KeyboardKit's stock QWERTY with overridden:
///        - `.space` → CustomSpacebar (renders the "rew testing" build label)
///        - `.nextKeyboard` → GlobeButton (explicit, with system long-press picker)
///        - `emojiKeyboard` → RewordiumEmojiPanel
struct RewordiumKeyboardView: View {

    let services: Keyboard.Services
    let state: Keyboard.State
    unowned let controller: KeyboardInputViewController
    let aiService: AIService
    /// True when KeyboardKit failed to initialize. We still need to draw
    /// *something* — an extension that returns no view at all is killed by
    /// iOS, which then de-prioritizes us in the globe rotation. So we render
    /// a minimal stock keyboard with no AI surface.
    let setupDidFail: Bool

    var body: some View {
        if setupDidFail {
            fallbackKeyboard
        } else {
            VStack(spacing: 0) {
                AIToolbar(aiService: aiService, controller: controller)
                SuggestionStrip(controller: controller)
                keyboard
            }
            .background(Color.clear)
        }
    }

    /// KeyboardKit's stock keyboard view with our overrides.
    ///
    /// `buttonView` lets us intercept per-button rendering. We layer SwiftUI
    /// content over `params.view` (the framework's default rendering) so
    /// press feedback, repeat behavior, and background materials stay
    /// intact — we're only decorating, not replacing the button's gesture
    /// stack.
    private var keyboard: some View {
        KeyboardView(
            layout: nil,
            services: services,
            buttonContent: { $0.view },
            buttonView:    { params in
                // KeyboardKit passes us the default rendering as `params.view`.
                // Wrap once in AnyView so we can layer SwiftUI content over it
                // without the closure's return-type inference erupting.
                let defaultView = AnyView(params.view)
                switch params.item.action {
                case .space:
                    AnyView(CustomSpacebar(defaultView: defaultView))
                case .nextKeyboard:
                    AnyView(GlobeButtonView(defaultView: defaultView, controller: controller))
                default:
                    defaultView
                }
            },
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
                        state.keyboardContext.keyboardType = .alphabetic
                    }
                )
            },
            toolbar:       { _ in EmptyView() }
        )
    }

    /// Bare-bones fallback when setup fails. Renders only the system
    /// QWERTY — no AI toolbar, no suggestion strip — so iOS still sees us
    /// as a working keyboard and keeps us in the globe rotation.
    private var fallbackKeyboard: some View {
        KeyboardView(
            layout: nil,
            services: services,
            buttonContent: { $0.view },
            buttonView:    { $0.view },
            collapsedView: { $0.view },
            emojiKeyboard: { $0.view },
            toolbar:       { _ in EmptyView() }
        )
    }
}
