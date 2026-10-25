package com.lumen.deviceinfo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val route: String, val label: String) {
    HOME("home", "Inicio"),
    HARDWARE("hardware", "Hardware"),
    BATTERY("battery", "Batería"),
    SYSTEM("system", "Sistema"),
    MORE("more", "Más"),
    CAMERAS("cameras", "Cámaras"),
    NETWORK("network", "Red"),
    SENSORS("sensors", "Sensores"),
    APPS("apps", "Apps"),
    TESTS("tests", "Tests"),
    SETTINGS("settings", "Ajustes");

    companion object {
        fun fromRoute(route: String): Screen = entries.firstOrNull { it.route == route } ?: HOME
    }
}

data class BottomItem(val screen: Screen, val icon: ImageVector)

val bottomItems = listOf(
    BottomItem(Screen.HOME, Icons.Filled.Home),
    BottomItem(Screen.HARDWARE, Icons.Filled.Memory),
    BottomItem(Screen.BATTERY, Icons.Filled.BatteryChargingFull),
    BottomItem(Screen.SYSTEM, Icons.Filled.Info)
)

data class MoreItem(val screen: Screen, val icon: ImageVector, val subtitle: String)

val moreItems = listOf(
    MoreItem(Screen.CAMERAS, Icons.Filled.CameraAlt, "Detalle por lente"),
    MoreItem(Screen.NETWORK, Icons.Filled.Wifi, "Conexión y operador"),
    MoreItem(Screen.SENSORS, Icons.Filled.Sensors, "Valores en vivo"),
    MoreItem(Screen.APPS, Icons.Filled.Apps, "Lista de apps"),
    MoreItem(Screen.TESTS, Icons.Filled.Speed, "Benchmarks"),
    MoreItem(Screen.SETTINGS, Icons.Filled.Settings, "Tema y exportar")
)
