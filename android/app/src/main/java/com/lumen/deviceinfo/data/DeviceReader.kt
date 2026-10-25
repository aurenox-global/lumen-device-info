package com.lumen.deviceinfo.data

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.Locale
import java.util.TimeZone
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext

class DeviceReader(private val context: Context) {

    fun readDeviceSummary(): DeviceSummary {
        val resolved = resolveDeviceName()
        val soc = readSoc()
        val os = safe { "Android ${Build.VERSION.RELEASE}" } ?: NA
        return DeviceSummary(
            name = resolved.name,
            modelCode = Build.MODEL.ifBlank { NA },
            manufacturer = resolved.manufacturer,
            nameSource = resolved.source,
            soc = soc,
            os = os
        )
    }

    data class ResolvedName(
        val name: String,
        val manufacturer: String,
        val source: String
    )

    /**
     * Resolvedor del nombre comercial real del dispositivo, por prioridad:
     *  a) Lista oficial de Google (`devices.tsv.gz`): Build.MODEL exacto, luego
     *     Build.DEVICE y por último quitando sufijos habituales (-AN00, 5G, …).
     *  b) Si hay match: «Marca Nombre» evitando duplicar la marca.
     *  c) Si no, `Settings.Global device_name` SOLO si no es igual al código.
     *  d) Luego ro.product.marketname y variantes vía getprop.
     *  e) Fallback: Build.MANUFACTURER + Build.MODEL limpios.
     * Nunca inventa: si solo hay código, devuelve el código limpio.
     */
    fun resolveDeviceName(): ResolvedName {
        // a+b) Lista oficial de dispositivos de Google.
        val entry = DeviceNames.resolve(
            context,
            Build.MODEL?.trim().orEmpty(),
            Build.DEVICE?.trim().orEmpty()
        )
        if (entry != null) {
            return ResolvedName(
                name = entry.display,
                manufacturer = entry.brand.ifBlank { cleanManufacturer() },
                source = "devices.tsv"
            )
        }

        // c) Nombre de usuario (device_name) solo si no es el código de modelo.
        val user = safe { Settings.Global.getString(context.contentResolver, "device_name") }
        if (!user.isNullOrBlank()) {
            val u = user.trim()
            val equalsModel = Build.MODEL?.trim()?.equals(u, ignoreCase = true) == true ||
                Build.DEVICE?.trim()?.equals(u, ignoreCase = true) == true
            if (!equalsModel) {
                return ResolvedName(u, cleanManufacturer(), "device_name")
            }
        }

        // d) Marketing names vía getprop / SystemProperties.
        for (prop in MARKETING_PROPS) {
            val v = readSystemProperty(prop)
            if (!v.isNullOrBlank()) {
                return ResolvedName(v.trim(), cleanManufacturer(), "getprop:$prop")
            }
        }

        // e) Fallback: MANUFACTURER + MODEL limpio.
        val man = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val cleanModel = model.ifBlank { NA }
        val name = joinNoDuplicate(man, model)
        return ResolvedName(
            name = name.ifBlank { cleanModel },
            manufacturer = cleanManufacturer(),
            source = "Build"
        )
    }

    private fun cleanManufacturer(): String {
        val raw = Build.MANUFACTURER?.trim().orEmpty()
        return if (raw.isBlank()) NA else capitalizeWords(raw)
    }

    private fun joinNoDuplicate(manufacturer: String, model: String): String {
        val m = manufacturer.trim()
        val mod = model.trim()
        if (mod.isBlank()) return m.ifBlank { NA }
        if (m.isBlank()) return mod
        // Evita "Google Google Pixel 9" y duplicados tipo "Samsung Samsung".
        val mNorm = m.lowercase(Locale.ROOT)
        val modNorm = mod.lowercase(Locale.ROOT)
        return when {
            modNorm.startsWith(mNorm) -> capitalizeWords(mod)
            mNorm.startsWith(modNorm) -> capitalizeWords(m)
            else -> "${capitalizeWords(m)} ${capitalizeWords(mod)}".trim()
        }
    }

    private fun capitalizeWords(value: String): String =
        value.split(Regex("[\\s_]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { w ->
                if (w.all { it.isUpperCase() || it.isDigit() }) w
                else w.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

    /**
     * Lee una propiedad del sistema vía reflexión a `android.os.SystemProperties`
     * y, como alternativa, vía `getprop`. Con timeout y try/catch (nunca bloquea).
     */
    private fun readSystemProperty(prop: String): String? {
        // Intento 1: reflexión (rápido, sin proceso externo).
        try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java)
            val value = get.invoke(null, prop) as? String
            if (!value.isNullOrBlank()) return value
        } catch (t: Throwable) {
            // cae al fallback
        }
        // Intento 2: getprop (solo si la reflexión no devolvió nada).
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", prop))
            val text = p.inputStream.bufferedReader().use { it.readText() }.trim()
            // Timeout suave: si no termina pronto, lo matamos y devolvemos null.
            if (!p.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                try { p.destroy() } catch (t: Throwable) { }
                null
            } else text.ifBlank { null }
        } catch (t: Throwable) { null }
    }

    private fun readSoc(): String {
        val man = if (Build.VERSION.SDK_INT >= 31) safe { Build.SOC_MANUFACTURER?.takeIf { it.isNotBlank() } } else null
        val mod = if (Build.VERSION.SDK_INT >= 31) safe { Build.SOC_MODEL?.takeIf { it.isNotBlank() } } else null
        return when {
            man != null && mod != null -> "$man $mod"
            mod != null -> mod
            Build.HARDWARE.isNotBlank() -> Build.HARDWARE
            else -> NA
        }
    }

    fun readCpu(): CpuInfo {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            val abi = Build.SUPPORTED_ABIS.joinToString(", ")
            val governor = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: NA
            val list = (0 until cores).mapNotNull { i ->
                val min = readFileLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                val max = readFileLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val cur = readFileLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                if (min <= 0 && max <= 0) null
                else CoreFreq(i, min, max, cur)
            }
            CpuInfo(cores, abi.ifBlank { NA }, governor, list)
        } catch (e: Exception) {
            CpuInfo()
        }
    }

    fun readCpuUsage(): Float {
        return try {
            val stat = readFile("/proc/stat") ?: return 0f
            val line = stat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 0f
            val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return 0f
            val idle = values[3] + (values.getOrNull(4) ?: 0)
            val total = values.sum()
            val lastIdle = lastCpuIdle
            val lastTotal = lastCpuTotal
            lastCpuIdle = idle
            lastCpuTotal = total
            if (lastTotal <= 0 || total <= lastTotal) return 0f
            val diffTotal = total - lastTotal
            val diffIdle = idle - lastIdle
            if (diffTotal <= 0) return 0f
            ((diffTotal - diffIdle) * 100f / diffTotal).coerceIn(0f, 100f)
        } catch (e: Exception) {
            0f
        }
    }

    fun readGpu(): GpuInfo {
        return try {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (display == EGL10.EGL_NO_DISPLAY) return GpuInfo()
            egl.eglInitialize(display, IntArray(2))
            val vendor = egl.eglQueryString(display, EGL10.EGL_VENDOR) ?: NA
            val version = egl.eglQueryString(display, EGL10.EGL_VERSION) ?: NA
            val ext = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS) ?: NA
            val renderer = safe { readFile("/sys/class/kgsl/kgsl-3d0/gpu_model") }
                ?: safe { readFile("/sys/class/misc/mali0/device/gpuinfo") }
                ?: NA
            GpuInfo(
                vendor = vendor.ifBlank { NA },
                renderer = renderer,
                version = version.ifBlank { NA },
                extensions = ext.ifBlank { NA }
            )
        } catch (e: Exception) {
            GpuInfo()
        }
    }

    fun readDisplay(): DisplayInfo {
        return try {
            val metrics = context.resources.displayMetrics
            val resolution = "${metrics.widthPixels} × ${metrics.heightPixels}"
            val density = "${metrics.densityDpi} dpi (${"%.2f".format(metrics.density)}x)"
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val refresh = try {
                if (Build.VERSION.SDK_INT >= 30) {
                    "%.1f Hz".format(display?.refreshRate ?: 0f)
                } else NA
            } catch (e: Exception) { NA }
            val hdr = try {
                if (Build.VERSION.SDK_INT >= 26 && display?.isHdr == true) "Compatible" else "No"
            } catch (e: Exception) { NA }
            val inches = computeScreenInches(metrics)
            DisplayInfo(resolution, density, refresh, hdr, inches)
        } catch (e: Exception) {
            DisplayInfo()
        }
    }

    private fun computeScreenInches(metrics: DisplayMetrics): String {
        return try {
            val x = metrics.widthPixels / metrics.xdpi
            val y = metrics.heightPixels / metrics.ydpi
            val inches = Math.sqrt((x * x + y * y).toDouble())
            "%.1f\"".format(inches)
        } catch (e: Exception) { NA }
    }

    fun readMemory(): MemInfo {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val total = mi.totalMem
            val avail = mi.availMem
            val used = total - avail
            val pct = if (total > 0) used * 100f / total else 0f
            MemInfo(formatBytes(total), formatBytes(avail), formatBytes(used), pct)
        } catch (e: Exception) { MemInfo() }
    }

    fun readStorage(): StorageInfo {
        return try {
            val dirs = mutableListOf(Environment.getDataDirectory())
            try {
                val ext = Environment.getExternalStorageDirectory()
                if (ext.absolutePath != dirs[0].absolutePath) dirs += ext
            } catch (e: Exception) { }
            var totalBytes = 0L
            var freeBytes = 0L
            val volumes = dirs.map { dir ->
                val s = StatFs(dir.absolutePath)
                val t = s.totalBytes
                val f = s.availableBytes
                totalBytes += t
                freeBytes += f
                VolumeInfo(
                    path = dir.absolutePath,
                    total = formatBytes(t),
                    free = formatBytes(f),
                    usedPercent = if (t > 0) (t - f) * 100f / t else 0f
                )
            }
            val used = totalBytes - freeBytes
            val pct = if (totalBytes > 0) used * 100f / totalBytes else 0f
            StorageInfo(
                total = formatBytes(totalBytes),
                free = formatBytes(freeBytes),
                used = formatBytes(used),
                usedPercent = pct,
                volumes = volumes
            )
        } catch (e: Exception) { StorageInfo() }
    }

    fun readCameras(): CameraInfo {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList
            val details = ids.mapNotNull { id ->
                try {
                    val chars = cm.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val face = when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                        CameraCharacteristics.LENS_FACING_BACK -> "Trasera"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa"
                        else -> "Desconocida"
                    }
                    val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = streamMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    val maxRes = sizes?.maxByOrNull { it.width * it.height }
                        ?.let { "${it.width}×${it.height}" } ?: NA
                    val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focalStr = focal?.firstOrNull()?.let { "%.1f mm".format(it) } ?: NA
                    "$face · $maxRes · focal $focalStr"
                } catch (e: Exception) { null }
            }
            CameraInfo(ids.size, details)
        } catch (e: Exception) { CameraInfo() }
    }

    /**
     * Detalle completo de cada cámara lógica con Camera2 (try/catch por API y
     * por dispositivo). Cada valor no expuesto se muestra como «No disponible».
     */
    fun readCameraDetails(): List<CameraDetail> {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = cm.cameraIdList.toList()
            val out = mutableListOf<CameraDetail>()
            var back = 0
            var front = 0
            var ext = 0
            for (id in ids) {
                try {
                    val chars = cm.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val facingStr = facingName(facing)
                    val title = when (facing) {
                        CameraCharacteristics.LENS_FACING_BACK -> {
                            back++
                            if (back == 1) "Trasera principal" else "Trasera $back"
                        }
                        CameraCharacteristics.LENS_FACING_FRONT -> {
                            front++
                            if (front == 1) "Frontal" else "Frontal $front"
                        }
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> {
                            ext++
                            if (ext == 1) "Externa" else "Externa $ext"
                        }
                        else -> {
                            ext++
                            "Cámara $ext"
                        }
                    }
                    out += buildCameraDetail(id, title, facingStr, chars, cm)
                } catch (e: Exception) {
                    // cámara no legible: se omite
                }
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    private fun buildCameraDetail(
        id: String,
        title: String,
        facing: String,
        chars: CameraCharacteristics,
        cm: CameraManager
    ): CameraDetail {
        val isLogical = isLogicalCamera(chars)
        val physicalIds = if (Build.VERSION.SDK_INT >= 28) {
            try { chars.physicalCameraIds?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        } else emptyList()
        val physicalLenses = if (isLogical && physicalIds.isNotEmpty()) {
            physicalIds.mapNotNull { pid ->
                try {
                    val pc = cm.getCameraCharacteristics(pid)
                    PhysicalLens(id = pid, lens = lensBasics(pc))
                } catch (e: Exception) { null }
            }
        } else emptyList()

        return CameraDetail(
            id = id,
            title = title,
            facing = facing,
            isLogical = isLogical,
            physicalIds = physicalIds,
            lens = lensBasics(chars),
            ois = opticalStabilization(chars),
            flash = flashAvailable(chars),
            afModes = afModes(chars),
            isoRange = isoRange(chars),
            raw = rawSupport(chars),
            hardwareLevel = hardwareLevel(chars),
            maxZoom = maxZoom(chars),
            videoMax = videoMax(chars),
            physicalLenses = physicalLenses
        )
    }

    private fun facingName(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
        CameraCharacteristics.LENS_FACING_BACK -> "Trasera"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "Externa"
        else -> "Desconocida"
    }

    private fun isLogicalCamera(chars: CameraCharacteristics): Boolean = try {
        chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA } == true
    } catch (e: Exception) { false }

    private fun lensBasics(chars: CameraCharacteristics): LensData {
        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = try { streamMap?.getOutputSizes(android.graphics.ImageFormat.JPEG) } catch (e: Exception) { null }
        val maxJpeg = jpegSizes?.maxByOrNull { it.width * it.height }
        val maxJpegStr = maxJpeg?.let { "${it.width} × ${it.height}" } ?: NA
        val mpStr = maxJpeg?.let { "%.1f MP".format(it.width * it.height / 1_000_000.0) } ?: NA

        val active = try { chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) } catch (e: Exception) { null }
        val activeStr = active?.let { "${it.width()} × ${it.height()}" } ?: NA
        val physical = try { chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) } catch (e: Exception) { null }
        val sensorSizeStr = physical?.let { "%.2f × %.2f mm".format(it.width, it.height) } ?: NA

        val apertures = try { chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) } catch (e: Exception) { null }
        val aperturesStr = apertures
            ?.map { "f/%.2f".format(it.toFloat()) }
            ?.distinct()
            ?.joinToString(" · ") ?: NA

        val focals = try { chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) } catch (e: Exception) { null }
        val focalStr = focals
            ?.map { "%.1f mm".format(it.toFloat()) }
            ?.distinct()
            ?.joinToString(" · ") ?: NA

        val focal35Str = if (focals != null && physical != null && physical.width > 0f) {
            val diag = Math.sqrt((physical.width * physical.width + physical.height * physical.height).toDouble())
            if (diag > 0) {
                focals.map { f -> 43.2666 / diag * f.toDouble() }
                    .map { "%.0f mm".format(it) }
                    .distinct()
                    .joinToString(" · ")
            } else NA
        } else NA

        val pixelSizeStr = if (physical != null && active != null && physical.width > 0f && active.width() > 0) {
            "%.2f µm".format(physical.width / active.width() * 1000.0)
        } else NA

        return LensData(
            maxJpeg = maxJpegStr,
            megapixels = mpStr,
            sensorArray = activeStr,
            sensorSize = sensorSizeStr,
            apertures = aperturesStr,
            focal = focalStr,
            focal35mm = focal35Str,
            pixelSize = pixelSizeStr
        )
    }

    private fun opticalStabilization(chars: CameraCharacteristics): String = try {
        val modes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: return NA
        val hasOn = modes.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON }
        val hasOff = modes.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF }
        when {
            modes.isEmpty() -> NA
            hasOn && hasOff -> "Sí (conmutable)"
            hasOn -> "Sí"
            else -> "No"
        }
    } catch (e: Exception) { NA }

    private fun flashAvailable(chars: CameraCharacteristics): String = try {
        when (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
            true -> "Sí"
            false -> "No"
            null -> NA
        }
    } catch (e: Exception) { NA }

    private fun afModes(chars: CameraCharacteristics): String = try {
        val modes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: return NA
        val names = mutableListOf<String>()
        for (mode in modes) {
            val n = when (mode) {
                CameraCharacteristics.CONTROL_AF_MODE_OFF -> "Off"
                CameraCharacteristics.CONTROL_AF_MODE_AUTO -> "Auto"
                CameraCharacteristics.CONTROL_AF_MODE_MACRO -> "Macro"
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuo vídeo"
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuo foto"
                CameraCharacteristics.CONTROL_AF_MODE_EDOF -> "EDoF"
                else -> null
            }
            if (n != null) names.add(n)
        }
        names.distinct().joinToString(" · ").ifBlank { NA }
    } catch (e: Exception) { NA }

    private fun isoRange(chars: CameraCharacteristics): String = try {
        val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return NA
        "${range.lower} – ${range.upper}"
    } catch (e: Exception) { NA }

    private fun rawSupport(chars: CameraCharacteristics): String = try {
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return NA
        if (caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW }) "Sí" else "No"
    } catch (e: Exception) { NA }

    private fun hardwareLevel(chars: CameraCharacteristics): String = try {
        when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> NA
        }
    } catch (e: Exception) { NA }

    private fun maxZoom(chars: CameraCharacteristics): String = try {
        val z = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: return NA
        "%.1f×".format(z)
    } catch (e: Exception) { NA }

    private fun videoMax(chars: CameraCharacteristics): String = try {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return NA
        val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: return NA
        val max = sizes.maxByOrNull { it.width * it.height } ?: return NA
        val minDur = map.getOutputMinFrameDuration(MediaRecorder::class.java, max)
        val fps = if (minDur > 0) (1_000_000_000L / minDur).toInt() else 0
        if (fps in 1..960) "${max.width} × ${max.height} @ $fps fps" else "${max.width} × ${max.height}"
    } catch (e: Exception) { NA }

    @Suppress("MissingPermission")
    fun readBluetooth(): BluetoothInfo {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bm.adapter
            if (adapter == null) BluetoothInfo(supported = false)
            else {
                var name = NA
                var address = NA
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                        PackageManager.PERMISSION_GRANTED) {
                        name = adapter.name ?: NA
                        address = adapter.address ?: NA
                    }
                } catch (e: Exception) { }
                val state = when (adapter.state) {
                    BluetoothAdapter.STATE_ON -> "Activado"
                    BluetoothAdapter.STATE_OFF -> "Desactivado"
                    BluetoothAdapter.STATE_TURNING_ON -> "Activando…"
                    BluetoothAdapter.STATE_TURNING_OFF -> "Desactivando…"
                    else -> NA
                }
                BluetoothInfo(true, name, address, state)
            }
        } catch (e: Exception) { BluetoothInfo() }
    }

    fun readBattery(): BatteryInfo {
        return try {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
                BatteryManager.BATTERY_STATUS_FULL -> "Llena"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "No cargando"
                else -> NA
            }
            val health = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
                BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Muerta"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fallo"
                else -> NA
            }
            val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val pluggedStr = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrico"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                0 -> "No"
                else -> NA
            }
            val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val tempStr = if (temp > -1) "%.1f °C".format(temp / 10f) else NA
            val volt = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val voltStr = if (volt > 0) "%.3f V".format(volt / 1000f) else NA

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentUa = safeInt { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } ?: Int.MIN_VALUE
            val currentStr = if (currentUa != Int.MIN_VALUE) {
                val mA = currentUa / 1000f
                "%.0f mA".format(mA)
            } else NA
            val powerStr = if (currentUa != Int.MIN_VALUE && volt > 0) {
                val w = (currentUa / 1_000_000.0) * (volt / 1000.0)
                "%.2f W".format(w)
            } else NA
            val capacityUa = safeInt { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } ?: Int.MIN_VALUE
            val capacityStr = if (capacityUa != Int.MIN_VALUE && capacityUa > 0) "%.0f mAh".format(capacityUa / 1000f) else NA
            val technology = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: NA
            val tempC = if (temp > -1) temp / 10f else Float.NaN
            val healthInfo = readBatteryHealth(pct, bm, i, capacityUa)

            BatteryInfo(
                level = pct,
                status = statusStr,
                health = healthStr,
                plugged = pluggedStr,
                temperature = tempStr,
                voltage = voltStr,
                current = currentStr,
                power = powerStr,
                technology = technology,
                capacity = capacityStr,
                chargeCounter = capacityStr,
                temperatureC = tempC,
                healthInfo = healthInfo
            )
        } catch (e: Exception) { BatteryInfo() }
    }

    /**
     * Salud de la batería sin root: sysfs (charge_full / charge_full_design o energy_*)
     * y, como alternativa, el contador de carga de BatteryManager contra la capacidad
     * de diseño que exponga PowerProfile. Nunca inventa: si no hay datos, devuelve NA.
     */
    private fun readBatteryHealth(
        pct: Int,
        bm: BatteryManager,
        intent: Intent?,
        counterUa: Int
    ): BatteryHealth {
        var sysfsFullUah = 0L
        var sysfsDesignUah = 0L
        var sysfsCycles: Int? = null
        var sysfsUsed = false

        for (base in SYSFS_BATTERY_PATHS) {
            // Algunos fabricantes exponen charge_* (µAh) y otros energy_* (µWh).
            val chargeFull = readLongIfReadable(base + "charge_full")
            val chargeDesign = readLongIfReadable(base + "charge_full_design")
            val energyFull = readLongIfReadable(base + "energy_full")
            val energyDesign = readLongIfReadable(base + "energy_full_design")

            if (sysfsFullUah <= 0L || sysfsDesignUah <= 0L) {
                val full = when {
                    chargeFull != null && chargeDesign != null -> normalizeToMicro(chargeFull)
                    energyFull != null && energyDesign != null -> normalizeToMicro(energyFull)
                    else -> 0L
                }
                val design = when {
                    chargeFull != null && chargeDesign != null -> normalizeToMicro(chargeDesign)
                    energyFull != null && energyDesign != null -> normalizeToMicro(energyDesign)
                    else -> 0L
                }
                if (full in MIN_CAPACITY_UAH..MAX_CAPACITY_UAH &&
                    design in MIN_CAPACITY_UAH..MAX_CAPACITY_UAH
                ) {
                    sysfsFullUah = full
                    sysfsDesignUah = design
                    sysfsUsed = true
                }
            }

            if (sysfsCycles == null) {
                val c = readLongIfReadable(base + "cycle_count")
                if (c != null && c in 0..20_000) sysfsCycles = c.toInt()
            }
        }

        val cyclesFromIntent = if (Build.VERSION.SDK_INT >= 34) {
            try {
                if (intent?.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT) == true) {
                    intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
                } else null
            } catch (t: Throwable) { null }
        } else null
        val cycles = sysfsCycles ?: cyclesFromIntent

        val designFromProfile = readDesignCapacityMah()
        val counterEstimateUah =
            if (counterUa > 0 && pct in 1..100) counterUa.toDouble() * 100.0 / pct else null

        // Descartamos contadores absurdos (p. ej. el stub de 10 mAh del emulador):
        // una batería de teléfono no baja de ~300 mAh ni sube de 30 Ah.
        val counterPlausible = counterEstimateUah != null &&
            counterEstimateUah / 1000.0 in 300.0..30_000.0
        val estimatedUah = when {
            sysfsFullUah > 0L -> sysfsFullUah.toDouble()
            counterPlausible -> counterEstimateUah
            else -> null
        }
        val designUah = when {
            sysfsDesignUah > 0L -> sysfsDesignUah.toDouble()
            designFromProfile != null -> designFromProfile * 1000.0
            else -> null
        }

        var soh: Double? = null
        if (estimatedUah != null && designUah != null && designUah > 0) {
            val ratio = estimatedUah / designUah * 100.0
            // Descartamos ratios imposibles (lecturas corruptas o unidades raras).
            if (ratio in 20.0..150.0) soh = ratio
        }

        val source = when {
            sysfsUsed && soh != null -> "sysfs"
            estimatedUah != null && designUah != null && soh != null -> "BatteryManager"
            else -> "no disponible"
        }
        val note = when (source) {
            "sysfs" -> "Leído de /sys/class/power_supply (sin root)."
            "BatteryManager" -> "Estimación con el contador de carga y la capacidad de diseño; puede variar."
            else -> "El fabricante no expone la capacidad real; requiere Shizuku o root."
        }

        val remainingMs = try {
            if (Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining() else -1L
        } catch (t: Throwable) { -1L }
        val remaining = if (remainingMs > 0) formatDuration(remainingMs) else NA

        return BatteryHealth(
            sohPercent = soh?.let { "%.0f%%".format(it) } ?: NA,
            sohValue = soh?.toFloat() ?: -1f,
            designMah = designUah?.let { "%.0f mAh".format(it / 1000.0) } ?: NA,
            estimatedMah = estimatedUah?.let { "%.0f mAh".format(it / 1000.0) } ?: NA,
            cycles = cycles?.toString() ?: NA,
            source = source,
            note = note,
            remaining = remaining
        )
    }

    /** Capacidad de diseño vía android.os.PowerProfile (API oculta, puede fallar). */
    private fun readDesignCapacityMah(): Double? {
        return try {
            val cls = Class.forName("android.os.PowerProfile")
            val profile = cls.getConstructor(Context::class.java).newInstance(context)
            val value = cls.getMethod("getBatteryCapacity").invoke(profile)
            (value as? Number)?.toDouble()?.takeIf { it in 100.0..30_000.0 }
        } catch (t: Throwable) { null }
    }

    /** sysfs usa µAh/µWh; algunos drivers publican mAh/mWh. Normalizamos a micro. */
    private fun normalizeToMicro(value: Long): Long =
        if (value in 1..999_999L) value * 1000L else value

    private fun formatDuration(ms: Long): String {
        val totalMin = (ms / 60_000L).toInt()
        if (totalMin <= 0) return NA
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * Datos públicos de SIM/eSIM. getActiveSubscriptionInfoList y el operador
     * requieren READ_PHONE_STATE; sin permiso se devuelve NA. El EID y los
     * perfiles eSIM no son accesibles para una app normal.
     */
    fun readSimInfo(hasPhonePermission: Boolean): SimInfo {
        val pm = context.packageManager
        val esimSupported = try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)) "Sí" else "No"
            } else NA
        } catch (t: Throwable) { NA }

        val euiccEnabled = try {
            if (Build.VERSION.SDK_INT >= 28) {
                val mgr = context.getSystemService(EuiccManager::class.java)
                when {
                    mgr == null -> NA
                    mgr.isEnabled -> "Sí"
                    else -> "No"
                }
            } else NA
        } catch (t: Throwable) { NA }

        val tm = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (t: Throwable) { null }
        val slots = try {
            val count = when {
                tm == null -> -1
                Build.VERSION.SDK_INT >= 30 -> {
                    // getSimCount() no está en el SDK público de compilación; reflexión + fallback.
                    val viaReflection = try {
                        tm.javaClass.getMethod("getSimCount").invoke(tm) as? Int
                    } catch (t: Throwable) { null }
                    viaReflection ?: tm.phoneCount
                }
                else -> tm.phoneCount
            }
            if (count >= 0) count.toString() else NA
        } catch (t: Throwable) { NA }

        var activeCount = NA
        var subs: List<String> = emptyList()
        if (hasPhonePermission) {
            try {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                val list = sm?.activeSubscriptionInfoList ?: emptyList()
                activeCount = list.size.toString()
                subs = list.map { info ->
                    val carrier = try { info.carrierName?.toString()?.takeIf { it.isNotBlank() } } catch (t: Throwable) { null }
                    val slot = info.simSlotIndex
                    val slotLabel = if (slot >= 0) "ranura ${slot + 1}" else "ranura ?"
                    val type = if (Build.VERSION.SDK_INT >= 29 && info.isEmbedded) "eSIM" else "SIM física"
                    "${carrier ?: "Operador desconocido"} · $slotLabel · $type"
                }
            } catch (t: Throwable) {
                activeCount = NA
                subs = emptyList()
            }
        }

        return SimInfo(
            esimSupported = esimSupported,
            euiccEnabled = euiccEnabled,
            slots = slots,
            activeCount = activeCount,
            permission = if (hasPhonePermission) "Concedido" else "No concedido",
            subscriptions = subs
        )
    }

    fun readSystem(): SystemInfoModel {
        val uptime = SystemClock.elapsedRealtime()
        val days = uptime / 86_400_000
        val hours = (uptime % 86_400_000) / 3_600_000
        val mins = (uptime % 3_600_000) / 60_000
        val uptimeStr = if (days > 0) "${days}d ${hours}h ${mins}m" else if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val deviceName = resolveDeviceName().name
        return SystemInfoModel(
            androidVersion = Build.VERSION.RELEASE.ifBlank { NA },
            sdk = "API ${Build.VERSION.SDK_INT}",
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { !it.isNullOrBlank() } ?: NA,
            kernel = System.getProperty("os.version")?.ifBlank { NA } ?: NA,
            buildId = Build.ID.ifBlank { NA },
            buildDisplay = Build.DISPLAY.ifBlank { NA },
            fingerprint = Build.FINGERPRINT.ifBlank { NA },
            uptime = uptimeStr,
            bootloader = Build.BOOTLOADER.ifBlank { NA },
            board = Build.BOARD.ifBlank { NA },
            hardware = Build.HARDWARE.ifBlank { NA },
            abis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { NA },
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
            manufacturer = Build.MANUFACTURER.ifBlank { NA },
            brand = Build.BRAND.ifBlank { NA },
            model = Build.MODEL.ifBlank { NA },
            device = Build.DEVICE.ifBlank { NA },
            deviceName = deviceName,
            soc = readSoc(),
            oem = safe { Build.getRadioVersion()?.ifBlank { NA } } ?: NA
        )
    }

    fun readNetwork(): NetworkInfo {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val lp = net?.let { cm.getLinkProperties(it) }
            val transport = caps?.let { c ->
                val parts = mutableListOf<String>()
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) parts += "WiFi"
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) parts += "Celular"
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) parts += "Ethernet"
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) parts += "Bluetooth"
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) parts += "VPN"
                parts.joinToString(" · ").ifBlank { NA }
            } ?: NA

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkType = safe { networkTypeName(tm.dataNetworkType) } ?: NA
            val operator = safe { tm.networkOperatorName?.ifBlank { NA } } ?: NA
            val simOperator = safe { tm.simOperatorName?.ifBlank { NA } } ?: NA
            val roaming = safe { if (tm.isNetworkRoaming) "Sí" else "No" } ?: NA
            val simState = safe { simStateName(tm.simState) } ?: NA

            val dns = lp?.dnsServers?.joinToString(", ")?.ifBlank { NA } ?: NA
            val mtu = lp?.mtu?.let { "$it" } ?: NA
            val iface = lp?.interfaceName?.ifBlank { NA } ?: NA
            val addresses = lp?.linkAddresses?.map { it.address?.hostAddress }?.filterNotNull() ?: emptyList()
            val ipv4 = addresses.filter { it.contains(".") }.joinToString(", ").ifBlank { NA }
            val ipv6 = addresses.filter { it.contains(":") }.joinToString(", ").ifBlank { NA }

            val wifi = try { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager } catch (e: Exception) { null }
            val wifiInfo = try { wifi?.connectionInfo } catch (e: Exception) { null }
            val ssid = wifiInfo?.ssid?.replace("\"", "")?.ifBlank { NA } ?: NA
            val linkSpeed = wifiInfo?.let { if (it.linkSpeed > 0) "${it.linkSpeed} Mbps" else NA } ?: NA
            val signal = signalStr(tm, wifiInfo)

            NetworkInfo(transport, operator, networkType, roaming, simOperator, simState, ipv4, ipv6, dns, mtu, iface, linkSpeed, signal, ssid, operator)
        } catch (e: Exception) { NetworkInfo() }
    }

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO 0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO A"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        else -> "Desconocido ($type)"
    }

    private fun simStateName(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "Ausente"
        TelephonyManager.SIM_STATE_READY -> "Lista"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN requerido"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK requerido"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueada"
        TelephonyManager.SIM_STATE_NOT_READY -> "No lista"
        else -> NA
    }

    private fun signalStr(tm: TelephonyManager, wifi: android.net.wifi.WifiInfo?): String {
        return try {
            val ss = tm.signalStrength ?: return NA
            val dbm = if (Build.VERSION.SDK_INT >= 28) ss.level else null
            val wifiRssi = wifi?.rssi
            val parts = mutableListOf<String>()
            if (wifiRssi != null && wifiRssi != 127) parts += "WiFi ${wifiRssi} dBm"
            if (dbm != null && dbm in 0..4) parts += "Celular nivel $dbm/4"
            parts.joinToString(" · ").ifBlank { NA }
        } catch (e: Exception) { NA }
    }

    @Suppress("QueryPermissionsNeeded")
    fun readApps(): List<AppInfo> {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            packages.mapNotNull { info ->
                try {
                    val name = pm.getApplicationLabel(info).toString()
                    val pkgName = info.packageName
                    val pkgInfo = pm.getPackageInfo(pkgName, 0)
                    val versionName = pkgInfo.versionName ?: NA
                    val versionCode = if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong()
                    val targetSdk = pkgInfo.applicationInfo?.targetSdkVersion ?: 0
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val size = try {
                        File(info.sourceDir).length()
                    } catch (e: Exception) { 0L }
                    AppInfo(name, pkgName, versionName, versionCode, targetSdk, size, isSystem, null)
                } catch (e: Exception) { null }
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

    fun loadAppIcon(pkg: String): Bitmap? {
        return try {
            val pm = context.packageManager
            val icon: Drawable = pm.getApplicationIcon(pkg)
            icon.toBitmap(48, 48)
        } catch (e: Exception) { null }
    }

    companion object {
        @Volatile private var lastCpuIdle = 0L
        @Volatile private var lastCpuTotal = 0L

        private val SYSFS_BATTERY_PATHS = listOf(
            "/sys/class/power_supply/battery/",
            "/sys/class/power_supply/bms/"
        )
        private const val MIN_CAPACITY_UAH = 100_000L      // 100 mAh
        private const val MAX_CAPACITY_UAH = 30_000_000L   // 30 Ah

        /** Propiedades de marketing que exponen el nombre comercial real. */
        private val MARKETING_PROPS = listOf(
            "ro.product.marketname",
            "ro.product.odm.marketname",
            "ro.vendor.product.marketname",
            "ro.system.product.marketname",
            "ro.product.model"
        )
    }

    private fun safe(block: () -> String?): String? = try { block() } catch (e: Exception) { null }

    private fun safeInt(block: () -> Int): Int? = try { block() } catch (e: Exception) { null }

    private fun readLongIfReadable(path: String): Long? = try {
        val f = File(path)
        if (!f.exists() || !f.canRead()) null
        else f.readText().trim().toLongOrNull()
    } catch (t: Throwable) { null }

    private fun readFile(path: String): String? = try {
        val s = File(path).readText().trim()
        if (s.isBlank()) null else s
    } catch (e: Exception) { null }

    private fun readFileLong(path: String): Long = try {
        File(path).readText().trim().toLongOrNull() ?: 0L
    } catch (e: Exception) { 0L }
}
