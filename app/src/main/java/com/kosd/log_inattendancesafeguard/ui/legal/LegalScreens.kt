package com.kosd.log_inattendancesafeguard.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosd.log_inattendancesafeguard.services.ConsentService
import com.kosd.log_inattendancesafeguard.services.ConsentType
import com.kosd.log_inattendancesafeguard.services.LegalDocument
import com.kosd.log_inattendancesafeguard.services.LegalService
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

// ── Legal Document Screen ────────────────────────────────────────────────────

@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    onAccept: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(document.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
        if (onAccept != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small
            ) { Text("Accept", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Privacy Policy Screen ────────────────────────────────────────────────────

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.privacyPolicy, onBack = onBack)
}

// ── Terms of Service Screen ──────────────────────────────────────────────────

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.termsOfService, onBack = onBack)
}

// ── Data Processing Addendum Screen ──────────────────────────────────────────

@Composable
fun DataProcessingAddendumScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.dataProcessingAddendum, onBack = onBack)
}

// ── Subprocessor Disclosure Screen ───────────────────────────────────────────

@Composable
fun SubprocessorDisclosureScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.subprocessorDisclosure, onBack = onBack)
}

// ── Refund Policy Screen ─────────────────────────────────────────────────────

@Composable
fun RefundPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.refundPolicy, onBack = onBack)
}

// ── Accessibility Statement Screen ───────────────────────────────────────────

@Composable
fun AccessibilityStatementScreen(onBack: () -> Unit) {
    LegalDocumentScreen(document = LegalService.accessibilityStatement, onBack = onBack)
}

// ── Location Consent Screen ──────────────────────────────────────────────────

@Composable
fun LocationConsentScreen(
    onDismiss: () -> Unit,
    onConsented: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Location Data Consent", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "ClockCard needs your permission to collect location data when you check in or check out.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        ConsentInfoRow(Icons.Default.LocationOn, "When location is collected",
            "Only during an explicit check-in or check-out action. We do NOT track your location continuously or in the background.")
        Spacer(Modifier.height(12.dp))
        ConsentInfoRow(Icons.Default.Security, "How location is used",
            "To verify you are within the geofence of your designated workplace, if your organization has enabled location verification.")
        Spacer(Modifier.height(12.dp))
        ConsentInfoRow(Icons.Default.Person, "Who can see it",
            "Your organization's administrators can see your check-in/out times. Other members cannot see your location.")
        Spacer(Modifier.height(12.dp))
        ConsentInfoRow(Icons.Default.PanTool, "Your right to withdraw",
            "You can withdraw location consent at any time in Settings. Check-in/out will still work but will be marked as 'location not verified.'")

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    val ok = ConsentService.recordConsent(
                        ConsentType.LOCATION_TRACKING,
                        LegalService.PRIVACY_POLICY_VERSION, true
                    )
                    isLoading = false
                    if (ok) onConsented()
                    else error = "Failed to record consent. Try again."
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("I Consent — Allow Location Collection", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            scope.launch {
                ConsentService.recordConsent(
                    ConsentType.LOCATION_TRACKING,
                    LegalService.PRIVACY_POLICY_VERSION, false
                )
                onConsented()
            }
        }) { Text("Decline — Check In Without Location", color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

// ── Monitoring Notice Screen ─────────────────────────────────────────────────

@Composable
fun MonitoringNoticeScreen(
    onDismiss: () -> Unit,
    onAcknowledged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text("Employee Monitoring Notice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(LegalService.monitoringNotice, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
        Spacer(Modifier.height(24.dp))
        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        ConsentService.recordConsent(
                            ConsentType.EMPLOYEE_MONITORING,
                            LegalService.MONITORING_NOTICE_VERSION, true,
                            LegalService.monitoringNotice
                        )
                        isLoading = false
                        onAcknowledged()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.small
            ) { Text("I Have Read and Understood This Notice", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

// ── Delete Account Screen ────────────────────────────────────────────────────

@Composable
fun DeleteAccountScreen(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var confirmText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    val confirmPhrase = "DELETE"

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text("Account Deleted") },
            text = { Text("Your account has been deleted. You will be signed out.") },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Text("Delete Account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("This action is permanent and cannot be undone.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("The following data will be permanently deleted:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Your profile (name, email)",
            "All your attendance records",
            "Your organization memberships",
            "Your consent records",
            "Your invite codes"
        ).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "If you are the owner of an organization, you must transfer ownership or delete the organization first.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        if (error != null) {
            Text(error!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Text("Type $confirmPhrase to confirm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = confirmText,
            onValueChange = { confirmText = it.uppercase() },
            placeholder = { Text(confirmPhrase) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                isLoading = true
                error = null
                scope.launch {
                    val ok = ConsentService.deleteAccount()
                    isLoading = false
                    if (ok) {
                        authViewModel.logout()
                        showSuccess = true
                    } else {
                        error = "Failed to delete account. You may be the owner of an organization. Transfer ownership first."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            enabled = !isLoading && confirmText == confirmPhrase,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
            else Text("Permanently Delete My Account", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

// ── Helper ───────────────────────────────────────────────────────────────────

@Composable
private fun ConsentInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
