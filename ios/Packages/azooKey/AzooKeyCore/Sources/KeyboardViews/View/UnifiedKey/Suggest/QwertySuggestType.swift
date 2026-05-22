public enum QwertySuggestType: Equatable, Hashable, Sendable {
    /// normal suggest is shown
    case normal
    /// variation suggest with selection
    case variation(selection: Int?)
}
