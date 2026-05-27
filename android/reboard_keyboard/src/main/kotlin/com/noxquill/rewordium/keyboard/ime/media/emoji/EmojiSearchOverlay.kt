/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.noxquill.rewordium.keyboard.ime.media.emoji

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.snapshotFlow
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
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Gboard-style emoji search overlay (Phase 7r.3).
 *
 * Renders ABOVE the smartbar + QWERTY when emoji search is active — the
 * keyboard itself stays mounted underneath so the user can keep typing into
 * the search field via normal key presses. Layout matches gboard_emoji_search.jpeg:
 *
 *  ┌──────────────────────────────────────────┐
 *  │ ◯←  Search emoji                          │  header (back + title)
 *  │ ╭──────────────────────────────────────╮ │
 *  │ │ 🙏 🎊 🍫 🌹 🤧 😭 💀 ▶︎               │ │  2-row scrollable strip,
 *  │ │ 🙌 🕊 😑 💥 🔴 🫥 🚗 …                │ │  rounded container
 *  │ │ ──────────────────────────────────── │ │  hairline divider
 *  │ │ 🔍 Search                            │ │  collapses to circle on scroll
 *  │ ╰──────────────────────────────────────╯ │
 *  └──────────────────────────────────────────┘
 *
 * UX details vs the previous iteration:
 *  - 2-row strip and search pill are wrapped in ONE rounded container with
 *    a hairline divider between them, so they read as one unit (Gboard does
 *    the same — the pill isn't a separate widget floating below).
 *  - **Pill collapses to a 40dp circle** while the strip is actively
 *    scrolling. Tapping the circle re-expands to full width. Frees up
 *    visual space for emoji browsing without losing the search affordance.
 *  - All colors driven by [rememberSnyggThemeQuery] — the overlay tracks
 *    whatever skin the user has chosen.
 *  - The cursor inside the search pill is a real 2dp animated bar so it
 *    doesn't masquerade as a trailing space character.
 */
@Composable
fun EmojiSearchOverlay() {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()

    val queryNullable by keyboardManager.emojiSearchQuery.collectAsState()
    val query = queryNullable.orEmpty()
    val trimmed = query.trim()

    // Locale-specific keyword file — root.txt has no keywords.
    var fullEmojiMappings by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(subtypeManager.activeSubtype) {
        val locale = subtypeManager.activeSubtype.primaryLocale
        val localized = EmojiData.get(context, locale)
        fullEmojiMappings = if (localized.byCategory.values.any { it.isNotEmpty() }) {
            localized
        } else {
            EmojiData.get(context, "ime/media/emoji/en.txt")
        }
    }

    val searchIndex = remember(fullEmojiMappings) {
        val out = ArrayList<Pair<EmojiSet, String>>(2048)
        for ((_, list) in fullEmojiMappings.byCategory) {
            for (set in list) {
                val base = set.base()
                val keyText = buildString {
                    append(base.name.lowercase())
                    base.keywords.forEach { kw ->
                        append(' ')
                        append(kw.lowercase())
                    }
                }
                out.add(set to keyText)
            }
        }
        out
    }

    val results = remember(trimmed, searchIndex) {
        if (trimmed.isEmpty()) emptyList()
        else searchIndex.asSequence()
            .filter { (_, key) -> key.contains(trimmed.lowercase()) }
            .map { it.first }
            .take(96)
            .toList()
    }

    val recentAndPopular = remember(query, fullEmojiMappings) {
        if (trimmed.isNotEmpty()) emptyList()
        else {
            val data = prefs.emoji.historyData.get()
            val recent = (data.pinned + data.recent).map { EmojiSet(listOf(it)) }
            if (recent.isNotEmpty()) {
                recent.take(40)
            } else {
                val smileys = fullEmojiMappings.byCategory[EmojiCategory.SMILEYS_EMOTION].orEmpty()
                smileys.take(40)
            }
        }
    }

    val displayed: List<EmojiSet> = if (results.isNotEmpty()) results else recentAndPopular

    // Reorder items for row-first fill in the 2-row LazyHorizontalGrid.
    // By default LazyHorizontalGrid fills column-first (top→bottom per
    // column). The user expects row-first: fill row 1 left→right, then
    // row 2. We interleave indices so the visual order is correct.
    val displayedRowFirst = remember(displayed) {
        val n = displayed.size
        if (n <= 2) return@remember displayed
        val cols = (n + 1) / 2 // ceil(n/2)
        val reordered = ArrayList<EmojiSet>(n)
        for (col in 0 until cols) {
            for (row in 0 until 2) {
                val srcIdx = row * cols + col
                if (srcIdx < n) reordered.add(displayed[srcIdx])
            }
        }
        reordered
    }

    // ── Snygg-driven palette ──────────────────────────────────────────────
    // Pull every color from the keyboard's snygg theme so the overlay
    // matches whatever skin is active. Smartbar = container BG; key style
    // = pill BG + foreground; the shift-focus accent is what the rest of
    // the keyboard uses for "active" markers (caps lock, selected
    // suggestion), so it's the closest thing to a brand color the theme
    // exposes — perfect for the cursor.
    val containerStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val pillStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionTile.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    // Enter key accent — the keyboard theme's "brand" color, used for
    // the blinking caret. Matches the accent used in GboardEmojiPanel.
    val enterKeyStyle = rememberSnyggThemeQuery(
        elementName = FlorisImeUi.Key.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to com.noxquill.rewordium.keyboard.ime.text.key.KeyCode.ENTER.toString()),
    )
    val containerBg = containerStyle.background(default = MaterialTheme.colorScheme.surface)
    val pillBg = pillStyle.background(default = MaterialTheme.colorScheme.surfaceContainerHigh)
    val pillFg = pillStyle.foreground(default = MaterialTheme.colorScheme.onSurface)
    val accent = enterKeyStyle.background(default = keyStyle.foreground(default = pillFg))
    // The rounded-rect container that wraps the 2-row strip + pill. We tone
    // it a notch darker than the smartbar background so it reads as a
    // distinct surface without looking like a floating modal.
    val cardBg = pillBg.copy(alpha = 0.85f)
    val dividerColor = pillFg.copy(alpha = 0.1f)

    // Pill expand/collapse state. Starts expanded. Collapses while the
    // emoji strip is actively scrolling (frees visual space); expands again
    // on tap (or when the user lifts the finger and the strip rests).
    var pillExpanded by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    pillExpanded = false
                } else {
                    // Give the user a brief moment after scroll-end before
                    // re-expanding so a quick flick doesn't whip the pill
                    // back open immediately.
                    delay(220)
                    pillExpanded = true
                }
            }
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
            // ─── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedRoundButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to emoji panel",
                    bg = pillBg,
                    fg = pillFg,
                    onClick = {
                        // Switch to MEDIA first while the overlay is still
                        // fully visible — this swaps the keyboard underneath
                        // from QWERTY back to the emoji panel, but the user
                        // can't see it because the overlay covers everything.
                        // THEN close the overlay (endEmojiSearch triggers
                        // AnimatedVisibility's exit: shrinkVertically from
                        // top ~160ms). The user sees the overlay slide up
                        // revealing the emoji panel already in place — zero
                        // lag, zero flash.
                        keyboardManager.activeState.imeUiMode = ImeUiMode.MEDIA
                        keyboardManager.endEmojiSearch()
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search emoji",
                    color = pillFg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ─── Card: strip + divider + pill ───────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(vertical = 6.dp),
            ) {
                // 2-row horizontally-scrollable emoji strip. Sized 96dp so
                // each cell gets a comfortable ~44dp tap target.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(horizontal = 6.dp),
                ) {
                    when {
                        displayedRowFirst.isNotEmpty() -> {
                            LazyHorizontalGrid(
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                rows = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(items = displayedRowFirst, key = { it.base().value }) { emojiSet ->
                                    Box(
                                        modifier = Modifier.size(width = 44.dp, height = 44.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        EmojiKey(
                                            emojiSet = emojiSet,
                                            emojiCompatInstance = null,
                                            preferredSkinTone = EmojiSkinTone.DEFAULT,
                                            onEmojiInput = { emoji ->
                                                // Commit directly so we don't
                                                // recurse through our own emoji-
                                                // search intercept.
                                                editorInstance.commitText(emoji.value)
                                                keyboardManager.clearEmojiSearch()
                                                scope.launch {
                                                    EmojiHistoryHelper.markEmojiUsed(prefs, emoji)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        trimmed.isNotEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No emojis match \"$trimmed\"",
                                    color = pillFg.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Type to search",
                                    color = pillFg.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // Hairline divider between strip and pill (visible in the
                // reference screenshot; helps the two read as one card).
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )

                // ─── Search pill ──────────────────────────────────
                // Collapses to a circle while the strip is being scrolled,
                // expands back when the scroll settles or the user taps it.
                CollapsiblePill(
                    expanded = pillExpanded,
                    onExpandRequest = { pillExpanded = true },
                    query = query,
                    pillBg = pillBg,
                    pillFg = pillFg,
                    accent = accent,
                    onClear = { keyboardManager.clearEmojiSearch() },
                )
            }
        }
    }
}

@Composable
private fun ThemedRoundButton(
    icon: ImageVector,
    contentDescription: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = bg,
            contentColor = fg,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Pill that shrinks to a search-icon circle when [expanded] is false. Tapping
 * the circle expands it back (via [onExpandRequest]). When expanded, shows
 * the query text + animated caret + clear button (if query non-empty).
 *
 * Width animation is driven by [animateDpAsState] so it ties into the same
 * frame budget as the rest of the overlay — no manual Animatable juggling.
 */
@Composable
private fun CollapsiblePill(
    expanded: Boolean,
    onExpandRequest: () -> Unit,
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

    // The pill animates between two visual modes:
    //   - expanded: full-width 40dp rounded rect with search icon + query + caret
    //   - collapsed: 40dp circle with just the search icon
    // We use a Box overlay so the position stays consistent (always at the
    // start) regardless of width.
    val horizontalPadding = animateDpAsState(
        targetValue = if (expanded) 12.dp else 0.dp,
        label = "pill-padding",
        animationSpec = tween(durationMillis = 180),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 2.dp, bottom = 4.dp),
    ) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .padding(horizontal = horizontalPadding.value),
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
                        // Empty state: just the blinking caret next to
                        // the search icon. No "Search" hint — cleaner,
                        // more minimal, the icon alone is a sufficient
                        // affordance.
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .alpha(caretAlpha)
                                .background(accent),
                        )
                    } else {
                        Text(
                            text = query,
                            color = pillFg,
                            fontSize = 14.sp,
                        )
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
        } else {
            // Collapsed: just a 40dp circle on the left with the search icon.
            // Tapping it re-expands the pill.
            FilledIconButton(
                onClick = onExpandRequest,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = pillBg,
                    contentColor = pillFg,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Expand search",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
