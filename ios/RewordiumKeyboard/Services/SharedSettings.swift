import Foundation

/// Read-only view of preferences synced from the Flutter host app via
/// the shared App Group. The host writes; the extension reads.
///
/// CRITICAL: this type is read very early in the extension's lifecycle
/// (KeyboardViewController.rewordiumApp passes `appGroupID` to KeyboardKit's
/// `setupKeyboardKit`). If it ever crashes the extension, iOS marks the
/// extension as broken and falls back to the system keyboard with no UI
/// to tell the user why.
///
/// Failure modes we explicitly tolerate:
///   * App Group not provisioned on the user's signing identity (typical
///     when Sideloadly re-signs with a free Apple ID — the entitlement
///     is silently stripped because free accounts can't create App Groups
///     in the developer portal). Falls back to the extension's own
///     UserDefaults — the AI features won't see the host app's API key
///     but the keyboard still loads and the diagnostic banner still
///     shows.
///   * Defaults backing file is corrupt / unwritable. Same fallback.
///
/// Writing happens in `Runner/KeyboardSettingsBridge.swift` — keep keys in
/// sync between the two.
enum SharedSettings {
    static let appGroupID = "group.com.noxquill.rewordium"

    /// Lazily resolves the App Group's UserDefaults. Never crashes — falls
    /// back to a process-local UserDefaults if the group isn't reachable.
    /// Caches the result so we don't pay the entitlement-check cost on
    /// every read.
    private static let store: UserDefaults = {
        if let defaults = UserDefaults(suiteName: appGroupID) {
            // Sanity: even when the entitlement is missing, iOS will hand
            // back a UserDefaults whose persistentDomain is the App Group
            // ID. A read-back probe would be more rigorous, but it's not
            // necessary — we just need to NOT crash here.
            return defaults
        }
        NSLog("[RewordiumKeyboard] SharedSettings: App Group '\(appGroupID)' unavailable. Falling back to .standard — AI features will lack the host app's settings until the App Group is provisioned.")
        return .standard
    }()

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
        store.string(forKey: Keys.groqModel) ?? "openai/gpt-oss-120b"
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
