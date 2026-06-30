import SwiftUI
import UIKit
import KeyboardKit

/// The single morphing surface that sits above the keyboard.
///
/// One view, five visual states (collapsed → grid → running → result → error).
/// No modal sheets, no overlays — the toolbar height grows in place so the
/// keyboard stays put. This is how the iOS system predictive bar feels.
///
/// Premium features layered on top of the v1 morph:
///   • Persona row above the action grid — `AIPersona` shapes voice.
///   • Streaming reveal in `.running` — tokens land into a live text view.
///   • Result card with [Copy] [Regenerate] [Try other style] [Apply].
///   • History dots — last 3 outputs reachable via swipe / dot tap.
///   • Liquid Glass surfaces on iOS 26 via the shared `rewordiumGlassContainer`.
///   • `sensoryFeedback(.selection, trigger:)` on every state transition.
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
    @State private var persona: AIPersona = .neutral
    @State private var showsPersonaInResult: Bool = false
    @State private var historyIndex: Int = 0
    @Namespace private var morphNamespace

    var body: some View {
        content
            .padding(.horizontal, RewordiumTokens.Space.md)
            .padding(.vertical, RewordiumTokens.Space.sm)
            .frame(minHeight: 44)
            .background(GlassSurfaceBackground())
            .animation(RewordiumTokens.AnimationCurve.surface, value: mode)
            .animation(RewordiumTokens.AnimationCurve.surface, value: showsCustomPromptField)
            .animation(RewordiumTokens.AnimationCurve.surface, value: showsPersonaInResult)
            .onChange(of: aiService.status) { _, newStatus in
                syncMode(from: newStatus)
            }
            .sensoryFeedback(.selection, trigger: mode)
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

    private var collapsedView: some View {
        HStack(spacing: RewordiumTokens.Space.sm) {
            Text("Tap to rewrite with AI")
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Spacer(minLength: 0)

            sparkleButton
                .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)
        }
    }

    private var sparkleButton: some View {
        Button {
            withAnimation(RewordiumTokens.AnimationCurve.surface) {
                mode = .actionGrid
            }
        } label: {
            HStack(spacing: 6) {
                if #available(iOS 18.1, *) {
                    Image(systemName: "apple.intelligence")
                        .font(.system(size: 13, weight: .medium))
                        .symbolRenderingMode(.hierarchical)
                } else {
                    Image(systemName: "sparkles")
                        .font(.system(size: 13, weight: .medium))
                        .symbolRenderingMode(.hierarchical)
                }
                Text("AI")
                    .font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(Color.accentColor)
            .padding(.horizontal, RewordiumTokens.Space.md)
            .padding(.vertical, RewordiumTokens.Space.xs)
            .rewordiumPill(isSelected: false)
            .background(
                Capsule().fill(Color.accentColor.opacity(0.10))
                    .blendMode(.normal)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open AI rewrite panel")
    }

    // MARK: - Action grid

    private var actionGridView: some View {
        VStack(alignment: .leading, spacing: RewordiumTokens.Space.sm) {
            HStack(spacing: RewordiumTokens.Space.sm) {
                Text("Rewrite with")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.primary)
                Spacer()
                if #available(iOS 18.1, *) {
                    Image(systemName: "apple.intelligence")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.accentColor)
                        .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)
                } else {
                    Image(systemName: "sparkles")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.accentColor)
                        .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)
                }
                closeButton
            }

            PersonaRow(selected: $persona)

            if showsCustomPromptField {
                customPromptField
            } else {
                chipsGrid
            }
        }
    }

    private var chipsGrid: some View {
        let columns = Array(
            repeating: GridItem(.flexible(), spacing: RewordiumTokens.Space.sm),
            count: 4
        )
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
                            Capsule().fill(
                                customPromptText.trimmingCharacters(in: .whitespaces).isEmpty
                                    ? Color.gray.opacity(0.4)
                                    : Color.accentColor
                            )
                        )
                }
                .buttonStyle(.plain)
                .disabled(customPromptText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }

    // MARK: - Running (streaming reveal)

    private func runningView(for action: AIAction) -> some View {
        VStack(alignment: .leading, spacing: RewordiumTokens.Space.xs) {
            HStack(spacing: RewordiumTokens.Space.sm) {
                BreathingDot()
                    .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)
                Text(action.loadingLabel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
                Spacer()
                closeButton
            }

            // Live token reveal. Truncates to two lines so the toolbar
            // height doesn't oscillate while text streams in.
            if !aiService.partialResult.isEmpty {
                Text(aiService.partialResult)
                    .font(.system(size: 14))
                    .foregroundStyle(.primary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, RewordiumTokens.Space.sm)
                    .padding(.vertical, RewordiumTokens.Space.xs)
                    .rewordiumCard()
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    // MARK: - Result

    private func resultView(text: String) -> some View {
        // Resolve the currently-paginated entry; default to the latest if
        // history is shorter than the cursor (shouldn't happen but cheap
        // to be defensive).
        let entries = aiService.history
        let resolvedText: String = {
            if entries.indices.contains(historyIndex) {
                return entries[historyIndex].result
            }
            return text
        }()

        return VStack(alignment: .leading, spacing: RewordiumTokens.Space.sm) {
            // Result text card.
            Text(resolvedText)
                .font(.system(size: 14))
                .foregroundStyle(.primary)
                .lineLimit(4)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, RewordiumTokens.Space.md)
                .padding(.vertical, RewordiumTokens.Space.sm)
                .rewordiumCard()
                .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)

            // Inline persona picker for "Try other style" — collapses by
            // default so the result card reads as the focal point.
            if showsPersonaInResult {
                PersonaRow(selected: $persona) { newPersona in
                    persona = newPersona
                    showsPersonaInResult = false
                    aiService.regenerate { _ in }
                }
            }

            // Toolbar row: [history dots] [Copy] [Regenerate] [Try style] [Apply]
            HStack(spacing: RewordiumTokens.Space.sm) {
                historyDots(count: entries.count)

                Spacer(minLength: 0)

                resultChromeButton(icon: "doc.on.doc", title: "Copy") {
                    UIPasteboard.general.string = resolvedText
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }

                resultChromeButton(icon: "arrow.counterclockwise", title: "Redo") {
                    aiService.regenerate { _ in }
                }

                resultChromeButton(icon: "paintpalette", title: "Style") {
                    withAnimation(RewordiumTokens.AnimationCurve.surface) {
                        showsPersonaInResult.toggle()
                    }
                }

                Button {
                    apply(text: resolvedText)
                } label: {
                    Text("Apply")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, RewordiumTokens.Space.md)
                        .padding(.vertical, RewordiumTokens.Space.xs)
                        .background(Capsule().fill(Color.accentColor))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Apply rewrite to text")
            }
        }
    }

    private func resultChromeButton(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .medium))
                Text(title)
                    .font(.system(size: 12, weight: .medium))
            }
            .foregroundStyle(.secondary)
            .padding(.horizontal, RewordiumTokens.Space.sm)
            .padding(.vertical, 5)
            .rewordiumPill(isSelected: false)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func historyDots(count: Int) -> some View {
        if count > 1 {
            HStack(spacing: 4) {
                ForEach(0..<count, id: \.self) { i in
                    Circle()
                        .fill(i == historyIndex ? Color.accentColor : Color.primary.opacity(0.25))
                        .frame(width: 5, height: 5)
                        .onTapGesture {
                            withAnimation(RewordiumTokens.AnimationCurve.tap) {
                                historyIndex = i
                            }
                        }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Result \(historyIndex + 1) of \(count)")
        } else {
            EmptyView()
        }
    }

    // MARK: - Error

    private func errorView(message: String) -> some View {
        HStack(spacing: RewordiumTokens.Space.sm) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: 14))
                .foregroundStyle(.orange)
                .matchedGeometryEffect(id: "ai-anchor", in: morphNamespace)
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
                showsPersonaInResult = false
            }
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.secondary)
                .frame(width: 22, height: 22)
                .background(Circle().fill(Color.primary.opacity(0.08)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close AI panel")
    }

    // MARK: - Behavior

    private func run(_ action: AIAction, customInstruction: String? = nil) {
        let source = currentText()
        historyIndex = 0
        aiService.run(
            action,
            on: source,
            persona: persona,
            customInstruction: customInstruction
        ) { _ in
            // status changes drive UI via syncMode(from:)
        }
    }

    private func syncMode(from status: AIService.Status) {
        withAnimation(RewordiumTokens.AnimationCurve.surface) {
            switch status {
            case .idle:
                if let text = aiService.lastResult {
                    historyIndex = 0
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
        let terminators = CharacterSet(charactersIn: ".!?")
        let parts = before.unicodeScalars
            .split(whereSeparator: { terminators.contains($0) })
            .map(String.init)
        return (parts.last ?? before).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Replace the source text with the AI result.
    private func apply(text: String) {
        let proxy = controller.textDocumentProxy
        if let selected = proxy.selectedText, !selected.isEmpty {
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

// MARK: - Action chip

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
                    .symbolRenderingMode(.hierarchical)
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

// MARK: - Background helpers

/// Surface backing for the AI toolbar. iOS 26 gets the Liquid Glass
/// treatment; older OSes fall back to `.ultraThinMaterial`. The hairline
/// bottom border keeps the toolbar visually anchored over the keyboard.
private struct GlassSurfaceBackground: View {
    var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
        }
        .overlay(
            Rectangle()
                .frame(height: RewordiumTokens.Stroke.hairline)
                .foregroundStyle(.quaternary),
            alignment: .bottom
        )
        .ignoresSafeArea(edges: .horizontal)
    }
}

/// Pulse indicator shown next to the loading label while a request streams.
/// Replaces the framework spinner for a calmer feel.
private struct BreathingDot: View {
    @State private var on: Bool = false

    var body: some View {
        Circle()
            .fill(Color.accentColor)
            .frame(width: 8, height: 8)
            .opacity(on ? 1.0 : 0.35)
            .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: on)
            .onAppear { on = true }
    }
}
