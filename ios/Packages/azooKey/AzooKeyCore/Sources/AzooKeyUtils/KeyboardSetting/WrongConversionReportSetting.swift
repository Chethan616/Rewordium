import Foundation
import SwiftUI

public struct EnableWrongConversionReport: BoolKeyboardSettingKey {
    public static let title: LocalizedStringKey = "誤変換レポートを送信"
    public static let explanation: LocalizedStringKey = "第一候補以外の変換候補を選んだ際に、内容を確認した上でレポートを送信できます。"
    public static let defaultValue: Bool = false
    public static let key: String = "enable_wrong_conversion_report"
    public static var requireFullAccess: Bool { true }
}

public extension KeyboardSettingKey where Self == EnableWrongConversionReport {
    static var enableWrongConversionReport: Self { .init() }
}

public struct WrongConversionReportFrequencySettingKey: KeyboardSettingKey, StoredInUserDefault {
    public enum Value: Int, CaseIterable, Sendable {
        case always = 1
        case frequent = 3
        case occasional = 10
        case rare = 50

        public var description: LocalizedStringKey {
            switch self {
            case .always:
                return "とても頻繁"
            case .frequent:
                return "頻繁"
            case .occasional:
                return "たまに"
            case .rare:
                return "まれに"
            }
        }
    }

    public static let title: LocalizedStringKey = "送信を提案する頻度"
    public static let explanation: LocalizedStringKey = "誤変換レポートの送信を提案する頻度を調整します。"
    public static let defaultValue: Value = .occasional
    public static let key: String = "wrong_conversion_report_frequency"

    @MainActor
    public static var value: Value {
        get {
            if let stored = SharedStore.userDefaults.object(forKey: key) as? Int,
               let value = Value(rawValue: stored) {
                return value
            }
            return defaultValue
        }
        set {
            SharedStore.userDefaults.set(newValue.rawValue, forKey: key)
        }
    }

    public static func denominator(for value: Value) -> Int {
        value.rawValue
    }
}

public extension KeyboardSettingKey where Self == WrongConversionReportFrequencySettingKey {
    static var wrongConversionReportFrequency: Self { .init() }
}

public struct WrongConversionReportIncludeContextKey: BoolKeyboardSettingKey {
    public static let title: LocalizedStringKey = "文脈をデフォルトで含める"
    public static let explanation: LocalizedStringKey = "レポートに左右の文脈を自動で含めます。左右それぞれ10文字程度まででカットされるので、すべての文章が送信されることはありません。都度、確認画面で含めないよう変更することもできます。"
    public static let defaultValue: Bool = false
    public static let key: String = "wrong_conversion_include_context"
}

public extension KeyboardSettingKey where Self == WrongConversionReportIncludeContextKey {
    static var wrongConversionIncludeContext: Self { .init() }
}

public struct WrongConversionReportUserNicknameKey: KeyboardSettingKey {
    public static let title: LocalizedStringKey = "ユーザニックネーム"
    public static let explanation: LocalizedStringKey = "レポートに含める任意のニックネームを設定できます。"
    public static let defaultValue: String = ""
    public static let key: String = "wrong_conversion_report_user_nickname"

    @MainActor
    public static var value: String {
        get {
            SharedStore.userDefaults.string(forKey: key) ?? defaultValue
        }
        set {
            SharedStore.userDefaults.set(newValue, forKey: key)
        }
    }
}

public extension KeyboardSettingKey where Self == WrongConversionReportUserNicknameKey {
    static var wrongConversionReportUserNickname: Self { .init() }
}

public extension WrongConversionReportFrequencySettingKey.Value {
    var probabilityDenominator: Int {
        self.rawValue
    }
}
