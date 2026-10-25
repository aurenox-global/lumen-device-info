package com.lumen.deviceinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.ui.components.AmbientBackground
import com.lumen.deviceinfo.ui.navigation.BottomItem
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
import com.lumen.deviceinfo.ui.theme.LocalLumenColors
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
            currentRoute = if (currentRoute == Screen.MORE.route) Screen.SYSTEM.route else Screen.MORE.route
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AmbientBackground()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                when (current) {
                    Screen.HOME -> HomeScreen(vm) { currentRoute = it }
                    Screen.HARDWARE -> HardwareScreen(vm)
                    Screen.BATTERY -> BatteryScreen(vm)
                    Screen.SYSTEM -> SystemScreen(vm, onOpenMore = { currentRoute = Screen.MORE.route })
                    Screen.MORE -> MoreScreen { currentRoute = it }
                    Screen.NETWORK -> NetworkScreen(vm)
                    Screen.SENSORS -> SensorsScreen(vm)
                    Screen.APPS -> AppsScreen(vm)
                    Screen.TESTS -> TestsScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
            FloatingPillBar(
                items = bottomItems,
                selectedRoute = currentRoute,
                onSelect = { currentRoute = it.screen.route },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

/**
 * Bottom bar flotante tipo píldora glass, separada del borde inferior.
 * Ítem activo con fondo tintado aqua y etiqueta clara.
 */
@Composable
private fun FloatingPillBar(
    items: List<BottomItem>,
    selectedRoute: String,
    onSelect: (BottomItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val lumen = LocalLumenColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(999.dp),
        color = lumen.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, lumen.glassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.screen.route == selectedRoute
                val active = MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) active.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickableNoRipple { onSelect(item) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.screen.label,
                        tint = if (selected) active else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.screen.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) active else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )
