/*
 * Copyright (C) 2021-2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.clipboard

import android.content.ContentUris
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.clipboardManager
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ClipboardItem
import com.noxquill.rewordium.keyboard.ime.clipboard.provider.ItemType
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaAction
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaActionSheet
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaEmptyState
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSectionHeader
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.MorphBlob
import com.noxquill.rewordium.keyboard.ime.media.ui.RepeatingDeleteButton
import com.noxquill.rewordium.keyboard.ime.media.ui.SpinningShape
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.Tone
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.lib.observeAsTransformingState
import com.noxquill.rewordium.keyboard.lib.util.NetworkUtils
import dev.patrickgold.jetpref.datastore.model.observeAsState
import java.time.Instant
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.stringRes

const val CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO: Int = 0

/**
 * The clipboard panel, on the same Material 3 Expressive kit as the media panels: a header with a morphing
 * clipboard badge and expressive icon buttons, filter chips, and the history as pressable tonal cards in a
 * staggered grid (pinned, recent and older, each under its own pill). A long press opens a card with the
 * clip's actions; clearing everything asks first, in a card of its own.
 */
@Composable
fun ClipboardInputLayout(
    modifier: Modifier = Modifier,
) = MediaExpressiveTheme {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val feedback = rememberMediaFeedback()
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    val historyEnabled by prefs.clipboard.historyEnabled.observeAsState()

    var filtersShown by remember { mutableStateOf(false) }
    val activeFilters = remember { mutableStateSetOf<ItemType>() }
    val unfilteredHistory by clipboardManager.historyFlow.collectAsState()
    val history = remember(unfilteredHistory, activeFilters.toSet()) {
        if (activeFilters.isEmpty()) unfilteredHistory else ClipboardHistory(unfilteredHistory.all.filter { it.type in activeFilters })
    }

    var actionsFor by remember(history) { mutableStateOf<ClipboardItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(activeFilters.toSet()) { gridState.scrollToItem(0) }
    LaunchedEffect(filtersShown) { if (!filtersShown) activeFilters.clear() }

    val canAct = !deviceLocked && historyEnabled && actionsFor == null && !confirmClear

    Column(
        modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        ClipboardHeader(
            filtersShown = filtersShown,
            canClear = canAct && history.all.isNotEmpty(),
            canFilter = canAct && unfilteredHistory.all.isNotEmpty(),
            onBack = {
                feedback.tap()
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            },
            onClear = {
                feedback.reveal()
                confirmClear = true
            },
            onToggleFilters = {
                feedback.tick()
                filtersShown = !filtersShown
            },
            onDelete = {
                feedback.delete()
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
            },
            onDeleteRepeat = {
                feedback.deleteRepeat()
                keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
            },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                deviceLocked -> MediaEmptyState(
                    icon = Symbols.Lock,
                    title = stringRes(R.string.clipboard__locked__title),
                    message = stringRes(R.string.clipboard__locked__message),
                    shape = MaterialShapes.Cookie6Sided,
                )
                !historyEnabled -> MediaEmptyState(
                    icon = Symbols.ContentPasteOff,
                    title = stringRes(R.string.clipboard__disabled__title),
                    message = stringRes(R.string.clipboard__disabled__message),
                    shape = MaterialShapes.SoftBurst,
                ) {
                    Button(
                        onClick = {
                            feedback.tap()
                            scope.launch { prefs.clipboard.historyEnabled.set(true) }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringRes(R.string.clipboard__disabled__enable_button))
                    }
                }
                history.all.isEmpty() && activeFilters.isEmpty() -> MediaEmptyState(
                    icon = Symbols.ContentPaste,
                    title = stringRes(R.string.clipboard__empty__title),
                    message = stringRes(R.string.clipboard__empty__message),
                    shape = MaterialShapes.Clover4Leaf,
                )
                else -> Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = filtersShown,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                    ) {
                        FilterChips(activeFilters) { type ->
                            feedback.tick()
                            if (!activeFilters.add(type)) activeFilters.remove(type)
                        }
                    }
                    HistoryGrid(
                        history = history,
                        state = gridState,
                        onPaste = { item ->
                            feedback.tap()
                            clipboardManager.pasteItem(item)
                        },
                        onActions = { item ->
                            feedback.reveal()
                            actionsFor = item
                        },
                    )
                }
            }

            val selected = actionsFor
            val formatter = LocalLocalizedDateTimeFormatter.current
            MediaActionSheet(
                visible = selected != null,
                onDismiss = { actionsFor = null },
                caption = selected?.let { formatter.format(Instant.ofEpochMilli(it.creationTimestampMs)) },
                actions = selected?.let { item ->
                    listOf(
                        MediaAction(Symbols.ContentPasteGo, stringRes(R.string.clip__paste_item), primary = true) {
                            feedback.tap()
                            clipboardManager.pasteItem(item)
                            actionsFor = null
                        },
                        MediaAction(
                            if (item.isPinned) Symbols.PushPin else Symbols.PushPinFilled,
                            stringRes(if (item.isPinned) R.string.clip__unpin_item else R.string.clip__pin_item),
                        ) {
                            feedback.tick()
                            if (item.isPinned) clipboardManager.unpinClip(item) else clipboardManager.pinClip(item)
                            actionsFor = null
                        },
                        MediaAction(Symbols.Delete, stringRes(R.string.clip__delete_item)) {
                            feedback.tick()
                            clipboardManager.deleteClip(item, onlyIfUnpinned = false)
                            actionsFor = null
                        },
                    )
                }.orEmpty(),
            ) {
                selected?.let { ClipPreview(it) }
            }

            ClearAllCard(
                visible = confirmClear,
                filtered = filtersShown && activeFilters.isNotEmpty(),
                onDismiss = { confirmClear = false },
                onConfirm = {
                    feedback.tap()
                    clipboardManager.clearExactHistory(history.unpinned)
                    context.showShortToastSync(R.string.clipboard__cleared_history)
                    confirmClear = false
                },
            )
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ClipboardHeader(
    filtersShown: Boolean,
    canClear: Boolean,
    canFilter: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onToggleFilters: () -> Unit,
    onDelete: () -> Unit,
    onDeleteRepeat: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(42.dp)) {
            Icon(Symbols.ArrowBack, contentDescription = "Back to the keyboard", modifier = Modifier.size(22.dp))
        }
        // The badge turns into the filter shape while filters are open.
        MorphBlob(
            shape = if (filtersShown) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie9Sided,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Symbols.ContentPaste, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(19.dp))
        }
        Text(
            stringRes(R.string.clipboard__header_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = onClear,
            enabled = canClear,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(42.dp),
        ) {
            Icon(Symbols.DeleteSweep, contentDescription = "Clear history", modifier = Modifier.size(22.dp))
        }
        FilledTonalIconToggleButton(
            checked = filtersShown,
            onCheckedChange = { onToggleFilters() },
            enabled = canFilter || filtersShown,
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(42.dp),
        ) {
            Icon(if (filtersShown) Symbols.FilterListOff else Symbols.FilterList, contentDescription = "Filter", modifier = Modifier.size(22.dp))
        }
        RepeatingDeleteButton(
            onDelete = onDelete,
            onRepeat = onDeleteRepeat,
            modifier = Modifier.width(54.dp).height(42.dp),
        )
    }
}

@Composable
private fun FilterChips(active: Set<ItemType>, onToggle: (ItemType) -> Unit) {
    val tones = rememberTones()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val filters = listOf(
            Triple(ItemType.TEXT, "Text", Symbols.TextFields),
            Triple(ItemType.IMAGE, "Images", Symbols.Image),
            Triple(ItemType.VIDEO, "Videos", Symbols.Videocam),
        )
        filters.forEachIndexed { index, (type, label, icon) ->
            item(key = type.name) {
                MediaChip(
                    selected = type in active,
                    onClick = { onToggle(type) },
                    label = label,
                    icon = icon,
                    tone = tones.at(index),
                )
            }
        }
    }
}

// ── History ──────────────────────────────────────────────────────────────────

@Composable
private fun HistoryGrid(
    history: ClipboardHistory,
    state: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    onPaste: (ClipboardItem) -> Unit,
    onActions: (ClipboardItem) -> Unit,
) {
    val prefs by FlorisPreferenceStore
    val tones = rememberTones()
    val cells by prefs.clipboard.historyNumGridColumns().observeAsTransformingState { columns ->
        if (columns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(columns)
    }

    fun LazyStaggeredGridScope.section(items: List<ClipboardItem>, key: String, title: @Composable () -> String, icon: ImageVector, tone: Tone, shape: Int) {
        if (items.isEmpty()) return
        item(key = key, span = StaggeredGridItemSpan.FullLine) {
            MediaSectionHeader(title(), icon = icon, tone = tone, shape = MediaShapes.blob(shape))
        }
        itemsIndexed(items, key = { _, item -> "$key-${item.id}" }) { index, item ->
            ClipCard(
                item = item,
                tone = if (item.isPinned) tone else tones.at(index),
                onClick = { onPaste(item) },
                onLongClick = { onActions(item) },
            )
        }
    }

    LazyVerticalStaggeredGrid(
        columns = cells,
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 10.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        section(history.pinned, "pinned", { stringRes(R.string.clipboard__group_pinned) }, Symbols.PushPinFilled, tones.at(2), 3)
        section(history.recent, "recent", { stringRes(R.string.clipboard__group_recent) }, Symbols.HistoryFilled, tones.at(0), 0)
        section(history.other, "other", { stringRes(R.string.clipboard__group_other) }, Symbols.Inventory2, tones.at(1), 2)
    }
}

/** A clip as a pressable tonal card: tap pastes, a long press opens its actions. */
@Composable
private fun ClipCard(item: ClipboardItem, tone: Tone, onClick: () -> Unit, onLongClick: () -> Unit) {
    val container = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tone.container, if (item.isPinned) 0.7f else 0.32f)
    MediaTile(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        color = container,
        corner = 22.dp,
        pressedCorner = 34.dp,
        label = if (item.type == ItemType.TEXT) null else "Clipboard ${item.type.name.lowercase()}",
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (item.type == ItemType.IMAGE || item.type == ItemType.VIDEO) {
                ClipThumbnail(item, Modifier.fillMaxWidth().heightIn(min = 72.dp))
            } else {
                ClipText(item, maxLines = 6, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp))
            }
            if (item.isPinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(tone.content, MaterialShapes.Cookie9Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Symbols.PushPinFilled, "Pinned", tint = tone.container, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun ClipText(item: ClipboardItem, maxLines: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val raw = remember(item) { item.stringRepresentation() }
    // A huge clip (a whole document) would lay out every line and stall the panel; show the start.
    val shown = remember(item, maxLines) {
        val limit = if (maxLines > 10) 20_000 else 1_500
        item.displayText(context).let { if (it.length > limit) it.take(limit) + "…" else it }
    }
    val kind = remember(raw) { clipKind(raw) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (kind != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, kind.shape.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(kind.icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringRes(kind.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            shown,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class ClipKind(val icon: ImageVector, val label: Int, val shape: androidx.graphics.shapes.RoundedPolygon)

/** E-mail, link or phone number. The patterns backtrack badly on long text, which is none of those anyway. */
private fun clipKind(text: String): ClipKind? = when {
    text.length > 2_048 -> null
    NetworkUtils.isEmailAddress(text) -> ClipKind(Symbols.Mail, R.string.clipboard__item_description_email, MaterialShapes.Cookie6Sided)
    NetworkUtils.isUrl(text) -> ClipKind(Symbols.Link, R.string.clipboard__item_description_url, MaterialShapes.Clover4Leaf)
    NetworkUtils.isPhoneNumber(text) -> ClipKind(Symbols.Call, R.string.clipboard__item_description_phone, MaterialShapes.Sunny)
    else -> null
}

/** An image or video clip, decoded off the main thread at tile size (see [ClipThumbnails]). */
@Composable
private fun ClipThumbnail(item: ClipboardItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isVideo = item.type == ItemType.VIDEO
    val id = item.uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: -1L
    val thumbnail by produceState(initialValue = ClipThumbnails.cached(id), id) {
        if (value == null) value = ClipThumbnails.load(context, id, isVideo)
    }
    Box(modifier) {
        val loaded = thumbnail
        when {
            loaded == null -> Box(Modifier.fillMaxWidth().height(96.dp))
            loaded.isSuccess -> Image(
                bitmap = loaded.getOrThrow(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(
                loaded.exceptionOrNull()?.message ?: "Couldn’t show this clip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (isVideo) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Symbols.Videocam, "Video", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** What the long-press card shows of a clip. */
@Composable
private fun BoxScope.ClipPreview(item: ClipboardItem) {
    if (item.type == ItemType.IMAGE || item.type == ItemType.VIDEO) {
        ClipThumbnail(item, Modifier.matchParentSize())
    } else {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            ClipText(item, maxLines = 40)
        }
    }
}

// ── Clear all ────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.ClearAllCard(visible: Boolean, filtered: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 360.dp)
                    .animateEnterExit(
                        enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f),
                    )
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpinningShape(MaterialShapes.SoftBurst, MaterialTheme.colorScheme.errorContainer, Modifier.size(64.dp)) {
                        Icon(Symbols.DeleteSweep, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringRes(
                            if (filtered) R.string.clipboard__confirm_clear_filtered_history__message
                            else R.string.clipboard__confirm_clear_unfiltered_history__message,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                            Text(stringRes(R.string.action__no))
                        }
                        Button(
                            onClick = onConfirm,
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringRes(R.string.action__yes))
                        }
                    }
                }
            }
        }
    }
}

