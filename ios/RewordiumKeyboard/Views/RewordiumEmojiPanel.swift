import SwiftUI
import UIKit

/// Production-grade emoji panel for the Rewordium keyboard.
///
/// We build our own instead of relying on KeyboardKit's free-tier default
/// because that one is intentionally minimal: a flat grid, no recents
/// persistence, no skin-tone variants, no category navigation. iOS users
/// expect the same affordances Apple's keyboard ships with.
///
/// What this provides:
///   * 9 categories (Recents, Smileys, People, Animals, Food, Activity,
///     Travel, Objects, Symbols, Flags) — matching iOS's native layout.
///   * LazyVGrid for performant scroll across ~1500 glyphs.
///   * Recent emojis persisted in App Group UserDefaults (shared with the
///     host app) so the user sees their picks across keyboard launches.
///   * Skin-tone picker on long-press for the people/hand glyphs (the only
///     ones that vary). Preference is also persisted.
///   * Bottom rail of category tabs with active-indicator.
///   * Backspace + ABC return-to-text affordance, matching iOS conventions.
///
/// Performance: the full emoji list is built once at compile time as
/// `EmojiCatalog.all` (static let, lazy-init via Swift). Render path uses
/// LazyVGrid with `id: \.self` on a String — no allocations per scroll tick.
struct RewordiumEmojiPanel: View {

    /// Caller hands us a way to insert text + delete + return to ABC.
    let onInsert: (String) -> Void
    let onBackspace: () -> Void
    let onReturnToAlphabetic: () -> Void

    @State private var selectedCategory: EmojiCatalog.Category = .recents
    // Lazy-loaded on first appearance — keeps the App Group read off the
    // hot view-init path so the panel's first frame isn't blocked on I/O.
    @State private var recents: [String] = []
    @State private var didLoadRecents: Bool = false
    @State private var skinTone: EmojiSkinTone = EmojiPrefs.skinTone
    @State private var skinToneAnchor: String? = nil
    @State private var showSkinTonePicker: Bool = false

    private let columns: [GridItem] = Array(
        repeating: GridItem(.flexible(), spacing: 0),
        count: 8
    )

    var body: some View {
        VStack(spacing: 0) {
            grid
            divider
            bottomRail
        }
        .background(Color.clear)
        .overlay(skinTonePopover, alignment: .center)
        .onAppear {
            guard !didLoadRecents else { return }
            didLoadRecents = true
            // Read off the main thread — App Group plist read is cheap
            // but not free, and the panel's first frame is more important
            // than freshly-populated recents.
            Task.detached(priority: .userInitiated) {
                let loaded = EmojiRecents.load()
                await MainActor.run { recents = loaded }
            }
        }
    }

    // MARK: - Grid

    private var grid: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(currentList, id: \.self) { emoji in
                    EmojiCell(
                        emoji: applySkinTone(to: emoji),
                        onTap: { insert(emoji) },
                        onLongPress: skinToneApplicable(emoji) ? {
                            skinToneAnchor = emoji
                            showSkinTonePicker = true
                        } : nil
                    )
                    .frame(height: 38)
                }
            }
            .padding(.horizontal, 4)
            .padding(.top, 6)
            .padding(.bottom, 8)
        }
        .frame(maxHeight: .infinity)
    }

    private var currentList: [String] {
        switch selectedCategory {
        case .recents:
            return recents.isEmpty ? EmojiCatalog.starterRecents : recents
        case .smileys: return EmojiCatalog.smileys
        case .people:  return EmojiCatalog.people
        case .animals: return EmojiCatalog.animals
        case .food:    return EmojiCatalog.food
        case .activity: return EmojiCatalog.activity
        case .travel:  return EmojiCatalog.travel
        case .objects: return EmojiCatalog.objects
        case .symbols: return EmojiCatalog.symbols
        case .flags:   return EmojiCatalog.flags
        }
    }

    // MARK: - Bottom rail (category tabs + ABC/backspace)

    private var divider: some View {
        Rectangle()
            .fill(Color.primary.opacity(0.06))
            .frame(height: 0.5)
    }

    private var bottomRail: some View {
        HStack(spacing: 0) {
            // ABC return-to-text
            Button(action: onReturnToAlphabetic) {
                Text("ABC")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 40)
            }
            .buttonStyle(.plain)

            Rectangle()
                .fill(Color.primary.opacity(0.06))
                .frame(width: 0.5, height: 24)

            // Categories — scroll so we always fit, even on small screens
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    ForEach(EmojiCatalog.Category.allCases, id: \.self) { cat in
                        CategoryTab(
                            category: cat,
                            isActive: selectedCategory == cat,
                            onTap: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                selectedCategory = cat
                            }
                        )
                    }
                }
                .padding(.horizontal, 4)
            }

            Rectangle()
                .fill(Color.primary.opacity(0.06))
                .frame(width: 0.5, height: 24)

            // Backspace
            Button(action: onBackspace) {
                Image(systemName: "delete.backward")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 40)
            }
            .buttonStyle(.plain)
        }
        .frame(height: 40)
        .background(Color.primary.opacity(0.03))
    }

    // MARK: - Skin tone popover

    @ViewBuilder
    private var skinTonePopover: some View {
        if showSkinTonePicker, let anchor = skinToneAnchor {
            ZStack {
                // Tap outside to dismiss
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.easeOut(duration: 0.15)) {
                            showSkinTonePicker = false
                        }
                    }

                HStack(spacing: 6) {
                    ForEach(EmojiSkinTone.allCases, id: \.self) { tone in
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            skinTone = tone
                            EmojiPrefs.skinTone = tone
                            insert(EmojiSkinToneApplier.apply(tone: tone, to: anchor))
                            withAnimation(.easeOut(duration: 0.15)) {
                                showSkinTonePicker = false
                            }
                        } label: {
                            Text(EmojiSkinToneApplier.apply(tone: tone, to: anchor))
                                .font(.system(size: 28))
                                .frame(width: 40, height: 40)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(tone == skinTone ? Color.accentColor.opacity(0.15) : Color.clear)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(.regularMaterial)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(Color.primary.opacity(0.1), lineWidth: 0.5)
                )
                .shadow(color: .black.opacity(0.15), radius: 12, y: 4)
            }
            .transition(.opacity.combined(with: .scale(scale: 0.95, anchor: .center)))
        }
    }

    // MARK: - Behavior

    private func insert(_ emoji: String) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        onInsert(emoji)
        EmojiRecents.markUsed(emoji)
        recents = EmojiRecents.load()
    }

    private func applySkinTone(to emoji: String) -> String {
        guard skinToneApplicable(emoji) else { return emoji }
        return EmojiSkinToneApplier.apply(tone: skinTone, to: emoji)
    }

    private func skinToneApplicable(_ emoji: String) -> Bool {
        // We only support skin-tone variants for the curated people/hand list
        // below — applying to e.g. a flag is illegal and renders garbled.
        EmojiCatalog.skinToneCapable.contains(emoji)
    }
}

// MARK: - Cell

private struct EmojiCell: View {
    let emoji: String
    let onTap: () -> Void
    let onLongPress: (() -> Void)?

    var body: some View {
        Button(action: onTap) {
            Text(emoji)
                .font(.system(size: 30))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(EmojiButtonStyle(onLongPress: onLongPress))
    }
}

private struct EmojiButtonStyle: ButtonStyle {
    let onLongPress: (() -> Void)?
    
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(configuration.isPressed ? Color.primary.opacity(0.08) : Color.clear)
            )
            .simultaneousGesture(
                LongPressGesture(minimumDuration: 0.35)
                    .onEnded { _ in
                        if let onLongPress {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            onLongPress()
                        }
                    }
            )
    }
}

// MARK: - Category tab

private struct CategoryTab: View {
    let category: EmojiCatalog.Category
    let isActive: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Image(systemName: category.symbolName)
                .font(.system(size: 16, weight: isActive ? .semibold : .regular))
                .foregroundStyle(isActive ? Color.accentColor : Color.secondary)
                .frame(width: 38, height: 40)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: 20, height: 2)
                        .opacity(isActive ? 1 : 0)
                }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Catalog

enum EmojiCatalog {

    enum Category: CaseIterable {
        case recents, smileys, people, animals, food, activity, travel, objects, symbols, flags

        var symbolName: String {
            switch self {
            case .recents:  return "clock"
            case .smileys:  return "face.smiling"
            case .people:   return "hand.wave"
            case .animals:  return "pawprint"
            case .food:     return "fork.knife"
            case .activity: return "soccerball"
            case .travel:   return "car"
            case .objects:  return "lightbulb"
            case .symbols:  return "heart"
            case .flags:    return "flag"
            }
        }
    }

    /// Used when the user hasn't picked anything yet — better than an empty
    /// "Recents" tab on first open.
    static let starterRecents: [String] = [
        "😀", "😂", "🥰", "😍", "🤔", "😎", "👍", "🙏",
        "❤️", "🔥", "💯", "✨", "👀", "🎉", "😭", "🤣"
    ]

    static let smileys: [String] = [
        "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩",
        "😘","😗","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐",
        "🤨","😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒",
        "🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","🤯","🤠","🥳","🥸","😎","🤓","🧐","😕",
        "😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱",
        "😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩",
        "🤡","👹","👺","👻","👽","👾","🤖"
    ]

    static let people: [String] = [
        "👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆",
        "🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✍️",
        "💅","🤳","💪","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁","👅",
        "👄","💋","🩸","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵","🙍",
        "🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","👮","🕵️","💂","🥷","👷","🤴","👸",
        "👳","👲","🧕","🤵","👰","🤰","🤱","👼","🎅","🤶","🦸","🦹","🧙","🧚","🧛","🧜",
        "🧝","🧞","🧟"
    ]

    static let animals: [String] = [
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁","🐮","🐷","🐽","🐸",
        "🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇","🐺",
        "🐗","🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪰","🪲","🪳","🦟","🦗","🕷",
        "🕸","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬",
        "🐳","🐋","🦈","🐊","🐅","🐆","🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒",
        "🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐕‍🦺",
        "🐈","🐈‍⬛","🪶","🐓","🦃","🦤","🦚","🦜","🦢","🦩","🕊","🐇","🦝","🦨","🦡","🦫",
        "🦦","🦥","🐁","🐀","🐿","🦔","🐾","🐉","🐲","🌵","🎄","🌲","🌳","🌴","🪵","🌱"
    ]

    static let food: [String] = [
        "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥",
        "🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠",
        "🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴",
        "🌭","🍔","🍟","🍕","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘","🫕","🥫","🍝","🍜",
        "🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧",
        "🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯",
        "🥛","🍼","🫖","☕️","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹",
        "🧉","🍾","🧊","🥄","🍴","🍽","🥣","🥡","🥢","🧂"
    ]

    static let activity: [String] = [
        "⚽️","🏀","🏈","⚾️","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🏑","🥍",
        "🏏","🪃","🥅","⛳️","🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸","🥌",
        "🎿","⛷","🏂","🪂","🏋️","🤼","🤸","⛹️","🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽",
        "🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖","🏵","🎗","🎫","🎟","🎪","🤹",
        "🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🪘","🎷","🎺","🎸","🪕","🎻","🎲",
        "♟","🎯","🎳","🎮","🎰","🧩"
    ]

    static let travel: [String] = [
        "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛵","🏍",
        "🛺","🚲","🛴","🛹","🛼","🚁","🛸","✈️","🛩","🛫","🛬","🪂","💺","🚀","🛰","🚉",
        "🚊","🚝","🚞","🚋","🚃","🚂","🚆","🚇","🚄","🚅","🚈","🚂","🛤","🛣","🛢","⛽️",
        "🚧","🚦","🚥","🗺","🗿","🗽","🗼","🏰","🏯","🏟","🎡","🎢","🎠","⛲️","⛱","🏖",
        "🏝","🏜","🌋","⛰","🏔","🗻","🏕","⛺️","🏠","🏡","🏘","🏚","🏗","🏭","🏢","🏬",
        "🏣","🏤","🏥","🏦","🏨","🏪","🏫","🏩","💒","🏛","⛪️","🕌","🕍","🛕","🕋","⛩",
        "🛤","🌁","🌃","🏙","🌄","🌅","🌆","🌇","🌉","♨️","🎠","🎡","🎢","💈","🎪"
    ]

    static let objects: [String] = [
        "⌚️","📱","📲","💻","⌨️","🖥","🖨","🖱","🖲","🕹","🗜","💽","💾","💿","📀","📼",
        "📷","📸","📹","🎥","📽","🎞","📞","☎️","📟","📠","📺","📻","🎙","🎚","🎛","🧭",
        "⏱","⏲","⏰","🕰","⌛️","⏳","📡","🔋","🪫","🔌","💡","🔦","🕯","🪔","🧯","🛢",
        "💸","💵","💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒",
        "🛠","⛏","🪚","🔩","⚙️","🪤","🧱","⛓","🧲","🔫","💣","🧨","🪓","🔪","🗡","⚔️",
        "🛡","🚬","⚰️","🪦","⚱️","🏺","🔮","📿","🧿","💈","⚗️","🔭","🔬","🕳","🩹","🩺",
        "💊","💉","🩸","🧬","🦠","🧫","🧪","🌡","🧹","🪠","🧺","🧻","🚽","🚰","🚿","🛁",
        "🛀","🧼","🪥","🪒","🧽","🪣","🧴","🛎","🔑","🗝","🚪","🪑","🛋","🛏","🛌","🧸",
        "🪆","🖼","🪞","🪟","🛍","🛒","🎁","🎈","🎏","🎀","🪄","🪅","🎊","🎉","🎎","🏮",
        "🎐","🪔","🧧","✉️","📩","📨","📧","💌","📥","📤","📦","🏷","🪧","📪","📫","📬",
        "📭","📮","📯","📜","📃","📄","📑","🧾","📊","📈","📉","🗒","🗓","📆","📅","🗑",
        "📇","🗃","🗳","🗄","📋","📁","📂","🗂","🗞","📰","📓","📔","📒","📕","📗","📘",
        "📙","📚","📖","🔖","🧷","🔗","📎","🖇","📐","📏","🧮","📌","📍","✂️","🖊","🖋",
        "✒️","🖌","🖍","📝","✏️","🔍","🔎","🔏","🔐","🔒","🔓"
    ]

    static let symbols: [String] = [
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖",
        "💘","💝","💟","☮️","✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️","☦️","🛐","⛎","♈️",
        "♉️","♊️","♋️","♌️","♍️","♎️","♏️","♐️","♑️","♒️","♓️","🆔","⚛️","🉑","☢️","☣️",
        "📴","📳","🈶","🈚️","🈸","🈺","🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹",
        "🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘","❌","⭕️","🛑","⛔️","📛","🚫","💯","💢","♨️",
        "🚷","🚯","🚳","🚱","🔞","📵","🚭","❗️","❕","❓","❔","‼️","⁉️","🔅","🔆","〽️",
        "⚠️","🚸","🔱","⚜️","🔰","♻️","✅","🈯️","💹","❇️","✳️","❎","🌐","💠","Ⓜ️","🌀",
        "💤","🏧","🚾","♿️","🅿️","🛗","🈂️","🛂","🛃","🛄","🛅","🚹","🚺","🚼","⚧","🚻",
        "🚮","🎦","📶","🈁","🔣","ℹ️","🔤","🔡","🔠","🆖","🆗","🆙","🆒","🆕","🆓","0️⃣",
        "1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣","🔟","🔢","#️⃣","*️⃣","⏏️","▶️","⏸",
        "⏯","⏹","⏺","⏭","⏮","⏩","⏪","⏫","⏬","◀️","🔼","🔽","➡️","⬅️","⬆️","⬇️",
        "↗️","↘️","↙️","↖️","↕️","↔️","↪️","↩️","⤴️","⤵️","🔀","🔁","🔂","🔄","🔃","🎵",
        "🎶","➕","➖","➗","✖️","🟰","♾","💲","💱","™️","©️","®️","👁‍🗨","🔚","🔙","🔛",
        "🔝","🔜","〰️","➰","➿","✔️","☑️","🔘","🔴","🟠","🟡","🟢","🔵","🟣","⚫️","⚪️",
        "🟤","🔺","🔻","🔸","🔹","🔶","🔷","🔳","🔲","▪️","▫️","◾️","◽️","◼️","◻️","🟥",
        "🟧","🟨","🟩","🟦","🟪","⬛️","⬜️","🟫","🔈","🔇","🔉","🔊","🔔","🔕","📣","📢",
        "👁‍🗨","💬","💭","🗯","♠️","♣️","♥️","♦️","🃏","🎴","🀄️","🕐","🕑","🕒","🕓","🕔",
        "🕕","🕖","🕗","🕘","🕙","🕚","🕛"
    ]

    static let flags: [String] = [
        "🏳️","🏴","🏁","🚩","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️","🇦🇺","🇧🇪","🇧🇷","🇨🇦","🇨🇳","🇪🇬","🇫🇷","🇩🇪","🇬🇧",
        "🇮🇳","🇮🇩","🇮🇪","🇮🇱","🇮🇹","🇯🇵","🇰🇷","🇲🇽","🇳🇱","🇳🇿","🇳🇴","🇵🇰","🇵🇭","🇵🇱","🇵🇹","🇷🇺",
        "🇸🇦","🇸🇬","🇿🇦","🇪🇸","🇸🇪","🇨🇭","🇹🇷","🇺🇦","🇦🇪","🇺🇸","🇻🇳"
    ]

    /// Glyphs we know accept skin-tone modifiers. (People + hand gestures.)
    /// Anything outside this set, we don't even offer the long-press picker.
    static let skinToneCapable: Set<String> = [
        "👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆",
        "🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🙏","✍️","💅",
        "🤳","💪","🦵","🦶","👂","🦻","👃","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩",
        "🧓","👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","👮","🕵️","💂",
        "🥷","👷","🤴","👸","👳","👲","🧕","🤵","👰","🤰","🤱","👼","🎅","🤶","🦸","🦹",
        "🧙","🧚","🧛","🧜","🧝","🤝"
    ]
}

// MARK: - Skin tone

enum EmojiSkinTone: CaseIterable {
    case none, light, mediumLight, medium, mediumDark, dark

    /// The Unicode FITZ-1..5 modifier code-points.
    var modifier: String {
        switch self {
        case .none:         return ""
        case .light:        return "\u{1F3FB}"
        case .mediumLight:  return "\u{1F3FC}"
        case .medium:       return "\u{1F3FD}"
        case .mediumDark:   return "\u{1F3FE}"
        case .dark:         return "\u{1F3FF}"
        }
    }
}

enum EmojiSkinToneApplier {
    /// Append the FITZ modifier directly after the base emoji's first scalar.
    /// Stripping any existing FITZ modifier first so re-applying is idempotent.
    static func apply(tone: EmojiSkinTone, to emoji: String) -> String {
        let stripped = emoji.unicodeScalars.filter { scalar in
            // Drop existing FITZ modifiers if present
            !(scalar.value >= 0x1F3FB && scalar.value <= 0x1F3FF)
        }
        if tone == .none {
            return String(String.UnicodeScalarView(stripped))
        }
        // Insert modifier right after the first scalar (the base glyph).
        var result = ""
        var inserted = false
        for scalar in stripped {
            result.append(Character(scalar))
            if !inserted {
                result.append(tone.modifier)
                inserted = true
            }
        }
        return result
    }
}

// MARK: - Persistence (recents + skin tone) via App Group

enum EmojiPrefs {
    private static let suite = UserDefaults(suiteName: SharedSettings.appGroupID) ?? .standard
    private static let skinToneKey = "emoji_panel_skin_tone"

    static var skinTone: EmojiSkinTone {
        get {
            let raw = suite.integer(forKey: skinToneKey)
            return EmojiSkinTone.allCases.indices.contains(raw)
                ? EmojiSkinTone.allCases[raw]
                : .none
        }
        set {
            let idx = EmojiSkinTone.allCases.firstIndex(of: newValue) ?? 0
            suite.set(idx, forKey: skinToneKey)
        }
    }
}

enum EmojiRecents {
    private static let suite = UserDefaults(suiteName: SharedSettings.appGroupID) ?? .standard
    private static let key = "emoji_panel_recents"
    private static let maxCount = 32

    static func load() -> [String] {
        suite.stringArray(forKey: key) ?? []
    }

    static func markUsed(_ emoji: String) {
        var current = load()
        current.removeAll { $0 == emoji }
        current.insert(emoji, at: 0)
        if current.count > maxCount {
            current = Array(current.prefix(maxCount))
        }
        suite.set(current, forKey: key)
    }
}
