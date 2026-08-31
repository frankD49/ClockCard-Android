package com.kosd.log_inattendancesafeguard.services

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
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Google Play Billing for subscription products that unlock organisation
 * population tiers (and CSV export / printing). The product IDs come from
 * [com.kosd.log_inattendancesafeguard.models.PopulationTier.productId] and must
 * be configured in the Play Console as subscriptions before purchases will
 * succeed in production.
 *
 * In debug builds where products are not configured, [productDetails] will be
 * empty and the purchase flow will surface a helpful error.
 */
class BillingService(context: Context) {

    private val appContext = context.applicationContext

    private val _purchasedProductIds = MutableStateFlow<Set<String>>(emptySet())
    val purchasedProductIds: StateFlow<Set<String>> = _purchasedProductIds.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Called when a purchase is confirmed. The receiver should sync the tier
     *  to the Supabase backend via the update_organization_tier RPC. */
    var onPurchaseConfirmed: ((productId: String) -> Unit)? = null

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { handlePurchase(it) }
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    /** Idempotent — safe to call multiple times. */
    fun startConnection(productIds: List<String>, onReady: (() -> Unit)? = null) {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            queryProducts(productIds)
            queryPurchases()
            onReady?.invoke()
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = ConnectionState.CONNECTED
                    queryProducts(productIds)
                    queryPurchases()
                    onReady?.invoke()
                } else {
                    _connectionState.value = ConnectionState.FAILED
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    private fun queryProducts(productIds: List<String>) {
        if (productIds.isEmpty()) return
        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = list.associateBy { it.productId }
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
                _purchasedProductIds.value = purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { it.products }
                    .toSet()
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        _purchasedProductIds.value = _purchasedProductIds.value + purchase.products
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* ignore result */ }
        }
        // Notify listener so it can sync the tier to the backend
        purchase.products.forEach { productId ->
            onPurchaseConfirmed?.invoke(productId)
        }
    }

    /**
     * Launch the Play purchase UI for the given product. Returns null on success,
     * or an error message describing why the flow could not start.
     */
    fun launchPurchase(activity: Activity, productId: String): String? {
        val details = _productDetails.value[productId]
            ?: return "This subscription is not yet available. Check that the product " +
                "'$productId' is configured in the Play Console and the app is " +
                "published to a test track."
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return "No subscription offer is available for $productId."

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null
        else result.debugMessage.ifBlank { "Billing error code ${result.responseCode}" }
    }

    fun release() {
        if (client.isReady) client.endConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
