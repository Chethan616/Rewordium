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

package com.noxquill.rewordium.keyboard.ime.media.sticker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.noxquill.rewordium.keyboard.FlorisImeService
import com.noxquill.rewordium.keyboard.ime.media.MediaCommit
import com.noxquill.rewordium.keyboard.ime.media.StickerSearchResults
import com.noxquill.rewordium.keyboard.ime.media.gif.KlipyClient
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaAction
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaActionSheet
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaEmptyState
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaLoading
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.TileBadge
import com.noxquill.rewordium.keyboard.ime.media.ui.TileBusy
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.ShapeTile
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sticker panel: a chip strip (recents, favourites, your own stickers, KLIPY's community stickers, the
 * bundled set and every WhatsApp pack, each with its own icon or tray picture) above a grid of pressable
 * tiles. Every sticker is copied into the keyboard's clipboard provider before it is sent, so the receiving
 * app gets a URI the keyboard can grant. A long press opens the sticker's actions; the "new sticker" tile
 * opens Sticker Studio.
 *
 * @param onStickerPicked called with the provider URI, the MIME type and a short description; the caller
 *   commits it.
 */
@Composable
fun StickerPanel(
    onStickerPicked: (Uri, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    val tones = rememberTones()
    val userStore = remember { UserStickerStore.get(context) }
    val whatsapp = remember { WhatsAppStickerReader(context) }
    val recentsStore = remember { StickerCollectionStore.recents(context) }
    val favoritesStore = remember { StickerCollectionStore.favorites(context) }
    val klipy = remember { KlipyClient() }
    val keyboardManager by context.keyboardManager()
    val panelSearch by keyboardManager.panelSearch.collectAsState()
    val searchedQuery = panelSearch?.takeIf { it.mode == KeyboardManager.MediaSearchMode.STICKER }?.query

    val rawUserEntries by userStore.entriesFlow.collectAsState()
    val userEntries = remember(rawUserEntries) { rawUserEntries.sortedByDescending { it.t } }
    val recentEntries by recentsStore.entriesFlow.collectAsState()
    val favoriteEntries by favoritesStore.entriesFlow.collectAsState()
    val favoriteKeys = remember(favoriteEntries) { favoriteEntries.map { it.ref.key }.toSet() }

    var packs by remember { mutableStateOf<List<WhatsAppStickerReader.Pack>>(emptyList()) }
    var premade by remember { mutableStateOf<List<PremadeEntry>>(emptyList()) }
    var community by remember { mutableStateOf<List<KlipyClient.StickerResult>>(emptyList()) }
    var communityLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var sendingKey by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<PanelSticker?>(null) }
    var lastStudioLaunch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        userStore.ensureLoaded()
        recentsStore.ensureLoaded()
        favoritesStore.ensureLoaded()
        packs = whatsapp.packs()
        premade = withContext(Dispatchers.IO) { loadPremadeIndex(context) }
    }

    val tabs = remember(packs, premade, klipy.isConfigured) {
        buildList {
            add(StickerTab.Recents)
            add(StickerTab.Favorites)
            add(StickerTab.Mine)
            if (klipy.isConfigured) add(StickerTab.Community)
            if (premade.isNotEmpty()) add(StickerTab.Premade)
            packs.forEach { add(StickerTab.Pack(it)) }
        }
    }
    val activeTab = tabs.getOrNull(selectedTab) ?: StickerTab.Recents
    LaunchedEffect(activeTab is StickerTab.Community) {
        if (activeTab is StickerTab.Community && community.isEmpty()) {
            communityLoading = true
            community = klipy.stickerTrending()
            communityLoading = false
        }
    }

    /** Turns a stored reference back into something the grid can draw and send; null if it's gone. */
    fun resolve(ref: StickerRef): PanelSticker? = when (ref) {
        is StickerRef.User -> userEntries.firstOrNull { it.id == ref.id }?.let { entry ->
            PanelSticker(ref, Uri.fromFile(userStore.fileFor(entry)), entry.mime, "Sticker")
        }
        is StickerRef.WhatsApp -> PanelSticker(ref, Uri.parse(ref.uri), "image/webp", ref.emojis.ifBlank { "Sticker" })
        is StickerRef.Premade -> premade.firstOrNull { it.slug == ref.slug }?.let { entry ->
            PanelSticker(ref, Uri.parse("file:///android_asset/$PREMADE_DIR/${entry.slug}.png"), "image/webp", entry.name.ifBlank { entry.slug })
        }
    }

    fun send(sticker: PanelSticker) {
        if (sendingKey != null) return
        feedback.tap()
        sendingKey = sticker.key
        scope.launch {
            val ref = sticker.ref
            when (ref) {
                is StickerRef.User -> userEntries.firstOrNull { it.id == ref.id }?.let { userStore.touch(it) }
                else -> Unit
            }
            if (sticker.remoteUrl == null) recentsStore.add(ref)
            val uri = when {
                sticker.remoteUrl != null -> MediaCommit.download(context, sticker.remoteUrl, "image/webp")
                ref is StickerRef.Premade -> MediaCommit.cloneAsset(context, "$PREMADE_DIR/${ref.slug}.png")
                else -> MediaCommit.clone(context, sticker.model, sticker.mime)
            }
            sendingKey = null
            if (uri != null) {
                onStickerPicked(uri, sticker.mime, sticker.description)
            } else {
                Toast.makeText(context, "Couldn’t send that sticker.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openStudio() {
        val now = System.currentTimeMillis()
        if (now - lastStudioLaunch < 1500L) return
        lastStudioLaunch = now
        feedback.tap()
        try {
            FlorisImeService.shouldPreserveMediaUiModeOnce = true
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ui://ReBoard/settings/sticker-studio"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            flogDebug { "StickerPanel: couldn't open Sticker Studio: ${e.message}" }
            Toast.makeText(context, "Couldn’t open Sticker Studio", Toast.LENGTH_SHORT).show()
        }
    }

    val onLongPress: (PanelSticker) -> Unit = {
        feedback.reveal()
        actionsFor = it
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (searchedQuery != null) {
                // A search run with the Search key; the panel's search bar shows it and clears it.
                StickerSearchResults(
                    query = searchedQuery,
                    onStickerPicked = onStickerPicked,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(tabs, key = { _, tab -> tab.key }) { index, tab ->
                        val selected = tab.key == activeTab.key
                        val onClick = {
                            feedback.tick()
                            selectedTab = index
                        }
                        when (tab) {
                            is StickerTab.Pack -> MediaChip(
                                selected = selected,
                                onClick = onClick,
                                label = tab.pack.name.ifBlank { "Pack ${index + 1}" },
                                iconContent = tab.pack.trayUri?.let { tray ->
                                    {
                                        AsyncImage(
                                            model = tray,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp).clip(CircleShape),
                                        )
                                    }
                                },
                                icon = Symbols.Sticker,
                            )
                            StickerTab.Recents, StickerTab.Favorites -> MediaChip(
                                selected = selected,
                                onClick = onClick,
                                tone = tones.at(index),
                                icon = tab.icon(selected),
                                contentDescription = tab.title,
                            )
                            else -> MediaChip(selected = selected, onClick = onClick, label = tab.title, icon = tab.icon(selected), tone = tones.at(index))
                        }
                    }
                }

                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = activeTab,
                    contentKey = { it.key },
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = "sticker-tab",
                ) { tab ->
                    when (tab) {
                        StickerTab.Recents -> {
                            val items = remember(recentEntries, userEntries, premade) { recentEntries.mapNotNull { resolve(it.ref) } }
                            if (items.isEmpty()) {
                                MediaEmptyState(Symbols.History, "No recent stickers yet", "Stickers you send show up here, newest first.", shape = MaterialShapes.Clover4Leaf)
                            } else {
                                StickerGrid { stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress) }
                            }
                        }
                        StickerTab.Favorites -> {
                            val items = remember(favoriteEntries, userEntries, premade) { favoriteEntries.mapNotNull { resolve(it.ref) } }
                            if (items.isEmpty()) {
                                MediaEmptyState(Symbols.KidStar, "No favourites yet", "Long-press a sticker and add it to your favourites.", shape = MaterialShapes.Sunny)
                            } else {
                                StickerGrid { stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress) }
                            }
                        }
                        StickerTab.Mine -> {
                            val items = remember(userEntries) {
                                userEntries.map { PanelSticker(StickerRef.User(it.id), Uri.fromFile(userStore.fileFor(it)), it.mime, "Sticker") }
                            }
                            if (items.isEmpty()) {
                                MediaEmptyState(
                                    icon = Symbols.StickerAdd,
                                    title = "Make your first sticker",
                                    message = "Turn any photo into a sticker in Sticker Studio. Long-press one to favourite or delete it.",
                                    shape = MaterialShapes.Cookie12Sided,
                                ) {
                                    Button(onClick = ::openStudio, shapes = ButtonDefaults.shapes()) {
                                        Icon(Symbols.StickerAdd, null, Modifier.size(18.dp))
                                        Spacer(Modifier.size(8.dp))
                                        Text("New sticker")
                                    }
                                }
                            } else {
                                StickerGrid {
                                    item(key = "new") { NewStickerTile(::openStudio) }
                                    stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress)
                                }
                            }
                        }
                        StickerTab.Community -> when {
                            communityLoading && community.isEmpty() -> MediaLoading()
                            community.isEmpty() -> MediaEmptyState(Symbols.CloudOff, "No community stickers right now", "Check your connection and try again.", shape = MaterialShapes.SoftBurst)
                            else -> {
                                val items = remember(community) {
                                    community.map {
                                        PanelSticker(
                                            ref = StickerRef.WhatsApp(it.stickerUrl, it.contentDescription),
                                            model = Uri.parse(it.previewUrl),
                                            mime = "image/webp",
                                            description = it.contentDescription.ifBlank { "Sticker" },
                                            remoteUrl = it.stickerUrl,
                                        )
                                    }
                                }
                                StickerGrid { stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress, favouritable = false) }
                            }
                        }
                        StickerTab.Premade -> {
                            val items = remember(premade) { premade.mapNotNull { resolve(StickerRef.Premade(it.slug)) } }
                            StickerGrid { stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress) }
                        }
                        is StickerTab.Pack -> {
                            val items = remember(tab.pack) {
                                tab.pack.stickers.map {
                                    PanelSticker(StickerRef.WhatsApp(it.uri.toString(), it.emojis), it.uri, "image/webp", it.emojis.ifBlank { "Sticker" })
                                }
                            }
                            if (items.isEmpty()) {
                                MediaEmptyState(Symbols.Sticker, "This pack is empty", "Add stickers to it in WhatsApp.")
                            } else {
                                StickerGrid { stickerItems(items, favoriteKeys, sendingKey, ::send, onLongPress) }
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
            actions = selected?.let { sticker ->
                val favourite = sticker.ref.key in favoriteKeys
                buildList {
                    add(MediaAction(Symbols.Send, "Send sticker", primary = true) {
                        actionsFor = null
                        send(sticker)
                    })
                    if (sticker.remoteUrl == null) {
                        add(MediaAction(if (favourite) Symbols.KidStarFilled else Symbols.KidStar, if (favourite) "Remove from favourites" else "Add to favourites") {
                            feedback.tick()
                            actionsFor = null
                            scope.launch { favoritesStore.toggle(sticker.ref) }
                        })
                    }
                    if (recentEntries.any { it.ref.key == sticker.ref.key }) {
                        add(MediaAction(Symbols.History, "Remove from recents") {
                            feedback.tick()
                            actionsFor = null
                            scope.launch { recentsStore.remove(sticker.ref) }
                        })
                    }
                    val ref = sticker.ref
                    val entry = if (ref is StickerRef.User) userEntries.firstOrNull { it.id == ref.id } else null
                    if (entry != null) {
                        add(MediaAction(Symbols.Delete, "Delete sticker") {
                            feedback.tick()
                            actionsFor = null
                            scope.launch {
                                userStore.remove(entry)
                                recentsStore.remove(ref)
                                if (favoritesStore.contains(ref)) favoritesStore.toggle(ref)
                            }
                        })
                    }
                }
            }.orEmpty(),
        ) {
            selected?.let { AsyncImage(model = it.model, contentDescription = it.description, modifier = Modifier.fillMaxSize().padding(10.dp)) }
        }
    }
}

/** A sticker as the panel shows it: where it's stored, how to draw it, and (for KLIPY) where to fetch it. */
internal data class PanelSticker(
    val ref: StickerRef,
    val model: Uri,
    val mime: String,
    val description: String,
    val remoteUrl: String? = null,
) {
    val key: String get() = remoteUrl?.let { "c:$it" } ?: ref.key
}

private sealed interface StickerTab {
    val key: String
    val title: String
    fun icon(selected: Boolean) = when (this) {
        Recents -> if (selected) Symbols.HistoryFilled else Symbols.History
        Favorites -> if (selected) Symbols.KidStarFilled else Symbols.KidStar
        Mine -> if (selected) Symbols.FaceFilled else Symbols.Face
        Community -> if (selected) Symbols.PublicFilled else Symbols.Public
        Premade -> if (selected) Symbols.AutoAwesomeFilled else Symbols.AutoAwesome
        is Pack -> if (selected) Symbols.StickerFilled else Symbols.Sticker
    }

    data object Recents : StickerTab { override val key = "recents"; override val title = "Recent stickers" }
    data object Favorites : StickerTab { override val key = "favorites"; override val title = "Favourite stickers" }
    data object Mine : StickerTab { override val key = "mine"; override val title = "Mine" }
    data object Community : StickerTab { override val key = "community"; override val title = "Community" }
    data object Premade : StickerTab { override val key = "premade"; override val title = "Fluent" }
    data class Pack(val pack: WhatsAppStickerReader.Pack) : StickerTab {
        override val key: String get() = "pack:${pack.identifier}"
        override val title: String get() = pack.name
    }
}

@Composable
private fun StickerGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun LazyGridScope.stickerItems(
    items: List<PanelSticker>,
    favoriteKeys: Set<String>,
    sendingKey: String?,
    onClick: (PanelSticker) -> Unit,
    onLongClick: (PanelSticker) -> Unit,
    favouritable: Boolean = true,
    offset: Int = 0,
) {
    itemsIndexed(items, key = { _, it -> it.key }) { index, sticker ->
        StickerTile(
            model = sticker.model,
            favorite = favouritable && sticker.ref.key in favoriteKeys,
            sending = sendingKey == sticker.key,
            label = sticker.description,
            onClick = { onClick(sticker) },
            onLongClick = { onLongClick(sticker) },
            slot = index + offset,
        )
    }
}

/**
 * A sticker on its own MaterialShape cushion. Neighbouring stickers get different shapes and tones from the
 * keyboard theme, so a pack reads like a playful sheet rather than a grid of grey squares.
 */
@Composable
internal fun StickerTile(
    model: Any,
    favorite: Boolean,
    sending: Boolean,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    slot: Int = 0,
) {
    val tone = rememberTones().at(slot)
    Box(modifier.aspectRatio(1f)) {
        ShapeTile(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = MediaShapes.blob(slot * 5 + 1),
            color = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tone.container, 0.75f),
            label = label,
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        if (favorite) TileBadge(Symbols.KidStarFilled, contentDescription = "Favourite")
        TileBusy(sending)
    }
}

/** The way into Sticker Studio: the official sticker_add symbol on a turning MaterialShape. */
@Composable
private fun NewStickerTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.aspectRatio(1f)) {
        ShapeTile(
            onClick = onClick,
            shape = MaterialShapes.Cookie12Sided,
            color = MaterialTheme.colorScheme.primary,
            label = "New sticker",
            modifier = Modifier.weight(1f),
        ) {
            Icon(Symbols.StickerAdd, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@kotlinx.serialization.Serializable
private data class PremadeEntry(
    val slug: String,
    val name: String = "",
    val category: String = "",
)

private const val PREMADE_DIR = "sticker/fluent_flat"
private const val PREMADE_INDEX = "$PREMADE_DIR/index.json"

private fun loadPremadeIndex(context: Context): List<PremadeEntry> = try {
    context.assets.open(PREMADE_INDEX).bufferedReader().use { reader ->
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(PremadeEntry.serializer()),
            reader.readText(),
        )
    }
} catch (e: Exception) {
    emptyList()
}
