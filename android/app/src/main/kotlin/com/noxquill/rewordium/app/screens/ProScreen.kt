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
            itemsIndexed(listOf("rewordium_pro_yearly", "rewordium_pro_monthly"), key = { _, id -> id }) { index, id ->
                val product = billing.products.firstOrNull { it.productId == id }
                PlanCard(
                    yearly = id.endsWith("yearly"),
                    product = product,
                    loading = billing.loading,
                    isPro = isPro,
                    modifier = Modifier.enterStagger(2 + index),
                    onChoose = { haptics.send(); onPurchase(id) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanCard(yearly: Boolean, product: ProductDetails?, loading: Boolean, isPro: Boolean, modifier: Modifier, onChoose: () -> Unit) {
    val offers = product?.subscriptionOfferDetails.orEmpty()
    val offer = offers.firstOrNull { it.offerId == "special-discount" } ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
    val regular = phases.lastOrNull()
    val intro = phases.dropLast(1).firstOrNull()
    val unit = if (yearly) "year" else "month"
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (yearly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (yearly) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (yearly) "Yearly" else "Monthly", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (yearly) StatusPill("Best value", active = true)
            }
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator(Modifier.size(30.dp))
                    Text("Getting prices from Google Play…", style = MaterialTheme.typography.bodyMedium)
                }
                regular == null -> Text("Price unavailable right now", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(regular.formattedPrice, style = Heading.Serif.display(40.sp))
                        Text("/ $unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (intro != null) Text(
                        if (intro.priceAmountMicros == 0L) "Free for ${periodText(intro.billingPeriod)}, then ${regular.formattedPrice} per $unit"
                        else "${intro.formattedPrice} for ${periodText(intro.billingPeriod)}, then ${regular.formattedPrice} per $unit",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Renews automatically every $unit · cancel any time", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onChoose,
                enabled = !loading && product != null && !isPro,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (isPro) "Active" else "Choose ${if (yearly) "yearly" else "monthly"}", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

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
