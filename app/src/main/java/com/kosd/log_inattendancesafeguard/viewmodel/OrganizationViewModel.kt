package com.kosd.log_inattendancesafeguard.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kosd.log_inattendancesafeguard.models.*
import com.kosd.log_inattendancesafeguard.models.EventStaffMember
import com.kosd.log_inattendancesafeguard.repository.OrganizationRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class OrganizationViewModel(private val repository: OrganizationRepository) : ViewModel() {

    var organizations       by mutableStateOf<List<Organization>>(emptyList())
    var myMemberships        by mutableStateOf<List<OrgMember>>(emptyList())
    var activeOrg            by mutableStateOf<Organization?>(null)
    var activeMembership     by mutableStateOf<OrgMember?>(null)
    var activePermissions    by mutableStateOf<Set<Permission>>(emptySet())
    var memberPermissions    by mutableStateOf<Map<String, Set<Permission>>>(emptyMap())
    var members              by mutableStateOf<List<Member>>(emptyList())
    var inviteCodes          by mutableStateOf<List<InviteCode>>(emptyList())
    var attendanceRules      by mutableStateOf<List<AttendanceRule>>(emptyList())
    var eventStaff           by mutableStateOf<List<EventStaffMember>>(emptyList())
    var isLoading            by mutableStateOf(false)
    var errorMessage         by mutableStateOf<String?>(null)
    var showError            by mutableStateOf(false)
    var successMessage       by mutableStateOf<String?>(null)
    var showSuccess          by mutableStateOf(false)

    val isAdminInActiveOrg: Boolean get() = activeMembership?.isAdmin ?: false
    val isOwnerInActiveOrg: Boolean get() = activeMembership?.role?.isOwner ?: false
    val isEventStaffInActiveOrg: Boolean get() = activeMembership?.role?.isEventStaff ?: false
    val myRoleInActiveOrg: String get() = activeMembership?.role?.displayName ?: ""
    fun can(permission: Permission): Boolean = isOwnerInActiveOrg || permission in activePermissions

    /** First active geofence rule for the active org (used by check-in screen). */
    val activeRule: AttendanceRule?
        get() = attendanceRules.firstOrNull { it.isActive }

    fun loadOrganizations() {
        viewModelScope.launch {
            loadOrganizationsAwait()
        }
    }

    /**
     * Suspending version of loadOrganizations.
     * Awaits the org list load and switchOrg so the caller knows
     * activeOrg / activeMembership are populated.
     */
    suspend fun loadOrganizationsAwait() {
        isLoading = true
        when (val result = repository.getOrganizations()) {
            is Result.Success -> {
                organizations = result.data
                if (result.data.isNotEmpty()) {
                    if (activeOrg == null) {
                        switchOrgAwait(result.data.first())
                    } else {
                        // Already have an active org — just refresh membership
                        val current = activeOrg!!
                        if (result.data.any { it.id == current.id }) {
                            switchOrgAwait(current)
                        } else if (result.data.isNotEmpty()) {
                            switchOrgAwait(result.data.first())
                        }
                    }
                } else {
                    activeOrg = null
                    activeMembership = null
                }
            }
            is Result.Error -> showError(result.message)
        }
        isLoading = false
    }

    fun switchOrg(org: Organization) {
        activeOrg = org
        attendanceRules = emptyList()
        viewModelScope.launch {
            when (val result = repository.getMyRoleInOrg(org.id)) {
                is Result.Success -> { activeMembership = result.data; loadActivePermissions(org.id) }
                is Result.Error   -> { activeMembership = null; activePermissions = emptySet() }
            }
        }
        loadRules(org.id)
    }

    /**
     * Suspending version of switchOrg.
     * Awaits the role lookup so the caller knows activeMembership is populated.
     */
    suspend fun switchOrgAwait(org: Organization) {
        activeOrg = org
        attendanceRules = emptyList()
        when (val result = repository.getMyRoleInOrg(org.id)) {
            is Result.Success -> { activeMembership = result.data; loadActivePermissionsAwait(org.id) }
            is Result.Error   -> { activeMembership = null; activePermissions = emptySet() }
        }
        loadRules(org.id)
    }

    fun joinByInviteCode(code: String) {
        viewModelScope.launch {
            joinByInviteCodeAwait(code)
            // result handled in await version
        }
    }

    /**
     * Suspending version of joinByInviteCode.
     * Returns the joined Organization or null on failure.
     */
    suspend fun joinByInviteCodeAwait(code: String): Organization? {
        isLoading = true
        return when (val result = repository.joinByInviteCode(code.trim())) {
            is Result.Success -> {
                organizations = organizations + result.data
                switchOrgAwait(result.data)
                successMessage = "Joined ${result.data.name}!"
                showSuccess = true
                isLoading = false
                result.data
            }
            is Result.Error -> {
                showError(result.message)
                isLoading = false
                null
            }
        }
    }

    fun createOrganization(
        name: String, description: String?,
        timezone: String?, maxMembers: Int?,
        onCreated: (Organization) -> Unit
    ) {
        viewModelScope.launch {
            val result = createOrganizationAwait(name, description, timezone, maxMembers)
            if (result != null) onCreated(result)
        }
    }

    /**
     * Suspending version of createOrganization.
     * Creates the org, switches to it (loading membership), and returns the org
     * or null on failure (with error set on this ViewModel).
     */
    suspend fun createOrganizationAwait(
        name: String, description: String?,
        timezone: String?, maxMembers: Int?
    ): Organization? {
        isLoading = true
        val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
        val resolvedMax = maxMembers ?: 100
        val populationTier = when {
            resolvedMax <= 10      -> PopulationTier.UNDER_10
            resolvedMax <= 50      -> PopulationTier.T_10_50
            resolvedMax <= 100     -> PopulationTier.T_50_100
            resolvedMax <= 500     -> PopulationTier.T_100_500
            resolvedMax <= 1000    -> PopulationTier.T_500_1000
            resolvedMax <= 10000   -> PopulationTier.T_1K_10K
            resolvedMax <= 100000  -> PopulationTier.T_10K_100K
            resolvedMax <= 1000000 -> PopulationTier.T_100K_1M
            else -> PopulationTier.T_OVER_1M
        }
        val result = repository.createOrganization(
            OrganizationCreateRequest(
                name = name.trim(),
                slug = slug,
                description = description?.ifBlank { null },
                timezone = timezone ?: "UTC",
                maxMembers = resolvedMax,
                populationTier = populationTier
            )
        )
        return when (result) {
            is Result.Success -> {
                activeOrg = result.data
                organizations = organizations + result.data
                switchOrgAwait(result.data)
                successMessage = "Organization created!"
                showSuccess = true
                isLoading = false
                result.data
            }
            is Result.Error -> {
                showError(result.message)
                isLoading = false
                null
            }
        }
    }

    fun updateOrganization(id: String, request: OrganizationUpdateRequest) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.updateOrganization(id, request)) {
                is Result.Success -> {
                    activeOrg = result.data
                    organizations = organizations.map { if (it.id == id) result.data else it }
                    successMessage = "Organization updated!"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun loadMembers(orgId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getMembers(orgId)) {
                is Result.Success -> {
                    members = result.data
                    if (isOwnerInActiveOrg) when (val permissions = repository.getOrganizationPermissions(orgId)) {
                        is Result.Success -> memberPermissions = permissions.data
                        is Result.Error -> memberPermissions = emptyMap()
                    }
                }
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    private fun loadActivePermissions(orgId: String) {
        viewModelScope.launch { loadActivePermissionsAwait(orgId) }
    }

    private suspend fun loadActivePermissionsAwait(orgId: String) {
        val membership = activeMembership
        if (membership == null || membership.role != UserRole.ADMIN) { activePermissions = emptySet(); return }
        activePermissions = when (val result = repository.getMyPermissions(orgId, membership.id)) {
            is Result.Success -> result.data
            is Result.Error -> emptySet()
        }
    }

    fun promoteAdmin(orgId: String, memberId: String, permissions: Set<Permission>) = adminMutation(orgId) {
        repository.promoteAdmin(orgId, memberId, permissions)
    }

    fun changeAdminPermissions(orgId: String, memberId: String, permissions: Set<Permission>) = adminMutation(orgId) {
        repository.changeAdminPermissions(orgId, memberId, permissions)
    }

    fun demoteAdmin(orgId: String, memberId: String) = adminMutation(orgId) {
        repository.demoteAdmin(orgId, memberId)
    }

    private fun adminMutation(orgId: String, operation: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            isLoading = true
            when (val result = operation()) {
                is Result.Success -> { loadMembers(orgId); loadActivePermissionsAwait(orgId); successMessage = "Administrator access updated."; showSuccess = true }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun addMember(orgId: String, email: String, employeeId: String?, department: String?, position: String?) {
        viewModelScope.launch {
            isLoading = true
            showError("Adding members by email requires backend support. Share your invite code instead.")
            isLoading = false
        }
    }

    fun removeMember(orgId: String, memberId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.removeMember(orgId, memberId)) {
                is Result.Success -> {
                    members = members.filter { it.id != memberId }
                    successMessage = "Member removed."
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun loadInviteCodes(orgId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getInviteCodes(orgId)) {
                is Result.Success -> inviteCodes = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun createInviteCode(orgId: String, maxUses: Int?, expiresAt: String?, role: String = "member") {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.createInviteCode(
                orgId,
                InviteCodeCreateRequest(organizationId = orgId, maxUses = maxUses, expiresAt = expiresAt, role = role)
            )) {
                is Result.Success -> {
                    inviteCodes = inviteCodes + result.data
                    successMessage = "Invite code created: ${result.data.code}"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun loadRules(orgId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getRules(orgId)) {
                is Result.Success -> attendanceRules = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun createRule(orgId: String, request: AttendanceRuleCreateRequest) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.createRule(orgId, request)) {
                is Result.Success -> {
                    // New rule overrides old: re-fetch so prior rules show as inactive.
                    loadRules(orgId)
                    successMessage = "Rule created and set as active."
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun deleteRule(ruleId: String, orgId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.deleteRule(ruleId)) {
                is Result.Success -> {
                    attendanceRules = attendanceRules.filter { it.id != ruleId }
                    successMessage = "Rule deleted."
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    /** Clears all per-user state. Call on logout to prevent stale data leaking across sessions. */
    fun resetState() {
        organizations = emptyList()
        myMemberships = emptyList()
        activeOrg = null
        activeMembership = null
        activePermissions = emptySet()
        memberPermissions = emptyMap()
        members = emptyList()
        inviteCodes = emptyList()
        attendanceRules = emptyList()
        isLoading = false
        errorMessage = null; showError = false
        successMessage = null; showSuccess = false
    }

    fun dismissError()   { showError = false; errorMessage = null }
    fun dismissSuccess() { showSuccess = false; successMessage = null }
    private fun showError(msg: String) { errorMessage = msg; showError = true }

    // ── Event Staff management (owner only) ───────────────────────────────────

    fun loadEventStaff(orgId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getEventStaff(orgId)) {
                is Result.Success -> eventStaff = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun grantEventStaff(memberId: String, orgId: String) {
        viewModelScope.launch {
            when (val result = repository.grantEventStaff(memberId, orgId)) {
                is Result.Success -> {
                    loadEventStaff(orgId)
                    loadMembers(orgId)
                    successMessage = "Event staff privilege granted"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
        }
    }

    fun revokeEventStaff(memberId: String, orgId: String) {
        viewModelScope.launch {
            when (val result = repository.revokeEventStaff(memberId, orgId)) {
                is Result.Success -> {
                    loadEventStaff(orgId)
                    loadMembers(orgId)
                    successMessage = "Event staff privilege revoked"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OrganizationViewModel(OrganizationRepository()) as T
    }
}
