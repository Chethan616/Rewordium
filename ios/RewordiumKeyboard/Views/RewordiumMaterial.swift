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
/// iOS 15-25: `.ultraThinMaterial` matches the system predictive bar.
/// iOS 26+:   `.glassEffect()` applies Liquid Glass — a subtle refraction
///            + edge-light treatment that the system itself uses on
///            toolbars and sheets. We pick the more diffuse `.regular`
///            variant so the bar reads as a calm surface above the
///            keyboard, not a flashy element competing for attention.
struct RewordiumSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(GlassyBackground())
            .overlay(
                Rectangle()
                    .frame(height: RewordiumTokens.Stroke.hairline)
                    .foregroundStyle(.quaternary),
                alignment: .bottom
            )
    }

    private struct GlassyBackground: View {
        var body: some View {
            if #available(iOS 26.0, *) {
                Rectangle()
                    .fill(.regularMaterial)
                    .glassEffect()
            } else {
                Rectangle()
                    .fill(.ultraThinMaterial)
            }
        }
    }
}

/// Chip background — flat, hairline border, faint primary tint when pressed.
///
/// On iOS 26 we adopt `.glassEffect(.regular, in: …)` so the chip refracts
/// the toolbar surface beneath it. The `GlassEffectContainer` wrapping the
/// whole AIToolbar coalesces neighboring chips so they blend instead of
/// stacking glass-on-glass.
struct RewordiumChipSurface: ViewModifier {
    var isPressed: Bool = false
    var isHighlighted: Bool = false

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .background(
                    RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                        .fill(Color.accentColor.opacity(isHighlighted ? 0.18 : 0.0))
                )
                .glassEffect(
                    isHighlighted ? .regular.tint(.accentColor.opacity(0.15)) : .regular,
                    in: RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: RewordiumTokens.Radius.chip, style: .continuous)
                        .strokeBorder(
                            isHighlighted ? Color.accentColor.opacity(0.7) : Color.primary.opacity(0.08),
                            lineWidth: isHighlighted ? 1.2 : RewordiumTokens.Stroke.hairline
                        )
                )
                .scaleEffect(isPressed ? 0.96 : 1.0)
                .animation(RewordiumTokens.AnimationCurve.tap, value: isPressed)
        } else {
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
}

/// Pill surface for persona chips, suggestion-strip slots, and small CTAs.
/// Same Liquid Glass treatment as a chip but with a pill radius and a tint
/// when selected.
struct RewordiumPillSurface: ViewModifier {
    var isSelected: Bool = false

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(
                    isSelected ? .regular.tint(.accentColor.opacity(0.22)) : .regular,
                    in: Capsule()
                )
                .overlay(
                    Capsule().strokeBorder(
                        isSelected ? Color.accentColor.opacity(0.7) : Color.primary.opacity(0.10),
                        lineWidth: isSelected ? 1.0 : RewordiumTokens.Stroke.hairline
                    )
                )
        } else {
            content
                .background(
                    Capsule().fill(isSelected
                        ? Color.accentColor.opacity(0.18)
                        : Color.primary.opacity(0.06))
                )
                .overlay(
                    Capsule().strokeBorder(
                        isSelected ? Color.accentColor.opacity(0.6) : Color.primary.opacity(0.10),
                        lineWidth: isSelected ? 1.0 : RewordiumTokens.Stroke.hairline
                    )
                )
        }
    }
}

/// Card surface used by the AI result panel. Slightly taller corner radius
/// and a subtle accent tint to set it apart from the chip grid.
struct RewordiumCardSurface: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(
                    .regular.tint(.accentColor.opacity(0.08)),
                    in: RoundedRectangle(cornerRadius: RewordiumTokens.Radius.card, style: .continuous)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: RewordiumTokens.Radius.card, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.08), lineWidth: RewordiumTokens.Stroke.hairline)
                )
        } else {
            content
                .background(
                    RoundedRectangle(cornerRadius: RewordiumTokens.Radius.card, style: .continuous)
                        .fill(.ultraThinMaterial)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: RewordiumTokens.Radius.card, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.10), lineWidth: RewordiumTokens.Stroke.hairline)
                )
        }
    }
}

extension View {
    func rewordiumSurface() -> some View { modifier(RewordiumSurface()) }

    func rewordiumChip(isPressed: Bool = false, isHighlighted: Bool = false) -> some View {
        modifier(RewordiumChipSurface(isPressed: isPressed, isHighlighted: isHighlighted))
    }

    func rewordiumPill(isSelected: Bool = false) -> some View {
        modifier(RewordiumPillSurface(isSelected: isSelected))
    }

    func rewordiumCard() -> some View { modifier(RewordiumCardSurface()) }

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

    /// Wraps content in a `GlassEffectContainer` on iOS 26 so neighboring
    /// glass surfaces coalesce into one refraction layer instead of
    /// stacking. No-op on earlier OSes.
    @ViewBuilder
    func rewordiumGlassContainer() -> some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer { self }
        } else {
            self
        }
    }
}
