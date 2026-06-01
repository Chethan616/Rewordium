import SwiftUI

/// Secondary "SwiftUI is alive" indicator beneath the UIKit diagnostic
/// banner that `KeyboardViewController` installs in `viewDidLoad`.
///
/// Loading-state legend:
///
///   1. UIKit purple banner visible (from KeyboardViewController.viewDidLoad)
///      AND this SwiftUI banner visible (with version + "rew test")
///      → everything's wired. The keyboard is fully loaded.
///
///   2. UIKit purple banner visible, SwiftUI banner NOT visible
///      → KeyboardViewController ran but KeyboardKit / SwiftUI setup
///        failed. We never reached `setupKeyboardView`. Inspect Console.app
///        on the device, filter on "[RewordiumKeyboard]", and look for a
///        "setupKeyboardKit FAILED" line.
///
///   3. Neither banner visible
///      → the extension binary never instantiated KeyboardViewController.
///        The failure is at the iOS extension-loading layer: principal
///        class lookup, entitlement / signing mismatch, or memory ceiling
///        crash. The Codemagic build succeeded but something at install
///        time is wrong.
///
/// Reads CFBundleShortVersionString + CFBundleVersion from the extension's
/// Info.plist (values that setup_keyboard_target.rb injects at build time),
/// so the banner text follows whatever IPA the user actually has installed.
struct BuildBanner: View {

    private var marketingVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    private var buildNumber: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
    }

    var body: some View {
        HStack(spacing: 6) {
            Rectangle()
                .fill(Color.accentColor)
                .frame(width: 3, height: 12)
                .clipShape(Capsule())

            Text("SwiftUI ready")
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .foregroundStyle(.primary)

            Text("v\(marketingVersion) (\(buildNumber))")
                .font(.system(size: 10, weight: .medium, design: .monospaced))
                .foregroundStyle(.secondary)

            Spacer(minLength: 0)

            Text("rew test")
                .font(.system(size: 10, weight: .medium, design: .rounded))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 3)
        .frame(maxWidth: .infinity)
        .background(
            ZStack {
                Color.accentColor.opacity(0.10)
                Rectangle()
                    .frame(height: 0.5)
                    .foregroundStyle(.quaternary)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        )
    }
}
