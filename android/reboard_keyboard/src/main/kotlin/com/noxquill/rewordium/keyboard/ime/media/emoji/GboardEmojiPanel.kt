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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
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
    var panelMode by remember { mutableStateOf(EmojiPanelMode.EMOJI) }

    LaunchedEffect(pagerState, categoryListState) {
        launch {
            snapshotFlow { pagerState.isScrollInProgress }
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
            EmojiPanelHeader(
                categories = pagerCategories,
                activeIndex = pagerState.currentPage,
                fg = onContainer,
                accent = accentColor,
                searchPillExpanded = searchPillExpanded,
                categoryListState = categoryListState,
                showCategories = panelMode == EmojiPanelMode.EMOJI,
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
                    scope.launch { pagerState.animateScrollToPage(idx) }
                },
                onSearchPillTapToExpand = {
                    searchPillExpanded = true
                }
            )

            when (panelMode) {
                EmojiPanelMode.EMOJI -> {
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
                        )
                    }
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
                onModeChange = { panelMode = it },
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
                        text = "Search",
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
                            tint = if (isActive) accent else fg.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
            label = "😀",
            fontSize = 18.sp,
            fg = fg,
            isActive = activeMode == EmojiPanelMode.EMOJI,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.EMOJI) }
        )

        // GIF slot — KLIPY-backed picker (Task D).
        BottomBarSlot(
            label = "GIF",
            fontSize = 13.sp,
            fg = if (activeMode == EmojiPanelMode.GIF) fg else fg.copy(alpha = 0.55f),
            isActive = activeMode == EmojiPanelMode.GIF,
            activeBg = activePillBg,
            onClick = { onModeChange(EmojiPanelMode.GIF) }
        )

        // Sticker slot — user-imported + WhatsApp packs (Task E).
        BottomBarSlot(
            icon = StickerIcon,
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

private val StickerIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Sticker",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addGroup(
            name = "translateGroup",
            translationY = 960f,
        )
        addPath(
            pathData = PathParser().parsePathString("M460-360q69 0 120-45t60-113l-320 90q26 32 62 50t78 18ZM294-510l106-30q4-28-14-49t-46-21q-25 0-42.5 17.5T280-550q0 11 4 21t10 19Zm240-70 106-30q5-28-13.5-49T580-680q-25 0-42.5 17.5T520-620q0 11 4 21t10 19Zm106 460H200q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v440L640-120Zm-40-80v-80q0-33 23.5-56.5T680-360h80v-400H200v560h400Zm0 0Zm-400 0v-560 560Z").toNodes(),
            fill = SolidColor(Color.White)
        )
        clearGroup()
    }.build()

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
