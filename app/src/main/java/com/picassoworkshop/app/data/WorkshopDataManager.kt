package com.picassoworkshop.app.data

import android.content.Context
import com.picassoworkshop.app.data.model.RepairProcedure
import com.picassoworkshop.app.data.model.VehicleSelection
import com.picassoworkshop.app.data.model.WorkshopContentPackage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.File

sealed class DataUpdateState {
    object Idle : DataUpdateState()
    object Checking : DataUpdateState()
    data class UpdateAvailable(val newVersion: String, val releaseNotes: String, val downloadUrl: String) : DataUpdateState()
    data class Downloading(val progress: Float) : DataUpdateState()
    data class Updated(val version: String) : DataUpdateState()
    object UpToDate : DataUpdateState()
    data class Error(val message: String) : DataUpdateState()
}

class WorkshopDataManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@WorkshopDataManager.json)
        }
    }

    private val _contentPackage = MutableStateFlow<WorkshopContentPackage>(WorkshopContentPackage())
    val contentPackage: StateFlow<WorkshopContentPackage> = _contentPackage.asStateFlow()

    private val _updateState = MutableStateFlow<DataUpdateState>(DataUpdateState.Idle)
    val updateState: StateFlow<DataUpdateState> = _updateState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        scope.launch {
            try {
                val cachedFile = File(context.filesDir, "workshop_data.json")
                val jsonString = if (cachedFile.exists() && cachedFile.length() > 0) {
                    Timber.d("Loading workshop data from local cache: ${cachedFile.absolutePath}")
                    cachedFile.readText()
                } else {
                    Timber.d("Loading workshop data from bundled assets")
                    context.assets.open("workshop_data.json").bufferedReader().use { it.readText() }
                }

                val pkg = json.decodeFromString<WorkshopContentPackage>(jsonString)
                _contentPackage.value = pkg
                Timber.d("Workshop data loaded: version ${pkg.dataVersion}, ${pkg.procedures.size} procedures")
            } catch (e: Throwable) {
                Timber.e(e, "Failed to load workshop data package")
            }
        }
    }

    fun getProceduresForVehicle(selection: VehicleSelection): List<RepairProcedure> {
        val current = _contentPackage.value
        return current.procedures.filter { proc ->
            selection.matches(proc.targetGeneration, proc.targetEngine)
        }
    }

    fun getProcedureById(id: String): RepairProcedure? {
        return _contentPackage.value.procedures.firstOrNull { it.id == id }
    }

    fun getDiagnosticEcusForVehicle(selection: VehicleSelection): List<com.picassoworkshop.app.data.model.DiagnosticEcuConfig> {
        val all = _contentPackage.value.diagnosticEcus
        return all.filter { ecu ->
            selection.matches(ecu.targetGeneration, ecu.targetEngine)
        }
    }

    fun getDtcDefinition(code: String): com.picassoworkshop.app.data.model.DtcDefinition? {
        val clean = code.trim().uppercase()
        return _contentPackage.value.dtcDefinitions.firstOrNull { it.code.equals(clean, ignoreCase = true) }
    }

    fun getLivePids(): List<com.picassoworkshop.app.data.model.LivePidConfig> {
        return _contentPackage.value.livePids
    }

    fun checkForDataUpdates(dataRepo: String = "CharlesLennon/picasso-data") {
        scope.launch {
            _updateState.value = DataUpdateState.Checking
            try {
                val url = "https://api.github.com/repos/$dataRepo/releases/latest"
                val response = httpClient.get(url) {
                    header("Accept", "application/vnd.github.v3+json")
                    header("User-Agent", "PicassoWorkshop-DataUpdater")
                }

                if (response.status.value in 200..299) {
                    val body = response.bodyAsText()
                    val releaseObj = json.parseToJsonElement(body).jsonObject
                    val tagName = releaseObj["tag_name"]?.jsonPrimitive?.content ?: ""
                    val releaseNotes = releaseObj["body"]?.jsonPrimitive?.content ?: ""
                    val assets = releaseObj["assets"]?.jsonArray ?: emptyList()

                    val dataAsset = assets.firstOrNull {
                        val name = it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                        name.endsWith(".json", ignoreCase = true)
                    }

                    if (dataAsset != null) {
                        val downloadUrl = dataAsset.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                        val remoteVersion = tagName.removePrefix("v").replace(".", "_")
                        val currentVersion = _contentPackage.value.dataVersion

                        if (isNewerDataVersion(remoteVersion, currentVersion)) {
                            _updateState.value = DataUpdateState.UpdateAvailable(
                                newVersion = remoteVersion,
                                releaseNotes = releaseNotes,
                                downloadUrl = downloadUrl
                            )
                        } else {
                            _updateState.value = DataUpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = DataUpdateState.UpToDate
                    }
                } else {
                    _updateState.value = DataUpdateState.Error("HTTP ${response.status.value}: Unable to query data updates")
                }
            } catch (e: Throwable) {
                Timber.e(e, "Data update check failed")
                _updateState.value = DataUpdateState.Error(e.message ?: "Network error checking for data updates")
            }
        }
    }

    fun downloadAndApplyDataUpdate(downloadUrl: String) {
        scope.launch {
            _updateState.value = DataUpdateState.Downloading(0.1f)
            try {
                val response = httpClient.get(downloadUrl) {
                    header("User-Agent", "PicassoWorkshop-DataUpdater")
                }
                val rawJson = response.bodyAsText()
                val newPackage = json.decodeFromString<WorkshopContentPackage>(rawJson)

                // Save to local cache
                withContext(Dispatchers.IO) {
                    val cacheFile = File(context.filesDir, "workshop_data.json")
                    cacheFile.writeText(rawJson)
                }

                _contentPackage.value = newPackage
                _updateState.value = DataUpdateState.Updated(newPackage.dataVersion)
                Timber.d("Successfully hot-reloaded workshop data to version: ${newPackage.dataVersion}")
            } catch (e: Throwable) {
                Timber.e(e, "Failed to download and apply data update")
                _updateState.value = DataUpdateState.Error("Failed to apply update: ${e.message}")
            }
        }
    }

    fun dismissUpdateState() {
        _updateState.value = DataUpdateState.Idle
    }

    companion object {
        fun isNewerDataVersion(remote: String, local: String): Boolean {
            val cleanRemote = remote.filter { it.isDigit() }
            val cleanLocal = local.filter { it.isDigit() }
            if (cleanRemote.isBlank() || cleanLocal.isBlank()) return false
            return cleanRemote.toLongOrNull() ?: 0L > cleanLocal.toLongOrNull() ?: 0L
        }

        fun interpolatePos(assembled: Float, exploded: Float, progress: Float): Float {
            val clamped = progress.coerceIn(0f, 1f)
            return assembled + (exploded - assembled) * clamped
        }
    }
}
