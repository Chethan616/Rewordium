package com.noxquill.rewordium.app.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.noxquill.rewordium.R
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

private val accountMascots = listOf("flower", "cloud", "triangle", "droid")

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, Boolean) -> Unit,
    onGoogleToken: (String) -> Unit,
    onResetPassword: (String) -> Unit,
    mascotType: String,
    onMascotSelected: (String) -> Unit,
    busy: Boolean,
    error: String?,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    var createAccount by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newsOptIn by remember { mutableStateOf(false) }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
            }.getOrNull().orEmpty()
            if (token.isNotBlank()) onGoogleToken(token)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    androidx.compose.material3.IconButton(onClick = { haptics.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                } else Spacer(Modifier.size(48.dp))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("rewordium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                BotAvatar(type = mascotType, mood = BotMood.Thinking, modifier = Modifier.padding(10.dp), size = 72.dp, label = "Rewordium mascot")
            }
            Spacer(Modifier.height(16.dp))
            Text(if (createAccount) "Make yourself at home" else "Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                if (createAccount) "Your words and settings will be ready wherever you write." else "Pick up right where your words left off.",
                modifier = Modifier.fillMaxWidth(0.88f).padding(top = 7.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (createAccount) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose your companion", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            accountMascots.forEach { type ->
                                val selected = type == mascotType
                                Surface(
                                    onClick = { haptics.click(); onMascotSelected(type) },
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        BotAvatar(type = type, mood = if (selected) BotMood.Happy else BotMood.Idle, size = 40.dp, interactive = false, still = true, label = "$type mascot")
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newsOptIn, onCheckedChange = { haptics.toggle(it); newsOptIn = it })
                        Text("Email me occasional Rewordium news", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trimStart() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.large,
                )
                if (error != null) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(error, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = {
                        haptics.click()
                        if (createAccount) onSignUp(name, email, password, newsOptIn) else onSignIn(email, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !busy && email.isNotBlank() && password.isNotBlank() && (!createAccount || name.isNotBlank()),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Text("  Working…", style = MaterialTheme.typography.titleMedium)
                    }
                    else Text(if (createAccount) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
                }
                if (!createAccount) {
                    TextButton(onClick = { haptics.click(); if (email.isNotBlank()) onResetPassword(email) }) {
                        Text("Forgot your password?")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    haptics.click()
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Continue with Google", style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (createAccount) "Already have an account?" else "New to Rewordium?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { haptics.tick(); createAccount = !createAccount }) {
                    Text(if (createAccount) "Sign in" else "Create account")
                }
            }
            Text("By continuing, you agree to our terms and privacy policy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
        }
    }
}
