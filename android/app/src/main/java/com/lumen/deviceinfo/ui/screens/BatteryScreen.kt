package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.Mint
import com.lumen.deviceinfo.ui.theme.MonoFamily

@Composable
fun BatteryScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val level = if (state.battery.level >= 0) state.battery.level else 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LumenCard {
            Text(
                text = "$level%",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFamily),
                color = Mint
            )
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.battery.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionHeader("Estado")
        LumenCard {
            InfoRow("Nivel", "$level%")
            InfoRow("Estado", state.battery.status)
            InfoRow("Salud", state.battery.health)
            InfoRow("Enchufado", state.battery.plugged)
            InfoRow("Tecnología", state.battery.technology)
            InfoRow("Capacidad", state.battery.capacity)
        }

        SectionHeader("Mediciones")
        LumenCard {
            InfoRow("Temperatura", state.battery.temperature)
            InfoRow("Voltaje", state.battery.voltage)
            InfoRow("Corriente", state.battery.current)
            InfoRow("Potencia", state.battery.power)
        }

        SectionHeader("Historial")
        LumenCard {
            Text(
                text = "El historial de nivel y temperatura se muestra aquí a medida que se acumulan muestras en vivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}
