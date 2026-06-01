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

package com.noxquill.rewordium.keyboard.ime.media.gif

import android.content.Context
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

/**
 * Persistent reactive store for GIF recents and favorites. Stores enough
 * metadata about each GIF (URLs, dimensions, description) that the UI can
 * render thumbnails without a KLIPY round-trip on every panel open —
 * critical for the recents tab where the user expects an instant grid.
 *
 * Two flavors live under one class:
 *  - `RECENTS`  — LRU-capped at [RECENTS_CAP]; the newest commit lands at
 *                 the top and the oldest entry falls off the bottom.
 *  - `FAVORITES`— unbounded; the user explicitly adds via long-press.
 *
 * Both surfaces share the same serialization shape so we can swap entries
 * between them without re-fetching KLIPY metadata.
 *
 * Singleton pattern (`get(context, kind)`): the GIF panel and the search
 * overlay both compose against the same instance, so favoriting in one
 * surface shows up in the other instantly via [entriesFlow].
 */
class GifCollectionStore private constructor(
    private val appContext: Context,
    private val kind: Kind,
) {

    enum class Kind(val fileName: String, val cap: Int) {
        RECENTS("gif_recents.json", RECENTS_CAP),
        FAVORITES("gif_favorites.json", Int.MAX_VALUE),
    }

    @Serializable
    data class Entry(
        val id: String,
        val gifUrl: String,
        val previewUrl: String,
        val width: Int,
        val height: Int,
        val description: String,
        /** Last-touched epoch seconds. Drives recency sort. */
        val t: Long,
    )

    private val mutex = Mutex()
    @Volatile private var loaded = false

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entriesFlow: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val manifestFile: File by lazy {
        File(appContext.filesDir, kind.fileName)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Entry.serializer())

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
                flogError { "GifCollectionStore($kind): load failed ($e), starting empty" }
            }
            loaded = true
        }
    }

    fun contains(id: String): Boolean = _entries.value.any { it.id == id }

    suspend fun add(result: KlipyClient.GifResult) = withContext(Dispatchers.IO) {
        ensureLoaded()
        val now = System.currentTimeMillis() / 1000
        val entry = Entry(
            id = result.id,
            gifUrl = result.gifUrl,
            previewUrl = result.previewUrl,
            width = result.width,
            height = result.height,
            description = result.contentDescription,
            t = now,
        )
        mutex.withLock {
            val withoutDup = _entries.value.filterNot { it.id == entry.id }
            val capped = (listOf(entry) + withoutDup).take(kind.cap)
            _entries.value = capped
            writeManifest()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            val next = _entries.value.filterNot { it.id == id }
            if (next.size == _entries.value.size) return@withLock
            _entries.value = next
            writeManifest()
        }
    }

    /** Toggle membership. Returns true if the item is now in the store. */
    suspend fun toggle(result: KlipyClient.GifResult): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (contains(result.id)) {
            remove(result.id)
            false
        } else {
            add(result)
            true
        }
    }

    private fun writeManifest() {
        try {
            val tmp = File(manifestFile.parentFile, "${manifestFile.name}.tmp")
            tmp.writeText(json.encodeToString(listSerializer, _entries.value), Charsets.UTF_8)
            if (!tmp.renameTo(manifestFile)) {
                manifestFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            flogError { "GifCollectionStore($kind): manifest write failed ($e)" }
        }
    }

    companion object {
        private const val RECENTS_CAP = 30

        @Volatile private var recentsInstance: GifCollectionStore? = null
        @Volatile private var favoritesInstance: GifCollectionStore? = null

        fun recents(context: Context): GifCollectionStore {
            val existing = recentsInstance
            if (existing != null) return existing
            return synchronized(this) {
                recentsInstance ?: GifCollectionStore(
                    context.applicationContext, Kind.RECENTS,
                ).also { recentsInstance = it }
            }
        }

        fun favorites(context: Context): GifCollectionStore {
            val existing = favoritesInstance
            if (existing != null) return existing
            return synchronized(this) {
                favoritesInstance ?: GifCollectionStore(
                    context.applicationContext, Kind.FAVORITES,
                ).also { favoritesInstance = it }
            }
        }
    }
}

/** Helper to project a stored entry back into the shape the grid renders. */
internal fun GifCollectionStore.Entry.toGifResult(): KlipyClient.GifResult = KlipyClient.GifResult(
    id = id,
    gifUrl = gifUrl,
    previewUrl = previewUrl,
    width = width,
    height = height,
    contentDescription = description,
)
