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
 * Reference to a sticker — discriminated union so the same recents /
 * favorites store can hold a mix of user-imported stickers (referenced by
 * UUID into [UserStickerStore]) and WhatsApp pack stickers (referenced by
 * the WhatsApp content URI). When WhatsApp gets uninstalled or a user
 * sticker is deleted, the dangling reference is silently filtered out at
 * read time rather than purged eagerly — that way a future reinstall
 * brings the favorite back.
 */
@Serializable
sealed class StickerRef {
    abstract val key: String

    @Serializable
    data class User(val id: String) : StickerRef() {
        override val key: String get() = "u:$id"
    }

    @Serializable
    data class WhatsApp(val uri: String, val emojis: String = "") : StickerRef() {
        override val key: String get() = "w:$uri"
    }

    /**
     * Reference to a sticker bundled in the APK's assets/sticker/fluent_flat/
     * directory. `slug` matches the entry's `slug` field in `index.json`
     * and the on-disk filename (`{slug}.png`).
     */
    @Serializable
    data class Premade(val slug: String) : StickerRef() {
        override val key: String get() = "p:$slug"
    }
}

/**
 * Persistent reactive store for sticker recents and favorites. Mirrors
 * [com.noxquill.rewordium.keyboard.ime.media.gif.GifCollectionStore] in
 * shape: two kinds (RECENTS / FAVORITES) backed by separate JSON manifests,
 * singleton-per-kind so the IME and the search overlay see the same data.
 */
class StickerCollectionStore private constructor(
    private val appContext: Context,
    private val kind: Kind,
) {

    enum class Kind(val fileName: String, val cap: Int) {
        RECENTS("sticker_recents.json", RECENTS_CAP),
        FAVORITES("sticker_favorites.json", Int.MAX_VALUE),
    }

    @Serializable
    data class Entry(
        val ref: StickerRef,
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
        // sealed-class discriminator — kotlinx-serialization handles
        // StickerRef.User / StickerRef.WhatsApp polymorphism by default
        // with the class name as the type marker.
        classDiscriminator = "type"
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
                flogError { "StickerCollectionStore($kind): load failed ($e), starting empty" }
            }
            loaded = true
        }
    }

    fun contains(ref: StickerRef): Boolean = _entries.value.any { it.ref.key == ref.key }

    suspend fun add(ref: StickerRef) = withContext(Dispatchers.IO) {
        ensureLoaded()
        val entry = Entry(ref, System.currentTimeMillis() / 1000)
        mutex.withLock {
            val withoutDup = _entries.value.filterNot { it.ref.key == ref.key }
            val capped = (listOf(entry) + withoutDup).take(kind.cap)
            _entries.value = capped
            writeManifest()
        }
    }

    suspend fun remove(ref: StickerRef) = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            val next = _entries.value.filterNot { it.ref.key == ref.key }
            if (next.size == _entries.value.size) return@withLock
            _entries.value = next
            writeManifest()
        }
    }

    /** Toggle membership. Returns true if the item is now in the store. */
    suspend fun toggle(ref: StickerRef): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded()
        if (contains(ref)) {
            remove(ref)
            false
        } else {
            add(ref)
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
            flogError { "StickerCollectionStore($kind): manifest write failed ($e)" }
        }
    }

    companion object {
        private const val RECENTS_CAP = 30

        @Volatile private var recentsInstance: StickerCollectionStore? = null
        @Volatile private var favoritesInstance: StickerCollectionStore? = null

        fun recents(context: Context): StickerCollectionStore {
            val existing = recentsInstance
            if (existing != null) return existing
            return synchronized(this) {
                recentsInstance ?: StickerCollectionStore(
                    context.applicationContext, Kind.RECENTS,
                ).also { recentsInstance = it }
            }
        }

        fun favorites(context: Context): StickerCollectionStore {
            val existing = favoritesInstance
            if (existing != null) return existing
            return synchronized(this) {
                favoritesInstance ?: StickerCollectionStore(
                    context.applicationContext, Kind.FAVORITES,
                ).also { favoritesInstance = it }
            }
        }
    }
}
