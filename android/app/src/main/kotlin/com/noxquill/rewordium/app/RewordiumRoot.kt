package com.noxquill.rewordium.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.noxquill.rewordium.MainActivity
import com.noxquill.rewordium.app.model.toolFor
import com.noxquill.rewordium.app.screens.AiSettingsScreen
import com.noxquill.rewordium.app.screens.AuthScreen
import com.noxquill.rewordium.app.screens.HomeScreen
import com.noxquill.rewordium.app.screens.InfoScreen
import com.noxquill.rewordium.app.screens.JadeMessage
import com.noxquill.rewordium.app.screens.JadeScreen
import com.noxquill.rewordium.app.screens.LicensesScreen
import com.noxquill.rewordium.app.screens.OnboardingScreen
import com.noxquill.rewordium.app.screens.ProScreen
import com.noxquill.rewordium.app.screens.SettingsScreen
import com.noxquill.rewordium.app.screens.ToolsScreen
import com.noxquill.rewordium.app.screens.WritingToolScreen
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.billing.PlayBillingManager
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.data.WritingRepository
import kotlinx.coroutines.launch

@Composable
fun RewordiumRoot(
    activity: MainActivity,
    routeRequest: String?,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPrefs = remember { context.getSharedPreferences("rewordium_app", 0) }
    var onboardingDone by remember { mutableStateOf(appPrefs.getBoolean("onboarding_complete", false)) }
    var destination by rememberSaveable { mutableStateOf(routeRequest ?: "home") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var platformStatus by remember { mutableStateOf(activity.setupStatus()) }
    var busy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authReturnTo by remember { mutableStateOf("settings") }
    var billingState by remember { mutableStateOf(BillingUiState()) }

    LaunchedEffect(routeRequest) {
        routeRequest?.let { route ->
            destination = when (route) {
                "home", "tools", "settings", "jade", "keyboard_settings" -> route
                else -> route
            }
            tab = when (destination) { "home" -> 0; "tools" -> 1; "jade" -> 2; "settings" -> 3; else -> tab }
            onRouteConsumed()
        }
    }

    suspend fun refreshAccount() {
        account = runCatching { RewordiumRepository.refreshAccount(context) }.getOrNull()
    }

    LaunchedEffect(Unit) { refreshAccount() }

    val authListener = remember {
        FirebaseAuth.AuthStateListener { _ -> scope.launch { refreshAccount() } }
    }
    DisposableEffect(activity) {
        runCatching { FirebaseAuth.getInstance().addAuthStateListener(authListener) }
        onDispose { runCatching { FirebaseAuth.getInstance().removeAuthStateListener(authListener) } }
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) platformStatus = activity.setupStatus()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val billingManager = remember(activity) {
        PlayBillingManager(
            activity,
            onState = { billingState = it },
            onPurchaseGranted = { productId, token, purchasedAt, autoRenewing, restored ->
                val granted = RewordiumRepository.activateProFromPlay(productId, token, purchasedAt, autoRenewing, restored)
                if (granted) refreshAccount()
                granted
            },
        )
    }
    LaunchedEffect(billingManager) { billingManager.connect() }
    DisposableEffect(billingManager) { onDispose { billingManager.close() } }

    fun goToAuth(returnTo: String) {
        authReturnTo = returnTo
        authError = null
        destination = "auth"
    }

    fun runAuth(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            authError = null
            runCatching { action() }
                .onSuccess {
                    refreshAccount()
                    destination = authReturnTo
                }
                .onFailure { authError = authMessage(it) }
            busy = false
        }
    }

    fun openLink(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Couldn’t open this link.", Toast.LENGTH_SHORT).show() }
    }

    fun openTool(id: String) {
        destination = if (id == "tools") "tools" else if (id == "jade") "jade" else id
        if (destination == "tools") tab = 1
        if (destination == "jade") tab = 2
    }

    suspend fun requestWriting(text: String, tone: String, language: String): String {
        if (RewordiumRepository.currentUser == null) throw IllegalStateException("Sign in to use Jade and keep your credits with your account.")
        if (account?.isPro != true) {
            val allowed = RewordiumRepository.consumeCredit(context, "writing_${destination}")
            if (!allowed) throw IllegalStateException("You’re out of credits. Explore Rewordium Pro for more room to write.")
            refreshAccount()
        }
        val toolId = if (destination in setOf("paraphraser", "grammar", "summarizer", "translator", "tone", "detector", "document")) destination else toolFor(destination).id
        return WritingRepository.generate(context, toolId, text, tone, language).also { activity.onAiGenerationSuccess() }
    }

    suspend fun requestJade(prompt: String, history: List<JadeMessage>): String {
        if (RewordiumRepository.currentUser == null) throw IllegalStateException("Sign in to chat with Jade and keep your credits with your account.")
        if (account?.isPro != true) {
            val allowed = RewordiumRepository.consumeCredit(context, "jade_chat")
            if (!allowed) throw IllegalStateException("You’re out of credits. Explore Rewordium Pro for more room to write.")
            refreshAccount()
        }
        val conversation = history.map { (if (it.fromJade) "Jade" else "Writer") to it.text }
        return WritingRepository.chat(context, prompt, conversation).also { activity.onAiGenerationSuccess() }
    }

    if (!onboardingDone) {
        OnboardingScreen {
            appPrefs.edit().putBoolean("onboarding_complete", true).apply()
            onboardingDone = true
        }
        return
    }

    if (destination == "auth") {
        AuthScreen(
            onBack = { destination = authReturnTo },
            onSignIn = { email, password -> runAuth { RewordiumRepository.signIn(email, password) } },
            onSignUp = { name, email, password, news -> runAuth { RewordiumRepository.signUp(name, email, password, news) } },
            onGoogleToken = { token -> runAuth { RewordiumRepository.signInWithGoogleIdToken(token) } },
            onResetPassword = { email -> runAuth { RewordiumRepository.sendPasswordReset(email) } },
            busy = busy,
            error = authError,
        )
        return
    }

    if (destination in setOf("home", "tools", "jade", "settings")) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val labels = listOf("Home", "Write", "Jade", "Settings")
                    val icons = listOf(Icons.Filled.Home, Icons.Filled.EditNote, Icons.Filled.AutoAwesome, Icons.Filled.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = {
                                tab = index
                                destination = when (index) { 0 -> "home"; 1 -> "tools"; 2 -> "jade"; else -> "settings" }
                            },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                when (destination) {
                    "home" -> HomeScreen(
                        account = account,
                        platformStatus = platformStatus,
                        onOpenTool = ::openTool,
                        onOpenSettings = activity::openAccessibilitySettings,
                        onOpenPro = { destination = "pro" },
                    )
                    "tools" -> ToolsScreen(::openTool)
                    "jade" -> JadeScreen(::requestJade)
                    "settings" -> SettingsScreen(
                        account = account,
                        status = platformStatus,
                        onSignIn = { goToAuth("settings") },
                        onSignOut = { RewordiumRepository.signOut(context); account = null },
                        onDelete = {
                            scope.launch {
                                runCatching { RewordiumRepository.deleteAccount() }
                                    .onSuccess { RewordiumRepository.signOut(context); account = null; Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, authMessage(it), Toast.LENGTH_LONG).show() }
                            }
                        },
                        onPro = { destination = "pro" },
                        onKeyboard = activity::openKeyboardPreferences,
                        onAccessibility = activity::openAccessibilitySettings,
                        onEnableKeyboard = activity::openKeyboardActivationSettings,
                        onChooseKeyboard = activity::chooseKeyboard,
                        onAiSettings = { destination = "ai_settings" },
                        onLicenses = { destination = "licenses" },
                        onPersonas = { destination = "personas" },
                        onPrivacy = { destination = "privacy" },
                        onTerms = { destination = "terms" },
                    )
                }
            }
        }
        return
    }

    when (destination) {
        "pro" -> ProScreen(
            account = account,
            billing = billingState,
            onBack = { destination = "settings" },
            onSignIn = { goToAuth("pro") },
            onPurchase = { id ->
                if (account == null) goToAuth("pro") else billingManager.purchase(activity, id)
            },
            onRestore = {
                if (account == null) goToAuth("pro") else billingManager.restorePurchases()
            },
        )
        "ai_settings" -> AiSettingsScreen(onBack = { destination = "settings" })
        "personas" -> com.noxquill.rewordium.app.screens.PersonaScreen(onBack = { destination = "settings" })
        "licenses" -> LicensesScreen(onBack = { destination = "settings" }, onOpenLink = ::openLink)
        "privacy" -> InfoScreen("Privacy policy", onBack = { destination = "settings" }, onOpenLink = ::openLink)
        "terms" -> InfoScreen("Terms of service", onBack = { destination = "settings" }, onOpenLink = ::openLink)
        "keyboard_settings" -> {
            activity.openKeyboardPreferences()
            destination = "settings"
        }
        else -> WritingToolScreen(
            toolId = destination,
            onBack = { destination = "tools"; tab = 1 },
            onUpgrade = { destination = "pro" },
            onGenerate = ::requestWriting,
        )
    }
}

private fun authMessage(error: Throwable): String = when {
    error.message?.contains("email", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("password", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("network", ignoreCase = true) == true -> "Check your connection and try again."
    else -> error.message ?: "Something went wrong. Please try again."
}
