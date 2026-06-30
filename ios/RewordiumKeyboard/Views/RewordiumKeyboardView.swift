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
            AIToolbar(aiService: aiService, controller: controller)
            SuggestionStrip(controller: controller)
            keyboard
        }
        .background(Color.clear)
    }

    /// KeyboardKit's stock keyboard view with our overrides.
    ///
    /// `buttonView` lets us intercept per-button rendering. We layer SwiftUI
    private var customLayout: KeyboardLayout {
        var layout = KeyboardLayout.standard(for: state.keyboardContext)
        layout.itemRows = layout.itemRows.map { row in
            row.filter { $0.action != KeyboardAction.capsLock && $0.action != KeyboardAction.tab }
        }
        return layout
    }

    /// This view is the main view of the keyboard. We use the default `KeyboardView`
    /// but intercept `.space` and `.nextKeyboard` (Globe). We leave the rest
    /// intact — we're only decorating, not replacing the button's gesture
    /// stack.
    private var keyboard: some View {
        KeyboardView(
            layout: customLayout,
            services: services,
            buttonContent: { params in
                switch params.item.action {
                case .nextKeyboard:
                    Image(systemName: "globe")
                        .font(.body)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                case .dismissKeyboard:
                    Image(systemName: "keyboard.chevron.compact.down")
                        .font(.body)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                case .keyboardType(let type):
                    switch type {
                    case .emojis:
                        Image(systemName: "face.smiling")
                            .font(.body)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    case .numeric:
                        Text("123")
                            .font(.body)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    case .alphabetic:
                        Text("ABC")
                            .font(.body)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    case .symbolic:
                        Text("#+=")
                            .font(.body)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    default:
                        params.view
                    }
                case .backspace:
                    Image(systemName: "delete.left")
                        .font(.body)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                case .shift(let currentCase):
                    let isCaps = currentCase == .capsLocked
                    let isUpper = currentCase == .uppercased
                    let active = isCaps || isUpper
                    Image(systemName: isCaps ? "capslock.fill" : (isUpper ? "shift.fill" : "shift"))
                        .font(.body)
                        .foregroundStyle(active ? Color.accentColor : Color.primary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                default:
                    params.view
                }
            },
            buttonView: { params in
                switch params.item.action {
                case .space:
                    CustomSpacebar(defaultView: params.view)
                case .nextKeyboard:
                    GlobeButtonView(defaultView: params.view, controller: controller)
                case .backspace:
                    SmartBackspaceButton(controller: controller, defaultView: params.view)
                default:
                    params.view
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
