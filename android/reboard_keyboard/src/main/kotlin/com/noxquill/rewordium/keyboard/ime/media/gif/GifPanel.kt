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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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

    // Source state: which collection drives the grid.
    //  - Trending: KLIPY trending / categorical search
    //  - Recents:  GifCollectionStore.recents (auto-tracked on commit)
    //  - Favorites: GifCollectionStore.favorites (long-press toggles)
    var source by remember { mutableStateOf(GifSource.Trending) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KlipyClient.GifResult>>(emptyList()) }
    var categories by remember { mutableStateOf<List<KlipyClient.Category>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val dim = fg.copy(alpha = 0.55f)

    val recentsStore = remember { GifCollectionStore.recents(context) }
    val favoritesStore = remember { GifCollectionStore.favorites(context) }
    val recentEntries by recentsStore.entriesFlow.collectAsState()
    val favoriteEntries by favoritesStore.entriesFlow.collectAsState()
    val favoriteIds = remember(favoriteEntries) { favoriteEntries.map { it.id }.toSet() }

    LaunchedEffect(Unit) {
        recentsStore.ensureLoaded()
        favoritesStore.ensureLoaded()
    }

    if (!client.isConfigured && recentEntries.isEmpty() && favoriteEntries.isEmpty()) {
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

    LaunchedEffect(source, query) {
        if (source != GifSource.Trending) return@LaunchedEffect
        loading = true
        if (client.isConfigured) {
            results = client.search(query)
            if (categories.isEmpty()) categories = client.categories()
        }
        loading = false
    }

    // Pick the grid data the user is currently looking at.
    val displayed: List<KlipyClient.GifResult> = when (source) {
        GifSource.Trending -> results
        GifSource.Recents -> recentEntries.map { it.toGifResult() }
        GifSource.Favorites -> favoriteEntries.map { it.toGifResult() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // M3 chip strip: pinned Recents (clock) + Favorites (star) icon
        // chips at the very start (Gboard's pattern — both are categories,
        // distinguishable as icons rather than labels), then the KLIPY
        // category chips inline after them. FilterChip handles the
        // selected-state surface tint, ripple, and outline.
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            item(key = "__recents") {
                FilterChip(
                    selected = source == GifSource.Recents,
                    onClick = {
                        source = if (source == GifSource.Recents) GifSource.Trending else GifSource.Recents
                    },
                    label = {},
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Recents",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = gifChipColors(fg, accent),
                    border = null,
                )
            }
            item(key = "__favorites") {
                FilterChip(
                    selected = source == GifSource.Favorites,
                    onClick = {
                        source = if (source == GifSource.Favorites) GifSource.Trending else GifSource.Favorites
                    },
                    label = {},
                    leadingIcon = {
                        Icon(
                            imageVector = if (source == GifSource.Favorites) Icons.Filled.Star
                            else Icons.Outlined.StarBorder,
                            contentDescription = "Favorites",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = gifChipColors(fg, accent),
                    border = null,
                )
            }
            if (categories.isNotEmpty() && source == GifSource.Trending) {
                items(categories, key = { "cat:${it.name}" }) { category ->
                    val isActive = query.equals(category.name, ignoreCase = true)
                    FilterChip(
                        selected = isActive,
                        onClick = { query = if (isActive) "" else category.name },
                        label = {
                            Text(
                                text = category.name.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                        colors = gifChipColors(fg, accent),
                        border = null,
                    )
                }
            }
        }

        AnimatedContent(
            targetState = GifGridState(source, displayed, loading),
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            contentKey = { it.source },
            label = "gif-grid-source",
        ) { state ->
            when {
                state.loading && state.items.isEmpty() && state.source == GifSource.Trending -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                state.items.isEmpty() -> GifEmptyState(source = state.source, fg = fg, accent = accent)
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalItemSpacing = 6.dp,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(state.items, key = { it.id }) { gif ->
                        GifTile(
                            gif = gif,
                            context = context,
                            imageLoader = imageLoader,
                            isFavorited = gif.id in favoriteIds,
                            fg = fg,
                            accent = accent,
                            onTap = {
                                scope.launch {
                                    // Auto-track as recent regardless of source —
                                    // the user just picked it, so it's a recent
                                    // use even if they picked from favorites.
                                    recentsStore.add(gif)
                                    val uri = downloadAndStore(context, gif.gifUrl)
                                    if (uri != null) onGifPicked(uri, gif.contentDescription)
                                }
                            },
                            onToggleFavorite = {
                                scope.launch {
                                    val nowFavorited = favoritesStore.toggle(gif)
                                    Toast.makeText(
                                        context,
                                        if (nowFavorited) "Added to favorites"
                                        else "Removed from favorites",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onRemoveRecent = if (source == GifSource.Recents) {
                                { scope.launch { recentsStore.remove(gif.id) } }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

private data class GifGridState(
    val source: GifSource,
    val items: List<KlipyClient.GifResult>,
    val loading: Boolean,
)

@Composable
private fun gifChipColors(fg: Color, accent: Color): SelectableChipColors {
    return FilterChipDefaults.filterChipColors(
        containerColor = fg.copy(alpha = 0.08f),
        labelColor = fg.copy(alpha = 0.85f),
        iconColor = fg.copy(alpha = 0.85f),
        selectedContainerColor = accent.copy(alpha = 0.28f),
        selectedLabelColor = fg,
        selectedLeadingIconColor = fg,
    )
}

@Composable
private fun GifTile(
    gif: KlipyClient.GifResult,
    context: android.content.Context,
    imageLoader: ImageLoader,
    isFavorited: Boolean,
    fg: Color,
    accent: Color,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveRecent: (() -> Unit)?,
) {
    val aspect = if (gif.width > 0 && gif.height > 0) {
        gif.width.toFloat() / gif.height.toFloat()
    } else 1f
    var menuOpen by remember(gif.id) { mutableStateOf(false) }
    val inputFeedbackController = com.noxquill.rewordium.keyboard.ime.input
        .LocalInputFeedbackController.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.5f, 2.0f))
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.06f))
            .pointerInput(gif.id) {
                detectTapGestures(
                    onTap = {
                        inputFeedbackController.keyPress(
                            com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData.UNSPECIFIED,
                        )
                        onTap()
                    },
                    onLongPress = { menuOpen = true },
                )
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
        if (isFavorited) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorited",
                    tint = accent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        // Gboard-style long-press menu — anchored to the tile, dismisses
        // on tap-outside. `focusable = false` keeps the IME alive (the
        // default M3 menu is focusable and the system kills the keyboard
        // when its input view loses focus).
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            properties = PopupProperties(focusable = false),
        ) {
            DropdownMenuItem(
                text = {
                    Text(if (isFavorited) "Remove from favorites" else "Add to favorites")
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleFavorite()
                },
            )
            if (onRemoveRecent != null) {
                DropdownMenuItem(
                    text = { Text("Remove from recents") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Schedule, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onRemoveRecent()
                    },
                )
            }
        }
    }
}

@Composable
private fun GifEmptyState(source: GifSource, fg: Color, accent: Color) {
    val (icon, headline, helper) = when (source) {
        GifSource.Trending -> Triple(Icons.Outlined.GifBox, "No GIFs",
            "Try a different search or tap a category chip.")
        GifSource.Recents -> Triple(Icons.Outlined.Schedule, "No recent GIFs yet",
            "GIFs you send appear here, newest first.")
        GifSource.Favorites -> Triple(Icons.Outlined.StarBorder, "No favorites yet",
            "Long-press a GIF and tap “Add to favorites.”")
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(40.dp),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = headline,
                color = fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = helper,
                color = fg.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private enum class GifSource { Trending, Recents, Favorites }

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
