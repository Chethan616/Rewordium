@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.noxquill.rewordium.app.screens.admin

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.SectionLabel
import com.noxquill.rewordium.app.ui.StatusPill
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.app.ui.monoLabel
import com.noxquill.rewordium.app.ui.monoNumber
import com.noxquill.rewordium.app.ui.segmentColors
import com.noxquill.rewordium.app.ui.segmentShape
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.data.admin.AdminRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date

private enum class AdminTab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Filled.Insights),
    Users("Users", Icons.Filled.People),
    Messages("In-app", Icons.Filled.Campaign),
    Push("Push", Icons.Filled.NotificationsActive),
    Reports("Reports", Icons.Filled.Flag),
}

/**
 * The owner's control room: how many people use Rewordium and how, what it earns, and the tools to talk to
 * users and manage accounts. Reached only through [AdminUnlockDialog]; every write it makes is also checked
 * by the Firestore rules and the push function on the server.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdminScreen(billing: BillingUiState, onBack: () -> Unit) {
    if (!AdminRepository.isUnlocked()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(AdminTab.Overview) }
    var openUser by remember { mutableStateOf<AdminRepository.AdminUser?>(null) }
    val haptics = LocalHaptics.current

    ExpressiveScaffold(
        title = "admin_",
        subtitle = AdminRepository.OWNER_EMAIL,
        onBack = onBack,
        heading = Heading.Mono,
        actions = {
            IconButton(onClick = { haptics.click(); AdminRepository.lock(); onBack() }) { Icon(Icons.Filled.Lock, "Lock the admin panel") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AdminTab.entries) { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = { haptics.tick(); tab = entry },
                        label = { Text(entry.label) },
                        leadingIcon = { Icon(entry.icon, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "admin-tab",
            ) { current ->
                when (current) {
                    AdminTab.Overview -> OverviewTab(billing)
                    AdminTab.Users -> UsersTab(onOpen = { openUser = it })
                    AdminTab.Messages -> MessagesTab()
                    AdminTab.Push -> PushTab()
                    AdminTab.Reports -> ReportsTab(onOpenUser = { uid -> scope.launch { openUser = runCatching { AdminRepository.user(uid) }.getOrNull() } })
                }
            }
        }
    }

    openUser?.let { user -> UserSheet(user, onDismiss = { openUser = null }, onChanged = { openUser = it }) }
}

// ── Unlock ──────────────────────────────────────────────────────────────────

/** The password prompt behind the hidden tap in Settings. Locks for a minute after five wrong tries. */
@Composable
fun AdminUnlockDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        if (checking || password.isEmpty()) return
        checking = true
        scope.launch {
            when (val result = AdminRepository.unlock(password)) {
                AdminRepository.Unlock.Granted -> { haptics.success(); onUnlocked() }
                is AdminRepository.Unlock.Wrong -> {
                    haptics.reject()
                    error = if (result.attemptsLeft > 0) "Wrong password · ${result.attemptsLeft} tries left" else "Not available on this account."
                }
                is AdminRepository.Unlock.LockedOut -> { haptics.reject(); error = "Too many tries. Wait ${result.seconds}s." }
            }
            password = ""
            checking = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text("Admin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !checking,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(error != null) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !checking && password.isNotEmpty()) {
                if (checking) LoadingIndicator(Modifier.size(20.dp)) else Text("Unlock")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Loading helper ──────────────────────────────────────────────────────────

private class Loadable<T>(val result: Result<T>?, val reload: () -> Unit)

@Composable
private fun <T> rememberLoadable(vararg keys: Any?, load: suspend () -> T): Loadable<T> {
    var version by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(version, *keys) {
        result = null
        result = runCatching { load() }
    }
    return Loadable(result) { version++ }
}

@Composable
private fun <T> LoadableContent(state: Loadable<T>, content: @Composable (T) -> Unit) {
    val result = state.result
    when {
        result == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        result.isFailure -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    result.exceptionOrNull()?.message ?: "Couldn’t load this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = state.reload) { Text("Retry") }
            }
        }
        else -> content(result.getOrThrow())
    }
}

private fun tabPadding() = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)

private fun Date?.short(): String = this?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) } ?: "—"

// ── Overview ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(billing: BillingUiState) {
    val state = rememberLoadable { AdminRepository.overview() }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = tabPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Right now", Modifier.weight(1f))
                IconButton(onClick = state.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item {
            LoadableContent(state) { o ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                        val tile = Modifier.weight(1f)
                        StatTile("Accounts", o.totalUsers, "+${o.newUsers7d} this week", Tone.Primary, tile)
                        StatTile("Active today", o.active24h, "${o.active7d} this week", Tone.Secondary, tile)
                        StatTile("Installs", o.installs, "devices seen", Tone.Tertiary, tile)
                        StatTile("Pro", o.proUsers, "${o.grantedPro} granted by you", Tone.Primary, tile)
                        StatTile("Banned", o.bannedUsers, "accounts", Tone.Error, tile)
                        StatTile("Open reports", o.openReports, "AI replies flagged", Tone.Neutral, tile)
                        StatTile("Community themes", o.communityThemes, "shared", Tone.Secondary, tile)
                    }
                    RevenueCard(o, billing)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Long, caption: String, tone: Tone, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = monoLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(NumberFormat.getIntegerInstance().format(value), style = monoNumber(30.sp), color = if (tone == Tone.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * What subscriptions bring in, from the live subscribers and the current Play prices. An estimate before
 * Google's fee, in the price of this phone's Play country — the exact figures live in Play Console.
 */
@Composable
private fun RevenueCard(o: AdminRepository.Overview, billing: BillingUiState) {
    val monthly = regularPrice(billing, "rewordium_pro_monthly")
    val yearly = regularPrice(billing, "rewordium_pro_yearly")
    val currency = monthly?.second ?: yearly?.second
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIcon(Icons.Filled.Stars, Tone.Tertiary)
                Text("Revenue", style = MaterialTheme.typography.titleLarge)
            }
            if (currency == null) {
                Text(
                    if (billing.loading) "Loading Play prices…" else "Play prices aren’t available on this phone, so revenue can’t be estimated here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val mrrMicros = o.monthlySubscribers * (monthly?.first ?: 0L) + o.yearlySubscribers * (yearly?.first ?: 0L) / 12
                RevenueLine("Monthly subscribers", "${o.monthlySubscribers} × ${monthly?.let { money(it.first, it.second) } ?: "—"}")
                RevenueLine("Yearly subscribers", "${o.yearlySubscribers} × ${yearly?.let { money(it.first, it.second) } ?: "—"}")
                RevenueLine("Est. monthly recurring", money(mrrMicros, currency), strong = true)
                RevenueLine("Est. per year", money(mrrMicros * 12, currency), strong = true)
                RevenueLine("After Google’s 15%", money(mrrMicros * 12 * 85 / 100, currency))
            }
            Text(
                "Estimated from live subscribers at today’s Play price. Exact earnings, refunds and taxes: Play Console › Monetize.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RevenueLine(label: String, value: String, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) monoNumber(20.sp) else monoNumber())
    }
}

private fun regularPrice(billing: BillingUiState, id: String): Pair<Long, String>? {
    val product: ProductDetails = billing.products.firstOrNull { it.productId == id } ?: return null
    val phase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
    return phase.priceAmountMicros to phase.priceCurrencyCode
}

private fun money(micros: Long, currency: String): String =
    runCatching { NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(micros / 1_000_000.0) }
        .getOrDefault("${micros / 1_000_000.0} $currency")

// ── Users ───────────────────────────────────────────────────────────────────

@Composable
private fun UsersTab(onOpen: (AdminRepository.AdminUser) -> Unit) {
    val haptics = LocalHaptics.current
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val state = rememberLoadable(submitted) {
        if (submitted.isBlank()) AdminRepository.recentUsers() else AdminRepository.searchUsers(submitted)
    }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = tabPadding(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Email or user ID") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { haptics.tick(); submitted = query.trim() }),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                SectionLabel(if (submitted.isBlank()) "Newest accounts" else "Matches for “$submitted”", Modifier.weight(1f))
                if (submitted.isNotBlank()) TextButton(onClick = { query = ""; submitted = "" }) { Text("Clear") }
                IconButton(onClick = state.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item {
            LoadableContent(state) { users ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (users.isEmpty()) Text("No accounts found.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    users.forEachIndexed { i, user ->
                        Surface(
                            onClick = { haptics.tick(); onOpen(user) },
                            shape = segmentShape(i, users.size),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(user.name.ifBlank { "No name" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(user.email.ifBlank { user.uid }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                UserBadges(user)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBadges(user: AdminRepository.AdminUser) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (user.banned) StatusPill("Banned", active = false, container = MaterialTheme.colorScheme.errorContainer)
        if (user.isPro) StatusPill("Pro · ${user.plan}", active = true) else StatusPill("${user.credits} cr", active = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun UserSheet(user: AdminRepository.AdminUser, onDismiss: () -> Unit, onChanged: (AdminRepository.AdminUser) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var busy by remember { mutableStateOf(false) }
    var lastSeen by remember(user.uid) { mutableStateOf<Date?>(null) }
    var customCredits by remember { mutableStateOf("") }
    var banReason by remember { mutableStateOf("") }
    var confirmBan by remember { mutableStateOf(false) }
    var messaging by remember { mutableStateOf(false) }
    LaunchedEffect(user.uid) { lastSeen = runCatching { AdminRepository.lastSeen(user.uid) }.getOrNull() }

    fun act(done: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { action() }
                .onSuccess {
                    haptics.success()
                    Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                    runCatching { AdminRepository.user(user.uid) }.getOrNull()?.let(onChanged)
                }
                .onFailure { haptics.reject(); Toast.makeText(context, it.message ?: "That didn’t work.", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.name.ifBlank { "No name" }, style = MaterialTheme.typography.headlineSmall)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(user.uid, style = monoLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    UserBadges(user)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoLine("Joined", user.createdAt.short())
                        InfoLine("Last seen", lastSeen.short())
                        InfoLine("Signs in with", user.signInMethod ?: "—")
                        InfoLine("Plan", if (user.isPro) "Pro · ${user.plan}" else "Free")
                        user.subscriptionStatus?.let { InfoLine("Subscription", it) }
                        user.expiresAt?.let { InfoLine("Renews / ends", it.short()) }
                        InfoLine("Credits", user.credits.toString())
                        if (user.banned) InfoLine("Ban reason", user.bannedReason ?: "—")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Message")
                    Button(onClick = { haptics.click(); messaging = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Message this person")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Credits")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10L, 50L, 100L).forEach { amount ->
                            FilledTonalButton(onClick = { act("Added $amount credits") { AdminRepository.addCredits(user.uid, amount) } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("+$amount") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customCredits,
                            onValueChange = { customCredits = it.filter { c -> c.isDigit() || c == '-' }.take(6) },
                            placeholder = { Text("Other amount") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f),
                        )
                        val amount = customCredits.toLongOrNull()
                        Button(onClick = { amount?.let { act("Credits changed by $it") { AdminRepository.addCredits(user.uid, it) }; customCredits = "" } }, enabled = !busy && amount != null && amount != 0L) { Text("Apply") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Plan")
                    if (user.isPro) {
                        OutlinedButton(onClick = { act("Pro removed") { AdminRepository.setPro(user.uid, false) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Take Pro away") }
                        if (user.subscriptionStatus != null) Text("They also have a Play subscription; that renews on its own until they cancel it in Play.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(onClick = { act("Pro granted") { AdminRepository.setPro(user.uid, true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Stars, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Promote to Pro")
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Access")
                    if (user.banned) {
                        FilledTonalButton(onClick = { act("Unbanned") { AdminRepository.setBanned(user.uid, false, null) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Lift the ban") }
                    } else {
                        Button(
                            onClick = { haptics.attention(); confirmBan = true },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Block, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ban this account")
                        }
                    }
                }
            }
        }
    }

    if (messaging) MessageUserDialog(user, onDismiss = { messaging = false })

    if (confirmBan) AlertDialog(
        onDismissRequest = { confirmBan = false },
        title = { Text("Ban ${user.name.ifBlank { user.email }}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("They’re signed out and can’t use their account until you lift it. They’ll see the reason.")
                OutlinedTextField(value = banReason, onValueChange = { banReason = it.take(200) }, label = { Text("Reason") }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmBan = false; act("Banned") { AdminRepository.setBanned(user.uid, true, banReason) } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("Ban") }
        },
        dismissButton = { TextButton(onClick = { confirmBan = false }) { Text("Cancel") } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
    }
}

// ── In-app messages & campaigns ─────────────────────────────────────────────

private enum class MessageKind(val label: String, val type: String) {
    Card("Card", "card"),
    Modal("Modal", "modal"),
    Banner("Banner", "banner"),
    WhatsNew("What’s new", "release_notes"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessagesTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var kind by rememberSaveable { mutableStateOf(MessageKind.Card) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var button by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AdminRepository.Message?>(null) }
    var busy by remember { mutableStateOf(false) }
    val state = rememberLoadable { AdminRepository.messages() }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = tabPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionLabel("New message") }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        MessageKind.entries.forEachIndexed { i, entry ->
                            ToggleButton(
                                checked = kind == entry,
                                onCheckedChange = { haptics.tick(); kind = entry },
                                colors = segmentColors(),
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    MessageKind.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(entry.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    Text(
                        when (kind) {
                            MessageKind.Card -> "A dialog, shown once to everyone on their next open."
                            MessageKind.Modal -> "A full-colour dialog, shown once to everyone."
                            MessageKind.Banner -> "A strip across the top of the screen, shown once."
                            MessageKind.WhatsNew -> "Shown once after people update to this version (never on a fresh install)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AiDraft(maxBody = 400) { t, b -> title = t; body = b }
                    Field(title, { title = it.take(80) }, "Title")
                    Field(body, { body = it.take(600) }, "Message", singleLine = false)
                    if (kind == MessageKind.WhatsNew) {
                        Field(version, { version = it.take(16) }, "Version (e.g. 3.2.0)")
                        Field(notes, { notes = it }, "What’s new · one per line, up to 10", singleLine = false)
                    } else {
                        Field(button, { button = it.take(30) }, "Button text (optional)")
                        Field(link, { link = it }, "Link · https://… or rewordium://theme_shop")
                        Field(image, { image = it }, "Image URL · https:// (optional)")
                    }
                    Button(
                        onClick = { haptics.click(); confirm = true },
                        enabled = !busy && title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Campaign, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publish")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                SectionLabel("Published", Modifier.weight(1f))
                IconButton(onClick = state.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item {
            LoadableContent(state) { messages ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (messages.isEmpty()) Text("Nothing published yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    messages.forEachIndexed { i, message ->
                        Surface(shape = segmentShape(i, messages.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(message.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(message.type, message.version, message.publishedAt.short()).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { haptics.click(); deleting = message }) { Icon(Icons.Filled.DeleteOutline, "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Publish to everyone?") },
        text = { Text("“$title” goes live now for every signed-in user.") },
        confirmButton = {
            Button(onClick = {
                confirm = false
                busy = true
                scope.launch {
                    runCatching {
                        AdminRepository.publishMessage(
                            title = title,
                            body = body,
                            type = kind.type,
                            buttonText = button.takeIf { kind != MessageKind.WhatsNew },
                            actionUrl = link.takeIf { kind != MessageKind.WhatsNew },
                            imageUrl = image.takeIf { kind != MessageKind.WhatsNew },
                            version = version.takeIf { kind == MessageKind.WhatsNew },
                            notes = if (kind == MessageKind.WhatsNew) notes.lines() else emptyList(),
                        )
                    }.onSuccess {
                        haptics.success()
                        Toast.makeText(context, "Published", Toast.LENGTH_SHORT).show()
                        title = ""; body = ""; button = ""; link = ""; image = ""; version = ""; notes = ""
                        state.reload()
                    }.onFailure { haptics.reject(); Toast.makeText(context, it.message ?: "Couldn’t publish.", Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )

    deleting?.let { message ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${message.title}”?") },
            text = { Text("People who haven’t seen it yet won’t.") },
            confirmButton = {
                Button(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { AdminRepository.deleteMessage(message.id) }
                            .onSuccess { state.reload() }
                            .onFailure { Toast.makeText(context, it.message ?: "Couldn’t delete it.", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/**
 * "Write it with AI": say what it's about in a few words and Jade drafts the title and message, which can
 * then be edited. Uses the same AI (and quota) as Jade.
 */
@Composable
private fun AiDraft(maxBody: Int, onDraft: (title: String, body: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var brief by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Text("Write it with AI", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = brief,
                onValueChange = { brief = it.take(300); error = null },
                placeholder = { Text("e.g. new Halloween keyboard themes are out") },
                enabled = !busy,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val message = error
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                FilledTonalButton(
                    onClick = {
                        busy = true
                        haptics.click()
                        scope.launch {
                            runCatching { com.noxquill.rewordium.data.jade.JadeEngine(context).draftMessage(brief, maxBody) }
                                .onSuccess { (t, b) -> haptics.success(); onDraft(t, b) }
                                .onFailure { haptics.reject(); error = it.message ?: "Couldn’t write that one." }
                            busy = false
                        }
                    },
                    enabled = !busy && brief.isNotBlank(),
                ) { if (busy) LoadingIndicator(Modifier.size(20.dp)) else Text("Draft it") }
            }
        }
    }
}

/** Sends one person a message to their private inbox (and, with the push function deployed, their phone). */
@Composable
private fun MessageUserDialog(user: AdminRepository.AdminUser, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MessageKind.Card) }
    var link by rememberSaveable { mutableStateOf("") }
    var button by rememberSaveable { mutableStateOf("") }
    var alsoPush by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                Column {
                    Text("Message ${user.name.ifBlank { user.email }}", style = MaterialTheme.typography.headlineSmall)
                    Text("Only they see it: live if the app is open, or the next time they open it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { AiDraft(maxBody = 400) { t, b -> title = t; body = b } }
            item { Field(title, { title = it.take(80) }, "Title") }
            item { Field(body, { body = it.take(600) }, "Message", singleLine = false) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    val kinds = listOf(MessageKind.Card, MessageKind.Modal, MessageKind.Banner)
                    kinds.forEachIndexed { i, entry ->
                        ToggleButton(
                            checked = kind == entry,
                            onCheckedChange = { haptics.tick(); kind = entry },
                            colors = segmentColors(),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                kinds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(entry.label) }
                    }
                }
            }
            item { Field(button, { button = it.take(30) }, "Button text (optional)") }
            item { Field(link, { link = it }, "Link · https://… or rewordium://pro (optional)") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Also send as a push", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (user.pushToken == null) "Their phone hasn’t registered for push yet." else "Needs the push function (Firebase Blaze plan).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    com.noxquill.rewordium.app.ui.ExpressiveSwitch(alsoPush && user.pushToken != null) { if (user.pushToken != null) alsoPush = it }
                }
            }
            item {
                Button(
                    onClick = {
                        sending = true
                        scope.launch {
                            runCatching { AdminRepository.messageUser(user, title, body, kind.type, button, link, alsoPush) }
                                .onSuccess { haptics.success(); Toast.makeText(context, "Sent", Toast.LENGTH_SHORT).show(); onDismiss() }
                                .onFailure { haptics.reject(); Toast.makeText(context, it.message ?: "Couldn’t send it.", Toast.LENGTH_LONG).show() }
                            sending = false
                        }
                    },
                    enabled = !sending && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { if (sending) LoadingIndicator(Modifier.size(22.dp)) else Text("Send") }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Push ────────────────────────────────────────────────────────────────────

private val PUSH_ROUTES = listOf(
    null to "Nothing",
    "home" to "Home",
    "jade" to "Jade",
    "theme_shop" to "Theme shop",
    "pro" to "Pro",
    "keyboard_settings" to "Keyboard",
    "settings" to "Settings",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PushTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("all_users") }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val history = rememberLoadable { AdminRepository.pushHistory() }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = tabPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Push needs a server (Cloud Functions → Blaze plan). Until then the console's composer is the way.
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sending push from here needs Cloud Functions, which need Firebase’s Blaze plan. On the free plan, use the console’s composer: it targets the same “all_users” topic and supports a “route” custom data key.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(AdminRepository.CONSOLE_MESSAGING_URL))) }
                    }) { Text("Open Firebase Console › Messaging") }
                }
            }
        }
        item { SectionLabel("New push notification") }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AiDraft(maxBody = 140) { t, b -> title = t.take(65); body = b }
                    Field(title, { title = it.take(65) }, "Title")
                    Field(body, { body = it.take(240) }, "Message", singleLine = false)
                    Text("Send to", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        listOf("all_users" to "Everyone", "android" to "Android only").forEachIndexed { i, (value, label) ->
                            ToggleButton(
                                checked = topic == value,
                                onCheckedChange = { haptics.tick(); topic = value },
                                colors = segmentColors(),
                                shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                modifier = Modifier.weight(1f),
                            ) { Text(label) }
                        }
                    }
                    Text("Tapping it opens", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PUSH_ROUTES.forEach { (value, label) ->
                            FilterChip(selected = route == value, onClick = { haptics.tick(); route = value }, label = { Text(label) }, shape = RoundedCornerShape(50))
                        }
                    }
                    Text(
                        "Reaches people with notifications on. Those who turned them off in Settings won’t get it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { haptics.click(); confirm = true },
                        enabled = !sending && title.isNotBlank() && body.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (sending) LoadingIndicator(Modifier.size(22.dp)) else {
                            Icon(Icons.Filled.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send")
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                SectionLabel("Sent", Modifier.weight(1f))
                IconButton(onClick = history.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item {
            LoadableContent(history) { logs ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (logs.isEmpty()) Text("No pushes sent yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    logs.forEachIndexed { i, log ->
                        Surface(shape = segmentShape(i, logs.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(log.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    StatusPill(if (log.success) "Sent" else "Failed", active = log.success)
                                }
                                Text(log.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${log.target} · ${log.sentAt.short()}${log.error?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Send this push?") },
        text = { Text("“$title” goes to ${if (topic == "all_users") "everyone" else "everyone on Android"} with notifications on, right now.") },
        confirmButton = {
            Button(onClick = {
                confirm = false
                sending = true
                scope.launch {
                    runCatching { AdminRepository.sendPush(title, body, topic, route) }
                        .onSuccess {
                            haptics.success()
                            Toast.makeText(context, "Sent", Toast.LENGTH_SHORT).show()
                            title = ""; body = ""
                            history.reload()
                        }
                        .onFailure { haptics.reject(); Toast.makeText(context, it.message ?: "Couldn’t send it.", Toast.LENGTH_LONG).show() }
                    sending = false
                }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

// ── AI content reports ──────────────────────────────────────────────────────

@Composable
private fun ReportsTab(onOpenUser: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val state = rememberLoadable { AdminRepository.reports() }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = tabPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Flagged AI replies", Modifier.weight(1f))
                IconButton(onClick = state.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        }
        item {
            LoadableContent(state) { reports ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (reports.isEmpty()) Text("Nothing flagged. 🎉", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    reports.forEach { report ->
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(report.reason.ifBlank { "Report" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(report.createdAt.short(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (report.details.isNotBlank()) Text(report.details, style = MaterialTheme.typography.bodyMedium)
                                report.prompt?.takeIf { it.isNotBlank() }?.let {
                                    Text("Asked: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Text("Reply: ${report.reply}", style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { haptics.click(); onOpenUser(report.uid) }, enabled = report.uid.isNotBlank()) { Text("View user") }
                                    FilledTonalButton(onClick = {
                                        scope.launch {
                                            runCatching { AdminRepository.resolveReport(report.id) }
                                                .onSuccess { haptics.success(); state.reload() }
                                                .onFailure { Toast.makeText(context, it.message ?: "Couldn’t resolve it.", Toast.LENGTH_LONG).show() }
                                        }
                                    }) { Text("Resolve") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
