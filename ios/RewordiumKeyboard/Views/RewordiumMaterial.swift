import SwiftUI

/// Visual tokens and material helpers for the keyboard extension.
///
/// One source of truth for spacing, radius, and surface treatment so the
/// keyboard reads as one product instead of a collage. No gradients, no
/// neon. Translucency is used sparingly — only on the toolbar bar itself.
enum RewordiumTokens {
    /// 8-point grid. Use these constants instead of literal numbers in views.
    enum Space {
        static let xxs: CGFloat = 4
        static let xs:  CGFloat = 6
        static let sm:  CGFloat = 8
        static let md:  CGFloat = 12
        static let lg:  CGFloat = 16
        static let xl:  CGFloat = 20
    }

    enum Radius {
        static let chip: CGFloat = 12
        static let pill: CGFloat = 18
        static let card: CGFloat = 16
    }

    enum Stroke {
        static let hairline: CGFloat = 0.5
    }

    enum AnimationCurve {
        /// Apple-style spring for surface morphs. Matches the feel of the
        /// system keyboard's predictive bar transitions.
        static let surface: Animation = .spring(response: 0.32, dampingFraction: 0.86)
        /// Slightly snappier for chip taps.
        static let tap: Animation = .spring(response: 0.22, dampingFraction: 0.78)
    }
}

/// Background material for the toolbar surface.
///
/// We use `.ultraThinMaterial` (iOS 15+) which matches the system predictive
/// bar's translucency and reads cleanly over both light and dark keyboards.
///
/// iOS 26 introduces a Liquid Glass design language with `.glassProminent`
/// button styles and a `.glassEffect()` modifier — we adopt the former on
/// the AI pill itself (`AIPill+iOS26.swift`-style conditional in AIToolbar),
/// but keep the surface material at `.ultraThinMaterial` because that's the
/// system's choice for keyboard predictive bars on iOS 26 too.
struct RewordiumSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(.ultraThinMaterial)
            .overlay(
                Rectangle()
                    .frame(height: RewordiumTokens.Stroke.hairline)
                    .foregroundStyle(.quaternary),
                alignment: .bottom
            )
    }
}

/// Chip background — flat, hairline border, faint primary tint when pressed.
struct RewordiumChipSurface: ViewModifier {
    var isPressed: Bool = false
    var isHighlighted: Bool = false

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                    .fill(.background.opacity(isHighlighted ? 0.0 : 0.6))
            )
            .background(
                RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                    .fill(Color.accentColor.opacity(isHighlighted ? 0.10 : 0.0))
            )
            .overlay(
                RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                    .strokeBorder(
                        isHighlighted ? Color.accentColor.opacity(0.7) : Color.primary.opacity(0.12),
                        lineWidth: isHighlighted ? 1.2 : RewordiumTokens.Stroke.hairline
                    )
            )
            .scaleEffect(isPressed ? 0.96 : 1.0)
            .animation(RewordiumTokens.AnimationCurve.tap, value: isPressed)
    }
}

extension View {
    func rewordiumSurface() -> some View { modifier(RewordiumSurface()) }
    func rewordiumChip(isPressed: Bool = false, isHighlighted: Bool = false) -> some View {
        modifier(RewordiumChipSurface(isPressed: isPressed, isHighlighted: isHighlighted))
    }

    /// Prominent CTA button style. On iOS 26 we adopt the Liquid Glass
    /// `glassProminent` style (matches the upstream KeyboardKit demo). Older
    /// targets keep the capsule + accent-color background already encoded in
    /// the calling view — so this modifier is intentionally a no-op pre-26.
    @ViewBuilder
    func rewordiumProminentStyle() -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glassProminent)
        } else {
            self
        }
    }
}
