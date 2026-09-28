/*
 * Copyright (C) 2024-2026 The ReBoard Contributors
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

package com.noxquill.rewordium.keyboard.ime.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Quickreply
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.editor.AiTextSnapshot
import com.noxquill.rewordium.keyboard.ime.input.LocalInputFeedbackController
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.KeyboardLikeButton
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.ime.theme.FlorisImeUi
import com.noxquill.rewordium.keyboard.keyboardManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** The panel's screens: pick a tool → (language) → working → result → applied. */
private sealed interface PanelState {
    data object Tools : PanelState
    data object Languages : PanelState
    data class Working(val tool: AiTool) : PanelState
    data class Done(val tool: AiTool, val text: String) : PanelState
    data class Failed(val tool: AiTool, val message: String, val kind: AIException.Kind, val retryInput: String?) : PanelState
    data class Applied(val tool: AiTool, val edit: AppliedEdit) : PanelState
}

/** The text a tool works on and where it sits in the field. */
private data class AiSource(val snapshot: AiTextSnapshot, val text: String, val start: Int, val end: Int)

/** What applying changed, so Undo can put it back. */
private data class AppliedEdit(val start: Int, val inserted: String, val original: String)

private data class AiAccount(val loggedIn: Boolean, val pro: Boolean, val credits: Int, val ownKey: Boolean)

private const val PANEL_PREFS = "ai_panel"
private const val KEY_LANGUAGE = "translate_language"

private val TOOL_GRID = listOf(
    listOf(AiTool.PROOFREAD, AiTool.REWRITE, AiTool.SHORTEN, AiTool.EXPAND),
    listOf(AiTool.PROFESSIONAL, AiTool.FRIENDLY, AiTool.CASUAL, AiTool.CONFIDENT),
    listOf(AiTool.SUMMARIZE, AiTool.BULLETS, AiTool.EMOJIFY, AiTool.TRANSLATE),
    listOf(AiTool.CONTINUE, AiTool.FUNNY, AiTool.ACADEMIC, AiTool.POETIC),
)

/** Follow-up tweaks offered on a result. */
private val REFINE_TOOLS = listOf(
    AiTool.SHORTEN, AiTool.EXPAND, AiTool.PROFESSIONAL, AiTool.FRIENDLY,
    AiTool.CASUAL, AiTool.CONFIDENT, AiTool.EMOJIFY, AiTool.PROOFREAD,
)

private val LANGUAGES = listOf(
    "English", "Hindi", "Hinglish", "Spanish", "French", "German", "Tamil", "Telugu",
    "Kannada", "Malayalam", "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu", "Arabic",
    "Portuguese", "Italian", "Russian", "Japanese", "Korean", "Chinese", "Indonesian", "Turkish",
)

private fun AiTool.icon(): ImageVector = when (this) {
    AiTool.PROOFREAD -> Icons.Rounded.Spellcheck
    AiTool.REWRITE -> Icons.Rounded.AutoFixHigh
    AiTool.SHORTEN -> Icons.AutoMirrored.Rounded.ShortText
    AiTool.EXPAND -> Icons.AutoMirrored.Rounded.Notes
    AiTool.SUMMARIZE -> Icons.Rounded.Summarize
    AiTool.BULLETS -> Icons.AutoMirrored.Rounded.FormatListBulleted
    AiTool.PROFESSIONAL -> Icons.Rounded.BusinessCenter
    AiTool.FRIENDLY -> Icons.Rounded.SentimentSatisfiedAlt
    AiTool.CASUAL -> Icons.Rounded.LocalCafe
    AiTool.CONFIDENT -> Icons.Rounded.Bolt
    AiTool.FUNNY -> Icons.Rounded.TheaterComedy
    AiTool.ACADEMIC -> Icons.Rounded.School
    AiTool.POETIC -> Icons.Rounded.AutoStories
    AiTool.EMOJIFY -> Icons.Rounded.EmojiEmotions
    AiTool.TRANSLATE -> Icons.Rounded.Translate
    AiTool.CONTINUE -> Icons.Rounded.EditNote
    AiTool.ENHANCE_PROMPT -> Icons.Rounded.AutoAwesome
    AiTool.REPLY -> Icons.Rounded.Quickreply
    AiTool.CUSTOM -> Icons.Rounded.Tune
}

private fun String.wordCount(): Int = trim().split(Regex("\\s+")).count { it.isNotBlank() }

/**
 * The keyboard's AI panel ("Writing tools"). One tap on a tool rewrites the
 * selection, or the whole message when nothing is selected; the result can
 * be refined, copied or applied, and applying can be undone. Inside AI chat
 * apps a prompt-enhancer banner leads the grid. Colors follow the active
 * keyboard theme.
 */
@Composable
fun AiInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()
    val feedback = LocalInputFeedbackController.current
    val scope = rememberCoroutineScope()

    val packageName = editorInstance.activeInfo.packageName
    val isInAiApp = remember(packageName) { PromptEnhancerDetector.isAiApp(packageName) }
    val aiAppName = remember(packageName) { PromptEnhancerDetector.getAiAppName(packageName) }

    // ── Theme ──
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val smartbarStyle = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val bgColor = windowStyle.background()
    val tileColor = keyStyle.background()
    val onSurface = keyStyle.foreground()
    val accent = smartbarStyle.foreground().takeIf { it.alpha > 0f } ?: onSurface
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val muted = onSurface.copy(alpha = 0.62f)
    val outline = onSurface.copy(alpha = 0.12f)
    val errorColor = Color(0xFFE5484D)

    // ── State ──
    val prefs = remember { context.getSharedPreferences(PANEL_PREFS, Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf<PanelState>(PanelState.Tools) }
    var source by remember { mutableStateOf<AiSource?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var language by remember { mutableStateOf(prefs.getString(KEY_LANGUAGE, null)) }
    var accountVersion by remember { mutableIntStateOf(0) }
    val content by editorInstance.activeContentFlow.collectAsState()
    val account = remember(accountVersion) {
        val kb = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        AiAccount(
            loggedIn = kb.getBoolean("user_logged_in", false),
            pro = kb.getBoolean("user_is_pro", false),
            credits = kb.getInt("user_credits", 0),
            ownKey = aiManager.usesOwnApiKey(),
        )
    }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    fun openApp() {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            }
        }
    }

    fun captureSource(): Result<AiSource> {
        val snapshot = editorInstance.snapshotForAi()
            ?: return Result.failure(AIException("This text field doesn't allow edits from the keyboard", AIException.Kind.OTHER))
        if (snapshot.hasSelection) {
            return Result.success(AiSource(snapshot, snapshot.selectedText, snapshot.selectionStart, snapshot.selectionEnd))
        }
        if (snapshot.text.isBlank()) {
            return Result.failure(AIException("Type or select some text first", AIException.Kind.NO_TEXT))
        }
        if (!snapshot.complete) {
            return Result.failure(
                AIException("This message is too long to edit as a whole. Select the part you want to change.", AIException.Kind.TOO_LONG),
            )
        }
        return Result.success(AiSource(snapshot, snapshot.text, 0, snapshot.text.length))
    }

    /** Runs [tool] on the field, or on [refineInput] (the current result) to refine it. */
    fun run(tool: AiTool, refineInput: String? = null) {
        val src = if (refineInput == null) {
            captureSource().getOrElse { e ->
                state = PanelState.Failed(tool, e.message.orEmpty(), (e as? AIException)?.kind ?: AIException.Kind.OTHER, null)
                return
            }.also { source = it }
        } else {
            source ?: return
        }
        val input = refineInput ?: src.text
        job?.cancel()
        state = PanelState.Working(tool)
        job = scope.launch {
            val options = AiOptions(targetLanguage = language, appName = aiAppName)
            val result = aiManager.runTool(tool, input, options)
            accountVersion++
            state = result.fold(
                onSuccess = { PanelState.Done(tool, it) },
                onFailure = { e ->
                    PanelState.Failed(
                        tool,
                        e.message ?: "Something went wrong",
                        (e as? AIException)?.kind ?: AIException.Kind.OTHER,
                        refineInput,
                    )
                },
            )
        }
    }

    /** Where [src] is in the field now, if it's still there. */
    fun locate(src: AiSource, now: AiTextSnapshot): Pair<Int, Int>? {
        if (now.text == src.snapshot.text) return src.start to src.end
        val index = now.text.indexOf(src.text)
        if (index >= 0 && now.text.indexOf(src.text, index + 1) < 0) return index to index + src.text.length
        return null
    }

    fun apply(tool: AiTool, result: String) {
        val src = source ?: return
        val now = editorInstance.snapshotForAi()
        val range = now?.let { locate(src, it) }
        if (now == null || range == null) {
            state = PanelState.Failed(tool, "Your text changed while the AI was working. Run the tool again.", AIException.Kind.OTHER, null)
            return
        }
        val edit = if (tool.insertsAfter) {
            val before = now.text.substring(0, range.second)
            val addition = result.trimStart()
            val joiner = when {
                before.isEmpty() || before.last().isWhitespace() -> ""
                addition.firstOrNull() in listOf(',', '.', ';', ':', '!', '?', ')') -> ""
                else -> " "
            }
            AppliedEdit(range.second, joiner + addition, "")
        } else {
            AppliedEdit(range.first, result, now.text.substring(range.first, range.second))
        }
        val ok = editorInstance.replaceRangeForAi(edit.start, edit.start + edit.original.length, edit.inserted)
        state = if (ok) {
            PanelState.Applied(tool, edit)
        } else {
            PanelState.Failed(tool, "This app didn't accept the change. Copy the result instead.", AIException.Kind.OTHER, null)
        }
    }

    fun undo(edit: AppliedEdit) {
        val now = editorInstance.snapshotForAi()
        if (now != null && now.text.regionMatches(edit.start, edit.inserted, 0, edit.inserted.length)) {
            editorInstance.replaceRangeForAi(edit.start, edit.start + edit.inserted.length, edit.original)
            source = null
            state = PanelState.Tools
        } else {
            Toast.makeText(context, "Can't undo: the text has changed", Toast.LENGTH_SHORT).show()
        }
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Rewordium AI", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun backToTools() {
        job?.cancel()
        state = PanelState.Tools
    }

    val panelHeight = FlorisImeSizing.keyboardRowBaseHeight * 5 + FlorisImeSizing.smartbarHeight

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(panelHeight)
                .background(bgColor),
        ) {
            // ════════ Header ════════
            PanelHeader(
                state = state,
                isInAiApp = isInAiApp,
                selectedText = content.selectedText,
                fieldEmpty = content.text.isBlank(),
                account = account,
                fg = onSurface,
                muted = muted,
                accent = accent,
                outline = outline,
                errorColor = errorColor,
                onBack = { feedback.keyPress(); backToTools() },
                onAccountClick = { openApp() },
            )

            // ════════ Body ════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "ai_panel_body",
                ) { current ->
                    when (current) {
                        PanelState.Tools -> ToolGrid(
                            isInAiApp = isInAiApp,
                            aiAppName = aiAppName,
                            tileColor = tileColor,
                            fg = onSurface,
                            accent = accent,
                            onTool = { tool ->
                                feedback.keyPress()
                                if (tool == AiTool.TRANSLATE) state = PanelState.Languages else run(tool)
                            },
                        )
                        PanelState.Languages -> LanguageGrid(
                            selected = language,
                            tileColor = tileColor,
                            fg = onSurface,
                            accent = accent,
                            onPick = { picked ->
                                feedback.keyPress()
                                language = picked
                                prefs.edit().putString(KEY_LANGUAGE, picked).apply()
                                run(AiTool.TRANSLATE)
                            },
                        )
                        is PanelState.Working -> WorkingView(
                            tool = current.tool,
                            sourcePreview = source?.text.orEmpty(),
                            fg = onSurface,
                            muted = muted,
                            accent = accent,
                            outline = outline,
                        )
                        is PanelState.Done -> ResultView(
                            tool = current.tool,
                            text = current.text,
                            sourceWords = source?.text?.wordCount() ?: 0,
                            tileColor = tileColor,
                            fg = onSurface,
                            muted = muted,
                            accent = accent,
                            outline = outline,
                            onCopy = { feedback.keyPress(); copy(current.text) },
                            onRetry = { feedback.keyPress(); run(current.tool) },
                            onRefine = { tool -> feedback.keyPress(); run(tool, refineInput = current.text) },
                        )
                        is PanelState.Failed -> FailedView(
                            message = current.message,
                            kind = current.kind,
                            fg = onSurface,
                            muted = muted,
                            accent = accent,
                            outline = outline,
                            errorColor = errorColor,
                            onRetry = { feedback.keyPress(); run(current.tool, current.retryInput) },
                            onOpenApp = { feedback.keyPress(); openApp() },
                        )
                        is PanelState.Applied -> AppliedView(
                            tool = current.tool,
                            fg = onSurface,
                            muted = muted,
                            accent = accent,
                            outline = outline,
                            onUndo = { feedback.keyPress(); undo(current.edit) },
                        )
                    }
                }
            }

            // ════════ Bottom row: ABC | primary | Backspace ════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.keyboardRowBaseHeight)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyboardLikeButton(
                    inputEventDispatcher = keyboardManager.inputEventDispatcher,
                    keyData = TextKeyData.IME_UI_MODE_TEXT,
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.84f),
                ) {
                    Text("ABC", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }

                val primaryModifier = Modifier
                    .weight(2.8f)
                    .fillMaxHeight(0.84f)
                when (val current = state) {
                    PanelState.Tools -> {
                        val quick = if (isInAiApp) AiTool.ENHANCE_PROMPT else AiTool.PROOFREAD
                        PrimaryButton(quick.icon(), quick.label, accent, onAccent, primaryModifier) {
                            feedback.keyPress()
                            run(quick)
                        }
                    }
                    PanelState.Languages -> SecondaryButton("Back", outline, onSurface, primaryModifier) {
                        feedback.keyPress()
                        backToTools()
                    }
                    is PanelState.Working -> SecondaryButton("Cancel", outline, onSurface, primaryModifier) {
                        feedback.keyPress()
                        backToTools()
                    }
                    is PanelState.Done -> {
                        val label = when (current.tool) {
                            AiTool.CONTINUE -> "Insert"
                            AiTool.ENHANCE_PROMPT -> "Use prompt"
                            else -> "Replace"
                        }
                        PrimaryButton(Icons.Rounded.Check, label, accent, onAccent, primaryModifier) {
                            feedback.keyPress()
                            apply(current.tool, current.text)
                        }
                    }
                    is PanelState.Failed -> SecondaryButton("Back to tools", outline, onSurface, primaryModifier) {
                        feedback.keyPress()
                        backToTools()
                    }
                    is PanelState.Applied -> PrimaryButton(Icons.Rounded.Check, "Done", accent, onAccent, primaryModifier) {
                        feedback.keyPress()
                        source = null
                        state = PanelState.Tools
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    }
                }

                KeyboardLikeButton(
                    inputEventDispatcher = keyboardManager.inputEventDispatcher,
                    keyData = TextKeyData.DELETE,
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.84f),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Header
// ──────────────────────────────────────────────

@Composable
private fun PanelHeader(
    state: PanelState,
    isInAiApp: Boolean,
    selectedText: String,
    fieldEmpty: Boolean,
    account: AiAccount,
    fg: Color,
    muted: Color,
    accent: Color,
    outline: Color,
    errorColor: Color,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
) {
    val title = when (state) {
        PanelState.Tools -> if (isInAiApp) "Prompt tools" else "Writing tools"
        PanelState.Languages -> "Translate to"
        is PanelState.Working -> state.tool.label
        is PanelState.Done -> state.tool.label
        is PanelState.Failed -> state.tool.label
        is PanelState.Applied -> state.tool.label
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state == PanelState.Tools) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = fg, modifier = Modifier.size(20.dp))
            }
        }
        Text(title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (state == PanelState.Tools || state == PanelState.Languages) {
            val sourceLabel = when {
                selectedText.isNotBlank() -> "Selection · ${selectedText.wordCount()} words"
                fieldEmpty -> "Empty field"
                else -> "Whole message"
            }
            Pill(sourceLabel, muted, outline)
        }
        when {
            account.ownKey -> Unit
            !account.loggedIn -> Pill("Log in", errorColor, errorColor.copy(alpha = 0.4f), onClick = onAccountClick)
            account.pro -> Pill("PRO", accent, accent.copy(alpha = 0.4f))
            else -> {
                val color = if (account.credits > 0) muted else errorColor
                Pill(if (account.credits == 1) "1 credit" else "${account.credits} credits", color, color.copy(alpha = 0.35f), onClick = onAccountClick)
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, border: Color, onClick: (() -> Unit)? = null) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 150.dp)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

// ──────────────────────────────────────────────
// Tools
// ──────────────────────────────────────────────

@Composable
private fun ToolGrid(
    isInAiApp: Boolean,
    aiAppName: String?,
    tileColor: Color,
    fg: Color,
    accent: Color,
    onTool: (AiTool) -> Unit,
) {
    val rows = if (isInAiApp) TOOL_GRID.take(3) else TOOL_GRID
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isInAiApp) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTool(AiTool.ENHANCE_PROMPT) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (aiAppName != null) "Enhance prompt for $aiAppName" else "Enhance prompt",
                        color = fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { tool ->
                    ToolTile(
                        tool = tool,
                        tileColor = tileColor,
                        fg = fg,
                        accent = accent,
                        onClick = { onTool(tool) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolTile(
    tool: AiTool,
    tileColor: Color,
    fg: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = tileColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(tool.icon(), null, tint = accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                tool.label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageGrid(
    selected: String?,
    tileColor: Color,
    fg: Color,
    accent: Color,
    onPick: (String) -> Unit,
) {
    val ordered = remember(selected) {
        if (selected != null && selected in LANGUAGES) listOf(selected) + (LANGUAGES - selected) else LANGUAGES
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(ordered) { lang ->
            val isSelected = lang == selected
            Surface(
                color = if (isSelected) accent.copy(alpha = 0.16f) else tileColor,
                shape = RoundedCornerShape(12.dp),
                border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.5f)) else null,
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(lang) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        lang,
                        color = if (isSelected) accent else fg,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Working / result / error / applied
// ──────────────────────────────────────────────

@Composable
private fun WorkingView(
    tool: AiTool,
    sourcePreview: String,
    fg: Color,
    muted: Color,
    accent: Color,
    outline: Color,
) {
    val transition = rememberInfiniteTransition(label = "ai_working")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "ai_pulse",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            tool.icon(),
            null,
            tint = accent,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    alpha = pulse
                    scaleX = 0.9f + pulse * 0.1f
                    scaleY = 0.9f + pulse * 0.1f
                },
        )
        Spacer(Modifier.height(10.dp))
        Text("${tool.progressLabel}…", color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = outline,
        )
        if (sourcePreview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                sourcePreview,
                color = muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultView(
    tool: AiTool,
    text: String,
    sourceWords: Int,
    tileColor: Color,
    fg: Color,
    muted: Color,
    accent: Color,
    outline: Color,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onRefine: (AiTool) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = tileColor,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, outline),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val words = text.wordCount()
                    Text(
                        if (tool.insertsAfter || sourceWords == 0) "$words words" else "$sourceWords → $words words",
                        color = muted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    SmallIconButton(Icons.Rounded.ContentCopy, "Copy", muted, onCopy)
                    SmallIconButton(Icons.Rounded.Refresh, "Try again", muted, onRetry)
                }
                Text(
                    text,
                    color = fg,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
        if (tool != AiTool.ENHANCE_PROMPT && tool != AiTool.TRANSLATE) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(REFINE_TOOLS.filter { it != tool }) { refine ->
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, outline),
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onRefine(refine) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(refine.icon(), null, tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(refine.label, color = fg, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedView(
    message: String,
    kind: AIException.Kind,
    fg: Color,
    muted: Color,
    accent: Color,
    outline: Color,
    errorColor: Color,
    onRetry: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val needsApp = kind == AIException.Kind.LOGIN || kind == AIException.Kind.CREDITS || kind == AIException.Kind.NO_KEY
    val canRetry = kind == AIException.Kind.NETWORK || kind == AIException.Kind.RATE_LIMIT ||
        kind == AIException.Kind.SERVICE || kind == AIException.Kind.EMPTY || kind == AIException.Kind.OTHER
    val isHint = kind == AIException.Kind.NO_TEXT || kind == AIException.Kind.TOO_LONG
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isHint) Icons.Rounded.EditNote else Icons.Rounded.ErrorOutline,
            null,
            tint = if (isHint) muted else errorColor,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(message, color = fg, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (needsApp) {
                val label = when (kind) {
                    AIException.Kind.CREDITS -> "Get more credits"
                    AIException.Kind.LOGIN -> "Log in"
                    else -> "Open settings"
                }
                ChipButton(label, accent, accent.copy(alpha = 0.16f), accent.copy(alpha = 0.4f), onOpenApp)
            }
            if (canRetry) {
                ChipButton("Try again", fg, Color.Transparent, outline, onRetry)
            }
        }
    }
}

@Composable
private fun AppliedView(
    tool: AiTool,
    fg: Color,
    muted: Color,
    accent: Color,
    outline: Color,
    onUndo: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (tool.insertsAfter) "Added to your text" else "Text updated",
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text("Not what you wanted? You can undo it.", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, outline),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onUndo),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Undo", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ──────────────────────────────────────────────
// Buttons
// ──────────────────────────────────────────────

@Composable
private fun PrimaryButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onAccent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = accent,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    outline: Color,
    fg: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, outline),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun ChipButton(label: String, fg: Color, bg: Color, border: Color, onClick: () -> Unit) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(16.dp))
    }
}
