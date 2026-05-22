import Flutter
import UIKit

/// Bridges Flutter ↔ the RewordiumKeyboard extension via App Group UserDefaults.
///
/// Flutter writes settings (API key, model, haptics, etc.); the extension
/// reads them via `SharedSettings`. Keys mirror those in
/// `ios/RewordiumKeyboard/Services/SharedSettings.swift` — keep both files
/// in sync when adding a setting.
///
/// Registered from `AppDelegate.application(_:didFinishLaunchingWithOptions:)`.
final class KeyboardSettingsBridge: NSObject {

    static let channelName = "com.noxquill.rewordium/keyboard_settings"
    static let appGroupID  = "group.com.noxquill.rewordium"

    private enum Keys {
        static let groqAPIKey     = "groq_api_key"
        static let groqModel      = "groq_model"
        static let hapticsEnabled = "haptics_enabled"
        static let aiEnabled      = "ai_enabled"
        static let defaultTone    = "default_tone"
        static let lastSyncedAt   = "last_synced_at"
    }

    private var channel: FlutterMethodChannel?

    private var store: UserDefaults? {
        UserDefaults(suiteName: Self.appGroupID)
    }

    func register(with messenger: FlutterBinaryMessenger) {
        let channel = FlutterMethodChannel(name: Self.channelName, binaryMessenger: messenger)
        channel.setMethodCallHandler { [weak self] call, result in
            self?.handle(call, result: result)
        }
        self.channel = channel
    }

    // MARK: - Method dispatch

    private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let store = store else {
            result(FlutterError(code: "app_group_unavailable",
                                message: "App Group \(Self.appGroupID) is not configured.",
                                details: nil))
            return
        }

        switch call.method {
        case "write":
            guard let args = call.arguments as? [String: Any] else {
                result(FlutterError(code: "bad_args", message: "Expected map.", details: nil))
                return
            }
            apply(args, to: store)
            store.set(Date().timeIntervalSince1970, forKey: Keys.lastSyncedAt)
            result(true)

        case "read":
            result(snapshot(from: store))

        case "clear":
            for key in [Keys.groqAPIKey, Keys.groqModel, Keys.hapticsEnabled, Keys.aiEnabled, Keys.defaultTone] {
                store.removeObject(forKey: key)
            }
            result(true)

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: - Helpers

    private func apply(_ args: [String: Any], to store: UserDefaults) {
        if let value = args[Keys.groqAPIKey] as? String {
            // Empty string = explicit clear; non-empty = write.
            value.isEmpty ? store.removeObject(forKey: Keys.groqAPIKey)
                          : store.set(value, forKey: Keys.groqAPIKey)
        }
        if let value = args[Keys.groqModel] as? String, !value.isEmpty {
            store.set(value, forKey: Keys.groqModel)
        }
        if let value = args[Keys.hapticsEnabled] as? Bool {
            store.set(value, forKey: Keys.hapticsEnabled)
        }
        if let value = args[Keys.aiEnabled] as? Bool {
            store.set(value, forKey: Keys.aiEnabled)
        }
        if let value = args[Keys.defaultTone] as? String, !value.isEmpty {
            store.set(value, forKey: Keys.defaultTone)
        }
    }

    private func snapshot(from store: UserDefaults) -> [String: Any] {
        var out: [String: Any] = [:]
        if let v = store.string(forKey: Keys.groqAPIKey)  { out[Keys.groqAPIKey] = v }
        if let v = store.string(forKey: Keys.groqModel)   { out[Keys.groqModel] = v }
        out[Keys.hapticsEnabled] = store.object(forKey: Keys.hapticsEnabled) as? Bool ?? true
        out[Keys.aiEnabled]      = store.object(forKey: Keys.aiEnabled)      as? Bool ?? true
        if let v = store.string(forKey: Keys.defaultTone) { out[Keys.defaultTone] = v }
        out[Keys.lastSyncedAt]   = store.double(forKey: Keys.lastSyncedAt)
        return out
    }
}
