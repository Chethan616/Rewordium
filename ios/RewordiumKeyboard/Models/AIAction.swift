import Foundation
import SwiftUI

/// One-shot rewrite operations the user can tap from the AI panel.
///
/// Each case carries its display chrome and the system prompt sent to the
/// model. Keep prompts terse — qwen3-32b produces better JSON when the
/// instructions are tight.
enum AIAction: String, CaseIterable, Identifiable, Hashable {
    case rewrite
    case shorten
    case expand
    case professional
    case friendly
    case fixGrammar
    case translate
    case custom

    var id: String { rawValue }

    var title: String {
        switch self {
        case .rewrite:      return "Rewrite"
        case .shorten:      return "Shorten"
        case .expand:       return "Expand"
        case .professional: return "Professional"
        case .friendly:     return "Friendly"
        case .fixGrammar:   return "Fix grammar"
        case .translate:    return "Translate"
        case .custom:       return "Custom"
        }
    }

    var systemImage: String {
        switch self {
        case .rewrite:      return "arrow.triangle.2.circlepath"
        case .shorten:      return "arrow.down.right.and.arrow.up.left"
        case .expand:       return "arrow.up.left.and.arrow.down.right"
        case .professional: return "briefcase"
        case .friendly:     return "face.smiling"
        case .fixGrammar:   return "checkmark.seal"
        case .translate:    return "globe"
        case .custom:       return "wand.and.stars"
        }
    }

    /// What the user sees in the inline status while the request runs.
    var loadingLabel: String {
        switch self {
        case .rewrite:      return "Rewriting…"
        case .shorten:      return "Shortening…"
        case .expand:       return "Expanding…"
        case .professional: return "Making it professional…"
        case .friendly:     return "Warming up the tone…"
        case .fixGrammar:   return "Fixing grammar…"
        case .translate:    return "Translating…"
        case .custom:       return "Thinking…"
        }
    }

    /// System prompt. The user message is always the text being acted on.
    /// We ask for raw text (no JSON envelope) for the extension flow — it
    /// keeps responses small enough to fit the 60 MB keyboard memory budget
    /// even with long inputs, and parsing JSON inside a memory-constrained
    /// process is wasted work.
    func systemPrompt(customInstruction: String? = nil) -> String {
        let base: String
        switch self {
        case .rewrite:
            base = "Rewrite the user's text in clearer, more natural language. Preserve meaning. Do not add commentary."
        case .shorten:
            base = "Rewrite the user's text more concisely. Cut filler. Preserve meaning. Do not add commentary."
        case .expand:
            base = "Expand the user's text with more detail while preserving meaning. Do not add commentary."
        case .professional:
            base = "Rewrite the user's text in a professional, polished tone. Keep it natural — not stiff. Do not add commentary."
        case .friendly:
            base = "Rewrite the user's text in a warm, conversational tone. Preserve meaning. Do not add commentary."
        case .fixGrammar:
            base = "Correct grammar, spelling, and punctuation in the user's text. Preserve the writer's voice. Return only the corrected text."
        case .translate:
            base = "Translate the user's text to English if not already English; otherwise translate to the user's likely target language inferred from context. Return only the translation."
        case .custom:
            let instruction = (customInstruction ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            base = instruction.isEmpty
                ? "Rewrite the user's text following good editorial judgment."
                : "Rewrite the user's text following these instructions: \(instruction). Preserve meaning unless instructed otherwise."
        }
        // Belt-and-suspenders for qwen3's thinking-mode tendency.
        return base + " Reply with only the rewritten text. /no_think"
    }
}
