package com.noxquill.rewordium.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.noxquill.rewordium.app.ui.avatar.BotAvatar
import com.noxquill.rewordium.app.ui.avatar.BotMood
import com.noxquill.rewordium.billing.BillingUiState
import com.noxquill.rewordium.data.AccountInfo
import com.noxquill.rewordium.app.ui.haptics.LocalHaptics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProScreen(
    account: AccountInfo?, billing: BillingUiState,
    onBack: () -> Unit, onSignIn: () -> Unit,
    onPurchase: (String) -> Unit, onRestore: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Rewordium Pro") }, navigationIcon = {
            androidx.compose.material3.IconButton(onClick = { haptics.click(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotAvatar(type = "star", mood = BotMood.Happy, size = 92.dp, label = "Rewordium Pro mascot")
                        Text("Make space for more ideas.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Unlimited writing tools, wherever you write.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                listOf("Unlimited Jade and writing tools", "Everyday access to your Rewordium keyboard", "Cancel anytime in Google Play").forEach { benefit ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("  $benefit", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            items(listOf("rewordium_pro_monthly", "rewordium_pro_yearly")) { id ->
                val product = billing.products.firstOrNull { it.productId == id }
                val offers = product?.subscriptionOfferDetails.orEmpty()
                val offer = offers.firstOrNull { it.offerId == "special-discount" }
                    ?: offers.firstOrNull { it.offerId == null }
                    ?: offers.firstOrNull()
                val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                val yearly = id.endsWith("yearly")
                ElevatedCard(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Stars, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (yearly) "Yearly" else "Monthly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (billing.loading) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    LoadingIndicator(Modifier.size(28.dp))
                                    Text("Connecting to Google Play", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else Text(price ?: "Price unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { if (account == null) onSignIn() else onPurchase(id) }, enabled = !billing.loading && product != null && account?.isPro != true) {
                            Text(if (account?.isPro == true) "Active" else if (account == null) "Sign in" else "Choose")
                        }
                    }
                }
            }
            if (!billing.message.isNullOrBlank()) item { Text(billing.message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            item {
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Text("  Restore purchases")
                }
            }
            item { Text("Plans are billed by Google Play. Manage or cancel your subscription from your Play Store account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
