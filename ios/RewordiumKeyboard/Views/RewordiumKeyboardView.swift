import SwiftUI
import UIKit
import KeyboardKit

/// Root SwiftUI hierarchy for the keyboard.
///
/// Structure (top-to-bottom):
///   1. BuildBanner     — always-visible "Rewordium vX (build) • rew test"
///                        diagnostic. If you don't see this, the extension
///                        binary isn't loading.
///   2. AIToolbar       — morphing surface (collapsed pill / action grid /
///                        result), with the ✨ AI button that opens it.
///   3. SuggestionStrip — predictive bar (UITextChecker + recent words).
///                        Only visible while a partial word is being typed.
///   4. KeyboardView    — KeyboardKit's stock QWERTY with overridden:
///                        - `.space` → CustomSpacebar (renders "rew testing")
///                        - `.nextKeyboard` → GlobeButton (explicit next-input
///                          with system long-press picker)
///                        - `emojiKeyboard` → RewordiumEmojiPanel
struct RewordiumKeyboardView: View {

    let services: Keyboard.Services
    let state: Keyboard.State
    unowned let controller: KeyboardInputViewController
    let aiService: AIService

    var body: some View {
        // BuildBanner is unconditional — independent of any KeyboardKit
        // initialization state. If the user sees the banner, the extension
        // binary is loading. That eliminates the "is it stock iOS or my
        // build?" ambiguity that an indistinguishable fallback would create.
        //
        // The full hierarchy below assumes KeyboardKit's services and state
        // are wired. If a real init failure ever happens, individual views
        // (AIToolbar, SuggestionStrip) handle it locally rather than the
        // root replacing itself with a stock-looking layout that would mask
        // the failure.
        VStack(spacing: 0) {
            // 22pt top inset: the UIKit diagnostic banner that
            // KeyboardViewController installs in viewDidLoad sits at the top
            // of self.view with height 22. The SwiftUI hierarchy lives
            // inside the SAME self.view (KeyboardKit's hosting controller
            // makes it a subview), so without this inset the SwiftUI
            // content draws over the UIKit banner. The inset is invisible
            // to the user — they just see the purple UIKit banner above a
            // slightly-shifted SwiftUI hierarchy.
            Color.clear.frame(height: 22)

            BuildBanner()
            AIToolbar(aiService: aiService, controller: controller)
            SuggestionStrip(controller: controller)
            keyboard
        }
        .background(Color.clear)
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
}
