import os
import re

path = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\ime\media\sticker\UserStickerStore.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Add StickerPack and StoreManifest schemas
target_schema = '''@Serializable
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
    )'''
replacement_schema = '''@Serializable
    data class StickerPack(
        val id: String,
        val name: String,
        val t: Long = System.currentTimeMillis() / 1000
    )

    @Serializable
    data class StoreManifest(
        val packs: List<StickerPack> = emptyList(),
        val entries: List<Entry> = emptyList()
    )

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
        /** Pack ID if it belongs to a custom pack. */
        val packId: String? = null
    )'''
c = c.replace(target_schema, replacement_schema)

# 2. Add pack reactive state
target_state = '''private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entriesFlow: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val entries: List<Entry> get() = _entries.value'''
replacement_state = '''private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entriesFlow: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val entries: List<Entry> get() = _entries.value

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packsFlow: StateFlow<List<StickerPack>> = _packs.asStateFlow()
    private val packs: List<StickerPack> get() = _packs.value'''
c = c.replace(target_state, replacement_state)

# 3. Update deserializer and loading
target_load = '''private val listSerializer = ListSerializer(Entry.serializer())

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
                flogError { "UserStickerStore: load failed (), starting empty" }
            }
            loaded = true
        }
    }'''
replacement_load = '''private val manifestSerializer = StoreManifest.serializer()
    private val legacyListSerializer = ListSerializer(Entry.serializer())

    /** Idempotent. Reads the manifest off disk on first call. */
    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            try {
                if (manifestFile.exists()) {
                    val text = manifestFile.readText(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        try {
                            val manifest = json.decodeFromString(manifestSerializer, text)
                            _entries.value = manifest.entries
                            _packs.value = manifest.packs
                        } catch (e: Exception) {
                            // Try legacy format
                            val legacy = json.decodeFromString(legacyListSerializer, text)
                            _entries.value = legacy
                            _packs.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                flogError { "UserStickerStore: load failed (), starting empty" }
            }
            loaded = true
        }
    }'''
c = c.replace(target_load, replacement_load)

# 4. Snapshot update
target_snap = '''fun snapshot(): List<Entry> {
        val current = entries
        return current.sortedByDescending { it.t }
    }'''
replacement_snap = '''fun snapshot(): List<Entry> {
        return entries.sortedByDescending { it.t }
    }
    
    fun getPacks(): List<StickerPack> {
        return packs.sortedByDescending { it.t }
    }'''
c = c.replace(target_snap, replacement_snap)

# 5. Import signature
target_import = '''suspend fun import(sourceUri: Uri, mimeType: String, tags: List<String> = emptyList()): Entry? = withContext(Dispatchers.IO) {'''
replacement_import = '''suspend fun import(sourceUri: Uri, mimeType: String, tags: List<String> = emptyList(), packId: String? = null): Entry? = withContext(Dispatchers.IO) {'''
c = c.replace(target_import, replacement_import)

target_entry_create = '''val entry = Entry(id, ext, mimeType, System.currentTimeMillis() / 1000, tags)'''
replacement_entry_create = '''val entry = Entry(id, ext, mimeType, System.currentTimeMillis() / 1000, tags, packId)'''
c = c.replace(target_entry_create, replacement_entry_create)

# 6. Write manifest
target_write = '''private fun writeManifest() {
        try {
            val tmp = File(dir, ".tmp")
            tmp.writeText(json.encodeToString(listSerializer, entries), Charsets.UTF_8)'''
replacement_write = '''private fun writeManifest() {
        try {
            val tmp = File(dir, ".tmp")
            val manifest = StoreManifest(packs, entries)
            tmp.writeText(json.encodeToString(manifestSerializer, manifest), Charsets.UTF_8)'''
c = c.replace(target_write, replacement_write)

# 7. Pack management methods
target_end = '''    private fun mimeToExt(mime: String): String = when {'''
replacement_end = '''    suspend fun createPack(name: String): StickerPack = withContext(Dispatchers.IO) {
        ensureLoaded()
        val pack = StickerPack(UUID.randomUUID().toString(), name)
        mutex.withLock {
            _packs.value = _packs.value + pack
            writeManifest()
        }
        pack
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            // Remove pack
            _packs.value = _packs.value.filterNot { it.id == packId }
            // Move its stickers back to general
            _entries.value = _entries.value.map { if (it.packId == packId) it.copy(packId = null) else it }
            writeManifest()
        }
    }

    suspend fun renamePack(packId: String, newName: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        mutex.withLock {
            _packs.value = _packs.value.map { if (it.id == packId) it.copy(name = newName) else it }
            writeManifest()
        }
    }

    private fun mimeToExt(mime: String): String = when {'''
c = c.replace(target_end, replacement_end)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("Updated UserStickerStore successfully")
