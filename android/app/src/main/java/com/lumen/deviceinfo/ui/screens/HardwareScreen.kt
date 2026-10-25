package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.IconBadge
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.InfoRowSub
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.LocalLumenColors

@Composable
fun HardwareScreen(vm: LumenViewModel, onOpenCameras: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("CPU")
        GlassCard {
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
        GlassCard {
            InfoRow("Vendor", state.gpu.vendor)
            InfoRow("Renderer", state.gpu.renderer)
            InfoRow("Versión GLES", state.gpu.version)
        }

        SectionHeader("Pantalla")
        GlassCard {
            InfoRow("Resolución", state.display.resolution)
            InfoRow("Densidad", state.display.density)
            InfoRow("Refresco", state.display.refreshRate)
            InfoRow("HDR", state.display.hdr)
            InfoRow("Tamaño", state.display.sizeInches)
        }

        SectionHeader("Memoria")
        GlassCard {
            InfoRow("Total", state.memory.total)
            InfoRow("Disponible", state.memory.available)
            InfoRow("Usada", state.memory.used)
            InfoRow("Usada %", "${state.memory.usedPercent.toInt()}%")
        }

        SectionHeader("Almacenamiento")
        GlassCard {
            InfoRow("Total", state.storage.total)
            InfoRow("Libre", state.storage.free)
            state.storage.volumes.forEach { v ->
                DividerLine()
                InfoRow(v.path, "${v.total} · ${v.free} libre")
            }
        }

        SectionHeader("Cámaras")
        GlassCard {
            InfoRow("Número", state.cameras.count.toString())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCameras)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = Icons.Filled.CameraAlt,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "Ver detalle de cámaras",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Resolución, lentes, apertura y vídeo",
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

        SectionHeader("Bluetooth")
        GlassCard {
            InfoRow("Soportado", if (state.bluetooth.supported) "Sí" else "No")
            InfoRow("Nombre", state.bluetooth.name)
            InfoRow("Dirección", state.bluetooth.address)
            InfoRow("Estado", state.bluetooth.state)
        }

        Spacer(Modifier.height(4.dp))
    }
}
