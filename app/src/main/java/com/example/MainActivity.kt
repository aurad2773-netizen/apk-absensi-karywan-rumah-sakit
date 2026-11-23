package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.HospitalDatabase
import com.example.data.preferences.SessionPreferences
import com.example.data.repository.HospitalRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LeaveScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.theme.MedPresensiTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.HospitalViewModel

object AppRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val SCHEDULE = "schedule"
    const val HISTORY = "history"
    const val LEAVE = "leave"
    const val PROFILE = "profile"
}

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val MAIN_TABS = listOf(
    NavTab(AppRoutes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(AppRoutes.SCHEDULE, R.string.nav_schedule, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    NavTab(AppRoutes.HISTORY, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    NavTab(AppRoutes.LEAVE, R.string.nav_leave, Icons.Filled.EventNote, Icons.Outlined.EventNote),
    NavTab(AppRoutes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedPresensiTheme {
                val context = LocalContext.current.applicationContext
                val repository = remember {
                    val db = HospitalDatabase.getInstance(context)
                    HospitalRepository(
                        employeeDao = db.employeeDao(),
                        attendanceDao = db.attendanceDao(),
                        shiftScheduleDao = db.shiftScheduleDao(),
                        leaveRequestDao = db.leaveRequestDao(),
                        sessionPreferences = SessionPreferences(context)
                    )
                }
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(repository)
                )
                MedPresensiRoot(
                    authViewModel = authViewModel,
                    repository = repository
                )
            }
        }
    }
}

@Composable
private fun MedPresensiRoot(
    authViewModel: AuthViewModel,
    repository: HospitalRepository
) {
    val loggedInEmployeeId by authViewModel.loggedInEmployeeId.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    val currentEmpId = loggedInEmployeeId
    if (currentEmpId == null) {
        val authNavController = rememberNavController()
        NavHost(
            navController = authNavController,
            startDestination = AppRoutes.LOGIN
        ) {
            composable(AppRoutes.LOGIN) {
                LoginScreen(
                    uiState = authUiState,
                    onLoginClick = { identifier, password ->
                        authViewModel.login(identifier, password)
                    },
                    onNavigateToRegister = {
                        authViewModel.clearError()
                        authNavController.navigate(AppRoutes.REGISTER)
                    },
                    onClearError = authViewModel::clearError
                )
            }
            composable(AppRoutes.REGISTER) {
                RegisterScreen(
                    uiState = authUiState,
                    onRegisterClick = { nip, name, email, pass, role, dept, phone, status, shift ->
                        authViewModel.register(
                            nip = nip,
                            fullName = name,
                            email = email,
                            password = pass,
                            role = role,
                            department = dept,
                            phone = phone,
                            employmentStatus = status,
                            defaultShift = shift
                        )
                    },
                    onBack = {
                        authViewModel.clearError()
                        authNavController.popBackStack()
                    },
                    onClearError = authViewModel::clearError
                )
            }
        }
    } else {
        key(currentEmpId) {
            val hospitalViewModel: HospitalViewModel = viewModel(
                key = "emp_$currentEmpId",
                factory = HospitalViewModel.Factory(currentEmpId, repository)
            )
            MainHospitalShell(
                viewModel = hospitalViewModel,
                onLogout = { authViewModel.logout() }
            )
        }
    }
}

@Composable
private fun MainHospitalShell(
    viewModel: HospitalViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val clockMillis by viewModel.clockMillis.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback.snackbarMessage) {
        val msg = feedback.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        MAIN_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MAIN_TABS.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.DASHBOARD
                    ) {
                        composable(AppRoutes.DASHBOARD) {
                            DashboardScreen(
                                state = dashboardState,
                                clockMillis = clockMillis,
                                feedback = feedback,
                                onRefreshGps = { viewModel.refreshGps(context) },
                                onCheckIn = { shift, mode, notes ->
                                    viewModel.performCheckIn(shift, mode, notes)
                                },
                                onCheckOut = { notes ->
                                    viewModel.performCheckOut(notes)
                                },
                                onNavigateToHistory = { navigateToTab(AppRoutes.HISTORY) },
                                onNavigateToSchedule = { navigateToTab(AppRoutes.SCHEDULE) }
                            )
                        }
                        composable(AppRoutes.SCHEDULE) {
                            ScheduleScreen(
                                schedules = dashboardState.shiftSchedules,
                                defaultDepartment = dashboardState.employee?.department ?: "Instalasi Rawat Inap",
                                defaultShift = dashboardState.todayShift,
                                onSaveShift = { dateKey, shiftType, unit, notes ->
                                    viewModel.saveShiftSchedule(dateKey, shiftType, unit, notes)
                                }
                            )
                        }
                        composable(AppRoutes.HISTORY) {
                            HistoryScreen(history = dashboardState.attendanceHistory)
                        }
                        composable(AppRoutes.LEAVE) {
                            LeaveScreen(
                                requests = dashboardState.leaveRequests,
                                onSubmitRequest = { type, start, end, reason, uri, onDone ->
                                    viewModel.submitLeaveRequest(type, start, end, reason, uri, onDone)
                                },
                                onDeleteRequest = { id ->
                                    viewModel.deleteLeaveRequest(id)
                                }
                            )
                        }
                        composable(AppRoutes.PROFILE) {
                            ProfileScreen(
                                employee = dashboardState.employee,
                                hospitalConfig = dashboardState.hospitalConfig,
                                onUpdateProfile = { name, role, dept, phone, shift, uri ->
                                    viewModel.updateProfile(name, role, dept, phone, shift, uri)
                                },
                                onCalibrateHospitalGps = { hospName, radius ->
                                    viewModel.calibrateHospitalWithCurrentGps(context, hospName, radius)
                                },
                                onSaveHospitalSettings = { hospName, radius ->
                                    viewModel.saveHospitalSettings(hospName, radius)
                                },
                                onLogout = onLogout
                            )
                        }
                    }
                }
            }
        }
    }
}
