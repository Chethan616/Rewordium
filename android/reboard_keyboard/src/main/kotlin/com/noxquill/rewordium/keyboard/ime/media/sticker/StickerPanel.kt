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

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.noxquill.rewordium.keyboard.ime.media.CustomChip
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.ime.media.StickerSearchResults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import coil.compose.AsyncImage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.ime.media.gif.KlipyClient
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Sticker picker — horizontal pack-tab strip ("User", then each installed
 * WhatsApp pack), grid of stickers below. Long-press on a user sticker
 * deletes it; the "+" tile in the User pack opens an image picker.
 *
 * On commit, every sticker (regardless of source) is cloned into
 * [ClipboardFileStorage] so the receiving editor gets a content URI from
 * the keyboard's own provider — avoids permission issues forwarding
 * WhatsApp's content URIs to third-party editors.
 *
 * Note: The image picker uses [Intent.ACTION_GET_CONTENT] launched via
 * [Context.startActivity] with [Intent.FLAG_ACTIVITY_NEW_TASK] because
 * the keyboard runs as an [InputMethodService], not an Activity. The
 * result is handled by [StickerImportActivity] which writes the selected
 * image into the user sticker store.
 *
 * @param onStickerPicked Called with the clipboard-provider content URI,
 *                        the sticker's MIME type, and a short description.
 *                        The caller is responsible for invoking
 *                        `EditorInstance.commitMedia`.
 */
@Composable
fun StickerPanel(
    fg: Color,
    accent: Color,
    onStickerPicked: (Uri, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userStore = remember { UserStickerStore.get(context) }
    val whatsapp = remember { WhatsAppStickerReader(context) }
    val recentsStore = remember { StickerCollectionStore.recents(context) }
    val favoritesStore = remember { StickerCollectionStore.favorites(context) }

    // Subscribe to the singleton stores so imports / recents / favorites
    // changes anywhere in the IME show up instantly here.
    val rawEntries by userStore.entriesFlow.collectAsState()
    val userEntries = remember(rawEntries) { rawEntries.sortedByDescending { it.t } }
    val recentEntries by recentsStore.entriesFlow.collectAsState()
    val favoriteEntries by favoritesStore.entriesFlow.collectAsState()
    val favoriteKeys = remember(favoriteEntries) { favoriteEntries.map { it.ref.key }.toSet() }

    val keyboardManager by context.keyboardManager()
    val mediaSearchQuery by keyboardManager.mediaSearchQuery.collectAsState()

    var waPacks by remember { mutableStateOf<List<WhatsAppStickerReader.Pack>>(emptyList()) }
    var premadeIndex by remember { mutableStateOf<List<PremadeEntry>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    val dim = fg.copy(alpha = 0.55f)

    // Community stickers (KLIPY API)
    val klipyClient = remember { KlipyClient() }
    var communityStickers by remember { mutableStateOf<List<KlipyClient.StickerResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        userStore.ensureLoaded()
        recentsStore.ensureLoaded()
        favoritesStore.ensureLoaded()
        waPacks = whatsapp.packs()
        premadeIndex = withContext(Dispatchers.IO) { loadPremadeIndex(context) }
        if (klipyClient.isConfigured) {
            communityStickers = klipyClient.stickerTrending()
        }
    }

    // Build the tab strip. Recents + Favorites pin at the start (Gboard
    // style); then "User", "Premade" (bundled Fluent UI Emoji), then each
    // installed WhatsApp pack.
    val tabs by remember(userEntries, waPacks, premadeIndex, klipyClient.isConfigured) {
        derivedStateOf { buildList {
            add(TabSpec.Recents)
            add(TabSpec.Favorites)
            if (klipyClient.isConfigured) add(TabSpec.Community)
            add(TabSpec.User)
            if (premadeIndex.isNotEmpty()) add(TabSpec.Premade)
            waPacks.forEachIndexed { i, pack -> add(TabSpec.WhatsApp(i, pack)) }
        } }
    }

    // Helper: resolve a StickerRef back into something the grid can render
    // and commit. Returns null if the source got deleted/uninstalled
    // (eg. user sticker removed or WhatsApp pack gone).
    fun resolveRef(ref: StickerRef): ResolvedSticker? = when (ref) {
        is StickerRef.User -> {
            val entry = userEntries.firstOrNull { it.id == ref.id }
            entry?.let {
                ResolvedSticker(
                    ref = ref,
                    model = Uri.fromFile(userStore.fileFor(it)),
                    mime = it.mime,
                    description = "Sticker",
                )
            }
        }
        is StickerRef.WhatsApp -> ResolvedSticker(
            ref = ref,
            model = Uri.parse(ref.uri),
            mime = "image/webp",
            description = ref.emojis.ifBlank { "Sticker" },
        )
        is StickerRef.Premade -> {
            val entry = premadeIndex.firstOrNull { it.slug == ref.slug }
            entry?.let {
                ResolvedSticker(
                    ref = ref,
                    // file:///android_asset/ is Coil's canonical way to
                    // address bundled assets; we re-encode at commit time
                    // (cloneAssetAsWebP) because the IME's clipboard
                    // provider expects a content:// URI it can serve from.
                    model = Uri.parse("file:///android_asset/sticker/fluent_flat/${it.slug}.png"),
                    mime = "image/webp",
                    description = it.name.ifBlank { it.slug },
                )
            }
        }
    }

    val onCommit: (StickerRef, ResolvedSticker) -> Unit = { ref, resolved ->
        scope.launch {
            recentsStore.add(ref)
            val uri = withContext(Dispatchers.IO) {
                if (ref is StickerRef.Premade) {
                    // Premade assets live in the APK — contentResolver
                    // can't open `file:///android_asset/…`, so we read
                    // via `context.assets.open` and re-encode as WebP
                    // into ClipboardFileStorage.
                    clonePremadeAssetToClipboardStore(context, ref.slug)
                } else {
                    cloneToClipboardStore(context, resolved.model, resolved.mime)
                }
            } ?: return@launch
            onStickerPicked(uri, resolved.mime, resolved.description)
        }
    }

    val onToggleFavorite: (StickerRef) -> Unit = { ref ->
        scope.launch {
            val nowFavorited = favoritesStore.toggle(ref)
            Toast.makeText(
                context,
                if (nowFavorited) "Added to favorites" else "Removed from favorites",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val onRemoveRecent: (StickerRef) -> Unit = { ref ->
        scope.launch { recentsStore.remove(ref) }
    }

    val onDeleteUser: (UserStickerStore.Entry) -> Unit = { entry ->
        scope.launch { userStore.remove(entry) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (mediaSearchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Search results for \"$mediaSearchQuery\"",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                CustomChip(
                    selected = true,
                    onClick = { keyboardManager.endMediaSearch() },
                    fg = fg,
                    accent = accent
                ) {
                    Text(text = "Clear ✕")
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                StickerSearchResults(
                    query = mediaSearchQuery,
                    fg = fg,
                    accent = accent,
                    onStickerPicked = onStickerPicked,
                )
            }
        } else {
            // M3 chip strip. Recents + Favorites are FilterChip icon-only tabs
            // at the start, then "User" + each WhatsApp pack as labeled chips.
            // FilterChip handles the selected surface tint, label tint, and
            // ripple uniformly so all the chips read as one M3 tab strip.
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tabs) { tab ->
                    val idx = tabs.indexOf(tab)
                    val isActive = idx == selectedTab
                    CustomChip(
                        selected = isActive,
                        onClick = { selectedTab = idx },
                        fg = fg,
                        accent = accent
                    ) {
                        when (tab) {
                            TabSpec.Recents -> {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = "Recents",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            TabSpec.Favorites -> {
                                Icon(
                                    imageVector = if (isActive) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Favorites",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            else -> {
                                Text(text = tab.label())
                            }
                        }
                    }
                }
            }

            val active = tabs.getOrNull(selectedTab) ?: TabSpec.Recents
            AnimatedContent(
                targetState = active,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                contentKey = { tabs.indexOf(it) },
                label = "sticker-tab",
            ) { tabContent ->
                when (tabContent) {
                    TabSpec.Recents -> CollectionGrid(
                        refs = recentEntries.map { it.ref },
                        emptyKind = EmptyKind.Recents,
                        fg = fg,
                        accent = accent,
                        favoriteKeys = favoriteKeys,
                        resolve = ::resolveRef,
                        onPick = { ref, resolved -> onCommit(ref, resolved) },
                        onToggleFavorite = onToggleFavorite,
                        onRemoveRecent = onRemoveRecent,
                    )
                    TabSpec.Favorites -> CollectionGrid(
                        refs = favoriteEntries.map { it.ref },
                        emptyKind = EmptyKind.Favorites,
                        fg = fg,
                        accent = accent,
                        favoriteKeys = favoriteKeys,
                        resolve = ::resolveRef,
                        onPick = { ref, resolved -> onCommit(ref, resolved) },
                        onToggleFavorite = onToggleFavorite,
                        onRemoveRecent = null,
                    )
                    TabSpec.User -> UserGrid(
                        entries = userEntries,
                        fg = fg, accent = accent,
                        favoriteKeys = favoriteKeys,
                        onAddClick = {
                            // Launch the transparent helper Activity which can use
                            // the system image picker and forward the result to
                            // UserStickerStore. InputMethodService cannot host
                            // ActivityResult contracts directly.
                            try {
                                com.noxquill.rewordium.keyboard.FlorisImeService.shouldPreserveMediaUiModeOnce = true
                                val intent = Intent(context, StickerImportActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                flogDebug { "StickerPanel: Failed to launch picker: ${e.message}" }
                                Toast.makeText(context, "Cannot open image picker", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPick = { entry ->
                            val ref = StickerRef.User(entry.id)
                            scope.launch {
                                userStore.touch(entry)
                                recentsStore.add(ref)
                                val file = userStore.fileFor(entry)
                                if (!file.exists()) return@launch
                                val uri = withContext(Dispatchers.IO) {
                                    cloneToClipboardStore(context, Uri.fromFile(file), entry.mime)
                                } ?: return@launch
                                onStickerPicked(uri, entry.mime, "Sticker")
                            }
                        },
                        onToggleFavorite = { entry -> onToggleFavorite(StickerRef.User(entry.id)) },
                        onDelete = onDeleteUser,
                    )
                    TabSpec.Premade -> CollectionGrid(
                        refs = premadeIndex.map { StickerRef.Premade(it.slug) },
                        emptyKind = EmptyKind.Recents, // never visible — we hide
                        fg = fg,
                        accent = accent,
                        favoriteKeys = favoriteKeys,
                        resolve = ::resolveRef,
                        onPick = { ref, resolved -> onCommit(ref, resolved) },
                        onToggleFavorite = onToggleFavorite,
                        onRemoveRecent = null,
                    )
                    is TabSpec.WhatsApp -> WhatsAppGrid(
                        pack = tabContent.pack,
                        fg = fg,
                        favoriteKeys = favoriteKeys,
                        accent = accent,
                        onPick = { sticker ->
                            val ref = StickerRef.WhatsApp(sticker.uri.toString(), sticker.emojis)
                            scope.launch {
                                recentsStore.add(ref)
                                val uri = withContext(Dispatchers.IO) {
                                    cloneToClipboardStore(context, sticker.uri, "image/webp")
                                } ?: return@launch
                                onStickerPicked(uri, "image/webp", sticker.emojis.ifBlank { "Sticker" })
                            }
                        },
                        onToggleFavorite = { sticker ->
                            onToggleFavorite(StickerRef.WhatsApp(sticker.uri.toString(), sticker.emojis))
                        },
                    )
                    TabSpec.Community -> CommunityGrid(
                        stickers = communityStickers,
                        fg = fg,
                        accent = accent,
                        onPick = { sticker ->
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    downloadStickerAndStore(context, sticker.stickerUrl)
                                } ?: return@launch
                                onStickerPicked(uri, "image/webp", sticker.contentDescription.ifBlank { "Sticker" })
                            }
                        },
                        onSearch = { query ->
                            scope.launch {
                                communityStickers = if (query.isBlank()) {
                                    klipyClient.stickerTrending()
                                } else {
                                    klipyClient.stickerSearch(query)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private enum class EmptyKind { Recents, Favorites }

private sealed interface TabSpec {
    fun label(): String
    object Recents : TabSpec { override fun label() = "Recents" }
    object Favorites : TabSpec { override fun label() = "Favorites" }
    object Community : TabSpec { override fun label() = "Community" }
    object User : TabSpec { override fun label() = "User" }
    object Premade : TabSpec { override fun label() = "Premade" }
    data class WhatsApp(val index: Int, val pack: WhatsAppStickerReader.Pack) : TabSpec {
        override fun label(): String = pack.name.ifBlank { "Pack ${index + 1}" }
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

private fun loadPremadeIndex(context: android.content.Context): List<PremadeEntry> {
    return try {
        context.assets.open(PREMADE_INDEX).bufferedReader().use { reader ->
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PremadeEntry.serializer()),
                    reader.readText(),
                )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun clonePremadeAssetToClipboardStore(
    context: android.content.Context,
    slug: String,
): Uri? = withContext(Dispatchers.IO) {
    try {
        val bitmap = context.assets.open("$PREMADE_DIR/$slug.png")
            .use { android.graphics.BitmapFactory.decodeStream(it) }
            ?: return@withContext null
        val cache = java.io.File(context.cacheDir, "premade_${slug}_${System.nanoTime()}.webp")
        java.io.FileOutputStream(cache).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
        }
        val cloned = cloneToClipboardStore(context, Uri.fromFile(cache), "image/webp")
        cache.delete()
        cloned
    } catch (e: Exception) {
        flogDebug { "clonePremadeAssetToClipboardStore failed: ${e.message}" }
        null
    }
}

private data class ResolvedSticker(
    val ref: StickerRef,
    val model: Uri,
    val mime: String,
    val description: String,
)


@Composable
private fun CollectionGrid(
    refs: List<StickerRef>,
    emptyKind: EmptyKind,
    fg: Color,
    accent: Color,
    favoriteKeys: Set<String>,
    resolve: (StickerRef) -> ResolvedSticker?,
    onPick: (StickerRef, ResolvedSticker) -> Unit,
    onToggleFavorite: (StickerRef) -> Unit,
    onRemoveRecent: ((StickerRef) -> Unit)?,
) {
    val resolved = remember(refs) { refs.mapNotNull { resolve(it)?.let { r -> it to r } } }
    if (resolved.isEmpty()) {
        StickerEmptyState(kind = emptyKind, fg = fg, accent = accent)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(resolved, key = { it.first.key }) { (ref, sticker) ->
            StickerTile(
                model = sticker.model,
                fg = fg,
                accent = accent,
                isFavorited = ref.key in favoriteKeys,
                onTap = { onPick(ref, sticker) },
                onToggleFavorite = { onToggleFavorite(ref) },
                onRemoveRecent = if (onRemoveRecent != null) { { onRemoveRecent(ref) } } else null,
                onDelete = null,
            )
        }
    }
}

@Composable
private fun StickerEmptyState(kind: EmptyKind, fg: Color, accent: Color) {
    val (icon, headline, helper) = when (kind) {
        EmptyKind.Recents -> Triple(
            Icons.Outlined.Schedule,
            "No recent stickers yet",
            "Stickers you send appear here, newest first.",
        )
        EmptyKind.Favorites -> Triple(
            Icons.Outlined.StarBorder,
            "No favorites yet",
            "Long-press a sticker and tap “Add to favorites.”",
        )
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = headline,
                color = fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = helper,
                color = fg.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StickerTile(
    model: Any,
    fg: Color,
    accent: Color,
    isFavorited: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveRecent: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuOpen by remember(model) { mutableStateOf(false) }
    val inputFeedbackController = com.noxquill.rewordium.keyboard.ime.input
        .LocalInputFeedbackController.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(fg.copy(alpha = 0.06f))
            .pointerInput(model) {
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
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(6.dp),
        )
        if (isFavorited) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorited",
                    tint = accent,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        // Gboard-style long-press menu — anchored to the tile.
        // `focusable = false` is critical: the default M3 DropdownMenu sets
        // focusable=true, which steals focus from the IME's input view and
        // the system kills the keyboard the moment the menu appears.
        // Outside-tap dismissal still works without focus.
        val mediaStyle = org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery(
            com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi.Media.elementName
        )
        val containerBg = mediaStyle.background(default = androidx.compose.material3.MaterialTheme.colorScheme.surface)

        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                surface = containerBg,
                onSurface = fg,
                surfaceVariant = containerBg,
                onSurfaceVariant = fg.copy(alpha = 0.8f),
                primary = accent,
                surfaceTint = Color.Transparent
            )
        ) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.background(containerBg)
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
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Delete sticker") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserGrid(
    entries: List<UserStickerStore.Entry>,
    fg: Color,
    accent: Color,
    favoriteKeys: Set<String>,
    onAddClick: () -> Unit,
    onPick: (UserStickerStore.Entry) -> Unit,
    onToggleFavorite: (UserStickerStore.Entry) -> Unit,
    onDelete: (UserStickerStore.Entry) -> Unit,
) {
    val context = LocalContext.current
    val dim = fg.copy(alpha = 0.55f)

    // First-run / empty-state — show a single big "Add sticker" CTA and a
    // short helper line so the user doesn't sit there wondering whether
    // the panel is broken when the grid is empty.
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.22f))
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add sticker",
                    tint = fg,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No stickers yet",
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap + to import an image. Long-press a sticker to favorite or remove it.",
                color = dim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember(entries) {
        entries.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    val filteredEntries = remember(entries, selectedTag) {
        if (selectedTag == null) {
            entries
        } else {
            entries.filter { entry ->
                entry.tags.any { it.equals(selectedTag, ignoreCase = true) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    CustomChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        fg = fg,
                        accent = accent,
                    ) {
                        Text("All")
                    }
                }
                items(allTags) { tag ->
                    CustomChip(
                        selected = selectedTag?.equals(tag, ignoreCase = true) == true,
                        onClick = {
                            selectedTag = if (selectedTag?.equals(tag, ignoreCase = true) == true) null else tag
                        },
                        fg = fg,
                        accent = accent,
                    ) {
                        Text(tag)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (selectedTag == null) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.20f))
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add sticker",
                            tint = fg,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            items(filteredEntries, key = { it.id }) { entry ->
                val store = remember { UserStickerStore.get(context) }
                val file = store.fileFor(entry)
                val refKey = StickerRef.User(entry.id).key
                StickerTile(
                    model = Uri.fromFile(file),
                    fg = fg,
                    accent = accent,
                    isFavorited = refKey in favoriteKeys,
                    onTap = { onPick(entry) },
                    onToggleFavorite = { onToggleFavorite(entry) },
                    onRemoveRecent = null,
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

@Composable
private fun WhatsAppGrid(
    pack: WhatsAppStickerReader.Pack,
    fg: Color,
    accent: Color,
    favoriteKeys: Set<String>,
    onPick: (WhatsAppStickerReader.Sticker) -> Unit,
    onToggleFavorite: (WhatsAppStickerReader.Sticker) -> Unit,
) {
    val dim = fg.copy(alpha = 0.55f)
    if (pack.stickers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This pack is empty.",
                color = dim,
                fontSize = 13.sp,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(pack.stickers, key = { it.uri.toString() }) { sticker ->
            val refKey = StickerRef.WhatsApp(sticker.uri.toString(), sticker.emojis).key
            StickerTile(
                model = sticker.uri,
                fg = fg,
                accent = accent,
                isFavorited = refKey in favoriteKeys,
                onTap = { onPick(sticker) },
                onToggleFavorite = { onToggleFavorite(sticker) },
                onRemoveRecent = null,
                onDelete = null,
            )
        }
    }
}

@Composable
private fun CommunityGrid(
    stickers: List<KlipyClient.StickerResult>,
    fg: Color,
    accent: Color,
    onPick: (KlipyClient.StickerResult) -> Unit,
    onSearch: (String) -> Unit,
) {
    val dim = fg.copy(alpha = 0.55f)
    if (stickers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No community stickers found.",
                color = dim,
                fontSize = 13.sp,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(stickers, key = { it.id }) { sticker ->
            StickerTile(
                model = sticker.previewUrl,
                fg = fg,
                accent = accent,
                isFavorited = false,
                onTap = { onPick(sticker) },
                onToggleFavorite = {},
                onRemoveRecent = null,
                onDelete = null,
            )
        }
    }
}

private val stickerDownloadClient = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

private suspend fun downloadStickerAndStore(
    context: android.content.Context,
    url: String,
): Uri? = withContext(Dispatchers.IO) {
    try {
        val response = stickerDownloadClient.newCall(Request.Builder().url(url).build()).execute()
        response.use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body ?: return@withContext null
            val cache = java.io.File(context.cacheDir, "sticker_dl_${System.nanoTime()}.tmp")
            cache.outputStream().use { out -> body.byteStream().copyTo(out) }
            val values = android.content.ContentValues(3).apply {
                put(ClipboardMediaProvider.Columns.MediaUri, Uri.fromFile(cache).toString())
                put(ClipboardMediaProvider.Columns.MimeTypes, "image/webp")
                put(android.provider.OpenableColumns.DISPLAY_NAME, "sticker")
            }
            val result = context.contentResolver.insert(ClipboardMediaProvider.IMAGE_CLIPS_URI, values)
            cache.delete()
            result
        }
    } catch (e: Exception) {
        flogDebug { "downloadStickerAndStore failed: ${e.message}" }
        null
    }
}

/**
 * Copies a file/content URI's bytes into [ClipboardFileStorage] **and**
 * registers the entry with [ClipboardMediaProvider] so that `getType(uri)`
 * returns the correct MIME type. Without this registration, apps like
 * WhatsApp reject the content because `getType()` returns null.
 */
private suspend fun cloneToClipboardStore(
    context: android.content.Context,
    source: Uri,
    mimeType: String = "image/webp",
): Uri? = withContext(Dispatchers.IO) {
    try {
        val values = android.content.ContentValues(3).apply {
            put(ClipboardMediaProvider.Columns.MediaUri, source.toString())
            put(ClipboardMediaProvider.Columns.MimeTypes, mimeType)
            put(android.provider.OpenableColumns.DISPLAY_NAME, "sticker")
        }
        context.contentResolver.insert(ClipboardMediaProvider.IMAGE_CLIPS_URI, values)
    } catch (e: Exception) {
        flogDebug { "cloneToClipboardStore failed: ${e.message}" }
        null
    }
}
