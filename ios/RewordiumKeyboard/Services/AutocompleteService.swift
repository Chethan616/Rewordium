import Foundation
import UIKit

/// Lightweight predictive-text source for the suggestion strip.
///
/// Three signals, in priority order:
///   1. `UITextChecker.completions(...)` for the current partial word.
///   2. `RecentWordsStore.completion(forPrefix:)` — biases toward words
///      the user has used recently.
///   3. `UITextChecker.guesses(...)` for the previous word if it's
///      misspelled — surfaces a tap-to-autocorrect chip.
///
/// All work happens off the main thread; results are delivered through
/// `@MainActor` callbacks because SwiftUI consumes them.
///
/// We use the *iOS-built-in* checker only — no bundled dictionary, no
/// network. This is the cheapest path to a useful strip and fits well
/// within the 60 MB keyboard memory budget.
@MainActor
@Observable
final class AutocompleteService {

    /// Suggested completion for the current partial word, if any.
    private(set) var partialCompletion: String?
    /// "Have used before" completion from `RecentWordsStore`.
    private(set) var recentCompletion: String?
    /// Best autocorrect for the previous (mis-spelled) word, if any.
    private(set) var autocorrection: AutocorrectSlot?

    /// Captured during update so the apply path knows what to delete.
    private(set) var currentPartial: String = ""
    private(set) var previousWordRange: NSRange?

    private let checker = UITextChecker()
    private let language = "en_US"

    struct AutocorrectSlot: Equatable {
        let original: String
        let suggestion: String
    }

    /// Refresh suggestions based on the proxy's current context.
    /// Called by `SuggestionStrip` on every keystroke (cheap — UITextChecker
    /// is O(prefix length) and our recent-words LRU is bounded at 200).
    func refresh(beforeInput: String, afterInput: String) {
        let trailing = beforeInput.suffix(120) // bounded scan
        let context = String(trailing)
        let (partial, prevWord, prevRange) = parseContext(context)

        currentPartial = partial
        previousWordRange = prevRange

        // Slot 1: system completion for partial word.
        if partial.count >= 1 {
            let nsContext = context as NSString
            let partialRange = NSRange(
                location: nsContext.length - partial.utf16.count,
                length: partial.utf16.count
            )
            let completions = checker.completions(
                forPartialWordRange: partialRange,
                in: context,
                language: language
            ) ?? []
            partialCompletion = completions.first
        } else {
            partialCompletion = nil
        }

        // Slot 2: recent words store.
        recentCompletion = partial.count >= 1
            ? RecentWordsStore.shared.completion(forPrefix: partial)
            : nil

        // Slot 3: autocorrect previous word if misspelled.
        if !prevWord.isEmpty {
            let nsContext = context as NSString
            if let prevR = prevRange {
                let mis = checker.rangeOfMisspelledWord(
                    in: context,
                    range: NSRange(location: 0, length: nsContext.length),
                    startingAt: prevR.location,
                    wrap: false,
                    language: language
                )
                if mis.location == prevR.location, mis.length == prevR.length {
                    if let guess = checker.guesses(forWordRange: mis, in: context, language: language)?.first {
                        autocorrection = AutocorrectSlot(original: prevWord, suggestion: guess)
                    } else {
                        autocorrection = nil
                    }
                } else {
                    autocorrection = nil
                }
            } else {
                autocorrection = nil
            }
        } else {
            autocorrection = nil
        }
    }

    /// Bookkeeping for `RecentWordsStore` when a word is committed
    /// (typically when the user types space or punctuation).
    func recordIfNewWord(beforeInput: String) {
        let trailing = String(beforeInput.suffix(120))
        // If the last char is a word-terminator, the word that came
        // before it just got committed; learn it.
        guard let last = trailing.last, !last.isLetter else {
            return
        }
        let stripped = trailing.dropLast()
        let lastWord = stripped.reversed()
            .prefix(while: { $0.isLetter || $0 == "'" })
            .reversed()
        if lastWord.count >= 2 {
            RecentWordsStore.shared.record(String(lastWord))
        }
    }

    /// Split the trailing context into (partial, previousWord, previousWordRange).
    /// `partial` is the unfinished word currently being typed (empty if the
    /// cursor sits right after a space). `previousWord` is the closed-off
    /// word immediately before the partial.
    private func parseContext(_ context: String) -> (String, String, NSRange?) {
        let scalars = Array(context.unicodeScalars)
        // 1. Walk backward from the end while we're still in letters.
        var partialEnd = scalars.endIndex
        var partialStart = partialEnd
        while partialStart > scalars.startIndex {
            let prev = scalars[partialStart - 1]
            if CharacterSet.letters.contains(prev) || prev == "'" {
                partialStart -= 1
            } else {
                break
            }
        }
        let partial = String(String.UnicodeScalarView(scalars[partialStart..<partialEnd]))

        // 2. Skip non-letters to find the previous word's tail.
        var i = partialStart
        while i > scalars.startIndex {
            let prev = scalars[i - 1]
            if CharacterSet.letters.contains(prev) || prev == "'" {
                break
            }
            i -= 1
        }
        let prevWordEnd = i

        var prevWordStart = prevWordEnd
        while prevWordStart > scalars.startIndex {
            let prev = scalars[prevWordStart - 1]
            if CharacterSet.letters.contains(prev) || prev == "'" {
                prevWordStart -= 1
            } else {
                break
            }
        }
        let previousWord = String(String.UnicodeScalarView(scalars[prevWordStart..<prevWordEnd]))

        // Build NSRange in UTF-16 units (UITextChecker speaks NSString).
        let prefix = String(String.UnicodeScalarView(scalars[scalars.startIndex..<prevWordStart]))
        let prevWordU16 = previousWord.utf16.count
        let prefixU16 = prefix.utf16.count
        let prevRange: NSRange? = previousWord.isEmpty
            ? nil
            : NSRange(location: prefixU16, length: prevWordU16)

        return (partial, previousWord, prevRange)
    }
}
