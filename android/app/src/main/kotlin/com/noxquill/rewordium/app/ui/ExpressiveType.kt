package com.noxquill.rewordium.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.R

/**
 * Rewordium's type, taken from the Material 3 Expressive showcase: an
 * ultra-condensed black italic poster face ("ELENA", "NICE"), a narrow
 * confident sans ("Cardfolio"), monospace labels ("Where · When"), an
 * editorial serif, a soft rounded face ("Now slowly exhale") and a
 * geometric rounded one ("Aquacoin"). Most of it is one variable font,
 * Roboto Flex, pushed along its width, weight and slant axes.
 */
object RewordiumFonts {
    private fun flex(weight: Int, width: Float = 100f, slant: Float = 0f, grade: Int = 0, style: FontStyle = FontStyle.Normal) = Font(
        resId = R.font.roboto_flex,
        weight = FontWeight(weight.coerceIn(1, 1000)),
        style = style,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.slant(slant),
            FontVariation.grade(grade),
        ),
    )

    private fun fredoka(weight: Int) = Font(
        resId = R.font.fredoka,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun comfortaa(weight: Int) = Font(
        resId = R.font.comfortaa,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** Body and UI text: Roboto Flex at its natural width. */
    val Body = FontFamily(
        flex(400), flex(500), flex(600), flex(700), flex(800), flex(900),
        flex(400, slant = -10f, style = FontStyle.Italic),
    )

    /** "ELENA": the narrowest, heaviest, slanted cut. */
    val Poster = FontFamily(flex(1000, width = 25f, slant = -10f))

    /** "Cardfolio": narrow and assured. */
    val Narrow = FontFamily(flex(600, width = 30f), flex(800, width = 30f))

    /** Wide and heavy, for big first impressions. */
    val Wide = FontFamily(flex(900, width = 151f))

    /** Wide and light, airy. */
    val Airy = FontFamily(flex(300, width = 135f))

    /** Thin, narrow and slanted, like the "Royal Preferred" script. */
    val Whisper = FontFamily(flex(300, width = 25f, slant = -10f))

    val Serif = FontFamily(
        Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )

    val Mono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )

    val Rounded = FontFamily(fredoka(400), fredoka(500), fredoka(600), fredoka(700))

    val Geometric = FontFamily(comfortaa(400), comfortaa(500), comfortaa(700))
}

/**
 * Every screen's headline has its own voice. Only family, weight, case and
 * spacing are set here; sizes come from wherever the heading sits, so the
 * same style works in a collapsing top bar and in a hero.
 */
enum class Heading(
    val family: FontFamily,
    val weight: FontWeight,
    val tracking: TextUnit,
    val uppercase: Boolean = false,
    val italic: Boolean = false,
    val lineHeightScale: Float = 1f,
) {
    /** Home: the ELENA poster. */
    Poster(RewordiumFonts.Poster, FontWeight(1000), (-0.01).em, uppercase = true, lineHeightScale = 0.88f),
    /** Settings: the Cardfolio cut. */
    Narrow(RewordiumFonts.Narrow, FontWeight(600), (-0.01).em),
    /** Onboarding: wide and heavy. */
    Wide(RewordiumFonts.Wide, FontWeight(900), (-0.02).em, uppercase = true, lineHeightScale = 0.95f),
    /** AI provider: wide and light. */
    Airy(RewordiumFonts.Airy, FontWeight(300), (-0.01).em),
    /** Personas: thin, narrow, slanted. */
    Whisper(RewordiumFonts.Whisper, FontWeight(300), 0.em),
    /** Pro: editorial italic serif. */
    SerifItalic(RewordiumFonts.Serif, FontWeight.Normal, (-0.01).em, italic = true),
    /** Privacy and terms: editorial serif. */
    Serif(RewordiumFonts.Serif, FontWeight.Normal, (-0.01).em),
    /** Keyboard: monospace, like a terminal. */
    Mono(RewordiumFonts.Mono, FontWeight.Bold, (-0.04).em),
    /** Licenses: quiet monospace. */
    MonoLight(RewordiumFonts.Mono, FontWeight.Normal, (-0.03).em),
    /** Jade: soft and rounded. */
    Rounded(RewordiumFonts.Rounded, FontWeight(600), 0.em),
    /** Sign in: geometric and round. */
    Geometric(RewordiumFonts.Geometric, FontWeight(700), (-0.01).em);

    fun text(value: String) = if (uppercase) value.uppercase() else value

    /** This heading's look applied to [base] (keeps the base size). */
    fun style(base: TextStyle): TextStyle = base.copy(
        fontFamily = family,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = tracking,
        lineHeight = if (base.lineHeight.isSp) (base.lineHeight.value * lineHeightScale).sp else base.lineHeight,
    )
}

/** A heading style at [size], tight enough for poster-sized words. */
@Composable
fun Heading.display(size: TextUnit): TextStyle =
    style(MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = (size.value * 1.02f).sp))

/** Space Mono labels, the "Where · When" voice used for section labels and numbers. */
@Composable
fun monoLabel(): TextStyle = MaterialTheme.typography.labelMedium.copy(
    fontFamily = RewordiumFonts.Mono,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.08.em,
)

@Composable
fun monoNumber(size: TextUnit = 15.sp): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontFamily = RewordiumFonts.Mono,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    letterSpacing = (-0.02).em,
)
