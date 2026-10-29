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

package com.noxquill.rewordium.keyboard.ime.media

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.ime.media.gif.GifCollectionStore
import com.noxquill.rewordium.keyboard.ime.media.gif.KlipyClient
import com.noxquill.rewordium.keyboard.ime.media.sticker.StickerCollectionStore
import com.noxquill.rewordium.keyboard.ime.media.sticker.StickerRef
import com.noxquill.rewordium.keyboard.ime.media.sticker.StickerTile
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.ime.media.sticker.WhatsAppStickerReader
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSearchField
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.SpinningShape
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.TileBusy
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggBox

/** How long typing has to pause before a network search goes out (KLIPY's free tier is shared). */
private const val SEARCH_DEBOUNCE_MS = 350L

/** Height of the results strip above the search bar. */
private val SEARCH_STRIP_HEIGHT = 112.dp

private val GifSuggestions = listOf("happy dance", "thank you", "lol", "mind blown", "good morning", "hug", "yes", "sorry", "birthday", "cat")
private val StickerSuggestions = listOf("love", "hello", "lol", "thanks", "wow", "sad", "party", "cat")

/**
 * GIF and sticker search, Gboard style: it sits above the full keyboard, which types the query. A strip of
 * results live-updates as you type (trending while the query is empty), recent and suggested searches sit
 * under it, and the search bar carries the back arrow. Sending keeps the search, so you can send another;
 * the Search key opens all the results in the panel.
 */
@Composable
fun MediaSearchOverlay() = MediaExpressiveTheme {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val feedback = rememberMediaFeedback()
    val mode by keyboardManager.mediaSearchMode.collectAsState()
    val query by keyboardManager.mediaSearchQuery.collectAsState()
    var loading by remember { mutableStateOf(false) }
    val kind = if (mode == KeyboardManager.MediaSearchMode.STICKER) "sticker" else "gif"

    fun recordQuery(query: String) = MediaMemory.recordSearch(context, kind, query, keyboardManager.activeState.isIncognitoMode)

    SnyggBox(elementName = FlorisImeUi.Smartbar.elementName, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(SEARCH_STRIP_HEIGHT)) {
                    when (mode) {
                        KeyboardManager.MediaSearchMode.GIF -> GifSearchResults(
                            query = query,
                            onLoading = { loading = it },
                            onGifPicked = { uri, description ->
                                recordQuery(query)
                                editorInstance.commitMedia(uri = uri, mimeType = "image/gif", description = description.ifBlank { "GIF" })
                            },
                        )
                        KeyboardManager.MediaSearchMode.STICKER -> StickerSearchResults(
                            query = query,
                            strip = true,
                            onLoading = { loading = it },
                            onStickerPicked = { uri, mime, description ->
                                recordQuery(query)
                                editorInstance.commitMedia(uri = uri, mimeType = mime, description = description.ifBlank { "Sticker" })
                            },
                        )
                        else -> Unit
                    }
                }
                SearchSuggestions(
                    kind = kind,
                    query = query,
                    suggestions = if (kind == "sticker") StickerSuggestions else GifSuggestions,
                    onPick = {
                        feedback.tick()
                        keyboardManager.setMediaSearchQuery(it)
                    },
                )
                MediaSearchField(
                    query = query,
                    hints = listOf(if (kind == "sticker") "Search stickers" else "Search GIFs on KLIPY"),
                    shape = MediaShapes.blob(query.length),
                    active = true,
                    busy = loading,
                    onBack = {
                        feedback.tick()
                        keyboardManager.endMediaSearch()
                    },
                    onClear = {
                        feedback.delete()
                        keyboardManager.clearEmojiSearch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * One tap fills the search: recent searches first (newest first), then suggestions, each with its own
 * symbol and tone. While typing, only the ones that still match stay.
 */
@Composable
internal fun SearchSuggestions(
    kind: String,
    query: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    iconFor: (String) -> androidx.compose.ui.graphics.vector.ImageVector = { MediaCommit.tagIcon(it) },
) {
    val context = LocalContext.current
    val tones = rememberTones()
    val version by MediaMemory.version.collectAsState()
    val recent = remember(kind, version) { MediaMemory.recentSearches(context, kind) }
    val q = query.trim().lowercase()
    val shown = remember(recent, suggestions, q) {
        val all = recent.map { it to true } + suggestions.filterNot { s -> recent.any { it.equals(s, ignoreCase = true) } }.map { it to false }
        val others = all.filterNot { (text, _) -> text.equals(q, ignoreCase = true) }
        // Narrow to what still matches while typing; with no match, keep offering the rest rather than a gap.
        others.filter { (text, _) -> text.lowercase().startsWith(q) }.ifEmpty { others }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(38.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(shown, key = { it.first }) { (text, isRecent) ->
            MediaChip(
                selected = false,
                onClick = { onPick(text) },
                label = text,
                icon = if (isRecent) Symbols.History else iconFor(text),
                tone = tones.at(text.length),
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        SpinningShape(MediaShapes.blob(text.length), MaterialTheme.colorScheme.primaryContainer, Modifier.size(52.dp)) {
            Icon(Symbols.SearchOff, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
}

@Composable
private fun GifSearchResults(query: String, onLoading: (Boolean) -> Unit, onGifPicked: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val client = remember { KlipyClient() }
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    val tones = rememberTones()
    val recents = remember { GifCollectionStore.recents(context) }
    val imageLoader = remember { MediaCommit.imageLoader(context) }
    var results by remember { mutableStateOf<List<KlipyClient.GifResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sendingId by remember { mutableStateOf<String?>(null) }

    if (!client.isConfigured) {
        SearchMessage("GIF search isn’t set up in this build")
        return
    }
    LaunchedEffect(Unit) { recents.ensureLoaded() }
    LaunchedEffect(query) {
        // Typing restarts this effect, so only a pause actually searches.
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        loading = true
        onLoading(true)
        results = client.search(query)
        loading = false
        onLoading(false)
    }
    val stripState = rememberLazyListState()
    LaunchedEffect(results) { stripState.scrollToItem(0) }
    when {
        loading && results.isEmpty() -> SearchLoading()
        results.isEmpty() -> SearchMessage(if (query.isEmpty()) "Type to search GIFs" else "No GIFs for “$query”")
        else -> LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            itemsIndexed(results, key = { _, gif -> gif.id }) { index, gif ->
                val aspect = if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 1f
                MediaTile(
                    onClick = {
                        if (sendingId == null) {
                            feedback.tap()
                            sendingId = gif.id
                            scope.launch {
                                recents.add(gif)
                                val uri = MediaCommit.download(context, gif.gifUrl, "image/gif")
                                sendingId = null
                                if (uri != null) onGifPicked(uri, gif.contentDescription)
                                else Toast.makeText(context, "Couldn’t send that GIF. Check your connection.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxHeight().aspectRatio(aspect.coerceIn(0.6f, 1.9f)),
                    color = tones.at(index).container,
                    corner = 16.dp,
                    pressedCorner = 26.dp,
                    label = gif.contentDescription.ifBlank { "GIF" },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(gif.previewUrl).build(),
                        imageLoader = imageLoader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TileBusy(sendingId == gif.id)
                }
            }
        }
    }
}

/**
 * Sticker search: your own stickers and installed WhatsApp packs (matched on their emoji and pack name)
 * first, then KLIPY's community stickers.
 */
@Composable
internal fun StickerSearchResults(
    query: String,
    onStickerPicked: (Uri, String, String) -> Unit,
    modifier: Modifier = Modifier,
    strip: Boolean = false,
    onLoading: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    val store = remember { UserStickerStore.get(context) }
    val whatsapp = remember { WhatsAppStickerReader(context) }
    val recents = remember { StickerCollectionStore.recents(context) }
    val klipy = remember { KlipyClient() }
    val userEntries by store.entriesFlow.collectAsState()
    var packs by remember { mutableStateOf<List<WhatsAppStickerReader.Pack>>(emptyList()) }
    var community by remember { mutableStateOf<List<KlipyClient.StickerResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sendingKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        store.ensureLoaded()
        recents.ensureLoaded()
        packs = whatsapp.packs()
    }
    LaunchedEffect(query) {
        if (!klipy.isConfigured) return@LaunchedEffect
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        loading = true
        onLoading(true)
        community = if (query.isBlank()) klipy.stickerTrending() else klipy.stickerSearch(query)
        loading = false
        onLoading(false)
    }

    val items = remember(userEntries, packs, community, query) {
        val q = query.trim().lowercase()
        buildList {
            userEntries.sortedByDescending { it.t }
                .filter { q.isEmpty() || it.id.lowercase().contains(q) }
                .forEach { add(SearchSticker.User(it)) }
            packs.forEach { pack ->
                pack.stickers
                    .filter { q.isEmpty() || it.emojis.lowercase().contains(q) || pack.name.lowercase().contains(q) }
                    .forEach { add(SearchSticker.WhatsApp(it)) }
            }
            community.forEach { add(SearchSticker.Community(it)) }
        }
    }

    fun send(item: SearchSticker) {
        if (sendingKey != null) return
        feedback.tap()
        sendingKey = item.key
        scope.launch {
            val picked: Triple<Uri?, String, String> = when (item) {
                is SearchSticker.User -> {
                    store.touch(item.entry)
                    recents.add(StickerRef.User(item.entry.id))
                    Triple(MediaCommit.clone(context, Uri.fromFile(store.fileFor(item.entry)), item.entry.mime), item.entry.mime, "Sticker")
                }
                is SearchSticker.WhatsApp -> {
                    recents.add(StickerRef.WhatsApp(item.sticker.uri.toString(), item.sticker.emojis))
                    Triple(MediaCommit.clone(context, item.sticker.uri, "image/webp"), "image/webp", item.sticker.emojis.ifBlank { "Sticker" })
                }
                is SearchSticker.Community -> Triple(
                    MediaCommit.download(context, item.sticker.stickerUrl, "image/webp"),
                    "image/webp",
                    item.sticker.contentDescription.ifBlank { "Sticker" },
                )
            }
            sendingKey = null
            val uri = picked.first
            if (uri != null) onStickerPicked(uri, picked.second, picked.third)
            else Toast.makeText(context, "Couldn’t send that sticker.", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun tile(index: Int, item: SearchSticker, modifier: Modifier) = StickerTile(
        model = when (item) {
            is SearchSticker.User -> Uri.fromFile(store.fileFor(item.entry))
            is SearchSticker.WhatsApp -> item.sticker.uri
            is SearchSticker.Community -> item.sticker.previewUrl
        },
        favorite = false,
        sending = sendingKey == item.key,
        label = "Sticker",
        onClick = { send(item) },
        onLongClick = null,
        modifier = modifier,
        slot = index,
    )

    val stripState = rememberLazyListState()
    LaunchedEffect(query) { stripState.scrollToItem(0) }
    Box(modifier.fillMaxSize()) {
        when {
            items.isEmpty() && loading -> SearchLoading()
            items.isEmpty() -> SearchMessage(if (query.isEmpty()) "No stickers yet" else "No stickers for “$query”")
            strip -> LazyRow(
                state = stripState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                    tile(index, item, Modifier.fillMaxHeight())
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                    tile(index, item, Modifier)
                }
            }
        }
    }
}

private sealed interface SearchSticker {
    val key: String

    data class User(val entry: UserStickerStore.Entry) : SearchSticker {
        override val key: String get() = "u:${entry.id}"
    }

    data class WhatsApp(val sticker: WhatsAppStickerReader.Sticker) : SearchSticker {
        override val key: String get() = "w:${sticker.uri}"
    }

    data class Community(val sticker: KlipyClient.StickerResult) : SearchSticker {
        override val key: String get() = "c:${sticker.id}"
    }
}
