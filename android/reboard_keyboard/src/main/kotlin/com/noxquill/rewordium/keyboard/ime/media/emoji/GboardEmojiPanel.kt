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

package com.noxquill.rewordium.keyboard.ime.media.emoji

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.vectorResource
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import com.noxquill.rewordium.keyboard.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Gboard-style emoji panel (Phase 7r.1). Custom Compose layout matching
 * the reference screenshots the user provided:
 *
 *  ┌───────────────────────────────────────────────────────────────┐
 *  │ ◯←  ⏵ Search                  [🕒] 😀 👤 🐾 🍕 🍵 …          │ ← header
 *  ├───────────────────────────────────────────────────────────────┤
 *  │                                                               │
 *  │  Recent emoji                                                 │
 *  │  😀 😂 😅 🙏 …                                                │
 *  │                                                               │
 *  │  (category-paged grid via HorizontalPager)                    │
 *  │                                                               │
 *  ├───────────────────────────────────────────────────────────────┤
 *  │ ABC   [😀]   GIF   📑   :-)                          ⌫       │ ← bottom bar
 *  └───────────────────────────────────────────────────────────────┘
 *
 * Three Composables:
 *  - [GboardEmojiPanel]    — outer Column with header / pager / bottom-bar.
 *  - [EmojiPanelHeader]    — back chip + rounded search pill + category strip.
 *  - [EmojiPanelBottomBar] — compact 6-slot action row (ABC, smiley active,
 *    GIF/sticker/emoticon placeholders, delete).
 *
 * **Intentional omission: "Emoji Kitchen".** Gboard's combo-emoji row uses
 * proprietary Google combination art; we don't have those assets and they
 * aren't standard Unicode. Skipping is the correct move.
 *
 * The bottom bar replaces what `MediaInputLayout` used to wrap any emoji
 * panel with (giant ABC + delete buttons). When [ENABLE_GBOARD_EMOJI_PANEL]
 * is on, MediaInputLayout renders this panel taking the full IME height —
 * no outer wrapper bottom row.
 */
@Composable
fun GboardEmojiPanel(
    fullEmojiMappings: EmojiData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val subtypeManager by context.subtypeManager()
    val nlpManager by context.nlpManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val inputFeedbackController = LocalInputFeedbackController.current
    // Snapshot history once per panel mount. The panel rebuilds on
    // open/close so we don't need a reactive subscription — when the user
    // picks an emoji and the panel re-renders, the next snapshot includes
    // the newly-recorded usage. Saves a jetpref subscription on the hot path.
    val historyData = remember { prefs.emoji.historyData.get() }

    // Categories rendered as section headers stacked vertically — Gboard's
    // single-scroll layout. Recently-Used first (with the "Recent" label),
    // then the standard groups in display order.
    val pagerCategories = remember {
        EmojiCategory.entries.toList()
    }

    // Build the flat item list: each non-empty category produces a header
    // item (spanning all EMOJI_COLUMNS columns) followed by its emoji
    // cells. `sections` tracks each header's grid index so the chip strip
    // can `animateScrollToItem(headerIndex)` to jump to a section.
    val sections = remember(fullEmojiMappings, historyData) {
        buildList {
            var cursor = 0
            for (category in pagerCategories) {
                val emojis = if (category == EmojiCategory.RECENTLY_USED) {
                    (historyData.pinned + historyData.recent).distinctBy { it.value }
                        .map { EmojiSet(listOf(it)) }
                } else {
                    fullEmojiMappings.byCategory[category].orEmpty()
                }
                if (emojis.isEmpty()) continue
                val headerIndex = cursor
                add(EmojiSection(category, emojis, headerIndex))
                cursor += 1 + emojis.size
            }
        }
    }

    val lazyGridState = rememberLazyGridState()

    // Gboard opens on whichever section is first non-empty — usually
    // Recents if the user has history, otherwise Smileys.
    LaunchedEffect(sections) {
        if (sections.isNotEmpty()) {
            lazyGridState.scrollToItem(sections.first().headerIndex)
        }
    }

    // Track which section the user is currently looking at so the chip
    // strip highlights the right category. Picks the section whose header
    // is the most-recent one at or above the first visible item.
    val activeSectionIndex by remember(sections) {
        derivedStateOf {
            val firstVisible = lazyGridState.firstVisibleItemIndex
            val idx = sections.indexOfLast { it.headerIndex <= firstVisible }
            idx.coerceAtLeast(0)
        }
    }
    val activeChipIndex = remember(activeSectionIndex, sections) {
        val activeCat = sections.getOrNull(activeSectionIndex)?.category
            ?: return@remember 0
        pagerCategories.indexOf(activeCat).coerceAtLeast(0)
    }

    // Surface colors pulled from the active Snygg theme so the panel
    // matches whatever skin the user has set.
    val mediaStyle = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val containerBg = mediaStyle.background(default = MaterialTheme.colorScheme.surface)
    val onContainer = mediaStyle.foreground(default = MaterialTheme.colorScheme.onSurface)

    // Accent color from the keyboard's Enter key background — this is the
    // theme's "brand" color (green by default) that tints shift, enter, and
    // active indicators. Using it here replaces the hardcoded
    // MaterialTheme.colorScheme.primary (purple) that didn't match the
    // keyboard skin.
    val enterKeyStyle = rememberSnyggThemeQuery(
        elementName = FlorisImeUi.Key.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to KeyCode.ENTER.toString()),
    )
    val accentColor = enterKeyStyle.background(default = onContainer)

    // Search-pill collapse: collapses to a circle while the user swipes
    // through categories or tabs. Only re-expands if the user explicitly taps it.
    var searchPillExpanded by remember { mutableStateOf(true) }
    val categoryListState = rememberLazyListState()

    // Panel mode state — lifted here so the header can adapt its search pill.
    var panelMode by remember {
        mutableStateOf(
            try {
                EmojiPanelMode.valueOf(keyboardManager.activeState.activeMediaMode)
            } catch (e: Exception) {
                EmojiPanelMode.EMOJI
            }
        )
    }

    LaunchedEffect(lazyGridState, categoryListState) {
        launch {
            snapshotFlow { lazyGridState.isScrollInProgress }
                .collect { scrolling ->
                    if (scrolling) searchPillExpanded = false
                }
        }
        launch {
            snapshotFlow { categoryListState.isScrollInProgress }
                .collect { scrolling ->
                    if (scrolling) searchPillExpanded = false
                }
        }
    }

    SnyggBox(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Single header used across emoji / GIF / sticker / emoticon
            // modes. In emoji mode it carries the horizontal category strip
            // for the pager; in every other mode that strip collapses away
            // so the search pill takes precedence. The pill itself looks the
            // same in every mode — tapping routes to whichever
            // [KeyboardManager.MediaSearchMode] matches the active panel.
            val hintText = if (panelMode == EmojiPanelMode.GIF || panelMode == EmojiPanelMode.STICKER) {
                "Search on KLIPY"
            } else {
                "Search"
            }
            EmojiPanelHeader(
                categories = pagerCategories,
                activeIndex = activeChipIndex,
                fg = onContainer,
                accent = accentColor,
                searchPillExpanded = searchPillExpanded,
                categoryListState = categoryListState,
                showCategories = panelMode == EmojiPanelMode.EMOJI,
                hintText = hintText,
                onBackClick = {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onSearchClick = {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    val target = when (panelMode) {
                        EmojiPanelMode.GIF -> com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager.MediaSearchMode.GIF
                        EmojiPanelMode.STICKER -> com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager.MediaSearchMode.STICKER
                        else -> com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager.MediaSearchMode.EMOJI
                    }
                    keyboardManager.beginMediaSearch(target)
                },
                onCategoryClick = { idx ->
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    // Map the clicked category to its section header index
                    // in the flat grid, then scroll there. Categories with
                    // no emoji (rare — only RECENTLY_USED on a fresh
                    // install) get clamped to the first available section.
                    val targetCat = pagerCategories.getOrNull(idx) ?: return@EmojiPanelHeader
                    val section = sections.firstOrNull { it.category == targetCat }
                    val headerIdx = section?.headerIndex
                        ?: sections.firstOrNull()?.headerIndex
                        ?: return@EmojiPanelHeader
                    scope.launch { lazyGridState.animateScrollToItem(headerIdx) }
                },
                onSearchPillTapToExpand = {
                    searchPillExpanded = true
                }
            )

            when (panelMode) {
                EmojiPanelMode.EMOJI -> {
                    // Single vertical grid spanning every category, with
                    // a full-width header per section ("Recent emoji",
                    // "Smileys and emotions", …). Replaces the horizontal
                    // pager — Gboard's actual layout.
                    VerticalEmojiSections(
                        sections = sections,
                        gridState = lazyGridState,
                        fg = onContainer,
                        onEmojiPicked = { emoji ->
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            // Capture previousWord context BEFORE the commit
                            // changes textBeforeSelection — once the emoji is
                            // appended, the "previous word" we'd extract is the
                            // word + emoji, not what came before.
                            nlpManager.notifyEmojiPickedFromPalette(
                                subtypeManager.activeSubtype, emoji.value,
                            )
                            editorInstance.commitText(emoji.value)
                            scope.launch {
                                EmojiHistoryHelper.markEmojiUsed(prefs, emoji)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                EmojiPanelMode.EMOTICON -> {
                    Box(modifier = Modifier.weight(1f)) {
                        EmoticonGrid(
                            fg = onContainer,
                            onEmoticonPicked = { emoticon ->
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                editorInstance.commitText(emoticon)
                            }
                        )
                    }
                }
                EmojiPanelMode.GIF -> {
                    Box(modifier = Modifier.weight(1f)) {
                        com.noxquill.rewordium.keyboard.ime.media.gif.GifPanel(
                            fg = onContainer,
                            accent = accentColor,
                            onGifPicked = { uri, description ->
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                editorInstance.commitMedia(
                                    uri = uri,
                                    mimeType = "image/gif",
                                    description = description.ifBlank { "GIF" },
                                )
                            },
                        )
                    }
                }
                EmojiPanelMode.STICKER -> {
                    Box(modifier = Modifier.weight(1f)) {
                        com.noxquill.rewordium.keyboard.ime.media.sticker.StickerPanel(
                            fg = onContainer,
                            accent = accentColor,
                            onStickerPicked = { uri, mime, description ->
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                editorInstance.commitMedia(
                                    uri = uri,
                                    mimeType = mime,
                                    description = description.ifBlank { "Sticker" },
                                )
                            },
                        )
                    }
                }
            }

            EmojiPanelBottomBar(
                fg = onContainer,
                accent = accentColor,
                activeMode = panelMode,
                onModeChange = {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    panelMode = it
                    keyboardManager.activeState.activeMediaMode = it.name
                },
                onAbcClick = {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onDeleteClick = {
                    inputFeedbackController.keyPress(TextKeyData.DELETE)
                    keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
                },
            )
        }
    }
}

/**
 * Header row: back chip + rounded search pill + horizontal category-icon
 * strip. ~44dp tall to match Gboard's visual proportions.
 */
@Composable
private fun EmojiPanelHeader(
    categories: List<EmojiCategory>,
    activeIndex: Int,
    fg: Color,
    accent: Color,
    searchPillExpanded: Boolean,
    categoryListState: LazyListState,
    showCategories: Boolean,
    hintText: String,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (Int) -> Unit,
    onSearchPillTapToExpand: () -> Unit,
) {
    val chipBg = fg.copy(alpha = 0.08f)
    val pillBg = fg.copy(alpha = 0.08f)
    val activeChipBg = accent.copy(alpha = 0.25f)

    // Pill width animation is only meaningful in emoji mode — when the
    // category strip is hidden, the pill takes the full remaining row width
    // via weight(1f) instead.
    val pillWidth by animateDpAsState(
        targetValue = if (searchPillExpanded) 200.dp else 36.dp,
        animationSpec = tween(durationMillis = 200),
        label = "pill-width",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Back chip
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(chipBg)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

        // Search pill — same M3 surface across emoji / GIF / sticker /
        // emoticon. In emoji mode (showCategories=true) it collapses to a
        // 36dp circle while the user scrolls the category strip; in every
        // other mode it stretches to fill the row.
        val pillModifier = if (showCategories) {
            Modifier.width(pillWidth)
        } else {
            Modifier.weight(1f)
        }
        Box(
            modifier = pillModifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(pillBg)
                .clickable(onClick = {
                    if (searchPillExpanded || !showCategories) {
                        onSearchClick()
                    } else {
                        onSearchPillTapToExpand()
                    }
                })
                .padding(horizontal = if (searchPillExpanded || !showCategories) 12.dp else 0.dp),
            contentAlignment = if (searchPillExpanded || !showCategories) Alignment.CenterStart else Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = fg.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
                if (searchPillExpanded || !showCategories) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = hintText,
                        color = fg.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (showCategories) {
            Spacer(Modifier.width(8.dp))
            // Category icons. Horizontal scroll so the full set fits even on
            // narrow devices. Active one gets a tinted disc background.
            LazyRow(
                state = categoryListState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(categories) { idx, category ->
                    val isActive = idx == activeIndex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isActive) activeChipBg else Color.Transparent)
                            .clickable { onCategoryClick(idx) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = category.id,
                            tint = if (isActive) fg else fg.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}


/** Materialised representation of one section the vertical grid renders. */
private data class EmojiSection(
    val category: EmojiCategory,
    val emojis: List<EmojiSet>,
    /** Index of this section's header item in the flat [LazyVerticalGrid]. */
    val headerIndex: Int,
)

/** Display label for a section header (Gboard-style). */
private fun EmojiCategory.sectionTitle(): String = when (this) {
    EmojiCategory.RECENTLY_USED -> "Recent"
    EmojiCategory.SMILEYS_EMOTION -> "Smileys and emotions"
    EmojiCategory.PEOPLE_BODY -> "People"
    EmojiCategory.ANIMALS_NATURE -> "Animals and nature"
    EmojiCategory.FOOD_DRINK -> "Food and drink"
    EmojiCategory.TRAVEL_PLACES -> "Travel and places"
    EmojiCategory.ACTIVITIES -> "Activities"
    EmojiCategory.OBJECTS -> "Objects"
    EmojiCategory.SYMBOLS -> "Symbols"
    EmojiCategory.FLAGS -> "Flags"
}

/**
 * Vertical-scroll emoji panel — one big [LazyVerticalGrid] that stacks
 * every category, separated by full-width section headers. Replaces the
 * old [HorizontalPager] approach. The chip strip at the top calls
 * `lazyGridState.animateScrollToItem(section.headerIndex)` to jump,
 * and the strip's active-chip state mirrors `firstVisibleItemIndex`
 * → section lookup.
 */
@Composable
private fun VerticalEmojiSections(
    sections: List<EmojiSection>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    fg: Color,
    onEmojiPicked: (Emoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(EMOJI_COLUMNS),
        state = gridState,
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "header_${section.category.id}",
                span = { GridItemSpan(EMOJI_COLUMNS) },
            ) {
                Text(
                    text = section.category.sectionTitle(),
                    color = fg.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            items(
                items = section.emojis,
                key = { "${section.category.id}_${it.base().value}" },
            ) { emojiSet ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onEmojiPicked(emojiSet.base()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emojiSet.base().value,
                        fontSize = 24.sp,
                    )
                }
            }
        }
    }
}

/**
 * Compact 6-slot action bar at the bottom of the panel, matching Gboard:
 * ABC | smiley(active) | GIF | sticker | emoticon | delete.
 * GIF / sticker / emoticon are placeholder visuals for now — we don't ship
 * those modes, so taps no-op. Smiley is the "you are here" indicator.
 */
@Composable
private fun EmojiPanelBottomBar(
    fg: Color,
    accent: Color,
    activeMode: EmojiPanelMode,
    onModeChange: (EmojiPanelMode) -> Unit,
    onAbcClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val activePillBg = accent.copy(alpha = 0.25f)
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onAbcClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ABC",
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }

        // Smiley slot
        BottomBarSlot(
            icon = ImageVector.vectorResource(id = R.drawable.ic_custom_emoji),
            fg = fg,
            isActive = activeMode == EmojiPanelMode.EMOJI,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.EMOJI) }
        )

        // GIF slot — KLIPY-backed picker (Task D).
        BottomBarSlot(
            icon = ImageVector.vectorResource(id = R.drawable.ic_custom_gif),
            fg = if (activeMode == EmojiPanelMode.GIF) fg else fg.copy(alpha = 0.55f),
            isActive = activeMode == EmojiPanelMode.GIF,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.GIF) }
        )

        // Sticker slot — user-imported + WhatsApp packs (Task E).
        BottomBarSlot(
            icon = ImageVector.vectorResource(id = R.drawable.ic_custom_sticker),
            fg = fg,
            isActive = activeMode == EmojiPanelMode.STICKER,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.STICKER) }
        )

        // Emoticon slot
        BottomBarSlot(
            label = ":-)",
            fontSize = 13.sp,
            fg = if (activeMode == EmojiPanelMode.EMOTICON) fg else fg.copy(alpha = 0.55f),
            isActive = activeMode == EmojiPanelMode.EMOTICON,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.EMOTICON) }
        )

        // Delete
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onDeleteClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomBarSlot(
    label: String? = null,
    icon: ImageVector? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fg: Color,
    isActive: Boolean,
    activeBg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) activeBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            // Match the active/inactive treatment used for text labels so an
            // icon slot doesn't optically over-weight its neighbours. Filled
            // glyphs (the sticker mark in particular) read as bolder than
            // text at the same tint, so we dim the inactive variant.
            val iconTint = if (isActive) fg else fg.copy(alpha = 0.55f)
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        } else if (label != null) {
            Text(
                text = label,
                color = fg,
                fontSize = fontSize,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

private enum class EmojiPanelMode {
    EMOJI, EMOTICON, GIF, STICKER
}

private val EMOTICONS = listOf(
    ":-)", ":-(", ";-)", ":-D", ":-P", ":-O", "B-)", "O:-)", ":-*", "<3",
    "T_T", "-_-", "0_0", "\\m/", "¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "ʕ•ᴥ•ʔ",
    "(╯°□°）╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "(ง'̀-'́)ง",
    "^_^", "(*_*)", "(>_<)", "(^o^)", "(^_-)", "(-_-)zzz", "(;_;)",
    "(ToT)", "(=_=)", "d(-_^)", "(´･ω･`)", "(｀_´)", "(>_>)", "(<_<)",
    "ಠ_ಠ", "(ง •̀_•́)ง", "༼ つ ◕_◕ ༽つ", "(づ｡◕‿‿◕｡)づ", "♥‿♥", "ᕙ(⇀‸↼‶)ᕗ"
)

@Composable
private fun EmoticonGrid(
    fg: Color,
    onEmoticonPicked: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
    ) {
        items(EMOTICONS.size) { index ->
            val emoticon = EMOTICONS[index]
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clickable { onEmoticonPicked(emoticon) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoticon,
                    color = fg,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private const val EMOJI_COLUMNS = 9
