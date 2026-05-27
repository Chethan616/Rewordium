/*
 * Copyright (C) 2026 The ReBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.noxquill.rewordium.keyboard.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.noxquill.rewordium.keyboard.ime.nlp.engine.ContactsLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Transparent trampoline activity presenting a premium, Material 3-styled
 * contacts-permission rationale dialog, then launching the system
 * READ_CONTACTS prompt.
 *
 * Fully Jetpack Compose — no AlertDialog. The activity window is translucent
 * with a dark scrim so it layers cleanly over whatever app (or keyboard) the
 * user was interacting with.
 *
 * On grant: broadcasts [ACTION_RELOAD_CONTACTS] so [FlorisImeService] can
 *   hot-reload contact names without an IME restart.
 * On permanent deny: sets [FlorisPreferenceStore.spelling.contactsPromptDismissed]
 *   so the smartbar prompt stops appearing.
 */
class ContactsPermissionActivity : ComponentActivity() {

    companion object {
        const val ACTION_RELOAD_CONTACTS = "com.noxquill.rewordium.keyboard.RELOAD_CONTACTS"
    }

    private val prefs by FlorisPreferenceStore

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            sendBroadcast(Intent(ACTION_RELOAD_CONTACTS))
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            lifecycleScope.launch { prefs.spelling.contactsPromptDismissed.set(true) }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContactsLoader.hasPermission(this)) {
            sendBroadcast(Intent(ACTION_RELOAD_CONTACTS))
            finish()
            return
        }

        setContent {
            ContactsPermissionScreen(
                onEnable = {
                    requestPermission.launch(Manifest.permission.READ_CONTACTS)
                },
                onDismiss = {
                    finish()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color palette — dark Material 3 surface tones
// ─────────────────────────────────────────────────────────────────────────────
private val ScrimColor = Color(0x99000000) // 60% black
private val DialogSurface = Color(0xFF1C1B1F)
private val DialogSurfaceVariant = Color(0xFF2B2930)
private val OnSurface = Color(0xFFE6E1E5)
private val OnSurfaceVariant = Color(0xFFCAC4D0)
private val AccentTeal = Color(0xFF80CBC4)     // Teal 200 — friendly, trustworthy
private val AccentTealDim = Color(0xFF4DB6AC)
private val PrivacyGreen = Color(0xFF81C784)
private val ButtonPrimary = Color(0xFF80CBC4)
private val ButtonPrimaryContent = Color(0xFF003735)
private val ButtonSecondaryContent = Color(0xFFCAC4D0)

@Composable
private fun ContactsPermissionScreen(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Entrance animation state
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80) // tiny delay so the scrim renders first
        visible = true
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "scrimAlpha",
    )

    // Full-screen scrim — tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimColor.copy(alpha = ScrimColor.alpha / 255f * scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = EaseOutCubic)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(320, easing = EaseOutCubic),
                    ),
        ) {
            DialogCard(
                onEnable = onEnable,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun DialogCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(DialogSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // consume click so scrim-dismiss doesn't fire
            )
            .padding(top = 28.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Icon ──
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentTeal.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Contacts,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Title ──
        Text(
            text = "Use contacts for smarter suggestions",
            color = OnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(10.dp))

        // ── Description ──
        Text(
            text = "ReBoard can suggest names from your contacts while you type and recognise them during swipe input.",
            color = OnSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(14.dp))

        // ── Privacy badge ──
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(PrivacyGreen.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = PrivacyGreen,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Your contacts stay completely on-device",
                color = PrivacyGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Secondary — "Not now"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not now",
                    color = ButtonSecondaryContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Primary — "Enable"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ButtonPrimary)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Enable",
                    color = ButtonPrimaryContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
