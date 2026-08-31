package com.kosd.log_inattendancesafeguard.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.models.AttendanceRecord
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.ui.theme.*
import com.kosd.log_inattendancesafeguard.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: AttendanceViewModel) {
    val filters = listOf("All", "This Week", "This Month", "Present", "Late", "Absent")
    var selectedFilter by remember { mutableStateOf("This Month") }

    LaunchedEffect(Unit) {
        viewModel.loadThisMonthHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        SectionHeader(
            title = "Your activity",
            subtitle = "Review attendance patterns and daily records"
        )
        Spacer(Modifier.height(12.dp))
        // ── Filter chips ─────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilter = filter
                        when (filter) {
                            "This Week"  -> viewModel.loadThisWeekHistory()
                            "This Month" -> viewModel.loadThisMonthHistory()
                            "Present"    -> viewModel.loadHistory(status = "present")
                            "Late"       -> viewModel.loadHistory(status = "late")
                            "Absent"     -> viewModel.loadHistory(status = "absent")
                            else         -> viewModel.loadThisMonthHistory()
                        }
                    },
                    label = { Text(filter) },
                    shape = MaterialTheme.shapes.large
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val historyRecords: List<AttendanceRecord> = viewModel.historyRecords

        if (viewModel.isHistoryLoading) {
            // Skeleton loading
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { StatCardSkeleton(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(16.dp))
                repeat(6) { HistoryRecordSkeleton() }
            }
        } else if (historyRecords.isEmpty()) {
            EmptyState(
                icon = Icons.Default.EventBusy,
                title = "No attendance records found",
                subtitle = "Your attendance history will appear here once you start checking in."
            )
        } else {
            // ── Summary row ──────────────────────────────────────────────────
            val total   = historyRecords.count()
            val present = historyRecords.count { r: AttendanceRecord -> r.status == AttendanceStatus.PRESENT || r.status == AttendanceStatus.LATE }
            val absent  = historyRecords.count { r: AttendanceRecord -> r.status == AttendanceStatus.ABSENT }
            val late    = historyRecords.count { r: AttendanceRecord -> r.status == AttendanceStatus.LATE }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStat("Total", "$total",    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                MiniStat("Present", "$present", statusContentColor(AttendanceStatus.PRESENT), statusContainerColor(AttendanceStatus.PRESENT), Modifier.weight(1f))
                MiniStat("Late", "$late",       statusContentColor(AttendanceStatus.LATE), statusContainerColor(AttendanceStatus.LATE), Modifier.weight(1f))
                MiniStat("Absent", "$absent",   statusContentColor(AttendanceStatus.ABSENT), statusContainerColor(AttendanceStatus.ABSENT), Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sorted: List<AttendanceRecord> = historyRecords.sortedByDescending { r: AttendanceRecord -> r.date }
                itemsIndexed(sorted) { index, record: AttendanceRecord ->
                    Box(Modifier.animateStaggeredItem(index)) {
                        AttendanceRecordCard(record)
                    }
                }
            }
        }
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(viewModel.errorMessage ?: "An error occurred") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun MiniStat(label: String, value: String, contentColor: Color, containerColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = contentColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AttendanceRecordCard(record: AttendanceRecord) {
    val (statusLabel, statusIcon) = when (record.status) {
        AttendanceStatus.PRESENT         -> "Present"        to Icons.Default.CheckCircle
        AttendanceStatus.LATE            -> "Late"           to Icons.Default.WatchLater
        AttendanceStatus.ABSENT          -> "Absent"         to Icons.Default.Cancel
        AttendanceStatus.EARLY_DEPARTURE -> "Early Dep."     to Icons.Default.ExitToApp
        AttendanceStatus.REMOTE          -> "Remote"         to Icons.Default.Home
        AttendanceStatus.ON_LEAVE        -> "On Leave"       to Icons.Default.BeachAccess
        AttendanceStatus.HALF_DAY        -> "Half Day"       to Icons.Default.Schedule
    }
    val statusContainer = statusContainerColor(record.status)
    val statusContent = statusContentColor(record.status)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(statusIcon, contentDescription = null, tint = statusContent, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDisplayDate(record.date),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.checkInTime != null) {
                        Text(
                            "In: ${formatTime(record.checkInTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusContentColor(AttendanceStatus.PRESENT)
                        )
                    }
                    if (record.checkOutTime != null) {
                        Text(
                            "Out: ${formatTime(record.checkOutTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusContentColor(AttendanceStatus.LATE)
                        )
                    }
                }
                if (record.isLate && record.lateMinutes != null) {
                    Text("${record.lateMinutes} min late", style = MaterialTheme.typography.labelSmall, color = statusContentColor(AttendanceStatus.LATE))
                }
                if (record.checkInNotes != null) {
                    Text(record.checkInNotes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            StatusBadge(record.status)
        }
    }
}

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val input  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val output = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val d = input.parse(dateStr) ?: return dateStr
        output.format(d)
    } catch (_: Exception) { dateStr }
}

private fun formatTime(isoStr: String): String {
    return try {
        val input  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val output = SimpleDateFormat("HH:mm", Locale.getDefault())
        val d = input.parse(isoStr) ?: return isoStr
        output.format(d)
    } catch (_: Exception) { isoStr }
}
