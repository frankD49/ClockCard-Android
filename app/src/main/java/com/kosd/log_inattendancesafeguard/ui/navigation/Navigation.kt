package com.kosd.log_inattendancesafeguard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.kosd.log_inattendancesafeguard.ui.auth.ForgotPasswordScreen
import com.kosd.log_inattendancesafeguard.ui.auth.ResetPasswordScreen
import com.kosd.log_inattendancesafeguard.ui.history.HistoryScreen
import com.kosd.log_inattendancesafeguard.ui.home.HomeScreen
import com.kosd.log_inattendancesafeguard.ui.profile.ProfileScreen
import com.kosd.log_inattendancesafeguard.ClockCardApp
import com.kosd.log_inattendancesafeguard.viewmodel.AdminViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AttendanceViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import com.kosd.log_inattendancesafeguard.viewmodel.BillingViewModel
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
    object ForgotPassword : Screen("forgot_password")
    object ResetPassword  : Screen("reset_password")
    object Main     : Screen("main")
}

// ── Bottom nav items (TEAMS mode) ───────────────────────────────────────────
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
}

// Routes that belong to the TEAMS bottom-nav group
private val teamsRoutes = setOf(BottomNavItem.Home.route, BottomNavItem.History.route, BottomNavItem.Reports.route)

// Routes that are full-screen overlays (no bottom bar, no top bar)
private val fullScreenRoutes = setOf(
    "profile", "members", "settings"
)

@Composable
fun ClockCardNavHost() {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel            = viewModel(factory = AuthViewModel.Factory())
    val attendanceViewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory())
    val orgViewModel: OrganizationViewModel      = viewModel(factory = OrganizationViewModel.Factory())
    val adminViewModel: AdminViewModel           = viewModel(factory = AdminViewModel.Factory())
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

    // When a password-reset deep link token is received, navigate to the
    // ResetPassword screen so the user can enter a new password.
    LaunchedEffect(authViewModel.passwordResetToken) {
        if (authViewModel.passwordResetToken != null) {
            navController.navigate(Screen.ResetPassword.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // When password reset is complete, navigate to Login.
    LaunchedEffect(authViewModel.passwordResetComplete) {
        if (authViewModel.passwordResetComplete) {
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
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
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

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ResetPassword.route) {
            ResetPasswordScreen(
                viewModel = authViewModel,
                onBackToLogin = {
                    authViewModel.passwordResetToken = null
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

// ── Main Screen (TEAMS mode) ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    attendanceViewModel: AttendanceViewModel,
    orgViewModel: OrganizationViewModel,
    adminViewModel: AdminViewModel,
    billingViewModel: BillingViewModel,
    onLogout: () -> Unit
) {
    val innerNav: NavHostController = rememberNavController()
    val isAuthenticated: Boolean = authViewModel.isAuthenticated
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Refresh orgs on entry (but don't overwrite if already loaded)
    LaunchedEffect(Unit) {
        if (orgViewModel.activeOrg == null || orgViewModel.activeMembership == null) {
            orgViewModel.loadOrganizationsAwait()
        }
    }

    // Watch for logout
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == false) onLogout()
    }

    // Bottom nav items (TEAMS mode)
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
            }
        }
    }

    val showBottomBar = currentRoute in teamsRoutes
    val showTopBar = currentRoute !in fullScreenRoutes

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
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                startDestination = BottomNavItem.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
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
