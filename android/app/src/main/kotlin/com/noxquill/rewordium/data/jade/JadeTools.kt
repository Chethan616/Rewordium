package com.noxquill.rewordium.data.jade

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.noxquill.rewordium.keyboard.app.ReboardLanguages
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge.Kind
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge.Setting
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import com.noxquill.rewordium.keyboard.ime.theme.studio.ThemeStudio

/** What the tools need from the conversation: progress, undo cards, approvals and links. */
interface JadeToolHost {
    fun status(text: String)
    suspend fun applied(changes: List<SettingChangeRecord>)
    suspend fun approve(proposals: List<SettingProposal>): Boolean
    fun suggestScreen(path: String, label: String)
}

/**
 * The functions Jade can call. Together they reach every ReBoard keyboard
 * preference (including hidden and developer ones) plus Rewordium's own
 * settings. Results are short plain text so they fit small model budgets.
 */
class JadeTools(private val context: Context) {

    val schemas: JsonArray by lazy { JsonParser.parseString(SCHEMAS).asJsonArray }

    suspend fun execute(name: String, rawArguments: String?, host: JadeToolHost): String {
        val args = runCatching { JsonParser.parseString(rawArguments?.ifBlank { "{}" } ?: "{}").asJsonObject }.getOrElse { JsonObject() }
        return runCatching {
            when (name) {
                "find_settings" -> findSettings(args.str("query").ifBlank { "all" }, host)
                "change_settings" -> changeSettings(args, host)
                "reset_settings" -> resetSettings(args, host)
                "list_keyboard_themes" -> listThemes(host)
                "manage_keyboard_languages" -> manageLanguages(args.str("action"), args.str("language"), host)
                "open_settings_screen" -> openScreen(args.str("screen"), host)
                "create_keyboard_theme" -> createTheme(args, host)
                else -> "Unknown tool \"$name\"."
            }
        }.getOrElse { "Tool error: ${it.message ?: it.javaClass.simpleName}" }
    }

    private suspend fun everything(): List<Setting> =
        ReboardSettingsBridge.all(context) + AppSettingsCatalog.all(context)

    private suspend fun findSettings(query: String, host: JadeToolHost): String {
        host.status("Looking through your settings…")
        val all = everything()
        val q = query.trim().lowercase()
        if (q == "all" || q == "*" || q == "overview") {
            return buildString {
                appendLine("Setting groups (search a topic, or use group:<name> to list one):")
                all.groupBy { it.group }.forEach { (group, items) ->
                    appendLine("- $group (${items.size}): ${items.take(4).joinToString { it.title }}")
                }
            }
        }
        if (q.startsWith("group:")) {
            val group = q.removePrefix("group:").trim()
            val items = all.filter { it.group == group }
            if (items.isEmpty()) return "No group \"$group\". Groups: ${all.map { it.group }.distinct().joinToString()}."
            return items.take(45).joinToString("\n") { line(it) }
        }
        val terms = expand(q)
        val scored = all.map { setting -> setting to score(setting, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(12)
        if (scored.isEmpty()) return "No settings matched \"$query\". Try other words, or find_settings with \"all\"."
        return scored.joinToString("\n") { line(it.first) }
    }

    private suspend fun changeSettings(args: JsonObject, host: JadeToolHost): String {
        val requested = args.getAsJsonArray("changes")?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val key = obj.str("key").trim().lowercase()
            val value = obj.get("value")?.let { if (it.isJsonPrimitive) it.asString else it.toString() }.orEmpty()
            if (key.isBlank()) null else key to value
        }.orEmpty()
        if (requested.isEmpty()) return "No changes were given."
        host.status("Tuning your settings…")
        val known = everything().associateBy { it.key }
        val unknown = requested.filter { it.first !in known }
        val (advanced, normal) = requested.filter { it.first in known }.partition { known.getValue(it.first).advanced }
        val lines = mutableListOf<String>()
        unknown.forEach { lines += "${it.first}: not a real setting. Use find_settings to get exact keys." }
        val allowedAdvanced = if (advanced.isEmpty()) emptyList() else {
            val ok = host.approve(advanced.map { (key, value) ->
                val setting = known.getValue(key)
                SettingProposal(key, setting.title, setting.value, value)
            })
            if (!ok) advanced.forEach { lines += "${it.first}: the user declined this advanced change. Don't retry it." }
            if (ok) advanced else emptyList()
        }
        val applied = mutableListOf<SettingChangeRecord>()
        for ((key, value) in normal + allowedAdvanced) {
            val result = if (AppSettingsCatalog.owns(key)) AppSettingsCatalog.set(context, key, value)
            else ReboardSettingsBridge.set(context, key, value)
            result.onSuccess { change ->
                if (change.previous != change.current) applied += SettingChangeRecord(change.key, change.title, change.previous, change.current, change.note, change.restore)
                lines += "${change.key}: ${change.previous} → ${change.current}" + (change.note?.let { " ($it)" } ?: "") +
                    if (change.previous == change.current) " (already set)" else ""
            }.onFailure { lines += "$key: failed — ${it.message}" }
        }
        if (applied.isNotEmpty()) host.applied(applied)
        return lines.joinToString("\n")
    }

    private suspend fun resetSettings(args: JsonObject, host: JadeToolHost): String {
        val keys = args.getAsJsonArray("keys")?.mapNotNull { runCatching { it.asString.trim().lowercase() }.getOrNull() }.orEmpty()
        if (keys.isEmpty()) return "No keys were given."
        host.status("Restoring defaults…")
        val known = everything().associateBy { it.key }
        val advanced = keys.filter { known[it]?.advanced == true }
        val allowed = if (advanced.isEmpty()) true else host.approve(advanced.map {
            val setting = known.getValue(it)
            SettingProposal(it, setting.title, setting.value, setting.defaultValue)
        })
        val lines = mutableListOf<String>()
        val applied = mutableListOf<SettingChangeRecord>()
        for (key in keys) {
            if (key !in known) { lines += "$key: not a real setting."; continue }
            if (key in advanced && !allowed) { lines += "$key: the user declined this advanced change."; continue }
            val result = if (AppSettingsCatalog.owns(key)) AppSettingsCatalog.reset(context, key) else ReboardSettingsBridge.reset(context, key)
            result.onSuccess { change ->
                if (change.previous != change.current) applied += SettingChangeRecord(change.key, change.title, change.previous, change.current, restore = change.restore)
                lines += "${change.key}: reset to ${change.current}"
            }.onFailure { lines += "$key: failed — ${it.message}" }
        }
        if (applied.isNotEmpty()) host.applied(applied)
        return lines.joinToString("\n")
    }

    private suspend fun listThemes(host: JadeToolHost): String {
        host.status("Browsing keyboard themes…")
        val themes = ReboardSettingsBridge.themes(context)
        if (themes.isEmpty()) return "No themes are installed yet."
        return "Use these ids for theme__day_theme_id / theme__night_theme_id:\n" +
            themes.joinToString("\n") { "${it.id} | ${it.label} | ${if (it.night) "dark" else "light"}" }
    }

    private suspend fun manageLanguages(action: String, language: String, host: JadeToolHost): String {
        fun describe(list: List<ReboardLanguages.Language>) =
            list.joinToString("\n") { "${it.name} (${it.tag}, ${it.layout})" + if (it.active) " — active" else "" }
        suspend fun find(): ReboardLanguages.Language? = ReboardLanguages.list(context).firstOrNull {
            it.tag.equals(language, ignoreCase = true) || it.name.equals(language, ignoreCase = true) || it.name.contains(language, ignoreCase = true)
        }
        return when (action.lowercase()) {
            "available" -> {
                host.status("Browsing keyboard languages…")
                val options = ReboardLanguages.options(context)
                val filtered = if (language.isBlank()) options else options.filter { it.name.contains(language, true) || it.tag.contains(language, true) }
                if (filtered.isEmpty()) "No matching languages to add." else filtered.take(40).joinToString("\n") { "${it.name} (${it.tag}, ${it.layout})" }
            }
            "add" -> {
                host.status("Adding a keyboard language…")
                ReboardLanguages.add(context, language).fold(
                    onSuccess = { "Added ${it.name} (${it.layout}). Users switch languages with the globe key or space bar." },
                    onFailure = { "Couldn’t add it: ${it.message}" },
                )
            }
            "remove" -> {
                val target = find() ?: return "That language isn’t on the keyboard. Current:\n${describe(ReboardLanguages.list(context))}"
                ReboardLanguages.remove(context, target.id).fold({ "Removed ${target.name}." }, { "Couldn’t remove it: ${it.message}" })
            }
            "switch" -> {
                val target = find() ?: return "That language isn’t on the keyboard. Current:\n${describe(ReboardLanguages.list(context))}"
                ReboardLanguages.activate(context, target.id).fold({ "Switched the keyboard to ${target.name}." }, { "Couldn’t switch: ${it.message}" })
            }
            else -> describe(ReboardLanguages.list(context)).ifBlank { "No languages yet." }
        }
    }

    /** Builds a keyboard theme from Jade's choices, installs it and switches the keyboard to it (undoable). */
    private suspend fun createTheme(args: JsonObject, host: JadeToolHost): String {
        host.status("Designing your keyboard theme…")
        val spec = KeyboardThemeSpec.fromJson(args.toString())?.readable()?.copy(author = "Jade")
            ?: return "Couldn't read that theme. Send name, night, background, keys, letters and accent as #RRGGBB."
        val key = if (spec.night) "theme__night_theme_id" else "theme__day_theme_id"
        val before = ReboardSettingsBridge.all(context).firstOrNull { it.key == key }
        val id = ThemeStudio.installAndApply(context, spec).getOrElse { return "Couldn't install the theme: ${it.message}" }
        if (before != null) {
            host.applied(listOf(SettingChangeRecord(key, if (spec.night) "Dark keyboard theme" else "Light keyboard theme", before.value, spec.name, restore = before.value)))
        }
        return "Installed and applied \"${spec.name}\" (${if (spec.night) "dark" else "light"}, id $id). Tell the user it's on their keyboard now and that the Theme shop in the Keys tab has more."
    }

    private fun openScreen(screen: String, host: JadeToolHost): String {
        val label = ReboardSettingsBridge.screens[screen]
            ?: return "Unknown screen. Options: ${ReboardSettingsBridge.screens.keys.joinToString()}."
        host.suggestScreen(screen, label)
        return "A button that opens \"$label\" is now shown under your reply."
    }

    private fun line(setting: Setting): String = buildString {
        append(setting.key).append(" | ").append(setting.title)
        append(" | ")
        append(
            when (setting.kind) {
                Kind.BOOLEAN -> "on/off"
                Kind.CHOICE -> "one of ${setting.options.joinToString(",")}"
                Kind.COLOR -> "colour #RRGGBB or dynamic"
                Kind.TIME -> "time HH:MM"
                Kind.THEME -> "theme id (list_keyboard_themes)"
                Kind.DATA -> "raw data (JSON)"
                Kind.TEXT -> "text"
                else -> {
                    val min = setting.min
                    val max = setting.max
                    val range = if (min != null && max != null) " ${trim(min)}..${trim(max)}" else ""
                    val step = setting.step?.let { " step ${trim(it)}" }.orEmpty()
                    "number$range$step"
                }
            },
        )
        append(" | now ").append(setting.value.take(80))
        append(" | default ").append(setting.defaultValue.take(40))
        if (setting.advanced) append(" | advanced")
        setting.summary?.takeIf { it.isNotBlank() }?.let { append(" | ").append(it.take(90)) }
    }

    private fun trim(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun score(setting: Setting, terms: List<String>): Int {
        val key = setting.key.replace('_', ' ')
        val title = setting.title.lowercase()
        val summary = setting.summary.orEmpty().lowercase()
        val options = setting.options.joinToString(" ").lowercase().replace('_', ' ')
        return terms.sumOf { term ->
            var s = 0
            if (key.contains(term)) s += 3
            if (title.contains(term)) s += 3
            if (setting.group == term) s += 2
            if (summary.contains(term)) s += 1
            if (options.contains(term)) s += 1
            s
        }
    }

    private fun expand(query: String): List<String> {
        val words = query.replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 1 && it !in STOP_WORDS }
        val expanded = words.toMutableSet()
        SYNONYMS.forEach { (trigger, extra) -> if (query.contains(trigger)) expanded += extra }
        return expanded.toList()
    }

    companion object {
        private val STOP_WORDS = setOf("the", "my", "a", "an", "to", "of", "and", "on", "off", "in", "it", "for", "make", "turn", "set", "please", "keyboard", "settings", "setting", "change", "more", "less", "bit", "is", "be")

        private val SYNONYMS = listOf(
            "dark" to listOf("theme", "night", "mode"), "light" to listOf("theme", "day"), "night" to listOf("theme"),
            "colour" to listOf("accent", "color"), "color" to listOf("accent"),
            "sound" to listOf("audio"), "click" to listOf("audio"), "volume" to listOf("audio"), "noise" to listOf("audio"),
            "vibrat" to listOf("haptic"), "buzz" to listOf("haptic"), "haptic" to listOf("haptic"),
            "tall" to listOf("height"), "short" to listOf("height"), "bigger" to listOf("height", "font"), "smaller" to listOf("height", "font"),
            "size" to listOf("height", "font"), "text size" to listOf("font"),
            "swipe typing" to listOf("glide"), "gesture typing" to listOf("glide"), "slide" to listOf("glide"), "trail" to listOf("glide"),
            "number" to listOf("number row", "hinted number"), "digit" to listOf("number row"),
            "spell" to listOf("spelling", "correct"), "autocorrect" to listOf("auto correct", "correction"), "typo" to listOf("correct"),
            "suggest" to listOf("suggestion", "candidates"), "predict" to listOf("suggestion"),
            "space" to listOf("space bar"), "cursor" to listOf("space bar swipe", "cursor"),
            "one hand" to listOf("one handed"), "one-hand" to listOf("one handed"),
            "capital" to listOf("capitalization", "caps"), "caps" to listOf("capitalization"),
            "full stop" to listOf("double space period"), "period" to listOf("double space period"),
            "incognito" to listOf("incognito"), "private" to listOf("incognito"),
            "emoji" to listOf("emoji"), "clipboard" to listOf("clipboard"), "copy" to listOf("clipboard"),
            "popup" to listOf("popup"), "long press" to listOf("long press"), "delay" to listOf("delay"),
            "avatar" to listOf("app avatar"), "mascot" to listOf("app avatar"), "palette" to listOf("app palette"),
            "app theme" to listOf("app theme mode", "app palette"), "persona" to listOf("persona"), "voice" to listOf("persona"),
            "toolbar" to listOf("smartbar"), "smart bar" to listOf("smartbar"), "learn" to listOf("learn personal words", "dictionary"),
            "contact" to listOf("contacts"), "language" to listOf("localization", "subtype"),
        )

        private fun JsonObject.str(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

        private val SCHEMAS = """
        [
          {"type":"function","function":{
            "name":"find_settings",
            "description":"Search every ReBoard keyboard setting (including hidden and developer ones) and Rewordium app setting. Returns lines: key | title | type and allowed values | current value | default.",
            "parameters":{"type":"object","properties":{
              "query":{"type":"string","description":"Words for what the user wants, e.g. 'keyboard height', 'vibration strength', 'dark theme', 'number row'. Use 'all' for an overview of groups or 'group:<name>' to list one group."}
            },"required":["query"]}}},
          {"type":"function","function":{
            "name":"change_settings",
            "description":"Change one or more settings at once. Values are strings: true/false, numbers, an option name, #RRGGBB colours or 'dynamic', HH:MM times, or a theme id.",
            "parameters":{"type":"object","properties":{
              "changes":{"type":"array","items":{"type":"object","properties":{
                "key":{"type":"string","description":"Exact key from find_settings"},
                "value":{"type":"string"}
              },"required":["key","value"]}}
            },"required":["changes"]}}},
          {"type":"function","function":{
            "name":"reset_settings",
            "description":"Restore settings to their default values.",
            "parameters":{"type":"object","properties":{
              "keys":{"type":"array","items":{"type":"string"}}
            },"required":["keys"]}}},
          {"type":"function","function":{
            "name":"list_keyboard_themes",
            "description":"List installed keyboard themes with their ids, for theme__day_theme_id and theme__night_theme_id.",
            "parameters":{"type":"object","properties":{}}}},
          {"type":"function","function":{
            "name":"manage_keyboard_languages",
            "description":"Keyboard languages: list the ones added, see which can be added, add one, remove one, or switch the active one.",
            "parameters":{"type":"object","properties":{
              "action":{"type":"string","enum":["list","available","add","remove","switch"]},
              "language":{"type":"string","description":"Language name or tag, e.g. 'Hindi' or 'hi-IN'. For 'available' it filters the list."}
            },"required":["action"]}}},
          {"type":"function","function":{
            "name":"create_keyboard_theme",
            "description":"Design a new keyboard theme and switch the keyboard to it. Use for 'make my keyboard look like…', 'a theme for…', colours, moods, brands or vibes. Pick harmonious colours with strong contrast between letters and keys.",
            "parameters":{"type":"object","properties":{
              "name":{"type":"string","description":"Short catchy theme name"},
              "night":{"type":"boolean","description":"true for a dark theme"},
              "background":{"type":"string","description":"#RRGGBB behind the keys"},
              "keys":{"type":"string","description":"#RRGGBB letter keys"},
              "letters":{"type":"string","description":"#RRGGBB text on keys, high contrast"},
              "accent":{"type":"string","description":"#RRGGBB enter key and highlights"},
              "special":{"type":"string","description":"#RRGGBB shift/delete/symbol keys"},
              "shape":{"type":"string","enum":["SQUARE","ROUNDED","SOFT","PILL","BUBBLE","LEAF","CUT","TICKET"]},
              "pressed_shape":{"type":"string","enum":["SQUARE","ROUNDED","SOFT","PILL","BUBBLE","LEAF","CUT","TICKET"]},
              "font":{"type":"string","enum":["SYSTEM","ROUNDED","SOFT","MONO","SERIF"]},
              "bold":{"type":"boolean"},
              "outline":{"type":"boolean"},
              "shadow":{"type":"integer","minimum":0,"maximum":4},
              "mascot":{"type":"string","description":"A bot on the space bar: clover, flower, triangle, square, blob, ghost, circle, drop, star, droid, mech, alien, hexagon, cat, cloud, pill, pebble, puddle; omit for none"}
            },"required":["name","night","background","keys","letters","accent"]}}},
          {"type":"function","function":{
            "name":"open_settings_screen",
            "description":"Show the user a button that opens a ReBoard settings screen, for things that need hands-on editing (adding languages, editing theme colours, dictionary words, backups, stickers).",
            "parameters":{"type":"object","properties":{
              "screen":{"type":"string","enum":[${ReboardSettingsBridge.screens.keys.joinToString(",") { "\"$it\"" }}]}
            },"required":["screen"]}}}
        ]
        """.trimIndent()
    }
}
