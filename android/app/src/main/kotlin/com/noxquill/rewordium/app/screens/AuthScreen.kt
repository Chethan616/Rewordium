package com.noxquill.rewordium.app.screens

import android.app.Activity
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.noxquill.rewordium.R
import com.noxquill.rewordium.app.ui.AvatarCarousel
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.LegalLinksText
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialShapes
import com.noxquill.rewordium.app.ui.ShapeMorph
import com.noxquill.rewordium.app.ui.avatarName
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.data.AppSettings

/**
 * The sign-in gate shown after onboarding and after signing out. It has no
 * back button: Jade, credits and the keyboard's AI all need an account.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    notice: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, Boolean) -> Unit,
    onGoogleToken: (String) -> Unit,
    onResetPassword: (String) -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val focus = LocalFocusManager.current
    val avatar by AppSettings.avatar.collectAsState()
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var newsOptIn by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = if (createAccount) password.length >= 6 else password.isNotEmpty()
    val canSubmit = !busy && emailValid && passwordValid && (!createAccount || (name.isNotBlank() && agreed))
    val shownError = googleError ?: error

    val shake = remember { Animatable(0f) }
    LaunchedEffect(shownError) {
        if (shownError != null) {
            haptics.reject()
            for (offset in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(offset, spring(stiffness = Spring.StiffnessHigh))
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
            }.getOrNull().orEmpty()
            if (token.isNotBlank()) onGoogleToken(token) else googleError = "Google sign-in didn’t finish. Try again."
        }
    }

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        haptics.send()
        googleError = null
        if (createAccount) onSignUp(name, email, password, newsOptIn) else onSignIn(email, password)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.enterStagger(0)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("REWORDIUM", style = Heading.Poster.display(26.sp))
            }
            Spacer(Modifier.height(12.dp))
            // Signing in sits on a sunny shape; creating an account springs into a flower.
            val heroColor by animateColorAsState(
                if (createAccount) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                label = "auth-hero-colour",
            )
            ShapeMorph(
                shape = if (createAccount) MaterialShapes.Flower else MaterialShapes.Sunny,
                color = heroColor,
                modifier = Modifier.size(170.dp).enterStagger(1),
            ) {
                BotAvatar(
                    type = avatar,
                    mood = when {
                        busy -> BotMood.Working
                        shownError != null -> BotMood.Sad
                        createAccount -> BotMood.Happy
                        else -> BotMood.Idle
                    },
                    size = 112.dp,
                    label = "${avatarName(avatar)}, your avatar",
                )
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = createAccount,
                transitionSpec = { (slideInVertically { it / 3 } + fadeIn()).togetherWith(slideOutVertically { -it / 3 } + fadeOut()) },
                label = "auth-title",
                modifier = Modifier.enterStagger(2),
            ) { creating ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (creating) "Make yourself at home" else "Welcome back", style = Heading.Geometric.display(34.sp), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (creating) "Optional: an account keeps its own included AI uses with you on any phone."
                        else "Optional: sign in to use your Rewordium account on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // Sign in / Create account
            Row(Modifier.fillMaxWidth().enterStagger(3), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                ToggleButton(
                    checked = !createAccount,
                    onCheckedChange = { haptics.tick(); createAccount = false; googleError = null },
                    colors = com.noxquill.rewordium.app.ui.segmentColors(),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Sign in") }
                ToggleButton(
                    checked = createAccount,
                    onCheckedChange = { haptics.tick(); createAccount = true; googleError = null },
                    colors = com.noxquill.rewordium.app.ui.segmentColors(),
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Create account") }
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.fillMaxWidth().enterStagger(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(createAccount, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your name") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = MaterialTheme.shapes.large,
                        )
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Pick your avatar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(avatarName(avatar), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                AvatarCarousel(selected = avatar, onSelect = { AppSettings.setAvatar(it) }, modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    isError = email.isNotBlank() && !emailValid,
                    supportingText = if (email.isNotBlank() && !emailValid) { { Text("That doesn’t look like an email address") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    supportingText = if (createAccount) { { Text("At least 6 characters") } } else null,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = MaterialTheme.shapes.large,
                )

                AnimatedVisibility(createAccount, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        CheckRow(checked = agreed, onChange = { agreed = it }) {
                            LegalLinksText("I’ve read and agree to the", onTerms = onTerms, onPrivacy = onPrivacy, textAlign = TextAlign.Start)
                        }
                        CheckRow(checked = newsOptIn, onChange = { newsOptIn = it }) {
                            Text("Email me occasional Rewordium news (optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                AnimatedVisibility(shownError != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() },
                    ) {
                        Text(shownError.orEmpty(), Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                AnimatedVisibility(notice != null && shownError == null) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.MarkEmailRead, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(notice.orEmpty(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    enabled = canSubmit,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("One moment…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(if (createAccount) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (createAccount && !agreed && name.isNotBlank() && emailValid && passwordValid) {
                    Text("Tick the box above to agree to the Terms and Privacy Policy.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                if (!createAccount) {
                    TextButton(
                        onClick = { haptics.click(); onResetPassword(email) },
                        enabled = !busy && emailValid,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(if (emailValid) "Forgot your password?" else "Forgot your password? Enter your email first") }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    haptics.click()
                    googleError = null
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(26.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("G", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black) }
                }
                Spacer(Modifier.width(10.dp))
                Text("Continue with Google", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(16.dp))
            LegalLinksText("By continuing, you agree to Rewordium’s", onTerms = onTerms, onPrivacy = onPrivacy)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { haptics.toggle(it); onChange(it) }).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
