package com.picassoworkshop.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.picassoworkshop.app.data.DataUpdateState
import com.picassoworkshop.app.data.WorkshopDataManager
import com.picassoworkshop.app.data.model.EngineType
import com.picassoworkshop.app.data.model.Generation
import com.picassoworkshop.app.data.model.Transmission
import com.picassoworkshop.app.data.model.VehicleSelection
import com.picassoworkshop.app.obd.BluetoothConnectionManager
import com.picassoworkshop.app.obd.ObdDiagnosticsCoordinator
import com.picassoworkshop.app.ui.dialogs.VehicleSelectorSheet
import com.picassoworkshop.app.ui.home.ProceduresListScreen
import com.picassoworkshop.app.ui.obd.ObdDiagnosticsScreen
import com.picassoworkshop.app.ui.procedure.ProcedureDetailScreen
import com.picassoworkshop.app.ui.theme.DarkBackground
import com.picassoworkshop.app.ui.theme.PicassoWorkshopTheme
import com.picassoworkshop.app.update.AppUpdateManager
import com.picassoworkshop.app.update.AppUpdateState
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var dataManager: WorkshopDataManager
    private lateinit var updateManager: AppUpdateManager
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager
    private lateinit var obdCoordinator: ObdDiagnosticsCoordinator

    private val prefs by lazy {
        getSharedPreferences("picasso_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataManager = WorkshopDataManager(applicationContext)
        updateManager = AppUpdateManager(applicationContext)
        bluetoothConnectionManager = BluetoothConnectionManager(applicationContext)
        obdCoordinator = ObdDiagnosticsCoordinator(bluetoothConnectionManager, dataManager)

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                        navController.navigate("obd")
                    }
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
