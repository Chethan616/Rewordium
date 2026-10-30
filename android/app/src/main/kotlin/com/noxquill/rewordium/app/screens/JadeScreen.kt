package com.noxquill.rewordium.app.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import com.noxquill.rewordium.app.jade.JadeController
import com.noxquill.rewordium.app.jade.jadeSkill
import com.noxquill.rewordium.app.jade.jadeSkills
import com.noxquill.rewordium.app.ui.AvatarBadge
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.container
import com.noxquill.rewordium.app.ui.shape
import com.noxquill.rewordium.app.ui.BackButton
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.MorphingBackdrop
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.ToneCard
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.rememberMarkdown
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.app.ui.toneAt
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.avatar.LocalBotFlight
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.DocumentText
import com.noxquill.rewordium.data.jade.JadeChatSummary
import com.noxquill.rewordium.data.jade.JadeItem
import com.noxquill.rewordium.data.jade.JadeSkills
import android.text.format.DateUtils
import kotlinx.coroutines.launch

private data class Starter(val text: String, val icon: ImageVector, val settings: Boolean)

private val starters = listOf(
    Starter("Make my keyboard a little taller", Icons.Filled.Height, true),
    Starter("Switch my keyboard to a dark theme", Icons.Filled.DarkMode, true),
    Starter("Turn off key press sounds", Icons.AutoMirrored.Filled.VolumeOff, true),
    Starter("Show the number row", Icons.Filled.Pin, true),
    Starter("Make vibration a bit stronger", Icons.Filled.Vibration, true),
    Starter("Hide the glide typing trail", Icons.Filled.Gesture, true),
    Starter("Help me write a polite follow-up email", Icons.Filled.EditNote, false),
    Starter("Give me 3 catchy titles for my post", Icons.Filled.Lightbulb, false),
)

private val reportReasons = listOf(
    "Offensive or hateful",
    "Sexual or violent",
    "Harmful or dangerous",
    "False or misleading",
    "Something else",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JadeScreen(
    controller: JadeController,
    userAvatar: String,
    userName: String?,
    isPro: Boolean,
    onBack: () -> Unit,
    onOpenKeyboardScreen: (String?) -> Unit,
    onUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val flight = LocalBotFlight.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var reportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf(false) }
    val welcomeScroll = rememberScrollState()
    val hero = remember { HeroFlight() }
    hero.scroll = { welcomeScroll.value }
    val showWelcome = controller.items.isEmpty() && !controller.working
    val handoff = remember { Animatable(1f) }

    // Sending the first message: Jade finishes her trip up to the top bar instead of popping there.
    LaunchedEffect(showWelcome) {
        if (showWelcome) {
            handoff.snapTo(1f)
            welcomeScroll.scrollTo(0)
            return@LaunchedEffect
        }
        val from = hero.rectFor(welcomeScroll.value) ?: return@LaunchedEffect
        if (from == hero.bar) return@LaunchedEffect
        hero.handoffFrom = from
        handoff.snapTo(0f)
        handoff.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    val heroVisible = hero.ready && (showWelcome || handoff.isRunning)

    LaunchedEffect(controller.items.size, controller.working) {
        val count = controller.items.size + if (controller.working) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(controller.notice) {
        controller.notice?.let {
            controller.notice = null
            snackbar.showSnackbar(it)
        }
    }

    val attach = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            attaching = true
            runCatching { DocumentText.read(context, uri) }
                .onSuccess { text ->
                    controller.draft = text
                    if (controller.skill == JadeSkills.CHAT) controller.skill = "polish"
                    controller.notice = "Document added — pick what Jade should do, then send"
                }
                .onFailure { controller.notice = it.message ?: "Couldn’t read that document." }
            attaching = false
        }
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Jade", text))
        haptics.confirm()
        controller.notice = "Copied"
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share Jade’s reply"))
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned {
            flight?.chatScreen = it
            hero.placeScreen(it)
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .onGloballyPositioned {
                            hero.placeBar(it)
                            if (!showWelcome) flight?.chatBotPlaced(it, "cloud", controller.mood)
                        }
                        // While the welcome bot is on its way here, it stands in for this one.
                        .graphicsLayer { alpha = if (heroVisible || flight?.hidesChatBot(JADE_FLIGHT) == true) 0f else 1f },
                ) {
                    BotAvatar(type = "cloud", mood = controller.mood, size = 48.dp, label = "Jade")
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Jade", style = Heading.Rounded.style(MaterialTheme.typography.headlineSmall))
                    AnimatedContent(
                        targetState = controller.status ?: if (controller.canUseTools) "Writing buddy · keyboard tuner" else "Your writing buddy",
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "jade-status",
                    ) { status ->
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { haptics.click(); showHistory = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Filled.History, "Chat history")
                }
                IconButton(
                    onClick = { haptics.click(); controller.newChat() },
                    enabled = controller.items.isNotEmpty(),
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Filled.AddComment, "New chat") }
            }

            // ── Conversation ─────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showWelcome) {
                    JadeWelcome(
                        name = userName,
                        settingsAvailable = controller.canUseTools,
                        scroll = welcomeScroll,
                        progress = { hero.progress(welcomeScroll.value) },
                        onSpotPlaced = hero::placeSpot,
                        onAsk = { haptics.send(); controller.ask(it) },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(controller.items, key = { _, item -> item.id }) { index, item ->
                            val entrance = if (index >= controller.animateFrom) Modifier.enterStagger(0) else Modifier
                            Box(Modifier.animateItem().then(entrance)) {
                                when (item) {
                                    is JadeItem.User -> UserBubble(item, userAvatar)
                                    is JadeItem.Reply -> ReplyBubble(
                                        item = item,
                                        lively = index == controller.items.lastIndex,
                                        mood = controller.mood,
                                        onCopy = { copy(item.text) },
                                        onShare = { share(item.text) },
                                        onReport = { reportTarget = item.id },
                                        onRefine = { controller.skill = JadeSkills.CHAT; controller.draft = "Make it " },
                                    )
                                    is JadeItem.Changes -> ChangesCard(
                                        item,
                                        onUndo = { haptics.reject(); controller.undo(item.id) },
                                        onOpenSettings = { onOpenKeyboardScreen(null) },
                                    )
                                    is JadeItem.Approval -> ApprovalCard(item, onDecide = { allowed ->
                                        if (allowed) haptics.confirm() else haptics.reject()
                                        controller.resolveApproval(item.id, allowed)
                                    })
                                    is JadeItem.Link -> LinkCard(item) { haptics.click(); onOpenKeyboardScreen(item.path) }
                                    is JadeItem.Failure -> FailureBubble(item, isPro, onUpgrade)
                                }
                            }
                        }
                        if (controller.working) item(key = "working") {
                            WorkingRow(controller.status, controller.mood, Modifier.animateItem())
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }

            // ── Composer ─────────────────────────────────────────────────
            JadeComposer(controller, attaching = attaching, isPro = isPro, onAttach = {
                haptics.click()
                attach.launch(arrayOf("text/*", "application/pdf"))
            })
        }

        // ── Jade in flight: the welcome bot, riding the scroll up into the top bar ──
        if (heroVisible) {
            val rest = hero.rest ?: Rect.Zero
            val density = LocalDensity.current
            val heroSize = with(density) { rest.width.toDp() }
            Box(
                Modifier
                    .offset {
                        val r = hero.currentRect(showWelcome, welcomeScroll.value, handoff.value)
                        IntOffset(r.left.roundToInt(), r.top.roundToInt())
                    }
                    .graphicsLayer {
                        val r = hero.currentRect(showWelcome, welcomeScroll.value, handoff.value)
                        transformOrigin = TransformOrigin(0f, 0f)
                        val s = if (rest.width > 0f) r.width / rest.width else 1f
                        scaleX = s
                        scaleY = s
                        alpha = if (flight?.hidesChatBot(JADE_FLIGHT) == true) 0f else 1f
                    }
                    .onGloballyPositioned { if (showWelcome) flight?.chatBotPlaced(it, "cloud", controller.mood) }
                    .size(heroSize),
            ) {
                Box(Modifier.enterStagger(0)) {
                    BotAvatar(
                        type = "cloud",
                        mood = if (controller.mood == BotMood.Idle && showWelcome) BotMood.Happy else controller.mood,
                        size = heroSize,
                        label = "Jade",
                    )
                }
            }
        }
    }

    if (showHistory) ChatHistorySheet(
        chats = controller.chats,
        currentId = controller.chatId,
        onDismiss = { showHistory = false },
        onOpen = { haptics.click(); controller.openChat(it); showHistory = false },
        onDelete = { haptics.reject(); controller.deleteChat(it) },
        onClearAll = { haptics.reject(); controller.clearHistory(); showHistory = false },
        onNew = { haptics.click(); controller.newChat(); showHistory = false },
    )

    reportTarget?.let { target ->
        ReportDialog(
            onDismiss = { reportTarget = null },
            onSend = { reason, details ->
                haptics.confirm()
                controller.report(target, reason, details)
                reportTarget = null
            },
        )
    }
}

const val JADE_FLIGHT = "jade"

/**
 * Where the welcome bot flies: it rides the scrolling welcome up, then eases
 * into the top bar's avatar slot, shrinking as it goes. All rects are in the
 * Jade screen's own coordinates, so the screen's enter/exit motion can't
 * skew them, and everything is re-measured whenever any of the three
 * anchors (screen, bar slot, welcome spot) moves.
 */
@Stable
private class HeroFlight {
    private var screen: LayoutCoordinates? = null
    private var barNode: LayoutCoordinates? = null
    private var spotNode: LayoutCoordinates? = null

    var scroll: () -> Int = { 0 }
    var bar by mutableStateOf<Rect?>(null); private set
    /** The welcome spot with the welcome scrolled to the top. */
    var rest by mutableStateOf<Rect?>(null); private set
    var handoffFrom: Rect? = null
    val ready: Boolean get() = bar != null && rest != null

    fun placeScreen(coordinates: LayoutCoordinates) { screen = coordinates; measure() }
    fun placeBar(coordinates: LayoutCoordinates) { barNode = coordinates; measure() }
    fun placeSpot(coordinates: LayoutCoordinates) { spotNode = coordinates; measure() }

    private fun measure() {
        val s = screen?.takeIf { it.isAttached } ?: return
        barNode?.takeIf { it.isAttached }?.let { bar = s.localBoundingBoxOf(it, clipBounds = false) }
        spotNode?.takeIf { it.isAttached }?.let {
            rest = s.localBoundingBoxOf(it, clipBounds = false).translate(0f, scroll().toFloat())
        }
    }

    /** Scroll distance over which the trip completes. */
    private fun travel(): Float {
        val b = bar ?: return 1f
        val r = rest ?: return 1f
        return ((r.top - b.top) * 0.9f).coerceAtLeast(1f)
    }

    fun progress(scrollY: Int): Float = (scrollY / travel()).coerceIn(0f, 1f)

    fun rectFor(scrollY: Int): Rect? {
        val b = bar ?: return null
        val r = rest ?: return null
        val riding = r.translate(0f, -scrollY.toFloat())
        return lerpRect(riding, b, FastOutSlowInEasing.transform(progress(scrollY)))
    }

    fun currentRect(welcome: Boolean, scrollY: Int, handoff: Float): Rect {
        val b = bar ?: return Rect.Zero
        if (welcome) return rectFor(scrollY) ?: b
        return lerpRect(handoffFrom ?: b, b, handoff)
    }

    private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
        androidx.compose.ui.util.lerp(a.left, b.left, t),
        androidx.compose.ui.util.lerp(a.top, b.top, t),
        androidx.compose.ui.util.lerp(a.right, b.right, t),
        androidx.compose.ui.util.lerp(a.bottom, b.bottom, t),
    )
}

@Composable
private fun JadeWelcome(
    name: String?,
    settingsAvailable: Boolean,
    scroll: ScrollState,
    progress: () -> Float,
    onSpotPlaced: (LayoutCoordinates) -> Unit,
    onAsk: (String) -> Unit,
) {
    val shown = starters.filter { settingsAvailable || !it.settings }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
            // The sunburst stays behind and shrinks away as Jade takes off.
            MorphingBackdrop(
                phase = 2f,
                modifier = Modifier
                    .matchParentSize()
                    .enterStagger(0)
                    .graphicsLayer {
                        val p = progress()
                        alpha = 1f - p
                        scaleX = 1f - 0.4f * p
                        scaleY = 1f - 0.4f * p
                        rotationZ = -40f * p
                    },
                colors = List(5) { MaterialTheme.colorScheme.secondaryContainer },
            )
            // Jade herself is drawn above the whole screen (see HeroFlight); this only marks her spot.
            Box(Modifier.size(128.dp).onGloballyPositioned(onSpotPlaced))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Hi ${name?.takeIf(String::isNotBlank) ?: "there"}! I’m Jade.",
            style = Heading.Rounded.display(36.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.enterStagger(1),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (settingsAvailable) "I write with you — and I can tune every corner of your keyboard. Just ask."
            else "Let’s find the right words together.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f).enterStagger(2),
        )
        Spacer(Modifier.height(22.dp))
        SectionLabel("Try asking", Modifier.fillMaxWidth().enterStagger(3))
        Spacer(Modifier.height(6.dp))
        shown.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { column, starter ->
                    val index = row * 2 + column
                    ToneCard(
                        tone = toneAt(index),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).heightIn(min = 104.dp).enterStagger(4 + index),
                        onClick = { onAsk(starter.text) },
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(starter.icon, null, Modifier.size(22.dp))
                            Text(starter.text, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val UserBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp)
private val JadeBubbleShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)

@Composable
private fun SkillTag(skillId: String, color: Color, contentColor: Color) {
    val skill = jadeSkill(skillId)
    Surface(shape = CircleShape, color = color, contentColor = contentColor) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(skill.icon, null, Modifier.size(14.dp))
            Text(skill.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun UserBubble(item: JadeItem.User, avatar: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f, fill = false).padding(start = 44.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (item.skill != JadeSkills.CHAT) SkillTag(item.skill, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Surface(shape = UserBubbleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                SelectionContainer {
                    Text(
                        item.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 40,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        AvatarBadge(type = avatar, size = 36.dp, container = MaterialTheme.colorScheme.primaryContainer, label = "You")
    }
}

@Composable
private fun JadeFace(mood: BotMood, lively: Boolean) {
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        BotAvatar(type = "cloud", mood = mood, size = 34.dp, still = !lively, interactive = lively, label = "Jade")
    }
}

@Composable
private fun ReplyBubble(
    item: JadeItem.Reply,
    lively: Boolean,
    mood: BotMood,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onRefine: () -> Unit,
) {
    val isSkill = item.skill != JadeSkills.CHAT
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            JadeFace(if (lively && mood == BotMood.Happy) BotMood.Happy else BotMood.Idle, lively)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = JadeBubbleShape,
                color = if (isSkill) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isSkill) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false).padding(end = 28.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSkill) SkillTag(item.skill, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.colorScheme.onSurface)
                    SelectionContainer {
                        Text(
                            rememberMarkdown(item.text, MaterialTheme.colorScheme.surfaceContainerHighest),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniAction(Icons.Filled.ContentCopy, "Copy", onCopy)
            MiniAction(Icons.Filled.Share, "Share", onShare)
            if (isSkill) MiniAction(Icons.Filled.Tune, "Refine", onRefine)
            if (item.reported) {
                Text("Reported", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                MiniAction(Icons.Filled.Flag, "Report", onReport)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    TextButton(
        onClick = { haptics.tick(); onClick() },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingRow(status: String?, mood: BotMood, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        JadeFace(mood, lively = true)
        Spacer(Modifier.width(8.dp))
        Surface(shape = JadeBubbleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadingIndicator(Modifier.size(36.dp))
                AnimatedContent(targetState = status ?: "Thinking…", label = "working-status") {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val hexColor = Regex("^#[0-9A-Fa-f]{6}$")

@Composable
private fun ValueChip(value: String, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hexColor.matches(value)) {
            Box(Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(value)), CircleShape))
        }
        Text(
            value.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase).take(28),
            style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChangesCard(item: JadeItem.Changes, onUndo: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (item.undone) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (item.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 42.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.noxquill.rewordium.app.ui.ToneIcon(
                    if (item.undone) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Tune,
                    if (item.undone) Tone.Neutral else Tone.Tertiary,
                    size = 38.dp,
                )
                AnimatedContent(targetState = item.undone, label = "changes-title") { undone ->
                    val count = item.changes.size
                    Text(
                        if (undone) "Changes undone" else "Updated $count setting${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            item.changes.take(8).forEach { change ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(change.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ValueChip(change.previous, strong = false)
                    Text("→", style = MaterialTheme.typography.labelLarge)
                    ValueChip(change.current, strong = true)
                }
            }
            if (item.changes.size > 8) Text("+${item.changes.size - 8} more", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.LocalContentColor.current),
                ) { Text("Keyboard settings", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = onUndo, enabled = !item.undone, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Undo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApprovalCard(item: JadeItem.Approval, onDecide: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 42.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Shield, null)
                Column {
                    Text("Okay to change advanced settings?", style = MaterialTheme.typography.titleSmall)
                    Text("These are hidden or developer options. You can undo afterwards.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item.proposals.forEach { proposal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(proposal.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ValueChip(proposal.current, strong = false)
                    Text("→", style = MaterialTheme.typography.labelLarge)
                    ValueChip(proposal.proposed, strong = true)
                }
            }
            AnimatedContent(targetState = item.state, label = "approval-state") { state ->
                when (state) {
                    JadeItem.Approval.State.Pending -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { onDecide(false) }, shapes = ButtonDefaults.shapes()) { Text("Not now") }
                        Button(onClick = { onDecide(true) }, shapes = ButtonDefaults.shapes()) { Text("Allow") }
                    }
                    JadeItem.Approval.State.Allowed -> Text("You allowed this", style = MaterialTheme.typography.labelLarge)
                    JadeItem.Approval.State.Declined -> Text("Not changed", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LinkCard(item: JadeItem.Link, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(start = 42.dp).expressivePress(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Text("Open ${item.label}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FailureBubble(item: JadeItem.Failure, isPro: Boolean, onUpgrade: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        JadeFace(BotMood.Sad, lively = false)
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = JadeBubbleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f, fill = false).padding(end = 28.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
                if (!isPro && (item.text.contains("credit", ignoreCase = true) || item.text.contains("AI uses", ignoreCase = true))) {
                    TextButton(onClick = onUpgrade, contentPadding = PaddingValues(0.dp)) {
                        Text(if (com.noxquill.rewordium.keyboard.app.ReleaseFlags.subscriptionsEnabled) "Get more room with Pro" else "See your AI uses")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JadeComposer(controller: JadeController, attaching: Boolean, isPro: Boolean, onAttach: () -> Unit) {
    val haptics = LocalHaptics.current
    val skill = jadeSkill(controller.skill)
    val canSend = controller.draft.isNotBlank() && !controller.working
    val tilt by animateFloatAsState(
        if (canSend) 0f else -35f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "send-tilt",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jadeSkills, key = { it.id }) { option ->
                    val selected = option.id == controller.skill
                    FilterChip(
                        selected = selected,
                        onClick = { haptics.tick(); controller.skill = option.id },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
                        shape = CircleShape,
                    )
                }
            }
            AnimatedVisibility(
                visible = controller.skill == "tone" || controller.skill == "translate",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val options = if (controller.skill == "tone") JadeSkills.tones else JadeSkills.languages
                val selected = if (controller.skill == "tone") controller.tone else controller.language
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    items(options, key = { it }) { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = {
                                haptics.tick()
                                if (controller.skill == "tone") controller.tone = option else controller.language = option
                            },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = onAttach, enabled = !attaching, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                    if (attaching) LoadingIndicator(Modifier.size(28.dp)) else Icon(Icons.Filled.AttachFile, "Attach a text file or PDF")
                }
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                    TextField(
                        value = controller.draft,
                        onValueChange = { controller.draft = it.take(JadeSkillsLimit) },
                        placeholder = { Text(skill.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                if (controller.working) {
                    FilledIconButton(
                        onClick = { haptics.reject(); controller.stop() },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.Filled.Stop, "Stop") }
                } else {
                    FilledIconButton(
                        onClick = { haptics.send(); controller.send() },
                        enabled = canSend,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(52.dp).graphicsLayer { rotationZ = tilt },
                    ) { Icon(if (controller.skill == JadeSkills.CHAT) Icons.Filled.ArrowUpward else Icons.AutoMirrored.Filled.Send, "Send to Jade") }
                }
            }
            val counter = if (controller.draft.length > 6_000) " · ${controller.draft.length}/8,000" else ""
            Text(
                "Jade is AI and can make mistakes${if (isPro) "" else " · each message uses 1 AI use"}$counter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

private const val JadeSkillsLimit = 8_000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatHistorySheet(
    chats: List<JadeChatSummary>,
    currentId: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onNew: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your chats", style = MaterialTheme.typography.headlineSmall)
                    Text("Saved only on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chats.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
            }
            if (chats.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotAvatar(type = "cloud", mood = BotMood.Sleeping, size = 72.dp, label = "No chats yet")
                    Text("No chats yet — say hi to Jade!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                        Surface(
                            onClick = { onOpen(chat.id) },
                            shape = segmentShape(index, chats.size),
                            color = if (chat.id == currentId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        ) {
                            Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Each chat gets a whole library shape in the row rhythm; the open chat's stays lime against its coral row.
                                val tone = if (chat.id == currentId) Tone.Tertiary else toneAt(index)
                                AvatarBadge(type = "cloud", size = 44.dp, container = tone.container(), shape = tone.shape())
                                Column(Modifier.weight(1f)) {
                                    Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // Softened row ink rather than fixed greys, so the open chat's coral row stays readable.
                                    Text(chat.preview, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(chat.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalContentColor.current.copy(alpha = 0.62f),
                                    )
                                }
                                IconButton(onClick = { onDelete(chat.id) }) { Icon(Icons.Filled.DeleteOutline, "Delete chat") }
                            }
                        }
                    }
                }
            }
            Button(onClick = onNew, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.AddComment, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New chat")
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        icon = { Icon(Icons.Filled.DeleteOutline, null) },
        title = { Text("Clear all chats?") },
        text = { Text("Every Jade conversation saved on this device will be deleted.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClearAll() }) { Text("Clear all", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
    )
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf(reportReasons.first()) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Flag, null) },
        title = { Text("Report this reply") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Tell us what’s wrong. This reply and the message before it are sent to the Rewordium team so we can review it and improve Jade.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                reportReasons.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = option == reason, onClick = { reason = option }, role = Role.RadioButton).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == reason, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = { Button(onClick = { onSend(reason, details) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
