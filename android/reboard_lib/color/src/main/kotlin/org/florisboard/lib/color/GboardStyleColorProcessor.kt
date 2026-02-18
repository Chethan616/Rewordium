/*
 * Copyright (C) 2025 The ReBoard Contributors
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

package org.florisboard.lib.color

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct
import kotlin.math.pow

/**
 * Centralized color processor for a Gboard-like dark theme.
 *
 * No fixed colors are used. All output colors are derived from the active
 * Material You [ColorScheme] by compressing chroma and remapping tone.
 *
 * This keeps the same wallpaper/system hue family, but with lower intensity:
 * - background: dark, lightly tinted
 * - regular keys: slightly lighter than background with subtle tint
 * - accent keys: a bit stronger tint than regular keys
 */
object GboardStyleColorProcessor {

    private const val SURFACE_MAX_CHROMA = 16.0
    private const val ACCENT_MAX_CHROMA = 24.0
    private const val PRESSED_EXTRA_TONE = 15.0
    private const val MIN_TONE = 8.0
    private const val MAX_TONE = 50.0

    fun process(scheme: ColorScheme): ColorScheme {
        val backgroundHct = Hct.fromInt(scheme.background.toArgb())
        val keyHct = Hct.fromInt(scheme.surfaceBright.toArgb())
        val accentHct = Hct.fromInt(scheme.primaryContainer.toArgb())

        // Blend much more of the system accent hue into everything
        val sharedHue = blendHue(backgroundHct.hue, accentHct.hue, 0.70)

        // Make keys and background significantly lighter/more colorful (tinted)
        // User requested approx 0.5% darker keys compared to previous iteration
        // Previous: MIN_TONE + 10.0
        // New: MIN_TONE + 9.5 and slightly less chroma for that "303538" look
        val baseKeyTone = ((keyHct.tone * 0.40) + (accentHct.tone * 0.40) + (backgroundHct.tone * 0.20))
            .coerceIn(MIN_TONE + 9.5, MAX_TONE)
        
        val targetKeyTone = baseKeyTone
        val targetBackgroundTone = (targetKeyTone - 8.0).coerceIn(MIN_TONE, MAX_TONE - 4.0)
        val targetAccentTone = (targetKeyTone + 8.0).coerceIn(MIN_TONE + 4.0, MAX_TONE)
        
        // Ensure pressed state is VERY LIGHT (nearly white/pastel) to maintain tint visibility
        val targetPressedTone = 85.0 

        var result = scheme.copy(
            background = transformSurfaceRole(
                color = scheme.background,
                targetHue = sharedHue,
                targetTone = targetBackgroundTone,
                toneMix = 0.90,
                chromaScale = 1.0, 
                chromaCap = SURFACE_MAX_CHROMA + 8.0, // Allow more flavor
                minChroma = 6.0, // Force minimum tint
            ),
            surfaceDim = transformSurfaceRole(
                color = scheme.surfaceDim,
                targetHue = sharedHue,
                targetTone = targetBackgroundTone - 2.0,
                toneMix = 0.90,
                chromaScale = 1.0,
                chromaCap = SURFACE_MAX_CHROMA + 8.0,
                minChroma = 4.0,
            ),
            surfaceContainerLowest = transformSurfaceRole(
                color = scheme.surfaceContainerLowest,
                targetHue = sharedHue,
                targetTone = targetBackgroundTone + 2.0,
                toneMix = 0.90,
                chromaScale = 1.0,
                chromaCap = SURFACE_MAX_CHROMA + 8.0,
                minChroma = 4.0,
            ),

            // Base key color (Normal keys)
            // Reduced chroma scale slightly (1.0 -> 0.9) to match Gboard "grayer" look (303538 vs 30373d)
            surfaceBright = transformSurfaceRole(
                color = scheme.surfaceBright,
                targetHue = sharedHue,
                targetTone = targetKeyTone,
                toneMix = 0.90, // Increased mix
                chromaScale = 0.9, // Slightly desaturated
                chromaCap = SURFACE_MAX_CHROMA + 8.0, 
                minChroma = 6.0,
            ),
            
            // Pressed state for normal keys (needs to keep tint!)
            surfaceVariant = transformSurfaceRole(
                color = scheme.surfaceVariant,
                targetHue = sharedHue,
                targetTone = targetPressedTone,
                toneMix = 1.0, // Force our tone completely
                chromaScale = 1.0, 
                chromaCap = ACCENT_MAX_CHROMA, 
                minChroma = 12.0,
            ),
            inverseSurface = transformSurfaceRole(
                color = scheme.inverseSurface,
                targetHue = sharedHue,
                targetTone = (targetKeyTone + 2.5).coerceIn(MIN_TONE, MAX_TONE),
                toneMix = 0.50,
                chromaScale = 0.40,
                chromaCap = SURFACE_MAX_CHROMA + 0.7,
                minChroma = 4.0,
            ),            
            
            // Accent Color (Active State: Enter, Backspace)
            primaryContainer = transformAccentRole(
                color = scheme.primaryContainer,
                fallbackHue = accentHct.hue,
                targetTone = targetAccentTone,
                toneMix = 0.85,
                chromaScale = 0.55, // Boost chroma
                chromaCap = 25.0,
            ),
            
            // Accent Pressed State (Shift, ?123 on press)
            // CRITICAL FIX: Ensure this maps to a very light, saturated color
            primary = transformAccentRole(
                color = scheme.primary,
                fallbackHue = accentHct.hue,
                targetTone = 90.0, // Extremely light tone
                toneMix = 1.0, 
                chromaScale = 1.5, // Oversaturate
                chromaCap = 50.0, // Allow high chroma
            ),

            inversePrimary = transformAccentRole(
                color = scheme.inversePrimary,
                fallbackHue = accentHct.hue,
                targetTone = (targetAccentTone + 2.0).coerceIn(MIN_TONE, MAX_TONE),
                toneMix = 0.50,
                chromaScale = 0.20,
                chromaCap = ACCENT_MAX_CHROMA,
            ),
            secondary = transformAccentRole(
                color = scheme.secondary,
                fallbackHue = accentHct.hue,
                targetTone = targetAccentTone,
                toneMix = 0.50,
                chromaScale = 0.20,
                chromaCap = ACCENT_MAX_CHROMA,
            ),
            secondaryContainer = transformAccentRole(
                color = scheme.secondaryContainer,
                fallbackHue = accentHct.hue,
                targetTone = targetAccentTone,
                toneMix = 0.50,
                chromaScale = 0.20,
                chromaCap = ACCENT_MAX_CHROMA,
            ),
            tertiary = transformAccentRole(
                color = scheme.tertiary,
                fallbackHue = accentHct.hue,
                targetTone = targetAccentTone,
                toneMix = 0.48,
                chromaScale = 0.18,
                chromaCap = ACCENT_MAX_CHROMA,
            ),
            tertiaryContainer = transformAccentRole(
                color = scheme.tertiaryContainer,
                fallbackHue = accentHct.hue,
                targetTone = targetAccentTone,
                toneMix = 0.48,
                chromaScale = 0.18,
                chromaCap = ACCENT_MAX_CHROMA,
            ),
        )
        result = enforceContrast(result)
        return result
    }

    private fun transformSurfaceRole(
        color: Color,
        targetHue: Double,
        targetTone: Double,
        toneMix: Double,
        chromaScale: Double,
        chromaCap: Double,
        minChroma: Double = 0.0, // Added minChroma parameter
    ): Color {
        val hct = Hct.fromInt(color.toArgb())
        // Boost chroma if it's too low, to add "flavor"
        val efficientChroma = maxOf(hct.chroma, minChroma)
        
        return hctToColor(
            hue = blendHue(hct.hue, targetHue, 0.40), // Increased hue blend strength
            chroma = minOf(efficientChroma * chromaScale, chromaCap),
            tone = lerp(hct.tone, targetTone, toneMix).coerceIn(MIN_TONE, MAX_TONE),
        )
    }

    private fun transformAccentRole(
        color: Color,
        fallbackHue: Double,
        targetTone: Double,
        toneMix: Double,
        chromaScale: Double,
        chromaCap: Double,
    ): Color {
        val hct = Hct.fromInt(color.toArgb())
        return hctToColor(
            hue = blendHue(hct.hue, fallbackHue, 0.18),
            chroma = minOf(hct.chroma * chromaScale, chromaCap),
            tone = lerp(hct.tone, targetTone, toneMix).coerceIn(MIN_TONE, MAX_TONE),
        )
    }

    // ── Contrast enforcement ─────────────────────────────────────────────────

    private fun enforceContrast(scheme: ColorScheme): ColorScheme {
        val fixedOnSurface = ensureMinContrast(scheme.surface, scheme.onSurface)
        val fixedOnPrimary = ensureMinContrast(scheme.primaryContainer, scheme.onPrimaryContainer)
        val fixedOnPrimaryPressed = ensureMinContrast(scheme.primary, scheme.onPrimary)
        return if (
            fixedOnSurface != scheme.onSurface ||
            fixedOnPrimary != scheme.onPrimaryContainer ||
            fixedOnPrimaryPressed != scheme.onPrimary
        ) {
            scheme.copy(
                onSurface = fixedOnSurface,
                onPrimaryContainer = fixedOnPrimary,
                onPrimary = fixedOnPrimaryPressed,
            )
        } else {
            scheme
        }
    }

    /**
     * If the contrast ratio between [bg] and [fg] is below 4.5:1,
     * nudge [fg] tone until the requirement is met.
     */
    private fun ensureMinContrast(
        bg: Color,
        fg: Color,
        minRatio: Double = 4.5,
    ): Color {
        if (contrastRatio(bg, fg) >= minRatio) return fg

        val fgHct = Hct.fromInt(fg.toArgb())
        val bgTone = Hct.fromInt(bg.toArgb()).tone
        var fgTone = fgHct.tone

        // Dark bg → push fg lighter; light bg → push fg darker.
        val step = if (bgTone < 50.0) 1.0 else -1.0

        repeat(100) {
            fgTone = (fgTone + step).coerceIn(0.0, 100.0)
            val candidate = hctToColor(fgHct.hue, fgHct.chroma, fgTone)
            if (contrastRatio(bg, candidate) >= minRatio) return candidate
        }
        return fg
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private fun hctToColor(hue: Double, chroma: Double, tone: Double): Color =
        Color(Hct.from(hue, chroma, tone).toInt())

    private fun blendHue(fromHue: Double, toHue: Double, t: Double): Double {
        val delta = ((toHue - fromHue + 540.0) % 360.0) - 180.0
        return (fromHue + delta * t + 360.0) % 360.0
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker  = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 2.1 relative luminance from linear sRGB. */
    private fun relativeLuminance(color: Color): Double {
        fun lin(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(color.red) + 0.7152 * lin(color.green) + 0.0722 * lin(color.blue)
    }
}

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * Creates a Gboard-style post-processed dark [ColorScheme] from the system accent color.
 *
 * Calls [com.materialkolor.dynamicColorScheme] internally, then post-processes through
 * [GboardStyleColorProcessor]. The result is less saturated than raw Monet, closer to
 * Gboard's dark theme, but still adapts to wallpaper changes.
 *
 * @param accentColor The resolved system accent or custom accent color.
 * @param flags [MaterialYouFlags] controlling palette style, contrast, and spec version.
 */
fun gboardStyleDarkColorScheme(
    accentColor: Color,
    flags: MaterialYouFlags = MaterialYouFlags(),
): ColorScheme {
    val (style, contrast, specVersion) = flags
    val raw = dynamicColorScheme(
        primary = accentColor,
        isDark = true,
        style = style,
        contrastLevel = contrast.value,
        specVersion = specVersion,
    )
    return GboardStyleColorProcessor.process(raw)
}

/**
 * Remembers a Gboard-style dark [ColorScheme] that is computed once and cached.
 *
 * Recomputes only when [flags] or the system accent color changes — never on plain
 * recomposition.
 *
 * @param flags [MaterialYouFlags] controlling palette style, contrast, and spec version.
 * @return A wallpaper-adaptive, desaturated dark [ColorScheme].
 */
@Composable
fun rememberRewordiumColorScheme(
    flags: MaterialYouFlags = MaterialYouFlags(),
): ColorScheme {
    val accentColor = systemAccentOrDefault(Color.Unspecified)
    return remember(accentColor, flags) {
        gboardStyleDarkColorScheme(accentColor, flags)
    }
}
