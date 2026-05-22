import Foundation
import SwiftUI

public struct UnifiedPositionSpecifier: Hashable, Sendable {
    public var x: CGFloat
    public var y: CGFloat
    public var width: CGFloat
    public var height: CGFloat

    public init(x: CGFloat, y: CGFloat, width: CGFloat = 1.0, height: CGFloat = 1.0) {
        self.x = x
        self.y = y
        self.width = width
        self.height = height
    }
}
