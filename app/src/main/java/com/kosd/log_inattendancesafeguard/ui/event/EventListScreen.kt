package com.kosd.log_inattendancesafeguard.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kosd.log_inattendancesafeguard.R
import com.kosd.log_inattendancesafeguard.models.Event
import com.kosd.log_inattendancesafeguard.ui.theme.*
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    navController: NavController,
    orgViewModel: OrganizationViewModel,
    eventViewModel: EventViewModel = viewModel(factory = EventViewModel.Factory())
) {
    val events by remember { derivedStateOf { eventViewModel.events } }
    val isLoading by remember { derivedStateOf { eventViewModel.isLoading } }
    val isAdmin = orgViewModel.can(com.kosd.log_inattendancesafeguard.models.Permission.MANAGE_EVENTS)
    val orgId = orgViewModel.activeOrg?.id ?: ""
    val haptics = rememberHaptics()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(orgId) {
        if (orgId.isNotEmpty()) eventViewModel.loadEvents(orgId)
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = {
                    haptics.tap()
                    navController.navigate("event_create")
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Event")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .paint(
                    painter = painterResource(R.drawable.event_bg),
                    contentScale = ContentScale.Crop
                )
        ) {
        if (isLoading && events.isEmpty()) {
            // Skeleton loading
            Column(Modifier.fillMaxSize().padding(padding)) {
                EventListSkeleton()
            }
        } else if (events.isEmpty()) {
            // Empty state with CTA
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "No events yet",
                    subtitle = if (isAdmin)
                        "Create your first event to start tracking attendance."
                    else
                        "Events created by your admin will appear here.",
                    ctaText = if (isAdmin) "Create Event" else null,
                    onCtaClick = if (isAdmin) { { navController.navigate("event_create") } } else null
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch {
                        if (orgId.isNotEmpty()) eventViewModel.loadEvents(orgId)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionHeader(
                            title = "Events",
                            subtitle = if (isAdmin) "Create and manage attendance experiences" else "Your upcoming attendance experiences"
                        )
                    }
                    itemsIndexed(events) { index, event ->
                        Box(Modifier.animateStaggeredItem(index)) {
                            EventCard(event = event, onClick = {
                                haptics.tap()
                                eventViewModel.selectEvent(event)
                                navController.navigate("event_detail/${event.id}")
                            })
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EventCard(event: Event, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().pressScale(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(Elevation.low)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(
                    Modifier.size(width = 58.dp, height = 62.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("EVENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (event.isActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) { Text(if (event.isActive) "LIVE" else "ENDED", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                    color = if (event.isActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            event.eventDate?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            event.venueName?.takeIf { it.isNotEmpty() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
    }
}

@Composable
private fun EventInstructionsCard(isAdmin: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Event, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("How Events Work", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            val steps = if (isAdmin) listOf(
                "Tap + to create a new event",
                "Set the event name, date, and check-in window",
                "Search a venue by name or tap the map to pin its location",
                "Set a geofence radius — attendees must be within it to check in",
                "Share the event QR code or check-in link with attendees",
                "Monitor live check-ins and generate reports after the event"
            ) else listOf(
                "Events created by your admin will appear here",
                "Tap an event to view details and check in",
                "You must be within the venue geofence to check in",
                "Check the live count to see who has arrived"
            )

            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall)
                    Text(step, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
