package com.lumen.deviceinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.ui.navigation.Screen
import com.lumen.deviceinfo.ui.navigation.bottomItems
import com.lumen.deviceinfo.ui.screens.AppsScreen
import com.lumen.deviceinfo.ui.screens.BatteryScreen
import com.lumen.deviceinfo.ui.screens.HardwareScreen
import com.lumen.deviceinfo.ui.screens.HomeScreen
import com.lumen.deviceinfo.ui.screens.MoreScreen
import com.lumen.deviceinfo.ui.screens.NetworkScreen
import com.lumen.deviceinfo.ui.screens.SensorsScreen
import com.lumen.deviceinfo.ui.screens.SettingsScreen
import com.lumen.deviceinfo.ui.screens.SystemScreen
import com.lumen.deviceinfo.ui.screens.TestsScreen
import com.lumen.deviceinfo.ui.theme.LumenTheme
import com.lumen.deviceinfo.ui.theme.ThemeMode

@Composable
fun LumenApp(vm: LumenViewModel) {
    var currentRoute by rememberSaveable { mutableStateOf(Screen.HOME.route) }
    val current = Screen.fromRoute(currentRoute)
    val isBottom = bottomItems.any { it.screen.route == currentRoute }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val themeMode = uiState.themeMode
    LumenTheme(themeMode = themeMode) {
        BackHandler(enabled = !isBottom) {
            currentRoute = Screen.MORE.route
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomItems.forEach { item ->
                        val selected = item.screen.route == currentRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentRoute = item.screen.route },
                            icon = { Icon(item.icon, contentDescription = item.screen.label) },
                            label = { Text(item.screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (current) {
                    Screen.HOME -> HomeScreen(vm) { currentRoute = it }
                    Screen.HARDWARE -> HardwareScreen(vm)
                    Screen.BATTERY -> BatteryScreen(vm)
                    Screen.SYSTEM -> SystemScreen(vm)
                    Screen.MORE -> MoreScreen { currentRoute = it }
                    Screen.NETWORK -> NetworkScreen(vm)
                    Screen.SENSORS -> SensorsScreen(vm)
                    Screen.APPS -> AppsScreen(vm)
                    Screen.TESTS -> TestsScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}
