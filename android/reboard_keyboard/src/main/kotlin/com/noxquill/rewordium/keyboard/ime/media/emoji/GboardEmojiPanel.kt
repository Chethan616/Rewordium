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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.subtypeManager
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
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    // Snapshot history once per panel mount. The panel rebuilds on
    // open/close so we don't need a reactive subscription — when the user
    // picks an emoji and the panel re-renders, the next snapshot includes
    // the newly-recorded usage. Saves a jetpref subscription on the hot path.
    val historyData = remember { prefs.emoji.historyData.get() }

    // Categories we render in the pager (Recently-Used first, then the
    // standard groups in the order Gboard shows them).
    val pagerCategories = remember {
        EmojiCategory.entries.toList()
    }
    // Gboard opens on the clock/recents tab when there's any history, otherwise
    // smileys. Mirrors the reference screenshot where the clock is the active
    // chip on launch.
    val initialPage = remember(historyData) {
        val recentsIdx = pagerCategories.indexOf(EmojiCategory.RECENTLY_USED)
        val smileysIdx = pagerCategories.indexOf(EmojiCategory.SMILEYS_EMOTION)
            .coerceAtLeast(0)
        if (recentsIdx >= 0 && (historyData.pinned.isNotEmpty() || historyData.recent.isNotEmpty())) {
            recentsIdx
        } else {
            smileysIdx
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pagerCategories.size }

    // Surface colors pulled from the active Snygg theme so the panel
    // matches whatever skin the user has set.
    val mediaStyle = rememberSnyggThemeQuery(FlorisImeUi.Media.elementName)
    val containerBg = mediaStyle.background(default = MaterialTheme.colorScheme.surface)
    val onContainer = mediaStyle.foreground(default = MaterialTheme.colorScheme.onSurface)

    SnyggBox(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EmojiPanelHeader(
                categories = pagerCategories,
                activeIndex = pagerState.currentPage,
                fg = onContainer,
                onBackClick = {
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onSearchClick = {
                    keyboardManager.beginEmojiSearch()
                },
                onCategoryClick = { idx ->
                    scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )

            // Per-category emoji grid, swipeable.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageIndex ->
                val category = pagerCategories[pageIndex]
                CategoryEmojiGrid(
                    category = category,
                    fullEmojiMappings = fullEmojiMappings,
                    historyData = historyData,
                    onEmojiPicked = { emoji ->
                        editorInstance.commitText(emoji.value)
                        scope.launch {
                            EmojiHistoryHelper.markEmojiUsed(prefs, emoji)
                        }
                    },
                )
            }

            EmojiPanelBottomBar(
                fg = onContainer,
                onAbcClick = {
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onDeleteClick = {
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
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (Int) -> Unit,
) {
    val chipBg = fg.copy(alpha = 0.08f)
    val pillBg = fg.copy(alpha = 0.08f)
    val activeChipBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

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

        // Search pill — taps trigger the existing emoji-search overlay
        // (keyboardManager.beginEmojiSearch). Pill takes ~40% of the
        // header's horizontal real estate to mirror Gboard's proportions.
        Box(
            modifier = Modifier
                .weight(0.42f)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = fg.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Search",
                    color = fg.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))

        // Category icons. Horizontal scroll so the full set fits even on
        // narrow devices. Active one gets a tinted disc background.
        LazyRow(
            modifier = Modifier.weight(0.58f),
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
                        tint = if (isActive) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Per-category vertical emoji grid. Recently-used pulls from the user's
 * history pref (pinned + recent, deduplicated). Everything else reads
 * straight from [EmojiData.byCategory].
 */
@Composable
private fun CategoryEmojiGrid(
    category: EmojiCategory,
    fullEmojiMappings: EmojiData,
    historyData: EmojiHistory,
    onEmojiPicked: (Emoji) -> Unit,
) {
    val emojis = remember(category, fullEmojiMappings, historyData) {
        if (category == EmojiCategory.RECENTLY_USED) {
            val combined = (historyData.pinned + historyData.recent).distinctBy { it.value }
            combined.map { EmojiSet(listOf(it)) }
        } else {
            fullEmojiMappings.byCategory[category].orEmpty()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(EMOJI_COLUMNS),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (category == EmojiCategory.RECENTLY_USED) {
            // Gboard prints a small "Recent emoji" label above the grid on
            // the clock tab. Full-width grid item so it spans all columns.
            item(span = { GridItemSpan(EMOJI_COLUMNS) }) {
                Text(
                    text = "Recent emoji",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                )
            }
        }
        items(items = emojis, key = { it.base().value }) { emojiSet ->
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

/**
 * Compact 6-slot action bar at the bottom of the panel, matching Gboard:
 * ABC | smiley(active) | GIF | sticker | emoticon | delete.
 * GIF / sticker / emoticon are placeholder visuals for now — we don't ship
 * those modes, so taps no-op. Smiley is the "you are here" indicator.
 */
@Composable
private fun EmojiPanelBottomBar(
    fg: Color,
    onAbcClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val activePillBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

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

        // Smiley active indicator
        Box(
            modifier = Modifier
                .height(32.dp)
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(activePillBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "😀",
                fontSize = 18.sp,
            )
        }

        // Placeholder slots — visually present so the bottom bar matches
        // Gboard's shape, but they currently no-op since we don't ship
        // GIF / sticker / emoticon modes. Each is just a labelled dimmed
        // text chip; replacing with real icons is a future polish pass.
        BottomBarSlot(label = "GIF", fg = fg.copy(alpha = 0.55f))
        BottomBarSlot(label = "📑", fg = fg)
        BottomBarSlot(label = ":-)", fg = fg.copy(alpha = 0.55f))

        // Delete
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onDeleteClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Backspace,
                contentDescription = "Delete",
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomBarSlot(
    label: String,
    fg: Color,
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val EMOJI_COLUMNS = 9
