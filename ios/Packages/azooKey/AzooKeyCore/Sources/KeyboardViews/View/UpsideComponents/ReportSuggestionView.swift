import SwiftUI

@MainActor
public struct ReportSuggestionView<Extension: ApplicationSpecificKeyboardViewExtension>: View {
    public init(content: ReportContent) {
        self.content = content
    }

    @Environment(Extension.Theme.self) private var theme
    @Environment(\.userActionManager) private var action
    @EnvironmentObject private var variableStates: VariableStates

    private let content: ReportContent

    @State private var viewState: ViewState = .ready

    private enum ViewState {
        case ready
        case sending
        case succeeded
        case failed
    }

    public var body: some View {
        HStack {
            Button(role: .cancel, action: close) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 22, weight: .semibold))
            }
            .foregroundStyle(.primary)
            controls
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, alignment: .center)
        .background(theme.backgroundColor.color.opacity(0.95))
    }

    @ViewBuilder
    private var controls: some View {
        switch viewState {
        case .ready:
            HStack {
                switch content {
                case let .candidateRankingMismatch(_, selected):
                    HStack(spacing: 0) {
                        if selected.displayText.count > 3 {
                            Text("「\(selected.displayText.prefix(3))...」")
                        } else {
                            Text("「\(selected.displayText)」")
                        }
                        Text("が良いと報告しますか？")
                    }
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Spacer()
                }
                Button("報告", action: submit)
                    .buttonStyle(.borderedProminent)
                Button(action: showDetail) {
                    Image(systemName: "ellipsis.circle.fill")
                        .font(.system(size: 22, weight: .semibold))
                }
                .foregroundStyle(.primary)
            }
            .fixedSize(horizontal: false, vertical: true)
        case .failed:
            HStack {
                Spacer()
                Image(systemName: "exclamationmark.circle.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.orange)
                Text("報告に失敗しました")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.orange)
                Button("再試行", systemImage: "arrow.clockwise", action: submit)
                    .buttonStyle(.borderedProminent)
                    .labelStyle(.iconOnly)
            }
        case .sending:
            HStack {
                Spacer()
                ProgressView()
                    .progressViewStyle(.circular)
                    .padding(.horizontal, 12)
            }
        case .succeeded:
            HStack {
                Spacer()
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.green)
                Text("報告しました")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.green)
            }
        }
    }

    private func submit() {
        guard viewState == .ready || viewState == .failed else { return }
        KeyboardFeedback<Extension>.click()
        viewState = .sending
        Task { @MainActor in
            let success = await action.reportSuggestion(content, variableStates: variableStates)
            if success {
                viewState = .succeeded
                scheduleCloseAfterSuccess()
            } else {
                viewState = .failed
            }
        }
    }

    private func showDetail() {
        KeyboardFeedback<Extension>.click()
        action.presentReportDetail(content, variableStates: variableStates)
    }

    private func scheduleCloseAfterSuccess() {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            close()
        }
    }

    private func close() {
        KeyboardFeedback<Extension>.click()
        action.registerAction(.setUpsideComponent(nil), variableStates: variableStates)
        action.dismissReportDetail(variableStates: variableStates)
    }
}
