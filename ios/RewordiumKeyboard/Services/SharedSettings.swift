import Foundation

/// Read-only view of preferences synced from the Flutter host app via
/// the shared App Group. The host writes; the extension reads.
///
/// Writing happens in `Runner/KeyboardSettingsBridge.swift` — keep keys in
/// sync between the two.
enum SharedSettings {
    static let appGroupID = "group.com.noxquill.rewordium"

    private static var store: UserDefaults {
        guard let defaults = UserDefaults(suiteName: appGroupID) else {
            assertionFailure("App Group not configured: \(appGroupID)")
            return .standard
        }
        return defaults
    }

    // MARK: - Keys (mirror in Runner/KeyboardSettingsBridge.swift)

    private enum Keys {
        static let groqAPIKey       = "groq_api_key"
        static let groqModel        = "groq_model"
        static let hapticsEnabled   = "haptics_enabled"
        static let aiEnabled        = "ai_enabled"
        static let defaultTone      = "default_tone"
        static let lastSyncedAt     = "last_synced_at"
    }

    // MARK: - Values

    static var groqAPIKey: String? {
        let value = store.string(forKey: Keys.groqAPIKey)
        return value?.isEmpty == false ? value : nil
    }

    static var groqModel: String {
        store.string(forKey: Keys.groqModel) ?? "qwen/qwen3-32b"
    }

    static var hapticsEnabled: Bool {
        store.object(forKey: Keys.hapticsEnabled) as? Bool ?? true
    }

    static var aiEnabled: Bool {
        store.object(forKey: Keys.aiEnabled) as? Bool ?? true
    }

    static var defaultTone: String {
        store.string(forKey: Keys.defaultTone) ?? "neutral"
    }

    static var lastSyncedAt: Date? {
        let interval = store.double(forKey: Keys.lastSyncedAt)
        return interval > 0 ? Date(timeIntervalSince1970: interval) : nil
    }
}
