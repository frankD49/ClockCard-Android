package com.kosd.log_inattendancesafeguard.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.models.AttendanceRecord
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.models.LocationStatus
import com.kosd.log_inattendancesafeguard.models.User
import com.kosd.log_inattendancesafeguard.services.LocationService
import com.kosd.log_inattendancesafeguard.ui.maps.OsmGeofenceMap
import com.kosd.log_inattendancesafeguard.ui.theme.*
import com.kosd.log_inattendancesafeguard.viewmodel.AttendanceViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    attendanceViewModel: AttendanceViewModel,
    orgViewModel: com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
) {
    val context = LocalContext.current
    val locationService = remember { LocationService(context) }
    val coroutineScope  = rememberCoroutineScope()
    val user: User? = authViewModel.currentUser
    val haptics = rememberHaptics()

    LaunchedEffect(orgViewModel.activeOrg?.id) {
        attendanceViewModel.currentOrgId = orgViewModel.activeOrg?.id
        attendanceViewModel.resetForOrgSwitch()
        if (orgViewModel.activeOrg != null) {
            attendanceViewModel.loadTodayStatus()
        }
    }

    var showCheckInDialog  by remember { mutableStateOf(false) }
    var showCheckOutDialog by remember { mutableStateOf(false) }
    var checkNotes         by remember { mutableStateOf("") }
    var isRemote           by remember { mutableStateOf(false) }
    var isFetchingLocation  by remember { mutableStateOf(false) }
    var userLat             by remember { mutableStateOf<Double?>(null) }
    var userLon             by remember { mutableStateOf<Double?>(null) }
    var ownerOptedIn        by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            coroutineScope.launch {
                isFetchingLocation = true
                val loc = try { locationService.getCurrentLocation() } catch (_: Exception) { null }
                isFetchingLocation = false
                userLat = loc?.latitude
                userLon = loc?.longitude
            }
        }
    }

    LaunchedEffect(Unit) {
        if (orgViewModel.activeOrg != null) {
            attendanceViewModel.loadTodayStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Organization Banner ─────────────────────────────────────────────
        orgViewModel.activeOrg?.let { org ->
            OrgBannerCard(
                orgName = org.name
            )
        }

        // ── Welcome Header ──────────────────────────────────────────────────
        BrandHero(
            eyebrow = "Hello, ${user?.firstName ?: "User"}",
            title = "Ready for today?",
            subtitle = listOfNotNull(
                orgViewModel.myRoleInActiveOrg.ifEmpty { "Member" },
                orgViewModel.activeOrg?.timezone
            ).joinToString(" • "),
            trailing = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.initials ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )

        // ── Date & Time ──────────────────────────────────────────────────────
        val currentDate = SimpleDateFormat("EEEE, MMMM d yyyy", Locale.getDefault()).format(Date())
        SectionHeader("Today's attendance", currentDate)

        // ── Organization Required ────────────────────────────────────────────
        if (attendanceViewModel.needsOrganization) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Organization Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Join or create an organization in Profile to start tracking attendance.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Today's Check-in / Check-out Status ──────────────────────────────
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Live status", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttendanceTimeIndicator(
                        icon = Icons.Default.Login,
                        label = "Check In",
                        time = attendanceViewModel.checkInTimeText,
                        isActive = attendanceViewModel.isCheckedIn,
                        activeColor = statusContentColor(AttendanceStatus.PRESENT)
                    )
                    VerticalDivider(modifier = Modifier.height(70.dp))
                    AttendanceTimeIndicator(
                        icon = Icons.Default.Logout,
                        label = "Check Out",
                        time = attendanceViewModel.checkOutTimeText,
                        isActive = attendanceViewModel.isCheckedOut,
                        activeColor = statusContentColor(AttendanceStatus.LATE)
                    )
                }

                val todayRecord: AttendanceRecord? = attendanceViewModel.todayRecord
                if (todayRecord != null) {
                    Spacer(Modifier.height(12.dp))
                    val statusLabel = when (todayRecord.status) {
                        AttendanceStatus.PRESENT         -> "Present"
                        AttendanceStatus.LATE            -> "Late"
                        AttendanceStatus.ABSENT          -> "Absent"
                        AttendanceStatus.EARLY_DEPARTURE -> "Early Departure"
                        AttendanceStatus.REMOTE          -> "Remote"
                        AttendanceStatus.ON_LEAVE        -> "On Leave"
                        AttendanceStatus.HALF_DAY        -> "Half Day"
                    }
                    val statusContainer = statusContainerColor(todayRecord.status)
                    val statusContent = statusContentColor(todayRecord.status)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = statusContainer
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = statusContent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    if (todayRecord.isLate && todayRecord.lateMinutes != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${todayRecord.lateMinutes} min late",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusContentColor(AttendanceStatus.LATE)
                        )
                    }
                }
            }
        }

        // ── Quick Actions ────────────────────────────────────────────────────
        // Check-in/out is automatic for all members. Owners are exempt —
        // attendance tracking is optional for them. They can opt in with a
        // button; once opted in (or if they already have a record), the
        // standard check-in/out buttons appear.
        val isOwner = orgViewModel.isOwnerInActiveOrg
        val ownerHasRecord = attendanceViewModel.todayRecord != null
        val showOwnerOptIn = isOwner && !ownerHasRecord && !ownerOptedIn

        if (showOwnerOptIn) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "As the owner, attendance tracking is optional for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { haptics.tap(); ownerOptedIn = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Track my attendance")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    icon = Icons.Default.Login,
                    label = "Check In",
                    containerColor = if (attendanceViewModel.canCheckIn && attendanceViewModel.needsOrganization == false)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    enabled = attendanceViewModel.canCheckIn && attendanceViewModel.needsOrganization == false
                            && attendanceViewModel.isLoading == false,
                    modifier = Modifier.weight(1f),
                    onClick = { haptics.tap(); showCheckInDialog = true }
                )
                ActionButton(
                    icon = Icons.Default.Logout,
                    label = "Check Out",
                    containerColor = if (attendanceViewModel.canCheckOut && attendanceViewModel.needsOrganization == false)
                        statusContentColor(AttendanceStatus.LATE) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    enabled = attendanceViewModel.canCheckOut && attendanceViewModel.needsOrganization == false
                            && attendanceViewModel.isLoading == false,
                    modifier = Modifier.weight(1f),
                    onClick = { haptics.tap(); showCheckOutDialog = true }
                )
            }
        }

        // ── Loading Indicator ────────────────────────────────────────────────
        if (attendanceViewModel.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }

    // ── Geofence helpers ─────────────────────────────────────────────────────
    val rule = orgViewModel.activeRule
    val ruleLat = rule?.locationLatitude
    val ruleLon = rule?.locationLongitude
    val ruleRadius = rule?.locationRadiusM
    val requireLocation = rule?.requireLocation == true

    val distanceMeters: Float? = remember(userLat, userLon, ruleLat, ruleLon) {
        if (userLat != null && userLon != null && ruleLat != null && ruleLon != null) {
            val r = FloatArray(1)
            android.location.Location.distanceBetween(userLat!!, userLon!!, ruleLat, ruleLon, r)
            r[0]
        } else null
    }
    val insideRadius: Boolean = if (distanceMeters != null && ruleRadius != null) {
        distanceMeters <= ruleRadius.toFloat()
    } else true   // if no geofence configured, treat as inside

    val locationStatus: LocationStatus = when {
        !requireLocation                    -> LocationStatus.NOT_REQUIRED
        userLat == null || userLon == null  -> LocationStatus.UNKNOWN
        ruleLat == null || ruleLon == null  -> LocationStatus.NOT_REQUIRED
        insideRadius                        -> LocationStatus.VALID
        else                                -> LocationStatus.INVALID
    }

    // Auto-fetch location when either dialog opens
    LaunchedEffect(showCheckInDialog, showCheckOutDialog) {
        if ((showCheckInDialog || showCheckOutDialog) && userLat == null && userLon == null) {
            if (locationService.hasLocationPermission()) {
                isFetchingLocation = true
                val loc = try { locationService.getCurrentLocation() } catch (_: Exception) { null }
                isFetchingLocation = false
                userLat = loc?.latitude
                userLon = loc?.longitude
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    @Composable
    fun GeofenceMapBlock() {
        if (isFetchingLocation) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Getting your location…", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val mapCenterLat = ruleLat ?: userLat
        val mapCenterLon = ruleLon ?: userLon
        if (mapCenterLat != null && mapCenterLon != null) {
            OsmGeofenceMap(
                centerLat = ruleLat,
                centerLon = ruleLon,
                radiusMeters = ruleRadius,
                userLat = userLat,
                userLon = userLon,
                insideRadius = insideRadius,
                interactive = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
        // Status badge
        when (locationStatus) {
            LocationStatus.VALID -> StatusBanner(
                "Inside allowed radius${distanceMeters?.let { " · ${it.toInt()} m from center" } ?: ""}",
                statusContentColor(AttendanceStatus.PRESENT)
            )
            LocationStatus.INVALID -> StatusBanner(
                "Outside allowed radius${distanceMeters?.let { " · ${it.toInt()} m from center" } ?: ""}",
                statusContentColor(AttendanceStatus.ABSENT)
            )
            LocationStatus.NOT_REQUIRED -> if (rule != null) StatusBanner(
                "No geofence configured for this rule",
                MaterialTheme.colorScheme.onSurfaceVariant
            )
            LocationStatus.UNKNOWN -> if (requireLocation) StatusBanner(
                "Waiting for your location…",
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ── Check-In Dialog ──────────────────────────────────────────────────────
    if (showCheckInDialog) {
        AlertDialog(
            onDismissRequest = { showCheckInDialog = false; userLat = null; userLon = null },
            icon  = { Icon(Icons.Default.Login, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Check In", fontWeight = FontWeight.Bold) },
            text  = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Record your attendance for today.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GeofenceMapBlock()
                    OutlinedTextField(
                        value = checkNotes,
                        onValueChange = { checkNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isRemote, onCheckedChange = { isRemote = it })
                        Text("Working remotely")
                    }
                }
            },
            confirmButton = {
                val blocked = requireLocation && !isRemote &&
                    (locationStatus == LocationStatus.INVALID || locationStatus == LocationStatus.UNKNOWN)
                Button(
                    onClick = {
                        haptics.tap()
                        showCheckInDialog = false
                        attendanceViewModel.checkIn(
                            orgId = attendanceViewModel.currentOrgId ?: "",
                            latitude = userLat,
                            longitude = userLon,
                            notes = checkNotes,
                            isRemote = isRemote,
                            locationStatus = if (isRemote) LocationStatus.NOT_REQUIRED else locationStatus
                        )
                        userLat = null; userLon = null
                        checkNotes = ""; isRemote = false
                    },
                    enabled = !blocked && !isFetchingLocation
                ) { Text(if (blocked) "Outside radius" else "Check In") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCheckInDialog = false; userLat = null; userLon = null
                }) { Text("Cancel") }
            }
        )
    }

    // ── Check-Out Dialog ─────────────────────────────────────────────────────
    if (showCheckOutDialog) {
        AlertDialog(
            onDismissRequest = { showCheckOutDialog = false; userLat = null; userLon = null },
            icon  = { Icon(Icons.Default.Logout, null, tint = statusContentColor(AttendanceStatus.LATE)) },
            title = { Text("Check Out", fontWeight = FontWeight.Bold) },
            text  = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("End your attendance for today.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GeofenceMapBlock()
                    OutlinedTextField(
                        value = checkNotes,
                        onValueChange = { checkNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                val blocked = requireLocation &&
                    (locationStatus == LocationStatus.INVALID || locationStatus == LocationStatus.UNKNOWN)
                Button(
                    onClick = {
                        haptics.tap()
                        showCheckOutDialog = false
                        attendanceViewModel.checkOut(
                            latitude = userLat,
                            longitude = userLon,
                            notes = checkNotes,
                            locationStatus = locationStatus
                        )
                        userLat = null; userLon = null
                        checkNotes = ""
                    },
                    enabled = !blocked && !isFetchingLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = statusContentColor(AttendanceStatus.LATE))
                ) { Text(if (blocked) "Outside radius" else "Check Out") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCheckOutDialog = false; userLat = null; userLon = null
                }) { Text("Cancel") }
            }
        )
    }

    // ── Error/Success Dialogs ────────────────────────────────────────────────
    if (attendanceViewModel.showError) {
        LaunchedEffect(Unit) { haptics.error() }
        AlertDialog(
            onDismissRequest = { attendanceViewModel.dismissError() },
            title = { Text("Error") },
            text  = { Text(attendanceViewModel.errorMessage ?: "An error occurred") },
            confirmButton = {
                TextButton(onClick = { attendanceViewModel.dismissError() }) { Text("OK") }
            }
        )
    }

    if (attendanceViewModel.showSuccess) {
        LaunchedEffect(Unit) { haptics.success() }
        AlertDialog(
            onDismissRequest = { attendanceViewModel.dismissSuccess() },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = statusContentColor(AttendanceStatus.PRESENT)) },
            title = { Text("Success") },
            text  = { Text(attendanceViewModel.successMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { attendanceViewModel.dismissSuccess() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun AttendanceTimeIndicator(
    icon: ImageVector,
    label: String,
    time: String?,
    isActive: Boolean,
    activeColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (time != null) {
            Text(time, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        } else {
            Text("--:--", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(80.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusBanner(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.LocationOn, null, tint = color, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
