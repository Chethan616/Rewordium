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

package com.noxquill.rewordium.keyboard.ime.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnails for image and video clips in the clipboard panel: decoded off the main thread, scaled down to
 * tile size and kept in a small memory cache. Decoding a camera photo whole (about 48 MB for 12 MP) on the
 * UI thread, a few times over, was enough to crash the keyboard.
 */
internal object ClipThumbnails {
    /** Longest side of a thumbnail; clipboard tiles are at most about half the panel wide. */
    private const val MAX_PX = 640

    private val cache = object : LruCache<Long, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(id: Long): Result<ImageBitmap>? = cache.get(id)?.let { Result.success(it) }

    suspend fun load(context: Context, id: Long, isVideo: Boolean): Result<ImageBitmap> = withContext(Dispatchers.IO) {
        runCatching {
            check(id >= 0) { "This clip has no file" }
            val file = ClipboardFileStorage.getFileForId(context, id)
            check(file.exists()) { "Unable to resolve ${if (isVideo) "video" else "image"} at ${file.absolutePath}" }
            val bitmap = if (isVideo) videoFrame(file) else image(file.absolutePath)
            checkNotNull(bitmap) { "Unable to decode ${if (isVideo) "video" else "image"} at ${file.absolutePath}" }
            bitmap.asImageBitmap().also { cache.put(id, it) }
        }
    }

    private fun image(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun videoFrame(file: java.io.File): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            return runCatching { ThumbnailUtils.createVideoThumbnail(file, Size(MAX_PX, MAX_PX), null) }.getOrNull()
                ?: MediaMetadataRetriever().run {
                    try {
                        setDataSource(file.absolutePath)
                        getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_PX, MAX_PX)
                    } finally {
                        release()
                    }
                }
        }
        @Suppress("DEPRECATION")
        return ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
    }
}
