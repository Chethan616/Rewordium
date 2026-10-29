package com.noxquill.rewordium.app.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.noxquill.rewordium.data.AppSettings

private val RewordiumDark = darkColorScheme(
    primary = Color(0xFFB4C7FF),
    onPrimary = Color(0xFF08235F),
    primaryContainer = RewordiumTokens.DarkBlueContainer,
    onPrimaryContainer = Color(0xFFDEE6FF),
    secondary = Color(0xFFF0B8FF),
    onSecondary = Color(0xFF4D086E),
    secondaryContainer = RewordiumTokens.DarkBerryContainer,
    onSecondaryContainer = Color(0xFFF9D9FF),
    tertiary = Color(0xFFFFB1CE),
    onTertiary = Color(0xFF641234),
    tertiaryContainer = RewordiumTokens.DarkPinkContainer,
    onTertiaryContainer = Color(0xFFFFD9E6),
    background = RewordiumTokens.DarkCanvas,
    onBackground = Color(0xFFE3E2F0),
    surface = RewordiumTokens.DarkSurface,
    onSurface = Color(0xFFE3E2F0),
    surfaceContainerLowest = Color(0xFF0A0C14),
    surfaceContainerLow = Color(0xFF191B27),
    surfaceContainer = Color(0xFF1E2130),
    surfaceContainerHigh = Color(0xFF282B3B),
    surfaceContainerHighest = Color(0xFF333648),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val RewordiumLight = lightColorScheme(
    primary = RewordiumTokens.InkBlue,
    onPrimary = Color.White,
    primaryContainer = RewordiumTokens.LightBlueContainer,
    onPrimaryContainer = Color(0xFF001949),
    secondary = RewordiumTokens.Berry,
    onSecondary = Color.White,
    secondaryContainer = RewordiumTokens.LightBerryContainer,
    onSecondaryContainer = Color(0xFF310047),
    tertiary = RewordiumTokens.Pink,
    onTertiary = Color.White,
    tertiaryContainer = RewordiumTokens.LightPinkContainer,
    onTertiaryContainer = Color(0xFF45001F),
    background = RewordiumTokens.LightCanvas,
    onBackground = Color(0xFF191B24),
    surface = RewordiumTokens.LightSurface,
    onSurface = Color(0xFF191B24),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F1FA),
    surfaceContainer = Color(0xFFEDEBF4),
    surfaceContainerHigh = Color(0xFFE7E5EF),
    surfaceContainerHighest = Color(0xFFE0DEE8),
)

/**
 * "Electric": the Material 3 Expressive showcase palette. Bright, saturated
 * containers (violet, coral, lime) carry near-black text in both light and
 * dark, so colour blocks pop instead of fading into pastel.
 */
private val ElectricLight = lightColorScheme(
    primary = Color(0xFF7A2BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC68CFA),
    onPrimaryContainer = Color(0xFF1A0033),
    inversePrimary = Color(0xFFD7B8FF),
    secondary = Color(0xFFC2453D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFF8F85),
    onSecondaryContainer = Color(0xFF3A0805),
    tertiary = Color(0xFF4F6A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F26B),
    onTertiaryContainer = Color(0xFF161F00),
    background = Color(0xFFEFE5FF),
    onBackground = Color(0xFF160F22),
    surface = Color(0xFFF7F1FF),
    onSurface = Color(0xFF160F22),
    surfaceVariant = Color(0xFFE6DAF7),
    onSurfaceVariant = Color(0xFF4A3F5C),
    surfaceTint = Color(0xFF7A2BE0),
    inverseSurface = Color(0xFF2B2238),
    inverseOnSurface = Color(0xFFF5EEFF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7B6E8E),
    outlineVariant = Color(0xFFCDBFE0),
    surfaceBright = Color(0xFFFBF7FF),
    surfaceDim = Color(0xFFDDD0F2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6EEFF),
    surfaceContainer = Color(0xFFFBF6FF),
    surfaceContainerHigh = Color(0xFFE9DCFC),
    surfaceContainerHighest = Color(0xFFDFCFF8),
)

private val ElectricDark = darkColorScheme(
    primary = Color(0xFFD0AEFF),
    onPrimary = Color(0xFF3A0078),
    primaryContainer = Color(0xFFBE8BFA),
    onPrimaryContainer = Color(0xFF14002B),
    inversePrimary = Color(0xFF7A2BE0),
    secondary = Color(0xFFFFB4AB),
    onSecondary = Color(0xFF5C1210),
    secondaryContainer = Color(0xFFFF8A80),
    onSecondaryContainer = Color(0xFF300603),
    tertiary = Color(0xFFC3E25C),
    onTertiary = Color(0xFF263300),
    tertiaryContainer = Color(0xFFD4F36B),
    onTertiaryContainer = Color(0xFF141C00),
    background = Color(0xFF0E0913),
    onBackground = Color(0xFFEDE3F7),
    surface = Color(0xFF0E0913),
    onSurface = Color(0xFFEDE3F7),
    surfaceVariant = Color(0xFF3A2E46),
    onSurfaceVariant = Color(0xFFCDBFDC),
    surfaceTint = Color(0xFFD0AEFF),
    inverseSurface = Color(0xFFEDE3F7),
    inverseOnSurface = Color(0xFF2B2238),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF968AA5),
    outlineVariant = Color(0xFF4A3E57),
    surfaceBright = Color(0xFF352A40),
    surfaceDim = Color(0xFF0E0913),
    surfaceContainerLowest = Color(0xFF08050B),
    surfaceContainerLow = Color(0xFF17101E),
    surfaceContainer = Color(0xFF1E1527),
    surfaceContainerHigh = Color(0xFF281C33),
    surfaceContainerHighest = Color(0xFF33263F),
)

private val RewordiumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Roboto Flex everywhere for UI text; each screen's headline swaps in its own voice (see [Heading]). */
private val RewordiumTypography = Typography().let { base ->
    val f = RewordiumFonts.Body
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
        displayMedium = base.displayMedium.copy(fontFamily = f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp),
        displaySmall = base.displaySmall.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = base.titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = f),
        bodyMedium = base.bodyMedium.copy(fontFamily = f),
        bodySmall = base.bodySmall.copy(fontFamily = f),
        labelLarge = base.labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    )
}

/** Seed palettes are generated once per run (generating one takes long enough to stall a frame). */
private val seedSchemes = ConcurrentHashMap<String, ColorScheme>()

/** The colour scheme for a palette id: the wallpaper, Rewordium's own, or an expressive seed. */
fun colorSchemeFor(context: Context, paletteId: String, dark: Boolean): ColorScheme {
    if (paletteId == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    if (paletteId == "electric" || paletteId == "dynamic") return if (dark) ElectricDark else ElectricLight
    val seed = AppSettings.palettes.firstOrNull { it.id == paletteId }?.seed
    if (seed == null || paletteId == "rewordium") return if (dark) RewordiumDark else RewordiumLight
    return seedSchemes.getOrPut("$paletteId:$dark") {
        dynamicColorScheme(
            seedColor = Color(seed),
            isDark = dark,
            isAmoled = false,
            style = PaletteStyle.Expressive,
        )
    }
}

/** Generates every seed palette ahead of time, off the main thread, so the palette picker opens instantly. */
fun warmColorSchemes(context: Context) {
    for (palette in AppSettings.palettes) {
        for (dark in listOf(false, true)) runCatching { colorSchemeFor(context, palette.id, dark) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RewordiumTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by AppSettings.themeMode.collectAsState()
    val paletteId by AppSettings.palette.collectAsState()
    val dark = when (mode) {
        AppSettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppSettings.ThemeMode.LIGHT -> false
        AppSettings.ThemeMode.DARK -> true
    }
    val target = remember(paletteId, dark) { colorSchemeFor(context, paletteId, dark) }
    val scheme = target.animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = RewordiumShapes,
        typography = RewordiumTypography,
        content = content,
    )
}

/** Palette and light/dark changes glide between colours instead of snapping. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.a(): Color = animateColorAsState(this, tween(520), label = "scheme").value
    return copy(
        primary = primary.a(), onPrimary = onPrimary.a(),
        primaryContainer = primaryContainer.a(), onPrimaryContainer = onPrimaryContainer.a(),
        inversePrimary = inversePrimary.a(),
        secondary = secondary.a(), onSecondary = onSecondary.a(),
        secondaryContainer = secondaryContainer.a(), onSecondaryContainer = onSecondaryContainer.a(),
        tertiary = tertiary.a(), onTertiary = onTertiary.a(),
        tertiaryContainer = tertiaryContainer.a(), onTertiaryContainer = onTertiaryContainer.a(),
        background = background.a(), onBackground = onBackground.a(),
        surface = surface.a(), onSurface = onSurface.a(),
        surfaceVariant = surfaceVariant.a(), onSurfaceVariant = onSurfaceVariant.a(),
        surfaceTint = surfaceTint.a(),
        inverseSurface = inverseSurface.a(), inverseOnSurface = inverseOnSurface.a(),
        error = error.a(), onError = onError.a(),
        errorContainer = errorContainer.a(), onErrorContainer = onErrorContainer.a(),
        outline = outline.a(), outlineVariant = outlineVariant.a(),
        surfaceBright = surfaceBright.a(), surfaceDim = surfaceDim.a(),
        surfaceContainer = surfaceContainer.a(),
        surfaceContainerHigh = surfaceContainerHigh.a(),
        surfaceContainerHighest = surfaceContainerHighest.a(),
        surfaceContainerLow = surfaceContainerLow.a(),
        surfaceContainerLowest = surfaceContainerLowest.a(),
    )
}
