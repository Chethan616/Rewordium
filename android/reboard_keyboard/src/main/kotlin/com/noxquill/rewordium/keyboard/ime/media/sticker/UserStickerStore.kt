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

package com.noxquill.rewordium.keyboard.ime.media.sticker

import android.content.Context
import android.net.Uri
import com.noxquill.rewordium.keyboard.lib.devtools.flogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Persistent store of user-imported stickers — images the user explicitly
 * adds via the sticker panel's "+" tile or by long-pressing a clipboard
 * suggestion. Each sticker is a single image file in
 * `filesDir/user_stickers/{uuid}.{ext}` with a manifest at
 * `filesDir/user_stickers/manifest.json`.
 *
 * Atomic writes (tmp + rename) protect the manifest against a process kill
 * mid-write. Reads return a defensive copy so callers can iterate without
 * holding the mutex.
 */
class UserStickerStore(private val context: Context) {

    @Serializable
    data class Entry(
        /** Stable UUID — used as the filename stem. */
        val id: String,
        /** "webp" or "png" or "jpg" — the on-disk extension. */
        val ext: String,
        /** MIME type, e.g. "image/webp" — captured at import time. */
        val mime: String,
        /** Last-touched epoch seconds. Drives recency sort. */
        val t: Long,
        /** User-created tags/categories for this sticker. */
        val tags: List<String> = emptyList(),
    )

    private val mutex = Mutex()
    @Volatile private var loaded = false
    // Reactive so the panel re-renders the moment an import completes via the
    // [StickerImportActivity] trampoline (which has no other way to notify
    // the IME UI it just woke up the store).
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entriesFlow: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val entries: List<Entry> get() = _entries.value

    private val dir: File by lazy {
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }
    }
    private val manifestFile: File by lazy { File(dir, MANIFEST_NAME) }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Entry.serializer())

    /** Idempotent. Reads the manifest off disk on first call. */
    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            try {
                if (manifestFile.exists()) {
                    val text = manifestFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        _entries.value = json.decodeFromString(listSerializer, text)
                    }
                }
            } catch (e: Exception) {
                flogError { "UserStickerStore: load failed ($e), starting empty" }
            }
            loaded = true
        }
    }

    /** Defensive copy of the current manifest, sorted newest-first. */
    fun snapshot(): List<Entry> {
        val current = entries
        return current.sortedByDescending { it.t }
    }

    /** Resolve [Entry] → on-disk [File]. */
    fun fileFor(entry: Entry): File = File(dir, "${entry.id}.${entry.ext}")

    /**
     * Copies the image at [sourceUri] into the store, returning the new
     * [Entry] on success. [mimeType] should be the source content type as
     * advertised by `contentResolver.getType(uri)`; we use it to pick an
     * extension and as the manifest's `mime` field.
     *
     * Heavy I/O runs on [Dispatchers.IO]. The manifest write is atomic
     * (tmp + rename) so a process kill leaves either the previous manifest
     * or the new one — never a partial.
     */
    suspend fun import(sourceUri: Uri, mimeType: String, tags: List<String> = emptyList()): Entry? = withContext(Dispatchers.IO) {
        ensureLoaded()
        val ext = mimeToExt(mimeType)
        val id = UUID.randomUUID().toString()
        val outFile = File(dir, "$id.$ext")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
        } catch (e: Exception) {
            flogError { "UserStickerStore.import failed: $e" }
            outFile.delete()
            return@withContext null
        }
        val entry = Entry(id, ext, mimeType, System.currentTimeMillis() / 1000, tags)
        mutex.withLock {
            _entries.value = _entries.value + entry
            writeManifest()
        }
        entry
    }

    /** Remove a sticker. Best-effort: continues even if the file is gone. */
    suspend fun remove(entry: Entry) = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            _entries.value = _entries.value.filterNot { it.id == entry.id }
            writeManifest()
        }
        runCatching { fileFor(entry).delete() }
        Unit
    }

    /** Bump the recency timestamp of a sticker that was just committed. */
    suspend fun touch(entry: Entry) = withContext(Dispatchers.IO) {
        ensureLoaded()
        val now = System.currentTimeMillis() / 1000
        mutex.withLock {
            _entries.value = _entries.value.map { if (it.id == entry.id) it.copy(t = now) else it }
            writeManifest()
        }
    }

    private fun writeManifest() {
        try {
            val tmp = File(dir, "$MANIFEST_NAME.tmp")
            tmp.writeText(json.encodeToString(listSerializer, entries), Charsets.UTF_8)
            if (!tmp.renameTo(manifestFile)) {
                manifestFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            flogError { "UserStickerStore: manifest write failed ($e)" }
        }
    }

    private fun mimeToExt(mime: String): String = when {
        mime.endsWith("webp", ignoreCase = true) -> "webp"
        mime.endsWith("png", ignoreCase = true) -> "png"
        mime.endsWith("gif", ignoreCase = true) -> "gif"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
        else -> "webp"
    }

    companion object {
        private const val DIR_NAME = "user_stickers"
        private const val MANIFEST_NAME = "manifest.json"

        // Process-wide singleton so the [StickerImportActivity] (in a
        // separate Activity context) and the IME's sticker panel (in the
        // IME service context) share the same StateFlow — imports show up
        // in the grid instantly without a panel re-open.
        @Volatile private var INSTANCE: UserStickerStore? = null

        fun get(context: Context): UserStickerStore {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                INSTANCE ?: UserStickerStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
