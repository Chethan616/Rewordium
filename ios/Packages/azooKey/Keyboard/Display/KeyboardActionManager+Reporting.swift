import AzooKeyUtils
import Foundation
import KanaKanjiConverterModule
import KeyboardViews

extension KeyboardActionManager {
    @MainActor
    func handleReportWrongConversion(
        _ candidate: any ResultViewItemData,
        index: Int?,
        variableStates: VariableStates
    ) async {
        await ReportSubmissionHelper.submitWrongConversion(
            candidate: candidate,
            index: index,
            variableStates: variableStates,
            inputManager: inputManager
        )
    }

    @MainActor
    func handlePrepareReportSuggestion(
        candidate: any ResultViewItemData,
        index: Int,
        variableStates: VariableStates
    ) {
        @KeyboardSetting(.enableWrongConversionReport) var reportEnabled
        guard reportEnabled else { return }
        guard SemiStaticStates.shared.hasFullAccess else { return }
        guard variableStates.isShowingPrimaryResults else { return }
        guard index != 0 else { return }
        guard let topCandidateData = variableStates.primaryResultCandidate(at: 0) else { return }
        guard let topCandidate = topCandidateData as? Candidate else { return }
        guard let selectedCandidate = candidate as? Candidate else { return }
        @KeyboardSetting(.wrongConversionReportFrequency) var frequency
        let denominator = frequency.probabilityDenominator
        if denominator > 1 && Int.random(in: 1...denominator) != 1 {
            return
        }

        let composingText = inputManager.getComposingText()
        let composingInputs = composingText.input
        let contextSnapshot = variableStates.surroundingText
        let leftContext = String(contextSnapshot.leftSideText.suffix(10))
        let rightContext = String(contextSnapshot.rightSideText.prefix(10))
        let evaluationText = ReportSuggestionInputFilter.evaluationText(for: composingText)
        // Skip report suggestions when the composing input mixes unsupported character categories.
        guard ReportSuggestionInputFilter.isEligible(evaluationText) else {
            return
        }
        // Skip predictive candidate
        if selectedCandidate.rubyCount > composingText.convertTarget.count {
            return
        }
        let topSummary = CandidateSummary(
            displayText: topCandidate.text,
            rank: 0,
            rawCandidate: topCandidateData,
            composingInputs: composingInputs,
            candidateID: nil,
            composingCount: topCandidate.composingCount,
            leftContext: leftContext,
            rightContext: rightContext,
            evaluationText: evaluationText
        )
        let selectedSummary = CandidateSummary(
            displayText: selectedCandidate.text,
            rank: index,
            rawCandidate: candidate,
            composingInputs: composingInputs,
            candidateID: nil,
            composingCount: selectedCandidate.composingCount,
            leftContext: leftContext,
            rightContext: rightContext,
            evaluationText: evaluationText
        )
        if topSummary.displayText.hasPrefix(selectedSummary.displayText)
            && topSummary.displayText.count > selectedSummary.displayText.count {
            // Selecting a prefix of the top candidate is a sign of intentional trimming; skip suggestion.
            return
        }
        guard let reportSuggestionState = variableStates.reportSuggestionState else {
            return
        }
        guard !reportSuggestionState.hasReportedPair(
            evaluationText: evaluationText,
            selectedDisplayText: selectedSummary.displayText
        ) else {
            return
        }
        guard reportSuggestionState.shouldPresent(
            topDisplayText: topSummary.displayText,
            selectedDisplayText: selectedSummary.displayText,
            textChangedCount: variableStates.textChangedCount,
            evaluationText: evaluationText
        ) else {
            return
        }
        variableStates.reportSuggestionState!.registerPresentation(
            topDisplayText: topSummary.displayText,
            selectedDisplayText: selectedSummary.displayText,
            textChangedCount: variableStates.textChangedCount,
            evaluationText: evaluationText
        )
        self.applyUpsideComponent(
            .reportSuggestion(.candidateRankingMismatch(top: topSummary, selected: selectedSummary)),
            variableStates: variableStates
        )
    }

    @MainActor
    func handleReportSuggestion(
        _ content: ReportContent,
        variableStates: VariableStates
    ) async -> Bool {
        @KeyboardSetting(.enableWrongConversionReport) var reportEnabled
        guard reportEnabled else {
            return false
        }
        let success = await ReportSubmissionHelper.submitSuggestion(
            content: content,
            variableStates: variableStates,
            inputManager: inputManager
        )
        if success {
            if case let .candidateRankingMismatch(_, selected) = content,
               let evaluationText = selected.evaluationText {
                variableStates.reportSuggestionState?.registerReportedPair(
                    evaluationText: evaluationText,
                    selectedDisplayText: selected.displayText
                )
            } else {
                variableStates.reportSuggestionState?.registerPendingPairAsReported()
            }
        }
        return success
    }

    @MainActor
    func handlePresentReportDetail(
        _ content: ReportContent,
        variableStates: VariableStates
    ) {
        let entries = ReportSubmissionHelper.detailEntries(
            for: content,
            variableStates: variableStates,
            inputManager: inputManager
        )
        variableStates.reportDetailState = ReportDetailState(content: content, entries: entries)
    }

    @MainActor
    func handleDismissReportDetail(variableStates: VariableStates) {
        variableStates.reportDetailState = nil
    }
}

private enum ReportSuggestionInputFilter {
    private static let hiraganaCharacters: String = {
        let scalars = (0x3040...0x309F).compactMap(UnicodeScalar.init)
        return String(scalars.map(Character.init))
    }()
    private static let allowedCharacterSet: CharacterSet = {
        var set = CharacterSet()
        set.formUnion(CharacterSet(charactersIn: hiraganaCharacters))
        set.formUnion(CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        set.formUnion(CharacterSet(charactersIn: "0123456789"))
        return set
    }()

    static func evaluationText(for composingText: ComposingText) -> String {
        composingText.convertTarget
    }

    static func isEligible(_ text: String) -> Bool {
        guard !text.isEmpty else { return false }
        return text.unicodeScalars.allSatisfy { allowedCharacterSet.contains($0) }
    }
}
