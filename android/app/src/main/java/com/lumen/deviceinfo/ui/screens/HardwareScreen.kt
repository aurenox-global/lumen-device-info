package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.InfoRowSub
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader

@Composable
fun HardwareScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("CPU")
        LumenCard {
            InfoRow("Núcleos", state.cpu.cores.toString())
            InfoRow("ABI", state.cpu.abi)
            InfoRow("Gobernador", state.cpu.governor)
            DividerLine()
            state.cpu.coresInfo.forEach { core ->
                InfoRowSub(
                    label = "Núcleo ${core.index}",
                    value = core.currentMhz,
                    sub = "mín ${core.minMhz} · máx ${core.maxMhz}"
                )
            }
            if (state.cpu.coresInfo.isEmpty()) {
                InfoRow("Frecuencias", "No disponible")
            }
        }

        SectionHeader("GPU")
        LumenCard {
            InfoRow("Vendor", state.gpu.vendor)
            InfoRow("Renderer", state.gpu.renderer)
            InfoRow("Versión GLES", state.gpu.version)
        }

        SectionHeader("Pantalla")
        LumenCard {
            InfoRow("Resolución", state.display.resolution)
            InfoRow("Densidad", state.display.density)
            InfoRow("Refresco", state.display.refreshRate)
            InfoRow("HDR", state.display.hdr)
            InfoRow("Tamaño", state.display.sizeInches)
        }

        SectionHeader("Memoria")
        LumenCard {
            InfoRow("Total", state.memory.total)
            InfoRow("Disponible", state.memory.available)
            InfoRow("Usada", state.memory.used)
            InfoRow("Usada %", "${state.memory.usedPercent.toInt()}%")
        }

        SectionHeader("Almacenamiento")
        LumenCard {
            InfoRow("Total", state.storage.total)
            InfoRow("Libre", state.storage.free)
            state.storage.volumes.forEach { v ->
                DividerLine()
                InfoRow(v.path, "${v.total} · ${v.free} libre")
            }
        }

        SectionHeader("Cámaras")
        LumenCard {
            InfoRow("Número", state.cameras.count.toString(), mono = false)
            if (state.cameras.details.isEmpty()) {
                InfoRow("Detalles", "No disponible")
            } else {
                state.cameras.details.forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        SectionHeader("Bluetooth")
        LumenCard {
            InfoRow("Soportado", if (state.bluetooth.supported) "Sí" else "No")
            InfoRow("Nombre", state.bluetooth.name)
            InfoRow("Dirección", state.bluetooth.address)
            InfoRow("Estado", state.bluetooth.state)
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
    }
}
