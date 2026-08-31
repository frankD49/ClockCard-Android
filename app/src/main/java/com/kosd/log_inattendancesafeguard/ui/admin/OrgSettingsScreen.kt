package com.kosd.log_inattendancesafeguard.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.models.AttendanceFrequency
import com.kosd.log_inattendancesafeguard.models.AttendanceRule
import com.kosd.log_inattendancesafeguard.models.AttendanceRuleCreateRequest
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.models.Organization
import com.kosd.log_inattendancesafeguard.models.OrganizationUpdateRequest
import com.kosd.log_inattendancesafeguard.models.PopulationTier
import com.kosd.log_inattendancesafeguard.ui.profile.SectionHeader
import com.kosd.log_inattendancesafeguard.ui.theme.statusContentColor
import com.kosd.log_inattendancesafeguard.ui.theme.statusContainerColor
import com.kosd.log_inattendancesafeguard.viewmodel.BillingViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
import kotlinx.coroutines.launch

@Composable
fun OrgSettingsScreen(
    orgViewModel: OrganizationViewModel,
    billingViewModel: BillingViewModel
) {
    val org: Organization? = orgViewModel.activeOrg ?: orgViewModel.organizations.firstOrNull()
    val orgId: String? = org?.id

    LaunchedEffect(orgId) {
        orgId?.let { orgViewModel.loadRules(it) }
    }

    if (org == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No organization. Create one in Profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val safeOrgId: String = orgId ?: ""
    var name        by remember(safeOrgId) { mutableStateOf(org.name) }
    var description by remember(safeOrgId) { mutableStateOf(org.description ?: "") }
    var timezone    by remember(safeOrgId) { mutableStateOf(org.timezone) }
    var maxMembers  by remember(safeOrgId) { mutableStateOf(org.maxMembers.toString()) }
    var populationTier by remember(safeOrgId) { mutableStateOf(org.populationTier) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var pendingDeleteRule by remember { mutableStateOf<AttendanceRule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Organization Settings", Icons.Default.Settings)

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Organization Name") },
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
                    label = { Text("Timezone (e.g. UTC, America/New_York)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = maxMembers,
                    onValueChange = { input ->
                        // Cap at 10,000,000 to match the highest tier ceiling.
                        val parsed = input.filter { it.isDigit() }.toLongOrNull()
                        maxMembers = when {
                            input.isEmpty() -> ""
                            parsed == null -> maxMembers
                            parsed > 10_000_000L -> "10000000"
                            else -> parsed.toString()
                        }
                    },
                    label = { Text("Max Members (up to 10,000,000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )

                Text(
                    "Population strength tier",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Free tier (< 10) is the default. Higher tiers unlock CSV export and printing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TierSelector(
                    selected = populationTier,
                    onSelect = { tier ->
                        if (tier.isFree || billingViewModel.isUnlocked(tier)) {
                            populationTier = tier
                        }
                    },
                    isUnlocked = { billingViewModel.isUnlocked(it) }
                )

                Button(
                    onClick = {
                        // Guard: don't save a paid tier the user hasn't purchased.
                        val tierToSave = if (populationTier.isFree || billingViewModel.isUnlocked(populationTier))
                            populationTier else PopulationTier.FREE
                        orgViewModel.updateOrganization(
                            safeOrgId,
                            OrganizationUpdateRequest(
                                name = name.ifBlank { null },
                                description = description.ifBlank { null },
                                timezone = timezone.ifBlank { null },
                                maxMembers = maxMembers.toIntOrNull(),
                                populationTier = tierToSave
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = orgViewModel.isLoading == false
                ) {
                    if (orgViewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Changes")
                    }
                }
            }
        }

        // ── Subscription / In-App Purchases ──────────────────────────────────
        SubscriptionSection(billingViewModel = billingViewModel)

        // ── Attendance Rules ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Attendance Rules", Icons.Default.Rule)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showRuleDialog = true }) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        val attendanceRules: List<AttendanceRule> = orgViewModel.attendanceRules
        if (attendanceRules.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No attendance rules defined", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showRuleDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Rule")
                    }
                }
            }
        } else {
            attendanceRules.forEach { rule: AttendanceRule ->
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
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, fontWeight = FontWeight.SemiBold)
                            Text("${rule.workStartTime} – ${rule.workEndTime} · ${rule.frequency.displayName}",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Check-in: ${rule.checkInStart}–${rule.checkInEnd}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Grace: ${rule.gracePeriodMins} min · ${if (rule.requireLocation) "Location required" else "No location"}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rule.isActive) statusContainerColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (rule.isActive) "Active" else "Off",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rule.isActive) statusContentColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDeleteRule = rule }) {
                                Icon(Icons.Default.Delete, "Delete rule", tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showRuleDialog) {
        CreateRuleDialog(
            orgId = safeOrgId,
            viewModel = orgViewModel,
            onDismiss = { showRuleDialog = false }
        )
    }

    pendingDeleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete rule?") },
            text = { Text("Are you sure you want to delete \"${rule.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    orgViewModel.deleteRule(rule.id, safeOrgId)
                    pendingDeleteRule = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRule = null }) { Text("Cancel") }
            }
        )
    }

    if (orgViewModel.showSuccess) {
        AlertDialog(
            onDismissRequest = { orgViewModel.dismissSuccess() },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = statusContentColor(AttendanceStatus.PRESENT)) },
            title = { Text("Success") },
            text  = { Text(orgViewModel.successMessage ?: "") },
            confirmButton = { TextButton(onClick = { orgViewModel.dismissSuccess() }) { Text("OK") } }
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
}

@Composable
fun CreateRuleDialog(orgId: String, viewModel: OrganizationViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationService = remember { com.kosd.log_inattendancesafeguard.services.LocationService(context) }
    val coroutineScope = rememberCoroutineScope()

    var name           by remember { mutableStateOf("Default Rule") }
    var workStart      by remember { mutableStateOf("09:00") }
    var workEnd        by remember { mutableStateOf("17:00") }
    var checkInStart   by remember { mutableStateOf("08:30") }
    var checkInEnd     by remember { mutableStateOf("09:15") }
    var checkOutStart  by remember { mutableStateOf("16:30") }
    var checkOutEnd    by remember { mutableStateOf("18:00") }
    var gracePeriod    by remember { mutableStateOf("15") }
    var frequency      by remember { mutableStateOf(AttendanceFrequency.DAILY) }
    var requireLocation by remember { mutableStateOf(false) }
    var lat            by remember { mutableStateOf<Double?>(null) }
    var lon            by remember { mutableStateOf<Double?>(null) }
    var radiusM        by remember { mutableStateOf(150f) }
    var fetchingLoc    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Attendance Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Rule Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workStart, onValueChange = { workStart = it },
                        label = { Text("Work Start") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = workEnd, onValueChange = { workEnd = it },
                        label = { Text("Work End") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                }
                Text("Check-In Window", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = checkInStart, onValueChange = { checkInStart = it },
                        label = { Text("From") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = checkInEnd, onValueChange = { checkInEnd = it },
                        label = { Text("To") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                }
                Text("Check-Out Window", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = checkOutStart, onValueChange = { checkOutStart = it },
                        label = { Text("From") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = checkOutEnd, onValueChange = { checkOutEnd = it },
                        label = { Text("To") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    )
                }
                OutlinedTextField(
                    value = gracePeriod, onValueChange = { gracePeriod = it },
                    label = { Text("Grace Period (minutes)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )

                EnumDropdown(
                    label = "Attendance frequency",
                    options = AttendanceFrequency.values().toList(),
                    selected = frequency,
                    optionLabel = { it.displayName },
                    onSelected = { frequency = it }
                )

                Text(
                    "Note: creating a new rule will deactivate the current active rule.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = requireLocation, onCheckedChange = { requireLocation = it })
                    Text("Require location verification (geofence)")
                }

                if (requireLocation) {
                    Text(
                        "Tap the map to set the geofence center, or use your current location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lat?.toString().orEmpty(),
                            onValueChange = { lat = it.toDoubleOrNull() },
                            label = { Text("Latitude") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = lon?.toString().orEmpty(),
                            onValueChange = { lon = it.toDoubleOrNull() },
                            label = { Text("Longitude") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (locationService.hasLocationPermission()) {
                                fetchingLoc = true
                                coroutineScope.launch {
                                    val loc = try { locationService.getCurrentLocation() } catch (_: Exception) { null }
                                    if (loc != null) { lat = loc.latitude; lon = loc.longitude }
                                    fetchingLoc = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !fetchingLoc
                    ) {
                        if (fetchingLoc) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Use my current location")
                    }
                    Text("Radius: ${radiusM.toInt()} m", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = radiusM,
                        onValueChange = { radiusM = it },
                        valueRange = 50f..2000f,
                        steps = 39
                    )
                    com.kosd.log_inattendancesafeguard.ui.maps.OsmGeofenceMap(
                        centerLat = lat,
                        centerLon = lon,
                        radiusMeters = radiusM.toDouble(),
                        interactive = true,
                        onMapTap = { tappedLat, tappedLon ->
                            lat = tappedLat
                            lon = tappedLon
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.createRule(
                        orgId,
                        AttendanceRuleCreateRequest(
                            organizationId = orgId,
                            name = name,
                            workStartTime = workStart,
                            workEndTime = workEnd,
                            checkInStart = checkInStart,
                            checkInEnd = checkInEnd,
                            checkOutStart = checkOutStart,
                            checkOutEnd = checkOutEnd,
                            gracePeriodMins = gracePeriod.toIntOrNull() ?: 0,
                            workingDays = frequency.workingDays,
                            requireLocation = requireLocation,
                            locationLatitude = if (requireLocation) lat else null,
                            locationLongitude = if (requireLocation) lon else null,
                            locationRadiusM = if (requireLocation) radiusM.toDouble() else null,
                            frequency = frequency
                        )
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank() && (!requireLocation || (lat != null && lon != null))
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TierSelector(
    selected: PopulationTier,
    onSelect: (PopulationTier) -> Unit,
    isUnlocked: (PopulationTier) -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PopulationTier.values().forEach { tier ->
            val unlocked = isUnlocked(tier)
            val active = tier == selected
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    active && unlocked -> MaterialTheme.colorScheme.primaryContainer
                    !unlocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = if (active && unlocked)
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = active,
                        onClick = { onSelect(tier) },
                        enabled = unlocked
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tier.displayName,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!unlocked) {
                        Icon(
                            Icons.Default.Lock, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionSection(billingViewModel: BillingViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    SectionHeader("Subscription & Add-Ons", Icons.Default.Workspaces)
    val canExport = billingViewModel.canExport
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canExport) statusContainerColor(AttendanceStatus.PRESENT)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (canExport) Icons.Default.IosShare else Icons.Default.Lock,
                null,
                tint = if (canExport) statusContentColor(AttendanceStatus.PRESENT) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (canExport) "CSV export & printing: Unlocked"
                    else "CSV export & printing: Locked",
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                Text(
                    if (canExport) "You can export attendance reports from the Reports tab."
                    else "Subscribe to any paid tier below to enable export and printing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Available plans", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            PopulationTier.values().forEach { tier ->
                if (tier.isFree) return@forEach
                val unlocked = billingViewModel.isUnlocked(tier)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tier.displayName, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Text(
                            if (unlocked) "Subscribed" else billingViewModel.priceText(tier),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unlocked) statusContentColor(AttendanceStatus.PRESENT)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unlocked) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = statusContentColor(AttendanceStatus.PRESENT),
                            modifier = Modifier.size(20.dp))
                    } else {
                        Button(
                            onClick = { activity?.let { billingViewModel.purchase(it, tier) } },
                            shape = MaterialTheme.shapes.extraSmall,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("Subscribe", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            Text(
                "Subscriptions are managed by Google Play. " +
                    "Tier product IDs must be configured in the Play Console.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    billingViewModel.lastError?.let { msg ->
        AlertDialog(
            onDismissRequest = { billingViewModel.dismissError() },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text("Purchase unavailable") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { billingViewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(10.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
