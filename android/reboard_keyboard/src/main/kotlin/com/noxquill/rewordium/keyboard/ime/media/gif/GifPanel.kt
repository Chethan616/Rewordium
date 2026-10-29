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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.noxquill.rewordium.keyboard.ime.media.gif

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.ime.media.MediaCommit
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaAction
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaActionSheet
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaEmptyState
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.TileBadge
import com.noxquill.rewordium.keyboard.ime.media.ui.TileBusy
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.launch

/**
 * The GIF panel: a chip strip (recents, favourites, trending, then KLIPY's categories, each with a Rounded
 * tag icon) above a staggered grid of animated tiles. Tapping a tile downloads the GIF into the keyboard's
 * clipboard provider and hands [onGifPicked] a content URI the editor can take; long-pressing opens its
 * actions. Search lives in the media search overlay, which this panel picks up through the shared query.
 */
@Composable
fun GifPanel(
    onGifPicked: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val client = remember { KlipyClient() }
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    val tones = rememberTones()
    val imageLoader = remember { MediaCommit.imageLoader(context) }
    val keyboardManager by context.keyboardManager()
    val panelSearch by keyboardManager.panelSearch.collectAsState()
    val searchedQuery = panelSearch?.takeIf { it.mode == KeyboardManager.MediaSearchMode.GIF }?.query

    val recentsStore = remember { GifCollectionStore.recents(context) }
    val favoritesStore = remember { GifCollectionStore.favorites(context) }
    val recentEntries by recentsStore.entriesFlow.collectAsState()
    val favoriteEntries by favoritesStore.entriesFlow.collectAsState()
    val favoriteIds = remember(favoriteEntries) { favoriteEntries.map { it.id }.toSet() }

    var source by remember { mutableStateOf(GifSource.Trending) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KlipyClient.GifResult>>(emptyList()) }
    var categories by remember { mutableStateOf<List<KlipyClient.Category>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var sendingId by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<KlipyClient.GifResult?>(null) }

    LaunchedEffect(Unit) {
        recentsStore.ensureLoaded()
        favoritesStore.ensureLoaded()
    }
    // A search run with the Search key fills the grid; clearing it (the bar's ×) goes back to trending.
    var shownSearch by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchedQuery) {
        if (searchedQuery != null) {
            query = searchedQuery
            source = GifSource.Trending
        } else if (shownSearch != null && query == shownSearch) {
            query = ""
        }
        shownSearch = searchedQuery
    }
    LaunchedEffect(source, query) {
        if (source != GifSource.Trending || !client.isConfigured) return@LaunchedEffect
        loading = true
        val found = client.search(query)
        results = found
        failed = found.isEmpty()
        if (categories.isEmpty()) categories = client.categories()
        loading = false
    }

    fun send(gif: KlipyClient.GifResult) {
        if (sendingId != null) return
        feedback.tap()
        sendingId = gif.id
        scope.launch {
            recentsStore.add(gif)
            val uri = MediaCommit.download(context, gif.gifUrl, "image/gif")
            sendingId = null
            if (uri != null) {
                onGifPicked(uri, gif.contentDescription)
            } else {
                Toast.makeText(context, "Couldn’t send that GIF. Check your connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "recents") {
                    MediaChip(
                        selected = source == GifSource.Recents,
                        onClick = {
                            feedback.tick()
                            source = if (source == GifSource.Recents) GifSource.Trending else GifSource.Recents
                        },
                        icon = Symbols.History,
                        selectedIcon = Symbols.HistoryFilled,
                        contentDescription = "Recent GIFs",
                    )
                }
                item(key = "favorites") {
                    MediaChip(
                        selected = source == GifSource.Favorites,
                        onClick = {
                            feedback.tick()
                            source = if (source == GifSource.Favorites) GifSource.Trending else GifSource.Favorites
                        },
                        icon = Symbols.KidStar,
                        selectedIcon = Symbols.KidStarFilled,
                        tone = tones.at(2),
                        contentDescription = "Favourite GIFs",
                    )
                }
                if (client.isConfigured) {
                    item(key = "trending") {
                        MediaChip(
                            selected = source == GifSource.Trending && query.isEmpty(),
                            onClick = {
                                feedback.tick()
                                source = GifSource.Trending
                                query = ""
                                keyboardManager.clearPanelSearch()
                            },
                            label = "Trending",
                            icon = Symbols.TrendingUp,
                            tone = tones.at(0),
                        )
                    }
                }
                if (source == GifSource.Trending) {
                    items(categories, key = { "cat:${it.name}" }) { category ->
                        val active = query.equals(category.name, ignoreCase = true)
                        MediaChip(
                            selected = active,
                            onClick = {
                                feedback.tick()
                                keyboardManager.clearPanelSearch()
                                query = if (active) "" else category.name
                            },
                            label = category.name.replaceFirstChar { it.uppercase() },
                            icon = MediaCommit.tagIcon(category.name),
                            tone = tones.at(categories.indexOf(category) + 1),
                        )
                    }
                }
            }

            val shown: List<KlipyClient.GifResult> = when (source) {
                GifSource.Trending -> results
                GifSource.Recents -> recentEntries.map { it.toGifResult() }
                GifSource.Favorites -> favoriteEntries.map { it.toGifResult() }
            }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = GifGridState(source, query, shown.isEmpty(), loading && shown.isEmpty()),
                contentKey = { it.source to it.query },
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "gif-grid",
            ) { state ->
                when {
                    !client.isConfigured && state.source == GifSource.Trending -> MediaEmptyState(
                        icon = Symbols.Gif2,
                        title = "GIFs aren’t set up",
                        message = "This build has no KLIPY key. Your recent and favourite GIFs still work.",
                    )
                    state.loading -> GifSkeleton()
                    state.empty -> GifEmptyState(state.source, failed && query.isEmpty())
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalItemSpacing = 6.dp,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(shown, key = { _, gif -> gif.id }) { index, gif ->
                            GifTile(
                                gif = gif,
                                placeholder = tones.at(index).container,
                                favorite = gif.id in favoriteIds,
                                sending = sendingId == gif.id,
                                imageLoader = imageLoader,
                                onClick = { send(gif) },
                                onLongClick = {
                                    feedback.reveal()
                                    actionsFor = gif
                                },
                            )
                        }
                        if (state.source == GifSource.Trending) {
                            item(key = "attribution", span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    "Powered by KLIPY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val selected = actionsFor
        MediaActionSheet(
            visible = selected != null,
            onDismiss = { actionsFor = null },
            actions = selected?.let { gif ->
                val favorite = gif.id in favoriteIds
                buildList {
                    add(MediaAction(Symbols.Send, "Send GIF", primary = true) {
                        actionsFor = null
                        send(gif)
                    })
                    add(MediaAction(if (favorite) Symbols.KidStarFilled else Symbols.KidStar, if (favorite) "Remove from favourites" else "Add to favourites") {
                        feedback.tick()
                        actionsFor = null
                        scope.launch { favoritesStore.toggle(gif) }
                    })
                    if (recentEntries.any { it.id == gif.id }) {
                        add(MediaAction(Symbols.History, "Remove from recents") {
                            feedback.tick()
                            actionsFor = null
                            scope.launch { recentsStore.remove(gif.id) }
                        })
                    }
                }
            }.orEmpty(),
        ) {
            selected?.let { gif ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(gif.previewUrl).build(),
                    imageLoader = imageLoader,
                    contentDescription = gif.contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class GifSource { Trending, Recents, Favorites }

private data class GifGridState(val source: GifSource, val query: String, val empty: Boolean, val loading: Boolean)

@Composable
private fun GifTile(
    gif: KlipyClient.GifResult,
    placeholder: Color,
    favorite: Boolean,
    sending: Boolean,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val aspect = if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 1f
    MediaTile(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.6f, 1.9f)),
        color = placeholder,
        corner = 20.dp,
        pressedCorner = 34.dp,
        label = gif.contentDescription.ifBlank { "GIF" },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(gif.previewUrl).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        if (favorite) TileBadge(Symbols.KidStarFilled, contentDescription = "Favourite")
        TileBusy(sending)
    }
}

@Composable
private fun GifEmptyState(source: GifSource, offline: Boolean) {
    when (source) {
        GifSource.Trending -> if (offline) {
            MediaEmptyState(
                icon = Symbols.CloudOff,
                title = "No GIFs right now",
                message = "Check your connection, then pick a category to try again.",
                shape = MaterialShapes.SoftBurst,
            )
        } else {
            MediaEmptyState(
                icon = Symbols.SearchOff,
                title = "Nothing found",
                message = "Try another word or tap a category.",
                shape = MaterialShapes.Cookie12Sided,
            )
        }
        GifSource.Recents -> MediaEmptyState(
            icon = Symbols.History,
            title = "No recent GIFs yet",
            message = "GIFs you send show up here, newest first.",
            shape = MaterialShapes.Clover4Leaf,
        )
        GifSource.Favorites -> MediaEmptyState(
            icon = Symbols.KidStar,
            title = "No favourites yet",
            message = "Long-press a GIF and add it to your favourites.",
            shape = MaterialShapes.Sunny,
        )
    }
}

/** While GIFs load: a staggered grid of softly pulsing tiles in the theme's tones, with the loading indicator. */
@Composable
private fun GifSkeleton() {
    val tones = rememberTones()
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    val heights = listOf(1.3f, 0.8f, 1f, 1.5f, 0.9f, 1.2f, 0.75f, 1.1f)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalItemSpacing = 6.dp,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(heights) { index, ratio ->
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / ratio)
                        .graphicsLayer { alpha = pulse }
                        .background(tones.at(index).container, RoundedCornerShape(20.dp)),
                )
            }
        }
        LoadingIndicator(Modifier.size(56.dp).align(Alignment.Center))
    }
}

