import CustardKit
import Foundation
import SwiftUI

// Unified variation model that can represent both Flick (4-way) and Qwerty (linear) variants.
public struct UnifiedVariation: Sendable, Equatable {
    public let label: KeyLabelType
    public let pressActions: [ActionType]
    public let longPressActions: LongpressActionType

    public init(label: KeyLabelType, pressActions: [ActionType], longPressActions: LongpressActionType = .none) {
        self.label = label
        self.pressActions = pressActions
        self.longPressActions = longPressActions
    }
}

public enum UnifiedVariationSpace: Sendable, Equatable {

    case none
    // Four directional variations (Flick)
    case fourWay([FlickDirection: UnifiedVariation])
    // Linear variations (Qwerty) with presentation direction
    case linear([QwertyVariationsModel.VariationElement], direction: VariationsViewDirection)
}
