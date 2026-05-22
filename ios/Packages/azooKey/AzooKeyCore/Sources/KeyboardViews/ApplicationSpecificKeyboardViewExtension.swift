//
//  ApplicationSpecificKeyboardViewExtension.swift
//
//
//  Created by ensan on 2023/07/21.
//

import Foundation
import KeyboardThemes

public protocol ApplicationSpecificKeyboardViewExtension {
    associatedtype ThemeExtension: ApplicationSpecificKeyboardViewExtensionLayoutDependentDefaultThemeProvidable
    associatedtype MessageProvider: ApplicationSpecificKeyboardViewMessageProvider
    associatedtype SettingProvider: ApplicationSpecificKeyboardViewSettingProvider
}

public extension ApplicationSpecificKeyboardViewExtension {
    typealias Theme = ThemeData<ThemeExtension>
}

public protocol ApplicationSpecificKeyboardViewExtensionLayoutDependentDefaultThemeProvidable: ApplicationSpecificTheme {
    /// アプリケーションデフォルト
    static var `default`: ThemeData<Self> { get }
    /// ネイティブデザイン
    static var native: ThemeData<Self> { get }
}

public protocol ApplicationSpecificKeyboardViewMessageProvider {
    associatedtype MessageID: MessageIdentifierProtocol
    static var messages: [MessageData<MessageID>] { get }
    static var userDefaults: UserDefaults { get }
}
