import struct CustardKit.ReplaceBehavior
import SwiftUtils

public struct ReplaceBehaviorManager {
    private static func dakutenReplace(_ character: Character) -> Character {
        var result = CharacterUtils.dakuten(character)
        if result != character {
            return result
        }
        let normalized = CharacterUtils.ogaki(CharacterUtils.muhandakuten(character))
        result = CharacterUtils.dakuten(normalized)
        if result != normalized {
            return result
        }
        return character
    }

    private static func handakutenReplace(_ character: Character) -> Character {
        var result = CharacterUtils.handakuten(character)
        if result != character {
            return result
        }
        let normalized = CharacterUtils.ogaki(CharacterUtils.mudakuten(character))
        result = CharacterUtils.handakuten(normalized)
        if result != normalized {
            return result
        }
        return character
    }

    private static func kogakiReplace(_ character: Character) -> Character {
        var result = CharacterUtils.kogaki(character)
        if result != character {
            return result
        }
        let normalized = CharacterUtils.muhandakuten(CharacterUtils.mudakuten(character))
        result = CharacterUtils.kogaki(normalized)
        if result != normalized {
            return result
        }
        return character
    }

    /// 濁点、小書き、半濁点などを相互に変換する関数。
    private static func defaultReplace(_ character: Character) -> String {
        if character.isLowercase {
            return character.uppercased()
        }
        if character.isUppercase {
            return character.lowercased()
        }

        if Set(["あ", "い", "え", "お", "や", "ゆ", "よ", "わ"]).contains(character) {
            return String(CharacterUtils.kogaki(character))
        }

        if Set(["ぁ", "ぃ", "ぇ", "ぉ", "ゃ", "ゅ", "ょ", "ゎ"]).contains(character) {
            return String(CharacterUtils.ogaki(character))
        }

        if Set(["か", "き", "く", "け", "こ", "さ", "し", "す", "せ", "そ", "た", "ち", "て", "と"]).contains(character) {
            return String(CharacterUtils.dakuten(character))
        }

        if Set(["が", "ぎ", "ぐ", "げ", "ご", "ざ", "じ", "ず", "ぜ", "ぞ", "だ", "ぢ", "で", "ど"]).contains(character) {
            return String(CharacterUtils.mudakuten(character))
        }

        if Set(["つ", "う"]).contains(character) {
            return String(CharacterUtils.kogaki(character))
        }

        if Set(["っ", "ぅ"]).contains(character) {
            return String(CharacterUtils.dakuten(CharacterUtils.ogaki(character)))
        }

        if Set(["づ", "ゔ"]).contains(character) {
            return String(CharacterUtils.mudakuten(character))
        }

        if Set(["は", "ひ", "ふ", "へ", "ほ"]).contains(character) {
            return String(CharacterUtils.dakuten(character))
        }

        if Set(["ば", "び", "ぶ", "べ", "ぼ"]).contains(character) {
            return String(CharacterUtils.handakuten(CharacterUtils.mudakuten(character)))
        }

        if Set(["ぱ", "ぴ", "ぷ", "ぺ", "ぽ"]).contains(character) {
            return String(CharacterUtils.muhandakuten(character))
        }

        return String(character)
    }

    private static func apply(replaceType: ReplaceBehavior.ReplaceType, to character: Character) -> String {
        switch replaceType {
        case .default: defaultReplace(character)
        case .dakuten: String(dakutenReplace(character))
        case .handakuten: String(handakutenReplace(character))
        case .kogaki: String(kogakiReplace(character))
        }
    }

    public static func apply(replaceBehavior: ReplaceBehavior, to character: Character) -> String {
        var result = apply(replaceType: replaceBehavior.type, to: character)
        var fallbacks = replaceBehavior.fallbacks
        while result == String(character), !fallbacks.isEmpty {
            result = apply(replaceType: fallbacks.removeFirst(), to: character)
        }
        return result
    }
}
