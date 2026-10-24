package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.IconBadge
import com.lumen.deviceinfo.ui.components.Sparkline
import com.lumen.deviceinfo.ui.theme.LocalLumenColors
import com.lumen.deviceinfo.ui.theme.ThemeMode

@Composable
fun HomeScreen(vm: LumenViewModel, onOpen: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val lumen = LocalLumenColors.current
    val name = state.summary.name.ifBlank { state.summary.modelCode.ifBlank { "Dispositivo" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabecera: título (nombre real) + toggle de tema.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Lumen",
                    style = MaterialTheme.typography.labelSmall,
                    color = lumen.textTertiary
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val isDark = state.themeMode == ThemeMode.DARK
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(lumen.glassSurface, CircleShape)
                    .border(BorderStroke(1.dp, lumen.glassBorder), CircleShape)
                    .clickable {
                        vm.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Alternar tema",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }
        }

        // Hero: nombre real grande + modelo/marca/Android, gradiente aqua→azul.
        HeroCard(name, state.summary.modelCode, state.summary.manufacturer, state.summary.os)

        // 4 stat tiles: RAM, almacenamiento, batería, CPU.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                icon = Icons.Filled.Memory,
                label = "RAM",
                value = "${state.memory.usedPercent.toInt()}%",
                tint = lumen.secondary,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = Icons.Filled.SdStorage,
                label = "Alm.",
                value = "${state.storage.usedPercent.toInt()}%",
                tint = lumen.lavender,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                icon = Icons.Filled.BatteryChargingFull,
                label = "Batería",
                value = "${if (state.battery.level >= 0) state.battery.level else 0}%",
                tint = lumen.success,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = Icons.Filled.Speed,
                label = "CPU",
                value = "${state.cpuUsage.toInt()}%",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Actividad en vivo (CPU + RAM).
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVIDAD EN VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        color = lumen.textTertiary
                    )
                    Text(
                        text = "Uso actual del sistema",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${state.cpuUsage.toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Sparkline(state.cpuHistory, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Sparkline(state.ramHistory, color = lumen.secondary)
        }

        // Accesos rápidos.
        Text(
            text = "ACCESOS RÁPIDOS",
            style = MaterialTheme.typography.labelSmall,
            color = lumen.textTertiary
        )
        QuickCard(Icons.Filled.Memory, "Hardware", "CPU, GPU, pantalla y más", lumen.secondary) { onOpen("hardware") }
        QuickCard(Icons.Filled.BatteryChargingFull, "Batería", "Estado, salud e historial", lumen.success) { onOpen("battery") }
        QuickCard(Icons.Filled.Info, "Sistema", "Android, kernel y build", MaterialTheme.colorScheme.primary) { onOpen("system") }
        QuickCard(Icons.Filled.Wifi, "Red", "Conexión y operador", lumen.lavender) { onOpen("network") }
        QuickCard(Icons.Filled.Settings, "Ajustes", "Tema y exportar informe", lumen.textTertiary) { onOpen("settings") }
    }
}

@Composable
private fun HeroCard(name: String, modelCode: String, manufacturer: String, os: String) {
    val lumen = LocalLumenColors.current
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.dp, lumen.glassBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(lumen.heroStart, lumen.heroEnd)
                    )
                )
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    text = "DISPOSITIVO",
                    style = MaterialTheme.typography.labelSmall,
                    color = lumen.textTertiary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                val detail = listOf(modelCode, manufacturer, os)
                    .filter { it.isNotBlank() && it != "No disponible" }
                    .joinToString(" · ")
                Text(
                    text = detail.ifBlank { modelCode },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        IconBadge(icon = icon, tint = tint)
        Spacer(Modifier.height(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconBadge(icon = icon, tint = tint)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
