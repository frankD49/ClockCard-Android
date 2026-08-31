package com.kosd.log_inattendancesafeguard.ui.event

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kosd.log_inattendancesafeguard.ui.maps.OsmGeofenceMap
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCreateScreen(
    navController: NavController,
    orgViewModel: OrganizationViewModel,
    eventViewModel: EventViewModel = viewModel(factory = EventViewModel.Factory())
) {
    var name by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(LocalDate.now()) }
    var checkInOpenAt by remember { mutableStateOf(Instant.now().toString()) }
    var checkInCloseAt by remember { mutableStateOf(Instant.now().plusSeconds(7200).toString()) }
    var venueName by remember { mutableStateOf("") }
    var venueLat by remember { mutableStateOf(0.0) }
    var venueLng by remember { mutableStateOf(0.0) }
    var venueRadius by remember { mutableStateOf("100") }
    var requireLocation by remember { mutableStateOf(false) }
    var expectedCount by remember { mutableStateOf("100") }
    var retentionDays by remember { mutableStateOf("90") }

    val isLoading by remember { derivedStateOf { eventViewModel.isLoading } }
    val createResult by remember { derivedStateOf { eventViewModel.createResult } }
    val orgId = orgViewModel.activeOrg?.id ?: ""

    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted — user can use "my location" button
        }
    }

    // Reverse-geocode: tap map → fill venue name
    fun reverseGeocode(lat: Double, lng: Double) {
        venueLat = lat
        venueLng = lng
        try {
            val geocoder = android.location.Geocoder(context)
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val parts = mutableListOf<String>()
                if (!addr.featureName.isNullOrEmpty()) parts.add(addr.featureName)
                if (!addr.thoroughfare.isNullOrEmpty()) parts.add(addr.thoroughfare)
                if (!addr.locality.isNullOrEmpty()) parts.add(addr.locality)
                if (parts.isNotEmpty()) {
                    venueName = parts.joinToString(", ")
                }
            }
        } catch (_: Exception) {
            // Geocoder may fail on emulator or without network — keep venueName as-is
        }
    }

    // Forward-geocode: type venue name → search and move map pin
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    fun forwardGeocode(query: String) {
        if (query.isBlank()) return
        isSearching = true
        searchError = null
        Thread {
            try {
                val geocoder = android.location.Geocoder(context)
                val addresses = geocoder.getFromLocationName(query, 1)
                handler.post {
                    isSearching = false
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        venueLat = addr.latitude
                        venueLng = addr.longitude
                    } else {
                        searchError = "Location not found"
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    isSearching = false
                    searchError = "Search failed: ${e.message}"
                }
            }
        }.start()
    }

    LaunchedEffect(createResult) {
        if (createResult?.success == true) {
            navController.popBackStack()
        }
    }

    // Update eventDate when date picker confirms
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            eventDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Event Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Date Picker ───────────────────────────────────────────────────
            OutlinedTextField(
                value = eventDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                onValueChange = { },
                label = { Text("Event Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                    }
                },
                enabled = false
            )

            // ── Venue Location Picker ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Venue Location", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("My Location")
                        }
                    }

                    // Venue name (auto-filled from reverse geocode, editable, searchable)
                    OutlinedTextField(
                        value = venueName, onValueChange = { venueName = it },
                        label = { Text("Venue Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { forwardGeocode(venueName) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search location")
                                }
                            }
                        }
                    )
                    if (searchError != null) {
                        Text(searchError!!, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.error)
                    }

                    // Map picker (clipped to prevent overlap with fields above)
                    OsmGeofenceMap(
                        centerLat = if (venueLat != 0.0) venueLat else null,
                        centerLon = if (venueLng != 0.0) venueLng else null,
                        radiusMeters = venueRadius.toDoubleOrNull() ?: 100.0,
                        interactive = true,
                        onMapTap = { lat, lng -> reverseGeocode(lat, lng) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.small)
                    )

                    // Coordinates display (read-only)
                    if (venueLat != 0.0 || venueLng != 0.0) {
                        Text(
                            text = "Lat: %.5f, Lng: %.5f".format(venueLat, venueLng),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Tap the map to set the venue location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Geofence radius slider
                    Text("Geofence Radius: ${(venueRadius.toDoubleOrNull() ?: 100.0).toInt()} m",
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = (venueRadius.toFloatOrNull() ?: 100f),
                        onValueChange = { venueRadius = it.toInt().toString() },
                        valueRange = 50f..2000f,
                        steps = 38
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Require Location", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = requireLocation, onCheckedChange = { requireLocation = it })
            }

            OutlinedTextField(
                value = expectedCount, onValueChange = { expectedCount = it },
                label = { Text("Expected Count") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            OutlinedTextField(
                value = retentionDays, onValueChange = { retentionDays = it },
                label = { Text("Retention (days)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Button(
                onClick = {
                    if (orgId.isNotEmpty()) {
                        eventViewModel.createEvent(
                            organizationId = orgId,
                            name = name,
                            eventDate = eventDate.toString(),
                            checkInOpenAt = checkInOpenAt,
                            checkInCloseAt = checkInCloseAt,
                            venueName = venueName,
                            venueLatitude = venueLat,
                            venueLongitude = venueLng,
                            venueRadiusM = venueRadius.toDoubleOrNull() ?: 100.0,
                            requireLocation = requireLocation,
                            expectedCount = expectedCount.toIntOrNull() ?: 100,
                            retentionDays = retentionDays.toIntOrNull() ?: 90
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotEmpty() && !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Create Event")
            }

            eventViewModel.errorMessage?.let { msg ->
                if (eventViewModel.showError) {
                    Text(msg, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
