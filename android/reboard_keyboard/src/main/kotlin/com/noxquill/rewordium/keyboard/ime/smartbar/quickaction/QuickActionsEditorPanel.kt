/*
 * Copyright (C) 2022-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.smartbar.quickaction

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.toSize
import androidx.graphics.shapes.RoundedPolygon
import com.noxquill.rewordium.keyboard.FlorisImeService
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaSectionHeader
import com.noxquill.rewordium.keyboard.ime.media.ui.MorphBlob
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.Tone
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.lib.toIntOffset
import org.florisboard.lib.compose.stringRes
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn

private const val ItemNotFound = -1
private val NoopAction = QuickAction.InsertKey(TextKeyData(code = KeyCode.NOOP))
private val DragMarkerAction = QuickAction.InsertKey(TextKeyData(code = KeyCode.DRAG_MARKER))

@Composable
fun QuickActionsEditorPanel() = MediaExpressiveTheme {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    // We get the current arrangement once and do not observe on purpose
    val actionArrangement = remember { prefs.smartbar.actionArrangement.get() }
    var stickyAction by remember(actionArrangement) {
        mutableStateOf(actionArrangement.stickyAction ?: NoopAction)
    }
    val dynamicActions = remember(actionArrangement) {
        actionArrangement.dynamicActions.ifEmpty { listOf(NoopAction) }.toMutableStateList()
    }
    val hiddenActions = remember(actionArrangement) {
        actionArrangement.hiddenActions.ifEmpty { listOf(NoopAction) }.toMutableStateList()
    }

    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    val gridState = rememberLazyGridState()
    var activeDragAction by remember { mutableStateOf<QuickAction?>(null) }
    var activeDragPosition by remember { mutableStateOf(IntOffset.Zero) }
    var activeDragSize by remember { mutableStateOf(IntSize.Zero) }

    fun findItemForOffsetOrClosestInRow(offset: IntOffset): LazyGridItemInfo? {
        var closestItemInRow: LazyGridItemInfo? = null
        // Using manual for loop with indices instead of firstOrNull() because this method gets
        // called a lot and firstOrNull allocates an iterator for each call
        for (index in gridState.layoutInfo.visibleItemsInfo.indices) {
            val item = gridState.layoutInfo.visibleItemsInfo[index]
            if (offset.y in item.offset.y..(item.offset.y + item.size.height)) {
                if (offset.x in item.offset.x..(item.offset.x + item.size.width)) {
                    return item
                }
                closestItemInRow = item
            }
        }
        return closestItemInRow
    }

    fun indexOfStickyAction(item: LazyGridItemInfo): Int {
        val i = item.index
        return if (i == 1) 0 else ItemNotFound
    }

    fun indexOfDynamicAction(item: LazyGridItemInfo): Int {
        val i = item.index
        val base = 3
        return if (i >= base && i < (dynamicActions.size + base)) i - base else ItemNotFound
    }

    fun indexOfHiddenAction(item: LazyGridItemInfo): Int {
        val i = item.index
        val base = dynamicActions.size + 4
        return if (i >= base && i < (hiddenActions.size + base)) i - base else ItemNotFound
    }

    fun keyOf(action: QuickAction): Any? {
        return if (action.keyData().code == KeyCode.NOOP) {
            null
        } else {
            action.hashCode()
        }
    }

    fun removeAllMarkers() {
        if (stickyAction == DragMarkerAction) {
            stickyAction = NoopAction
        }
        dynamicActions.remove(DragMarkerAction)
        if (dynamicActions.isEmpty()) {
            dynamicActions.add(NoopAction)
        }
        hiddenActions.remove(DragMarkerAction)
        if (hiddenActions.isEmpty()) {
            hiddenActions.add(NoopAction)
        }
    }

    fun beginDragGesture(pos: IntOffset) {
        val item = findItemForOffsetOrClosestInRow(pos) ?: return
        val stickyActionIndex = indexOfStickyAction(item)
        val dynamicActionIndex = indexOfDynamicAction(item)
        val hiddenActionIndex = indexOfHiddenAction(item)
        if (stickyActionIndex != ItemNotFound && stickyAction != NoopAction) {
            activeDragAction = stickyAction
            stickyAction = DragMarkerAction
        } else if (dynamicActionIndex != ItemNotFound && dynamicActions[dynamicActionIndex] != NoopAction) {
            activeDragAction = dynamicActions[dynamicActionIndex]
            dynamicActions[dynamicActionIndex] = DragMarkerAction
        } else if (hiddenActionIndex != ItemNotFound && hiddenActions[hiddenActionIndex] != NoopAction) {
            activeDragAction = hiddenActions[hiddenActionIndex]
            hiddenActions[hiddenActionIndex] = DragMarkerAction
        } else {
            return
        }
        activeDragPosition = pos
        activeDragSize = item.size
        FlorisImeService.inputFeedbackController()?.uiReveal()
    }

    fun handleDragGestureChange(posChange: IntOffset) {
        if (activeDragAction == null) return
        val pos = activeDragPosition + posChange
        activeDragPosition = pos
        val item = findItemForOffsetOrClosestInRow(pos) ?: return
        val stickyActionIndex = indexOfStickyAction(item)
        val dynamicActionIndex = indexOfDynamicAction(item)
        val hiddenActionIndex = indexOfHiddenAction(item)
        if (stickyActionIndex != ItemNotFound && stickyAction != DragMarkerAction) {
            if (stickyAction != NoopAction) {
                dynamicActions.add(0, stickyAction)
            }
            removeAllMarkers()
            stickyAction = DragMarkerAction
            FlorisImeService.inputFeedbackController()?.uiTick()
        } else if (dynamicActionIndex != ItemNotFound && dynamicActions[dynamicActionIndex] != DragMarkerAction) {
            if (dynamicActions[dynamicActionIndex] == NoopAction) {
                removeAllMarkers()
                dynamicActions[dynamicActionIndex] = DragMarkerAction
            } else {
                removeAllMarkers()
                dynamicActions.add(dynamicActionIndex, DragMarkerAction)
            }
            FlorisImeService.inputFeedbackController()?.uiTick()
        } else if (hiddenActionIndex != ItemNotFound && hiddenActions[hiddenActionIndex] != DragMarkerAction) {
            if (hiddenActions[hiddenActionIndex] == NoopAction) {
                removeAllMarkers()
                hiddenActions[hiddenActionIndex] = DragMarkerAction
            } else {
                removeAllMarkers()
                hiddenActions.add(hiddenActionIndex, DragMarkerAction)
            }
            FlorisImeService.inputFeedbackController()?.uiTick()
        }
    }

    fun completeDragGestureAndCleanUp() {
        val action = activeDragAction
        if (action != null) {
            if (stickyAction == DragMarkerAction) {
                stickyAction = action
            } else {
                val i = dynamicActions.indexOf(DragMarkerAction)
                if (i >= 0) {
                    dynamicActions[i] = action
                } else {
                    val j = hiddenActions.indexOf(DragMarkerAction)
                    if (j >= 0) {
                        hiddenActions[j] = action
                    }
                }
            }
        }
        activeDragAction = null
        activeDragPosition = IntOffset.Zero
        activeDragSize = IntSize.Zero
    }

    DisposableEffect(Unit) {
        onDispose {
            completeDragGestureAndCleanUp()
            val newActionArrangement = QuickActionArrangement(
                if (stickyAction != NoopAction && stickyAction != DragMarkerAction) stickyAction else null,
                dynamicActions.filter { it != NoopAction && it != DragMarkerAction },
                hiddenActions.filter { it != NoopAction && it != DragMarkerAction },
            )
            runBlocking {
                prefs.smartbar.actionArrangement.set(newActionArrangement)
            }
            if (keyboardManager.activeState.isActionsEditorVisible) {
                keyboardManager.activeState.isActionsEditorVisible = false
            }
        }
    }

    val tones = rememberTones()
    SnyggColumn(FlorisImeUi.SmartbarActionsEditor.elementName, modifier = Modifier.safeDrawingPadding()) {
        EditorHeader(
            dragging = activeDragAction != null,
            onBack = { keyboardManager.activeState.isActionsEditorVisible = false },
        )

        SnyggBox(FlorisImeUi.SmartbarActionsEditorTileGrid.elementName) {
            LazyVerticalGrid(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { beginDragGesture(it.toIntOffset()) },
                            onDrag = { _, it -> handleDragGestureChange(it.toIntOffset()) },
                            onDragEnd = { completeDragGestureAndCleanUp() },
                            onDragCancel = { completeDragGestureAndCleanUp() },
                        )
                    },
                columns = GridCells.Adaptive(FlorisImeSizing.smartbarHeight * 1.9f),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val n = if (stickyAction != NoopAction) 1 else 0
                    Subheader(
                        text = stringRes(R.string.quick_actions_editor__subheader_sticky_action, "n" to n),
                        icon = Symbols.PushPin,
                        tone = tones.at(0),
                        shape = MaterialShapes.Cookie9Sided,
                    )
                }
                item(key = keyOf(stickyAction)) {
                    QuickActionButton(
                        modifier = Modifier.animateItem(),
                        action = stickyAction,
                        evaluator = evaluator,
                        type = QuickActionBarType.EDITOR_TILE,
                        toneIndex = 0,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val n = dynamicActions.count { it != NoopAction }
                    Subheader(
                        text = stringRes(R.string.quick_actions_editor__subheader_dynamic_actions, "n" to n),
                        icon = Symbols.Bolt,
                        tone = tones.at(1),
                        shape = MaterialShapes.Sunny,
                    )
                }
                itemsIndexed(dynamicActions, key = { i, a -> keyOf(a) ?: i }) { index, action ->
                    QuickActionButton(
                        modifier = Modifier.animateItem(),
                        action = action,
                        evaluator = evaluator,
                        type = QuickActionBarType.EDITOR_TILE,
                        toneIndex = index + 1,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val n = hiddenActions.count { it != NoopAction }
                    Subheader(
                        text = stringRes(R.string.quick_actions_editor__subheader_hidden_actions, "n" to n),
                        icon = Symbols.Inventory2,
                        tone = tones.at(2),
                        shape = MaterialShapes.Clover4Leaf,
                    )
                }
                itemsIndexed(hiddenActions, key = { i, a -> keyOf(a) ?: i }) { index, action ->
                    QuickActionButton(
                        modifier = Modifier.animateItem(),
                        action = action,
                        evaluator = evaluator,
                        type = QuickActionBarType.EDITOR_TILE,
                        toneIndex = index + 2,
                    )
                }
            }
            if (activeDragAction != null) {
                val size = with(LocalDensity.current) {
                    remember(activeDragSize) { activeDragSize.toSize().toDpSize() }
                }
                QuickActionButton(
                    modifier = Modifier
                        .size(size)
                        .offset { activeDragPosition }
                        .offset(-size.width / 2, -size.height / 2),
                    action = activeDragAction!!,
                    evaluator = evaluator,
                    type = QuickActionBarType.EDITOR_TILE,
                    lifted = true,
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(dragging: Boolean, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(Symbols.ArrowBack, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        // The badge bursts while a tile is being carried.
        MorphBlob(
            shape = if (dragging) MaterialShapes.SoftBurst else MaterialShapes.Cookie9Sided,
            color = if (dragging) colors.primary else colors.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Symbols.Tune,
                contentDescription = null,
                tint = if (dragging) colors.onPrimary else colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringRes(R.string.quick_actions_editor__header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(R.string.quick_actions_editor__hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Subheader(
    text: String,
    icon: ImageVector,
    tone: Tone,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
) {
    MediaSectionHeader(text = text, icon = icon, tone = tone, shape = shape, modifier = modifier.fillMaxWidth())
}
