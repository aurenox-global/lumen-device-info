package com.lumen.deviceinfo.data

import android.graphics.Bitmap

const val NA = "No disponible"

data class CoreFreq(val index: Int, val minKhz: Long, val maxKhz: Long, val currentKhz: Long) {
    val minMhz: String get() = formatMhz(minKhz)
    val maxMhz: String get() = formatMhz(maxKhz)
    val currentMhz: String get() = formatMhz(currentKhz)
}

fun formatMhz(khz: Long): String {
    if (khz <= 0) return NA
    return "%.0f MHz".format(khz / 1000.0)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return NA
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1.0 -> "%.2f TB".format(tb)
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

data class CpuInfo(
    val cores: Int = 0,
    val abi: String = NA,
    val governor: String = NA,
    val coresInfo: List<CoreFreq> = emptyList()
)

data class GpuInfo(
    val vendor: String = NA,
    val renderer: String = NA,
    val version: String = NA,
    val extensions: String = NA
)

data class DisplayInfo(
    val resolution: String = NA,
    val density: String = NA,
    val refreshRate: String = NA,
    val hdr: String = NA,
    val sizeInches: String = NA
)

data class MemInfo(
    val total: String = NA,
    val available: String = NA,
    val used: String = NA,
    val usedPercent: Float = 0f
)

data class VolumeInfo(
    val path: String,
    val total: String,
    val free: String,
    val usedPercent: Float
)

data class StorageInfo(
    val total: String = NA,
    val free: String = NA,
    val used: String = NA,
    val usedPercent: Float = 0f,
    val volumes: List<VolumeInfo> = emptyList()
)

data class CameraInfo(
    val count: Int = 0,
    val details: List<String> = emptyList()
)

data class BluetoothInfo(
    val supported: Boolean = false,
    val name: String = NA,
    val address: String = NA,
    val state: String = NA
)

data class BatteryInfo(
    val level: Int = -1,
    val status: String = NA,
    val health: String = NA,
    val plugged: String = NA,
    val temperature: String = NA,
    val voltage: String = NA,
    val current: String = NA,
    val power: String = NA,
    val technology: String = NA,
    val capacity: String = NA,
    val chargeCounter: String = NA
)

data class SystemInfoModel(
    val androidVersion: String = NA,
    val sdk: String = NA,
    val securityPatch: String = NA,
    val kernel: String = NA,
    val buildId: String = NA,
    val buildDisplay: String = NA,
    val fingerprint: String = NA,
    val uptime: String = NA,
    val bootloader: String = NA,
    val board: String = NA,
    val hardware: String = NA,
    val abis: String = NA,
    val locale: String = NA,
    val timezone: String = NA,
    val manufacturer: String = NA,
    val brand: String = NA,
    val model: String = NA,
    val device: String = NA,
    val soc: String = NA,
    val oem: String = NA
)

data class DeviceSummary(
    val model: String = NA,
    val soc: String = NA,
    val os: String = NA
)

data class NetworkInfo(
    val transport: String = NA,
    val operator: String = NA,
    val networkType: String = NA,
    val roaming: String = NA,
    val simOperator: String = NA,
    val simState: String = NA,
    val ipv4: String = NA,
    val ipv6: String = NA,
    val dns: String = NA,
    val mtu: String = NA,
    val interfaceName: String = NA,
    val linkSpeed: String = NA,
    val signal: String = NA,
    val wifiSsid: String = NA,
    val carrierName: String = NA
)

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val sizeBytes: Long,
    val isSystem: Boolean,
    val icon: Bitmap? = null
)

data class SensorValue(
    val name: String,
    val vendor: String,
    val typeName: String,
    val range: String,
    val resolution: String,
    val power: String,
    val liveValue: String = NA
)

data class BenchmarkResult(
    val name: String,
    val score: Double,
    val unit: String,
    val detail: String
)
