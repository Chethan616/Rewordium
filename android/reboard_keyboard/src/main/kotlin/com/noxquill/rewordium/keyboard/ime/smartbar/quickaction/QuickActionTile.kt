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

package com.noxquill.rewordium.keyboard.ime.smartbar.quickaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.keyboard.FlorisImeService
import com.noxquill.rewordium.keyboard.ime.keyboard.ComputingEvaluator
import com.noxquill.rewordium.keyboard.ime.keyboard.computeImageVector
import com.noxquill.rewordium.keyboard.ime.keyboard.computeLabel
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MorphBlob
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** Each action keeps the same library shape wherever it shows up, so it is recognisable at a glance. */
private fun QuickAction.shapeSeed(): Int = when (this) {
    is QuickAction.InsertKey -> data.code.absoluteValue
    is QuickAction.InsertText -> data.hashCode().absoluteValue
}

/** Toggles that are switched on get the filled treatment. */
private fun QuickAction.isActive(evaluator: ComputingEvaluator): Boolean =
    keyData().code == KeyCode.TOGGLE_INCOGNITO_MODE && evaluator.state.isIncognitoMode

/**
 * A quick action as a Material 3 Expressive tile, for the overflow grid and the action editor. The icon
 * sits on the action's own MaterialShape, tinted by the keyboard theme; pressing squishes the tile, rounds
 * its corners and springs the shape into another one with a turn. Tiles pop in one after another when the
 * grid opens ([enterDelayIndex]); in the editor the dragged tile is [lifted] and tilts.
 */
@Composable
internal fun QuickActionTile(
    action: QuickAction,
    evaluator: ComputingEvaluator,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    toneIndex: Int = 0,
    enterDelayIndex: Int = -1,
    lifted: Boolean = false,
) {
    val code = action.keyData().code
    if (code == KeyCode.NOOP || code == KeyCode.DRAG_MARKER) {
        EmptyActionSlot(marker = code == KeyCode.DRAG_MARKER, label = action.computeDisplayName(evaluator), modifier = modifier)
        return
    }

    val context = LocalContext.current
    val feedback = FlorisImeService.inputFeedbackController()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val enabled = !interactive || evaluator.evaluateEnabled(action.keyData())
    val active = action.isActive(evaluator)
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val tone = rememberTones().at(toneIndex)
    // Light themes get pale containers; nudge each shape toward its own accent so it still pops.
    val accent = listOf(colors.primary, colors.secondary, colors.tertiary)[toneIndex.mod(3)]
    val shapeColor = when {
        active -> colors.primary
        colors.surface.luminance() > 0.5f -> lerp(tone.container, accent, 0.2f)
        else -> tone.container
    }
    val name = action.computeDisplayName(evaluator)

    if (interactive) {
        // Cancel a held key if the tile leaves the composition mid-press, so it can't get stuck down.
        DisposableEffect(action, enabled) {
            onDispose {
                if (action is QuickAction.InsertKey) action.onPointerCancel(context)
            }
        }
    }

    val corner = animateDpAsState(
        targetValue = when {
            pressed -> 36.dp
            lifted -> 30.dp
            active -> 28.dp
            else -> 20.dp
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "action-corner",
    )
    val scale = animateFloatAsState(
        targetValue = when {
            pressed -> 0.9f
            lifted -> 1.1f
            else -> 1f
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "action-scale",
    )
    val tilt = animateFloatAsState(if (lifted) -5f else 0f, motion.defaultSpatialSpec(), label = "action-tilt")
    val container by animateColorAsState(
        targetValue = when {
            active -> tone.container
            lifted -> colors.surfaceContainerHighest
            else -> colors.surfaceContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "action-container",
    )
    val appear = remember { Animatable(if (enterDelayIndex >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enterDelayIndex < 0) return@LaunchedEffect
        delay(enterDelayIndex.coerceAtMost(11) * 26L)
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
    }

    val restShape = MediaShapes.blob(action.shapeSeed())
    val blobShape = if (pressed || lifted) MediaShapes.blob(action.shapeSeed() + 5) else restShape

    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val a = appear.value
                val s = scale.value * (0.55f + 0.45f * a)
                scaleX = s
                scaleY = s
                alpha = a.coerceIn(0f, 1f) * if (enabled) 1f else 0.38f
                translationY = (1f - a) * 18.dp.toPx()
                rotationZ = tilt.value
                shadowElevation = if (lifted) 10.dp.toPx() else 0f
                shape = RoundedCornerShape(corner.value)
                clip = true
            }
            .background(container)
            .indication(interaction, ripple(color = tone.content))
            .then(
                if (interactive) {
                    Modifier.pointerInput(action, enabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            if (!enabled) return@awaitEachGesture
                            val press = PressInteraction.Press(down.position)
                            feedback?.keyPress(TextKeyData.UNSPECIFIED)
                            interaction.tryEmit(press)
                            action.onPointerDown(context)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                interaction.tryEmit(PressInteraction.Release(press))
                                action.onPointerUp(context)
                            } else {
                                interaction.tryEmit(PressInteraction.Cancel(press))
                                action.onPointerCancel(context)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = name
                role = Role.Button
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            MorphBlob(
                shape = blobShape,
                color = shapeColor,
                modifier = Modifier.size(46.dp),
                turnPerChange = 60f,
            ) {
                ActionGlyph(action, evaluator, tint = if (active) colors.onPrimary else tone.content)
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) tone.content else colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionGlyph(action: QuickAction, evaluator: ComputingEvaluator, tint: androidx.compose.ui.graphics.Color) {
    when (action) {
        is QuickAction.InsertKey -> {
            val (imageVector, label) = remember(action, evaluator) {
                evaluator.computeImageVector(action.data) to evaluator.computeLabel(action.data)
            }
            if (imageVector != null) {
                Icon(imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            } else if (label != null) {
                Text(label, color = tint, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
        is QuickAction.InsertText -> {
            Text(
                text = action.data.firstOrNull()?.toString()?.ifBlank { "?" } ?: "?",
                color = tint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** An empty spot in the editor: where a dragged tile will land, or a section with nothing in it yet. */
@Composable
private fun EmptyActionSlot(marker: Boolean, label: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val stroke = if (marker) colors.primary else colors.outlineVariant
    val fill = if (marker) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainerLow
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .drawBehind {
                val r = CornerRadius(22.dp.toPx())
                drawRoundRect(fill, cornerRadius = r)
                drawRoundRect(
                    color = stroke,
                    cornerRadius = r,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (marker) {
            MorphBlob(
                shape = MediaShapes.blob(label.length),
                color = colors.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp),
            )
        } else if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}
