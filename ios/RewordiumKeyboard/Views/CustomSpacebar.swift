import SwiftUI
import KeyboardKit

/// Spacebar override that draws a small build-tag over the framework's
/// default space key.
///
/// The tag is a temporary smoke-test affordance so the developer can verify
/// on-device that the right binary loaded. The keyboard's actual space
/// behavior (long-press cursor move, predictive locale label, etc.) is
/// untouched — we layer purely visual content over `defaultView`.
struct CustomSpacebar: View {

    let defaultView: AnyView

    var body: some View {
        defaultView
            .overlay(alignment: .center) {
                Text("rew testing")
                    .font(.system(size: 11, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .allowsHitTesting(false)
            }
    }
}
