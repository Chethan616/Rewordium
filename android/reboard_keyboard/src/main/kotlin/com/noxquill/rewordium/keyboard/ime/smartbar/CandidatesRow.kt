/*
 * Copyright (C) 2024-2025 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.smartbar

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.app.ContactsPermissionActivity
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.nlp.ClipboardSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.EmojiSuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.SuggestionCandidate
import com.noxquill.rewordium.keyboard.ime.nlp.engine.ContactsLoader
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import com.noxquill.rewordium.keyboard.nlpManager
import com.noxquill.rewordium.keyboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

import androidx.compose.runtime.rememberUpdatedState

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val inputFeedbackController = LocalInputFeedbackController.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()
    val scope = rememberCoroutineScope()

    val displayMode by prefs.suggestion.displayMode.observeAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()

    // Show contacts prompt in the smartbar when: contacts pref is on, permission
    // not yet granted, prompt not dismissed, and no active suggestion candidates
    // (so it doesn't crowd out results while the user is typing).
    val useContacts by prefs.spelling.useContacts.observeAsState()
    val contactsPromptDismissed by prefs.spelling.contactsPromptDismissed.observeAsState()
    val showContactsPrompt = useContacts &&
        !contactsPromptDismissed &&
        !ContactsLoader.hasPermission(context) &&
        candidates.isEmpty()

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = if (candidates.size > 1) {
            Arrangement.Start
        } else {
            Arrangement.Center
        },
    ) {
        if (showContactsPrompt) {
            ContactsPromptBanner(
                onAllow = {
                    context.startActivity(
                        Intent(context, ContactsPermissionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onDismiss = {
                    scope.launch { prefs.spelling.contactsPromptDismissed.set(true) }
                },
            )
        } else if (candidates.isNotEmpty()) {
            val candidateModifier = if (candidates.size == 1) {
                Modifier
                    .fillMaxHeight()
                    .weight(1f, fill = false)
            } else {
                Modifier
                    .fillMaxHeight()
                    .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                        weight(1f)
                    }
                    .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                        wrapContentWidth().widthIn(max = 160.dp)
                    }
            }
            val list = when (displayMode) {
                CandidatesDisplayMode.CLASSIC -> {
                    // Gboard layout: in the fixed 3-slot strip, always reserve
                    // the rightmost slot for an emoji candidate when present
                    // (the EmojiSuggestionProvider already caps to 1). Without
                    // this split, the trailing emoji is dropped whenever the
                    // text provider fills the first 3 slots.
                    val emoji = candidates.firstOrNull { it is EmojiSuggestionCandidate }
                    val text = candidates.filter { it !is EmojiSuggestionCandidate }
                    if (emoji != null) {
                        text.take(2) + emoji
                    } else {
                        text.take(3)
                    }
                }
                else -> candidates
            }
            for ((n, candidate) in list.withIndex()) {
                // ── Stagger entrance animation ───────────────────────────────────
                // Each chip animates in with a 30 ms delay per item so they
                // cascade in rather than all appearing at once. The alpha +
                // translateY combination follows Emil's philosophy: elements
                // should not pop into existence; they should emerge.
                val staggerAlpha = remember(candidates) { Animatable(0f) }
                val staggerTranslate = remember(candidates) { Animatable(8f) } // dp, converted below
                val density = LocalDensity.current
                LaunchedEffect(candidates) {
                    delay(n * 35L) // 35 ms stagger between items
                    staggerAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                    )
                }
                LaunchedEffect(candidates) {
                    delay(n * 35L)
                    staggerTranslate.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                    )
                }
                // ── End stagger setup ───────────────────────────────────────────
                if (n > 0) {
                    SnyggSpacer(
                        elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .align(Alignment.CenterVertically),
                    )
                }
                CandidateItem(
                    modifier = candidateModifier.graphicsLayer {
                        alpha = staggerAlpha.value
                        translationY = with(density) { staggerTranslate.value.dp.toPx() }
                    },
                    candidate = candidate,
                    displayMode = displayMode,
                    onClick = {
                        inputFeedbackController.keyPress()
                        // Use `candidate` from the displayed `list` — NOT
                        // `candidates[n]`. After the CLASSIC slot-reservation
                        // for emoji, the displayed list is reordered
                        // (e.g. [text1, text2, emoji] from underlying
                        // [text1, text2, text3, emoji]), so indexing back
                        // into `candidates` by the displayed slot number
                        // commits the wrong item — tapping the emoji slot
                        // committed "text3" (e.g. lol → lollipop bug).
                        keyboardManager.commitCandidate(candidate)
                    },
                    onLongPress = {
                        if (candidate.isEligibleForUserRemoval) {
                            inputFeedbackController.keyLongPress()
                            nlpManager.removeSuggestion(subtypeManager.activeSubtype, candidate)
                        } else {
                            false
                        }
                    },
                    longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                )
            }
        } // end else if (candidates.isNotEmpty())
    }
}

@Composable
private fun ContactsPromptBanner(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val style = rememberSnyggThemeQuery(FlorisImeUi.SmartbarCandidateWord.elementName)
    val fg = style.foreground(default = MaterialTheme.colorScheme.onSurface)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAllow)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = fg.copy(alpha = 0.65f),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Allow contacts for better suggestions",
            color = fg.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Dismiss",
            tint = fg.copy(alpha = 0.5f),
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDismiss)
                .padding(6.dp),
        )
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        FlorisImeUi.SmartbarCandidateClip
    } else {
        FlorisImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (currentOnLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}
