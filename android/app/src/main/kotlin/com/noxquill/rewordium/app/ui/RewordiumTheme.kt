package com.noxquill.rewordium.app.ui

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val JadeLight = expressiveLightColorScheme().copy(
    primary = Color(0xFF176B51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6F0D6),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF48645B),
    secondaryContainer = Color(0xFFD0E9DE),
    tertiary = Color(0xFF5C5A83),
    tertiaryContainer = Color(0xFFE4DFFF),
    surface = Color(0xFFFAF8F3),
    background = Color(0xFFFAF8F3),
)

private val JadeDark = darkColorScheme(
    primary = Color(0xFF94D6B7),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005137),
    onPrimaryContainer = Color(0xFFB1F2D2),
    secondary = Color(0xFFB5D0C4),
    secondaryContainer = Color(0xFF304B41),
    tertiary = Color(0xFFC7C1EF),
    tertiaryContainer = Color(0xFF45436A),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B221E),
    surfaceContainerHigh = Color(0xFF252D28),
    surfaceContainerHighest = Color(0xFF303933),
)

private val RewordiumShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

private object RewordiumMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = 420f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = 780f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.86f, stiffness = 260f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1500f)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3600f)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 760f)
}

@Composable
fun RewordiumTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> JadeDark
        else -> JadeLight
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = RewordiumMotion,
        shapes = RewordiumShapes,
        content = content,
    )
}
