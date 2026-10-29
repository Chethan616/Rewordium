/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.noxquill.rewordium.keyboard.ime.media.emoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.media.MediaMemory
import com.noxquill.rewordium.keyboard.ime.media.SearchSuggestions
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaLoading
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSearchField
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.SpinningShape
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.snygg.ui.SnyggBox

/**
 * Emoji search over the emoji data's names and keywords, ranked the way people expect: the emoji whose
 * name is the query, then whole-word matches ("cat" finds 🐱 before 🎓 "graduation cap"), then words that
 * start with it, then anything containing it. Every word of a multi-word query has to match.
 */
internal class EmojiSearchIndex(val mappings: EmojiData) {
    private class Entry(val set: EmojiSet, val name: String, val nameWords: List<String>, val keywords: List<String>)

    private val entries: List<Entry> = buildList {
        for ((_, list) in mappings.byCategory) {
            for (set in list) {
                val base = set.base()
                val name = base.name.lowercase()
                add(Entry(set, name, name.split(' ', '-', ':').filter { it.isNotBlank() }, base.keywords.map { it.lowercase() }))
            }
        }
    }.distinctBy { it.set.base().value }

    fun search(query: String, limit: Int = 120): List<EmojiSet> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ').filter { it.isNotBlank() }
        val strong = ArrayList<Pair<Int, Entry>>()
        val loose = ArrayList<Pair<Int, Entry>>()
        entries@ for (entry in entries) {
            var total = if (entry.name == q) -10 else if (entry.name.startsWith(q)) -3 else 0
            var isLoose = false
            for (token in tokens) {
                val score = when {
                    entry.nameWords.any { it == token } || entry.keywords.any { it == token } -> 1
                    entry.nameWords.any { it.startsWith(token) } -> 2
                    entry.keywords.any { it.startsWith(token) } -> 3
                    // Inside a word ("cat" in "education") is mostly noise; only longer words, as a last resort.
                    token.length >= 4 && (entry.name.contains(token) || entry.keywords.any { it.contains(token) }) -> {
                        isLoose = true
                        5
                    }
                    else -> continue@entries
                }
                total += score
            }
            (if (isLoose) loose else strong).add(total to entry)
        }
        // A stable sort, so ties keep the emoji data's own order (which is roughly by popularity).
        val ranked = strong.sortedBy { it.first } + if (strong.size < 12) loose.sortedBy { it.first } else emptyList()
        return ranked.take(limit).map { it.second.set }
    }
}

/**
 * The search index for the keyboard's language (English as a fallback), built off the main thread. The
 * panel's own emoji set has no names or keywords, so search always uses the localized data.
 */
@Composable
internal fun rememberEmojiSearchIndex(): EmojiSearchIndex? {
    val context = LocalContext.current
    val subtypeManager by context.subtypeManager()
    val locale = subtypeManager.activeSubtype.primaryLocale
    val index by produceState<EmojiSearchIndex?>(initialValue = null, locale) {
        val localized = EmojiData.get(context, locale)
        val data = if (localized.byCategory.values.any { it.isNotEmpty() }) localized else EmojiData.get(context, "ime/media/emoji/en.txt")
        value = withContext(Dispatchers.Default) { EmojiSearchIndex(data) }
    }
    return index
}

internal val EmojiSuggestions = listOf("heart", "smile", "laugh", "party", "fire", "cry", "thumbs up", "cat", "star", "pizza")
internal val EmoticonSuggestions = listOf("shrug", "lenny", "table flip", "cool", "happy", "love", "hug", "cry", "bear", "dance")

/**
 * Emoji search, Gboard style: it sits above the full keyboard, which types the query. Two rows of matches
 * (recents while the query is empty) live-update as you type, recent and suggested searches sit under
 * them, and the search bar carries the back arrow. Tapping an emoji types it and keeps the search, so you
 * can pick several; the Search key opens every result in the panel.
 */
@Composable
fun EmojiSearchOverlay() = MediaExpressiveTheme {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val editorInstance by context.editorInstance()
    val nlpManager by context.nlpManager()
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    val tones = rememberTones()
    val skinTone by prefs.emoji.preferredSkinTone.observeAsState()

    val queryNullable by keyboardManager.emojiSearchQuery.collectAsState()
    val query = queryNullable.orEmpty()

    val index = rememberEmojiSearchIndex()
    val recent = remember(index) {
        val history = prefs.emoji.historyData.get()
        (history.pinned + history.recent).distinctBy { it.value }.map { EmojiSet(listOf(it)) }
            .ifEmpty { index?.mappings?.byCategory?.get(EmojiCategory.SMILEYS_EMOTION).orEmpty() }
            .take(48)
    }
    val shown = remember(query, index, recent) { if (query.isBlank()) recent else index?.search(query, 96).orEmpty() }

    // Search opened from the emoticon panel looks for emoticons (by name, mood and nickname) instead.
    val emoticonMode = remember { keyboardManager.activeState.activeMediaMode == EmojiPanelMode.EMOTICON.name }
    val emoticons = remember(query, emoticonMode) {
        when {
            !emoticonMode -> emptyList()
            query.isNotBlank() -> searchEmoticons(query)
            else -> MediaMemory.recentEmoticons(context).ifEmpty { EmoticonGroups.first().items }
        }
    }

    fun recordQuery() = MediaMemory.recordSearch(context, if (emoticonMode) "emoticon" else "emoji", query, keyboardManager.activeState.isIncognitoMode)

    fun commitEmoticon(emoticon: String) {
        feedback.tap()
        editorInstance.commitText(emoticon)
        MediaMemory.recordEmoticon(context, emoticon, keyboardManager.activeState.isIncognitoMode)
        recordQuery()
    }

    SnyggBox(elementName = FlorisImeUi.Smartbar.elementName, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(100.dp)) {
                    if (emoticonMode) {
                        EmoticonResults(emoticons, query) { commitEmoticon(it) }
                    } else if (shown.isEmpty() && index == null) {
                        MediaLoading()
                    } else if (shown.isEmpty()) {
                        NoResults("No emoji for “${query.trim()}”", query)
                    } else {
                        val gridState = rememberLazyGridState()
                        // New letters, new results: start from the best match again.
                        LaunchedEffect(query) { gridState.scrollToItem(0) }
                        val pressShape = MaterialShapes.Cookie9Sided.toShape()
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            itemsIndexed(shown, key = { _, it -> it.base().value }) { i, set ->
                                val emoji = set.base(withSkinTone = skinTone)
                                EmojiCell(
                                    emoji = emoji,
                                    hasVariants = false,
                                    pressShape = pressShape,
                                    pressColor = tones.at(i).container,
                                    onClick = {
                                        feedback.tap()
                                        nlpManager.notifyEmojiPickedFromPalette(subtypeManager.activeSubtype, emoji.value)
                                        editorInstance.commitText(emoji.value)
                                        recordQuery()
                                        scope.launch { EmojiHistoryHelper.markEmojiUsed(prefs, emoji) }
                                    },
                                    onLongClick = null,
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                        }
                    }
                }
                SearchSuggestions(
                    kind = if (emoticonMode) "emoticon" else "emoji",
                    query = query,
                    suggestions = if (emoticonMode) EmoticonSuggestions else EmojiSuggestions,
                    onPick = {
                        feedback.tick()
                        keyboardManager.setMediaSearchQuery(it)
                    },
                )
                MediaSearchField(
                    query = query,
                    hints = listOf(if (emoticonMode) "Search emoticons" else "Search emoji"),
                    shape = MediaShapes.blob(query.length),
                    active = true,
                    onBack = {
                        feedback.tick()
                        keyboardManager.endEmojiSearch()
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

@Composable
private fun NoResults(text: String, query: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        SpinningShape(MediaShapes.blob(query.length), MaterialTheme.colorScheme.primaryContainer, Modifier.size(48.dp)) {
            Icon(Symbols.SearchOff, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Matching emoticons in two rows of tonal tiles. */
@Composable
private fun EmoticonResults(emoticons: List<String>, query: String, onPick: (String) -> Unit) {
    val tones = rememberTones()
    if (emoticons.isEmpty()) {
        NoResults("No emoticons for “${query.trim()}”", query)
        return
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(query) { gridState.scrollToItem(0) }
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(emoticons, key = { _, it -> it }) { index, emoticon ->
            MediaTile(
                onClick = { onPick(emoticon) },
                modifier = Modifier.height(46.dp).widthIn(min = 64.dp),
                color = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tones.at(index).container, 0.6f),
                corner = 16.dp,
                pressedCorner = 24.dp,
                label = emoticon,
            ) {
                Text(
                    emoticon,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}
