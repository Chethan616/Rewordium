import SwiftUI
import KeyboardKit

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
        static let key: CGFloat = 7
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

enum RewordiumKeyboardTheme {
    static var appleGlass: KeyboardTheme? {
        try? KeyboardTheme(
            name: "Apple Glass",
            collectionName: "Rewordium",
            backgroundStyle: .color(Color(uiColor: .systemGray5).opacity(0.72)),
            inputForegroundColor: .primary,
            inputBackgroundColor: Color.white.opacity(0.88),
            systemForegroundColor: .primary,
            systemBackgroundColor: Color.white.opacity(0.76),
            primaryForegroundColor: .white,
            primaryBackgroundColor: Color.accentColor,
            buttonCornerRadius: RewordiumTokens.Radius.key,
            buttonBorderColor: Color.white.opacity(0.45),
            buttonBorderSize: 0.5,
            buttonPressedOverlayColor: Color.primary.opacity(0.08),
            buttonShadowColor: Color.black.opacity(0.16),
            buttonShadowSize: 0.5,
            autocompleteForegroundColor: .primary,
            autocorrectForegroundColor: .primary,
            autocorrectBackgroundColor: Color.white.opacity(0.34),
            autocorrectBackgroundCornerRadius: 10
        )
    }
}

struct RewordiumKeyboardBackplate: View {
    var body: some View {
        if #available(iOS 26.0, *) {
            Rectangle()
                .fill(Color(uiColor: .systemGray5).opacity(0.34))
                .glassEffect(.regular.tint(Color.white.opacity(0.10)), in: .rect(cornerRadius: 0))
        } else {
            Rectangle()
                .fill(.regularMaterial)
                .overlay(Color(uiColor: .systemGray5).opacity(0.58))
        }
    }
}

struct RewordiumPrimaryKeyVisual: View {
    let type: Keyboard.ReturnKeyType

    private var isProminent: Bool {
        switch type {
        case .return, .newLine:
            return false
        default:
            return true
        }
    }

    var body: some View {
        Group {
            if #available(iOS 26.0, *) {
                content
                    .background(keyFill)
                    .glassEffect(.regular.tint(isProminent ? Color.accentColor.opacity(0.42) : Color.white.opacity(0.18)).interactive(), in: .rect(cornerRadius: RewordiumTokens.Radius.key))
            } else {
                content
                    .background(keyFill)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: RewordiumTokens.Radius.key, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RewordiumTokens.Radius.key, style: .continuous)
                .strokeBorder(Color.white.opacity(isProminent ? 0.18 : 0.42), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(isProminent ? 0.14 : 0.10), radius: 0.6, x: 0, y: 0.6)
    }

    private var keyFill: some View {
        RoundedRectangle(cornerRadius: RewordiumTokens.Radius.key, style: .continuous)
            .fill(isProminent ? Color.accentColor : Color.white.opacity(0.86))
    }

    @ViewBuilder
    private var content: some View {
        switch type {
        case .search:
            Image(systemName: "magnifyingglass")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .return, .newLine:
            Image(systemName: "return")
                .font(.system(size: 21, weight: .regular))
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        default:
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(isProminent ? Color.white : Color.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.74)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 5)
        }
    }

    private var title: String {
        switch type {
        case .continue: return "Continue"
        case .done: return "Done"
        case .emergencyCall: return "Emergency"
        case .go: return "Go"
        case .join: return "Join"
        case .next: return "Next"
        case .ok: return "OK"
        case .route: return "Route"
        case .send: return "Send"
        case .custom(let title): return title
        case .return, .newLine, .search: return ""
        }
    }
}

/// Background material for the toolbar surface.
///
/// `.ultraThinMaterial` matches the system predictive bar.
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
                    .fill(.clear)
                    .glassEffect(.regular.tint(Color.white.opacity(0.08)), in: .rect(cornerRadius: 0))
            } else {
                Rectangle()
                    .fill(.regularMaterial)
            }
        }
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
                    .fill(Color.accentColor.opacity(isHighlighted ? 0.18 : 0.0))
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

/// Pill surface for persona chips, suggestion-strip slots, and small CTAs.
struct RewordiumPillSurface: ViewModifier {
    var isSelected: Bool = false

    func body(content: Content) -> some View {
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

/// Card surface used by the AI result panel. Slightly taller corner radius
/// and a subtle accent tint to set it apart from the chip grid.
struct RewordiumCardSurface: ViewModifier {
    func body(content: Content) -> some View {
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

extension View {
    func rewordiumSurface() -> some View { modifier(RewordiumSurface()) }

    func rewordiumChip(isPressed: Bool = false, isHighlighted: Bool = false) -> some View {
        modifier(RewordiumChipSurface(isPressed: isPressed, isHighlighted: isHighlighted))
    }

    func rewordiumPill(isSelected: Bool = false) -> some View {
        modifier(RewordiumPillSurface(isSelected: isSelected))
    }

    func rewordiumCard() -> some View { modifier(RewordiumCardSurface()) }

    @ViewBuilder
    func rewordiumProminentStyle() -> some View {
        self.buttonStyle(.plain)
    }

    @ViewBuilder
    func rewordiumGlassContainer() -> some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: 14) {
                self
            }
        } else {
            self
        }
    }
}
