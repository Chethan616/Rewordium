package com.noxquill.rewordium.app.screens

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.PlatformSetupStatus
import com.noxquill.rewordium.app.jade.JadeSkillUi
import com.noxquill.rewordium.app.jade.jadeSkills
import com.noxquill.rewordium.app.ui.AvatarBadge
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import com.noxquill.rewordium.app.ui.monoLabel
import com.noxquill.rewordium.app.ui.monoNumber
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.graphics.shapes.RoundedPolygon
import com.noxquill.rewordium.app.ui.ExpressiveShapes
import com.noxquill.rewordium.app.ui.ShapeMotion
import com.noxquill.rewordium.app.ui.ShapeShifter
import com.noxquill.rewordium.app.ui.shape
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.StatusPill
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneCard
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.container
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.expressivePress
import com.noxquill.rewordium.app.ui.onContainer
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.app.ui.toneAt
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.avatar.LocalBotFlight
import com.noxquill.rewordium.app.ui.avatar.flightEnd
import com.noxquill.rewordium.app.ui.avatar.rememberFlightSpot
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.jade.JadeChatSummary
import java.util.Calendar

private data class KeyboardAsk(val label: String, val prompt: String, val icon: ImageVector)

private val keyboardAsks = listOf(
    KeyboardAsk("Taller keys", "Make my keyboard a little taller", Icons.Filled.Height),
    KeyboardAsk("Dark keyboard", "Switch my keyboard to a dark theme", Icons.Filled.DarkMode),
    KeyboardAsk("Quiet typing", "Turn off key press sounds", Icons.AutoMirrored.Filled.VolumeOff),
    KeyboardAsk("Number row", "Show the number row on my keyboard", Icons.Filled.Pin),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    account: AccountInfo?,
    avatar: String,
    status: PlatformSetupStatus,
    recentChats: List<JadeChatSummary>,
    jadeCanTune: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenJade: () -> Unit,
    onAskJade: (prompt: String) -> Unit,
    onUseSkill: (skillId: String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenKeyboard: () -> Unit,
    onEnableKeyboard: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onOpenPro: () -> Unit,
    onChangeAvatar: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val flight = LocalBotFlight.current
    val jadeSpot = rememberFlightSpot()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hey night owl"
        }
    }
    fun openJadeWithFlight() {
        flight?.toChat(JADE_FLIGHT, "cloud", BotMood.Happy, jadeSpot.coords)
        onOpenJade()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomInset + 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Greeting: the ELENA poster ──────────────────────────────────
        item(key = "header") {
            Column(Modifier.fillMaxWidth().enterStagger(0), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarBadge(
                        type = avatar,
                        size = 56.dp,
                        mood = BotMood.Happy,
                        still = false,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        motion = ShapeMotion.Spin,
                        label = "Your avatar. Tap to change.",
                        modifier = Modifier
                            .expressivePress(0.9f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { haptics.click(); onChangeAvatar() },
                    )
                    Text(
                        "WHAT ARE WE\nWRITING TODAY?",
                        style = monoLabel(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = { haptics.click(); onOpenPro() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.expressivePress(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Stars, null, Modifier.size(16.dp))
                            Text(
                                when {
                                    account?.isPro == true -> "PRO"
                                    account == null -> "AI"
                                    else -> "${account.credits} AI"
                                },
                                style = monoNumber(),
                            )
                        }
                    }
                }
                val firstName = account?.name?.trim()?.substringBefore(' ')?.takeIf(String::isNotBlank)
                // ELENA-style: a smaller greeting line, then the name as the giant hero word,
                // each sized to fill exactly one line on any screen.
                Column(verticalArrangement = Arrangement.spacedBy((-6).dp)) {
                    Text(
                        Heading.Poster.text(if (firstName != null) "$greeting," else greeting),
                        style = Heading.Poster.display(if (firstName != null) 44.sp else 72.sp).copy(lineHeight = 1.0.em),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = if (firstName != null) 48.sp else 80.sp, stepSize = 2.sp),
                    )
                    if (firstName != null) Text(
                        Heading.Poster.text(firstName),
                        style = Heading.Poster.display(104.sp).copy(lineHeight = 0.95.em),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 120.sp, stepSize = 2.sp),
                    )
                }
            }
        }

        // ── Jade hero ────────────────────────────────────────────────────
        item(key = "jade") {
            Surface(
                onClick = { haptics.click(); openJadeWithFlight() },
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().enterStagger(1).expressivePress(0.97f),
            ) {
                Box {
                    // Jade is a cloud, so her shape loop starts on the puffy cloud.
                    ShapeShifter(
                        shapes = JadeHeroShapes,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        holdMillis = 3_200L,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(170.dp),
                    )
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Jade", style = Heading.Rounded.display(52.sp))
                                Text(
                                    if (jadeCanTune) "Your writing buddy — who can also tune every keyboard setting for you."
                                    else "Your writing buddy for clearer, kinder, sharper words.",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            BotAvatar(
                                type = "cloud",
                                mood = BotMood.Happy,
                                size = 104.dp,
                                label = "Jade",
                                modifier = Modifier.flightEnd(flight, JADE_FLIGHT, jadeSpot),
                            )
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Ask Jade anything…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.ArrowUpward, null) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Writing skills ───────────────────────────────────────────────
        item(key = "skills") {
            Column(Modifier.enterStagger(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Write with Jade")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    itemsIndexed(jadeSkills.drop(1), key = { _, skill -> skill.id }) { index, skill ->
                        SkillTile(skill, toneAt(index)) { haptics.tick(); onUseSkill(skill.id) }
                    }
                }
            }
        }

        // ── Tune the keyboard by asking ──────────────────────────────────
        if (jadeCanTune) item(key = "tune") {
            Column(Modifier.enterStagger(3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Just ask Jade", Modifier.weight(1f))
                    TextButton(onClick = { haptics.click(); onOpenKeyboard() }) { Text("All settings") }
                }
                keyboardAsks.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { column, ask ->
                            val tone = listOf(Tone.Secondary, Tone.Tertiary, Tone.Tertiary, Tone.Secondary)[row * 2 + column]
                            ToneCard(
                                tone = tone,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f),
                                onClick = { onAskJade(ask.prompt) },
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(ask.icon, null, Modifier.size(22.dp))
                                    Text(ask.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Setup ────────────────────────────────────────────────────────
        item(key = "setup") {
            Column(Modifier.enterStagger(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Your writing tools")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetupTile(
                        title = "Keyboard",
                        bot = "droid",
                        tone = Tone.Tertiary,
                        shapeColor = MaterialTheme.colorScheme.primaryContainer,
                        shapes = KeyboardTileShapes,
                        ready = status.keyboardSelected,
                        statusText = when {
                            status.keyboardSelected -> "Ready"
                            status.keyboardEnabled -> "Pick it"
                            else -> "Set up"
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { if (status.keyboardSelected) onOpenKeyboard() else onEnableKeyboard() },
                    )
                    SetupTile(
                        title = "Accessibility",
                        bot = "ghost",
                        // Primary vs the keyboard's tertiary: the pair that stays far apart in every palette.
                        tone = Tone.Primary,
                        shapeColor = MaterialTheme.colorScheme.tertiaryContainer,
                        shapes = AccessibilityTileShapes,
                        ready = status.accessibilityEnabled,
                        statusText = if (status.accessibilityEnabled) "On" else "Turn on",
                        modifier = Modifier.weight(1f),
                        onClick = onEnableAccessibility,
                    )
                }
            }
        }

        // ── Recent chats ─────────────────────────────────────────────────
        if (recentChats.isNotEmpty()) item(key = "recent") {
            Column(Modifier.enterStagger(5), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Pick up where you left off", Modifier.weight(1f))
                    TextButton(onClick = { haptics.click(); onOpenJade() }) { Text("Open Jade") }
                }
                val shown = recentChats.take(3)
                shown.forEachIndexed { index, chat ->
                    Surface(
                        onClick = { haptics.click(); onOpenChat(chat.id) },
                        shape = segmentShape(index, shown.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().expressivePress(0.98f),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarBadge(type = "cloud", size = 44.dp, container = toneAt(index).container(), shape = toneAt(index).shape())
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(chat.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                DateUtils.getRelativeTimeSpanString(chat.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }

        // ── Pro ──────────────────────────────────────────────────────────
        item(key = "pro") {
            ToneCard(tone = Tone.Tertiary, modifier = Modifier.fillMaxWidth().enterStagger(6), onClick = onOpenPro) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AvatarBadge(
                        type = "star",
                        size = 60.dp,
                        mood = BotMood.Happy,
                        container = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f),
                        shape = MaterialShapes.SoftBurst,
                        motion = ShapeMotion.Spin,
                        label = "Rewordium Pro",
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val selling = com.noxquill.rewordium.keyboard.app.ReleaseFlags.subscriptionsEnabled
                        Text(
                            when {
                                account?.isPro == true -> "Rewordium Pro is on"
                                selling -> "More room with Pro"
                                else -> "AI writing uses"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                account?.isPro == true -> "Unlimited AI, everywhere you write."
                                selling -> "${account?.credits ?: 0} credits left · Unlimited Jade with Pro"
                                account == null -> "Optional AI tools · sign in to use the built-in AI"
                                else -> "${account.credits} of ${com.noxquill.rewordium.keyboard.app.ReleaseFlags.FREE_AI_USES} free AI uses left · your keyboard is free"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }
        }
    }
}

/** Jade's hero shape loop: from the cloud, through a few soft friends, and back. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val JadeHeroShapes by lazy {
    ExpressiveShapes.loop(MaterialShapes.Puffy, MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val KeyboardTileShapes by lazy {
    ExpressiveShapes.loop(MaterialShapes.Cookie6Sided, MaterialShapes.Cookie4Sided, MaterialShapes.Pentagon, MaterialShapes.Cookie9Sided)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val AccessibilityTileShapes by lazy {
    ExpressiveShapes.loop(MaterialShapes.Flower, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Clover8Leaf)
}

/** Each writing skill wears its own library shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun skillShape(id: String): RoundedPolygon = when (id) {
    "rewrite" -> MaterialShapes.Clover4Leaf
    "grammar" -> MaterialShapes.Cookie9Sided
    "tone" -> MaterialShapes.Sunny
    "summarize" -> MaterialShapes.Cookie4Sided
    "translate" -> MaterialShapes.Cookie12Sided
    "humanize" -> MaterialShapes.Flower
    "polish" -> MaterialShapes.Gem
    "detector" -> MaterialShapes.SoftBurst
    else -> ExpressiveShapes.pick(id)
}

@Composable
private fun SkillTile(skill: JadeSkillUi, tone: Tone, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.size(width = 132.dp, height = 132.dp).expressivePress(0.92f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ToneIcon(skill.icon, tone, shape = skillShape(skill.id), size = 46.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(skill.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(skill.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A writing tool's tile. Each tool keeps its own colour and shape loop, so
 * the keyboard and accessibility tiles never look alike; whether a tool is
 * ready shows in the status pill and the bot's mood.
 */
@Composable
private fun SetupTile(
    title: String,
    bot: String,
    tone: Tone,
    shapeColor: androidx.compose.ui.graphics.Color,
    shapes: List<RoundedPolygon>,
    ready: Boolean,
    statusText: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics.click(); onClick() },
        shape = MaterialTheme.shapes.large,
        color = tone.container(),
        contentColor = tone.onContainer(),
        modifier = modifier.heightIn(min = 128.dp).expressivePress(0.95f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ShapeShifter(shapes = shapes, color = shapeColor, holdMillis = 2_200L, modifier = Modifier.size(60.dp)) {
                    BotAvatar(
                        type = bot,
                        mood = if (ready) BotMood.Happy else BotMood.NeedsYou,
                        size = 42.dp,
                        still = !ready,
                        label = title,
                        modifier = Modifier.graphicsLayer { translationY = -1.5.dp.toPx() },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (ready) StatusPill(statusText, true)
                else StatusPill(
                    statusText,
                    active = false,
                    container = tone.onContainer().copy(alpha = 0.12f),
                    content = tone.onContainer(),
                    dot = tone.onContainer(),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}