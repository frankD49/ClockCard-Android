package com.kosd.log_inattendancesafeguard.ui.event

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kosd.log_inattendancesafeguard.BuildConfig
import com.kosd.log_inattendancesafeguard.models.Event
import com.kosd.log_inattendancesafeguard.services.QRService
import com.kosd.log_inattendancesafeguard.ui.theme.AnimatedNumber
import com.kosd.log_inattendancesafeguard.ui.theme.AttendeeRowSkeleton
import com.kosd.log_inattendancesafeguard.ui.theme.EmptyState
import com.kosd.log_inattendancesafeguard.ui.theme.LiveCountSkeleton
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    navController: NavController,
    event: Event,
    eventViewModel: EventViewModel = viewModel(factory = EventViewModel.Factory()),
    isAdmin: Boolean = false,
    isEventStaff: Boolean = false
) {
    val liveCount by remember { derivedStateOf { eventViewModel.liveCount } }
    val attendees by remember { derivedStateOf { eventViewModel.attendees } }
    val qrToken by remember { derivedStateOf { eventViewModel.qrToken } }
    val haptics = com.kosd.log_inattendancesafeguard.ui.theme.rememberHaptics()

    val checkInUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/event-check-in?slug=${event.slug}&t=${qrToken ?: ""}"

    LaunchedEffect(event.id) {
        eventViewModel.startLiveCountPolling(event.id)
        eventViewModel.loadAttendees(event.id)
        // Event staff fetches the pre-generated kiosk token; admin generates their own
        if (!isAdmin && isEventStaff) {
            eventViewModel.fetchKioskToken(event.id)
        }
    }
    DisposableEffect(Unit) {
        onDispose { eventViewModel.stopLiveCountPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Count Card
            Card(modifier = Modifier.fillMaxWidth()) {
                if (liveCount == null) {
                    LiveCountSkeleton()
                } else {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Live Count", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedNumber(
                                targetValue = liveCount?.checkedIn ?: 0,
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(" / ${liveCount?.registered ?: 0}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = {
                                val reg = (liveCount?.registered ?: 1).coerceAtLeast(1)
                                (liveCount?.checkedIn ?: 0).toFloat() / reg.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("Walk-ins: ${liveCount?.walkIns ?: 0}", style = MaterialTheme.typography.bodySmall)
                            Text("Pre-reg: ${liveCount?.preRegistered ?: 0}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // QR Code Card (admin only — event staff use kiosk mode directly)
            if (isAdmin) {
                Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Check-In QR Code", style = MaterialTheme.typography.titleMedium)
                    val bitmap = QRService.generateQRBitmap(checkInUrl, 400)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp).background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.extraSmall
                            ),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text("Scan to check in", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        haptics.tap()
                        navController.navigate("kiosk/${event.id}")
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Kiosk Mode") }
                if (isAdmin) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            navController.navigate("event_report/${event.id}")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Report") }
                }
            }

            // Recent Attendees
            Text("Recent Attendees", style = MaterialTheme.typography.titleMedium)
            if (attendees.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.People,
                    title = "No attendees yet",
                    subtitle = "Check-ins will appear here in real time."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(attendees.take(10)) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (a.checkedIn) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (a.checkedIn) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(a.fullName, style = MaterialTheme.typography.bodyMedium)
                                a.checkInMethod?.let {
                                    Text(it.replaceFirstChar { c -> c.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
