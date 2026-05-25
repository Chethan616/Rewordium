/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.subtypeManager
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Gboard-style emoji search overlay.
 *
 * Renders ABOVE the regular keyboard (smartbar + qwerty) when emoji search
 * is active. Total height ~190dp:
 *
 *  ┌────────────────────────────────────────┐
 *  │ ◯←   Search emoji                      │  44dp  header
 *  │ ╭──────────────────────────────────╮   │
 *  │ │ 🎉 🍫 🌹 😤 😭 🙏 💀 🎉 →        │   │  104dp 2-row scrollable grid
 *  │ │ 🙌 🕊 😑 💥 🔴 😯 🚗 →           │   │
 *  │ ╰──────────────────────────────────╯   │
 *  │ 🔍 happy▎                              │  40dp  search pill
 *  └────────────────────────────────────────┘
 *
 * Theme: pulls every color from the keyboard's snygg theme via
 * rememberSnyggThemeQuery so it matches whatever skin the user has set
 * (dark/light/AMOLED/custom). No hard-coded Material colors except as
 * fallbacks if a theme element is undefined.
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

    // CRITICAL: root.txt is the layout-only file (categories, ordering) and
    // contains NO names or keywords — every line is "emoji;;". Searching it
    // returns nothing for "lol" / "happy". The keyword data lives in the
    // locale-specific files (en.txt, de.txt, …). Load the active subtype's
    // locale file so search actually matches.
    var fullEmojiMappings by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(subtypeManager.activeSubtype) {
        val locale = subtypeManager.activeSubtype.primaryLocale
        val localized = EmojiData.get(context, locale)
        fullEmojiMappings = if (localized.byCategory.values.any { it.isNotEmpty() }) {
            localized
        } else {
            // Locale file missing → emergency fallback for an English layout.
            EmojiData.get(context, "ime/media/emoji/en.txt")
        }
    }

    // Build a flat searchable index once per catalog. Lowercased name +
    // keywords are joined with spaces for cheap substring matching.
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

    // Empty state content: pinned + recent emojis, then a popular-emoji
    // fallback (first slice of smileys) so a fresh install still has
    // something to surface instead of the strip being blank.
    val recentAndPopular = remember(query, fullEmojiMappings) {
        if (trimmed.isNotEmpty()) emptyList()
        else {
            val data = prefs.emoji.historyData.get()
            val recent = (data.pinned + data.recent).map { EmojiSet(listOf(it)) }
            if (recent.isNotEmpty()) {
                recent.take(40)
            } else {
                // Fresh install: surface the first wave of smileys so users
                // see "something useful" instead of dead space.
                val smileys = fullEmojiMappings.byCategory[EmojiCategory.SMILEYS_EMOTION].orEmpty()
                smileys.take(40)
            }
        }
    }

    val displayed: List<EmojiSet> = if (results.isNotEmpty()) results else recentAndPopular

    // ── Snygg-driven palette ──────────────────────────────────────────────
    // Pull every color from the keyboard's snygg theme so the overlay
    // matches whatever skin the user has (light / dark / AMOLED / custom).
    // Accent (the cursor color) comes from the SHIFT key's focused style —
    // that's the same accent the keyboard uses for "active" indicators
    // (caps lock, selected suggestion, etc.), so it's the closest thing
    // to a brand color the theme exposes.
    val containerStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val pillStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionTile.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val containerBg = containerStyle.background(default = MaterialTheme.colorScheme.surface)
    val pillBg = pillStyle.background(default = MaterialTheme.colorScheme.surfaceContainerHigh)
    val pillFg = pillStyle.foreground(default = MaterialTheme.colorScheme.onSurface)
    val accent = keyStyle.foreground(default = pillFg)

    SnyggBox(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ─── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedRoundButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to emoji panel",
                    bg = pillBg,
                    fg = pillFg,
                    onClick = {
                        // Defer imeUiMode flip until the overlay close
                        // animation has fully run, so MediaInputLayout
                        // doesn't mount mid-shrink (would cause a layout
                        // swap during the close transition).
                        // Tuned to match FlorisImeService.ImeUi exit timing:
                        // fadeOut(90ms) + shrink(160ms, delay 30ms) = 190ms.
                        keyboardManager.endEmojiSearch()
                        scope.launch {
                            kotlinx.coroutines.delay(190)
                            keyboardManager.activeState.imeUiMode = ImeUiMode.MEDIA
                        }
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

            // ─── Results grid ────────────────────────────────────────
            // No background container — emojis sit directly on the overlay
            // surface like the Gboard reference. Background tints around
            // result strips look cluttered next to the smartbar's keys.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                when {
                    displayed.isNotEmpty() -> {
                        LazyHorizontalGrid(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                            rows = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(items = displayed, key = { it.base().value }) { emojiSet ->
                                Box(
                                    modifier = Modifier.size(width = 44.dp, height = 44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmojiKey(
                                        emojiSet = emojiSet,
                                        emojiCompatInstance = null,
                                        preferredSkinTone = EmojiSkinTone.DEFAULT,
                                        onEmojiInput = { emoji ->
                                            // Commit directly to the host
                                            // editor (bypassing the keyboard
                                            // event dispatcher whose synchronous
                                            // runBlocking would otherwise route
                                            // the emoji char through our own
                                            // emoji-search intercept and append
                                            // it to the query).
                                            //
                                            // We DON'T end the search here —
                                            // multi-pick is the expected UX.
                                            // User can keep tapping emojis;
                                            // overlay stays until they hit
                                            // the back button.
                                            editorInstance.commitText(emoji.value)
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
                        // Query typed but nothing matched.
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(
                                "No emojis match \"$trimmed\"",
                                color = pillFg.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    else -> {
                        // Fresh install + no recent/pinned + popular fallback
                        // also empty (catalog still loading).
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(
                                "Type to search",
                                color = pillFg.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // ─── Search pill ─────────────────────────────────────────
            EmojiSearchPill(
                query = query,
                pillBg = pillBg,
                pillFg = pillFg,
                accent = accent,
                onClear = { keyboardManager.backspaceEmojiSearch() },
            )
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
    // FilledIconButton is the M3 component for the "tonal circular icon
    // button" pattern — gets ripple, proper touch target (48dp), and
    // accessibility role baked in. Sizing forced to 36dp via Modifier.size
    // to match the Gboard back-chip footprint while keeping the 48dp
    // touch slop M3 provides under the hood.
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

@Composable
private fun EmojiSearchPill(
    query: String,
    pillBg: Color,
    pillFg: Color,
    accent: Color,
    onClear: () -> Unit,
) {
    // Real cursor: a 2dp-wide bar with blinking opacity. Replaces the
    // unicode "│" character previously embedded in the query text, which
    // some users perceived as a trailing space ("lol " in the screenshot).
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
            .height(40.dp)
            .background(color = pillBg, shape = RoundedCornerShape(50))
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
                Text(
                    text = "Search",
                    color = pillFg.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.width(2.dp))
                // Caret blinks at start when empty too — matches Gboard.
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
            // M3 IconButton gives a 48dp touch target with built-in ripple.
            // Visually constrained to ~32dp via the wrapping Box so it sits
            // cleanly inside the pill while still being easy to hit.
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
