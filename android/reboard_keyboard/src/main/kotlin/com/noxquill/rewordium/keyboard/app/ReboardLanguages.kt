/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noxquill.rewordium.keyboard.app

import android.content.Context
import com.noxquill.rewordium.keyboard.ime.core.Subtype
import com.noxquill.rewordium.keyboard.ime.core.SubtypeJsonConfig
import com.noxquill.rewordium.keyboard.ime.core.SubtypePreset
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The keyboard's languages (subtypes) in plain values, for the host app's
 * keyboard page and for Jade: list, add from the built-in presets, switch
 * and remove — without opening ReBoard's own settings.
 */
object ReboardLanguages {
    data class Language(val id: Long, val tag: String, val name: String, val layout: String, val active: Boolean)

    data class Option(val tag: String, val name: String, val layout: String)

    private val prefs by FlorisPreferenceStore

    /** Decodes the stored language list; cheap enough to call on every change of the two prefs. */
    fun decode(raw: String, activeId: Long): List<Language> {
        val subtypes = runCatching {
            if (raw.isBlank()) emptyList() else SubtypeJsonConfig.decodeFromString<List<Subtype>>(raw)
        }.getOrDefault(emptyList())
        val effectiveActive = subtypes.firstOrNull { it.id == activeId }?.id ?: subtypes.firstOrNull()?.id
        return subtypes.map { it.toLanguage(it.id == effectiveActive) }
    }

    suspend fun list(context: Context): List<Language> {
        if (!ReboardSettingsBridge.awaitReady(context)) return emptyList()
        return decode(prefs.localization.subtypes.get(), prefs.localization.activeSubtypeId.get())
    }

    /** Languages that can be added (everything with a built-in preset that isn't already added). */
    suspend fun options(context: Context): List<Option> {
        val existing = list(context).map { it.tag.lowercase() }.toSet()
        return presets(context)
            .map { Option(it.locale.languageTag(), it.locale.displayName(), layoutName(it.preferred.characters.componentId)) }
            .filter { it.tag.lowercase() !in existing }
            .distinctBy { it.tag }
            .sortedBy { it.name }
    }

    suspend fun add(context: Context, tagOrName: String): Result<Language> = runCatching {
        check(ReboardSettingsBridge.awaitReady(context)) { UNAVAILABLE }
        val wanted = tagOrName.trim()
        val all = presets(context)
        val preset = all.firstOrNull { it.locale.languageTag().equals(wanted, ignoreCase = true) }
            ?: all.firstOrNull { it.locale.displayName().equals(wanted, ignoreCase = true) }
            ?: all.firstOrNull { it.locale.displayName().contains(wanted, ignoreCase = true) }
            ?: error("No keyboard is available for \"$wanted\".")
        val added = withContext(Dispatchers.Main) { context.subtypeManager().value.addSubtype(preset.toSubtype()) }
        if (!added) error("${preset.locale.displayName()} is already on your keyboard.")
        // The manager saves asynchronously; wait for the new language to land.
        withTimeoutOrNull(2_000) {
            while (list(context).none { it.tag.equals(preset.locale.languageTag(), ignoreCase = true) }) delay(50)
        }
        list(context).last { it.tag.equals(preset.locale.languageTag(), ignoreCase = true) }
    }

    suspend fun remove(context: Context, id: Long): Result<Unit> = runCatching {
        check(ReboardSettingsBridge.awaitReady(context)) { UNAVAILABLE }
        val manager = withContext(Dispatchers.Main) { context.subtypeManager().value }
        // The manager loads its list asynchronously the first time it starts.
        withTimeoutOrNull(2_000) { while (manager.subtypes.none { it.id == id }) delay(50) }
        val subtype = manager.subtypes.firstOrNull { it.id == id } ?: error("That language isn't on your keyboard.")
        check(manager.subtypes.size > 1) { "Your keyboard needs at least one language." }
        manager.removeSubtype(subtype)
    }

    suspend fun activate(context: Context, id: Long): Result<Unit> = runCatching {
        check(ReboardSettingsBridge.awaitReady(context)) { UNAVAILABLE }
        val manager = withContext(Dispatchers.Main) { context.subtypeManager().value }
        withTimeoutOrNull(2_000) { while (manager.subtypes.none { it.id == id }) delay(50) }
        check(manager.subtypes.any { it.id == id }) { "That language isn't on your keyboard." }
        manager.switchToSubtypeById(id)
        Unit
    }

    private const val UNAVAILABLE = "Keyboard languages aren’t available on this device right now."

    private suspend fun presets(context: Context): List<SubtypePreset> {
        if (!ReboardSettingsBridge.awaitReady(context)) return emptyList()
        // Keyboard resources observe LiveData, which must start on the main thread.
        val manager = withContext(Dispatchers.Main) { context.keyboardManager().value }
        return withTimeoutOrNull(5_000) {
            var presets = manager.resources.subtypePresets.value.orEmpty()
            while (presets.isEmpty()) {
                delay(100)
                presets = manager.resources.subtypePresets.value.orEmpty()
            }
            presets
        }.orEmpty()
    }

    private fun Subtype.toLanguage(active: Boolean) = Language(
        id = id,
        tag = primaryLocale.languageTag(),
        name = primaryLocale.displayName(),
        layout = layoutName(layoutMap.characters.componentId),
        active = active,
    )

    private fun layoutName(id: String): String = when (id.lowercase()) {
        "qwerty", "qwertz", "azerty", "colemak", "dvorak", "workman", "bepo" -> id.uppercase()
        else -> id.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}
