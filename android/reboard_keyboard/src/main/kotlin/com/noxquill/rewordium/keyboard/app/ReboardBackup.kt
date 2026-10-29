/*
 * Copyright (C) 2026 Noxquill
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
import com.noxquill.rewordium.keyboard.clipboardManager
import com.noxquill.rewordium.keyboard.ime.clipboard.ClipboardHistory
import com.noxquill.rewordium.keyboard.ime.clipboard.ClipboardManager
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardItem
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ItemType
import com.noxquill.rewordium.keyboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

/**
 * The keyboard's half of the Rewordium app backup: every keyboard setting,
 * custom layouts and themes, learned words and suggestions, and (only when
 * asked) the clipboard's text history. It reuses the same JetPref
 * export/import as the keyboard's own Backup & Restore screens, so restored
 * settings take effect at once instead of being overwritten by the running
 * store.
 */
object ReboardBackup {
    private const val LEARNED_WORDS = "learned_words.json"
    private const val LEARNED_BIGRAMS = "learned_bigrams.json"
    private const val LEARNED_EMOJI = "learned_emoji_associations.json"
    private const val CLIPBOARD_TEXT = "clipboard_text_items.json"

    /** What a keyboard backup folder holds, for the restore summary. */
    data class Contents(val settings: Boolean, val themes: Int, val layouts: Int, val learnedWords: Boolean, val clipboardItems: Int)

    private fun prefsFile(dir: File): File = dir
        .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
        .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")

    /** Writes the keyboard's data into [dir]. */
    suspend fun exportTo(context: Context, dir: File, includeClipboard: Boolean): Result<Contents> = runCatching {
        check(ReboardSettingsBridge.awaitReady(context)) { "The keyboard engine isn’t available on this device." }
        prefsFile(dir).parentFile?.mkdirs()
        FlorisPreferenceStore.export(FileBasedStorage(prefsFile(dir).path)).getOrThrow()
        withContext(Dispatchers.IO) {
            val files = dir.subDir("files")
            for (path in listOf(ExtensionManager.IME_KEYBOARD_PATH, ExtensionManager.IME_THEME_PATH)) {
                val source = context.filesDir.subDir(path)
                if (source.exists()) source.copyRecursively(files.subDir(path), overwrite = true)
            }
            for (name in listOf(LEARNED_WORDS, LEARNED_BIGRAMS, LEARNED_EMOJI)) {
                val source = context.filesDir.subFile(name)
                if (source.exists()) source.copyTo(files.subFile(name), overwrite = true)
            }
        }
        if (includeClipboard) {
            val texts = loadedClipboard(context).currentHistory.all.filter { it.type == ItemType.TEXT }
            withContext(Dispatchers.IO) {
                val clipboardDir = dir.subDir("clipboard").apply { mkdirs() }
                clipboardDir.subFile(CLIPBOARD_TEXT).writeJson(texts)
            }
        }
        describe(dir)
    }

    /** What a backup folder written by [exportTo] contains. */
    fun describe(dir: File): Contents {
        val files = dir.subDir("files")
        fun count(path: String) = files.subDir(path).listFiles()?.size ?: 0
        val clipboard = dir.subDir("clipboard").subFile(CLIPBOARD_TEXT)
        return Contents(
            settings = prefsFile(dir).exists(),
            themes = count(ExtensionManager.IME_THEME_PATH),
            layouts = count(ExtensionManager.IME_KEYBOARD_PATH),
            learnedWords = files.subFile(LEARNED_WORDS).exists() || files.subFile(LEARNED_BIGRAMS).exists(),
            clipboardItems = if (clipboard.exists()) runCatching { clipboard.readJson<List<ClipboardItem>>().size }.getOrDefault(0) else 0,
        )
    }

    /**
     * Restores a folder written by [exportTo]. Settings are replaced as a
     * whole, so the keyboard comes back exactly as it was; themes, layouts and
     * clipboard text are added alongside what's already here.
     */
    suspend fun importFrom(context: Context, dir: File): Result<Contents> = runCatching {
        check(ReboardSettingsBridge.awaitReady(context)) { "The keyboard engine isn’t available on this device." }
        val prefs = prefsFile(dir)
        if (prefs.exists()) {
            FlorisPreferenceStore.import(ImportStrategy.Erase, FileBasedStorage(prefs.path)).getOrThrow()
        }
        withContext(Dispatchers.IO) {
            val files = dir.subDir("files")
            for (path in listOf(ExtensionManager.IME_KEYBOARD_PATH, ExtensionManager.IME_THEME_PATH)) {
                val source = files.subDir(path)
                if (source.exists()) source.copyRecursively(context.filesDir.subDir(path), overwrite = true)
            }
            for (name in listOf(LEARNED_WORDS, LEARNED_BIGRAMS, LEARNED_EMOJI)) {
                val source = files.subFile(name)
                if (source.exists()) source.copyTo(context.filesDir.subFile(name), overwrite = true)
            }
        }
        val clipboardFile = dir.subDir("clipboard").subFile(CLIPBOARD_TEXT)
        if (clipboardFile.exists()) {
            val items = withContext(Dispatchers.IO) { clipboardFile.readJson<List<ClipboardItem>>() }
            loadedClipboard(context).restoreHistory(items = items.filter { it.type == ItemType.TEXT })
        }
        describe(dir)
    }

    /**
     * The clipboard manager once its history has loaded from the database (it
     * loads asynchronously at start-up), so exports aren't empty and restores
     * can skip items that are already there.
     */
    private suspend fun loadedClipboard(context: Context): ClipboardManager {
        val clipboard by context.clipboardManager()
        // An empty history never replaces EMPTY (equal values don't emit), so this simply times out then.
        withTimeoutOrNull(2_000) { clipboard.historyFlow.first { it !== ClipboardHistory.EMPTY } }
        return clipboard
    }
}
