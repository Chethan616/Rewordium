import SwiftUI

/// Horizontal pill row for picking the active `AIPersona`. Sits above the
/// action chip grid (and inline inside the result card when the user wants
/// to "try another style").
///
/// Single-select; tapping the active persona deselects back to `.neutral`.
struct PersonaRow: View {

    @Binding var selected: AIPersona
    var personas: [AIPersona] = AIPersona.allCases
    var onChange: (AIPersona) -> Void = { _ in }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: RewordiumTokens.Space.xs) {
                ForEach(personas) { persona in
                    chip(for: persona)
                }
            }
            .padding(.horizontal, RewordiumTokens.Space.xxs)
        }
        .scrollClipDisabled()
    }

    private func chip(for persona: AIPersona) -> some View {
        let isSelected = persona == selected
        return Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            let next: AIPersona = isSelected ? .neutral : persona
            withAnimation(RewordiumTokens.AnimationCurve.tap) {
                selected = next
            }
            onChange(next)
        } label: {
            HStack(spacing: 5) {
                Image(systemName: persona.systemImage)
                    .font(.system(size: 11, weight: .medium))
                Text(persona.title)
                    .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
            }
            .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
            .padding(.horizontal, RewordiumTokens.Space.md)
            .padding(.vertical, 6)
            .rewordiumPill(isSelected: isSelected)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(persona.title) persona")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
