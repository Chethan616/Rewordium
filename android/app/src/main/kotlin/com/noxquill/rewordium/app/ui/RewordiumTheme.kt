package com.noxquill.rewordium.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val RewordiumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val RewordiumTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private object RewordiumMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 420f)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 900f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 240f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

@Composable
fun RewordiumTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) RewordiumDark else RewordiumLight
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = RewordiumMotion,
        shapes = RewordiumShapes,
        typography = RewordiumTypography,
        content = content,
    )
}
