import Foundation

public struct ReportSuggestionState: Equatable {
    struct Identifier: Equatable, Sendable {
        var topDisplayText: String
        var selectedDisplayText: String
        var textChangedCount: Int
    }

    private var lastIdentifier: Identifier?
    public private(set) var presentedAt: Date?
    private var pendingPairKey: String?
    private var reportedPairKeys: [String]
    public private(set) var reportedPairCount: Int
    private let storageUserDefaults: UserDefaults

    private static let reportedPairsUserDefaultsKey = "WrongConversionReportedPairHistory"
    private static let reportedPairsMaxCount = 2048
    private static let pairSeparator = "\u{1f}"

    public init(storageUserDefaults: UserDefaults) {
        self.lastIdentifier = nil
        self.presentedAt = nil
        self.pendingPairKey = nil
        self.storageUserDefaults = storageUserDefaults
        self.reportedPairKeys = Self.loadReportedPairs(from: storageUserDefaults)
        self.reportedPairCount = reportedPairKeys.count
    }

    public func shouldPresent(
        topDisplayText: String,
        selectedDisplayText: String,
        textChangedCount: Int,
        evaluationText: String?
    ) -> Bool {
        let identifier = Identifier(
            topDisplayText: topDisplayText,
            selectedDisplayText: selectedDisplayText,
            textChangedCount: textChangedCount
        )
        guard lastIdentifier != identifier else {
            return false
        }
        if let evaluationText,
           let key = Self.pairKey(evaluationText: evaluationText, selectedDisplayText: selectedDisplayText) {
            return !reportedPairKeys.contains(key)
        }
        return true
    }

    public mutating func registerPresentation(
        topDisplayText: String,
        selectedDisplayText: String,
        textChangedCount: Int,
        evaluationText: String?
    ) {
        lastIdentifier = Identifier(
            topDisplayText: topDisplayText,
            selectedDisplayText: selectedDisplayText,
            textChangedCount: textChangedCount
        )
        presentedAt = Date()
        if let evaluationText,
           let key = Self.pairKey(evaluationText: evaluationText, selectedDisplayText: selectedDisplayText) {
            pendingPairKey = key
        } else {
            pendingPairKey = nil
        }
    }

    public mutating func clearTimestamp() {
        presentedAt = nil
    }

    public func hasReportedPair(evaluationText: String, selectedDisplayText: String) -> Bool {
        guard let key = Self.pairKey(evaluationText: evaluationText, selectedDisplayText: selectedDisplayText) else {
            return false
        }
        return reportedPairKeys.contains(key)
    }

    public mutating func registerPendingPairAsReported() {
        guard let key = pendingPairKey else {
            return
        }
        pendingPairKey = nil
        appendReportedPairKey(key)
    }

    public mutating func registerReportedPair(evaluationText: String, selectedDisplayText: String) {
        guard let key = Self.pairKey(evaluationText: evaluationText, selectedDisplayText: selectedDisplayText) else {
            return
        }
        if pendingPairKey == key {
            pendingPairKey = nil
        }
        appendReportedPairKey(key)
    }

    public mutating func discardPendingPair() {
        pendingPairKey = nil
    }

    private mutating func appendReportedPairKey(_ key: String) {
        if let existingIndex = reportedPairKeys.firstIndex(of: key) {
            reportedPairKeys.remove(at: existingIndex)
        }
        reportedPairKeys.append(key)
        if reportedPairKeys.count > Self.reportedPairsMaxCount {
            let overflow = reportedPairKeys.count - Self.reportedPairsMaxCount
            reportedPairKeys.removeFirst(overflow)
        }
        reportedPairCount = reportedPairKeys.count
        Self.saveReportedPairs(reportedPairKeys, to: storageUserDefaults)
    }

    private static func loadReportedPairs(from storage: UserDefaults) -> [String] {
        storage.stringArray(forKey: reportedPairsUserDefaultsKey) ?? []
    }

    private static func saveReportedPairs(_ keys: [String], to storage: UserDefaults) {
        storage.set(keys, forKey: reportedPairsUserDefaultsKey)
    }

    private static func pairKey(evaluationText: String, selectedDisplayText: String) -> String? {
        guard !evaluationText.isEmpty else { return nil }
        return evaluationText + pairSeparator + selectedDisplayText
    }

    public static func == (lhs: ReportSuggestionState, rhs: ReportSuggestionState) -> Bool {
        lhs.lastIdentifier == rhs.lastIdentifier
            && lhs.presentedAt == rhs.presentedAt
            && lhs.pendingPairKey == rhs.pendingPairKey
            && lhs.reportedPairKeys == rhs.reportedPairKeys
            && lhs.reportedPairCount == rhs.reportedPairCount
    }
}
