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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.noxquill.rewordium.keyboard.ime.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.ui.avatar.BOT_TYPES
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.keyboard.aiManager
import com.noxquill.rewordium.keyboard.app.FlorisPreferenceStore
import com.noxquill.rewordium.keyboard.editorInstance
import com.noxquill.rewordium.keyboard.ime.ImeUiMode
import com.noxquill.rewordium.keyboard.ime.editor.AiTextSnapshot
import com.noxquill.rewordium.keyboard.ime.keyboard.FlorisImeSizing
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaAbcButton
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaChip
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaExpressiveTheme
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaShapes
import com.noxquill.rewordium.keyboard.ime.media.ui.MediaTile
import com.noxquill.rewordium.keyboard.ime.media.ui.MorphBlob
import com.noxquill.rewordium.keyboard.ime.media.ui.RepeatingDeleteButton
import com.noxquill.rewordium.keyboard.ime.media.ui.SpinningShape
import com.noxquill.rewordium.keyboard.ime.media.ui.Symbols
import com.noxquill.rewordium.keyboard.ime.media.ui.Tone
import com.noxquill.rewordium.keyboard.ime.media.ui.at
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberMediaFeedback
import com.noxquill.rewordium.keyboard.ime.media.ui.rememberTones
import com.noxquill.rewordium.keyboard.ime.text.keyboard.TextKeyData
import com.noxquill.rewordium.keyboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    AiTool.PROOFREAD -> Symbols.Spellcheck
    AiTool.REWRITE -> Symbols.AutoFixHigh
    AiTool.SHORTEN -> Symbols.ShortText
    AiTool.EXPAND -> Symbols.Notes
    AiTool.SUMMARIZE -> Symbols.Summarize
    AiTool.BULLETS -> Symbols.FormatListBulleted
    AiTool.PROFESSIONAL -> Symbols.BusinessCenter
    AiTool.FRIENDLY -> Symbols.SentimentSatisfied
    AiTool.CASUAL -> Symbols.LocalCafe
    AiTool.CONFIDENT -> Symbols.Bolt
    AiTool.FUNNY -> Symbols.TheaterComedy
    AiTool.ACADEMIC -> Symbols.School
    AiTool.POETIC -> Symbols.AutoStories
    AiTool.EMOJIFY -> Symbols.EmojiEmotions
    AiTool.TRANSLATE -> Symbols.Translate
    AiTool.CONTINUE -> Symbols.EditNote
    AiTool.ENHANCE_PROMPT -> Symbols.AutoAwesome
    AiTool.REPLY -> Symbols.Quickreply
    AiTool.CUSTOM -> Symbols.Tune
}

private fun String.wordCount(): Int = trim().split(Regex("\\s+")).count { it.isNotBlank() }

/** How the mascot feels about each screen. */
private fun PanelState.mood(): BotMood = when (this) {
    PanelState.Tools -> BotMood.Idle
    PanelState.Languages -> BotMood.Thinking
    is PanelState.Working -> BotMood.Working
    is PanelState.Done -> BotMood.Happy
    is PanelState.Failed -> if (kind == AIException.Kind.NO_TEXT || kind == AIException.Kind.TOO_LONG) BotMood.NeedsYou else BotMood.Sad
    is PanelState.Applied -> BotMood.Happy
}

/**
 * The keyboard's AI panel ("Writing tools"), on the same Material 3 Expressive kit as the media panels and
 * coloured by the keyboard theme. The keyboard's mascot sits in the header and reacts: it thinks while a
 * tool runs, hops when a result lands, and looks sad when something fails. One tap on a tool rewrites the
 * selection, or the whole message when nothing is selected; the result can be refined, copied or applied,
 * and applying can be undone. Inside AI chat apps a prompt-enhancer card leads the grid.
 */
@Composable
fun AiInputLayout(
    modifier: Modifier = Modifier,
) = MediaExpressiveTheme {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val aiManager by context.aiManager()
    val feedback = rememberMediaFeedback()
    val scope = rememberCoroutineScope()

    val packageName = editorInstance.activeInfo.packageName
    val isInAiApp = remember(packageName) { PromptEnhancerDetector.isAiApp(packageName) }
    val aiAppName = remember(packageName) { PromptEnhancerDetector.getAiAppName(packageName) }

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
            val keyboardPrefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
            val options = AiOptions(
                targetLanguage = language,
                appName = aiAppName,
                instruction = keyboardPrefs.getString("active_persona_description", null),
            )
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
    // The mascot hops whenever a result or an applied edit lands.
    var hops by remember { mutableIntStateOf(0) }
    LaunchedEffect(state::class) {
        if (state is PanelState.Done || state is PanelState.Applied) hops++
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(panelHeight)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            PanelHeader(
                state = state,
                isInAiApp = isInAiApp,
                selectedText = content.selectedText,
                fieldEmpty = content.text.isBlank(),
                account = account,
                hops = hops,
                onBack = { feedback.tick(); backToTools() },
                onAccountClick = { feedback.tap(); openApp() },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = {
                        // Forward through the flow slides in from the end, back to the tools from the start.
                        val back = targetState == PanelState.Tools
                        (slideInHorizontally(motion.defaultSpatialSpec()) { if (back) -it / 5 else it / 5 } +
                            fadeIn(motion.defaultEffectsSpec()) +
                            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                            (slideOutHorizontally(motion.fastSpatialSpec()) { if (back) it / 6 else -it / 6 } +
                                fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "ai_panel_body",
                ) { current ->
                    when (current) {
                        PanelState.Tools -> ToolGrid(
                            isInAiApp = isInAiApp,
                            aiAppName = aiAppName,
                            onTool = { tool ->
                                feedback.tap()
                                if (tool == AiTool.TRANSLATE) state = PanelState.Languages else run(tool)
                            },
                        )
                        PanelState.Languages -> LanguageGrid(
                            selected = language,
                            onPick = { picked ->
                                feedback.tap()
                                language = picked
                                prefs.edit().putString(KEY_LANGUAGE, picked).apply()
                                run(AiTool.TRANSLATE)
                            },
                        )
                        is PanelState.Working -> WorkingView(tool = current.tool, sourcePreview = source?.text.orEmpty())
                        is PanelState.Done -> ResultView(
                            tool = current.tool,
                            text = current.text,
                            sourceWords = source?.text?.wordCount() ?: 0,
                            onCopy = { feedback.tick(); copy(current.text) },
                            onRetry = { feedback.tick(); run(current.tool) },
                            onRefine = { tool -> feedback.tap(); run(tool, refineInput = current.text) },
                            onReport = { feedback.tick(); AiContentReports.report(context, "keyboard", current.tool.name, current.text) },
                        )
                        is PanelState.Failed -> FailedView(
                            message = current.message,
                            kind = current.kind,
                            onRetry = { feedback.tap(); run(current.tool, current.retryInput) },
                            onOpenApp = { feedback.tap(); openApp() },
                        )
                        is PanelState.Applied -> AppliedView(
                            tool = current.tool,
                            onUndo = { feedback.tap(); undo(current.edit) },
                        )
                    }
                }
            }

            // ABC | the screen's main action | delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.keyboardRowBaseHeight)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaAbcButton(
                    onClick = {
                        feedback.tap()
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                val primaryModifier = Modifier.weight(2.8f).fillMaxHeight()
                when (val current = state) {
                    PanelState.Tools -> {
                        val quick = if (isInAiApp) AiTool.ENHANCE_PROMPT else AiTool.PROOFREAD
                        PrimaryButton(quick.icon(), quick.label, primaryModifier) {
                            feedback.tap()
                            run(quick)
                        }
                    }
                    PanelState.Languages -> SecondaryButton(Symbols.ArrowBack, "Back", primaryModifier) {
                        feedback.tick()
                        backToTools()
                    }
                    is PanelState.Working -> SecondaryButton(Symbols.Close, "Cancel", primaryModifier) {
                        feedback.tick()
                        backToTools()
                    }
                    is PanelState.Done -> {
                        val label = when (current.tool) {
                            AiTool.CONTINUE -> "Insert"
                            AiTool.ENHANCE_PROMPT -> "Use prompt"
                            else -> "Replace"
                        }
                        PrimaryButton(Symbols.Check, label, primaryModifier) {
                            feedback.tap()
                            apply(current.tool, current.text)
                        }
                    }
                    is PanelState.Failed -> SecondaryButton(Symbols.ArrowBack, "Back to tools", primaryModifier) {
                        feedback.tick()
                        backToTools()
                    }
                    is PanelState.Applied -> PrimaryButton(Symbols.Check, "Done", primaryModifier) {
                        feedback.tap()
                        source = null
                        state = PanelState.Tools
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    }
                }
                RepeatingDeleteButton(
                    onDelete = {
                        feedback.delete()
                        keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
                    },
                    onRepeat = {
                        feedback.deleteRepeat()
                        keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

// ── Mascot ───────────────────────────────────────────────────────────────────

/** The keyboard's mascot (the theme shop's pick, a star by default), wearing [mood]. */
@Composable
private fun PanelMascot(mood: BotMood, size: Dp, hops: Int, modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val chosen by prefs.theme.mascot.observeAsState()
    BotAvatar(
        type = chosen.takeIf { it in BOT_TYPES } ?: "star",
        mood = mood,
        size = size,
        modifier = modifier,
        // Alive (breathing, blinking) only while it has something to show; still otherwise, to save frames.
        still = mood == BotMood.Idle || mood == BotMood.Sleeping,
        hopSignal = hops,
        label = "Jade",
    )
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun PanelHeader(
    state: PanelState,
    isInAiApp: Boolean,
    selectedText: String,
    fieldEmpty: Boolean,
    account: AiAccount,
    hops: Int,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
) {
    val tones = rememberTones()
    val title = when (state) {
        PanelState.Tools -> if (isInAiApp) "Prompt tools" else "Writing tools"
        PanelState.Languages -> "Translate to"
        is PanelState.Working -> state.tool.label
        is PanelState.Done -> state.tool.label
        is PanelState.Failed -> state.tool.label
        is PanelState.Applied -> state.tool.label
    }
    // Each screen has its own shape behind the mascot; moving between screens morphs it.
    val stageShape = when (state) {
        PanelState.Tools -> MaterialShapes.Cookie9Sided
        PanelState.Languages -> MaterialShapes.Clover4Leaf
        is PanelState.Working -> MaterialShapes.SoftBurst
        is PanelState.Done -> MaterialShapes.Sunny
        is PanelState.Failed -> MaterialShapes.Cookie6Sided
        is PanelState.Applied -> MaterialShapes.Flower
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state != PanelState.Tools) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                Icon(Symbols.ArrowBack, "Back", modifier = Modifier.size(22.dp))
            }
        }
        MorphBlob(stageShape, MaterialTheme.colorScheme.primaryContainer, Modifier.size(44.dp)) {
            PanelMascot(state.mood(), 32.dp, hops)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state == PanelState.Tools || state == PanelState.Languages) {
            val sourceLabel = when {
                selectedText.isNotBlank() -> "${selectedText.wordCount()} words"
                fieldEmpty -> "Empty"
                else -> "Message"
            }
            StatusPill(sourceLabel, if (selectedText.isNotBlank()) Symbols.FormatListBulleted else Symbols.Notes, tones.at(1))
        }
        when {
            account.ownKey -> Unit
            !account.loggedIn -> StatusPill("Log in", Symbols.Lock, errorTone(), onAccountClick)
            account.pro -> StatusPill("PRO", Symbols.StarShine, tones.at(0))
            else -> StatusPill(
                if (account.credits == 1) "1 credit" else "${account.credits}",
                Symbols.Bolt,
                if (account.credits > 0) tones.at(2) else errorTone(),
                onAccountClick,
            )
        }
    }
}

@Composable
private fun errorTone() = Tone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)

@Composable
private fun StatusPill(text: String, icon: ImageVector, tone: Tone, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = CircleShape,
        color = tone.container,
        contentColor = tone.content,
    ) {
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 110.dp),
            )
        }
    }
}

// ── Tools ────────────────────────────────────────────────────────────────────

@Composable
private fun ToolGrid(isInAiApp: Boolean, aiAppName: String?, onTool: (AiTool) -> Unit) {
    val tones = rememberTones()
    val rows = if (isInAiApp) TOOL_GRID.take(3) else TOOL_GRID
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isInAiApp) {
            MediaTile(
                onClick = { onTool(AiTool.ENHANCE_PROMPT) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.primary,
                corner = 20.dp,
                pressedCorner = 30.dp,
                label = "Enhance prompt",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphBlob(MaterialShapes.SoftBurst, MaterialTheme.colorScheme.onPrimary, Modifier.size(30.dp)) {
                        Icon(Symbols.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (aiAppName != null) "Enhance prompt for $aiAppName" else "Enhance prompt",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            // Each row is a family (fix, tone of voice, shape, play) and wears its own tone.
            val tone = tones.at(rowIndex)
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEachIndexed { column, tool ->
                    ToolTile(
                        tool = tool,
                        tone = tone,
                        shapeSlot = rowIndex * 4 + column,
                        onClick = { onTool(tool) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: AiTool, tone: Tone, shapeSlot: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaTile(
        onClick = onClick,
        modifier = modifier,
        color = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tone.container, 0.5f),
        corner = 18.dp,
        pressedCorner = 30.dp,
        label = tool.label,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(tone.content, MediaShapes.blob(shapeSlot).toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tool.icon(), null, tint = tone.container, modifier = Modifier.size(15.dp))
            }
            Text(
                tool.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageGrid(selected: String?, onPick: (String) -> Unit) {
    val tones = rememberTones()
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
        itemsIndexed(ordered, key = { _, lang -> lang }) { index, lang ->
            val isSelected = lang == selected
            MediaTile(
                onClick = { onPick(lang) },
                modifier = Modifier.height(42.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tones.at(index).container, 0.4f)
                },
                corner = 21.dp,
                pressedCorner = 12.dp,
                label = lang,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Symbols.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        lang,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ── Working / result / error / applied ───────────────────────────────────────

/** While a tool runs: its symbol on a shape that keeps morphing, a wavy progress line and the text it's reading. */
@Composable
private fun WorkingView(tool: AiTool, sourcePreview: String) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(650)
            step++
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MorphBlob(MediaShapes.blob(step), MaterialTheme.colorScheme.primaryContainer, Modifier.size(72.dp), turnPerChange = 60f) {
            Icon(tool.icon(), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("${tool.progressLabel}…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        LinearWavyProgressIndicator(Modifier.fillMaxWidth(0.5f))
        if (sourcePreview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                sourcePreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onRefine: (AiTool) -> Unit,
    onReport: () -> Unit,
) {
    val tones = rememberTones()
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialShapes.Sunny.toShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tool.icon(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    val words = text.wordCount()
                    Text(
                        if (tool.insertsAfter || sourceWords == 0) "$words words" else "$sourceWords → $words words",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    CardIconButton(Symbols.ContentCopy, "Copy", onCopy)
                    CardIconButton(Symbols.Refresh, "Try again", onRetry)
                    CardIconButton(Symbols.Flag, "Report this result", onReport)
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                )
            }
        }
        if (tool != AiTool.ENHANCE_PROMPT && tool != AiTool.TRANSLATE) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(REFINE_TOOLS.filter { it != tool }, key = { _, t -> t.name }) { index, refine ->
                    MediaChip(
                        selected = false,
                        onClick = { onRefine(refine) },
                        label = refine.label,
                        icon = refine.icon(),
                        tone = tones.at(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(38.dp)) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun FailedView(
    message: String,
    kind: AIException.Kind,
    onRetry: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val needsApp = kind == AIException.Kind.LOGIN || kind == AIException.Kind.CREDITS || kind == AIException.Kind.NO_KEY
    val canRetry = kind == AIException.Kind.NETWORK || kind == AIException.Kind.RATE_LIMIT ||
        kind == AIException.Kind.SERVICE || kind == AIException.Kind.EMPTY || kind == AIException.Kind.OTHER
    val isHint = kind == AIException.Kind.NO_TEXT || kind == AIException.Kind.TOO_LONG
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpinningShape(
            if (isHint) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie6Sided,
            if (isHint) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
            Modifier.size(60.dp),
        ) {
            Icon(
                if (isHint) Symbols.EditNote else Symbols.Error,
                null,
                tint = if (isHint) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (needsApp) {
                val label = when (kind) {
                    AIException.Kind.CREDITS -> "Get more credits"
                    AIException.Kind.LOGIN -> "Log in"
                    else -> "Open settings"
                }
                Button(onClick = onOpenApp, shapes = ButtonDefaults.shapes()) { Text(label) }
            }
            if (canRetry) {
                OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                    Icon(Symbols.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun AppliedView(tool: AiTool, onUndo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpinningShape(MaterialShapes.Sunny, MaterialTheme.colorScheme.tertiaryContainer, Modifier.size(64.dp)) {
            Icon(Symbols.CheckCircleFilled, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (tool.insertsAfter) "Added to your text" else "Text updated",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Not what you wanted? You can undo it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onUndo, shapes = ButtonDefaults.shapes()) {
            Icon(Symbols.Undo, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Undo")
        }
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────

/** The screen's main action: a filled expressive button that squares up when pressed. */
@Composable
private fun PrimaryButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SecondaryButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
