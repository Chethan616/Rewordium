package com.noxquill.rewordium.app.screens.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.R
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyFont
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyMotion
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyShape
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeFactory
import com.noxquill.rewordium.keyboard.ime.theme.studio.KeyboardThemeSpec
import com.noxquill.rewordium.keyboard.ime.theme.studio.keyPressMotion

/** `#RRGGBB` as a Compose colour. */
fun hexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

/** The Compose shape of a keyboard key shape (the same geometry the keyboard's Snygg shapes draw). */
fun KeyShape.composeShape(): Shape = when (this) {
    KeyShape.SQUARE -> RoundedCornerShape(3.dp)
    KeyShape.ROUNDED -> RoundedCornerShape(9.dp)
    KeyShape.SOFT -> RoundedCornerShape(32)
    KeyShape.PILL -> RoundedCornerShape(50)
    KeyShape.BUBBLE -> RoundedCornerShape(topStartPercent = 46, topEndPercent = 46, bottomEndPercent = 46, bottomStartPercent = 10)
    KeyShape.LEAF -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 8, bottomEndPercent = 50, bottomStartPercent = 8)
    KeyShape.CUT -> CutCornerShape(7.dp)
    KeyShape.TICKET -> RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
}

/** The typeface the keyboard uses for [font] (the same bundled files go into the theme). */
fun KeyFont.composeFamily(): FontFamily? = when (this) {
    KeyFont.SYSTEM -> null
    KeyFont.ROUNDED -> FontFamily(Font(R.font.fredoka, FontWeight.Medium), Font(R.font.fredoka, FontWeight.Bold))
    KeyFont.SOFT -> FontFamily(Font(R.font.comfortaa, FontWeight.Medium), Font(R.font.comfortaa, FontWeight.Bold))
    KeyFont.MONO -> FontFamily(Font(R.font.space_mono_bold, FontWeight.Bold))
    KeyFont.SERIF -> FontFamily(Font(R.font.instrument_serif_regular, FontWeight.Normal))
}

private enum class KeyRole { Letter, Special, Punctuation, Space, Enter }

private data class MockKey(val label: String, val weight: Float = 1f, val role: KeyRole = KeyRole.Letter)

private val rows = listOf(
    "qwertyuiop".map { MockKey(it.toString()) },
    "asdfghjkl".map { MockKey(it.toString()) },
    listOf(MockKey("⇧", 1.5f, KeyRole.Special)) + "zxcvbnm".map { MockKey(it.toString()) } + MockKey("⌫", 1.5f, KeyRole.Special),
    listOf(
        MockKey("?123", 1.5f, KeyRole.Special), MockKey(",", 1f, KeyRole.Punctuation), MockKey("", 5f, KeyRole.Space),
        MockKey(".", 1f, KeyRole.Punctuation), MockKey("↵", 1.5f, KeyRole.Enter),
    ),
)

/**
 * A miniature of the keyboard in [spec]'s look: its colours, key shapes (and the shape a key morphs into
 * when pressed), typeface, depth, outline, suggestion strip and space-bar mascot. With [interactive] the
 * keys can be pressed and play [motion], exactly like the real keyboard.
 */
@Composable
fun KeyboardMock(
    spec: KeyboardThemeSpec,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    interactive: Boolean = false,
    motion: KeyMotion = KeyMotion.NONE,
    mascot: String? = spec.mascot,
) {
    val s = remember(spec) { spec.sanitized() }
    val c = remember(s) { KeyboardThemeFactory.colors(s) }
    val family = remember(s.font) { s.font.composeFamily() }
    val weight = when {
        s.font == KeyFont.MONO -> FontWeight.Bold
        s.bold -> FontWeight.Bold
        family != null -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val gap = if (compact) 2.dp else 5.dp
    val keyHeight = if (compact) 15.dp else 42.dp
    val labelSize = if (compact) 7.sp else 20.sp
    var spacePresses by remember { mutableIntStateOf(0) }
    Surface(
        color = hexColor(c.background),
        shape = RoundedCornerShape(if (compact) 14.dp else 26.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(if (compact) 6.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (!compact) {
                // The suggestion strip: the likely word in the middle, bold in the accent.
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Thank", "you", "thanks").forEachIndexed { i, word ->
                        Text(
                            word,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontFamily = family,
                                fontWeight = if (i == 1) FontWeight.Bold else weight,
                                fontSize = 15.sp,
                                color = hexColor(if (i == 1) c.highlight else c.letters),
                            ),
                        )
                    }
                }
            }
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = if (r == 1) (if (compact) 5.dp else 16.dp) else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { key ->
                        MockKeyView(
                            key = key, spec = s, colors = c, height = keyHeight, labelSize = labelSize, family = family,
                            weight = weight, compact = compact, interactive = interactive, motion = motion,
                            mascot = mascot, spacePresses = spacePresses, onSpace = { spacePresses++ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MockKeyView(
    key: MockKey,
    spec: KeyboardThemeSpec,
    colors: KeyboardThemeFactory.Colors,
    height: Dp,
    labelSize: TextUnit,
    family: FontFamily?,
    weight: FontWeight,
    compact: Boolean,
    interactive: Boolean,
    motion: KeyMotion,
    mascot: String?,
    spacePresses: Int,
    onSpace: () -> Unit,
) {
    val haptics = LocalHaptics.current
    var pressed by remember { mutableStateOf(false) }
    val (fill, ink) = when {
        pressed && key.role == KeyRole.Enter -> colors.accentStrong to colors.onAccentStrong
        pressed && key.role == KeyRole.Special -> colors.accent to colors.onAccent
        pressed -> colors.keyPressed to colors.onKeyPressed
        key.role == KeyRole.Special -> colors.special to colors.onSpecial
        key.role == KeyRole.Punctuation -> colors.punctuation to colors.letters
        key.role == KeyRole.Enter -> colors.accent to colors.onAccent
        else -> colors.key to colors.letters
    }
    val shape = when (key.role) {
        KeyRole.Space, KeyRole.Enter -> RoundedCornerShape(50)
        else -> (if (pressed) spec.pressedShape ?: spec.shape else spec.shape).composeShape()
    }
    val elevation = (if (compact) spec.shadow * 0.5f else spec.shadow.toFloat()).dp
    Box(
        Modifier
            .weight(key.weight)
            .height(height)
            .keyPressMotion(pressed, motion)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(hexColor(fill))
            .then(if (spec.outline) Modifier.border(BorderStroke(1.dp, hexColor(colors.letters).copy(alpha = 0.35f)), shape) else Modifier)
            .then(
                if (interactive) Modifier.pointerInput(key) {
                    detectTapGestures(onPress = {
                        pressed = true
                        haptics.tick()
                        if (key.role == KeyRole.Space) onSpace()
                        tryAwaitRelease()
                        pressed = false
                    })
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (key.role == KeyRole.Space) {
            if (mascot != null) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = if (compact) 3.dp else 10.dp)) {
                    BotAvatar(
                        type = mascot,
                        mood = if (spacePresses > 0) BotMood.Happy else BotMood.Idle,
                        size = height * 0.8f,
                        interactive = false,
                        still = true,
                        hopSignal = spacePresses,
                        label = null,
                    )
                }
            }
            if (!compact) Text("English", style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 12.sp, color = hexColor(colors.lettersDim)))
        } else if (keyIcon(key.label) != null) {
            Icon(
                painter = keyIcon(key.label)!!,
                contentDescription = null,
                tint = hexColor(ink),
                modifier = Modifier.size(if (compact) 8.dp else 22.dp),
            )
        } else if (key.label.isNotEmpty()) {
            Text(
                key.label,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = weight,
                    fontSize = if (key.label.length > 1) labelSize * 0.62f else labelSize,
                    color = hexColor(ink),
                ),
            )
        }
    }
}

/** The keyboard's own artwork for shift, delete and enter, so the preview matches the real keys. */
@Composable
private fun keyIcon(label: String): Painter? = when (label) {
    "⇧" -> painterResource(com.noxquill.rewordium.keyboard.R.drawable.ic_keyboard_shift)
    "⌫" -> rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace)
    "↵" -> painterResource(com.noxquill.rewordium.keyboard.R.drawable.ic_custom_return)
    else -> null
}

/** The theme's four main colours as a row of pills, for compact listings. */
@Composable
fun ThemeSwatchRow(spec: KeyboardThemeSpec, modifier: Modifier = Modifier) {
    val c = remember(spec) { KeyboardThemeFactory.colors(spec) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(c.background, c.key, c.special, c.accent).forEach { hex ->
            Box(Modifier.height(12.dp).weight(1f).clip(RoundedCornerShape(50)).background(hexColor(hex)).border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(50)))
        }
    }
}
