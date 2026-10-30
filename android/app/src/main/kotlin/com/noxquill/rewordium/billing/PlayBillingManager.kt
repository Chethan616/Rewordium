package com.noxquill.rewordium.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val MONTHLY_ID = "rewordium_pro_monthly"
private const val YEARLY_ID = "rewordium_pro_yearly"

data class BillingUiState(
    val products: List<ProductDetails> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    /** The Pro subscriptions Google Play says this Play account has right now. */
    val activePlans: List<ActivePlan> = emptyList(),
)

data class ActivePlan(val productId: String, val purchaseToken: String, val autoRenewing: Boolean)

/**
 * The offer this person gets for [this] plan: Play only lists offers they're eligible for, so the cheapest
 * first phase wins — a free trial, then an intro discount, then the base plan. The plan card and the
 * purchase both use this, so the price shown is the price charged.
 */
fun ProductDetails.bestOffer(): ProductDetails.SubscriptionOfferDetails? =
    subscriptionOfferDetails.orEmpty().minWithOrNull(
        compareBy<ProductDetails.SubscriptionOfferDetails>(
            { it.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE },
            { if (it.offerId == null) 1 else 0 },
        ),
    )

class PlayBillingManager(
    context: Context,
    private val onState: (BillingUiState) -> Unit,
    private val onPurchaseGranted: suspend (
        productId: String,
        token: String,
        purchaseTimeMillis: Long,
        autoRenewing: Boolean,
        restored: Boolean,
    ) -> Boolean,
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state = BillingUiState()
    private val inFlightTokens = mutableSetOf<String>()
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (billingClient.isReady) {
            loadProducts()
            return
        }
        publish(state.copy(loading = true, message = null))
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                } else {
                    publish(state.copy(loading = false, message = "Google Play billing is unavailable right now."))
                }
            }

            override fun onBillingServiceDisconnected() {
                publish(state.copy(loading = false, message = "Reconnect to Google Play to load plan prices."))
            }
        })
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        publish(state.copy(loading = true, message = "Checking your purchases…"))
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                publish(state.copy(loading = false, message = "We couldn’t check purchases just now."))
                return@queryPurchasesAsync
            }
            if (purchases.isEmpty()) {
                publish(state.copy(loading = false, message = "No active Rewordium Pro plan was found."))
            } else {
                purchases.forEach { processPurchase(it, restored = true) }
                publish(state.copy(loading = false, message = null))
            }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        val product = state.products.firstOrNull { it.productId == productId }
        if (product == null) {
            publish(state.copy(message = "Plan prices are still loading. Try again in a moment."))
            connect()
            return
        }
        if (state.activePlans.any { it.productId == productId }) {
            publish(state.copy(message = "That’s already your plan."))
            return
        }
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
        product.bestOffer()?.offerToken?.let(paramsBuilder::setOfferToken)
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
        // Already subscribed to the other plan: switch it (Play credits the unused time) instead of
        // starting a second subscription.
        state.activePlans.firstOrNull { it.productId != productId }?.let { current ->
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.purchaseToken)
                    .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
                    .build(),
            )
        }
        val params = builder.build()
        publish(state.copy(message = null))
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            publish(state.copy(message = billingMessage(result.responseCode)))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { processPurchase(it, restored = false) }
            BillingClient.BillingResponseCode.USER_CANCELED -> publish(state.copy(message = "Purchase canceled."))
            else -> publish(state.copy(message = billingMessage(result.responseCode)))
        }
    }

    fun close() {
        scope.cancel()
        if (billingClient.isReady) billingClient.endConnection()
    }

    private fun loadProducts() {
        val products = listOf(MONTHLY_ID, YEARLY_ID).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, productResult ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productResult.productDetailsList
            } else emptyList()
            val message = if (found.isEmpty()) "Plans are unavailable in Google Play right now." else null
            publish(state.copy(products = found, loading = false, message = message))
            refreshActivePlans()
        }
    }

    /** Asks Play which Pro plans this Play account has, quietly, so the plan cards show the truth. */
    private fun refreshActivePlans() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val active = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .mapNotNull { purchase -> purchase.products.firstOrNull()?.let { ActivePlan(it, purchase.purchaseToken, purchase.isAutoRenewing) } }
            publish(state.copy(activePlans = active))
        }
    }

    private fun processPurchase(purchase: Purchase, restored: Boolean) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            publish(state.copy(loading = false, message = "Payment pending. Pro will unlock after Google Play confirms it."))
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!inFlightTokens.add(purchase.purchaseToken)) return
        val productId = purchase.products.firstOrNull() ?: return
        publish(state.copy(loading = true, message = if (restored) "Restoring Rewordium Pro…" else "Finishing your purchase…"))
        scope.launch {
            val granted = runCatching {
                onPurchaseGranted(productId, purchase.purchaseToken, purchase.purchaseTime, purchase.isAutoRenewing, restored)
            }.getOrDefault(false)
            if (granted && !purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    val message = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        "Rewordium Pro is active. Thank you for supporting us."
                    } else {
                        "Your plan is active. Google Play is finishing the receipt confirmation."
                    }
                    publish(state.copy(loading = false, message = message))
                    inFlightTokens.remove(purchase.purchaseToken)
                    refreshActivePlans()
                }
            } else {
                publish(state.copy(
                    loading = false,
                    message = if (granted) "Rewordium Pro is active. Thank you for supporting us."
                    else "Sign in to Rewordium, then restore your purchase to link Pro to your account.",
                ))
                inFlightTokens.remove(purchase.purchaseToken)
                refreshActivePlans()
            }
        }
    }

    private fun publish(next: BillingUiState) {
        state = next
        onState(next)
    }

    private fun billingMessage(code: Int): String = when (code) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play billing is unavailable on this device."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "That plan is unavailable in your Play Store region."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play is temporarily unavailable. Try again shortly."
        else -> "We couldn’t complete the purchase. Please try again."
    }
}
