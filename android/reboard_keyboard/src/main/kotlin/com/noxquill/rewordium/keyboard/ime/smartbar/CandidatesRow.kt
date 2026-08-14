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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    val useContacts by prefs.spelling.useContacts.observeAsState()
    val contactsPromptDismissed by prefs.spelling.contactsPromptDismissed.observeAsState()
    val showContactsPrompt = useContacts &&
        !contactsPromptDismissed &&
        !ContactsLoader.hasPermission(context) &&
        candidates.isEmpty()

    val displayedCandidates = when (displayMode) {
        CandidatesDisplayMode.CLASSIC -> classicCandidates(candidates)
        else -> candidates
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .conditional(
                displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE &&
                    displayedCandidates.size > 1,
            ) {
                florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = if (displayedCandidates.size > 1) {
            Arrangement.Start
        } else {
            Arrangement.Center
        },
    ) {
        when {
            displayedCandidates.isNotEmpty() -> {
                for ((index, candidate) in displayedCandidates.withIndex()) {
                    if (index > 0) {
                        SnyggSpacer(
                            elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.56f)
                                .align(Alignment.CenterVertically),
                        )
                    }

                    val candidateModifier = when {
                        displayedCandidates.size == 1 -> {
                            Modifier
                                .fillMaxHeight()
                                .weight(1f, fill = false)
                        }

                        displayMode == CandidatesDisplayMode.CLASSIC -> {
                            Modifier
                                .fillMaxHeight()
                                .weight(classicSlotWeight(displayedCandidates.size, index))
                        }

                        else -> {
                            Modifier
                                .fillMaxHeight()
                                .wrapContentWidth()
                                .widthIn(max = 160.dp)
                        }
                    }

                    CandidateItem(
                        modifier = candidateModifier,
                        candidate = candidate,
                        displayMode = displayMode,
                        onClick = {
                            inputFeedbackController.keyPress()
                            // Commit the displayed candidate itself. Classic mode
                            // intentionally moves the best candidate to the
                            // center, so source-list indexing would be wrong.
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
            }

            showContactsPrompt -> {
                ContactsPromptBanner(
                    onAllow = {
                        context.startActivity(Intent(context, ContactsPermissionActivity::class.java))
                    },
                    onDismiss = {
                        scope.launch { prefs.spelling.contactsPromptDismissed.set(true) }
                    },
                )
            }
        }
    }
}

/**
 * Keeps the highest-confidence text candidate in the visual center, where it
 * gets the most room and is easiest to reach. Emojis remain in the trailing
 * slot so they never displace the primary word candidate.
 */
private fun classicCandidates(candidates: List<SuggestionCandidate>): List<SuggestionCandidate> {
    val emoji = candidates.firstOrNull { it is EmojiSuggestionCandidate }
    val text = candidates.filterNot { it is EmojiSuggestionCandidate }
    val textSlots = text.take(if (emoji != null) 2 else 3)
    val primary = textSlots.firstOrNull()
    val alternatives = textSlots.drop(1)

    val orderedText = when {
        primary == null -> emptyList()
        alternatives.size >= 2 -> listOf(alternatives[0], primary, alternatives[1])
        alternatives.size == 1 -> listOf(alternatives[0], primary)
        else -> listOf(primary)
    }

    return orderedText + listOfNotNull(emoji)
}

private fun classicSlotWeight(candidateCount: Int, index: Int): Float {
    if (candidateCount != 3) return 1f
    return if (index == 1) 1.35f else 0.825f
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
) {
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
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            } else {
                Modifier
            },
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
