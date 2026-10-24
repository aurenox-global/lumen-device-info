package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.data.NA
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.components.Sparkline
import com.lumen.deviceinfo.ui.theme.Mint
import com.lumen.deviceinfo.ui.theme.MonoFamily
import com.lumen.deviceinfo.ui.theme.Warn
import java.util.Locale
import kotlin.math.ceil

@Composable
fun BatteryScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val level = if (state.battery.level >= 0) state.battery.level else 0
    val health = state.battery.healthInfo
    val history = state.batteryHistory
    val sohAvailable = health.sohValue >= 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LumenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$level%",
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Mint
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = state.battery.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.battery.plugged,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { level / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Mint,
                trackColor = MaterialTheme.colorScheme.outline
            )
            if (health.remaining != NA) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tiempo restante estimado: ${health.remaining}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionHeader("Salud")
        LumenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (sohAvailable) health.sohPercent else "No disponible",
                        fontSize = if (sohAvailable) 36.sp else 20.sp,
                        lineHeight = if (sohAvailable) 40.sp else 26.sp,
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sohAvailable) Mint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Salud estimada (SoH)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = health.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            DividerLine()
            InfoRow("Capacidad de diseño", health.designMah)
            InfoRow("Capacidad estimada", health.estimatedMah)
            InfoRow("Ciclos de carga", health.cycles)
            DividerLine()
            Spacer(Modifier.height(10.dp))
            Text(
                text = health.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionHeader("Historial")
        LumenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NIVEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (level > 0) "$level%" else NA,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFamily,
                    color = Mint
                )
            }
            Sparkline(
                values = history.map { it.level.toFloat() },
                color = Mint,
                maxValue = 100f
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEMPERATURA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.battery.temperature,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFamily,
                    color = Warn
                )
            }
            val maxTemp = history.maxOfOrNull { it.tempC } ?: 40f
            Sparkline(
                values = history.map { it.tempC },
                color = Warn,
                maxValue = ceil(maxTemp.coerceAtLeast(40f) / 5f) * 5f
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = historySpanText(history.size, history.firstOrNull()?.ts, history.lastOrNull()?.ts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionHeader("Estado")
        LumenCard {
            InfoRow("Nivel", "$level%")
            InfoRow("Estado", state.battery.status)
            InfoRow("Salud del sistema", state.battery.health)
            InfoRow("Enchufado", state.battery.plugged)
            InfoRow("Tecnología", state.battery.technology)
            InfoRow("Carga actual", state.battery.capacity)
        }

        SectionHeader("Mediciones")
        LumenCard {
            InfoRow("Temperatura", state.battery.temperature)
            InfoRow("Voltaje", state.battery.voltage)
            InfoRow("Corriente", state.battery.current)
            InfoRow("Potencia", state.battery.power)
        }

        Spacer(Modifier.height(4.dp))
    }
}

private fun historySpanText(count: Int, firstTs: Long?, lastTs: Long?): String {
    if (count == 0 || firstTs == null || lastTs == null) {
        return "Sin muestras todavía; se guardan cada 2 s con la app abierta."
    }
    val minutes = ((lastTs - firstTs) / 60_000L).coerceAtLeast(1L)
    return String.format(Locale.US, "%d muestras · %d min", count, minutes)
}
