import Foundation
import Observation

/// Stateful façade the keyboard UI talks to. One instance is held by the
/// KeyboardViewController and observed by the SwiftUI hierarchy.
///
/// Single-flight: a new action cancels any in-progress one (typical for a
/// keyboard — users tap rapidly and only the latest result matters).
@MainActor
@Observable
final class AIService {

    enum Status: Equatable {
        case idle
        case running(AIAction)
        case error(String)
    }

    private(set) var status: Status = .idle
    private(set) var lastResult: String?

    private var inflight: Task<Void, Never>?

    func run(
        _ action: AIAction,
        on text: String,
        customInstruction: String? = nil,
        onCompletion: @escaping (Result<String, Error>) -> Void
    ) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            status = .error("Type something first.")
            return
        }

        inflight?.cancel()
        status = .running(action)

        inflight = Task { [weak self] in
            do {
                let systemPrompt = action.systemPrompt(customInstruction: customInstruction)
                let result = try await GroqClient.chat(
                    systemPrompt: systemPrompt,
                    userMessage: trimmed,
                    temperature: action == .fixGrammar ? 0.3 : 0.7
                )
                try Task.checkCancellation()
                guard let self else { return }
                self.lastResult = result
                self.status = .idle
                onCompletion(.success(result))
            } catch is CancellationError {
                // Newer action took over; leave status alone.
                return
            } catch {
                guard let self else { return }
                self.status = .error(error.localizedDescription)
                onCompletion(.failure(error))
            }
        }
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
        status = .idle
    }
}
