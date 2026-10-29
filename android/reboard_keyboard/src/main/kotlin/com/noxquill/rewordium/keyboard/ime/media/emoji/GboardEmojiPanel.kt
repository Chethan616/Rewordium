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

package com.noxquill.rewordium.keyboard.ime.media.emoji

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.keyboard.KeyboardManager
import com.noxquill.rewordium.keyboard.ime.media.MediaMemory
import com.noxquill.rewordium.keyboard.ime.media.gif.GifPanel
import com.noxquill.rewordium.keyboard.ime.media.sticker.StickerPanel
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSearchField
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaEmptyState
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaLoading
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSectionHeader
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.RepeatingDeleteButton
import com.noxquill.rewordium.keyboard.ime.media.ui.SlidingMorphIndicator
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.Tone
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch

/**
 * The keyboard's media panel: emoji, GIFs, stickers and emoticons, built from Material 3 Expressive parts
 * (see [MediaExpressiveTheme]) and coloured by the active keyboard theme.
 *
 *  ┌───────────────────────────────────────────────┐
 *  │ ( ✿🔍  Search emoji / Try “party” …       )   │  search bar; its shape morphs with the panel
 *  │  🕒  😀  👤  🐾  🍕  ✈  ⚽  💡  🔣  🏳          │  one shape slides and morphs between categories
 *  │  (● Recent)                                    │  tonal section pills
 *  │  😀 😂 😅 🙏 …                                 │
 *  │ ┌─────┐ ┌──────┬──────┬──────┬──────┐ ┌────┐  │
 *  │ │ ABC │ │  😊  │ GIF  │  🏷  │ :-)  │ │ ⌫  │  │  ABC · connected mode buttons · held delete repeats
 *  └───────────────────────────────────────────────┘
 *
 * Emoji with skin tones show a small dot; a long press opens the tones. Emoji Kitchen is left out on
 * purpose: its combination art is Google's and isn't standard Unicode.
 */
@Composable
fun GboardEmojiPanel(
    fullEmojiMappings: EmojiData,
    modifier: Modifier = Modifier,
) = MediaExpressiveTheme {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val subtypeManager by context.subtypeManager()
    val nlpManager by context.nlpManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val feedback = rememberMediaFeedback()
    // History is read once per opening: picking re-renders the panel on its next open anyway, and a live
    // subscription would sit on the typing hot path.
    val historyData = remember { prefs.emoji.historyData.get() }
    val skinTone by prefs.emoji.preferredSkinTone.observeAsState()

    val categories = remember { EmojiCategory.entries.toList() }
    val sections = remember(fullEmojiMappings, historyData) {
        buildList {
            var cursor = 0
            for (category in categories) {
                val emojis = if (category == EmojiCategory.RECENTLY_USED) {
                    (historyData.pinned + historyData.recent).distinctBy { it.value }.map { EmojiSet(listOf(it)) }
                } else {
                    fullEmojiMappings.byCategory[category].orEmpty()
                }
                if (emojis.isEmpty()) continue
                add(EmojiSection(category, emojis, cursor))
                cursor += 1 + emojis.size
            }
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(sections) {
        sections.firstOrNull()?.let { gridState.scrollToItem(it.headerIndex) }
    }
    val activeCategory by remember(sections) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            sections.lastOrNull { it.headerIndex <= first }?.category ?: sections.firstOrNull()?.category
        }
    }

    var panelMode by remember {
        mutableStateOf(
            runCatching { EmojiPanelMode.valueOf(keyboardManager.activeState.activeMediaMode) }
                .getOrDefault(EmojiPanelMode.EMOJI),
        )
    }
    var variantsFor by remember { mutableStateOf<EmojiSet?>(null) }
    // A search run with the Search key: this panel shows its results and the bar keeps the query.
    val panelSearch by keyboardManager.panelSearch.collectAsState()
    val searchedQuery = panelSearch?.takeIf { it.mode == panelMode.searchMode }?.query

    fun commitEmoji(emoji: Emoji) {
        feedback.tap()
        // The context must be read before the emoji lands, or the "previous word" would include it.
        nlpManager.notifyEmojiPickedFromPalette(subtypeManager.activeSubtype, emoji.value)
        editorInstance.commitText(emoji.value)
        scope.launch { EmojiHistoryHelper.markEmojiUsed(prefs, emoji) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MediaSearchField(
                query = searchedQuery.orEmpty(),
                hints = panelMode.searchHints,
                shape = panelMode.shape,
                onClick = {
                    feedback.tick()
                    keyboardManager.beginMediaSearch(panelMode.searchMode)
                },
                onClear = {
                    feedback.delete()
                    keyboardManager.clearPanelSearch()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            )

            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = panelMode,
                transitionSpec = {
                    // Panels slide in from the side their button sits on.
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } +
                        fadeIn(motion.defaultEffectsSpec()) +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                        (slideOutHorizontally(motion.fastSpatialSpec()) { if (forward) -it / 6 else it / 6 } +
                            fadeOut(motion.fastEffectsSpec()))
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "media-mode",
            ) { mode ->
                when (mode) {
                    EmojiPanelMode.EMOJI -> if (searchedQuery != null) {
                        EmojiSearchResults(
                            query = searchedQuery,
                            skinTone = skinTone,
                            onPick = ::commitEmoji,
                            onShowVariants = { set ->
                                feedback.reveal()
                                variantsFor = set
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else Column(Modifier.fillMaxSize()) {
                        CategoryTabs(
                            categories = categories,
                            active = activeCategory,
                            onPick = { category ->
                                feedback.tick()
                                val target = sections.firstOrNull { it.category == category } ?: sections.firstOrNull()
                                if (target != null) scope.launch { gridState.animateScrollToItem(target.headerIndex) }
                            },
                        )
                        EmojiGrid(
                            sections = sections,
                            gridState = gridState,
                            skinTone = skinTone,
                            onPick = ::commitEmoji,
                            onShowVariants = { set ->
                                feedback.reveal()
                                variantsFor = set
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    EmojiPanelMode.EMOTICON -> {
                        val onPick = { emoticon: String ->
                            feedback.tap()
                            editorInstance.commitText(emoticon)
                            MediaMemory.recordEmoticon(context, emoticon, keyboardManager.activeState.isIncognitoMode)
                        }
                        if (searchedQuery != null) {
                            EmoticonSearchResults(searchedQuery, onPick, Modifier.fillMaxSize())
                        } else {
                            EmoticonPanel(feedback = feedback, onPick = onPick)
                        }
                    }
                    EmojiPanelMode.GIF -> GifPanel(
                        onGifPicked = { uri, description ->
                            editorInstance.commitMedia(uri = uri, mimeType = "image/gif", description = description.ifBlank { "GIF" })
                        },
                    )
                    EmojiPanelMode.STICKER -> StickerPanel(
                        onStickerPicked = { uri, mime, description ->
                            editorInstance.commitMedia(uri = uri, mimeType = mime, description = description.ifBlank { "Sticker" })
                        },
                    )
                }
            }

            MediaBottomBar(
                mode = panelMode,
                onModeChange = {
                    feedback.tick()
                    panelMode = it
                    variantsFor = null
                    // Like Gboard, a search belongs to the tab it was run in.
                    keyboardManager.clearPanelSearch()
                    keyboardManager.activeState.activeMediaMode = it.name
                    keyboardManager.rememberMediaPanelTab(it.name)
                },
                onAbc = {
                    feedback.tap()
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
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
        }

        VariantTray(
            set = variantsFor,
            skinTone = skinTone,
            onPick = { emoji ->
                variantsFor = null
                commitEmoji(emoji)
            },
            onDismiss = { variantsFor = null },
        )
    }
}

// ── Categories ───────────────────────────────────────────────────────────────

/** Each category's symbol, filled while it's the one in view. */
internal fun EmojiCategory.symbol(selected: Boolean): ImageVector = when (this) {
    EmojiCategory.RECENTLY_USED -> if (selected) Symbols.HistoryFilled else Symbols.History
    EmojiCategory.SMILEYS_EMOTION -> if (selected) Symbols.SentimentSatisfiedFilled else Symbols.SentimentSatisfied
    EmojiCategory.PEOPLE_BODY -> if (selected) Symbols.EmojiPeopleFilled else Symbols.EmojiPeople
    EmojiCategory.ANIMALS_NATURE -> if (selected) Symbols.EmojiNatureFilled else Symbols.EmojiNature
    EmojiCategory.FOOD_DRINK -> if (selected) Symbols.EmojiFoodBeverageFilled else Symbols.EmojiFoodBeverage
    EmojiCategory.TRAVEL_PLACES -> if (selected) Symbols.EmojiTransportationFilled else Symbols.EmojiTransportation
    EmojiCategory.ACTIVITIES -> if (selected) Symbols.EmojiEventsFilled else Symbols.EmojiEvents
    EmojiCategory.OBJECTS -> if (selected) Symbols.EmojiObjectsFilled else Symbols.EmojiObjects
    EmojiCategory.SYMBOLS -> if (selected) Symbols.EmojiSymbolsFilled else Symbols.EmojiSymbols
    EmojiCategory.FLAGS -> if (selected) Symbols.EmojiFlagsFilled else Symbols.EmojiFlags
}

/**
 * One symbol per category over a single indicator that slides to the category in view on a bouncy spring,
 * morphing from one category's shape (and colour) into the next as it travels.
 */
@Composable
private fun CategoryTabs(
    categories: List<EmojiCategory>,
    active: EmojiCategory?,
    onPick: (EmojiCategory) -> Unit,
) {
    val tones = rememberTones()
    val activeIndex = categories.indexOf(active).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
        label = "category-indicator",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 6.dp),
    ) {
        SlidingMorphIndicator(
            position = position,
            count = categories.size,
            indicatorSize = 40.dp,
            shapeAt = { MediaShapes.blob(it) },
            colorAt = { tones.at(it).container },
            modifier = Modifier.matchParentSize(),
        )
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            categories.forEachIndexed { index, category ->
                val selected = index == activeIndex
                val lift by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "category-lift",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(category) { detectTapGestures { onPick(category) } }
                        .semantics {
                            role = Role.Tab
                            contentDescription = category.sectionTitle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = category.symbol(selected),
                        contentDescription = null,
                        tint = if (selected) tones.at(index).content else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                val s = 1f + 0.12f * lift
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }
        }
    }
}

private data class EmojiSection(
    val category: EmojiCategory,
    val emojis: List<EmojiSet>,
    /** Index of this section's header in the flat grid. */
    val headerIndex: Int,
)

internal fun EmojiCategory.sectionTitle(): String = when (this) {
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

// ── Emoji grid ───────────────────────────────────────────────────────────────

@Composable
private fun EmojiGrid(
    sections: List<EmojiSection>,
    gridState: LazyGridState,
    skinTone: EmojiSkinTone,
    onPick: (Emoji) -> Unit,
    onShowVariants: (EmojiSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = rememberTones()
    // One press shape per category (built once here, not per cell); each section presses on its own shape.
    val pressShapes = EmojiCategory.entries.map { MediaShapes.blob(it.ordinal).toShape() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 42.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
    ) {
        sections.forEach { section ->
            val index = section.category.ordinal
            val tone = tones.at(index)
            item(key = "header_${section.category.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                MediaSectionHeader(
                    section.category.sectionTitle(),
                    icon = section.category.symbol(selected = true),
                    tone = tone,
                    shape = MediaShapes.blob(index),
                )
            }
            items(
                items = section.emojis,
                key = { "${section.category.id}_${it.base().value}" },
                contentType = { "emoji" },
            ) { set ->
                val hasVariants = set.emojis.size > 1
                EmojiCell(
                    emoji = set.base(withSkinTone = skinTone),
                    hasVariants = hasVariants,
                    pressShape = pressShapes[index],
                    pressColor = tone.container,
                    onClick = { onPick(set.base(withSkinTone = skinTone)) },
                    onLongClick = if (hasVariants) ({ onShowVariants(set) }) else null,
                )
            }
        }
    }
}

/** Every emoji for a search run with the Search key, best matches first; long-press for skin tones. */
@Composable
private fun EmojiSearchResults(
    query: String,
    skinTone: EmojiSkinTone,
    onPick: (Emoji) -> Unit,
    onShowVariants: (EmojiSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = rememberTones()
    val index = rememberEmojiSearchIndex()
    if (index == null) {
        MediaLoading(modifier)
        return
    }
    val results = remember(index, query) { index.search(query, limit = 240) }
    if (results.isEmpty()) {
        MediaEmptyState(
            icon = Symbols.SearchOff,
            title = "No emoji for “$query”",
            message = "Tap the search bar to try another word.",
            shape = MediaShapes.blob(query.length),
            modifier = modifier,
        )
        return
    }
    val pressShape = MaterialShapes.Cookie9Sided.toShape()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 42.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            MediaSectionHeader(
                text = if (results.size == 1) "1 emoji for “$query”" else "${results.size} emoji for “$query”",
                icon = Symbols.Search,
                tone = tones.at(0),
                shape = MediaShapes.blob(query.length),
            )
        }
        itemsIndexed(results, key = { _, set -> set.base().value }, contentType = { _, _ -> "emoji" }) { i, set ->
            val hasVariants = set.emojis.size > 1
            EmojiCell(
                emoji = set.base(withSkinTone = skinTone),
                hasVariants = hasVariants,
                pressShape = pressShape,
                pressColor = tones.at(i).container,
                onClick = { onPick(set.base(withSkinTone = skinTone)) },
                onLongClick = if (hasVariants) ({ onShowVariants(set) }) else null,
            )
        }
    }
}

/** Every emoticon for a search run with the Search key. */
@Composable
private fun EmoticonSearchResults(query: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val tones = rememberTones()
    val results = remember(query) { searchEmoticons(query) }
    if (results.isEmpty()) {
        MediaEmptyState(
            icon = Symbols.SearchOff,
            title = "No emoticons for “$query”",
            message = "Try a mood like “happy”, “shrug” or “love”.",
            shape = MediaShapes.blob(query.length),
            modifier = modifier,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 92.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            MediaSectionHeader(
                text = "Emoticons for “$query”",
                icon = Symbols.Search,
                tone = tones.at(1),
                shape = MediaShapes.blob(query.length),
            )
        }
        itemsIndexed(
            items = results,
            key = { _, it -> it },
            span = { _, it -> GridItemSpan(if (it.length > 9) minOf(2, maxLineSpan) else 1) },
        ) { i, emoticon ->
            EmoticonTile(emoticon, tones.at(i)) { onPick(emoticon) }
        }
    }
}

/** An emoji that grows and wobbles on a MaterialShape while pressed. */
@Composable
internal fun EmojiCell(
    emoji: Emoji,
    hasVariants: Boolean,
    pressShape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pressColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    fontSize: Int = 26,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "emoji-press",
    )
    Box(
        modifier
            .aspectRatio(1f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = emoji.name.ifBlank { emoji.value } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize(0.96f)
                .graphicsLayer {
                    val p = press.value.coerceAtLeast(0f)
                    scaleX = 0.4f + 0.6f * p
                    scaleY = 0.4f + 0.6f * p
                    alpha = p.coerceIn(0f, 1f)
                    rotationZ = (1f - p) * -50f
                }
                .background(pressColor, pressShape),
        )
        Text(
            text = emoji.value,
            fontSize = fontSize.sp,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val p = press.value
                val s = 1f + 0.2f * p
                scaleX = s
                scaleY = s
                rotationZ = -8f * p
            },
        )
        if (hasVariants) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** A long-pressed emoji's skin tones, in a card that rises from the bottom of the panel. */
@Composable
private fun BoxScope.VariantTray(
    set: EmojiSet?,
    skinTone: EmojiSkinTone,
    onPick: (Emoji) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keep the last set while the tray animates out.
    var shown by remember { mutableStateOf(set) }
    if (set != null) shown = set
    AnimatedVisibility(
        visible = set != null,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val emojis = shown?.emojis.orEmpty()
            val pressShape = MaterialShapes.Sunny.toShape()
            val chosenShape = MaterialShapes.Cookie9Sided.toShape()
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 66.dp)
                    .animateEnterExit(
                        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } +
                            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.85f),
                        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                    )
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        "Skin tone",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(emojis, key = { _, e -> e.value }) { _, emoji ->
                            Box(Modifier.size(52.dp)) {
                                if (emoji.skinTone == skinTone) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .padding(2.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, chosenShape),
                                    )
                                }
                                EmojiCell(
                                    emoji = emoji,
                                    hasVariants = false,
                                    pressShape = pressShape,
                                    onClick = { onPick(emoji) },
                                    onLongClick = null,
                                    fontSize = 28,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Emoticons ────────────────────────────────────────────────────────────────

internal data class EmoticonGroup(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val items: List<String>)

internal val EmoticonGroups: List<EmoticonGroup> by lazy {
    listOf(
        EmoticonGroup(
            "Happy", Symbols.SentimentVerySatisfied, Symbols.SentimentVerySatisfiedFilled,
            listOf(
                ":-)", ":-D", "^_^", "(^o^)", "(^_-)", "d(-_^)", ";-)", ":-P", "B-)", "XD", "(*^▽^*)", "(≧◡≦)",
                "(｡◕‿◕｡)", "ヽ(•‿•)ノ", "(✿◠‿◠)", "＼(^o^)／", "(•‿•)", "(￣▽￣)", "(☞ﾟヮﾟ)☞", "O:-)",
                "٩(◕‿◕)۶", "ヾ(＾∇＾)", "(⌒‿⌒)", "(ﾉ´ヮ`)ﾉ*: ･ﾟ",
            ),
        ),
        EmoticonGroup(
            "Love", Symbols.Favorite, Symbols.FavoriteFilled,
            listOf(
                "<3", "♥‿♥", ":-*", "(づ｡◕‿‿◕｡)づ", "(♡˙︶˙♡)", "(˘³˘)♥", "(♥ω♥*)", "♡(｡- ω -)",
                "(っ˘з(˘⌣˘ )", "(´｡• ᵕ •｡`) ♡", "(*˘︶˘*).｡.:*♡", "( ˘ ³˘)♥", "(｡♥‿♥｡)", "♡＾▽＾♡",
            ),
        ),
        EmoticonGroup(
            "Hugs", Symbols.VolunteerActivism, Symbols.VolunteerActivism,
            listOf(
                "(っ◔◡◔)っ", "⊂(・▽・⊂)", "(づ￣ ³￣)づ", "(つ≧▽≦)つ", "⊂(◉‿◉)つ", "(⊃｡•́‿•̀｡)⊃", "(っ´▽`)っ",
                "༼ つ ◕_◕ ༽つ", "ʕっ•ᴥ•ʔっ", "(づ ◕‿◕ )づ",
            ),
        ),
        EmoticonGroup(
            "Sad", Symbols.SentimentDissatisfied, Symbols.SentimentDissatisfiedFilled,
            listOf(
                ":-(", "T_T", "(;_;)", "(ToT)", "(╥﹏╥)", "(._.)", "(´；ω；`)", "( ˘︹˘ )", "(っ˘̩╭╮˘̩)っ",
                "(-_-)", "(=_=)", "(｡•́︿•̀｡)", "(ಥ﹏ಥ)", "(╯︵╰,)",
            ),
        ),
        EmoticonGroup(
            "Angry", Symbols.MoodBad, Symbols.MoodBadFilled,
            listOf(
                ">:(", "(｀_´)", "ಠ_ಠ", "(╬ಠ益ಠ)", "(ง'̀-'́)ง", "(ノಠ益ಠ)ノ", "ᕙ(⇀‸↼‶)ᕗ", "(>_<)", "(¬_¬)",
                "(눈_눈)", "(ง •̀_•́)ง", "-_-", "(╬ Ò﹏Ó)", "凸(￣ヘ￣)",
            ),
        ),
        EmoticonGroup(
            "Wow", Symbols.SentimentExcited, Symbols.SentimentExcitedFilled,
            listOf(
                ":-O", "0_0", "(O_O)", "(°ロ°)", "(⊙_⊙)", "Σ(°△°|||)", "(*_*)", "w(°ｏ°)w", "(゜゜)", "(◎_◎;)",
                "(>_>)", "(<_<)", "(ﾟДﾟ;)", "(⊙ω⊙)",
            ),
        ),
        EmoticonGroup(
            "Sleepy", Symbols.Bedtime, Symbols.Bedtime,
            listOf("(-_-)zzz", "(｡-ω-)zzz", "(∪｡∪)｡｡｡zzz", "(-.-)Zzz", "(︶｡︶✽)", "( ˘ω˘ )ｽﾔｧ", "(￣o￣) zzZZ", "(´〜｀*) zzz"),
        ),
        EmoticonGroup(
            "Animals", Symbols.Pets, Symbols.PetsFilled,
            listOf(
                "ʕ•ᴥ•ʔ", "(=^･ω･^=)", "U・ᴥ・U", "(・⊝・)", "<コ:彡", ">°))))彡", "(°)#))<<", "ᘛ⁐̤ᕐᐷ",
                "(=①ω①=)", "ʕ ·ᴥ·ʔ", "ฅ^•ﻌ•^ฅ", "(ᵔᴥᵔ)", "ʕ ꈍᴥꈍʔ", "(•ㅅ•)",
            ),
        ),
        EmoticonGroup(
            "Classics", Symbols.LocalFireDepartment, Symbols.LocalFireDepartmentFilled,
            listOf(
                "¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "(╯°□°）╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "\\m/", "(´･ω･`)",
                "(•_•) ( •_•)>⌐■-■ (⌐■_■)", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "(☞ﾟ∀ﾟ)☞", "¯\\(°_o)/¯", "♪~ ᕕ(ᐛ)ᕗ", "(~˘▾˘)~",
            ),
        ),
    )
}

/** Other words for each group, so searching "smile" or "cry" finds the right faces. */
private val EmoticonGroupWords = mapOf(
    "Happy" to listOf("happy", "smile", "joy", "glad", "grin", "yay"),
    "Love" to listOf("love", "heart", "kiss", "crush", "cute"),
    "Hugs" to listOf("hug", "cuddle", "comfort"),
    "Sad" to listOf("sad", "cry", "tears", "upset", "sorry"),
    "Angry" to listOf("angry", "mad", "rage", "annoyed", "fight"),
    "Wow" to listOf("wow", "surprise", "shock", "omg", "what"),
    "Sleepy" to listOf("sleep", "tired", "zzz", "night", "bored"),
    "Animals" to listOf("animal", "cat", "dog", "bear", "fish", "bird", "bunny"),
    "Classics" to listOf("classic", "meme", "funny"),
)

/** Particular faces people search for by name. */
private val EmoticonKeywords = mapOf(
    "¯\\_(ツ)_/¯" to listOf("shrug", "whatever", "idk", "dunno"),
    "( ͡° ͜ʖ ͡°)" to listOf("lenny", "sus", "smirk"),
    "(╯°□°）╯︵ ┻━┻" to listOf("table flip", "flip", "rage quit"),
    "┬─┬ノ( º _ ºノ)" to listOf("table", "calm down", "put back"),
    "ಠ_ಠ" to listOf("disapproval", "stare", "look"),
    "(•_•) ( •_•)>⌐■-■ (⌐■_■)" to listOf("cool", "sunglasses", "deal with it"),
    "♪~ ᕕ(ᐛ)ᕗ" to listOf("dance", "music", "party"),
    "(~˘▾˘)~" to listOf("dance", "vibe"),
    "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧" to listOf("magic", "sparkle", "excited"),
    "\\m/" to listOf("rock", "metal"),
    "ʕ•ᴥ•ʔ" to listOf("bear"),
    "(=^･ω･^=)" to listOf("cat", "kitty"),
    "ฅ^•ﻌ•^ฅ" to listOf("cat", "paws"),
    "U・ᴥ・U" to listOf("dog", "puppy"),
    ">°))))彡" to listOf("fish"),
    "<3" to listOf("heart"),
    "(ง •̀_•́)ง" to listOf("fight", "strong", "lets go"),
    "(☞ﾟ∀ﾟ)☞" to listOf("you", "point", "hey"),
)

/** Emoticons for a search: faces known by that name first, then the groups the word belongs to. */
internal fun searchEmoticons(query: String): List<String> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return buildList {
        EmoticonKeywords.forEach { (face, words) -> if (words.any { it.startsWith(q) || q.startsWith(it) }) add(face) }
        EmoticonGroups.forEach { group ->
            val words = EmoticonGroupWords[group.label].orEmpty() + group.label.lowercase()
            if (words.any { it.startsWith(q) || q.startsWith(it) }) addAll(group.items)
        }
    }.distinct()
}

/** Recent emoticons first, then the groups; each group's tiles wear their own tone. */
@Composable
private fun EmoticonPanel(feedback: MediaFeedback, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val tones = rememberTones()
    // Read once per opening, so sending one doesn't reshuffle the grid under the finger.
    val recents = remember { MediaMemory.recentEmoticons(context) }
    val groups = remember(recents) {
        buildList {
            if (recents.isNotEmpty()) add(EmoticonGroup("Recent", Symbols.History, Symbols.HistoryFilled, recents))
            addAll(EmoticonGroups)
        }
    }
    var groupIndex by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(groups, key = { _, g -> g.label }) { index, g ->
                MediaChip(
                    selected = index == groupIndex,
                    onClick = {
                        feedback.tick()
                        groupIndex = index
                    },
                    label = g.label,
                    icon = g.icon,
                    selectedIcon = g.selectedIcon,
                    tone = tones.at(index),
                )
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = groupIndex,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 5 else -it / 5 } +
                    fadeIn(motion.defaultEffectsSpec())) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "emoticon-group",
        ) { index ->
            val group = groups.getOrElse(index) { groups.first() }
            val tone = tones.at(index)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = group.items,
                    key = { it },
                    span = { GridItemSpan(if (it.length > 9) minOf(2, maxLineSpan) else 1) },
                ) { emoticon ->
                    EmoticonTile(emoticon, tone) { onPick(emoticon) }
                }
            }
        }
    }
}

@Composable
private fun EmoticonTile(emoticon: String, tone: Tone, onClick: () -> Unit) {
    MediaTile(
        onClick = onClick,
        modifier = Modifier.height(54.dp),
        color = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tone.container, 0.55f),
        corner = 18.dp,
        pressedCorner = 28.dp,
        label = emoticon,
    ) {
        Text(
            emoticon,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

// ── Bottom bar ───────────────────────────────────────────────────────────────

internal val EmojiPanelMode.searchMode: KeyboardManager.MediaSearchMode
    get() = when (this) {
        EmojiPanelMode.GIF -> KeyboardManager.MediaSearchMode.GIF
        EmojiPanelMode.STICKER -> KeyboardManager.MediaSearchMode.STICKER
        EmojiPanelMode.EMOJI, EmojiPanelMode.EMOTICON -> KeyboardManager.MediaSearchMode.EMOJI
    }

internal enum class EmojiPanelMode(val label: String, val searchHints: List<String>) {
    EMOJI("Emoji", listOf("Search emoji", "Try “party”", "Try “heart”", "Try “cat”", "Try “thumbs up”")),
    GIF("GIFs", listOf("Search GIFs on KLIPY", "Try “happy dance”", "Try “thank you”", "Try “mind blown”")),
    STICKER("Stickers", listOf("Search stickers", "Try “love”", "Try “hello”", "Try “lol”")),
    EMOTICON("Emoticons", listOf("Search emoji", "Try “shrug”", "Try “smile”")),
    ;

    /** Every panel has its own shape; the search bar morphs between them as you switch. */
    val shape: RoundedPolygon
        get() = when (this) {
            EMOJI -> MaterialShapes.Cookie9Sided
            GIF -> MaterialShapes.Clover4Leaf
            STICKER -> MaterialShapes.Flower
            EMOTICON -> MaterialShapes.Sunny
        }
}

/**
 * ABC on the left, the four panels as a connected Material 3 Expressive button group (the checked one
 * morphs round, fills with the accent and its symbol fills and pops), and a delete that repeats while held.
 */
@Composable
private fun MediaBottomBar(
    mode: EmojiPanelMode,
    onModeChange: (EmojiPanelMode) -> Unit,
    onAbc: () -> Unit,
    onDelete: () -> Unit,
    onDeleteRepeat: () -> Unit,
) {
    val modes = EmojiPanelMode.entries
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AbcButton(onAbc, Modifier.width(62.dp).fillMaxHeight())
        Row(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, m ->
                val checked = mode == m
                val pop by animateFloatAsState(
                    targetValue = if (checked) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
                    label = "mode-pop",
                )
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onModeChange(m) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = m.label
                        },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    // Unchecked segments share ABC's and delete's tone, with full-strength symbols.
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    val bounce = Modifier.graphicsLayer {
                        // Peaks mid-spring, so the symbol pops as its button is chosen.
                        val s = 1f + 0.9f * pop * (1f - pop)
                        scaleX = s
                        scaleY = s
                    }
                    when (m) {
                        EmojiPanelMode.EMOJI -> Icon(
                            if (checked) Symbols.SentimentSatisfiedFilled else Symbols.SentimentSatisfied, null, bounce.size(24.dp),
                        )
                        EmojiPanelMode.GIF -> Icon(Symbols.Gif2, null, bounce.size(24.dp))
                        EmojiPanelMode.STICKER -> Icon(if (checked) Symbols.StickerFilled else Symbols.Sticker, null, bounce.size(24.dp))
                        EmojiPanelMode.EMOTICON -> Text(
                            ":-)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = bounce,
                        )
                    }
                }
            }
        }
        RepeatingDeleteButton(
            onDelete = onDelete,
            onRepeat = onDeleteRepeat,
            modifier = Modifier.width(62.dp).fillMaxHeight(),
        )
    }
}

/** ABC: a pill that squares up while pressed, the expressive button press. */
@Composable
private fun AbcButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 12.dp else 25.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "abc-corner")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = "Back to the keyboard" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("ABC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}
