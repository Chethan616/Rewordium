package com.noxquill.rewordium.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import kotlinx.coroutines.delay

/** The accent families used to give rows and tiles a playful rhythm. */
enum class Tone { Primary, Secondary, Tertiary, Error, Neutral }

@Composable
fun Tone.container(): Color = when (this) {
    Tone.Primary -> MaterialTheme.colorScheme.primaryContainer
    Tone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    Tone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    Tone.Error -> MaterialTheme.colorScheme.errorContainer
    Tone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
fun Tone.onContainer(): Color = when (this) {
    Tone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    Tone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    Tone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    Tone.Error -> MaterialTheme.colorScheme.onErrorContainer
    Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun toneAt(index: Int): Tone = listOf(Tone.Primary, Tone.Tertiary, Tone.Secondary)[index.mod(3)]

/**
 * Content rises into place on a spring, one item after another. It plays once
 * per screen visit (the flag survives recomposition and process restore), and
 * runs on the layer only, so it never re-lays out the list.
 */
@Composable
fun Modifier.enterStagger(index: Int, stepMillis: Long = 45L): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index.coerceAtMost(10) * stepMillis)
        progress.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
        played = true
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 42.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Connected toggle buttons with a visible resting fill, so unselected segments still read as buttons on white cards. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentColors() = androidx.compose.material3.ToggleButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedContainerColor = MaterialTheme.colorScheme.primary,
    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)

/** A round, morph-on-press back button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    FilledTonalIconButton(
        onClick = { haptics.click(); onBack() },
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

/**
 * A screen with the expressive large, collapsing title bar. The window
 * insets are already applied by the app root, so the bar adds none.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    heading: Heading = Heading.Narrow,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        heading.text(title),
                        fontFamily = heading.family,
                        fontWeight = heading.weight,
                        fontStyle = if (heading.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        letterSpacing = heading.tracking,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = subtitle?.let { { Text(it, style = monoLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = { if (onBack != null) BackButton(onBack, Modifier.padding(start = 8.dp)) },
                actions = actions,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** Corner rhythm of an expressive segmented list: round ends, tight joins. */
fun segmentShape(index: Int, count: Int, outer: Dp = 26.dp, inner: Dp = 6.dp): Shape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    else -> RoundedCornerShape(inner)
}

/** Section labels speak in Space Mono capitals, the "Where · When" voice from the showcase. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 8.dp, bottom = 2.dp),
        style = monoLabel(),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A titled stack of segmented rows. Each row receives its own segment shape. */
@Composable
fun SegmentedSection(
    title: String?,
    rows: List<@Composable (Shape) -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (title != null) SectionLabel(title, Modifier.padding(bottom = 6.dp))
        rows.forEachIndexed { index, row -> row(segmentShape(index, rows.size)) }
    }
}

/** An icon on a whole Material library shape, tinted by [tone]. */
@Composable
fun ToneIcon(
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = tone.shape(),
    size: Dp = 40.dp,
    spin: Boolean = false,
) {
    ShapeBadge(shape = shape, color = tone.container(), modifier = modifier.size(size), spin = spin) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.5f), tint = tone.onContainer())
    }
}

/**
 * A tappable settings row. [onClick] is deliberately the last parameter so a
 * trailing `{ … }` block is always the tap action — never the [trailing]
 * slot, which is composable content and would run on every composition.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    tone: Tone = Tone.Primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ToneIcon(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tone == Tone.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = { haptics.click(); onClick() },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().expressivePress(0.98f),
            content = content,
        )
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    shape: Shape,
    tone: Tone = Tone.Primary,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHaptics.current
    SettingRow(
        icon = icon, title = title, subtitle = subtitle, shape = shape, tone = tone,
        onClick = { onCheckedChange(!checked); haptics.toggle(!checked) },
        trailing = { ExpressiveSwitch(checked) { onCheckedChange(it); haptics.toggle(it) } },
    )
}

@Composable
fun ExpressiveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

/** A small rounded status chip with a coloured dot. */
@Composable
fun StatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    container: Color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
    dot: Color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
) {
    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
    }
}

/** How the library shape behind an [AvatarBadge] moves. */
enum class ShapeMotion {
    /** A whole library shape, at rest. */
    Still,

    /** The shape turns slowly behind the bot. */
    Spin,

    /** The shape springs from one library shape to the next. */
    Shift,
}

/**
 * A bot centred on a whole Material library shape in a theme colour. Bots
 * stand on a contact shadow at the bottom of their box, so the bot is nudged
 * up a touch to sit optically centred in the shape.
 */
@Composable
fun AvatarBadge(
    type: String,
    size: Dp,
    modifier: Modifier = Modifier,
    mood: BotMood = BotMood.Idle,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: RoundedPolygon = ExpressiveShapes.avatar(type),
    motion: ShapeMotion = ShapeMotion.Still,
    loop: List<RoundedPolygon> = ExpressiveShapes.badges,
    still: Boolean = true,
    interactive: Boolean = false,
    label: String? = null,
) {
    val bot: @Composable BoxScope.() -> Unit = {
        BotAvatar(
            type = type,
            mood = mood,
            size = size * 0.74f,
            still = still,
            interactive = interactive,
            label = label,
            modifier = Modifier.graphicsLayer { translationY = -size.toPx() * 0.025f },
        )
    }
    when (motion) {
        ShapeMotion.Shift -> {
            val shapes = remember(shape, loop) { listOf(shape) + loop.filter { it != shape } }
            ShapeShifter(shapes = shapes, color = container, modifier = modifier.size(size), content = bot)
        }
        // A spinning badge is the user's own avatar; picking a new bot morphs it into the new bot's shape.
        ShapeMotion.Spin -> ShapeMorph(shape = shape, color = container, modifier = modifier.size(size), content = bot)
        ShapeMotion.Still -> ShapeBadge(shape = shape, color = container, modifier = modifier.size(size), content = bot)
    }
}

/** "By continuing you agree to the Terms of Service and Privacy Policy", with real links. */
@Composable
fun LegalLinksText(
    prefix: String,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append(prefix)
        append(" ")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onTerms() }) { append("Terms of Service") }
        append(" and ")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onPrivacy() }) { append("Privacy Policy") }
        append(".")
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign)
}

/** A tonal card with generous corners, used for hero blocks. */
@Composable
fun ToneCard(
    tone: Tone,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    if (onClick != null) {
        Surface(onClick = { haptics.click(); onClick() }, shape = shape, color = tone.container(), contentColor = tone.onContainer(), modifier = modifier.expressivePress()) {
            Column(content = content)
        }
    } else {
        Surface(shape = shape, color = tone.container(), contentColor = tone.onContainer(), modifier = modifier) {
            Column(content = content)
        }
    }
}
