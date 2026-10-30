package com.noxquill.rewordium.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.noxquill.rewordium.app.ui.ExpressiveScaffold
import com.noxquill.rewordium.app.ui.Heading
import com.noxquill.rewordium.app.ui.display
import androidx.compose.ui.unit.sp
import com.noxquill.rewordium.app.ui.LegalLinksText
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import com.noxquill.rewordium.app.ui.ShapeBadge
import com.noxquill.rewordium.app.ui.StatusPill
import com.noxquill.rewordium.app.ui.Tone
import com.noxquill.rewordium.app.ui.ToneIcon
import com.noxquill.rewordium.app.ui.enterStagger
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.billing.bestOffer
import androidx.compose.ui.draw.alpha
import com.noxquill.rewordium.data.AccountInfo

private data class Perk(val title: String, val body: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: Tone, val shape: RoundedPolygon)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val perks by lazy {
    listOf(
        Perk("Unlimited Jade", "Chat, rewrite and tune your keyboard without counting credits.", Icons.Filled.AllInclusive, Tone.Primary, MaterialShapes.Cookie12Sided),
        Perk("Every writing skill", "Rewrite, grammar, tone, translate, summarize, humanize and more.", Icons.Filled.AutoAwesome, Tone.Tertiary, MaterialShapes.Sunny),
        Perk("AI in the keyboard", "The same writing power while you type in any app.", Icons.Filled.Keyboard, Tone.Secondary, MaterialShapes.Cookie4Sided),
        Perk("On your account", "Your plan follows you when you sign in on another phone.", Icons.Filled.Devices, Tone.Primary, MaterialShapes.Clover4Leaf),
    )
}

private const val MANAGE_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProScreen(
    account: AccountInfo?,
    billing: BillingUiState,
    packageName: String,
    onBack: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onOpenLink: (String) -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val isPro = account?.isPro == true
    ExpressiveScaffold(title = "Rewordium Pro", subtitle = if (isPro) "You’re all set" else "More room for your ideas", onBack = onBack, heading = Heading.SerifItalic) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth().enterStagger(0)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Nova on a whole Material soft burst that turns slowly, like a star.
                        ShapeBadge(
                            shape = MaterialShapes.SoftBurst,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            spin = true,
                            spinMillis = 32_000,
                            modifier = Modifier.size(156.dp),
                        ) {
                            BotAvatar(type = "star", mood = BotMood.Happy, size = 104.dp, label = "Rewordium Pro")
                        }
                        Text(if (isPro) "Pro is on. Write away!" else "Write without limits", style = Heading.SerifItalic.display(44.sp), textAlign = TextAlign.Center)
                        Text(
                            if (isPro) "${account?.planType?.replaceFirstChar(Char::uppercase)} plan · thanks for supporting Rewordium"
                            else "${account?.credits ?: 0} free credits left. Pro removes the limit.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item(key = "perks") {
                Column(Modifier.enterStagger(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    perks.forEach { perk ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ToneIcon(perk.icon, perk.tone, shape = perk.shape, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(perk.title, style = MaterialTheme.typography.titleSmall)
                                Text(perk.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            itemsIndexed(listOf(YEARLY_ID, MONTHLY_ID), key = { _, id -> id }) { index, id ->
                val product = billing.products.firstOrNull { it.productId == id }
                // Play is the truth about which plan this person pays for; the account is the fallback
                // (signed in on a phone with a different Play account, or Pro granted by us).
                val playPlan = billing.activePlans.firstOrNull()?.productId
                val currentPlan = playPlan ?: when {
                    !isPro -> null
                    account?.planType == "yearly" -> YEARLY_ID
                    account?.planType == "monthly" -> MONTHLY_ID
                    else -> GIFTED
                }
                PlanCard(
                    yearly = id == YEARLY_ID,
                    product = product,
                    monthly = billing.products.firstOrNull { it.productId == MONTHLY_ID },
                    loading = billing.loading,
                    state = when {
                        currentPlan == null -> PlanState.Available
                        currentPlan == id -> PlanState.Current
                        currentPlan == GIFTED || playPlan == null -> PlanState.Included
                        else -> PlanState.SwitchTo
                    },
                    modifier = Modifier.enterStagger(2 + index),
                    onChoose = { haptics.send(); onPurchase(id) },
                    onManage = { haptics.click(); onOpenLink("$MANAGE_SUBSCRIPTIONS?sku=$id&package=$packageName") },
                )
            }
            if (!billing.message.isNullOrBlank()) item(key = "message") {
                Text(billing.message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            item(key = "terms") {
                Column(Modifier.enterStagger(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Subscriptions are billed through Google Play and renew automatically at the price shown for each period until you cancel. " +
                            "Cancel any time in Google Play → Subscriptions; you keep Pro until the end of the period you’ve paid for. " +
                            "Any free trial or intro price applies once and then the regular price is charged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LegalLinksText("Subscribing means you agree to our", onTerms = onTerms, onPrivacy = onPrivacy, textAlign = TextAlign.Start)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { haptics.click(); onRestore() }) {
                            Icon(Icons.Filled.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore purchases")
                        }
                        TextButton(onClick = { haptics.click(); onOpenLink("$MANAGE_SUBSCRIPTIONS?package=$packageName") }) {
                            Text("Manage subscription")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private const val YEARLY_ID = "rewordium_pro_yearly"
private const val MONTHLY_ID = "rewordium_pro_monthly"
private const val GIFTED = "gifted"

/** Where this plan stands for the person looking at it. */
private enum class PlanState { Available, Current, SwitchTo, Included }

/**
 * One plan, priced live from Google Play. A discounted intro shows the regular price struck through beside
 * the sale price; a free trial leads with "Free". Yearly also shows what it works out to per month and how
 * much it saves over paying monthly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    yearly: Boolean,
    product: ProductDetails?,
    monthly: ProductDetails?,
    loading: Boolean,
    state: PlanState,
    modifier: Modifier,
    onChoose: () -> Unit,
    onManage: () -> Unit,
) {
    val offer = product?.bestOffer()
    val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
    val regular = phases.lastOrNull()
    val intro = phases.dropLast(1).firstOrNull()
    val trial = intro != null && intro.priceAmountMicros == 0L
    val discount = intro?.takeIf { regular != null && it.priceAmountMicros in 1 until regular.priceAmountMicros }
    val discountPercent = if (discount != null && regular != null) ((1 - discount.priceAmountMicros.toDouble() / regular.priceAmountMicros) * 100).toInt() else 0
    val monthlyRegular = monthly?.bestOffer()?.pricingPhases?.pricingPhaseList?.lastOrNull()
    val yearlySaving = if (yearly && regular != null && monthlyRegular != null && monthlyRegular.priceCurrencyCode == regular.priceCurrencyCode) {
        ((1 - regular.priceAmountMicros.toDouble() / (monthlyRegular.priceAmountMicros * 12)) * 100).toInt().takeIf { it > 0 }
    } else null
    val unit = if (yearly) "year" else "month"
    val current = state == PlanState.Current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = when {
            current -> MaterialTheme.colorScheme.tertiaryContainer
            yearly -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            current -> MaterialTheme.colorScheme.onTertiaryContainer
            yearly -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (yearly) "Yearly" else "Monthly", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current) StatusPill("Your plan", active = true)
                    if (discount != null && discountPercent > 0) StatusPill("$discountPercent% off", active = true)
                    if (trial) StatusPill("Free trial", active = true)
                    if (yearlySaving != null && !current) StatusPill("Save $yearlySaving%", active = false)
                }
            }
            when {
                loading && product == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator(Modifier.size(30.dp))
                    Text("Getting prices from Google Play…", style = MaterialTheme.typography.bodyMedium)
                }
                regular == null -> Text("Price unavailable right now", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            current -> PriceText(regular.formattedPrice, unit)
                            trial -> Text("Free", style = Heading.Serif.display(40.sp))
                            discount != null -> {
                                PriceText(discount.formattedPrice, unit)
                                Text(
                                    regular.formattedPrice,
                                    style = MaterialTheme.typography.titleMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                                    modifier = Modifier.padding(bottom = 6.dp).alpha(0.7f),
                                )
                            }
                            else -> PriceText(regular.formattedPrice, unit)
                        }
                    }
                    val detail = when {
                        current -> null
                        trial -> "Free for ${periodText(intro!!.billingPeriod)}, then ${regular.formattedPrice} per $unit"
                        discount != null -> "${discount.formattedPrice} per $unit for ${introLength(discount)}, then ${regular.formattedPrice}"
                        else -> null
                    }
                    detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (yearly) Text("That’s ${money(regular.priceAmountMicros / 12, regular.priceCurrencyCode)} a month", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (current) "Renews automatically every $unit · manage or cancel in Google Play"
                        else "Renews automatically every $unit · cancel any time",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            when (state) {
                PlanState.Current -> androidx.compose.material3.OutlinedButton(
                    onClick = onManage,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Manage in Google Play", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                }
                PlanState.Included -> Button(onClick = {}, enabled = false, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Included with your Pro", style = MaterialTheme.typography.titleMedium)
                }
                PlanState.SwitchTo, PlanState.Available -> Button(
                    onClick = onChoose,
                    enabled = !loading && product != null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(
                        when {
                            state == PlanState.SwitchTo && yearly -> yearlySaving?.let { "Switch to yearly · save $it%" } ?: "Switch to yearly"
                            state == PlanState.SwitchTo -> "Switch to monthly"
                            trial -> "Start free trial"
                            discount != null -> "Get $discountPercent% off"
                            else -> "Choose ${if (yearly) "yearly" else "monthly"}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceText(price: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(price, style = Heading.Serif.display(40.sp))
        Text("/ $unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
    }
}

/** How long a discounted intro lasts: "3 months", or "1 year". */
private fun introLength(phase: ProductDetails.PricingPhase): String {
    val cycles = phase.billingCycleCount.coerceAtLeast(1)
    val match = Regex("P(\\d+)([DWMY])").find(phase.billingPeriod) ?: return periodText(phase.billingPeriod)
    val count = (match.groupValues[1].toIntOrNull() ?: 1) * cycles
    return periodText("P$count${match.groupValues[2]}")
}

private fun money(micros: Long, currency: String): String =
    runCatching {
        java.text.NumberFormat.getCurrencyInstance().apply { this.currency = java.util.Currency.getInstance(currency) }.format(micros / 1_000_000.0)
    }.getOrDefault("$currency ${micros / 1_000_000.0}")

/** "P1W" → "1 week", "P3M" → "3 months", "P1Y" → "1 year". */
private fun periodText(iso: String): String {
    val match = Regex("P(\\d+)([DWMY])").find(iso) ?: return iso
    val count = match.groupValues[1].toIntOrNull() ?: return iso
    val unit = when (match.groupValues[2]) {
        "D" -> "day"
        "W" -> "week"
        "M" -> "month"
        else -> "year"
    }
    return "$count $unit${if (count == 1) "" else "s"}"
}
