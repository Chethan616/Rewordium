import SwiftUI

/// Always-visible diagnostic banner pinned to the top of the keyboard.
///
/// Purpose: give the user an unmistakable visual signal that the Rewordium
/// extension binary is actually loading. If the user sees this banner, the
/// keyboard binary is running. If they don't, the system is either showing
/// the stock iOS keyboard, an older installed build, or our extension is
/// being terminated before SwiftUI can render.
///
/// Reads CFBundleShortVersionString + CFBundleVersion from the extension's
/// Info.plist (the values setup_keyboard_target.rb injects at build time),
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
            // Tinted leading bar — instantly recognizable, never matches any
            // stock keyboard chrome.
            Rectangle()
                .fill(Color.accentColor)
                .frame(width: 3, height: 14)
                .clipShape(Capsule())

            Text("Rewordium")
                .font(.system(size: 11, weight: .semibold, design: .rounded))
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
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity)
        .background(
            ZStack {
                Color.accentColor.opacity(0.12)
                Rectangle()
                    .frame(height: 0.5)
                    .foregroundStyle(.quaternary)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        )
    }
}
