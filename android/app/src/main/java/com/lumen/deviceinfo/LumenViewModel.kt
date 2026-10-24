package com.lumen.deviceinfo

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.deviceinfo.data.AppInfo
import com.lumen.deviceinfo.data.BatteryInfo
import com.lumen.deviceinfo.data.BatterySample
import com.lumen.deviceinfo.data.Benchmark
import com.lumen.deviceinfo.data.BluetoothInfo
import com.lumen.deviceinfo.data.CameraInfo
import com.lumen.deviceinfo.data.CpuInfo
import com.lumen.deviceinfo.data.DeviceReader
import com.lumen.deviceinfo.data.DeviceSummary
import com.lumen.deviceinfo.data.DisplayInfo
import com.lumen.deviceinfo.data.GpuInfo
import com.lumen.deviceinfo.data.MemInfo
import com.lumen.deviceinfo.data.NetworkInfo
import com.lumen.deviceinfo.data.SensorReader
import com.lumen.deviceinfo.data.SimInfo
import com.lumen.deviceinfo.data.StorageInfo
import com.lumen.deviceinfo.data.SystemInfoModel
import com.lumen.deviceinfo.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val summary: DeviceSummary = DeviceSummary(),
    val cpu: CpuInfo = CpuInfo(),
    val cpuUsage: Float = 0f,
    val gpu: GpuInfo = GpuInfo(),
    val display: DisplayInfo = DisplayInfo(),
    val memory: MemInfo = MemInfo(),
    val storage: StorageInfo = StorageInfo(),
    val battery: BatteryInfo = BatteryInfo(),
    val system: SystemInfoModel = SystemInfoModel(),
    val network: NetworkInfo = NetworkInfo(),
    val cameras: CameraInfo = CameraInfo(),
    val bluetooth: BluetoothInfo = BluetoothInfo(),
    val apps: List<AppInfo> = emptyList(),
    val appsLoaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val batteryHistory: List<BatterySample> = emptyList(),
    val sim: SimInfo = SimInfo()
)

class LumenViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = DeviceReader(app)
    private val sensorReader = SensorReader(app)
    val benchmark = Benchmark(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val sensors = sensorReader.sensors
    val liveSensorValues = sensorReader.liveValues

    private var tickJob: Job? = null
    private val prefs = app.getSharedPreferences("lumen_prefs", android.content.Context.MODE_PRIVATE)

    init {
        _uiState.update {
            it.copy(themeMode = loadThemeMode(), batteryHistory = loadBatteryHistory())
        }
        loadStatic()
        sensorReader.loadSensorList()
    }

    private fun loadThemeMode(): ThemeMode {
        return try {
            when (prefs.getString("theme", "dark")) {
                "light" -> ThemeMode.LIGHT
                "system" -> ThemeMode.SYSTEM
                else -> ThemeMode.DARK
            }
        } catch (e: Exception) { ThemeMode.DARK }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        val value = when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.SYSTEM -> "system"
            ThemeMode.DARK -> "dark"
        }
        try { prefs.edit().putString("theme", value).apply() } catch (e: Exception) { }
    }

    private fun loadStatic() {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = reader.readDeviceSummary()
            val cpu = reader.readCpu()
            val gpu = reader.readGpu()
            val display = reader.readDisplay()
            val system = reader.readSystem()
            val cameras = reader.readCameras()
            val bluetooth = reader.readBluetooth()
            _uiState.update {
                it.copy(
                    summary = summary,
                    cpu = cpu,
                    gpu = gpu,
                    display = display,
                    system = system,
                    cameras = cameras,
                    bluetooth = bluetooth
                )
            }
        }
    }

    /** Actualización en vivo cada 2 s; solo debe llamarse con la app visible. */
    fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (isActive) {
                refreshDynamic()
                delay(2000)
            }
        }
    }

    fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private suspend fun refreshDynamic() = withContext(Dispatchers.IO) {
        val cpuUsage = reader.readCpuUsage()
        val cpu = reader.readCpu()
        val memory = reader.readMemory()
        val storage = reader.readStorage()
        val battery = reader.readBattery()
        val network = reader.readNetwork()
        val sim = reader.readSimInfo(hasPhonePermission())
        val now = System.currentTimeMillis()
        _uiState.update { st ->
            val cpuHist = (st.cpuHistory + cpuUsage).takeLast(40)
            val ramHist = (st.ramHistory + memory.usedPercent).takeLast(40)
            val batteryHist = if (battery.level >= 0 && !battery.temperatureC.isNaN()) {
                (st.batteryHistory + BatterySample(now, battery.level, battery.temperatureC))
                    .takeLast(MAX_BATTERY_SAMPLES)
            } else st.batteryHistory
            st.copy(
                cpuUsage = cpuUsage,
                cpu = cpu,
                memory = memory,
                storage = storage,
                battery = battery,
                network = network,
                sim = sim,
                cpuHistory = cpuHist,
                ramHistory = ramHist,
                batteryHistory = batteryHist
            )
        }
        saveBatteryHistory(_uiState.value.batteryHistory)
    }

    fun hasPhonePermission(): Boolean = try {
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) { false }

    private fun loadBatteryHistory(): List<BatterySample> = try {
        prefs.getString(KEY_BATTERY_HISTORY, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size != 3) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val level = parts[1].toIntOrNull() ?: return@mapNotNull null
                val temp = parts[2].toFloatOrNull() ?: return@mapNotNull null
                if (level !in 0..100 || temp !in -60f..120f) return@mapNotNull null
                BatterySample(ts, level, temp)
            }
            ?.toList()
            ?.takeLast(MAX_BATTERY_SAMPLES)
            ?: emptyList()
    } catch (t: Throwable) { emptyList() }

    private fun saveBatteryHistory(samples: List<BatterySample>) {
        try {
            val csv = samples.joinToString("\n") { "${it.ts},${it.level},${it.tempC}" }
            prefs.edit().putString(KEY_BATTERY_HISTORY, csv).apply()
        } catch (t: Throwable) { }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshDynamic() }
    }

    fun loadApps() {
        if (_uiState.value.appsLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            val apps = reader.readApps()
            _uiState.update { it.copy(apps = apps, appsLoaded = true) }
        }
    }

    fun loadAppIcon(pkg: String, onIcon: (String, android.graphics.Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val icon = reader.loadAppIcon(pkg)
            withContext(Dispatchers.Main) { onIcon(pkg, icon) }
        }
    }

    suspend fun loadAppIconSuspend(pkg: String): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) { reader.loadAppIcon(pkg) }

    fun startSensors() = sensorReader.startListening()
    fun stopSensors() = sensorReader.stopListening()

    fun buildReport(): String {
        val st = _uiState.value
        val b = StringBuilder()
        b.appendLine("LUMEN — Informe del dispositivo")
        b.appendLine("==============================")
        b.appendLine()
        b.appendLine("Dispositivo: ${st.summary.model}")
        b.appendLine("SoC: ${st.summary.soc}")
        b.appendLine("Sistema: ${st.summary.os}")
        b.appendLine()
        b.appendLine("[Batería]")
        b.appendLine("Nivel: ${st.battery.level}%")
        b.appendLine("Estado: ${st.battery.status}")
        b.appendLine("Salud del sistema: ${st.battery.health}")
        b.appendLine("Salud estimada (SoH): ${st.battery.healthInfo.sohPercent} (${st.battery.healthInfo.source})")
        b.appendLine("Capacidad de diseño: ${st.battery.healthInfo.designMah}")
        b.appendLine("Capacidad estimada: ${st.battery.healthInfo.estimatedMah}")
        b.appendLine("Ciclos: ${st.battery.healthInfo.cycles}")
        b.appendLine("Temperatura: ${st.battery.temperature}")
        b.appendLine("Voltaje: ${st.battery.voltage}")
        b.appendLine("Tecnología: ${st.battery.technology}")
        b.appendLine()
        b.appendLine("[SIM y eSIM]")
        b.appendLine("eSIM compatible: ${st.sim.esimSupported}")
        b.appendLine("eUICC habilitado: ${st.sim.euiccEnabled}")
        b.appendLine("Ranuras SIM: ${st.sim.slots}")
        b.appendLine("SIMs activas: ${st.sim.activeCount}")
        b.appendLine("Permiso: ${st.sim.permission}")
        b.appendLine()
        b.appendLine("[Memoria]")
        b.appendLine("Total: ${st.memory.total}")
        b.appendLine("Disponible: ${st.memory.available}")
        b.appendLine()
        b.appendLine("[Almacenamiento]")
        b.appendLine("Total: ${st.storage.total}")
        b.appendLine("Libre: ${st.storage.free}")
        st.storage.volumes.forEach { v ->
            b.appendLine("  ${v.path}: ${v.total} total, ${v.free} libre")
        }
        b.appendLine()
        b.appendLine("[Sistema]")
        b.appendLine("Android: ${st.system.androidVersion}")
        b.appendLine("SDK: ${st.system.sdk}")
        b.appendLine("Parche: ${st.system.securityPatch}")
        b.appendLine("Kernel: ${st.system.kernel}")
        b.appendLine("Build: ${st.system.buildId}")
        b.appendLine("Fingerprint: ${st.system.fingerprint}")
        b.appendLine("ABIs: ${st.system.abis}")
        b.appendLine("Locale: ${st.system.locale}")
        b.appendLine()
        b.appendLine("[Red]")
        b.appendLine("Transporte: ${st.network.transport}")
        b.appendLine("Operador: ${st.network.operator}")
        b.appendLine("IPv4: ${st.network.ipv4}")
        b.appendLine("IPv6: ${st.network.ipv6}")
        b.appendLine("DNS: ${st.network.dns}")
        return b.toString()
    }

    private companion object {
        const val MAX_BATTERY_SAMPLES = 60
        const val KEY_BATTERY_HISTORY = "battery_history"
    }
}
