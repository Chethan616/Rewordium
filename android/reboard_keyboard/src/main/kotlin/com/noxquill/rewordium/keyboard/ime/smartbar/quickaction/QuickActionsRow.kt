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

package com.noxquill.rewordium.keyboard.ime.smartbar.quickaction

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.ime.smartbar.SmartbarLayout
import com.noxquill.rewordium.keyboard.ime.text.key.KeyCode
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.SnyggRow

private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
private const val KEY_AI_ENABLED = "flutter.paraphraser_enabled"

internal val ToggleOverflowPanelAction = QuickAction.InsertKey(TextKeyData.TOGGLE_ACTIONS_OVERFLOW)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun QuickActionsRow(
    elementName: String,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val flipToggles by prefs.smartbar.flipToggles.observeAsState()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    val smartbarLayout by prefs.smartbar.layout.observeAsState()
    val actionArrangement by prefs.smartbar.actionArrangement.observeAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.observeAsState()
    
    // Check if AI features are enabled from Flutter SharedPreferences
    val isAiEnabled = remember {
        val flutterPrefs = context.getSharedPreferences(FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
        flutterPrefs.getBoolean(KEY_AI_ENABLED, false)
    }

    val dynamicActions = remember(smartbarLayout, actionArrangement, isAiEnabled) {
        val baseActions = if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
            buildList {
                add(actionArrangement.stickyAction!!)
                addAll(actionArrangement.dynamicActions)
            }
        } else {
            actionArrangement.dynamicActions
        }
        // Filter out AI_REWRITE action if AI is disabled
        if (isAiEnabled) {
            baseActions
        } else {
            baseActions.filter { action ->
                when (action) {
                    is QuickAction.InsertKey -> action.data.code != KeyCode.AI_REWRITE
                    else -> true
                }
            }
        }
    }
    val showOverflowAction = actionArrangement.stickyAction != null ||
        smartbarLayout != SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || !sharedActionsExpanded

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toDp()
        val height = constraints.maxHeight.toDp()
        val numActionsToShow = ((width / height).toInt() - (if (showOverflowAction) 1 else 0)).coerceAtLeast(0)
        val visibleActions = dynamicActions
            .subList(0, numActionsToShow.coerceAtMost(dynamicActions.size))

        SideEffect {
            keyboardManager.smartbarVisibleDynamicActionsCount =
                if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
                    numActionsToShow - 1
                } else {
                    numActionsToShow
                }.coerceAtLeast(0)
        }

        SnyggRow(
            elementName = elementName,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (showOverflowAction && flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
            for (action in visibleActions) {
                QuickActionButton(action, evaluator)
            }
            if (showOverflowAction && !flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
        }
    }
}
