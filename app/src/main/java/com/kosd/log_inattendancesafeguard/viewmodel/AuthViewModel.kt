package com.kosd.log_inattendancesafeguard.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import com.kosd.log_inattendancesafeguard.models.User
import com.kosd.log_inattendancesafeguard.repository.AuthRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import com.kosd.log_inattendancesafeguard.services.BiometricService
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthViewModel(val repository: AuthRepository) : ViewModel() {

    var currentUser    by mutableStateOf<User?>(null)
    var isAuthenticated  by mutableStateOf(false)
    var isLoading        by mutableStateOf(false)
    var errorMessage     by mutableStateOf<String?>(null)
    var showError        by mutableStateOf(false)
    var successMessage   by mutableStateOf<String?>(null)
    var showSuccess      by mutableStateOf(false)

    // ── Biometric Authentication state ─────────────────────────────────────
    var requiresBiometric by mutableStateOf(false)
    var biometricError    by mutableStateOf<String?>(null)

    // ── Email Confirmation state ───────────────────────────────────────────
    // Set to true after signUp when email confirmation is required.
    // The user must click the confirmation link in their email, which opens
    // the app via deep link (clockcard://auth-callback) and completes setup.
    var requiresEmailConfirmation by mutableStateOf(false)
    var pendingEmail: String? = null

    // Set after verify-signup succeeds → show login screen with success message.
    var emailConfirmed by mutableStateOf(false)

    // Internal: set during Resend signup, checked after sign-in to run
    // completeRegistrationAfterConfirmation (org creation / invite join).
    var pendingRegistrationCompletion by mutableStateOf(false)

    // The activity reference is set from the UI layer to enable BiometricPrompt
    var activity: FragmentActivity? = null

    private val biometricService: BiometricService? = null

    val userDisplayName: String get() = currentUser?.fullName?.ifBlank { currentUser?.email } ?: ""

    fun login(email: String, password: String, orgViewModel: OrganizationViewModel? = null) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.login(email.trim(), password)) {
                is Result.Success -> {
                    currentUser = result.data

                    // Load org state before authenticating so MainScreen has it.
                    if (orgViewModel != null) {
                        orgViewModel.loadOrganizationsAwait()
                    }

                    // Require biometric verification after successful login
                    val act = activity
                    if (act != null) {
                        val service = BiometricService(act)
                        if (service.isAuthenticationAvailable) {
                            requiresBiometric = true
                            biometricError = null
                        } else {
                            // No biometrics available — authenticate immediately
                            isAuthenticated = true
                        }
                    } else {
                        // No activity reference — authenticate immediately
                        isAuthenticated = true
                    }
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    /**
     * Triggers the biometric prompt. Must be called when [activity] is set
     * and [requiresBiometric] is true.
     */
    fun verifyBiometric() {
        val act = activity ?: run {
            biometricError = "Unable to launch biometric prompt."
            return
        }
        val service = BiometricService(act)
        service.authenticate(
            activity = act,
            onSuccess = {
                requiresBiometric = false
                biometricError = null
                isAuthenticated = true
            },
            onError = { msg ->
                biometricError = msg
            },
            onCancel = {
                // User cancelled — stay at biometric prompt
                biometricError = null
            }
        )
    }

    fun cancelBiometric() {
        requiresBiometric = false
        biometricError = null
        currentUser = null
    }

    fun register(
        email: String, password: String,
        firstName: String, lastName: String,
        inviteCode: String?,
        orgName: String? = null,
        orgViewModel: OrganizationViewModel? = null
    ) {
        viewModelScope.launch {
            isLoading = true

            // ── Invite-code path: bypasses email confirmation ──────────────
            // If the user has an invite code, use the Edge Function which
            // creates the user with email_confirm: true and joins the org
            // immediately. No confirmation email needed.
            if (!inviteCode.isNullOrBlank()) {
                when (val result = repository.inviteSignup(
                    email.trim(), password, firstName.trim(), lastName.trim(),
                    inviteCode.trim().uppercase()
                )) {
                    is Result.Success -> {
                        val data = result.data
                        if (data.requiresSignIn) {
                            // Account created but no session — sign in manually
                            login(email.trim(), password, orgViewModel)
                        } else {
                            // Session imported — load user and orgs
                            when (val userResult = repository.getCurrentUser()) {
                                is Result.Success -> {
                                    currentUser = userResult.data
                                    orgViewModel?.let { orgViewModel.loadOrganizationsAwait() }
                                    isAuthenticated = true
                                }
                                is Result.Error -> showError(userResult.message)
                            }
                        }
                    }
                    is Result.Error -> showError(result.message)
                }
                isLoading = false
                return@launch
            }

            // ── Owner path: signup via Resend (bypasses Supabase Auth email rate limit) ──
            // The send-email Edge Function creates the auth user (unconfirmed) and
            // emails a confirmation link via Resend. No session is returned.
            when (val result = repository.signupWithResend(
                email.trim(), password, firstName.trim(), lastName.trim(),
                orgName?.ifBlank { null }
            )) {
                is Result.Success -> {
                    // User created but unconfirmed — show "check your email" screen.
                    pendingEmail = email.trim()
                    requiresEmailConfirmation = true
                    pendingRegistrationCompletion = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    /**
     * Called after the email confirmation deep link callback succeeds.
     * The Auth plugin imports the session, then this method:
     * 1. Loads the current user profile
     * 2. Reads pending_org_name / pending_invite_code from user metadata
     * 3. Creates the org or joins via invite code
     * 4. Sets isAuthenticated = true
     */
    fun completeRegistrationAfterConfirmation(orgViewModel: OrganizationViewModel) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getCurrentUser()) {
                is Result.Success -> {
                    currentUser = result.data
                    requiresEmailConfirmation = false

                    // Load org state (await so activeOrg/activeMembership are populated)
                    orgViewModel.loadOrganizationsAwait()

                    // If user already has an org (from a previous session or
                    // the trigger already created one), just authenticate.
                    if (orgViewModel.activeOrg != null) {
                        pendingRegistrationCompletion = false
                        isAuthenticated = true
                        isLoading = false
                        return@launch
                    }

                    // Read pending org name / invite code from auth user metadata
                    val authUser = repository.getAuthUserMetadata()
                    val pendingOrgName = authUser?.get("pending_org_name")?.jsonPrimitive?.contentOrNull
                    val pendingInviteCode = authUser?.get("pending_invite_code")?.jsonPrimitive?.contentOrNull

                    var orgSetupSuccess = true

                    if (!pendingOrgName.isNullOrBlank()) {
                        val org = orgViewModel.createOrganizationAwait(
                            name = pendingOrgName,
                            description = null,
                            timezone = "UTC",
                            maxMembers = 100
                        )
                        if (org == null) orgSetupSuccess = false
                    } else if (!pendingInviteCode.isNullOrBlank()) {
                        val org = orgViewModel.joinByInviteCodeAwait(pendingInviteCode)
                        if (org == null) orgSetupSuccess = false
                    }

                    if (orgSetupSuccess) {
                        pendingRegistrationCompletion = false
                        isAuthenticated = true
                    } else {
                        showError(orgViewModel.errorMessage ?: "Failed to set up organization")
                    }
                }
                is Result.Error -> {
                    showError(result.message)
                }
            }
            isLoading = false
        }
    }

    // ── Resend Email Confirmation Verification ──────────────────────────────

    /**
     * Called when the app receives a deep link with a `token` query parameter
     * (from the Resend-sent confirmation email). Verifies the token via the
     * verify-signup Edge Function, then transitions to the login screen.
     *
     * After verification, the user signs in with their password. The
     * pendingRegistrationCompletion flag stays true so that
     * completeRegistrationAfterConfirmation runs after sign-in.
     */
    fun handleSignupConfirmationToken(token: String) {
        viewModelScope.launch {
            isLoading = true
            val confirmedEmail = repository.verifySignupConfirmation(token)

            if (confirmedEmail != null) {
                requiresEmailConfirmation = false
                emailConfirmed = true
                pendingEmail = confirmedEmail
                successMessage = "Email confirmed! Please sign in to continue."
                showSuccess = true
            } else {
                showError("This confirmation link is invalid or has expired. Please request a new one.")
            }
            isLoading = false
        }
    }

    fun loadCurrentUser(orgViewModel: OrganizationViewModel? = null) {
        viewModelScope.launch {
            isLoading = true
            if (repository.currentUserId() != null) {
                when (val result = repository.getCurrentUser()) {
                    is Result.Success -> {
                        currentUser = result.data

                        // Load org state before authenticating (await so
                        // activeOrg/activeMembership are populated).
                        if (orgViewModel != null) {
                            orgViewModel.loadOrganizationsAwait()
                        }

                        // Fallback: if user has no org but has pending org name /
                        // invite code in metadata (from registration with email
                        // confirmation), complete the org setup now.
                        if (orgViewModel != null && orgViewModel.activeOrg == null) {
                            val metadata = repository.getAuthUserMetadata()
                            val pendingOrgName = metadata?.get("pending_org_name")?.jsonPrimitive?.contentOrNull
                            val pendingInviteCode = metadata?.get("pending_invite_code")?.jsonPrimitive?.contentOrNull

                            if (!pendingOrgName.isNullOrBlank()) {
                                orgViewModel.createOrganizationAwait(
                                    name = pendingOrgName,
                                    description = null,
                                    timezone = "UTC",
                                    maxMembers = 100
                                )
                            } else if (!pendingInviteCode.isNullOrBlank()) {
                                orgViewModel.joinByInviteCodeAwait(pendingInviteCode)
                            }
                        }

                        // Require biometric re-verification on app launch
                        val act = activity
                        if (act != null) {
                            val service = BiometricService(act)
                            if (service.isAuthenticationAvailable) {
                                requiresBiometric = true
                            } else {
                                isAuthenticated = true
                            }
                        } else {
                            isAuthenticated = true
                        }
                    }
                    is Result.Error -> isAuthenticated = false
                }
            } else {
                isAuthenticated = false
            }
            isLoading = false
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        if (new != confirm) { showError("Passwords do not match"); return }
        if (new.length < 6) { showError("Password must be at least 6 characters"); return }
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.changePassword(current, new)) {
                is Result.Success -> { successMessage = "Password changed successfully"; showSuccess = true }
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            currentUser = null
            isAuthenticated = false
            requiresBiometric = false
            biometricError = null
        }
    }

    fun dismissError() { showError = false; errorMessage = null }
    fun dismissSuccess() { showSuccess = false; successMessage = null }

    private fun showError(message: String) {
        errorMessage = message
        showError = true
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(AuthRepository()) as T
    }
}
