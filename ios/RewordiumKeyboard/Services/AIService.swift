import Foundation
import Observation

/// Stateful façade the keyboard UI talks to. One instance is held by the
/// KeyboardViewController and observed by the SwiftUI hierarchy.
///
/// Single-flight: a new action cancels any in-progress one (typical for a
/// keyboard — users tap rapidly and only the latest result matters).
///
/// The service now supports streaming responses: while a request is in
/// flight, `partialResult` accumulates token-by-token as Groq sends SSE
/// chunks back. The toolbar's `.running` state reads that field so the
/// user sees text appear as it's generated, not after the request settles.
@MainActor
@Observable
final class AIService {

    enum Status: Equatable {
        case idle
        case running(AIAction)
        case error(String)
    }

    private(set) var status: Status = .idle
    /// Most recent finished output. The UI shows this in the result card.
    private(set) var lastResult: String?
    /// Live token accumulator while a request is streaming. Cleared on each
    /// new run; populated by the streaming callback. The UI reads this in
    /// the `.running` state to render a token-by-token reveal.
    private(set) var partialResult: String = ""
    /// Last 3 finished results (newest first). The result card shows a
    /// pagination dot row that lets the user swipe between them.
    private(set) var history: [HistoryEntry] = []

    /// Lightweight record so the UI knows which action produced each entry
    /// (useful for the "regenerate with same style" path).
    struct HistoryEntry: Identifiable, Equatable {
        let id = UUID()
        let action: AIAction
        let persona: AIPersona
        let sourceText: String
        let result: String
    }

    private var inflight: Task<Void, Never>?
    /// Stashed for `regenerate()` — what we sent last, so we can replay it.
    private var lastRequest: (action: AIAction, persona: AIPersona, source: String, customInstruction: String?)?

    private static let historyLimit = 3

    /// One-shot run. Streams tokens into `partialResult`; on completion,
    /// promotes the accumulated text into `lastResult` and the history
    /// ring.
    func run(
        _ action: AIAction,
        on text: String,
        persona: AIPersona = .neutral,
        customInstruction: String? = nil,
        onCompletion: @escaping (Result<String, Error>) -> Void
    ) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            status = .error("Type something first.")
            return
        }

        inflight?.cancel()
        partialResult = ""
        status = .running(action)
        lastRequest = (action, persona, trimmed, customInstruction)

        inflight = Task { [weak self] in
            do {
                let systemPrompt = action.systemPrompt(
                    persona: persona,
                    customInstruction: customInstruction
                )
                let result = try await GroqClient.stream(
                    systemPrompt: systemPrompt,
                    userMessage: trimmed,
                    temperature: action == .fixGrammar ? 0.3 : 0.7,
                    onToken: { [weak self] token in
                        // Hop back to the main actor — URLSession bytes
                        // delivers chunks on a background queue.
                        Task { @MainActor [weak self] in
                            self?.partialResult.append(token)
                        }
                    }
                )
                try Task.checkCancellation()
                guard let self else { return }
                self.lastResult = result
                self.partialResult = ""
                self.pushHistory(action: action, persona: persona, source: trimmed, result: result)
                self.status = .idle
                onCompletion(.success(result))
            } catch is CancellationError {
                // Newer action took over; leave status alone.
                return
            } catch {
                guard let self else { return }
                self.partialResult = ""
                self.status = .error(error.localizedDescription)
                onCompletion(.failure(error))
            }
        }
    }

    /// Replay the most recent request — used by the result card's
    /// "Regenerate" button. Temperature jitter not applied; the model's
    /// own sampling provides variation.
    func regenerate(onCompletion: @escaping (Result<String, Error>) -> Void) {
        guard let r = lastRequest else { return }
        run(
            r.action,
            on: r.source,
            persona: r.persona,
            customInstruction: r.customInstruction,
            onCompletion: onCompletion
        )
    }

    func clearError() {
        if case .error = status {
            status = .idle
        }
    }

    /// Clears the last result and any error, returning the service to idle.
    /// Called by the UI after the user has applied or dismissed a rewrite.
    func reset() {
        inflight?.cancel()
        inflight = nil
        lastResult = nil
        partialResult = ""
        status = .idle
    }

    private func pushHistory(action: AIAction, persona: AIPersona, source: String, result: String) {
        history.insert(
            HistoryEntry(action: action, persona: persona, sourceText: source, result: result),
            at: 0
        )
        if history.count > Self.historyLimit {
            history.removeLast(history.count - Self.historyLimit)
        }
    }
}
