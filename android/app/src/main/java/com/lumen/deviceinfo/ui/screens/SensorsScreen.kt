package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.SectionHeader

@Composable
fun SensorsScreen(vm: LumenViewModel) {
    val sensors by vm.sensors.collectAsStateWithLifecycle()
    val live by vm.liveSensorValues.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.startSensors()
        onDispose { vm.stopSensors() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Sensores (${sensors.size})")
        if (sensors.isEmpty()) {
            GlassCard {
                Text(
                    text = "No se detectaron sensores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        sensors.forEach { s ->
            GlassCard {
                Text(
                    text = s.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${s.typeName} · ${s.vendor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("Rango", s.range)
                InfoRow("Resolución", s.resolution)
                InfoRow("Consumo", s.power)
                val liveVal = live[s.type]
                if (liveVal != null) {
                    InfoRow("Valor en vivo", liveVal)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
