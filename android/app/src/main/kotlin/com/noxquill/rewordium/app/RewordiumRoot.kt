package com.noxquill.rewordium.app

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.noxquill.rewordium.app.screens.KeyboardScreen
import com.noxquill.rewordium.app.ui.ExpressiveFabMenu
import com.noxquill.rewordium.app.ui.FabAction
import com.noxquill.rewordium.app.ui.FloatingNavBar
import com.noxquill.rewordium.app.ui.NavTab
import com.noxquill.rewordium.data.jade.JadeSkills
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.noxquill.rewordium.MainActivity
import com.noxquill.rewordium.app.jade.JadeController
import com.noxquill.rewordium.app.screens.AccessibilityDisclosureDialog
import com.noxquill.rewordium.app.screens.AiSettingsScreen
import com.noxquill.rewordium.app.screens.AuthScreen
import com.noxquill.rewordium.app.screens.HomeScreen
import com.noxquill.rewordium.app.screens.InfoScreen
import com.noxquill.rewordium.app.screens.JADE_FLIGHT
import com.noxquill.rewordium.app.screens.JadeScreen
import com.noxquill.rewordium.app.screens.LicensesScreen
import com.noxquill.rewordium.app.screens.OnboardingScreen
import com.noxquill.rewordium.app.screens.PersonaScreen
import com.noxquill.rewordium.app.screens.ProScreen
import com.noxquill.rewordium.app.screens.SettingsScreen
import com.noxquill.rewordium.app.screens.recordAccessibilityConsent
import com.noxquill.rewordium.app.ui.AvatarPickerSheet
import com.noxquill.rewordium.app.ui.avatar.BotFlight
import com.noxquill.rewordium.app.ui.avatar.BotFlightLayer
import com.noxquill.rewordium.app.ui.avatar.LocalBotFlight
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.avatar.LocalBotHop
import com.noxquill.rewordium.app.notifications.InAppMessageHost
import com.noxquill.rewordium.app.ui.haptics.rememberHaptics
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.billing.PlayBillingManager
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.data.AppSettings
import com.noxquill.rewordium.data.RewordiumRepository
import com.noxquill.rewordium.keyboard.ime.ai.AIConfigProvider
import com.noxquill.rewordium.app.ui.warmColorSchemes
import com.noxquill.rewordium.data.KeyboardEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Stage { Onboarding, SignIn, App }

@Composable
fun RewordiumRoot(
    activity: MainActivity,
    routeRequest: String?,
    onRouteConsumed: () -> Unit,
) {
    val haptics = rememberHaptics { AppSettings.haptics.value }
    CompositionLocalProvider(LocalHaptics provides haptics, LocalBotHop provides { haptics.hop() }) {
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
    var firebaseUser by remember { mutableStateOf<FirebaseUser?>(RewordiumRepository.currentUser) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    // TEMP local verification only (debug build + files/store_demo). Never committed.
    val storeDemo = remember { (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && java.io.File(context.filesDir, "store_demo").exists() }
    var platformStatus by remember { mutableStateOf(activity.setupStatus()) }
    var billingState by remember { mutableStateOf(BillingUiState()) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var showAvatarSheet by rememberSaveable { mutableStateOf(false) }
    val avatar by AppSettings.avatar.collectAsState()

    suspend fun refreshAccount(): AccountInfo? =
        if (storeDemo) AccountInfo("guest", "Maya Patel", "maya@example.com", 250, true, "pro").also { account = it }
        else runCatching { RewordiumRepository.refreshAccount(context) }.getOrNull().also { account = it }

    fun usesOwnKey() = AIConfigProvider.getConfig(context).isAdvancedEnabled

    val jade = remember {
        JadeController(
            context = context,
            scope = scope,
            owner = { RewordiumRepository.currentUser?.uid ?: "guest" },
            userName = { account?.name?.trim()?.substringBefore(' ') },
            beforeRequest = {
                if (RewordiumRepository.currentUser == null) throw IllegalStateException("Sign in to chat with Jade.")
                if (!usesOwnKey() && account?.isPro != true) {
                    val credits = account?.credits ?: refreshAccount()?.credits ?: 0
                    if (credits <= 0) throw IllegalStateException("You’re out of credits. Rewordium Pro gives you unlimited Jade.")
                }
            },
            afterSuccess = {
                if (!usesOwnKey() && account?.isPro != true) {
                    RewordiumRepository.consumeCredit(context, "jade")
                    refreshAccount()
                }
                activity.onAiGenerationSuccess()
            },
        )
    }

    val authListener = remember {
        FirebaseAuth.AuthStateListener { auth ->
            val previousUid = firebaseUser?.uid
            firebaseUser = auth.currentUser
            if (auth.currentUser?.uid != previousUid) jade.accountChanged()
            scope.launch { refreshAccount() }
        }
    }
    DisposableEffect(activity) {
        runCatching { FirebaseAuth.getInstance().addAuthStateListener(authListener) }
        onDispose { runCatching { FirebaseAuth.getInstance().removeAuthStateListener(authListener) } }
    }
    LaunchedEffect(Unit) { refreshAccount() }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) platformStatus = activity.setupStatus()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    // Switching keyboards in Android's picker (or turning the assistant on) doesn't pause the app, so
    // watch the system settings themselves: the keyboard status updates the moment the user switches.
    DisposableEffect(activity) {
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                platformStatus = activity.setupStatus()
            }
        }
        val resolver = activity.contentResolver
        listOf(
            Settings.Secure.DEFAULT_INPUT_METHOD,
            Settings.Secure.ENABLED_INPUT_METHODS,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).forEach { key -> runCatching { resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, settingsObserver) } }
        onDispose { resolver.unregisterContentObserver(settingsObserver) }
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

    fun openLink(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Couldn’t open this link.", Toast.LENGTH_SHORT).show() }
    }

    fun requestAccessibility() {
        if (platformStatus.accessibilityEnabled) activity.openAccessibilitySettings() else showDisclosure = true
    }

    val stage = when {
        !onboardingDone -> Stage.Onboarding
        firebaseUser == null && !storeDemo -> Stage.SignIn
        else -> Stage.App
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(320)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 1.04f))
                },
                label = "stage",
            ) { current ->
                when (current) {
                    Stage.Onboarding -> OnboardingScreen(onFinish = {
                        appPrefs.edit().putBoolean("onboarding_complete", true).apply()
                        onboardingDone = true
                    })
                    Stage.SignIn -> SignInGate(onOpenLink = ::openLink, onSignedIn = { scope.launch { refreshAccount() } })
                    Stage.App -> MainApp(
                        activity = activity,
                        account = account,
                        avatar = avatar,
                        platformStatus = platformStatus,
                        billingState = billingState,
                        billingManager = billingManager,
                        jade = jade,
                        routeRequest = routeRequest,
                        onRouteConsumed = onRouteConsumed,
                        onOpenLink = ::openLink,
                        onRequestAccessibility = ::requestAccessibility,
                        onChangeAvatar = { showAvatarSheet = true },
                        onSignOut = {
                            RewordiumRepository.signOut(context)
                            account = null
                        },
                        onDeleteAccount = { password, token ->
                            try {
                                if (password != null) RewordiumRepository.reauthenticateWithPassword(password)
                                if (token != null) RewordiumRepository.reauthenticateWithGoogle(token)
                                RewordiumRepository.deleteAccount()
                            } catch (e: Exception) {
                                throw IllegalStateException(authMessage(e))
                            }
                            jade.clearHistory()
                            RewordiumRepository.signOut(context)
                            account = null
                            Toast.makeText(context, "Your account was deleted", Toast.LENGTH_LONG).show()
                        },
                    )
                }
            }
        }
    }

    // An admin banned the account that was signed in: it has been signed out; say why, once.
    val suspension by RewordiumRepository.suspension.collectAsState()
    suspension?.let { reason ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { RewordiumRepository.dismissSuspension() },
            title = { androidx.compose.material3.Text("Account suspended") },
            text = {
                androidx.compose.material3.Text(
                    if (reason.isBlank()) "This account can’t use Rewordium right now. Contact ${com.noxquill.rewordium.data.RewordiumLinks.SUPPORT_EMAIL} if you think this is a mistake."
                    else "This account can’t use Rewordium right now: $reason\n\nContact ${com.noxquill.rewordium.data.RewordiumLinks.SUPPORT_EMAIL} if you think this is a mistake.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { RewordiumRepository.dismissSuspension() }) { androidx.compose.material3.Text("OK") }
            },
        )
    }

    if (showDisclosure) AccessibilityDisclosureDialog(
        onAgree = {
            showDisclosure = false
            recordAccessibilityConsent(context)
            activity.openAccessibilitySettings()
        },
        onDecline = { showDisclosure = false },
    )

    if (showAvatarSheet) AvatarPickerSheet(
        current = avatar,
        onDismiss = { showAvatarSheet = false },
        onConfirm = { AppSettings.setAvatar(it); showAvatarSheet = false },
    )
}

/** Sign-in gate with in-place Terms and Privacy pages (back returns to the form). */
@Composable
private fun SignInGate(onOpenLink: (String) -> Unit, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var legal by rememberSaveable { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit, success: () -> Unit = onSignedIn) {
        scope.launch {
            busy = true
            error = null
            notice = null
            runCatching { action() }
                .onSuccess { success() }
                .onFailure { error = authMessage(it) }
            busy = false
        }
    }

    BackHandler(enabled = legal != null) { legal = null }
    AnimatedContent(
        targetState = legal,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(NavSpring) { it / 4 } + fadeIn()).togetherWith(fadeOut(tween(150)))
            } else {
                fadeIn(tween(220)).togetherWith(slideOutHorizontally(NavSpring) { it / 4 } + fadeOut())
            }
        },
        label = "auth-legal",
    ) { page ->
        if (page != null) {
            InfoScreen(privacy = page == "privacy", onBack = { legal = null }, onOpenLink = onOpenLink)
        } else {
            AuthScreen(
                busy = busy,
                error = error,
                notice = notice,
                onSignIn = { email, password -> run({ RewordiumRepository.signIn(email, password) }) },
                onSignUp = { name, email, password, news -> run({ RewordiumRepository.signUp(name, email, password, news) }) },
                onGoogleToken = { token -> run({ RewordiumRepository.signInWithGoogleIdToken(token) }) },
                onResetPassword = { email ->
                    run({ RewordiumRepository.sendPasswordReset(email) }) {
                        notice = "We sent a password reset link to ${email.trim()}."
                    }
                },
                onTerms = { legal = "terms" },
                onPrivacy = { legal = "privacy" },
            )
        }
    }
}

@Composable
private fun MainApp(
    activity: MainActivity,
    account: AccountInfo?,
    avatar: String,
    platformStatus: com.noxquill.rewordium.PlatformSetupStatus,
    billingState: BillingUiState,
    billingManager: PlayBillingManager,
    jade: JadeController,
    routeRequest: String?,
    onRouteConsumed: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRequestAccessibility: () -> Unit,
    onChangeAvatar: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: suspend (String?, String?) -> Unit,
) {
    val navController = rememberNavController()
    val botFlight = remember { BotFlight() }
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    var jadeWasOpen by remember { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    // Warm what the tabs need once the first screen is up, so Settings and Keys open without a stall:
    // the palette previews (generated off the main thread) and the keyboard engine with its themes.
    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) { warmColorSchemes(activity) }
        delay(500)
        KeyboardEngine.ensure(activity)
        KeyboardEngine.refreshThemes(activity)
    }

    LaunchedEffect(currentEntry) {
        val jadeOpen = route == "jade"
        if (jadeOpen && !jadeWasOpen) botFlight.chatOpened(JADE_FLIGHT)
        if (jadeWasOpen && route == "home") botFlight.toHome(JADE_FLIGHT)
        jadeWasOpen = jadeOpen
        fabExpanded = false
    }

    fun go(target: String) = navController.navigate(target) { launchSingleTop = true }

    /** Top-level tabs keep their own scroll and state, like a bottom bar. */
    fun selectTab(tab: String) {
        if (tab == "jade") { go("jade"); return }
        navController.navigate(tab) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openJade(skill: String? = null) {
        if (skill != null) jade.skill = skill
        go("jade")
    }

    fun askJade(prompt: String) {
        jade.ask(prompt)
        go("jade")
    }

    fun handleRoute(requested: String) {
        when (requested) {
            "home" -> navController.popBackStack("home", inclusive = false)
            "settings" -> selectTab("settings")
            "keyboard_settings" -> selectTab("keyboard")
            "pro", "ai_settings", "licenses", "privacy", "terms", "personas", "theme_shop" -> go(requested)
            "jade", "tools" -> openJade()
            "paraphraser" -> openJade("rewrite")
            "summarizer" -> openJade("summarize")
            "translator" -> openJade("translate")
            "detector" -> openJade("detector")
            "document" -> openJade("polish")
            "grammar", "tone", "humanizer", "rewrite" -> openJade(if (requested == "humanizer") "humanize" else requested)
            else -> Unit
        }
    }

    LaunchedEffect(routeRequest) {
        routeRequest?.let { handleRoute(it); onRouteConsumed() }
    }

    val tabs = remember {
        listOf(
            NavTab("home", "HOME", Icons.Outlined.Home, Icons.Filled.Home),
            NavTab("jade", "JADE", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
            NavTab("keyboard", "KEYS", Icons.Outlined.Keyboard, Icons.Filled.Keyboard),
            NavTab("settings", "SETTINGS", Icons.Outlined.Settings, Icons.Filled.Settings),
        )
    }
    val showNav = route == "home" || route == "keyboard" || route == "settings"
    val fabActions: List<FabAction>? = when (route) {
        "home" -> listOf(
            FabAction("Chat with Jade", Icons.Filled.AutoAwesome) { openJade(JadeSkills.CHAT) },
            FabAction("Rewrite", Icons.Filled.EditNote) { openJade("rewrite") },
            FabAction("Fix grammar", Icons.Filled.Spellcheck) { openJade("grammar") },
            FabAction("Translate", Icons.Filled.Translate) { openJade("translate") },
            FabAction("Change tone", Icons.Filled.Tune) { openJade("tone") },
        )
        "keyboard" -> listOf(
            FabAction("Taller keys", Icons.Filled.Height) { askJade("Make my keyboard a little taller") },
            FabAction("Dark keyboard", Icons.Filled.DarkMode) { askJade("Switch my keyboard to a dark theme") },
            FabAction("Quiet typing", Icons.AutoMirrored.Filled.VolumeOff) { askJade("Turn off key press sounds") },
            FabAction("Add a language", Icons.Filled.Language) { askJade("Help me add another language to my keyboard") },
        )
        else -> null
    }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    CompositionLocalProvider(LocalBotFlight provides botFlight) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Wide screens fit the toolbar and FAB side by side; on phones the FAB floats above the toolbar.
            val sideBySide = maxWidth >= 420.dp
            val tabInset = if (fabActions != null && !sideBySide) 172.dp else 104.dp

            NavHost(
                navController = navController,
                startDestination = "home",
                // Three kinds of move, each with its own motion:
                //  · tab ↔ tab: a fade-through (the old page dips out, the new one settles in);
                //  · into Jade: Jade rises over the page, and sinks back down when you go back;
                //  · any other page: a shared-axis slide in from the side, and back out the same way.
                enterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        isTabSwitch(from, to) -> TabEnter
                        to == "jade" -> JadeRise
                        else -> PushEnter
                    }
                },
                exitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        isTabSwitch(from, to) -> TabExit
                        to == "jade" -> UnderRecede
                        else -> PushExit
                    }
                },
                popEnterTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        isTabSwitch(from, to) -> TabEnter
                        from == "jade" -> UnderReturn
                        else -> PopEnter
                    }
                },
                popExitTransition = {
                    val from = initialState.destination.route
                    val to = targetState.destination.route
                    when {
                        isTabSwitch(from, to) -> TabExit
                        from == "jade" -> JadeSink
                        else -> PopExit
                    }
                },
                // The back gesture scrubs the same moves (tween-based, so they follow the finger).
                predictivePopEnterTransition = {
                    if (initialState.destination.route == "jade") fadeIn(tween(300)) + scaleIn(tween(300, easing = EmphasizedDecelerate), initialScale = 0.94f)
                    else slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { -it / 8 } + fadeIn(tween(300))
                },
                predictivePopExitTransition = {
                    if (initialState.destination.route == "jade") slideOutVertically(tween(300, easing = EmphasizedDecelerate)) { it / 4 } + fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.92f)
                    else slideOutHorizontally(tween(300, easing = EmphasizedDecelerate)) { it / 4 } + fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.94f)
                },
            ) {
                composable("home") {
                    Screen {
                        HomeScreen(
                            account = account,
                            avatar = avatar,
                            status = platformStatus,
                            recentChats = jade.chats,
                            jadeCanTune = jade.canUseTools,
                            bottomInset = tabInset,
                            onOpenJade = { openJade() },
                            onAskJade = ::askJade,
                            onUseSkill = { skill -> openJade(skill) },
                            onOpenChat = { id -> jade.openChat(id); go("jade") },
                            onOpenKeyboard = { selectTab("keyboard") },
                            onEnableKeyboard = { if (platformStatus.keyboardEnabled) activity.chooseKeyboard() else activity.openKeyboardActivationSettings() },
                            onEnableAccessibility = onRequestAccessibility,
                            onOpenPro = { go("pro") },
                            onChangeAvatar = onChangeAvatar,
                        )
                    }
                }
                composable("jade") {
                    Screen {
                        JadeScreen(
                            controller = jade,
                            userAvatar = avatar,
                            userName = account?.name?.trim()?.substringBefore(' '),
                            isPro = account?.isPro == true,
                            onBack = { navController.popBackStack() },
                            onOpenKeyboardScreen = { path -> if (path == null) selectTab("keyboard") else activity.openReboardPath(path) },
                            onUpgrade = { go("pro") },
                        )
                    }
                }
                composable("keyboard") {
                    Screen {
                        KeyboardScreen(
                            status = platformStatus,
                            bottomInset = tabInset,
                            onEnableKeyboard = activity::openKeyboardActivationSettings,
                            onChooseKeyboard = activity::chooseKeyboard,
                            onAskJade = { openJade(JadeSkills.CHAT) },
                            onOpenAdvanced = { activity.openKeyboardPreferences() },
                            onOpenThemeShop = { go("theme_shop") },
                        )
                    }
                }
                composable("settings") {
                    Screen {
                        SettingsScreen(
                            account = account,
                            status = platformStatus,
                            bottomInset = tabInset,
                            onPro = { go("pro") },
                            onAiSettings = { go("ai_settings") },
                            onPersonas = { go("personas") },
                            onPrivacy = { go("privacy") },
                            onTerms = { go("terms") },
                            onLicenses = { go("licenses") },
                            onOpenLink = onOpenLink,
                            onOpenKeyboard = { selectTab("keyboard") },
                            onAccessibility = onRequestAccessibility,
                            onAskJade = { openJade(JadeSkills.CHAT) },
                            onClearJadeHistory = { jade.clearHistory() },
                            onBackupRestored = { jade.reloadChats() },
                            onSignOut = onSignOut,
                            onDeleteAccount = onDeleteAccount,
                            onOpenAdmin = { go("admin") },
                        )
                    }
                }
                composable("admin") {
                    Screen {
                        com.noxquill.rewordium.app.screens.admin.AdminScreen(
                            billing = billingState,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("pro") {
                    Screen {
                        ProScreen(
                            account = account,
                            billing = billingState,
                            packageName = activity.packageName,
                            onBack = { navController.popBackStack() },
                            onPurchase = { id -> billingManager.purchase(activity, id) },
                            onRestore = { billingManager.restorePurchases() },
                            onOpenLink = onOpenLink,
                            onTerms = { go("terms") },
                            onPrivacy = { go("privacy") },
                        )
                    }
                }
                composable("ai_settings") { Screen { AiSettingsScreen(onBack = { navController.popBackStack() }) } }
                composable("personas") { Screen { PersonaScreen(onBack = { navController.popBackStack() }) } }
                composable("theme_shop") {
                    Screen {
                        com.noxquill.rewordium.app.screens.themes.ThemeShopScreen(
                            jade = jade,
                            authorName = account?.name?.trim()?.substringBefore(' '),
                            onBack = { navController.popBackStack() },
                            onOpenBuilder = { spec ->
                                com.noxquill.rewordium.app.screens.themes.ThemeDraft.initial = spec
                                go("theme_builder")
                            },
                        )
                    }
                }
                composable("theme_builder") {
                    Screen {
                        com.noxquill.rewordium.app.screens.themes.ThemeBuilderScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                }
                composable("licenses") { Screen { LicensesScreen(onBack = { navController.popBackStack() }, onOpenLink = onOpenLink) } }
                composable("privacy") { Screen { InfoScreen(privacy = true, onBack = { navController.popBackStack() }, onOpenLink = onOpenLink) } }
                composable("terms") { Screen { InfoScreen(privacy = false, onBack = { navController.popBackStack() }, onOpenLink = onOpenLink) } }
            }

            // Release notes, campaigns and announcements, once each.
            InAppMessageHost(onOpenLink = onOpenLink)

            // Scrim behind the open FAB menu; a tap anywhere closes it.
            AnimatedVisibility(visible = fabExpanded, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fabExpanded = false },
                )
            }

            AnimatedVisibility(
                visible = showNav,
                enter = slideInVertically(NavBarSpring) { it * 2 } + fadeIn(),
                exit = slideOutVertically { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(if (sideBySide && fabActions != null) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                FloatingNavBar(tabs = tabs, selected = route, onSelect = ::selectTab)
            }

            AnimatedVisibility(
                visible = fabActions != null,
                enter = scaleIn(NavScale, initialScale = 0.4f) + fadeIn(),
                exit = scaleOut(targetScale = 0.4f) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = if (sideBySide) 4.dp else 76.dp),
            ) {
                ExpressiveFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    icon = if (route == "keyboard") Icons.Filled.Tune else Icons.Filled.AutoAwesome,
                    label = if (route == "keyboard") "Quick keyboard asks" else "Write with Jade",
                    actions = fabActions.orEmpty(),
                )
            }

            BotFlightLayer(botFlight)
        }
    }
}

private val TabRoutes = setOf("home", "keyboard", "settings")

private fun isTabSwitch(from: String?, to: String?) = from in TabRoutes && to in TabRoutes

private val NavBarSpring = spring(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset(1, 1),
)

/** Every route travels on its own opaque surface, so screens never show through each other mid-slide. */
@Composable
private fun Screen(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
}

private val NavSpring = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset(1, 1),
)
private val NavScale = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

// ── Screen transitions (Material 3 emphasized easing and expressive springs) ──
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val SlideSpring = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset(1, 1))
private val SettleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

/** Tabs: the old page dips out quickly, then the new one fades and settles in (fade-through). */
private val TabExit = fadeOut(tween(90, easing = EmphasizedAccelerate)) + scaleOut(tween(90), targetScale = 0.98f)
private val TabEnter = fadeIn(tween(210, delayMillis = 70, easing = EmphasizedDecelerate)) +
    scaleIn(tween(300, delayMillis = 70, easing = EmphasizedDecelerate), initialScale = 0.95f)

/** Jade rises over the page from just below, and the page behind recedes a touch… */
private val JadeRise = slideInVertically(SlideSpring) { it / 6 } + fadeIn(tween(220, easing = EmphasizedDecelerate)) +
    scaleIn(SettleSpring, initialScale = 0.96f)
private val UnderRecede = fadeOut(tween(260, delayMillis = 40)) + scaleOut(tween(300, easing = EmphasizedDecelerate), targetScale = 0.95f)

/** …and on the way back Jade sinks away while the page comes forward again. */
private val JadeSink = slideOutVertically(tween(260, easing = EmphasizedAccelerate)) { it / 5 } +
    fadeOut(tween(200, delayMillis = 40)) + scaleOut(tween(260, easing = EmphasizedAccelerate), targetScale = 0.94f)
private val UnderReturn = fadeIn(tween(220)) + scaleIn(SettleSpring, initialScale = 0.95f)

/** Other pages: a shared-axis slide — in from the right, the page beneath shifting left, and the reverse on back. */
private val PushEnter = slideInHorizontally(SlideSpring) { it / 4 } + fadeIn(tween(200, easing = EmphasizedDecelerate))
private val PushExit = slideOutHorizontally(SlideSpring) { -it / 10 } + fadeOut(tween(200, delayMillis = 60))
private val PopEnter = slideInHorizontally(SlideSpring) { -it / 10 } + fadeIn(tween(220))
private val PopExit = slideOutHorizontally(SlideSpring) { it / 4 } + fadeOut(tween(160, easing = EmphasizedAccelerate))

private fun authMessage(error: Throwable): String = when (error) {
    is FirebaseAuthWeakPasswordException -> "Pick a stronger password — at least 6 characters."
    is FirebaseAuthUserCollisionException -> "There’s already an account with that email. Try signing in instead."
    is FirebaseAuthInvalidUserException -> "We couldn’t find an account with that email."
    is FirebaseAuthInvalidCredentialsException -> "That email or password isn’t right."
    is FirebaseAuthRecentLoginRequiredException -> "For your security, sign out and sign in again, then try once more."
    else -> when {
        error.message?.contains("network", ignoreCase = true) == true -> "Check your connection and try again."
        else -> error.message ?: "Something went wrong. Please try again."
    }
}
