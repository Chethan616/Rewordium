package com.noxquill.rewordium.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.noxquill.rewordium.MainActivity
import com.noxquill.rewordium.app.model.toolFor
import com.noxquill.rewordium.app.model.writingTools
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
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.haptics.rememberHaptics
import com.noxquill.rewordium.app.ui.avatar.BotFlight
import com.noxquill.rewordium.app.ui.avatar.BotFlightLayer
import com.noxquill.rewordium.app.ui.avatar.LocalBotFlight
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.billing.PlayBillingManager
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.data.WritingRepository
import com.noxquill.rewordium.util.KeyboardConstants
import kotlinx.coroutines.launch

@Composable
fun RewordiumRoot(
    activity: MainActivity,
    routeRequest: String?,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(KeyboardConstants.PREFS_NAME, 0) }
    val haptics = rememberHaptics { prefs.getBoolean(KeyboardConstants.KEY_HAPTIC_FEEDBACK, true) }
    CompositionLocalProvider(LocalHaptics provides haptics) {
        RewordiumRootContent(activity, routeRequest, onRouteConsumed)
    }
}

@Composable
private fun RewordiumRootContent(
    activity: MainActivity,
    routeRequest: String?,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPrefs = remember { context.getSharedPreferences("rewordium_app", 0) }
    var onboardingDone by remember { mutableStateOf(appPrefs.getBoolean("onboarding_complete", false)) }
    val navController = rememberNavController()
    val botFlight = remember { BotFlight() }
    val currentEntry by navController.currentBackStackEntryAsState()
    var openToolRoute by remember { mutableStateOf<String?>(null) }
    var activeToolId by rememberSaveable { mutableStateOf("paraphraser") }
    var mascotType by remember { mutableStateOf(appPrefs.getString("mascot_type", "flower") ?: "flower") }
    var recentToolIds by remember {
        mutableStateOf(
            appPrefs.getString("recent_tool_ids", "")
                .orEmpty().split(',').filter(String::isNotBlank).distinct().take(5),
        )
    }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var platformStatus by remember { mutableStateOf(activity.setupStatus()) }
    var busy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var billingState by remember { mutableStateOf(BillingUiState()) }

    LaunchedEffect(currentEntry) {
        val toolId = currentEntry
            ?.takeIf { it.destination.route == "tool/{toolId}" }
            ?.arguments?.getString("toolId")
        val previousToolId = openToolRoute
        if (toolId != null && toolId != previousToolId) botFlight.chatOpened(toolId)
        if (previousToolId != null && toolId == null && currentEntry?.destination?.route == "home") {
            botFlight.toHome(previousToolId)
        }
        openToolRoute = toolId
    }

    fun recordRecent(id: String) {
        if (writingTools.none { it.id == id }) return
        recentToolIds = (listOf(id) + recentToolIds.filterNot { it == id }).take(5)
        appPrefs.edit().putString("recent_tool_ids", recentToolIds.joinToString(",")).apply()
    }

    fun navigateTo(id: String) {
        when (id) {
            "home", "tools", "jade", "settings", "settings/access", "pro", "auth", "ai_settings", "licenses", "privacy", "terms", "personas" ->
                navController.navigate(id)
            "keyboard_settings" -> navController.navigate("settings/access")
            else -> {
                activeToolId = id
                recordRecent(id)
                navController.navigate("tool/$id")
            }
        }
    }

    LaunchedEffect(routeRequest) {
        routeRequest?.let { requested ->
            when (requested) {
                "home", "tools", "settings", "jade", "pro", "ai_settings", "licenses", "privacy", "terms", "personas" -> navigateTo(requested)
                "keyboard_settings" -> navigateTo("settings/access")
                "settings/access" -> navigateTo("settings/access")
                "auth" -> navigateTo("auth")
                else -> navigateTo(requested)
            }
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
                if (granted) scope.launch { refreshAccount() }
                granted
            },
        )
    }
    LaunchedEffect(billingManager) { billingManager.connect() }
    DisposableEffect(billingManager) { onDispose { billingManager.close() } }

    fun goToAuth() {
        authError = null
        navigateTo("auth")
    }

    fun runAuth(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            authError = null
            runCatching { action() }
                .onSuccess {
                    refreshAccount()
                    navController.popBackStack()
                }
                .onFailure { authError = authMessage(it) }
            busy = false
        }
    }

    fun openLink(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Couldn’t open this link.", Toast.LENGTH_SHORT).show() }
    }

    suspend fun requestWriting(text: String, tone: String, language: String): String {
        if (RewordiumRepository.currentUser == null) throw IllegalStateException("Sign in to use Jade and keep your credits with your account.")
        if (account?.isPro != true) {
            val allowed = RewordiumRepository.consumeCredit(context, "writing_$activeToolId")
            if (!allowed) throw IllegalStateException("You’re out of credits. Explore Rewordium Pro for more room to write.")
            refreshAccount()
        }
        val toolId = if (activeToolId in setOf("paraphraser", "grammar", "summarizer", "translator", "tone", "detector", "document")) activeToolId else toolFor(activeToolId).id
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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            OnboardingScreen {
                appPrefs.edit().putBoolean("onboarding_complete", true).apply()
                onboardingDone = true
            }
        }
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize().safeDrawingPadding()) {
      CompositionLocalProvider(LocalBotFlight provides botFlight) {
      NavHost(
          navController = navController,
          startDestination = "home",
          enterTransition = { slideInHorizontally(NavSlide) { it } },
          exitTransition = { slideOutHorizontally(NavSlide) { -it / 4 } },
          popEnterTransition = { slideInHorizontally(NavSlide) { -it / 4 } },
          popExitTransition = { slideOutHorizontally(NavSlide) { it } },
          predictivePopEnterTransition = { slideInHorizontally(NavSlide) { -it / 4 } },
          predictivePopExitTransition = { slideOutHorizontally(NavSlide) { it } },
      ) {
        composable("home") {
            HomeScreen(
                account = account,
                platformStatus = platformStatus,
                recentTools = recentToolIds.mapNotNull { id -> writingTools.firstOrNull { it.id == id } },
                mascotType = mascotType,
                onOpenTool = ::navigateTo,
                onOpenRecentTool = { id ->
                    activeToolId = id
                    navController.navigate("tool/$id")
                },
                onOpenSettings = { navigateTo("settings") },
                onOpenJade = { navigateTo("jade") },
                onOpenPro = { navigateTo("pro") },
            )
        }
        composable("tools") {
            ToolsScreen(onOpenTool = ::navigateTo, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                account = account, status = platformStatus,
                onBack = { navController.popBackStack() },
                mascotType = mascotType,
                onMascotSelected = { type -> mascotType = type; appPrefs.edit().putString("mascot_type", type).apply() },
                onSignIn = ::goToAuth,
                onSignOut = { RewordiumRepository.signOut(context); account = null },
                onDelete = {
                    scope.launch {
                        runCatching { RewordiumRepository.deleteAccount() }
                            .onSuccess { RewordiumRepository.signOut(context); account = null; Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, authMessage(it), Toast.LENGTH_LONG).show() }
                    }
                },
                onPro = { navigateTo("pro") },
                onKeyboard = activity::openKeyboardPreferences,
                onAccessibility = activity::openAccessibilitySettings,
                onEnableKeyboard = activity::openKeyboardActivationSettings,
                onChooseKeyboard = activity::chooseKeyboard,
                onAiSettings = { navigateTo("ai_settings") },
                onLicenses = { navigateTo("licenses") },
                onPersonas = { navigateTo("personas") },
                onPrivacy = { navigateTo("privacy") },
                onTerms = { navigateTo("terms") },
            )
        }
        composable("settings/access") {
            SettingsScreen(
                account = account, status = platformStatus,
                onBack = { navController.popBackStack() }, initialTab = 1,
                mascotType = mascotType,
                onMascotSelected = { type -> mascotType = type; appPrefs.edit().putString("mascot_type", type).apply() },
                onSignIn = ::goToAuth,
                onSignOut = { RewordiumRepository.signOut(context); account = null },
                onDelete = {
                    scope.launch {
                        runCatching { RewordiumRepository.deleteAccount() }
                            .onSuccess { RewordiumRepository.signOut(context); account = null; Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, authMessage(it), Toast.LENGTH_LONG).show() }
                    }
                },
                onPro = { navigateTo("pro") },
                onKeyboard = activity::openKeyboardPreferences,
                onAccessibility = activity::openAccessibilitySettings,
                onEnableKeyboard = activity::openKeyboardActivationSettings,
                onChooseKeyboard = activity::chooseKeyboard,
                onAiSettings = { navigateTo("ai_settings") },
                onLicenses = { navigateTo("licenses") },
                onPersonas = { navigateTo("personas") },
                onPrivacy = { navigateTo("privacy") },
                onTerms = { navigateTo("terms") },
            )
        }
        composable("jade") { JadeScreen(::requestJade, onBack = { navController.popBackStack() }) }
        composable("auth") {
            AuthScreen(
                onBack = { navController.popBackStack() },
                onSignIn = { email, password -> runAuth { RewordiumRepository.signIn(email, password) } },
                onSignUp = { name, email, password, news -> runAuth { RewordiumRepository.signUp(name, email, password, news) } },
                onGoogleToken = { token -> runAuth { RewordiumRepository.signInWithGoogleIdToken(token) } },
                onResetPassword = { email -> runAuth { RewordiumRepository.sendPasswordReset(email) } },
                busy = busy, error = authError,
            )
        }
        composable("pro") {
            ProScreen(
                account = account, billing = billingState,
                onBack = { navController.popBackStack() },
                onSignIn = ::goToAuth,
                onPurchase = { id -> if (account == null) goToAuth() else billingManager.purchase(activity, id) },
                onRestore = { if (account == null) goToAuth() else billingManager.restorePurchases() },
            )
        }
        composable("ai_settings") { AiSettingsScreen(onBack = { navController.popBackStack() }) }
        composable("personas") { com.noxquill.rewordium.app.screens.PersonaScreen(onBack = { navController.popBackStack() }) }
        composable("licenses") { LicensesScreen(onBack = { navController.popBackStack() }, onOpenLink = ::openLink) }
        composable("privacy") { InfoScreen("Privacy policy", onBack = { navController.popBackStack() }, onOpenLink = ::openLink) }
        composable("terms") { InfoScreen("Terms of service", onBack = { navController.popBackStack() }, onOpenLink = ::openLink) }
        composable("tool/{toolId}", arguments = listOf(navArgument("toolId") { type = NavType.StringType })) { entry ->
            val toolId = entry.arguments?.getString("toolId") ?: "paraphraser"
            activeToolId = toolId
            WritingToolScreen(
                toolId = toolId,
                onBack = { if (!navController.popBackStack()) navigateTo("tools") },
                onUpgrade = { navigateTo("pro") },
                onGenerate = ::requestWriting,
            )
        }
      }
      BotFlightLayer(botFlight)
      }
      }
    }
}

private val NavSlide = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 320,
    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
)

private fun authMessage(error: Throwable): String = when {
    error.message?.contains("email", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("password", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("network", ignoreCase = true) == true -> "Check your connection and try again."
    else -> error.message ?: "Something went wrong. Please try again."
}
