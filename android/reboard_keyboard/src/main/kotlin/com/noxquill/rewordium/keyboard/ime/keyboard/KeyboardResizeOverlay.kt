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

package com.noxquill.rewordium.keyboard.ime.keyboard

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.R
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.isOrientationPortrait
import org.florisboard.lib.compose.stringRes
import kotlin.math.roundToInt

private const val MIN_HEIGHT_FACTOR = 50
private const val MAX_HEIGHT_FACTOR = 150
private const val DEFAULT_HEIGHT_FACTOR = 100
private const val STEP = 5

/**
 * Gboard-style in-keyboard resize mode. Covers the keys (so nothing is typed
 * by accident) and lets the user drag the top handle, or use −/+, to change
 * the keyboard height for the current orientation. Changes apply live.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyboardResizeOverlay(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val heightPref = if (configuration.isOrientationPortrait()) {
        prefs.keyboard.heightFactorPortrait
    } else {
        prefs.keyboard.heightFactorLandscape
    }
    val factor by heightPref.observeAsState()

    var overlayHeightPx by remember { mutableIntStateOf(0) }
    var dragStartRawY by remember { mutableFloatStateOf(0f) }
    var dragStartFactor by remember { mutableIntStateOf(DEFAULT_HEIGHT_FACTOR) }
    var dragStartHeightPx by remember { mutableIntStateOf(0) }

    fun setFactor(value: Int) {
        val clamped = value.coerceIn(MIN_HEIGHT_FACTOR, MAX_HEIGHT_FACTOR)
        if (clamped != heightPref.get()) {
            scope.launch { heightPref.set(clamped) }
        }
    }

    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f)
    val onScrim = Color.White
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .background(scrim)
            .onSizeChanged { overlayHeightPx = it.height }
            // Claim every touch so keys underneath never fire (children still
            // receive their events first).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle along the top edge. Uses raw (screen) coordinates:
            // the keyboard grows under the finger while dragging, so local
            // coordinates would feed back into the gesture.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                dragStartRawY = event.rawY
                                dragStartFactor = heightPref.get()
                                dragStartHeightPx = overlayHeightPx.coerceAtLeast(1)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val grownPx = dragStartRawY - event.rawY
                                val ratio = (dragStartHeightPx + grownPx) / dragStartHeightPx
                                setFactor((dragStartFactor * ratio).roundToInt())
                            }
                        }
                        true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(Modifier.width(6.dp))
                    Text(
                        text = stringRes(R.string.keyboard_resize__title),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResizeRoundButton(onClick = { setFactor(factor - STEP) }, tint = onScrim) {
                        Icon(Icons.Outlined.Remove, contentDescription = null, tint = onScrim)
                    }
                    Text(
                        text = stringRes(R.string.keyboard_resize__height, "v" to factor),
                        color = onScrim,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    ResizeRoundButton(onClick = { setFactor(factor + STEP) }, tint = onScrim) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = onScrim)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                ResizePillButton(
                    text = stringRes(R.string.keyboard_resize__reset),
                    background = Color.White.copy(alpha = 0.18f),
                    foreground = onScrim,
                    onClick = { setFactor(DEFAULT_HEIGHT_FACTOR) },
                )
                ResizePillButton(
                    text = stringRes(R.string.keyboard_resize__done),
                    background = accent,
                    foreground = MaterialTheme.colorScheme.onPrimary,
                    onClick = { keyboardManager.setResizeMode(false) },
                )
            }
        }
    }
}

@Composable
private fun ResizeRoundButton(onClick: () -> Unit, tint: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ResizePillButton(text: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
