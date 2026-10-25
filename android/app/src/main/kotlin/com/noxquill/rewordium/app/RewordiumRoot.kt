package com.noxquill.rewordium.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import kotlinx.coroutines.delay

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

    fun selectMascot(type: String) {
        mascotType = type
        appPrefs.edit().putString("mascot_type", type).apply()
    }

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
        val toolId = if (activeToolId in setOf("paraphraser", "grammar", "summarizer", "translator", "tone", "detector", "document", "humanizer")) activeToolId else toolFor(activeToolId).id
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
            OnboardingScreen(
                mascotType = mascotType,
                onMascotSelected = ::selectMascot,
            ) {
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
          enterTransition = { fadeIn(tween(180)) + slideInHorizontally(NavSlide) { it / 8 } },
          exitTransition = { fadeOut(tween(140)) + slideOutHorizontally(NavSlide) { -it / 12 } },
          popEnterTransition = { fadeIn(tween(180)) + slideInHorizontally(NavSlide) { -it / 10 } },
          popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(NavSlide) { it / 8 } },
          predictivePopEnterTransition = { fadeIn(tween(180)) + slideInHorizontally(NavSlide) { -it / 10 } },
          predictivePopExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(NavSlide) { it / 8 } },
      ) {
        composable("home") {
            HomeScreen(
                account = account,
                platformStatus = platformStatus,
                recentTools = recentToolIds.mapNotNull { id -> writingTools.firstOrNull { it.id == id } },
                onClearRecents = {
                    recentToolIds = emptyList()
                    appPrefs.edit().remove("recent_tool_ids").apply()
                },
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
            StagedDestination("tools", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) {
                ToolsScreen(onOpenTool = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("settings") {
            StagedDestination("settings", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { SettingsScreen(
                account = account, status = platformStatus,
                onBack = { navController.popBackStack() },
                mascotType = mascotType,
                onMascotSelected = ::selectMascot,
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
                onKeyboard = { activity.openKeyboardPreferences() },
                onKeyboardSection = { section -> activity.openKeyboardPreferences(section) },
                onAccessibility = activity::openAccessibilitySettings,
                onEnableKeyboard = activity::openKeyboardActivationSettings,
                onChooseKeyboard = activity::chooseKeyboard,
                onAiSettings = { navigateTo("ai_settings") },
                onLicenses = { navigateTo("licenses") },
                onPersonas = { navigateTo("personas") },
                onPrivacy = { navigateTo("privacy") },
                onTerms = { navigateTo("terms") },
            ) }
        }
        composable("settings/access") {
            StagedDestination("settings/access", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { SettingsScreen(
                account = account, status = platformStatus,
                onBack = { navController.popBackStack() }, initialTab = 1,
                mascotType = mascotType,
                onMascotSelected = ::selectMascot,
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
                onKeyboard = { activity.openKeyboardPreferences() },
                onKeyboardSection = { section -> activity.openKeyboardPreferences(section) },
                onAccessibility = activity::openAccessibilitySettings,
                onEnableKeyboard = activity::openKeyboardActivationSettings,
                onChooseKeyboard = activity::chooseKeyboard,
                onAiSettings = { navigateTo("ai_settings") },
                onLicenses = { navigateTo("licenses") },
                onPersonas = { navigateTo("personas") },
                onPrivacy = { navigateTo("privacy") },
                onTerms = { navigateTo("terms") },
            ) }
        }
        composable("jade") { StagedDestination("jade", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { JadeScreen(::requestJade, onBack = { navController.popBackStack() }, onOpenKeyboardSettings = { activity.openKeyboardPreferences() }) } }
        composable("auth") {
            StagedDestination("auth", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { AuthScreen(
                onBack = { navController.popBackStack() },
                onSignIn = { email, password -> runAuth { RewordiumRepository.signIn(email, password) } },
                onSignUp = { name, email, password, news -> runAuth { RewordiumRepository.signUp(name, email, password, news) } },
                onGoogleToken = { token -> runAuth { RewordiumRepository.signInWithGoogleIdToken(token) } },
                onResetPassword = { email -> runAuth { RewordiumRepository.sendPasswordReset(email) } },
                mascotType = mascotType,
                onMascotSelected = ::selectMascot,
                busy = busy, error = authError,
            ) }
        }
        composable("pro") {
            StagedDestination("pro", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { ProScreen(
                account = account, billing = billingState,
                onBack = { navController.popBackStack() },
                onSignIn = ::goToAuth,
                onPurchase = { id -> if (account == null) goToAuth() else billingManager.purchase(activity, id) },
                onRestore = { if (account == null) goToAuth() else billingManager.restorePurchases() },
            ) }
        }
        composable("ai_settings") { StagedDestination("ai_settings", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { AiSettingsScreen(onBack = { navController.popBackStack() }) } }
        composable("personas") { StagedDestination("personas", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { com.noxquill.rewordium.app.screens.PersonaScreen(onBack = { navController.popBackStack() }) } }
        composable("licenses") { StagedDestination("licenses", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { LicensesScreen(onBack = { navController.popBackStack() }, onOpenLink = ::openLink) } }
        composable("privacy") { StagedDestination("privacy", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { InfoScreen("Privacy policy", onBack = { navController.popBackStack() }, onOpenLink = ::openLink) } }
        composable("terms") { StagedDestination("terms", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { InfoScreen("Terms of service", onBack = { navController.popBackStack() }, onOpenLink = ::openLink) } }
        composable("tool/{toolId}", arguments = listOf(navArgument("toolId") { type = NavType.StringType })) { entry ->
            val toolId = entry.arguments?.getString("toolId") ?: "paraphraser"
            activeToolId = toolId
            StagedDestination("tool/$toolId", settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible) { WritingToolScreen(
                toolId = toolId,
                onBack = { if (!navController.popBackStack()) navigateTo("tools") },
                onUpgrade = { navigateTo("pro") },
                onGenerate = ::requestWriting,
            ) }
        }
      }
      BotFlightLayer(botFlight)
      }
      }
    }
}

private val NavSlide = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 240,
    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
)

/**
 * A route's opaque surface travels first. Delaying the heavy controls until
 * that short slide has settled removes the half-built-card effect that caused
 * dropped frames on device while keeping predictive back fully native.
 */
@Composable
private fun StagedDestination(
    route: String,
    settled: Boolean,
    content: @Composable () -> Unit,
) {
    // Compose the destination as it enters so the user never sees a blank
    // route followed by a late card-build. Keep the route surface stable while
    // the shorter motion runs.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        content()
    }
}

private fun authMessage(error: Throwable): String = when {
    error.message?.contains("email", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("password", ignoreCase = true) == true -> error.message.orEmpty()
    error.message?.contains("network", ignoreCase = true) == true -> "Check your connection and try again."
    else -> error.message ?: "Something went wrong. Please try again."
}
