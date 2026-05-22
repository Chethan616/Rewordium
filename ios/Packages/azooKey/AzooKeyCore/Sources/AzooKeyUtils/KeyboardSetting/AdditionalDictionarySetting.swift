import Foundation
import KanaKanjiConverterModule
import SwiftUI

public struct AdditionalSystemDictionarySetting: Sendable, Codable, Equatable {
    public init(systemDictionarySettings: [AdditionalSystemDictionarySetting.SystemDictionaryType: AdditionalSystemDictionarySetting.SystemDictionaryConfig]) {
        self.systemDictionarySettings = systemDictionarySettings
    }

    public enum SystemDictionaryType: String, Sendable, Codable, Hashable, CaseIterable {
        case emoji
        case kaomoji
    }

    public struct SystemDictionaryConfig: Sendable, Codable, Equatable {
        public init(enabled: Bool, denylist: Set<String> = Set<String>()) {
            self.enabled = enabled
            self.denylist = denylist
        }

        /// このシステム辞書が有効化されているか
        public var enabled: Bool
        /// 特定のエントリを拒否する場合、そのsurface形をここに指定する
        public var denylist = Set<String>()
    }

    public var systemDictionarySettings: [SystemDictionaryType: SystemDictionaryConfig]

    public static func get(_ value: Any) -> Self? {
        if let value = value as? Data {
            let decoder = JSONDecoder()
            if let data = try? decoder.decode(Self.self, from: value) {
                return data
            }
        }
        return nil
    }
}

public extension StoredInUserDefault where Value == AdditionalSystemDictionarySetting {
    @MainActor static func get() -> Value? {
        if let value = SharedStore.userDefaults.value(forKey: key) {
            AdditionalSystemDictionarySetting.get(value)
        } else {
            nil
        }
    }
    @MainActor static func set(newValue: Value) {
        SharedStore.userDefaults.set(newValue.saveValue, forKey: key)
    }
}

extension AdditionalSystemDictionarySetting: Savable {
    typealias SaveValue = Data
    var saveValue: Data {
        let encoder = JSONEncoder()
        if let encodedValue = try? encoder.encode(self) {
            return encodedValue
        } else {
            return Data()
        }
    }
}

public struct AdditionalSystemDictionarySettingKey: KeyboardSettingKey, StoredInUserDefault {
    public static let title: LocalizedStringKey = "追加システム辞書"
    public static let explanation: LocalizedStringKey = "「絵文字」「顔文字」などの追加のシステム辞書の設定です。"
    public static let defaultValue = AdditionalSystemDictionarySetting(systemDictionarySettings: [
        .emoji: .init(enabled: true),
        .kaomoji: .init(enabled: false),
    ])
    public static let key: String = "additional_system_dictionary_setting"

    public typealias Value = AdditionalSystemDictionarySetting

    public static var value: Value {
        get {
            get() ?? defaultValue
        }
        set {
            set(newValue: newValue)
        }
    }

    @MainActor public static var available: Bool {
        SharedStore.userDefaults.object(forKey: Self.key) != nil
    }
}

public extension KeyboardSettingKey where Self == AdditionalSystemDictionarySettingKey {
    static var additionalSystemDictionarySetting: Self { .init() }
}
