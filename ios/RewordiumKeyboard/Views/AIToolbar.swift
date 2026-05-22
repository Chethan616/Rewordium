import SwiftUI
import KeyboardKit

/// The single morphing surface that sits above the keyboard.
///
/// One view, three visual states (collapsed → grid → result). No modal
/// sheets, no overlays — the toolbar height grows in place so the keyboard
/// stays put. This is how the iOS system predictive bar feels.
struct AIToolbar: View {

    enum Mode: Equatable {
        case collapsed
        case actionGrid
        case running(AIAction)
        case result(String)
        case error(String)
    }

    /// `@Observable` makes property access in `body` self-tracking, so plain
    /// `let` is enough — no `@Bindable` or `@ObservedObject` required.
    let aiService: AIService
    unowned let controller: KeyboardInputViewController
    @State private var mode: Mode = .collapsed
    @State private var customPromptText: String = ""
    @State private var showsCustomPromptField: Bool = false

    var body: some View {
        ZStack(alignment: .top) {
            // Surface background — extends edge-to-edge for tactile feel.
            Color.clear.rewordiumSurface()

            content
                .padding(.horizontal, RewordiumTokens.Space.md)
                .padding(.vertical, RewordiumTokens.Space.sm)
        }
        .frame(minHeight: 44)
        .animation(RewordiumTokens.AnimationCurve.surface, value: mode)
        .onChange(of: aiService.status) { _, newStatus in
            syncMode(from: newStatus)
        }
    }

    // MARK: - Mode dispatch

    @ViewBuilder
    private var content: some View {
        switch mode {
        case .collapsed:    collapsedView
        case .actionGrid:   actionGridView
        case .running(let action): runningView(for: action)
        case .result(let text):    resultView(text: text)
        case .error(let message):  errorView(message: message)
        }
    }

    // MARK: - Collapsed (compact pill)
    //
    // Single line: "Tap to rewrite" hint on the left, sparkle pill on the
    // right. Matches the system predictive bar's information density.

    private var collapsedView: some View {
        HStack(spacing: RewordiumTokens.Space.sm) {
            Text("Tap to rewrite with AI")
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Spacer(minLength: 0)

            sparkleButton
        }
    }

    private var sparkleButton: some View {
        Button {
            withAnimation(RewordiumTokens.AnimationCurve.surface) {
                mode = .actionGrid
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.system(size: 13, weight: .medium))
                Text("AI")
                    .font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(Color.accentColor)
            .padding(.horizontal, RewordiumTokens.Space.md)
            .padding(.vertical, RewordiumTokens.Space.xs)
            .background(
                Capsule().fill(Color.accentColor.opacity(0.12))
            )
            .overlay(
                Capsule().strokeBorder(Color.accentColor.opacity(0.25), lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Action grid (8 chips in 2×4)

    private var actionGridView: some View {
        VStack(alignment: .leading, spacing: RewordiumTokens.Space.sm) {
            HStack(spacing: RewordiumTokens.Space.sm) {
                Text("Rewrite with")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.primary)
                Spacer()
                closeButton
            }

            if showsCustomPromptField {
                customPromptField
            } else {
                chipsGrid
            }
        }
    }

    private var chipsGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: RewordiumTokens.Space.sm), count: 4)
        return LazyVGrid(columns: columns, spacing: RewordiumTokens.Space.sm) {
            ForEach(AIAction.allCases) { action in
                AIActionChip(action: action) {
                    if action == .custom {
                        withAnimation(RewordiumTokens.AnimationCurve.surface) {
                            showsCustomPromptField = true
                        }
                    } else {
                        run(action)
                    }
                }
            }
        }
    }

    private var customPromptField: some View {
        VStack(alignment: .leading, spacing: RewordiumTokens.Space.sm) {
            TextField("Tell the AI what to do…", text: $customPromptText, axis: .vertical)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .lineLimit(1...3)
                .padding(.horizontal, RewordiumTokens.Space.md)
                .padding(.vertical, RewordiumTokens.Space.sm)
                .rewordiumChip()

            HStack {
                Button("Back") {
                    withAnimation(RewordiumTokens.AnimationCurve.surface) {
                        showsCustomPromptField = false
                    }
                }
                .buttonStyle(.plain)
                .font(.system(size: 13))
                .foregroundStyle(.secondary)

                Spacer()

                Button {
                    run(.custom, customInstruction: customPromptText)
                } label: {
                    Text("Run")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, RewordiumTokens.Space.md)
                        .padding(.vertical, RewordiumTokens.Space.xs)
                        .background(
                            Capsule().fill(customPromptText.trimmingCharacters(in: .whitespaces).isEmpty
                                ? Color.gray.opacity(0.4)
                                : Color.accentColor)
                        )
                }
                .buttonStyle(.plain)
                .disabled(customPromptText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }

    // MARK: - Running

    private func runningView(for action: AIAction) -> some View {
        HStack(spacing: RewordiumTokens.Space.sm) {
            ProgressView()
                .scaleEffect(0.7)
                .frame(width: 18, height: 18)
            Text(action.loadingLabel)
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
            Spacer()
            closeButton
        }
    }

    // MARK: - Result

    private func resultView(text: String) -> some View {
        VStack(alignment: .leading, spacing: RewordiumTokens.Space.sm) {
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(.primary)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: RewordiumTokens.Space.sm) {
                Button {
                    withAnimation(RewordiumTokens.AnimationCurve.surface) {
                        mode = .actionGrid
                    }
                } label: {
                    Label("Try again", systemImage: "arrow.counterclockwise")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

                Spacer()

                Button {
                    apply(text: text)
                } label: {
                    Text("Apply")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, RewordiumTokens.Space.md)
                        .padding(.vertical, RewordiumTokens.Space.xs)
                        .background(Capsule().fill(Color.accentColor))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Error

    private func errorView(message: String) -> some View {
        HStack(spacing: RewordiumTokens.Space.sm) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 14))
                .foregroundStyle(.orange)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(.primary)
                .lineLimit(2)
            Spacer()
            Button("Dismiss") {
                aiService.clearError()
                withAnimation(RewordiumTokens.AnimationCurve.surface) {
                    mode = .actionGrid
                }
            }
            .buttonStyle(.plain)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(.secondary)
        }
    }

    private var closeButton: some View {
        Button {
            withAnimation(RewordiumTokens.AnimationCurve.surface) {
                mode = .collapsed
                showsCustomPromptField = false
            }
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.secondary)
                .frame(width: 22, height: 22)
                .background(Circle().fill(Color.primary.opacity(0.08)))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Behavior

    private func run(_ action: AIAction, customInstruction: String? = nil) {
        let source = currentText()
        aiService.run(action, on: source, customInstruction: customInstruction) { _ in
            // status changes drive UI via syncMode(from:)
        }
    }

    private func syncMode(from status: AIService.Status) {
        withAnimation(RewordiumTokens.AnimationCurve.surface) {
            switch status {
            case .idle:
                if let text = aiService.lastResult {
                    mode = .result(text)
                } else if case .actionGrid = mode {
                    // stay in grid
                }
            case .running(let action):
                mode = .running(action)
            case .error(let message):
                mode = .error(message)
            }
        }
    }

    /// Pull the text we should act on. Priority:
    ///   1. Currently-selected text (if any).
    ///   2. The full document context if it's short enough.
    ///   3. The last sentence (split on .?!) — covers the common "fix what
    ///      I just typed" case without sending wall-of-text to Groq.
    private func currentText() -> String {
        let proxy = controller.textDocumentProxy
        if let selected = proxy.selectedText, !selected.isEmpty {
            return selected
        }
        let before = proxy.documentContextBeforeInput ?? ""
        let after = proxy.documentContextAfterInput ?? ""
        let combined = (before + after).trimmingCharacters(in: .whitespacesAndNewlines)
        if combined.count <= 600 {
            return combined
        }
        // Last-sentence heuristic.
        let terminators = CharacterSet(charactersIn: ".!?")
        let parts = before.unicodeScalars
            .split(whereSeparator: { terminators.contains($0) })
            .map(String.init)
        return (parts.last ?? before).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Replace the source text with the AI result by deleting backward across
    /// the source range and inserting the rewrite. Selection-aware.
    private func apply(text: String) {
        let proxy = controller.textDocumentProxy
        if let selected = proxy.selectedText, !selected.isEmpty {
            // KeyboardKit's input view controller doesn't expose a direct
            // replace-selection API — deleting forwards across the selection
            // is what UIKit gives us.
            for _ in 0..<selected.count {
                proxy.deleteBackward()
            }
            proxy.insertText(text)
        } else {
            let source = currentText()
            for _ in 0..<source.count {
                proxy.deleteBackward()
            }
            proxy.insertText(text)
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(RewordiumTokens.AnimationCurve.surface) {
            mode = .collapsed
        }
        aiService.reset()
    }
}

// MARK: - Chip

private struct AIActionChip: View {
    let action: AIAction
    let onTap: () -> Void
    @State private var isPressed = false

    var body: some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onTap()
        } label: {
            VStack(spacing: 4) {
                Image(systemName: action.systemImage)
                    .font(.system(size: 16, weight: .regular))
                    .foregroundStyle(.primary)
                Text(action.title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, RewordiumTokens.Space.sm)
            .padding(.horizontal, RewordiumTokens.Space.xs)
            .rewordiumChip(isPressed: isPressed)
        }
        .buttonStyle(.plain)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
    }
}
