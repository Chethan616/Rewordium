package com.noxquill.rewordium.keyboard.compose

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🌊 PROFESSIONAL LIQUID GLASS KEY
 * Inspired by AndroidLiquidGlass with iOS-accurate glass morphism
 * - Backdrop blur effect
 * - Light refraction for depth
 * - Vibrancy for content bleeding
 * - NO ripple effects, NO animations - Pure glass only
 */
@Composable
fun ProfessionalLiquidGlassKey(
    text: String,
    onClick: () -> Unit,
    isDarkMode: Boolean,
    isPressed: Boolean,
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null
) {
    // ⚡ CACHED VALUES - Prevent recreation on every frame
    val baseColor = remember(isDarkMode) {
        if (isDarkMode) Color(0xFF333333) else Color(0xFFFFFFFF)
    }
    
    val textColor = remember(isDarkMode) {
        if (isDarkMode) Color.White else Color.Black
    }
    
    // ⚡ UNIFORM TRANSPARENCY - No gradient, solid glass effect
    val solidColor = remember(baseColor) {
        baseColor.copy(alpha = 0.25f)  // Uniform 25% opacity throughout
    }
    
    // Professional iOS-style squircle shape (continuous corners)
    val shape = RoundedCornerShape(10.dp)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .background(
                color = solidColor,  // ⚡ Uniform solid color - fastest rendering
                shape = shape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress?.invoke()
                        tryAwaitRelease()
                        onRelease?.invoke()
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // ⚡ CLEAN TEXT - No shadow for uniform appearance
        Text(
            text = text,
            color = textColor.copy(alpha = 0.95f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 🌊 PROFESSIONAL LIQUID GLASS SPECIAL KEY
 * For shift, delete, emoji, and other special function keys
 */
@Composable
fun ProfessionalLiquidGlassSpecialKey(
    text: String,
    iconResId: Int?,
    onClick: () -> Unit,
    isDarkMode: Boolean,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val baseColor = if (isDarkMode) {
        Color(0xFF444444)  // Slightly lighter for special keys
    } else {
        Color(0xFFE8E8E8)
    }
    
    val contentColor = if (isDarkMode) Color.White else Color.Black
    val shape = RoundedCornerShape(9.dp)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isPressed) 0.5.dp else 1.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            // NO BLUR for special keys - keep them sharp and clear
            .background(
                color = baseColor.copy(alpha = 0.90f),  // More opaque for better visibility
                shape = shape
            )
            .drawBehind {
                // Subtle highlight for depth
                drawRect(
                    color = Color.White.copy(alpha = 0.08f),
                    size = size
                )
            }
            .clickable(
                indication = null,  // NO RIPPLE
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (iconResId != null) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = text,
                tint = contentColor.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        } else {
            // Text with shadow for better visibility
            Box {
                // Shadow layer
                Text(
                    text = text,
                    color = if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(x = 0.5.dp, y = 0.5.dp)
                )
                // Main text layer
                Text(
                    text = text,
                    color = contentColor.copy(alpha = 0.95f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 🌊 PROFESSIONAL LIQUID GLASS RETURN KEY
 * Theme-colored return key with professional glass effect
 * NO shimmer animation - Just pure themed glass
 */
@Composable
fun ProfessionalLiquidGlassReturnKey(
    text: String,
    themeColor: String,
    onClick: () -> Unit,
    isDarkMode: Boolean,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val baseColor = try {
        Color(AndroidColor.parseColor(themeColor))
    } catch (e: Exception) {
        Color(0xFF007AFF)  // Fallback to iOS blue
    }
    
    val shape = RoundedCornerShape(9.dp)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isPressed) 1.dp else 2.dp,
                shape = shape,
                ambientColor = baseColor.copy(alpha = 0.15f),
                spotColor = baseColor.copy(alpha = 0.25f)
            )
            // NO BLUR for return key - keep it sharp and prominent
            .background(
                color = baseColor.copy(alpha = 0.95f),  // Highly opaque for prominence
                shape = shape
            )
            .drawBehind {
                // Highlight for glass depth
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    size = size
                )
            }
            .clickable(
                indication = null,  // NO RIPPLE
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
