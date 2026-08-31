package com.kosd.log_inattendancesafeguard.ui.event

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kosd.log_inattendancesafeguard.models.Event
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventReportScreen(
    navController: NavController,
    event: Event,
    eventViewModel: EventViewModel = viewModel(factory = EventViewModel.Factory())
) {
    val report by remember { derivedStateOf { eventViewModel.report } }
    val isLoading by remember { derivedStateOf { eventViewModel.isLoading } }
    val context = LocalContext.current

    LaunchedEffect(event.id) {
        eventViewModel.getReport(event.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Report") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val r = report ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No report data")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    r.summary?.let { s ->
                        StatRow("Total Registered", "${s.totalRegistered}")
                        StatRow("Checked In", "${s.checkedIn}")
                        StatRow("No-Shows", "${s.noShows}")
                        StatRow("Walk-Ins", "${s.walkIns}")
                        StatRow("Pre-Registered", "${s.preRegistered}")
                        StatRow("Check-In Rate", "${s.checkInRate}%")
                    }
                }
            }

            // Method Breakdown
            r.methodBreakdown.takeIf { it.isNotEmpty() }?.let { methods ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Check-In Methods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        methods.forEach { m ->
                            val methodName = (m["method"] as? JsonPrimitive)?.content ?: "unknown"
                            val count = (m["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(methodName)
                                Text("$count", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Time Distribution
            r.timeDistribution.takeIf { it.isNotEmpty() }?.let { buckets ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Time Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val maxCount = buckets.maxOfOrNull {
                            (it["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        } ?: 1
                        buckets.forEach { b ->
                            val binName = (b["bin"] as? JsonPrimitive)?.content ?: ""
                            val count = (b["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(binName, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(50.dp))
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        .widthIn(max = ((count.toFloat() / maxCount.toFloat() * 200).toInt()).dp)
                                )
                                Text("$count", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }

            // Export CSV Button
            Button(
                onClick = { exportCSV(context, event, r) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun exportCSV(
    context: android.content.Context,
    event: Event,
    report: com.kosd.log_inattendancesafeguard.models.EventReport
) {
    val bom = "\uFEFF"
    val sb = StringBuilder()
    sb.append(bom)
    sb.append("ClockCard Event Report\n")
    sb.append("Event,").append(escapeCSV(event.name)).append("\n")
    sb.append("Date,").append(escapeCSV(event.eventDate ?: "")).append("\n")
    sb.append("Venue,").append(escapeCSV(event.venueName ?: "")).append("\n\n")

    sb.append("Summary\n")
    report.summary?.let { s ->
        sb.append("Total Registered,").append(s.totalRegistered).append("\n")
        sb.append("Checked In,").append(s.checkedIn).append("\n")
        sb.append("No-Shows,").append(s.noShows).append("\n")
        sb.append("Walk-Ins,").append(s.walkIns).append("\n")
        sb.append("Check-In Rate,").append(s.checkInRate).append("%\n\n")
    }

    sb.append("Attendee List\n")
    sb.append("Name,Email,Registration Type,Check-In Method,Check-In Time,Location Status\n")
    report.attendees.forEach { a ->
        sb.append(escapeCSV(a.fullName)).append(",")
        sb.append(escapeCSV(a.email ?: "")).append(",")
        sb.append(escapeCSV(a.registrationType)).append(",")
        sb.append(escapeCSV(a.checkInMethod ?: "")).append(",")
        sb.append(escapeCSV(a.checkedInAt ?: "")).append(",")
        sb.append(escapeCSV(a.checkInLocationStatus ?: "")).append("\n")
    }

    val file = File(context.cacheDir, "${event.name}_report.csv")
    file.writeText(sb.toString())

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export CSV"))
}

private fun escapeCSV(field: String): String {
    return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
        "\"${field.replace("\"", "\"\"")}\""
    } else field
}
