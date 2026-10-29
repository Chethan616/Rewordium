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

package com.noxquill.rewordium.keyboard.ime.theme.studio

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.noxquill.rewordium.app.ui.avatar.BOT_TYPES
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import kotlinx.coroutines.delay

/** The key press animation in use and how strongly it plays (1 = as designed). */
data class KeyMotion(val style: KeyPressAnimation, val strength: Float) {
    val active: Boolean get() = style != KeyPressAnimation.NONE && strength > 0f

    /** Pressing goes in fast; letting go springs back with the style's own bounce. */
    fun spec(pressing: Boolean): AnimationSpec<Float> = if (pressing) {
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh)
    } else {
        when (style) {
            KeyPressAnimation.BOUNCE -> spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium)
            KeyPressAnimation.POP -> spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
            KeyPressAnimation.SQUISH -> spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium)
            KeyPressAnimation.WOBBLE -> spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessMediumLow)
            KeyPressAnimation.JELLY -> spring(dampingRatio = 0.16f, stiffness = Spring.StiffnessMediumLow)
            KeyPressAnimation.NONE -> spring()
        }
    }

    companion object {
        val NONE = KeyMotion(KeyPressAnimation.NONE, 0f)
    }
}

/**
 * The key press animation: [pressed] drives a spring from 0 (rest) to 1 (pressed), and the release
 * overshoots past 0, which is what makes keys bounce, wobble and jiggle back.
 */
@Composable
fun Modifier.keyPressMotion(pressed: Boolean, motion: KeyMotion): Modifier {
    if (!motion.active) return this
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motion.spec(pressed),
        label = "key-press",
    )
    val k = motion.strength
    return this
        .zIndex(if (pressed) 1f else 0f)
        .graphicsLayer {
            val p = progress * k
            when (motion.style) {
                KeyPressAnimation.BOUNCE -> {
                    val s = 1f - 0.13f * p
                    scaleX = s; scaleY = s
                }
                KeyPressAnimation.POP -> {
                    val s = 1f + 0.18f * p
                    scaleX = s; scaleY = s
                    translationY = -7.dp.toPx() * p
                }
                KeyPressAnimation.SQUISH -> {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = 1f + 0.12f * p
                    scaleY = 1f - 0.2f * p
                }
                KeyPressAnimation.WOBBLE -> {
                    rotationZ = 9f * p
                    val s = 1f - 0.06f * p
                    scaleX = s; scaleY = s
                }
                KeyPressAnimation.JELLY -> {
                    scaleX = 1f + 0.16f * p
                    scaleY = 1f - 0.12f * p
                }
                KeyPressAnimation.NONE -> Unit
            }
        }
}

/** The space-bar mascot: which bot (blank for none), whether it reacts to typing, and whether it idles alive. */
data class MascotStyle(val type: String, val reactions: Boolean, val lively: Boolean) {
    val visible: Boolean get() = type in BOT_TYPES
}

/**
 * The bot that sits at the start of the space bar. It hops and grins when space is pressed, and dozes off
 * when typing stops. Still by default (a resting portrait, no redraws while idle); "lively" lets it
 * breathe, blink and look around.
 */
@Composable
fun BoxScope.SpaceBarMascot(style: MascotStyle, spacePressed: Boolean, keyHeight: Dp) {
    if (!style.visible) return
    var mood by remember { mutableStateOf(BotMood.Idle) }
    var hop by remember { mutableIntStateOf(0) }
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(spacePressed) {
        if (spacePressed && style.reactions) {
            presses++
            hop++
        }
    }
    LaunchedEffect(presses, style.reactions) {
        if (!style.reactions) {
            mood = BotMood.Idle
            return@LaunchedEffect
        }
        if (presses > 0) {
            mood = BotMood.Happy
            delay(1_400)
        }
        mood = BotMood.Idle
        delay(25_000)
        mood = BotMood.Sleeping
    }
    val size = (keyHeight * 0.72f).coerceAtMost(30.dp)
    Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) {
        BotAvatar(
            type = style.type,
            mood = mood,
            size = size,
            interactive = false,
            still = !style.lively,
            hopSignal = hop,
            label = "Keyboard mascot",
        )
    }
}
