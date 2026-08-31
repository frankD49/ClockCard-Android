package com.kosd.log_inattendancesafeguard.ui.admin

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.models.InviteCode
import com.kosd.log_inattendancesafeguard.models.Member
import com.kosd.log_inattendancesafeguard.models.Organization
import com.kosd.log_inattendancesafeguard.models.Permission
import com.kosd.log_inattendancesafeguard.models.UserRole
import com.kosd.log_inattendancesafeguard.ui.theme.statusContentColor
import com.kosd.log_inattendancesafeguard.ui.theme.statusContainerColor
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel

@Composable
fun MembersScreen(orgViewModel: OrganizationViewModel) {
    val org: Organization? = orgViewModel.activeOrg ?: orgViewModel.organizations.firstOrNull()
    val orgId: String? = org?.id
    var showAddMemberDialog  by remember { mutableStateOf(false) }
    var showInviteCodeDialog by remember { mutableStateOf(false) }
    var memberToRemove       by remember { mutableStateOf<Member?>(null) }
    var adminToEdit          by remember { mutableStateOf<Member?>(null) }
    var searchQuery          by remember { mutableStateOf("") }

    LaunchedEffect(orgId) {
        orgId?.let { id ->
            orgViewModel.loadMembers(id)
            orgViewModel.loadInviteCodes(id)
        }
    }

    if (org == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No organization found. Create one in Profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top action bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search members…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            )
            if (orgViewModel.can(Permission.MANAGE_MEMBERS)) IconButton(onClick = { showAddMemberDialog = true }) {
                Icon(Icons.Default.PersonAdd, "Add member", tint = MaterialTheme.colorScheme.primary)
            }
            if (orgViewModel.can(Permission.CREATE_INVITES)) IconButton(onClick = { showInviteCodeDialog = true }) {
                Icon(Icons.Default.Share, "Create member invitation", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // ── Member count info ────────────────────────────────────────────────
        val memberList: List<Member> = orgViewModel.members
        val memberCount: Int = memberList.count()
        Text(
            "$memberCount/${org.maxMembers} members",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        if (orgViewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filteredMembers: List<Member> = memberList.filter { member: Member ->
                if (searchQuery.isBlank()) true
                else {
                    val name = member.profile?.fullName ?: ""
                    val email = member.profile?.email ?: ""
                    name.contains(searchQuery, ignoreCase = true) ||
                    email.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredMembers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                        Spacer(Modifier.height(8.dp))
                        Text("No members found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMembers, key = { it.id }) { member ->
                        MemberCard(
                            member = member,
                            isOwner = orgViewModel.isOwnerInActiveOrg,
                            canRemoveOrdinaryMember = orgViewModel.can(Permission.MANAGE_MEMBERS),
                            onManageAdmin = { adminToEdit = member },
                            onDemoteAdmin = { orgViewModel.demoteAdmin(org.id, member.id) },
                            onRemove = { memberToRemove = member }
                        )
                    }
                }
            }
        }
    }

    adminToEdit?.let { member ->
        AdministratorDialog(
            member = member,
            initialPermissions = orgViewModel.memberPermissions[member.id]
                ?: setOf(Permission.MANAGE_MEMBERS, Permission.CREATE_INVITES),
            onDismiss = { adminToEdit = null },
            onSave = { permissions ->
                if (member.role == UserRole.ADMIN) orgViewModel.changeAdminPermissions(org.id, member.id, permissions)
                else orgViewModel.promoteAdmin(org.id, member.id, permissions)
                adminToEdit = null
            }
        )
    }

    // ── Add Member Dialog ────────────────────────────────────────────────────
    if (showAddMemberDialog) {
        AddMemberDialog(
            viewModel = orgViewModel,
            orgId = org.id,
            onDismiss = { showAddMemberDialog = false }
        )
    }

    // ── Invite Code Dialog ───────────────────────────────────────────────────
    if (showInviteCodeDialog) {
        InviteCodeDialog(
            viewModel = orgViewModel,
            orgId = org.id,
            onDismiss = { showInviteCodeDialog = false }
        )
    }

    // ── Remove Member Confirm ────────────────────────────────────────────────
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon  = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Member") },
            text  = {
                Text("Remove ${member.profile?.fullName ?: "this member"} from ${org.name}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        orgViewModel.removeMember(org.id, member.id)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            }
        )
    }

    if (orgViewModel.showError) {
        AlertDialog(
            onDismissRequest = { orgViewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(orgViewModel.errorMessage ?: "") },
            confirmButton = { TextButton(onClick = { orgViewModel.dismissError() }) { Text("OK") } }
        )
    }

    if (orgViewModel.showSuccess) {
        LaunchedEffect(Unit) {
            orgViewModel.dismissSuccess()
        }
    }
}

@Composable
fun MemberCard(
    member: Member,
    isOwner: Boolean,
    canRemoveOrdinaryMember: Boolean,
    onManageAdmin: () -> Unit,
    onDemoteAdmin: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                Text(
                    text = member.profile?.initials ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.profile?.fullName ?: "Unknown",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    member.profile?.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    member.role.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (member.isActive) statusContainerColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (member.isActive) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (member.isActive) statusContentColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (member.role != UserRole.OWNER && (isOwner || (canRemoveOrdinaryMember && member.role == UserRole.MEMBER))) Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, "Member actions", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isOwner && member.role == UserRole.MEMBER) DropdownMenuItem(
                            text = { Text("Make administrator") },
                            onClick = { menuExpanded = false; onManageAdmin() }
                        )
                        if (isOwner && member.role == UserRole.ADMIN) {
                            DropdownMenuItem(text = { Text("Edit administrator permissions") }, onClick = { menuExpanded = false; onManageAdmin() })
                            DropdownMenuItem(text = { Text("Remove administrator access") }, onClick = { menuExpanded = false; onDemoteAdmin() })
                        }
                        if (member.role == UserRole.MEMBER) DropdownMenuItem(
                            text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onRemove() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdministratorDialog(
    member: Member,
    initialPermissions: Set<Permission>,
    onDismiss: () -> Unit,
    onSave: (Set<Permission>) -> Unit
) {
    var administratorAccess by remember(member.id) { mutableStateOf(true) }
    var selected by remember(member.id) { mutableStateOf(initialPermissions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member.role == UserRole.ADMIN) "Edit administrator permissions" else "Make administrator") },
        text = {
            Column {
                PermissionCheckbox("Administrator access", administratorAccess) { administratorAccess = it }
                HorizontalDivider()
                Permission.entries.forEach { permission ->
                    PermissionCheckbox(permission.displayName, permission in selected, administratorAccess) { checked ->
                        selected = if (checked) selected + permission else selected - permission
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }, enabled = administratorAccess) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PermissionCheckbox(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AddMemberDialog(viewModel: OrganizationViewModel, orgId: String, onDismiss: () -> Unit) {
    var email      by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var position   by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    label = { Text("Employee ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Position / Role") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.addMember(orgId, email, employeeId.ifBlank { null },
                        department.ifBlank { null }, position.ifBlank { null })
                    onDismiss()
                },
                enabled = email.isNotBlank() && viewModel.isLoading == false
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InviteCodeDialog(viewModel: OrganizationViewModel, orgId: String, onDismiss: () -> Unit) {
    val context        = LocalContext.current
    var showCreateForm by remember { mutableStateOf(false) }
    var maxUses        by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Codes", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val inviteCodes: List<InviteCode> = viewModel.inviteCodes
                if (inviteCodes.isEmpty()) {
                    Text("No invite codes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    inviteCodes.take(5).forEach { code: InviteCode ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (code.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        code.code,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (code.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)
                                    )
                                    Text(
                                        "Used: ${code.useCount}${if (code.maxUses != null) "/${code.maxUses}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Join our organization")
                                            putExtra(Intent.EXTRA_TEXT,
                                                "You're invited! Use code: ${code.code} to join our organization on ClockCard.")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Invite Code"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                if (showCreateForm) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = maxUses,
                        onValueChange = { maxUses = it },
                        label = { Text("Max Uses (leave blank for unlimited)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (!showCreateForm) {
                Button(onClick = { showCreateForm = true }) { Text("Generate Code") }
            } else {
                Button(
                    onClick = {
                        viewModel.createInviteCode(orgId, maxUses.toIntOrNull(), null)
                        onDismiss()
                    }
                ) { Text("Create") }
            }
        },
        dismissButton = {
            TextButton(onClick = if (showCreateForm) ({ showCreateForm = false }) else onDismiss) {
                Text(if (showCreateForm) "Back" else "Close")
            }
        }
    )
}
