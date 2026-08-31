package com.kosd.log_inattendancesafeguard.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.kosd.log_inattendancesafeguard.models.*
import com.kosd.log_inattendancesafeguard.ui.history.AttendanceRecordCard
import com.kosd.log_inattendancesafeguard.ui.theme.*
import com.kosd.log_inattendancesafeguard.viewmodel.AdminViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.BillingViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    orgViewModel: OrganizationViewModel,
    billingViewModel: BillingViewModel
) {
    val context = LocalContext.current
    val org: Organization? = orgViewModel.activeOrg ?: orgViewModel.organizations.firstOrNull()
    val orgId: String? = org?.id

    // Matrix view is more useful for multi-day filters (weekly/monthly/last month).
    var useMatrix by remember(adminViewModel.selectedFilter) {
        mutableStateOf(adminViewModel.selectedFilter != "Today")
    }

    LaunchedEffect(orgId) {
        orgId?.let { id ->
            adminViewModel.applyFilter(id, adminViewModel.selectedFilter)
        }
    }

    // Live updates: subscribe to attendance_records changes for this org.
    DisposableEffect(orgId) {
        orgId?.let { id ->
            adminViewModel.subscribeRealtime(id) {
                adminViewModel.applyFilter(id, adminViewModel.selectedFilter)
            }
        }
        onDispose { adminViewModel.unsubscribeRealtime() }
    }

    if (org == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Business, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                Spacer(Modifier.height(12.dp))
                Text("No organization selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Go to Profile to create one", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Org header
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(org.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${org.memberCount ?: 0}/${org.maxMembers} members",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(org.timezone, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.AdminPanelSettings, null, tint = Color.White,
                        modifier = Modifier.size(40.dp))
                }
            }
        }

        item {
            // Filter row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(adminViewModel.filters) { filter ->
                    FilterChip(
                        selected = adminViewModel.selectedFilter == filter,
                        onClick  = { orgId?.let { adminViewModel.applyFilter(it, filter) } },
                        label    = { Text(filter) },
                        shape    = MaterialTheme.shapes.large
                    )
                }
            }
        }

        // Summary stats
        val summary: AttendanceSummary? = adminViewModel.summary
        if (summary != null) {
            item {
                Text("Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatCard("Total", "${summary.total}", Icons.Default.People,
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                    AdminStatCard("Present", "${summary.present}", Icons.Default.CheckCircle,
                        statusContentColor(AttendanceStatus.PRESENT), statusContainerColor(AttendanceStatus.PRESENT), Modifier.weight(1f))
                    AdminStatCard("Absent", "${summary.absent}", Icons.Default.Cancel,
                        statusContentColor(AttendanceStatus.ABSENT), statusContainerColor(AttendanceStatus.ABSENT), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatCard("Late", "${summary.late}", Icons.Default.WatchLater,
                        statusContentColor(AttendanceStatus.LATE), statusContainerColor(AttendanceStatus.LATE), Modifier.weight(1f))
                    AdminStatCard("Remote", "${summary.remote}", Icons.Default.Home,
                        statusContentColor(AttendanceStatus.REMOTE), statusContainerColor(AttendanceStatus.REMOTE), Modifier.weight(1f))
                    AdminStatCard("Rate", "${"%.0f".format(summary.attendanceRate)}%",
                        Icons.Default.TrendingUp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                }
            }
        }

        if (adminViewModel.isLoading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        val records: List<AttendanceRecord> = adminViewModel.orgAttendance
            .sortedByDescending { it.checkInTime ?: it.date }
        val profiles = adminViewModel.userProfiles

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Attendance Records", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusContainerColor(AttendanceStatus.PRESENT)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(statusContentColor(AttendanceStatus.PRESENT))
                        ) {}
                        Text("Live", style = MaterialTheme.typography.labelSmall, color = statusContentColor(AttendanceStatus.PRESENT),
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                val canExport = billingViewModel.canExport
                FilledTonalButton(
                    onClick = {
                        if (!canExport) return@FilledTonalButton
                        val ctx = context
                        val intent = com.kosd.log_inattendancesafeguard.services.AttendanceExporter.shareIntent(
                            context = ctx,
                            records = records,
                            profiles = profiles,
                            orgName = org.name,
                            filterLabel = adminViewModel.selectedFilter
                        )
                        ctx.startActivity(intent)
                    },
                    enabled = records.isNotEmpty() && canExport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (canExport) Icons.Default.IosShare else Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (canExport) "Export CSV" else "Export (Locked)",
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "View:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !useMatrix,
                    onClick = { useMatrix = false },
                    label = { Text("List", style = MaterialTheme.typography.bodySmall) },
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = useMatrix,
                    onClick = { useMatrix = true },
                    label = { Text("Matrix", style = MaterialTheme.typography.bodySmall) },
                    shape = MaterialTheme.shapes.medium
                )
            }
        }

        if (records.isNotEmpty()) {
            item {
                if (useMatrix) AttendanceMatrixTable(records, profiles)
                else AttendanceTable(records, profiles)
            }
        } else if (!adminViewModel.isLoading) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Inbox, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No records for ${adminViewModel.selectedFilter.lowercase()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (adminViewModel.showError) {
        AlertDialog(
            onDismissRequest = { adminViewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(adminViewModel.errorMessage ?: "") },
            confirmButton = { TextButton(onClick = { adminViewModel.dismissError() }) { Text("OK") } }
        )
    }
}

@Composable
fun AdminStatCard(
    label: String, value: String,
    icon: ImageVector,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = contentColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Attendance Table ────────────────────────────────────────────────────────

private val DATE_IN  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val DATE_OUT = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val TIME_IN  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
private val TIME_OUT = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun fmtDate(date: String): String =
    runCatching { DATE_OUT.format(DATE_IN.parse(date)!!) }.getOrDefault(date)

/**
 * Returns the displayed attendance status. If a record has a check_in_time it cannot
 * be "Absent" regardless of the DB column — derive from facts to prevent inversion.
 */
internal fun derivedStatus(record: AttendanceRecord): AttendanceStatus {
    if (record.checkInTime.isNullOrBlank()) return AttendanceStatus.ABSENT
    // checked-in: prefer existing meaningful status, otherwise PRESENT.
    return when (record.status) {
        AttendanceStatus.ABSENT -> if (record.isRemote) AttendanceStatus.REMOTE else AttendanceStatus.PRESENT
        else -> record.status
    }
}

private fun fmtTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        // "2026-05-26T17:40:00" or "2026-05-26T17:40:00.123" or with timezone
        val trimmed = iso.substringBefore('.').substringBefore('+').take(19)
        TIME_OUT.format(TIME_IN.parse(trimmed)!!)
    }.getOrDefault(iso.takeLast(8).take(5))
}

@Composable
fun AttendanceTable(records: List<AttendanceRecord>, profiles: Map<String, User>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Header
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Email",     200.dp)
                TableHeaderCell("Date",      120.dp)
                TableHeaderCell("Check-In",   80.dp)
                TableHeaderCell("Check-Out",  80.dp)
                TableHeaderCell("Status",    100.dp)
            }
            HorizontalDivider()

            // Rows
            records.forEachIndexed { index, record ->
                val profile = profiles[record.userId]
                val email   = profile?.email ?: record.userId.take(8) + "…"
                val displayed = derivedStatus(record)
                val statusLabel = when (displayed) {
                    AttendanceStatus.PRESENT         -> "Present"
                    AttendanceStatus.LATE            -> "Late"
                    AttendanceStatus.ABSENT          -> "Absent"
                    AttendanceStatus.EARLY_DEPARTURE -> "Early"
                    AttendanceStatus.REMOTE          -> "Remote"
                    AttendanceStatus.ON_LEAVE        -> "On Leave"
                    AttendanceStatus.HALF_DAY        -> "Half Day"
                }
                val statusContainer = statusContainerColor(displayed)
                val statusContent = statusContentColor(displayed)
                Row(
                    modifier = Modifier
                        .background(
                            if (index % 2 == 0) Color.Transparent
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(email, 200.dp)
                    TableCell(fmtDate(record.date), 120.dp)
                    TableCell(fmtTime(record.checkInTime),  80.dp,
                        color = if (record.checkInTime != null) statusContentColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.onSurfaceVariant)
                    TableCell(fmtTime(record.checkOutTime), 80.dp,
                        color = if (record.checkOutTime != null) statusContentColor(AttendanceStatus.LATE) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.width(100.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusContainer
                        ) {
                            Text(
                                text = statusLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusContent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (index < records.size - 1) HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun TableHeaderCell(label: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = label,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(end = 8.dp),
        fontSize = 13.sp,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// ── Matrix view: members × dates with [in:out] cells ─────────────────────────

private val MATRIX_DATE_HEADER = SimpleDateFormat("d MMM", Locale.getDefault())

@Composable
fun AttendanceMatrixTable(records: List<AttendanceRecord>, profiles: Map<String, User>) {
    // Collect unique sorted dates and unique users present in records.
    val dates: List<String> = records.map { it.date }.distinct().sorted()
    val userIds: List<String> = records.map { it.userId }.distinct().sortedBy {
        profiles[it]?.email ?: it
    }
    // Index records by user+date for quick lookup
    val byKey: Map<Pair<String, String>, AttendanceRecord> =
        records.associateBy { it.userId to it.date }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Header row: empty corner + date columns
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Member", 200.dp)
                dates.forEach { d ->
                    val label = runCatching { MATRIX_DATE_HEADER.format(DATE_IN.parse(d)!!) }
                        .getOrDefault(d)
                    TableHeaderCell(label, 110.dp)
                }
            }
            HorizontalDivider()

            userIds.forEachIndexed { rowIdx, uid ->
                val email = profiles[uid]?.email ?: uid.take(8) + "…"
                Row(
                    modifier = Modifier
                        .background(
                            if (rowIdx % 2 == 0) Color.Transparent
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(email, 200.dp)
                    dates.forEach { d ->
                        val rec = byKey[uid to d]
                        val cellText = when {
                            rec == null -> "—"
                            rec.checkInTime == null -> "Absent"
                            else -> "${fmtTime(rec.checkInTime)} : ${fmtTime(rec.checkOutTime)}"
                        }
                        val cellColor = when {
                            rec == null || rec.checkInTime == null ->
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                            else -> statusContentColor(AttendanceStatus.PRESENT)
                        }
                        TableCell(cellText, 110.dp, color = cellColor)
                    }
                }
                if (rowIdx < userIds.size - 1) HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}
