package com.lumen.deviceinfo.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.data.formatBytes
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.Sparkline
import com.lumen.deviceinfo.ui.theme.Mint
import com.lumen.deviceinfo.ui.theme.MonoFamily

@Composable
fun HomeScreen(vm: LumenViewModel, onOpen: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero
        LumenCard {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
            ) {
                // subtle glow via accent circle
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Mint.copy(alpha = 0.14f))
                    drawCircle(Mint, radius = size.minDimension * 0.18f)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.summary.model.ifBlank { "Dispositivo" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.summary.soc,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.summary.os,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4 métricas rápidas
        val ramUsed = state.memory.usedPercent
        val storageUsed = state.storage.usedPercent
        val battery = if (state.battery.level >= 0) state.battery.level else 0
        val cpu = state.cpuUsage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCell(
                icon = Icons.Filled.Memory,
                label = "RAM",
                value = "${ramUsed.toInt()}%",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                icon = Icons.Filled.SdStorage,
                label = "Alm.",
                value = "${storageUsed.toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCell(
                icon = Icons.Filled.BatteryChargingFull,
                label = "Batería",
                value = "$battery%",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                icon = Icons.Filled.Speed,
                label = "CPU",
                value = "${cpu.toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }

        // Sparklines en vivo
        LumenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${cpu.toInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MonoFamily,
                    color = Mint
                )
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(state.cpuHistory, color = Mint)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAM".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${"%.0f".format(state.ramHistory.lastOrNull() ?: 0f)} %",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MonoFamily,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(state.ramHistory, color = MaterialTheme.colorScheme.secondary)
        }

        // Accesos rápidos
        LumenCard {
            Text(
                text = "ACCESOS".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            QuickLink("Hardware", "CPU, GPU, pantalla y más") { onOpen("hardware") }
            QuickLink("Batería", "Estado, temperatura e historial") { onOpen("battery") }
            QuickLink("Sistema", "Android, kernel y build") { onOpen("system") }
            QuickLink("Red", "Conexión y operador") { onOpen("network") }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MetricCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    LumenCard(modifier = modifier) {
        Icon(icon, contentDescription = label, tint = Mint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("→", color = Mint, style = MaterialTheme.typography.titleMedium)
    }
}
