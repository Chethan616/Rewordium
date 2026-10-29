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

package com.noxquill.rewordium.keyboard.ime.theme.studio

import android.annotation.SuppressLint
import android.content.Context
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.app.ReboardSettingsBridge
import com.noxquill.rewordium.keyboard.ime.theme.ThemeMode
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionComponentName
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionDefaults
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionManager
import com.noxquill.rewordium.keyboard.themeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installs [KeyboardThemeSpec]s as real keyboard themes: each becomes a theme extension (a `.flex`
 * archive with its manifest, generated stylesheet, font and the spec itself) in the keyboard's theme
 * folder, where the keyboard indexes it like any other theme. The spec travels inside the archive, so an
 * installed theme can be edited again or shared.
 */
object ThemeStudio {
    /** Extension ids of themes made in Rewordium start with this. */
    const val EXTENSION_PREFIX = "rewordium.theme."
    /** Extension id of the theme being tried on (never installed for good). */
    const val PREVIEW_ID = "rewordium.preview"
    private const val SPEC_FILE = "rewordium_theme.json"

    data class InstalledTheme(val componentId: String, val spec: KeyboardThemeSpec, val installedAt: Long)

    private val prefs by FlorisPreferenceStore
    /** The mascot to put back when a preview that brought its own ends; null while nothing is previewed. */
    @Volatile private var mascotBeforePreview: String? = null

    /** Installs [spec] and returns its theme id (`extension:theme`), which [apply] takes. */
    suspend fun install(context: Context, spec: KeyboardThemeSpec): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(ReboardSettingsBridge.awaitReady(context)) { "The keyboard engine isn’t available." }
            val clean = spec.sanitized()
            val slug = slug(clean.name)
            write(context, EXTENSION_PREFIX + slug, slug, clean)
        }
    }

    /** Installs [spec] (if needed) and makes it the keyboard's light or dark theme, switching mode so it shows. */
    suspend fun installAndApply(context: Context, spec: KeyboardThemeSpec): Result<String> =
        install(context, spec).mapCatching { id -> apply(context, id, spec.sanitized()); id }

    suspend fun apply(context: Context, componentId: String, spec: KeyboardThemeSpec) {
        val name = ExtensionComponentName.from(componentId)
        awaitIndexed(context, name)
        if (spec.night) prefs.theme.nightThemeId.set(name) else prefs.theme.dayThemeId.set(name)
        val mode = prefs.theme.mode.get()
        if (spec.night && mode == ThemeMode.ALWAYS_DAY) prefs.theme.mode.set(ThemeMode.ALWAYS_NIGHT)
        if (!spec.night && mode == ThemeMode.ALWAYS_NIGHT) prefs.theme.mode.set(ThemeMode.ALWAYS_DAY)
        spec.mascot?.let { prefs.theme.mascot.set(it) }
    }

    /**
     * Shows [spec] on the real keyboard without installing it (until [endPreview]): the user can try it in
     * any text field. Returns false when the keyboard engine isn't available.
     */
    suspend fun preview(context: Context, spec: KeyboardThemeSpec): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            check(ReboardSettingsBridge.awaitReady(context))
            val id = write(context, PREVIEW_ID, "preview", spec.sanitized())
            val name = ExtensionComponentName.from(id)
            awaitIndexed(context, name)
            withContext(Dispatchers.Main) { context.themeManager().value.previewThemeId.value = name }
            // The theme's buddy rides along while trying it.
            spec.mascot?.let { mascot ->
                if (mascotBeforePreview == null) mascotBeforePreview = prefs.theme.mascot.get()
                prefs.theme.mascot.set(mascot)
            }
        }.isSuccess
    }

    /**
     * Ends a preview from somewhere that can't wait (a screen being torn down, whose own scope is already
     * cancelled): the preview is cleared at once, the mascot put back in the background.
     */
    fun endPreviewDetached(context: Context) {
        val manager = context.themeManager().value
        if (manager.previewThemeId.value?.extensionId == PREVIEW_ID) manager.previewThemeId.value = null
        detachedScope.launch { endPreview(context) }
    }

    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun endPreview(context: Context) {
        runCatching {
            withContext(Dispatchers.Main) {
                val manager = context.themeManager().value
                if (manager.previewThemeId.value?.extensionId == PREVIEW_ID) manager.previewThemeId.value = null
            }
            mascotBeforePreview?.let { prefs.theme.mascot.set(it) }
            mascotBeforePreview = null
        }
    }

    /** Themes made in Rewordium (built, from the shop, Jade or the community), newest first. */
    suspend fun installed(context: Context): List<InstalledTheme> = withContext(Dispatchers.IO) {
        if (!ReboardSettingsBridge.awaitReady(context)) return@withContext emptyList()
        themeDir(context).listFiles { f -> f.name.startsWith(EXTENSION_PREFIX) && f.name.endsWith(".${ExtensionDefaults.FILE_EXTENSION}") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    ZipFile(file).use { zip ->
                        val entry = zip.getEntry(SPEC_FILE) ?: return@runCatching null
                        val spec = KeyboardThemeSpec.fromJson(zip.getInputStream(entry).bufferedReader().readText()) ?: return@runCatching null
                        val extId = file.name.removeSuffix(".${ExtensionDefaults.FILE_EXTENSION}")
                        InstalledTheme("$extId:${extId.removePrefix(EXTENSION_PREFIX)}", spec, file.lastModified())
                    }
                }.getOrNull()
            }
            .sortedByDescending { it.installedAt }
    }

    /** Removes an installed Rewordium theme; the keyboard falls back to the default look if it was in use. */
    suspend fun delete(context: Context, componentId: String) = withContext(Dispatchers.IO) {
        val name = ExtensionComponentName.from(componentId)
        require(name.extensionId.startsWith(EXTENSION_PREFIX)) { "Only themes made in Rewordium can be removed here." }
        if (prefs.theme.dayThemeId.get() == name) prefs.theme.dayThemeId.reset()
        if (prefs.theme.nightThemeId.get() == name) prefs.theme.nightThemeId.reset()
        File(themeDir(context), ExtensionDefaults.createFlexName(name.extensionId)).delete()
    }

    private fun themeDir(context: Context) = File(context.filesDir, ExtensionManager.IME_THEME_PATH).apply { mkdirs() }

    private fun slug(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24).ifBlank { "theme" }
        return base + "_" + java.lang.Long.toString(System.currentTimeMillis() % 1_000_000L, 36)
    }

    /** Writes the theme archive atomically (the keyboard watches the folder) and returns the theme id. */
    @SuppressLint("DiscouragedApi")
    private fun write(context: Context, extensionId: String, themeId: String, spec: KeyboardThemeSpec): String {
        val template = context.assets.open(KeyboardThemeFactory.TEMPLATE_ASSET).bufferedReader().use { it.readText() }
        val fontPath = KeyboardThemeFactory.fontPath(spec.font)
        val fontBytes = fontPath?.let {
            val res = context.resources.getIdentifier(spec.font.resource, "font", context.packageName)
            if (res == 0) null else context.resources.openRawResource(res).use { stream -> stream.readBytes() }
        }
        // Without the font file, fall back to the system font rather than a stylesheet pointing at nothing.
        val effective = if (fontPath != null && fontBytes == null) spec.copy(font = KeyFont.SYSTEM) else spec
        val stylesheet = KeyboardThemeFactory.stylesheet(effective, template)
        val manifest = JsonObject(mapOf(
            "$" to JsonPrimitive("ime.extension.theme"),
            "meta" to JsonObject(mapOf(
                "id" to JsonPrimitive(extensionId),
                "version" to JsonPrimitive("1.0.0"),
                "title" to JsonPrimitive(effective.name),
                "description" to JsonPrimitive("Made in the Rewordium theme shop"),
                "maintainers" to JsonArray(listOf(JsonPrimitive(effective.author.ifBlank { "Rewordium" }))),
                "license" to JsonPrimitive("apache-2.0"),
            )),
            "themes" to JsonArray(listOf(JsonObject(mapOf(
                "id" to JsonPrimitive(themeId),
                "label" to JsonPrimitive(effective.name),
                "authors" to JsonArray(listOf(JsonPrimitive(effective.author.ifBlank { "Rewordium" }))),
                "isNight" to JsonPrimitive(effective.night),
            )))),
        )).toString()

        val dir = themeDir(context)
        val target = File(dir, ExtensionDefaults.createFlexName(extensionId))
        val tmp = File(context.cacheDir, target.name + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            // The keyboard's unzip only creates folders that have their own entries.
            fun dir(name: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.closeEntry()
            }
            put(ExtensionDefaults.MANIFEST_FILE_NAME, manifest.toByteArray())
            dir("stylesheets/")
            if (fontPath != null && fontBytes != null) dir("fonts/")
            put("stylesheets/$themeId.json", stylesheet.toByteArray())
            put(SPEC_FILE, effective.toJson().toByteArray())
            if (fontPath != null && fontBytes != null) put(fontPath, fontBytes)
        }
        // Copy then rename inside the watched folder, so the keyboard only ever sees a complete archive.
        val staged = File(dir, ".${target.name}.part")
        tmp.copyTo(staged, overwrite = true)
        tmp.delete()
        if (!staged.renameTo(target)) {
            target.delete()
            check(staged.renameTo(target)) { "Couldn’t save the theme." }
        }
        return "$extensionId:$themeId"
    }

    /** Waits (briefly) until the keyboard has indexed [name], so selecting it never falls back. */
    private suspend fun awaitIndexed(context: Context, name: ExtensionComponentName) {
        val manager = withContext(Dispatchers.Main) { context.themeManager().value }
        withTimeoutOrNull(4_000) {
            while (manager.indexedThemeConfigs.value.first[name] == null) delay(80)
        }
        // A theme that was replaced in place keeps its id; give the index a moment to pick up the new file.
        delay(120)
    }
}
