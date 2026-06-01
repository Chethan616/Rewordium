import Foundation

/// App-Group-backed LRU of words the user has typed. Used by
/// `AutocompleteService` to surface "words you've used before" completions
/// in the suggestion strip.
///
/// Capacity is small (200 entries) on purpose — the goal is freshness, not
/// dictionary coverage. UITextChecker already supplies the broad
/// dictionary; this layer just biases toward the user's recent vocabulary.
///
/// Storage is shared with the host app via the App Group so words typed
/// in the Flutter paraphraser are reachable from the keyboard and vice
/// versa.
final class RecentWordsStore {

    static let shared = RecentWordsStore()

    private let capacity = 200
    private let key = "recent_words_lru"
    private let queue = DispatchQueue(label: "com.noxquill.rewordium.recentwords", qos: .utility)
    private var cache: [String]?

    private var defaults: UserDefaults {
        UserDefaults(suiteName: SharedSettings.appGroupID) ?? .standard
    }

    /// Returns the LRU snapshot, newest first. Cheap — reads from in-memory
    /// cache after the first call.
    func snapshot() -> [String] {
        queue.sync {
            if let cache { return cache }
            let stored = defaults.stringArray(forKey: key) ?? []
            cache = stored
            return stored
        }
    }

    /// Move `word` to the front of the LRU. No-op for words shorter than 2
    /// characters or containing non-letter characters.
    func record(_ word: String) {
        let normalized = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.count >= 2,
              normalized.unicodeScalars.allSatisfy({ CharacterSet.letters.contains($0) || $0 == "'" })
        else { return }

        queue.async { [weak self] in
            guard let self else { return }
            var list = self.cache ?? self.defaults.stringArray(forKey: self.key) ?? []
            // Case-insensitive dedupe but preserve the casing the user typed
            // most recently — surfaces "Hello" vs "hello" naturally.
            list.removeAll { $0.lowercased() == normalized.lowercased() }
            list.insert(normalized, at: 0)
            if list.count > self.capacity {
                list = Array(list.prefix(self.capacity))
            }
            self.cache = list
            self.defaults.set(list, forKey: self.key)
        }
    }

    /// Best completion for a prefix, if any. Linear scan is fine at N=200.
    func completion(forPrefix prefix: String) -> String? {
        guard !prefix.isEmpty else { return nil }
        let lower = prefix.lowercased()
        let snapshot = snapshot()
        return snapshot.first { word in
            word.count > prefix.count && word.lowercased().hasPrefix(lower)
        }
    }
}
