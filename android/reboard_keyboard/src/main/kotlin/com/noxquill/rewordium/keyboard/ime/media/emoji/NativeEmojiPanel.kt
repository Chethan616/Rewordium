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

package com.noxquill.rewordium.keyboard.ime.media.emoji

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Phase 7 replacement for [EmojiPaletteView]. Wraps Google's official
 * `androidx.emoji2.emojipicker.EmojiPickerView` (Apache 2.0) so we get
 * the platform-blessed picker UX — category tabs, built-in search,
 * skin-tone long-press, accessibility — instead of maintaining a ~900-line
 * hand-rolled Compose panel.
 *
 * The picker is an Android `View`, not Compose. We host it via [AndroidView]
 * and bridge two callbacks:
 *   * [EmojiPickerView.setOnEmojiPickedListener] → [editorInstance.commitText]
 *   * [EmojiPickerView.setRecentEmojiProvider] → our existing
 *     [EmojiHistoryHelper] so recents survive the swap with no migration.
 *
 * Theme: relies on the inherited IME theme (Material). A Snygg-driven
 * theme override is a follow-up; the picker exposes most colors through
 * standard `?attr/colorSurface`/`?attr/colorPrimary` so a `ContextTheme-
 * Wrapper` is enough to retint without touching the picker source.
 *
 * NOTE: the [fullEmojiMappings] parameter is intentionally unused — the
 * picker ships with its own emoji catalog kept in lockstep with the
 * platform's emoji-compat metadata, which is fresher than our shipped
 * `ime/media/emoji` text snapshots. We keep the parameter for signature
 * compatibility with [EmojiPaletteView] so the call-site swap is one line.
 */
@Composable
fun NativeEmojiPanel(
    @Suppress("UNUSED_PARAMETER") fullEmojiMappings: EmojiData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            EmojiPickerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                emojiGridColumns = EMOJI_GRID_COLUMNS
                setRecentEmojiProvider(
                    FlorisRecentEmojiProvider(scope, prefs)
                )
                setOnEmojiPickedListener { item ->
                    // Direct commit — bypasses the keyboard event dispatcher
                    // so we don't loop back through any emoji-search intercept.
                    editorInstance.commitText(item.emoji)
                    scope.launch {
                        // markEmojiUsed updates pinned/recent in the same
                        // historyData prefs that FlorisRecentEmojiProvider
                        // reads, so the next picker render reflects it.
                        // We round-trip via the existing EmojiHistoryHelper
                        // path rather than poking prefs directly so any
                        // future invariants (cap sizes, dedup) stay in
                        // one place.
                        EmojiHistoryHelper.markEmojiUsed(prefs, Emoji(item.emoji, "", emptyList()))
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Bridges the picker's [RecentEmojiProvider] contract to our existing
 * [FlorisPreferenceModel.emoji.historyData] pref. We expose the union of
 * pinned + recent (pinned first, then recent in MRU order) so the
 * picker's Recents tab matches what the user pinned in the legacy panel
 * — important for the pinned set to survive the panel swap.
 */
private class FlorisRecentEmojiProvider(
    private val scope: CoroutineScope,
    private val prefs: com.noxquill.rewordium.keyboard.app.FlorisPreferenceModel,
) : RecentEmojiProvider {

    override suspend fun getRecentEmojiList(): List<String> {
        val data = prefs.emoji.historyData.get()
        val pinned = data.pinned.map { it.value }
        val recent = data.recent.map { it.value }
        return (pinned + recent).distinct()
    }

    override fun recordSelection(emoji: String) {
        // The picker calls this synchronously after the user taps; we record
        // into the same prefs the legacy panel used so both implementations
        // stay in sync if a user toggles back and forth via the feature flag.
        // markEmojiUsed is suspending — launch on the bridge's own scope so
        // the picker thread isn't blocked.
        scope.launch {
            EmojiHistoryHelper.markEmojiUsed(prefs, Emoji(emoji, "", emptyList()))
        }
    }
}

private const val EMOJI_GRID_COLUMNS = 9
