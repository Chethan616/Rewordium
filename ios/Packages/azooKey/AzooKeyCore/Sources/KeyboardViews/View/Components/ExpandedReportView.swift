import SwiftUI

@MainActor
struct ExpandedReportView<Extension: ApplicationSpecificKeyboardViewExtension>: View {
    @Environment(Extension.Theme.self) private var theme
    @EnvironmentObject private var variableStates: VariableStates

    let state: ReportDetailState

    private var currentState: ReportDetailState {
        variableStates.reportDetailState ?? state
    }

    private var content: ReportContent { currentState.content }

    var body: some View {
        VStack(spacing: 0) {
            Text("送信内容")
                .font(.title2.bold())
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            Form {
                Section("送信したくない情報は右スワイプで削除できます。") {
                    ForEach(Array(currentState.entries.enumerated()), id: \.offset) { pair in
                        entryRow(at: pair.offset, entry: pair.element)
                    }
                }
            }
            .environment(\.defaultMinListRowHeight, 24)
            .scrollContentBackground(.hidden)
        }
        .background(theme.backgroundColor.color)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func entryRow(at index: Int, entry: ReportDetailEntry) -> some View {
        LabeledContent {
            Text(entry.value)
                .foregroundStyle(entry.isExcluded ? .secondary : .primary)
                .strikethrough(entry.isExcluded, color: .secondary)
        } label: {
            Text(entry.isOptional ? entry.field : "\(entry.field)*")
                .foregroundStyle(entry.isExcluded ? .secondary : .primary)
                .strikethrough(entry.isExcluded, color: .secondary)
        }
        .textSelection(.enabled)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if entry.isOptional {
                Button {
                    toggleEntry(at: index)
                } label: {
                    Label(entry.isExcluded ? "報告に含める" : "送信しない", systemImage: entry.isExcluded ? "plus" : "trash")
                }
                .tint(entry.isExcluded ? .accentColor : .red)
            }
        }
    }

    private func toggleEntry(at index: Int) {
        guard let detailState = variableStates.reportDetailState else { return }
        guard detailState.entries.indices.contains(index) else { return }
        var entries = detailState.entries
        guard entries[index].isOptional else { return }
        entries[index].isExcluded.toggle()
        variableStates.reportDetailState = ReportDetailState(content: detailState.content, entries: entries)
    }
}
