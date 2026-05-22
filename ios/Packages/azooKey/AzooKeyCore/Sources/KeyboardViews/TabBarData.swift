//
//  TabBarData.swift
//  azooKey
//
//  Created by ensan on 2021/02/21.
//  Copyright © 2021 ensan. All rights reserved.
//

import CustardKit
import Foundation

public enum TabBarItemLabelType: Codable, Equatable, Sendable {
    case text(String)
    case image(String)

    public enum LabelType: CodingKey, Equatable, Hashable {
        case text
        case image
    }

    public var labelType: LabelType {
        switch self {
        case .image: return .image
        case .text: return .text
        }
    }
}

public extension TabBarItemLabelType {
    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: LabelType.self)
        switch self {
        case let .text(value as any Encodable), let .image(value as any Encodable):
            try value.containerEncode(container: &container, key: self.labelType)
        }
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: LabelType.self)
        guard let key = container.allKeys.first else {
            throw DecodingError.dataCorrupted(
                DecodingError.Context(codingPath: container.codingPath, debugDescription: "Unabled to decode TabBarItemLabelType.")
            )
        }
        switch key {
        case .text:
            let value = try container.decode(String.self, forKey: .text)
            self = .text(value)
        case .image:
            let value = try container.decode(String.self, forKey: .image)
            self = .image(value)
        }
    }
}

public struct TabBarItem: Codable, Sendable {
    public init(label: TabBarItemLabelType, pinned: Bool, actions: [CodableActionData]) {
        self.label = label
        self.pinned = pinned
        self.actions = actions
    }

    public let label: TabBarItemLabelType
    public var pinned: Bool
    public let actions: [CodableActionData]

    private enum CodingKeys: CodingKey {
        case pinned
        case label
        case actions
    }

    public init(from decoder: any Decoder) throws {
        let container: KeyedDecodingContainer<TabBarItem.CodingKeys> = try decoder.container(keyedBy: TabBarItem.CodingKeys.self)
        self.pinned = try container.decodeIfPresent(Bool.self, forKey: TabBarItem.CodingKeys.pinned) ?? false
        self.label = try container.decode(TabBarItemLabelType.self, forKey: TabBarItem.CodingKeys.label)
        self.actions = try container.decode([CodableActionData].self, forKey: TabBarItem.CodingKeys.actions)
    }

    public func encode(to encoder: any Encoder) throws {
        var container: KeyedEncodingContainer<TabBarItem.CodingKeys> = encoder.container(keyedBy: TabBarItem.CodingKeys.self)
        try container.encode(self.pinned, forKey: TabBarItem.CodingKeys.pinned)
        try container.encode(self.label, forKey: TabBarItem.CodingKeys.label)
        try container.encode(self.actions, forKey: TabBarItem.CodingKeys.actions)
    }
}

public struct TabBarData: Codable, Sendable {
    public init(identifier: Int, lastUpdateDate: Date? = Date(), items: [TabBarItem]) {
        self.identifier = identifier
        self.lastUpdateDate = lastUpdateDate
        self.items = items
    }

    public let identifier: Int
    public var lastUpdateDate: Date? = Date()
    public var items: [TabBarItem]

    public static let `default` = TabBarData(identifier: 0, items: [
        TabBarItem(label: .image("keyboard.chevron.compact.down"), pinned: true, actions: [.dismissKeyboard]),
        TabBarItem(label: .image("aspectratio"), pinned: true, actions: [.enableResizingMode, .toggleTabBar]),
        TabBarItem(label: .image("face.smiling"), pinned: true, actions: [.moveTab(.system(.emoji_tab))]),
        TabBarItem(label: .text("あいう"), pinned: false, actions: [.moveTab(.system(.user_japanese))]),
        TabBarItem(label: .text("ABC"), pinned: false, actions: [.moveTab(.system(.user_english))]),
    ])
}

private extension Encodable {
    /// Encodes this value into the given container.
    /// - Parameters:
    ///   - container: The container to encode this value into.
    func containerEncode<CodingKeys: CodingKey>(container: inout KeyedEncodingContainer<CodingKeys>, key: CodingKeys) throws {
        try container.encode(self, forKey: key)
    }
}
