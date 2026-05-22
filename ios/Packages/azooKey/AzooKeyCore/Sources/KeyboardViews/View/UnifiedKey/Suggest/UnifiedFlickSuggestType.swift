import enum CustardKit.FlickDirection
import Foundation

public enum FlickSuggestType: Equatable, Hashable, Sendable {
    case all
    case flick(FlickDirection)
}
