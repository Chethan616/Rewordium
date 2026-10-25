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
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RewordiumDark = darkColorScheme(
    primary = Color(0xFF9CCAFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF0F4A86),
    onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF394659),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD8BDF6),
    tertiaryContainer = Color(0xFF52406C),
    onTertiaryContainer = Color(0xFFF0DBFF),
    background = Color(0xFF101216),
    surface = Color(0xFF101216),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF181A1F),
    surfaceContainer = Color(0xFF1C1E23),
    surfaceContainerHigh = Color(0xFF26282E),
    surfaceContainerHighest = Color(0xFF313339),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
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
    val colors = when {
        dark -> RewordiumDark
        else -> expressiveLightColorScheme().copy(
            primary = Color(0xFF1665C0),
            primaryContainer = Color(0xFFD5E3FF),
            onPrimaryContainer = Color(0xFF001B3D),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = RewordiumMotion,
        shapes = RewordiumShapes,
        typography = RewordiumTypography,
        content = content,
    )
}
