import SwiftUI

struct KeyBackground: View {
    var backgroundColor: Color
    var borderColor: Color
    var borderWidth: CGFloat
    var size: CGSize
    var shadow: (color: Color, radius: CGFloat, x: CGFloat, y: CGFloat)
    var blendMode: BlendMode

    var body: some View {
        RoundedRectangle(cornerRadius: 6)
            .strokeAndFill(
                fillContent: self.backgroundColor,
                strokeContent: self.borderColor,
                lineWidth: self.borderWidth
            )
            .frame(width: self.size.width, height: self.size.height)
            .compositingGroup()
            .shadow(
                color: self.shadow.color,
                radius: self.shadow.radius,
                x: self.shadow.x,
                y: self.shadow.y
            )
            .blendMode(self.blendMode)
    }
}
