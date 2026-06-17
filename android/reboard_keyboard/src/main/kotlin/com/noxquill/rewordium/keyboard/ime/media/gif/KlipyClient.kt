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

import com.noxquill.rewordium.keyboard.BuildConfig
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Thin client over the KLIPY v1 REST API. Replaces the original Tenor v2
 * integration after Google announced the Tenor API sunset (new keys closed
 * 2026-01-13, full decommission 2026-06-30). KLIPY was founded by former
 * Tenor employees as a drop-in replacement; Discord and WhatsApp both
 * migrated to it as their Tenor successor.
 *
 * Endpoints wrapped:
 *   * [trending]   — featured GIFs for the default empty-search state
 *   * [search]     — keyword search
 *   * [categories] — popular category chips
 *
 * Auth model: the API key is embedded in the URL path, not a header or
 * query parameter — `https://api.klipy.com/api/v1/{KEY}/gifs/trending`.
 *
 * Locale: KLIPY uses an `xx_XX` format (e.g. `en_US`). When unset, falls
 * back to KLIPY's global defaults.
 *
 * Failure modes: every call returns an empty list and logs a debug line on
 * network / parse failure. The UI surfaces a "Set KLIPY_API_KEY" hint when
 * [isConfigured] is false (build without `-PklipyApiKey=...`).
 */
class KlipyClient(
    private val apiKey: String = BuildConfig.KLIPY_API_KEY,
    /**
     * Used by KLIPY's recent + ad-monetization features. We pass a stable
     * per-install identifier so KLIPY can dedupe trending impressions and
     * (optionally) credit ad revenue back to this client. Empty when not
     * used.
     */
    private val customerId: String = "rewordium-android",
) {

    @Serializable
    data class GifResult(
        val id: String,
        /** Full-resolution animated GIF URL — used at commit time. */
        val gifUrl: String,
        /** Small preview (typically `hd` or `sm` size) — used in the grid. */
        val previewUrl: String,
        val width: Int,
        val height: Int,
        val contentDescription: String,
    )

    @Serializable
    data class Category(
        val name: String,
        val previewUrl: String,
    )

    // ── Response shapes ─────────────────────────────────────────────────────
    // KLIPY's payload is nested deeper than the original docs suggested
    // (`file` is a two-level size→format→entry map, ids come back as
    // numbers, and the categories envelope wraps under `categories` rather
    // than `data`). We parse the loose tree via JsonElement and pick the
    // fields we actually use so a future field addition can't poison the
    // whole batch. `ignoreUnknownKeys = true` keeps things resilient.

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** True iff a non-empty API key is wired in at build time. */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * Trending GIFs for the default empty-search state. Returns up to [limit]
     * results (KLIPY enforces 8–50). Locale picks up the device default.
     */
    suspend fun trending(limit: Int = 24): List<GifResult> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            flogDebug { "KlipyClient: API key not configured — returning empty trending" }
            return@withContext emptyList()
        }
        fetchGifs("${base()}/gifs/trending?${commonParams(limit)}")
    }

    /**
     * Keyword search. Trims [query]; an empty query falls back to [trending].
     */
    suspend fun search(query: String, limit: Int = 24): List<GifResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext trending(limit)
        if (!isConfigured) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        fetchGifs("${base()}/gifs/search?q=$encoded&${commonParams(limit)}")
    }

    /**
     * Top categories for the chip strip. Empty when not configured or KLIPY
     * returns an error envelope.
     */
    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val url = "${base()}/gifs/categories?customer_id=$customerId"
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val body = r.body?.string() ?: return@withContext emptyList()
                parseCategories(body)
            }
        } catch (e: Exception) {
            flogDebug { "KlipyClient.categories failed: ${e.message}" }
            emptyList()
        }
    }

    private fun fetchGifs(url: String): List<GifResult> {
        return try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) {
                    flogDebug { "KlipyClient: HTTP ${r.code} for $url" }
                    return emptyList()
                }
                val body = r.body?.string() ?: return emptyList()
                parseGifs(body)
            }
        } catch (e: Exception) {
            flogDebug { "KlipyClient.fetchGifs failed: ${e.message}" }
            emptyList()
        }
    }

    // ── Tree parsing ────────────────────────────────────────────────────────
    // `file` from KLIPY is shaped `{ "hd": { "gif": {url,width,height}, "webp": ... }, "sm": ..., "md": ... }`.
    // We pick a preview (prefer webp at sm, then hd → md → first available)
    // and a full-resolution gif/webp to commit.

    private fun parseGifs(body: String): List<GifResult> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val items = data["data"] ?: data["gifs"] ?: return emptyList()
        return items.tryArray().mapNotNull { it.toGifResult() }
    }

    private fun parseCategories(body: String): List<Category> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val items = data["categories"] ?: data["data"] ?: return emptyList()
        return items.tryArray().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["category"]?.tryString()
                ?: obj["query"]?.tryString()
                ?: obj["name"]?.tryString()
                ?: return@mapNotNull null
            val preview = obj["preview_url"]?.tryString()
                ?: obj["image"]?.tryString()
                ?: ""
            Category(name, preview)
        }
    }

    private fun JsonElement.toGifResult(): GifResult? {
        val obj = this as? JsonObject ?: return null
        val id = obj["id"]?.tryString()
            ?: obj["slug"]?.tryString()
            ?: return null
        val title = obj["title"]?.tryString().orEmpty()
        val file = obj["file"]?.jsonObject ?: return null
        // Each size bucket (hd/md/sm) is itself a map of format→entry.
        // For previews, prefer the small bucket (lighter), falling back upward.
        val previewBuckets = listOf("sm", "md", "hd")
        val fullBuckets = listOf("hd", "md", "sm")
        // Within a bucket, prefer the static gif for animated playback in the
        // grid; webp is great too. The mp4/webm carry video which Coil's
        // image loader can't decode, so deprioritize those.
        val previewFormats = listOf("gif", "webp")
        val fullFormats = listOf("gif", "webp")

        val preview = pickEntry(file, previewBuckets, previewFormats) ?: return null
        val full = pickEntry(file, fullBuckets, fullFormats) ?: preview

        val previewUrl = preview["url"]?.tryString() ?: return null
        val fullUrl = full["url"]?.tryString() ?: previewUrl
        val w = full["width"]?.tryInt() ?: preview["width"]?.tryInt() ?: 0
        val h = full["height"]?.tryInt() ?: preview["height"]?.tryInt() ?: 0

        return GifResult(
            id = id,
            gifUrl = fullUrl,
            previewUrl = previewUrl,
            width = w,
            height = h,
            contentDescription = title,
        )
    }

    private fun pickEntry(
        file: JsonObject,
        bucketOrder: List<String>,
        formatOrder: List<String>,
    ): JsonObject? {
        for (bucketKey in bucketOrder) {
            val bucket = file[bucketKey]?.jsonObject ?: continue
            for (fmt in formatOrder) {
                val entry = bucket[fmt]?.jsonObject ?: continue
                if (entry["url"]?.tryString().isNullOrBlank()) continue
                return entry
            }
        }
        // Some payloads ship a flat `{ "url": ... }` directly per size, no
        // nested format map. Fall back to that shape.
        for (bucketKey in bucketOrder) {
            val entry = file[bucketKey]?.jsonObject ?: continue
            if (!entry["url"]?.tryString().isNullOrBlank()) return entry
        }
        return null
    }

    private fun JsonElement.tryString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.tryInt(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement.tryArray(): List<JsonElement> = when (this) {
        is kotlinx.serialization.json.JsonArray -> this
        else -> emptyList()
    }

    @Serializable
    data class StickerResult(
        val id: String,
        /** Full-resolution sticker URL (transparent WebP or PNG). */
        val stickerUrl: String,
        /** Small preview — used in the grid thumbnail. */
        val previewUrl: String,
        val width: Int,
        val height: Int,
        val contentDescription: String,
    )

    /**
     * Trending stickers for the default empty-search state.
     */
    suspend fun stickerTrending(limit: Int = 24): List<StickerResult> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        fetchStickers("${base()}/stickers/trending?${commonParams(limit)}")
    }

    /**
     * Keyword search for stickers.
     */
    suspend fun stickerSearch(query: String, limit: Int = 24): List<StickerResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext stickerTrending(limit)
        if (!isConfigured) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        fetchStickers("${base()}/stickers/search?q=$encoded&${commonParams(limit)}")
    }

    /**
     * Top sticker categories for the chip strip.
     */
    suspend fun stickerCategories(): List<Category> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val url = "${base()}/stickers/categories?customer_id=$customerId"
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val body = r.body?.string() ?: return@withContext emptyList()
                parseCategories(body)
            }
        } catch (e: Exception) {
            flogDebug { "KlipyClient.stickerCategories failed: ${e.message}" }
            emptyList()
        }
    }

    private fun fetchStickers(url: String): List<StickerResult> {
        return try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) {
                    flogDebug { "KlipyClient: HTTP ${r.code} for sticker $url" }
                    return emptyList()
                }
                val body = r.body?.string() ?: return emptyList()
                parseStickers(body)
            }
        } catch (e: Exception) {
            flogDebug { "KlipyClient.fetchStickers failed: ${e.message}" }
            emptyList()
        }
    }

    private fun parseStickers(body: String): List<StickerResult> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val items = data["data"] ?: data["stickers"] ?: return emptyList()
        return items.tryArray().mapNotNull { it.toStickerResult() }
    }

    private fun JsonElement.toStickerResult(): StickerResult? {
        val obj = this as? JsonObject ?: return null
        val id = obj["id"]?.tryString()
            ?: obj["slug"]?.tryString()
            ?: return null
        val title = obj["title"]?.tryString().orEmpty()
        val file = obj["file"]?.jsonObject ?: return null
        // Stickers prefer webp (transparent) > png > gif
        val previewBuckets = listOf("sm", "md", "hd")
        val fullBuckets = listOf("hd", "md", "sm")
        val stickerFormats = listOf("webp", "png", "gif")

        val preview = pickEntry(file, previewBuckets, stickerFormats) ?: return null
        val full = pickEntry(file, fullBuckets, stickerFormats) ?: preview

        val previewUrl = preview["url"]?.tryString() ?: return null
        val fullUrl = full["url"]?.tryString() ?: previewUrl
        val w = full["width"]?.tryInt() ?: preview["width"]?.tryInt() ?: 0
        val h = full["height"]?.tryInt() ?: preview["height"]?.tryInt() ?: 0

        return StickerResult(
            id = id,
            stickerUrl = fullUrl,
            previewUrl = previewUrl,
            width = w,
            height = h,
            contentDescription = title,
        )
    }

    private fun base() = "$BASE/$apiKey"

    private fun commonParams(limit: Int): String {
        val capped = limit.coerceIn(8, 50)
        return "per_page=$capped&customer_id=$customerId"
    }

    private companion object {
        const val BASE = "https://api.klipy.com/api/v1"
    }
}
