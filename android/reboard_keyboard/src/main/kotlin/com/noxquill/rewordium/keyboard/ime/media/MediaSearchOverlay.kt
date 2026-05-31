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

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.ime.media.gif.KlipyClient
import com.noxquill.rewordium.keyboard.ime.media.sticker.UserStickerStore
import com.noxquill.rewordium.keyboard.ime.media.sticker.WhatsAppStickerReader
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardFileStorage
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardMediaProvider
import com.noxquill.rewordium.keyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Overlay that stacks above the QWERTY when the user activates search from
 * the GIF or sticker panel. Mirrors [EmojiSearchOverlay] visually so the
 * experience feels uniform across emoji / GIF / sticker — same back chip,
 * same blinking-caret pill, same M3 surfaces. The grid below the pill flips
 * between a KLIPY-backed GIF grid and a user+WhatsApp sticker grid based on
 * [KeyboardManager.mediaSearchMode].
 *
 * The QWERTY keyboard rendered below this overlay (by FlorisImeService) is
 * what feeds keystrokes into [KeyboardManager.mediaSearchQuery] — same path
 * that the emoji overlay uses.
 */
@Composable
fun MediaSearchOverlay() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    val inputFeedbackController = LocalInputFeedbackController.current

    val mode by keyboardManager.mediaSearchMode.collectAsState()
    val query by keyboardManager.mediaSearchQuery.collectAsState()

    val containerStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val pillStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionTile.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val enterKeyStyle = rememberSnyggThemeQuery(
        elementName = FlorisImeUi.Key.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to KeyCode.ENTER.toString()),
    )
    val pillBg = pillStyle.background(default = MaterialTheme.colorScheme.surfaceContainerHigh)
    val pillFg = keyStyle.foreground(default = MaterialTheme.colorScheme.onSurface)
    val accent = enterKeyStyle.background(default = MaterialTheme.colorScheme.primary)
    val cardBg = pillBg.copy(alpha = 0.85f)
    val dividerColor = pillFg.copy(alpha = 0.10f)

    val titleText = when (mode) {
        KeyboardManager.MediaSearchMode.GIF -> "Search GIFs"
        KeyboardManager.MediaSearchMode.STICKER -> "Search stickers"
        else -> "Search"
    }

    SnyggBox(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        keyboardManager.endMediaSearch()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = pillBg,
                        contentColor = pillFg,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to media panel",
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = titleText,
                    color = pillFg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 6.dp),
                ) {
                    when (mode) {
                        KeyboardManager.MediaSearchMode.GIF -> GifSearchResults(
                            query = query,
                            fg = pillFg,
                            accent = accent,
                            onGifPicked = { uri, description ->
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                editorInstance.commitMedia(
                                    uri = uri,
                                    mimeType = "image/gif",
                                    description = description.ifBlank { "GIF" },
                                )
                                keyboardManager.clearEmojiSearch()
                            },
                        )
                        KeyboardManager.MediaSearchMode.STICKER -> StickerSearchResults(
                            query = query,
                            fg = pillFg,
                            accent = accent,
                            onStickerPicked = { uri, mime, description ->
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                editorInstance.commitMedia(
                                    uri = uri,
                                    mimeType = mime,
                                    description = description.ifBlank { "Sticker" },
                                )
                                keyboardManager.clearEmojiSearch()
                            },
                        )
                        else -> Unit
                    }
                }

                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )

                SearchPill(
                    query = query,
                    pillBg = pillBg,
                    pillFg = pillFg,
                    accent = accent,
                    onClear = {
                        inputFeedbackController.keyPress(TextKeyData.DELETE)
                        keyboardManager.clearEmojiSearch()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchPill(
    query: String,
    pillBg: Color,
    pillFg: Color,
    accent: Color,
    onClear: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caret-alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 2.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = pillFg.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (query.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .alpha(caretAlpha)
                            .background(accent),
                    )
                } else {
                    Text(text = query, color = pillFg, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .alpha(caretAlpha)
                            .background(accent),
                    )
                }
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = pillFg.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GifSearchResults(
    query: String,
    fg: Color,
    accent: Color,
    onGifPicked: (android.net.Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val client = remember { KlipyClient() }
    val scope = rememberCoroutineScope()
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
    var results by remember { mutableStateOf<List<KlipyClient.GifResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val dim = fg.copy(alpha = 0.55f)

    if (!client.isConfigured) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("KLIPY_API_KEY not set", color = dim, fontSize = 13.sp)
        }
        return
    }
    LaunchedEffect(query) {
        loading = true
        results = client.search(query)
        loading = false
    }
    when {
        loading && results.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        results.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (query.isEmpty()) "Type to search GIFs" else "No GIFs",
                color = dim,
                fontSize = 13.sp,
            )
        }
        else -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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

@Composable
private fun StickerSearchResults(
    query: String,
    fg: Color,
    accent: Color,
    onStickerPicked: (android.net.Uri, String, String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { UserStickerStore.get(context) }
    val whatsapp = remember { WhatsAppStickerReader(context) }
    val scope = rememberCoroutineScope()
    val userEntries by store.entriesFlow.collectAsState()
    var waPacks by remember { mutableStateOf<List<WhatsAppStickerReader.Pack>>(emptyList()) }
    val dim = fg.copy(alpha = 0.55f)
    LaunchedEffect(Unit) {
        store.ensureLoaded()
        waPacks = whatsapp.packs()
    }

    // Combined sticker list: user stickers first (most recently touched),
    // then every WhatsApp sticker the user has installed. We don't have
    // text metadata on user stickers, so the query only filters WhatsApp
    // stickers via their emoji tags — a partial but useful fallback.
    val flatStickers = remember(userEntries, waPacks, query) {
        val q = query.trim().lowercase()
        val out = mutableListOf<StickerSearchItem>()
        val sortedUser = userEntries.sortedByDescending { it.t }
        if (q.isEmpty()) {
            sortedUser.forEach { out += StickerSearchItem.User(it) }
            waPacks.forEach { pack ->
                pack.stickers.forEach { sticker -> out += StickerSearchItem.WhatsApp(sticker) }
            }
        } else {
            // User stickers: match by id substring (best we have).
            sortedUser
                .filter { it.id.lowercase().contains(q) || it.mime.lowercase().contains(q) }
                .forEach { out += StickerSearchItem.User(it) }
            waPacks.forEach { pack ->
                pack.stickers
                    .filter { it.emojis.lowercase().contains(q) }
                    .forEach { sticker -> out += StickerSearchItem.WhatsApp(sticker) }
            }
        }
        out
    }

    if (flatStickers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (query.isEmpty()) "No stickers yet" else "No stickers match",
                color = dim,
                fontSize = 13.sp,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(flatStickers, key = { it.key }) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fg.copy(alpha = 0.05f))
                    .clickable {
                        scope.launch {
                            when (item) {
                                is StickerSearchItem.User -> {
                                    store.touch(item.entry)
                                    val file = store.fileFor(item.entry)
                                    if (!file.exists()) return@launch
                                    val uri = withContext(Dispatchers.IO) {
                                        cloneToClipboardStore(context, android.net.Uri.fromFile(file))
                                    } ?: return@launch
                                    onStickerPicked(uri, item.entry.mime, "Sticker")
                                }
                                is StickerSearchItem.WhatsApp -> {
                                    val uri = withContext(Dispatchers.IO) {
                                        cloneToClipboardStore(context, item.sticker.uri)
                                    } ?: return@launch
                                    onStickerPicked(uri, "image/webp", item.sticker.emojis.ifBlank { "Sticker" })
                                }
                            }
                        }
                    },
            ) {
                val model: Any = when (item) {
                    is StickerSearchItem.User -> android.net.Uri.fromFile(store.fileFor(item.entry))
                    is StickerSearchItem.WhatsApp -> item.sticker.uri
                }
                AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private sealed interface StickerSearchItem {
    val key: String
    data class User(val entry: UserStickerStore.Entry) : StickerSearchItem {
        override val key: String get() = "u:${entry.id}"
    }
    data class WhatsApp(val sticker: WhatsAppStickerReader.Sticker) : StickerSearchItem {
        override val key: String get() = "w:${sticker.uri}"
    }
}

private val sharedDownloadClient = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

private suspend fun downloadAndStore(
    context: android.content.Context,
    url: String,
): android.net.Uri? = withContext(Dispatchers.IO) {
    try {
        val response = sharedDownloadClient.newCall(Request.Builder().url(url).build()).execute()
        response.use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body ?: return@withContext null
            val id = System.nanoTime()
            val file = ClipboardFileStorage.getFileForId(context, id)
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
            android.content.ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, id)
        }
    } catch (e: Exception) {
        flogDebug { "MediaSearchOverlay.downloadAndStore failed: ${e.message}" }
        null
    }
}

private suspend fun cloneToClipboardStore(
    context: android.content.Context,
    source: android.net.Uri,
): android.net.Uri? = withContext(Dispatchers.IO) {
    try {
        val id = System.nanoTime()
        val file = ClipboardFileStorage.getFileForId(context, id)
        context.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        android.content.ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, id)
    } catch (e: Exception) {
        flogDebug { "MediaSearchOverlay.cloneToClipboardStore failed: ${e.message}" }
        null
    }
}
