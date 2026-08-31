package com.kosd.log_inattendancesafeguard.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kosd.log_inattendancesafeguard.models.Event
import com.kosd.log_inattendancesafeguard.network.KioskAuthClient
import com.kosd.log_inattendancesafeguard.ui.theme.KioskSuccessOverlay
import com.kosd.log_inattendancesafeguard.ui.theme.OtpTextField
import com.kosd.log_inattendancesafeguard.ui.theme.rememberHaptics
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KioskModeScreen(
    navController: NavController,
    event: Event,
    eventViewModel: EventViewModel = viewModel(factory = EventViewModel.Factory())
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var successCount by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var otpShakeTrigger by remember { mutableStateOf(0) }
    var otpIsError by remember { mutableStateOf(false) }

    // Kiosk step: 1 = enter details, 2 = enter OTP code
    var kioskStep by remember { mutableStateOf(1) }

    val qrToken by remember { derivedStateOf { eventViewModel.qrToken } }
    val liveCount by remember { derivedStateOf { eventViewModel.liveCount } }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error as snackbar instead of persistent text
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            errorMsg = null
        }
    }

    LaunchedEffect(event.id) {
        eventViewModel.startLiveCountPolling(event.id)
    }
    DisposableEffect(Unit) {
        onDispose { eventViewModel.stopLiveCountPolling() }
    }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(3000)
            showSuccess = false
            fullName = ""
            email = ""
            otpCode = ""
            kioskStep = 1
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (showSuccess) {
            // ── Animated success screen ───────────────────────────────────────
            KioskSuccessOverlay(
                fullName = fullName,
                attendeeNumber = successCount
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Header ─────────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                ) {
                    Text(event.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    event.venueName?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${liveCount?.checkedIn ?: 0} checked in",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.weight(1f))

                if (kioskStep == 1) {
                    // ── Step 1: Enter name + email ──────────────────────────────
                    Text("Step 1 of 2: Your Details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))

                    OutlinedTextField(
                        value = fullName, onValueChange = { fullName = it },
                        label = { Text("Full Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("Email *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    Text("A 6-digit verification code will be sent to your email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))

                    Button(
                        onClick = {
                            haptics.tap()
                            scope.launch {
                                isLoading = true
                                val sent = KioskAuthClient.sendOtp(email)
                                isLoading = false
                                if (sent) {
                                    haptics.success()
                                    kioskStep = 2
                                } else {
                                    haptics.error()
                                    errorMsg = "Failed to send code. Check the email address."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        enabled = fullName.isNotEmpty() && email.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("SEND CODE", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ── Step 2: Enter OTP code ──────────────────────────────────
                    Text("Step 2 of 2: Enter Verification Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))

                    Text("Code sent to $email",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))

                    OtpTextField(
                        otpText = otpCode,
                        onOtpChange = { otpCode = it; otpIsError = false },
                        shakeTrigger = otpShakeTrigger,
                        isError = otpIsError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            haptics.tap()
                            scope.launch {
                                isLoading = true
                                // 1. Verify the OTP code → get verified email
                                val verifiedEmail = KioskAuthClient.verifyOtp(email, otpCode)
                                if (verifiedEmail == null) {
                                    isLoading = false
                                    haptics.error()
                                    otpIsError = true
                                    otpShakeTrigger++
                                    errorMsg = "Invalid or expired code. Try again."
                                    return@launch
                                }
                                // 2. Check in via the main client (admin session)
                                //    Pass user_id = null so the guest path kicks in,
                                //    which deduplicates by email_hash via guest_profiles
                                val token = qrToken
                                if (token != null) {
                                    eventViewModel.checkIn(
                                        eventId = event.id,
                                        token = token,
                                        fullName = fullName,
                                        email = verifiedEmail,
                                        userId = null,
                                        guestId = null,
                                        latitude = null,
                                        longitude = null,
                                        checkInMethod = "kiosk"
                                    )
                                }
                                isLoading = false
                                // Check result
                                val result = eventViewModel.checkInResult
                                if (result?.success == true) {
                                    haptics.success()
                                    successCount = (liveCount?.checkedIn ?: 0) + 1
                                    showSuccess = true
                                } else {
                                    haptics.error()
                                    errorMsg = result?.message ?: "Check-in failed"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        enabled = otpCode.length == 6 && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("VERIFY & CHECK IN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Back to step 1
                    TextButton(
                        onClick = {
                            kioskStep = 1
                            otpCode = ""
                            errorMsg = null
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Use a different email") }
                }

                Spacer(Modifier.weight(1f))

                // ── Exit ───────────────────────────────────────────────────────
                TextButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Exit Kiosk Mode") }
            }
        }
    }
    }
}
