package com.kosd.log_inattendancesafeguard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kosd.log_inattendancesafeguard.ui.admin.AdminDashboardScreen
import com.kosd.log_inattendancesafeguard.ui.admin.MembersScreen
import com.kosd.log_inattendancesafeguard.ui.admin.OrgSettingsScreen
import com.kosd.log_inattendancesafeguard.ui.auth.LoginScreen
import com.kosd.log_inattendancesafeguard.ui.auth.RegisterScreen
import com.kosd.log_inattendancesafeguard.ui.event.EventListScreen
import com.kosd.log_inattendancesafeguard.ui.event.EventCreateScreen
import com.kosd.log_inattendancesafeguard.ui.event.EventDetailScreen
import com.kosd.log_inattendancesafeguard.ui.event.EventReportScreen
import com.kosd.log_inattendancesafeguard.ui.event.EventStaffScreen
import com.kosd.log_inattendancesafeguard.ui.event.KioskModeScreen
import com.kosd.log_inattendancesafeguard.ui.history.HistoryScreen
import com.kosd.log_inattendancesafeguard.ui.home.HomeScreen
import com.kosd.log_inattendancesafeguard.ui.profile.ProfileScreen
import com.kosd.log_inattendancesafeguard.ui.theme.pressScale
import com.kosd.log_inattendancesafeguard.ClockCardApp
import com.kosd.log_inattendancesafeguard.viewmodel.AdminViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AttendanceViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.BillingViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.EventViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.OrganizationViewModel
import com.kosd.log_inattendancesafeguard.models.Permission
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login    : Screen("login")
    object Register : Screen("register")
    object EmailConfirmation : Screen("email_confirmation")
    object Main     : Screen("main")
    object EventList   : Screen("event_list")
    object EventCreate : Screen("event_create")
    object EventDetail : Screen("event_detail/{eventId}")
    object EventReport : Screen("event_report/{eventId}")
    object Kiosk       : Screen("kiosk/{eventId}")
}

// ── Bottom nav items (TEAMS mode only) ───────────────────────────────────────
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false
) {
    object Home    : BottomNavItem("home",    "Home",    Icons.Default.Home)
    object History : BottomNavItem("history", "History", Icons.Default.History)
    object Reports : BottomNavItem("reports", "Reports", Icons.Default.BarChart, adminOnly = true)
}

// ── Drawer menu items ─────────────────────────────────────────────────────────
sealed class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
    val ownerOnly: Boolean = false
) {
    object Profile      : DrawerItem("profile",       "Profile",       Icons.Default.Person)
    object Members      : DrawerItem("members",       "Members",       Icons.Default.People,    adminOnly = true)
    object Settings     : DrawerItem("settings",      "Settings",      Icons.Default.Settings,  adminOnly = true)
    object EventStaff   : DrawerItem("event_staff",   "Event Staff",   Icons.Default.Badge,     ownerOnly = true)
}

// Routes that belong to the TEAMS bottom-nav group
private val teamsRoutes = setOf(BottomNavItem.Home.route, BottomNavItem.History.route, BottomNavItem.Reports.route)

// Routes that are full-screen overlays (no bottom bar, no top bar)
private val fullScreenRoutes = setOf(
    "mode_selector", "profile", "members", "settings", "event_staff",
    "event_create", "event_detail", "event_report", "kiosk"
)

@Composable
fun ClockCardNavHost() {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel            = viewModel(factory = AuthViewModel.Factory())
    val attendanceViewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory())
    val orgViewModel: OrganizationViewModel      = viewModel(factory = OrganizationViewModel.Factory())
    val adminViewModel: AdminViewModel           = viewModel(factory = AdminViewModel.Factory())
    val eventViewModel: EventViewModel           = viewModel(factory = EventViewModel.Factory())
    val app = LocalContext.current.applicationContext as ClockCardApp
    val billingViewModel: BillingViewModel       = viewModel(factory = BillingViewModel.Factory(app, orgViewModel))

    // Restore session on launch
    LaunchedEffect(Unit) {
        authViewModel.loadCurrentUser(orgViewModel)
    }

    // Navigate to EmailConfirmation screen when requiresEmailConfirmation is set
    LaunchedEffect(authViewModel.requiresEmailConfirmation) {
        if (authViewModel.requiresEmailConfirmation) {
            navController.navigate(Screen.EmailConfirmation.route) {
                popUpTo(Screen.Register.route) { inclusive = true }
            }
        }
    }

    // When email is confirmed via Resend (emailConfirmed = true), navigate to
    // Login so the user can sign in. pendingRegistrationCompletion stays true
    // so completeRegistrationAfterConfirmation runs after sign-in.
    LaunchedEffect(authViewModel.emailConfirmed) {
        if (authViewModel.emailConfirmed) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Observe Supabase session status — when a session appears via deep link
    // callback while waiting for email confirmation, complete the registration.
    // In the Resend flow, the user signs in manually after email confirmation,
    // so this watches pendingRegistrationCompletion instead.
    LaunchedEffect(authViewModel.pendingRegistrationCompletion) {
        if (authViewModel.pendingRegistrationCompletion) {
            while (authViewModel.pendingRegistrationCompletion) {
                if (authViewModel.repository.currentUserId() != null) {
                    authViewModel.completeRegistrationAfterConfirmation(orgViewModel)
                    break
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // Global auth state observer
    LaunchedEffect(authViewModel.isAuthenticated) {
        val current = navController.currentBackStackEntry?.destination?.route
        if (authViewModel.isAuthenticated) {
            if (current == Screen.Login.route || current == Screen.Register.route ||
                current == Screen.EmailConfirmation.route) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } else {
            orgViewModel.resetState()
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel            = authViewModel,
                orgViewModel         = orgViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel       = authViewModel,
                orgViewModel    = orgViewModel,
                onNavigateBack  = { navController.popBackStack() }
            )
        }

        composable(Screen.EmailConfirmation.route) {
            EmailConfirmationScreen(
                viewModel   = authViewModel,
                onBackToLogin = {
                    authViewModel.requiresEmailConfirmation = false
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                authViewModel       = authViewModel,
                attendanceViewModel = attendanceViewModel,
                orgViewModel        = orgViewModel,
                adminViewModel      = adminViewModel,
                eventViewModel      = eventViewModel,
                billingViewModel    = billingViewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

// ── Bifocal Mode Selector Screen ─────────────────────────────────────────────

@Composable
fun ModeSelectorScreen(
    onSelectTeams: () -> Unit,
    onSelectEvents: () -> Unit,
    showEvents: Boolean = true,
    lastMode: String? = null
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccessTimeFilled, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("What are you managing?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Choose a workspace. You can switch later from the menu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))

                val teamsCard: @Composable (Modifier) -> Unit = { modifier ->
                    ModeCard(
                        icon = Icons.Default.Groups,
                        eyebrow = "TEAMS",
                        title = "Team attendance",
                        subtitle = "Daily check-ins, schedules, attendance history and reports.",
                        badge = if (lastMode == "teams") "LAST USED" else null,
                        gradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(.76f))),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = modifier,
                        onClick = onSelectTeams
                    )
                }
                val eventsCard: @Composable (Modifier) -> Unit = { modifier ->
                    ModeCard(
                        icon = Icons.Default.ConfirmationNumber,
                        eyebrow = "EVENTS",
                        title = "Event check-ins",
                        subtitle = "Guest registration, QR check-in, venue access and event reports.",
                        badge = if (lastMode == "events") "LAST USED" else null,
                        gradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(.76f))),
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = modifier,
                        onClick = onSelectEvents
                    )
                }

                if (wideLayout) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        teamsCard(Modifier.weight(1f))
                        if (showEvents) eventsCard(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        teamsCard(Modifier.fillMaxWidth())
                        if (showEvents) eventsCard(Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Switch workspaces any time from the navigation menu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    subtitle: String,
    badge: String?,
    gradient: Brush,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 176.dp)
            .pressScale(scaleDown = 0.98f)
            .semantics {
                role = Role.Button
                contentDescription = "Open $title workspace"
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = contentColor.copy(.16f)) {
                    Icon(icon, null, Modifier.padding(10.dp).size(28.dp), tint = contentColor)
                }
                badge?.let {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = contentColor.copy(.16f)) {
                        Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = contentColor)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(.78f))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(subtitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(.82f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = contentColor, modifier = Modifier.padding(start = 12.dp).size(22.dp))
            }
        }
    }
}

// ── Main Screen (hosts both TEAMS and EVENTS modes) ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    attendanceViewModel: AttendanceViewModel,
    orgViewModel: OrganizationViewModel,
    adminViewModel: AdminViewModel,
    eventViewModel: EventViewModel,
    billingViewModel: BillingViewModel,
    onLogout: () -> Unit
) {
    val innerNav: NavHostController = rememberNavController()
    val isAuthenticated: Boolean = authViewModel.isAuthenticated
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val modePreferences = remember { context.getSharedPreferences("workspace_mode", android.content.Context.MODE_PRIVATE) }
    val modePreferenceKey = "last_mode_${orgViewModel.activeOrg?.id ?: "default"}"
    var lastMode by remember(modePreferenceKey) { mutableStateOf(modePreferences.getString(modePreferenceKey, null)) }
    var forceModeSelector by remember { mutableStateOf(false) }
    var modeContextReady by remember { mutableStateOf(orgViewModel.activeOrg != null && orgViewModel.activeMembership != null) }

    val backStackEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Refresh orgs on entry (but don't overwrite if already loaded)
    LaunchedEffect(Unit) {
        if (orgViewModel.activeOrg == null || orgViewModel.activeMembership == null) {
            orgViewModel.loadOrganizationsAwait()
        }
        modeContextReady = true
    }

    // Watch for logout
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == false) onLogout()
    }

    val isAdmin = orgViewModel.isAdminInActiveOrg

    // Bottom nav items (TEAMS mode only)
    val bottomItems by remember {
        derivedStateOf {
            buildList {
                add(BottomNavItem.Home)
                add(BottomNavItem.History)
                if (orgViewModel.can(Permission.VIEW_REPORTS)) add(BottomNavItem.Reports)
            }
        }
    }

    // Drawer items (admin-gated)
    val isOwner = orgViewModel.isOwnerInActiveOrg

    val drawerItems by remember {
        derivedStateOf {
            buildList {
                add(DrawerItem.Profile)
                if (orgViewModel.can(Permission.MANAGE_MEMBERS)) add(DrawerItem.Members)
                if (isOwner) add(DrawerItem.Settings)
                if (isOwner) add(DrawerItem.EventStaff)
            }
        }
    }

    val showBottomBar = currentRoute in teamsRoutes
    val showTopBar = currentRoute != "mode_selector" && currentRoute !in fullScreenRoutes
    val showBackOnEvents = currentRoute == "events" || currentRoute == "event_list"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                // Drawer header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = authViewModel.currentUser?.initials ?: "?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = authViewModel.currentUser?.fullName ?: "User",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = authViewModel.currentUser?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (orgViewModel.activeOrg != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = orgViewModel.activeOrg?.name ?: "",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                HorizontalDivider()

                // Drawer items
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            innerNav.navigate(item.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Mode switch + logout at bottom
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Switch Mode") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        forceModeSelector = true
                        innerNav.navigate("mode_selector") {
                            popUpTo(innerNav.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Logout") },
                    label = { Text("Logout") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        authViewModel.logout()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = { Text(titleForRoute(currentRoute)) },
                        navigationIcon = {
                            if (showBackOnEvents) {
                                IconButton(onClick = {
                                    forceModeSelector = true
                                    innerNav.navigate("mode_selector") {
                                        popUpTo(innerNav.graph.findStartDestination().id) {
                                            inclusive = true
                                        }
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        bottomItems.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = currentRoute == item.route,
                                onClick = {
                                    innerNav.navigate(item.route) {
                                        popUpTo(innerNav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            NavHost(
                navController = innerNav,
                startDestination = "mode_selector",
                modifier = Modifier.padding(paddingValues)
            ) {
                // ── Mode Selector ──────────────────────────────────────────────
                composable("mode_selector") {
                    val canUseEvents = orgViewModel.isEventStaffInActiveOrg
                    LaunchedEffect(modeContextReady, canUseEvents, lastMode, forceModeSelector) {
                        if (!modeContextReady) return@LaunchedEffect
                        val destination = when {
                            !canUseEvents -> BottomNavItem.Home.route
                            !forceModeSelector && lastMode == "teams" -> BottomNavItem.Home.route
                            !forceModeSelector && lastMode == "events" -> "events"
                            else -> null
                        }
                        destination?.let {
                            innerNav.navigate(it) {
                                popUpTo("mode_selector") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    if (!modeContextReady) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else ModeSelectorScreen(
                        showEvents = canUseEvents,
                        lastMode = lastMode,
                        onSelectTeams = {
                            lastMode = "teams"
                            forceModeSelector = false
                            modePreferences.edit().putString(modePreferenceKey, "teams").apply()
                            innerNav.navigate(BottomNavItem.Home.route) {
                                popUpTo("mode_selector") { inclusive = true }
                            }
                        },
                        onSelectEvents = {
                            lastMode = "events"
                            forceModeSelector = false
                            modePreferences.edit().putString(modePreferenceKey, "events").apply()
                            innerNav.navigate("events") {
                                popUpTo("mode_selector") { inclusive = true }
                            }
                        }
                    )
                }

                // ── TEAMS bottom nav screens ──────────────────────────────────
                composable(BottomNavItem.Home.route) {
                    HomeScreen(authViewModel, attendanceViewModel, orgViewModel)
                }
                composable(BottomNavItem.History.route) {
                    HistoryScreen(attendanceViewModel)
                }
                composable(BottomNavItem.Reports.route) {
                    AdminDashboardScreen(authViewModel, adminViewModel, orgViewModel, billingViewModel)
                }

                // ── Drawer screens (full-screen) ──────────────────────────────
                composable(DrawerItem.Profile.route) {
                    ProfileScreen(authViewModel, orgViewModel)
                }
                composable(DrawerItem.Members.route) {
                    MembersScreen(orgViewModel)
                }
                composable(DrawerItem.Settings.route) {
                    OrgSettingsScreen(orgViewModel, billingViewModel)
                }
                composable(DrawerItem.EventStaff.route) {
                    EventStaffScreen(orgViewModel)
                }

                // ── EVENTS mode ───────────────────────────────────────────────
                composable("events") {
                    EventListScreen(navController = innerNav, orgViewModel = orgViewModel, eventViewModel = eventViewModel)
                }
                composable("event_create") {
                    EventCreateScreen(navController = innerNav, orgViewModel = orgViewModel, eventViewModel = eventViewModel)
                }
                composable("event_detail/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    val event = eventViewModel.events.find { it.id == eventId } ?: eventViewModel.selectedEvent
                    if (event != null) {
                        EventDetailScreen(
                            navController = innerNav,
                            event = event,
                            eventViewModel = eventViewModel,
                            isAdmin = orgViewModel.can(Permission.MANAGE_EVENTS),
                            isEventStaff = orgViewModel.isEventStaffInActiveOrg
                        )
                    }
                }
                composable("event_report/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    val event = eventViewModel.events.find { it.id == eventId } ?: eventViewModel.selectedEvent
                    if (event != null) {
                        EventReportScreen(navController = innerNav, event = event, eventViewModel = eventViewModel)
                    }
                }
                composable("kiosk/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    val event = eventViewModel.events.find { it.id == eventId } ?: eventViewModel.selectedEvent
                    if (event != null) {
                        KioskModeScreen(navController = innerNav, event = event, eventViewModel = eventViewModel)
                    }
                }
            }
        }
    }
}

private fun titleForRoute(route: String?): String {
    return when (route) {
        BottomNavItem.Home.route    -> "Teams"
        BottomNavItem.History.route -> "My Attendance"
        BottomNavItem.Reports.route -> "Reports & Analytics"
        DrawerItem.Profile.route    -> "Profile"
        DrawerItem.Members.route    -> "Member Management"
        DrawerItem.Settings.route   -> "Organization Settings"
        DrawerItem.EventStaff.route -> "Event Staff Management"
        "events"                    -> "Events"
        else                        -> "ClockCard"
    }
}

// ── Email Confirmation Screen ────────────────────────────────────────────────

@Composable
fun EmailConfirmationScreen(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Check Your Email",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "We've sent a confirmation link to:\n${viewModel.pendingEmail ?: "your email"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Click the link in the email to verify your account and complete registration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (viewModel.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Verifying...", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onBackToLogin) {
            Text("Back to Login")
        }
    }
}
