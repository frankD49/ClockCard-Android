package com.kosd.log_inattendancesafeguard.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosd.log_inattendancesafeguard.models.EventStaffMember
import com.kosd.log_inattendancesafeguard.models.UserRole
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventStaffScreen(
    orgViewModel: OrganizationViewModel
) {
    val orgId = orgViewModel.activeOrg?.id ?: ""
    val staff by remember { derivedStateOf { orgViewModel.eventStaff } }
    val members by remember { derivedStateOf { orgViewModel.members } }
    val isLoading by remember { derivedStateOf { orgViewModel.isLoading } }

    LaunchedEffect(orgId) {
        if (orgId.isNotEmpty()) {
            orgViewModel.loadEventStaff(orgId)
            orgViewModel.loadMembers(orgId)
        }
    }

    // Members who are eligible to be promoted (regular members only)
    val eligibleMembers by remember(members) {
        derivedStateOf {
            members.filter { it.role == UserRole.MEMBER }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Event Staff") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Event Staff Privileges", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Grant event staff privileges to members so they can access Events mode and run kiosk check-ins. They cannot create events, view reports, or extend privileges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Current staff
            Text("Current Event Staff (${staff.size})", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            if (staff.isEmpty() && !isLoading) {
                Text("No event staff assigned yet", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(staff) { member ->
                    StaffRow(
                        member = member,
                        onRevoke = { orgViewModel.revokeEventStaff(member.memberId, orgId) }
                    )
                }
            }

            HorizontalDivider()

            // Eligible members
            Text("Eligible Members (${eligibleMembers.size})", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            if (eligibleMembers.isEmpty() && !isLoading) {
                Text("No eligible members. Invite members to your organization first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eligibleMembers) { member ->
                    val name = member.profile?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Unknown"
                    val email = member.profile?.email ?: ""
                    MemberRow(
                        name = name,
                        email = email,
                        onGrant = { orgViewModel.grantEventStaff(member.id, orgId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffRow(member: EventStaffMember, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(member.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(member.email, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(member.role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onRevoke) {
                Icon(Icons.Default.PersonRemove, contentDescription = "Revoke",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MemberRow(name: String, email: String, onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(email, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onGrant) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Grant")
            }
        }
    }
}
