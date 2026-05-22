import SwiftUI

public struct HelpAlertButton: View {
    public init(title: LocalizedStringKey, explanation: LocalizedStringKey) {
        self.title = title
        self.explanation = explanation
    }
    private var title: LocalizedStringKey
    private var explanation: LocalizedStringKey

    @State private var showAlert = false
    public var body: some View {
        Button {
            showAlert = true
        } label: {
            Image(systemName: "questionmark.circle")
        }
        .alert(title, isPresented: $showAlert) {
            Button("OK") {
                self.showAlert = false
            }
        } message: {
            Text(explanation)
        }
    }
}
