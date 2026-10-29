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

package com.noxquill.rewordium.keyboard.ime.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.vector.ImageVector
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for sending GIFs and stickers: everything is copied into the keyboard's own clipboard
 * provider first, so the receiving app gets a content URI the keyboard can grant (never another app's URI,
 * and never a network URL).
 */
object MediaCommit {
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var loader: ImageLoader? = null

    /** One animated-image loader for every panel, so its memory cache survives switching tabs. */
    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
            .also { loader = it }
    }

    /** Downloads [url] into the clipboard provider; null when offline or the server says no. */
    suspend fun download(context: Context, url: String, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "media_dl_${System.nanoTime()}.tmp")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                cache.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            register(context, Uri.fromFile(cache), mimeType, "media")
        } catch (e: Exception) {
            flogDebug { "MediaCommit.download failed: ${e.message}" }
            null
        } finally {
            cache.delete()
        }
    }

    /**
     * Copies [source] (a file or content URI) into the clipboard provider and registers its MIME type,
     * which apps like WhatsApp need before they accept the content.
     */
    suspend fun clone(context: Context, source: Uri, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        try {
            register(context, source, mimeType, "sticker")
        } catch (e: Exception) {
            flogDebug { "MediaCommit.clone failed: ${e.message}" }
            null
        }
    }

    /** Re-encodes a bundled asset sticker as WebP (assets can't be served through a content URI). */
    suspend fun cloneAsset(context: Context, assetPath: String): Uri? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "asset_${System.nanoTime()}.webp")
        try {
            val bitmap = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            cache.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
                bitmap.compress(format, 100, out)
            }
            register(context, Uri.fromFile(cache), "image/webp", "sticker")
        } catch (e: Exception) {
            flogDebug { "MediaCommit.cloneAsset failed: ${e.message}" }
            null
        } finally {
            cache.delete()
        }
    }

    private fun register(context: Context, source: Uri, mimeType: String, name: String): Uri? {
        val values = ContentValues(3).apply {
            put(ClipboardMediaProvider.Columns.MediaUri, source.toString())
            put(ClipboardMediaProvider.Columns.MimeTypes, mimeType)
            put(OpenableColumns.DISPLAY_NAME, name)
        }
        val uri = context.contentResolver.insert(ClipboardMediaProvider.IMAGE_CLIPS_URI, values) ?: return null
        // The provider answers ".../0" when it couldn't copy the file.
        return uri.takeIf { it.lastPathSegment != "0" }
    }

    /** A Material Symbol for a GIF or sticker tag, so the chip strip reads at a glance. */
    fun tagIcon(tag: String): ImageVector {
        val t = tag.lowercase()
        return when {
            "trend" in t -> Symbols.TrendingUp
            listOf("love", "kiss", "heart", "hug", "crush").any { it in t } -> Symbols.Favorite
            listOf("heartbreak", "broken").any { it in t } -> Symbols.HeartBroken
            listOf("happy", "smile", "excited", "yay", "joy").any { it in t } -> Symbols.SentimentVerySatisfied
            listOf("lol", "laugh", "funny", "haha", "lmao").any { it in t } -> Symbols.SentimentExcited
            listOf("sad", "cry", "upset", "sorry").any { it in t } -> Symbols.SentimentSad
            listOf("angry", "mad", "rage", "annoyed").any { it in t } -> Symbols.MoodBad
            listOf("bored", "meh", "whatever", "awkward").any { it in t } -> Symbols.SentimentNeutral
            listOf("hi", "hello", "bye", "wave").any { t == it || t.startsWith("$it ") } -> Symbols.WavingHand
            listOf("thank", "grateful", "please").any { it in t } -> Symbols.VolunteerActivism
            listOf("yes", "ok", "agree", "thumbs up", "good").any { t == it || it in t } -> Symbols.ThumbUp
            listOf("no", "nope", "disagree", "thumbs down").any { t == it } -> Symbols.ThumbDown
            listOf("dance", "music", "song", "sing").any { it in t } -> Symbols.MusicNote
            listOf("party", "celebrat", "hype").any { it in t } -> Symbols.Celebration
            listOf("congrat", "win", "proud", "bravo").any { it in t } -> Symbols.EmojiEvents
            listOf("birthday", "bday").any { it in t } -> Symbols.Cake
            listOf("cat", "dog", "animal", "puppy", "kitten", "pet").any { it in t } -> Symbols.Pets
            listOf("morning", "sun").any { it in t } -> Symbols.Sunny
            listOf("night", "sleep", "tired").any { it in t } -> Symbols.Bedtime
            listOf("wow", "omg", "shock", "surprise", "mind").any { it in t } -> Symbols.AutoAwesome
            listOf("fire", "hot", "lit").any { t == it || it in t } -> Symbols.LocalFireDepartment
            listOf("game", "gaming").any { it in t } -> Symbols.SportsEsports
            listOf("movie", "tv", "film", "anime").any { it in t } -> Symbols.Movie
            listOf("coffee", "food", "hungry", "eat").any { it in t } -> Symbols.LocalCafe
            listOf("think", "hmm", "confus").any { it in t } -> Symbols.Psychology
            listOf("sick", "ill", "ew", "gross").any { t == it || it in t } -> Symbols.Sick
            listOf("deal", "team", "friend").any { it in t } -> Symbols.Handshake
            listOf("hurry", "fast", "go").any { t == it } -> Symbols.Bolt
            else -> Symbols.Tag
        }
    }
}
