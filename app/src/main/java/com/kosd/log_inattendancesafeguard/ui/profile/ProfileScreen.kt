package com.kosd.log_inattendancesafeguard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.models.Organization
import com.kosd.log_inattendancesafeguard.models.User
import com.kosd.log_inattendancesafeguard.ui.theme.statusContentColor
import com.kosd.log_inattendancesafeguard.ui.theme.statusContainerColor
import com.kosd.log_inattendancesafeguard.ui.theme.rememberHaptics
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    orgViewModel: OrganizationViewModel
) {
    val user: User? = authViewModel.currentUser
    var showCreateOrgDialog    by remember { mutableStateOf(false) }
    var showJoinOrgDialog       by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm      by remember { mutableStateOf(false) }
    var showPrivacyPolicy      by remember { mutableStateOf(false) }
    var showTermsOfService     by remember { mutableStateOf(false) }
    var showLocationConsent    by remember { mutableStateOf(false) }
    var showMonitoringNotice   by remember { mutableStateOf(false) }
    var showDeleteAccount      by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (orgViewModel.activeOrg == null) {
            orgViewModel.loadOrganizations()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Avatar & Info ────────────────────────────────────────────────────
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.initials ?: "?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.fullName ?: "Unknown", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                val roleLabel = orgViewModel.myRoleInActiveOrg.ifEmpty { "No org" }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        roleLabel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Organizations ────────────────────────────────────────────────────
        SectionHeader("Organizations", Icons.Default.Business)

        val organizations: List<Organization> = orgViewModel.organizations
        if (orgViewModel.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else if (organizations.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No organizations yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreateOrgDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create")
                        }
                        OutlinedButton(
                            onClick = { showJoinOrgDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Join")
                        }
                    }
                }
            }
        } else {
            organizations.forEach { org: Organization ->
                val isCurrentOrg = orgViewModel.activeOrg?.id == org.id
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentOrg) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                         else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(if (isCurrentOrg) 0.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrentOrg) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            else Modifier
                        )
                        .clickable { orgViewModel.switchOrg(org) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Business, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(org.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "${org.memberCount ?: 0}/${org.maxMembers} members · ${org.timezone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isCurrentOrg) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("Current", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (org.isActive) statusContainerColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (org.isActive) "Active" else "Inactive",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (org.isActive) statusContentColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showCreateOrgDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create")
                }
                OutlinedButton(
                    onClick = { showJoinOrgDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Join")
                }
            }
        }

        // ── Account Actions ──────────────────────────────────────────────────
        SectionHeader("Account", Icons.Default.ManageAccounts)

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileActionRow(
                    icon = Icons.Default.Lock,
                    label = "Change Password",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showChangePasswordDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileActionRow(
                    icon = Icons.Default.Logout,
                    label = "Sign Out",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showLogoutConfirm = true }
                )
            }
        }

        // ── Legal & Privacy (C1, C2, C5) ─────────────────────────────────────
        SectionHeader("Legal & Privacy", Icons.Default.Description)

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileActionRow(
                    icon = Icons.Default.PrivacyTip,
                    label = "Privacy Policy",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showPrivacyPolicy = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileActionRow(
                    icon = Icons.Default.Description,
                    label = "Terms of Service",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showTermsOfService = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileActionRow(
                    icon = Icons.Default.LocationOn,
                    label = "Location Consent",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { showLocationConsent = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ProfileActionRow(
                    icon = Icons.Default.Visibility,
                    label = "Monitoring Notice",
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = { showMonitoringNotice = true }
                )
            }
        }

        // ── Danger Zone (C3: Data Erasure) ───────────────────────────────────
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileActionRow(
                    icon = Icons.Default.Delete,
                    label = "Delete Account",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteAccount = true }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Create Org Dialog ─────────────────────────────────────────────────────
    if (showCreateOrgDialog) {
        CreateOrganizationDialog(
            viewModel = orgViewModel,
            onDismiss = { showCreateOrgDialog = false }
        )
    }

    // ── Join Org Dialog ───────────────────────────────────────────────────────
    if (showJoinOrgDialog) {
        JoinOrganizationDialog(
            viewModel = orgViewModel,
            onDismiss = { showJoinOrgDialog = false }
        )
    }

    // ── Change Password Dialog ────────────────────────────────────────────────
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            viewModel = authViewModel,
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    // ── Logout Confirm ────────────────────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon  = { Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Sign Out") },
            text  = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                Button(
                    onClick = { showLogoutConfirm = false; authViewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Legal & Privacy Screens (full-screen overlays) ────────────────────────
    if (showPrivacyPolicy) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.kosd.log_inattendancesafeguard.ui.legal.LegalDocumentScreen(
                document = com.kosd.log_inattendancesafeguard.services.LegalService.privacyPolicy,
                onBack = { showPrivacyPolicy = false }
            )
        }
    }
    if (showTermsOfService) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.kosd.log_inattendancesafeguard.ui.legal.LegalDocumentScreen(
                document = com.kosd.log_inattendancesafeguard.services.LegalService.termsOfService,
                onBack = { showTermsOfService = false }
            )
        }
    }
    if (showLocationConsent) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.kosd.log_inattendancesafeguard.ui.legal.LocationConsentScreen(
                onDismiss = { showLocationConsent = false },
                onConsented = { showLocationConsent = false }
            )
        }
    }
    if (showMonitoringNotice) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.kosd.log_inattendancesafeguard.ui.legal.MonitoringNoticeScreen(
                onDismiss = { showMonitoringNotice = false },
                onAcknowledged = { showMonitoringNotice = false }
            )
        }
    }
    if (showDeleteAccount) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            com.kosd.log_inattendancesafeguard.ui.legal.DeleteAccountScreen(
                authViewModel = authViewModel,
                onDismiss = { showDeleteAccount = false }
            )
        }
    }

    if (orgViewModel.showSuccess) {
        LaunchedEffect(Unit) {
            orgViewModel.loadOrganizations()
            orgViewModel.dismissSuccess()
        }
    }
    if (orgViewModel.showError) {
        AlertDialog(
            onDismissRequest = { orgViewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(orgViewModel.errorMessage ?: "") },
            confirmButton = { TextButton(onClick = { orgViewModel.dismissError() }) { Text("OK") } }
        )
    }
    if (authViewModel.showSuccess) {
        AlertDialog(
            onDismissRequest = { authViewModel.dismissSuccess() },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = statusContentColor(AttendanceStatus.PRESENT)) },
            title = { Text("Success") },
            text  = { Text(authViewModel.successMessage ?: "") },
            confirmButton = { TextButton(onClick = { authViewModel.dismissSuccess() }) { Text("OK") } }
        )
    }
    if (authViewModel.showError) {
        AlertDialog(
            onDismissRequest = { authViewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(authViewModel.errorMessage ?: "") },
            confirmButton = { TextButton(onClick = { authViewModel.dismissError() }) { Text("OK") } }
        )
    }
    } // Box
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ProfileActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    TextButton(
        onClick = { haptics.tap(); onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CreateOrganizationDialog(
    viewModel: OrganizationViewModel,
    onDismiss: () -> Unit
) {
    var name        by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var timezone    by remember { mutableStateOf("UTC") }
    var maxMembers  by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Organization", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Organization Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text("Timezone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = maxMembers,
                    onValueChange = { maxMembers = it },
                    label = { Text("Max Members") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.createOrganization(
                        name = name,
                        description = description.ifBlank { null },
                        timezone = timezone.ifBlank { "UTC" },
                        maxMembers = maxMembers.toIntOrNull() ?: 100,
                        onCreated = { onDismiss() }
                    )
                },
                enabled = name.isNotBlank() && viewModel.isLoading == false
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinOrganizationDialog(viewModel: OrganizationViewModel, onDismiss: () -> Unit) {
    var inviteCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Organization", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the invite code shared by your organization admin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("Invite Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("e.g. ABC12345") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.joinByInviteCode(inviteCode); onDismiss() },
                enabled = inviteCode.isNotBlank() && viewModel.isLoading == false
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Join")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChangePasswordDialog(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPass  by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && newPass != confirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.changePassword(current, newPass, confirm)
                    if (viewModel.showError == false) onDismiss()
                },
                enabled = current.isNotBlank() && newPass.isNotBlank()
                        && newPass == confirm && viewModel.isLoading == false
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Change")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
