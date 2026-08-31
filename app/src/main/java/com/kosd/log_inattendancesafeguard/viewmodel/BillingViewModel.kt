package com.kosd.log_inattendancesafeguard.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.kosd.log_inattendancesafeguard.ClockCardApp
import com.kosd.log_inattendancesafeguard.models.PopulationTier
import com.kosd.log_inattendancesafeguard.network.SupabaseClientProvider.client
import com.kosd.log_inattendancesafeguard.services.BillingService
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Surfaces in-app billing state to Compose.
 *
 * Free features (PopulationTier.UNDER_10) are always entitled. Paid features —
 * including CSV export / printing — require an active subscription whose
 * product ID matches the desired tier.
 */
class BillingViewModel(
    app: ClockCardApp,
    private val orgViewModel: OrganizationViewModel? = null
) : AndroidViewModel(app) {

    private val billing = BillingService(app)

    var purchasedProductIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var productDetails by mutableStateOf<Map<String, ProductDetails>>(emptyMap())
        private set
    var connectionState by mutableStateOf(BillingService.ConnectionState.DISCONNECTED)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /**
     * The set of tiers the current user has unlocked. Free tier is always included.
     * Paid tiers are unlocked when the corresponding subscription is owned.
     */
    val unlockedTiers: Set<PopulationTier>
        get() = buildSet {
            add(PopulationTier.UNDER_10)
            PopulationTier.values().forEach { tier ->
                val pid = tier.productId
                if (pid != null && pid in purchasedProductIds) add(tier)
            }
        }

    /** True if the user has any paid subscription and may export/print CSV. */
    val canExport: Boolean
        get() = unlockedTiers.any { !it.isFree }

    init {
        val productIds = PopulationTier.values().mapNotNull { it.productId }
        billing.startConnection(productIds)

        // Sync purchases to the Supabase backend as they come in
        billing.onPurchaseConfirmed = { productId ->
            viewModelScope.launch {
                syncTierToBackend(productId)
            }
        }

        viewModelScope.launch {
            billing.purchasedProductIds.collect { purchasedProductIds = it }
        }
        viewModelScope.launch {
            billing.productDetails.collect { productDetails = it }
        }
        viewModelScope.launch {
            billing.connectionState.collect { connectionState = it }
        }
    }

    fun isUnlocked(tier: PopulationTier): Boolean = tier in unlockedTiers

    fun priceText(tier: PopulationTier): String {
        val pid = tier.productId ?: return "Free"
        val details = productDetails[pid] ?: return "—"
        return details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
            ?: "—"
    }

    /** Begin Play Billing purchase for [tier]. */
    fun purchase(activity: Activity, tier: PopulationTier) {
        val pid = tier.productId
        if (pid == null) { lastError = "${tier.displayName} is free — no purchase needed."; return }
        val err = billing.launchPurchase(activity, pid)
        if (err != null) lastError = err
    }

    /**
     * Calls the update_organization_tier RPC to persist the new population tier
     * and max_members on the server. The server enforces max_members on all
     * member-join operations.
     */
    private suspend fun syncTierToBackend(productId: String) {
        val tier = PopulationTier.values().firstOrNull { it.productId == productId } ?: return
        val orgId = orgViewModel?.activeOrg?.id ?: return
        try {
            val params = buildJsonObject {
                put("p_organization_id", orgId)
                put("p_population_tier", tier.value)
            }
            client.postgrest.rpc("update_organization_tier", params)
        } catch (e: Exception) {
            lastError = "Failed to sync subscription to server: ${e.message}"
        }
    }

    fun dismissError() { lastError = null }

    override fun onCleared() {
        billing.release()
        super.onCleared()
    }

    class Factory(
        private val app: ClockCardApp,
        private val orgViewModel: OrganizationViewModel? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            BillingViewModel(app, orgViewModel) as T
    }
}
