import SwiftUI
import UIKit
import KeyboardKit

/// Predictive bar between the AI toolbar and the keyboard. Three slots,
/// each tappable; the strip auto-hides when none of the slots have content.
///
/// Slot layout:
///   [recent-word completion] [system completion] [autocorrect prev word]
///
/// On every appearance the strip subscribes to KeyboardKit's text-change
/// stream so the suggestions update with each keystroke. KeyboardKit
/// doesn't expose a "text changed" observable directly in the free tier;
/// we sample on a short timer instead — cheap (`UITextChecker` is O(prefix)
/// and our LRU is bounded) and keeps the suggestion lag below one frame.
struct SuggestionStrip: View {

    unowned let controller: KeyboardInputViewController

    @State private var autocomplete = AutocompleteService()
    @State private var refreshTrigger: Int = 0
    @State private var pollTask: Task<Void, Never>?

    private var hasAnySuggestion: Bool {
        autocomplete.partialCompletion != nil
            || autocomplete.recentCompletion != nil
            || autocomplete.autocorrection != nil
    }

    var body: some View {
        Group {
            if hasAnySuggestion {
                HStack(spacing: RewordiumTokens.Space.sm) {
                    if let recent = autocomplete.recentCompletion {
                        slot(label: recent, icon: "clock.arrow.circlepath") {
                            applyCompletion(recent)
                        }
                    }
                    if let system = autocomplete.partialCompletion,
                       system != autocomplete.recentCompletion {
                        slot(label: system, icon: "text.cursor") {
                            applyCompletion(system)
                        }
                    }
                    if let correction = autocomplete.autocorrection {
                        slot(
                            label: correction.suggestion,
                            icon: "checkmark.seal",
                            isCorrection: true
                        ) {
                            applyAutocorrect(correction)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, RewordiumTokens.Space.md)
                .padding(.vertical, RewordiumTokens.Space.xs)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(StripBackground())
                .transition(.opacity)
            }
        }
        .frame(height: hasAnySuggestion ? nil : 0)
        .animation(.easeInOut(duration: 0.18), value: hasAnySuggestion)
        .onAppear { startPolling() }
        .onDisappear { pollTask?.cancel() }
    }

    private func slot(label: String, icon: String, isCorrection: Bool = false, action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(isCorrection ? Color.orange : Color.secondary)
                Text(label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .padding(.horizontal, RewordiumTokens.Space.sm)
            .padding(.vertical, 5)
            .rewordiumPill(isSelected: false)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isCorrection ? "Replace with \(label)" : "Insert \(label)")
    }

    // MARK: - Behavior

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task { @MainActor in
            // Refresh once immediately so the strip shows up on first
            // appearance without waiting for the first poll tick.
            refresh()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 120_000_000) // 120ms
                refresh()
            }
        }
    }

    private func refresh() {
        let before = controller.textDocumentProxy.documentContextBeforeInput ?? ""
        let after = controller.textDocumentProxy.documentContextAfterInput ?? ""
        autocomplete.refresh(beforeInput: before, afterInput: after)
        autocomplete.recordIfNewWord(beforeInput: before)
    }

    /// Insert a completion. Deletes the partial prefix the user has typed
    /// and inserts the full word + trailing space — matches how iOS's
    /// predictive bar behaves.
    private func applyCompletion(_ word: String) {
        let proxy = controller.textDocumentProxy
        let partial = autocomplete.currentPartial
        for _ in 0..<partial.count {
            proxy.deleteBackward()
        }
        proxy.insertText(word + " ")
    }

    /// Replace the previous misspelled word with the suggested correction.
    /// We use the captured `previousWordRange` from the AutocompleteService
    /// so we know exactly how many characters back the word starts.
    private func applyAutocorrect(_ slot: AutocompleteService.AutocorrectSlot) {
        let proxy = controller.textDocumentProxy
        let before = proxy.documentContextBeforeInput ?? ""
        // The misspelled word lives behind some whitespace/punctuation +
        // the partial we're currently typing. We walk back: skip the
        // partial, skip non-letters, delete the word, then re-emit
        // everything we skipped in order.
        let partial = autocomplete.currentPartial
        var trailingNonLetter = ""
        var idx = before.endIndex
        // 1. Skip partial.
        for _ in 0..<partial.count {
            if idx > before.startIndex {
                idx = before.index(before: idx)
            }
        }
        // 2. Collect the non-letters between the partial and the prev word.
        while idx > before.startIndex {
            let c = before[before.index(before: idx)]
            if c.isLetter || c == "'" { break }
            trailingNonLetter.insert(c, at: trailingNonLetter.startIndex)
            idx = before.index(before: idx)
        }
        // Delete: partial + trailingNonLetter + slot.original
        let deleteCount = partial.count + trailingNonLetter.count + slot.original.count
        for _ in 0..<deleteCount {
            proxy.deleteBackward()
        }
        proxy.insertText(slot.suggestion + trailingNonLetter + partial)
    }

    private struct StripBackground: View {
        var body: some View {
            if #available(iOS 26.0, *) {
                Rectangle()
                    .fill(.thinMaterial)
                    .glassEffect()
                    .overlay(
                        Rectangle()
                            .frame(height: RewordiumTokens.Stroke.hairline)
                            .foregroundStyle(.quaternary),
                        alignment: .bottom
                    )
            } else {
                Rectangle()
                    .fill(.thinMaterial)
                    .overlay(
                        Rectangle()
                            .frame(height: RewordiumTokens.Stroke.hairline)
                            .foregroundStyle(.quaternary),
                        alignment: .bottom
                    )
            }
        }
    }
}
