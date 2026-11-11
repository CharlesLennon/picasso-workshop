package com.picassoworkshop.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.picassoworkshop.app.dashboard.DashboardManager
import com.picassoworkshop.app.dashboard.TelemetryRecorder
import com.picassoworkshop.app.data.DataUpdateState
import com.picassoworkshop.app.data.WorkshopDataManager
import com.picassoworkshop.app.data.model.EngineType
import com.picassoworkshop.app.data.model.Generation
import com.picassoworkshop.app.data.model.Transmission
import com.picassoworkshop.app.data.model.VehicleSelection
import com.picassoworkshop.app.obd.BluetoothConnectionManager
import com.picassoworkshop.app.obd.ObdDiagnosticsCoordinator
import com.picassoworkshop.app.ui.dashboard.DashboardsScreen
import com.picassoworkshop.app.ui.dialogs.VehicleSelectorSheet
import com.picassoworkshop.app.ui.home.ProceduresListScreen
import com.picassoworkshop.app.ui.obd.ObdDiagnosticsScreen
import com.picassoworkshop.app.ui.procedure.ProcedureDetailScreen
import com.picassoworkshop.app.ui.theme.*
import com.picassoworkshop.app.update.AppUpdateManager
import com.picassoworkshop.app.update.AppUpdateState
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var dataManager: WorkshopDataManager
    private lateinit var updateManager: AppUpdateManager
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager
    private lateinit var obdCoordinator: ObdDiagnosticsCoordinator
    private lateinit var dashboardManager: DashboardManager
    private lateinit var telemetryRecorder: TelemetryRecorder

    private val prefs by lazy {
        getSharedPreferences("picasso_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataManager = WorkshopDataManager(applicationContext)
        updateManager = AppUpdateManager(applicationContext)
        bluetoothConnectionManager = BluetoothConnectionManager(applicationContext)
        obdCoordinator = ObdDiagnosticsCoordinator(bluetoothConnectionManager, dataManager)
        dashboardManager = DashboardManager(applicationContext)
        telemetryRecorder = TelemetryRecorder(applicationContext)

        // Load saved vehicle selection or default to Mk1 1.6 HDi
        val isFirstLaunch = !prefs.getBoolean("has_selected_vehicle", false)
        val initialSelection = loadSavedVehicleSelection()

        setContent {
            PicassoWorkshopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    WorkshopApp(
                        dataManager = dataManager,
                        updateManager = updateManager,
                        obdCoordinator = obdCoordinator,
                        dashboardManager = dashboardManager,
                        telemetryRecorder = telemetryRecorder,
                        initialSelection = initialSelection,
                        startWithSelectorOpen = isFirstLaunch,
                        onSaveVehicleSelection = { selection ->
                            saveVehicleSelection(selection)
                        }
                    )
                }
            }
        }
    }

    private fun loadSavedVehicleSelection(): VehicleSelection {
        val genName = prefs.getString("vehicle_gen", Generation.MK1_B58.name)
        val engName = prefs.getString("vehicle_eng", EngineType.DV6_16_HDI.name)
        val transName = prefs.getString("vehicle_trans", Transmission.EGS6.name)

        val gen = try {
            Generation.valueOf(genName ?: Generation.MK1_B58.name)
        } catch (e: Exception) {
            Generation.MK1_B58
        }

        val eng = try {
            EngineType.valueOf(engName ?: EngineType.DV6_16_HDI.name)
        } catch (e: Exception) {
            EngineType.DV6_16_HDI
        }

        val trans = try {
            Transmission.valueOf(transName ?: Transmission.EGS6.name)
        } catch (e: Exception) {
            Transmission.EGS6
        }

        return VehicleSelection(gen, eng, trans)
    }

    private fun saveVehicleSelection(selection: VehicleSelection) {
        prefs.edit()
            .putBoolean("has_selected_vehicle", true)
            .putString("vehicle_gen", selection.generation.name)
            .putString("vehicle_eng", selection.engine.name)
            .putString("vehicle_trans", selection.transmission.name)
            .apply()
    }
}

@Composable
fun WorkshopApp(
    dataManager: WorkshopDataManager,
    updateManager: AppUpdateManager,
    obdCoordinator: ObdDiagnosticsCoordinator,
    dashboardManager: DashboardManager,
    telemetryRecorder: TelemetryRecorder,
    initialSelection: VehicleSelection,
    startWithSelectorOpen: Boolean,
    onSaveVehicleSelection: (VehicleSelection) -> Unit
) {
    val navController = rememberNavController()
    var currentSelection by remember { mutableStateOf(initialSelection) }
    var showVehicleSelector by remember { mutableStateOf(startWithSelectorOpen) }

    val contentPackage by dataManager.contentPackage.collectAsState()
    val dataUpdateState by dataManager.updateState.collectAsState()
    val appUpdateState by updateManager.updateState.collectAsState()

    // Automatic update checks on startup
    LaunchedEffect(Unit) {
        dataManager.checkForDataUpdates()
        updateManager.checkForAppUpdates()
    }

    // Filter procedures for currently selected vehicle
    val availableProcedures = remember(contentPackage, currentSelection) {
        dataManager.getProceduresForVehicle(currentSelection)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Persistent bottom navigation bar for top-level destinations
            if (currentRoute == "home" || currentRoute == "dashboards" || currentRoute == "obd") {
                NavigationBar(
                    containerColor = WorkshopSurfaceElevated,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                        icon = {
                            Icon(Icons.Default.Build, contentDescription = "Tutorials")
                        },
                        label = {
                            Text(
                                text = "TUTORIALS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CitroenRed,
                            selectedTextColor = CitroenRed,
                            indicatorColor = CitroenRed.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == "dashboards",
                        onClick = {
                            if (currentRoute != "dashboards") {
                                navController.navigate("dashboards") {
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(Icons.Default.Speed, contentDescription = "Live Telemetry Dashboards")
                        },
                        label = {
                            Text(
                                text = "DASHBOARDS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == "obd",
                        onClick = {
                            if (currentRoute != "obd") {
                                navController.navigate("obd") {
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            Icon(Icons.Default.Troubleshoot, contentDescription = "OBD Diagnostics")
                        },
                        label = {
                            Text(
                                text = "OBD SCAN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WarningOrange,
                            selectedTextColor = WarningOrange,
                            indicatorColor = WarningOrange.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = showVehicleSelector,
                        onClick = {
                            showVehicleSelector = true
                        },
                        icon = {
                            Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle Selection")
                        },
                        label = {
                            Text(
                                text = "VEHICLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldenBolt,
                            selectedTextColor = GoldenBolt,
                            indicatorColor = GoldenBolt.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    ProceduresListScreen(
                        currentSelection = currentSelection,
                        procedures = availableProcedures,
                        dataVersion = contentPackage.dataVersion,
                        appVersion = updateManager.currentVersionName,
                        dataUpdateState = dataUpdateState,
                        appUpdateState = appUpdateState,
                        onSwitchVehicleClick = { showVehicleSelector = true },
                        onProcedureClick = { procedure ->
                            navController.navigate("procedure/${procedure.id}")
                        },
                        onCheckDataUpdatesClick = {
                            dataManager.checkForDataUpdates()
                        },
                        onCheckAppUpdatesClick = {
                            updateManager.checkForAppUpdates()
                        },
                        onApplyDataUpdateClick = { downloadUrl ->
                            dataManager.downloadAndApplyDataUpdate(downloadUrl)
                        },
                        onDownloadAppUpdateClick = {
                            val release = updateManager.cachedRelease
                            if (release != null) {
                                updateManager.startDownload(release)
                            }
                        },
                        onInstallAppUpdateClick = {
                            val state = appUpdateState
                            if (state is AppUpdateState.ReadyToInstall) {
                                updateManager.installApk(state.apkFile)
                            }
                        },
                        onDismissDataUpdate = {
                            dataManager.dismissUpdateState()
                        },
                        onDismissAppUpdate = {
                            updateManager.dismissUpdate()
                        },
                        onOpenObdClick = {
                            navController.navigate("dashboards")
                        }
                    )
                }

                composable("dashboards") {
                    DashboardsScreen(
                        coordinator = obdCoordinator,
                        dashboardManager = dashboardManager,
                        telemetryRecorder = telemetryRecorder,
                        onBackClick = { navController.popBackStack() },
                        onOpenObdScan = { navController.navigate("obd") }
                    )
                }

                composable("obd") {
                    ObdDiagnosticsScreen(
                        coordinator = obdCoordinator,
                        onBackClick = { navController.popBackStack() },
                        onOpenProcedure = { procedureId ->
                            navController.navigate("procedure/$procedureId")
                        }
                    )
                }

                composable(
                    route = "procedure/{procedureId}",
                    arguments = listOf(navArgument("procedureId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val procedureId = backStackEntry.arguments?.getString("procedureId") ?: ""
                    val procedure = remember(procedureId, contentPackage) {
                        dataManager.getProcedureById(procedureId)
                    }

                    if (procedure != null) {
                        ProcedureDetailScreen(
                            procedure = procedure,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }

            // Vehicle Selector Modal Sheet (Onboarding or Switcher)
            if (showVehicleSelector) {
                VehicleSelectorSheet(
                    currentSelection = currentSelection,
                    onDismiss = { showVehicleSelector = false },
                    onConfirmSelection = { newSelection ->
                        currentSelection = newSelection
                        onSaveVehicleSelection(newSelection)
                        showVehicleSelector = false
                    }
                )
            }
        }
    }
}
