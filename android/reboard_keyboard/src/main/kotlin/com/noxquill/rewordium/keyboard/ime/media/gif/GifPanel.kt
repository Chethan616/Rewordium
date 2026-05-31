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

import android.content.ContentUris
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GIF picker panel — category chip strip + staggered grid of thumbnails.
 * Tapping a thumbnail downloads the full-res GIF in the background, clones
 * it into the keyboard's [ClipboardFileStorage], and fires [onGifPicked]
 * with a content URI the host editor can accept via
 * `EditorInstance.commitMedia`. Tapping a chip seeds a [KlipyClient.search]
 * with the chip's keyword.
 *
 * Search is handled by [com.noxquill.rewordium.keyboard.ime.media.MediaSearchOverlay]
 * — the panel header's pill triggers it, and the overlay drives a richer
 * full-grid search with the QWERTY keyboard below.
 *
 * Coil drives image loading. The image loader is built with the gif
 * decoders enabled so previews animate (Android P+ uses the native
 * ImageDecoder path; older devices fall back to coil's GifDecoder).
 *
 * Falls back to a centred "set KLIPY_API_KEY to use GIFs" hint when no
 * API key is wired in at build time — see [KlipyClient.isConfigured].
 */
@Composable
fun GifPanel(
    fg: Color,
    accent: Color,
    onGifPicked: (android.net.Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val client = remember { KlipyClient() }
    val scope = rememberCoroutineScope()
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KlipyClient.GifResult>>(emptyList()) }
    var categories by remember { mutableStateOf<List<KlipyClient.Category>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val dim = fg.copy(alpha = 0.55f)

    if (!client.isConfigured) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Set KLIPY_API_KEY in .env to enable the GIF panel.",
                color = dim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LaunchedEffect(query) {
        loading = true
        results = client.search(query)
        if (categories.isEmpty()) categories = client.categories()
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Material 3 chip strip — taps narrow the trending grid to that
        // category's keyword. The chip with the active query gets the
        // accent fill; everything else uses the muted surface tint.
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(categories) { category ->
                    val isActive = query.equals(category.name, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isActive) accent.copy(alpha = 0.28f)
                                else fg.copy(alpha = 0.08f),
                            )
                            .clickable {
                                query = if (isActive) "" else category.name
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = category.name.replaceFirstChar { it.uppercase() },
                            color = if (isActive) fg else fg.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (loading && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp))
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No GIFs found.", color = dim, fontSize = 13.sp)
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalItemSpacing = 6.dp,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(results, key = { it.id }) { gif ->
                    val aspect = if (gif.width > 0 && gif.height > 0) {
                        gif.width.toFloat() / gif.height.toFloat()
                    } else 1f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceIn(0.5f, 2.0f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(fg.copy(alpha = 0.05f))
                            .clickable {
                                scope.launch {
                                    val uri = downloadAndStore(context, gif.gifUrl)
                                    if (uri != null) onGifPicked(uri, gif.contentDescription)
                                }
                            },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(gif.previewUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = gif.contentDescription,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private val downloadClient = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

/**
 * Downloads the GIF at [url] into [ClipboardFileStorage] and returns the
 * content URI the editor can commit. Returns null on network failure or
 * empty body. Heavy work runs on [Dispatchers.IO].
 */
private suspend fun downloadAndStore(
    context: android.content.Context,
    url: String,
): android.net.Uri? = withContext(Dispatchers.IO) {
    try {
        val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
        response.use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body ?: return@withContext null
            val id = System.nanoTime()
            val file = ClipboardFileStorage.getFileForId(context, id)
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
            ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, id)
        }
    } catch (e: Exception) {
        flogDebug { "downloadAndStore failed: ${e.message}" }
        null
    }
}
