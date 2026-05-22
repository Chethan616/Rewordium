//
//  ZenzaiEffortSetting.swift
//  AzooKeyUtils
//
//  Created by Codex on 2025/08/28.
//

import Foundation
import SwiftUI

public struct ZenzaiEffortSettingKey: KeyboardSettingKey, StoredInUserDefault {
    public enum Value: Int, Sendable {
        case low
        case medium
        case high
    }

    public static let title: LocalizedStringKey = "Zenzaiのエフォート"
    public static let explanation: LocalizedStringKey = "Zenzaiの探索の強さを調整します。高いほど変換品質は上がりますが処理が重くなります。"
    public static let defaultValue: Value = .medium
    public static let key: String = "zenzai_effort"

    @MainActor
    static func get() -> Value? {
        let object = SharedStore.userDefaults.object(forKey: key)
        if let object, let value = object as? Int {
            return Value(rawValue: value)
        }
        return nil
    }

    @MainActor
    static func set(newValue: Value) {
        SharedStore.userDefaults.set(newValue.rawValue, forKey: key)
    }

    @MainActor
    public static var value: Value {
        get { get() ?? defaultValue }
        set { set(newValue: newValue) }
    }
}

public extension KeyboardSettingKey where Self == ZenzaiEffortSettingKey {
    static var zenzaiEffort: Self { .init() }
}
