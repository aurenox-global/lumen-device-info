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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.data.CameraDetail
import com.lumen.deviceinfo.data.LensData
import com.lumen.deviceinfo.data.NA
import com.lumen.deviceinfo.data.PhysicalLens
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.IconBadge
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.theme.LocalLumenColors

@Composable
fun CamerasScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val lumen = LocalLumenColors.current

    LaunchedEffect(Unit) { vm.loadCameraDetails() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "CÁMARAS",
            style = MaterialTheme.typography.labelSmall,
            color = lumen.textTertiary
        )

        val details = state.cameraDetails
        if (details.isEmpty()) {
            GlassCard {
                InfoRow("Estado", if (state.cameraDetailsLoaded) "No disponible" else "Leyendo cámaras…")
            }
        } else {
            details.forEach { cam -> CameraCard(cam) }
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Filled.CameraAlt,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 44.dp
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = "Nota",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Android no expone el nombre comercial de cada lente (p. ej. «Sony IMX989»). Se muestra el id interno de la cámara y los datos técnicos leídos de Camera2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CameraCard(cam: CameraDetail) {
    val lumen = LocalLumenColors.current
    val subtitle = listOf(cam.lens.megapixels, cam.lens.apertures)
        .filter { it.isNotBlank() && it != NA }
        .joinToString(" · ")

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Filled.CameraAlt,
                tint = MaterialTheme.colorScheme.primary,
                size = 44.dp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = cam.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle.ifBlank { cam.facing },
                    style = MaterialTheme.typography.bodyMedium,
                    color = lumen.textTertiary
                )
            }
        }
        DividerLine(Modifier.padding(vertical = 10.dp))

        InfoRow("Orientación", cam.facing)
        if (cam.isLogical) {
            InfoRow("Cámara lógica", "Sí")
            InfoRow("Cámaras físicas", cam.physicalIds.joinToString(", ").ifBlank { NA })
        }
        LensRows(cam.lens)
        InfoRow("OIS", cam.ois)
        InfoRow("Flash", cam.flash)
        InfoRow("Enfoque", cam.afModes)
        InfoRow("Sensibilidad ISO", cam.isoRange)
        InfoRow("RAW", cam.raw)
        InfoRow("Nivel hardware", cam.hardwareLevel)
        InfoRow("Zoom máx.", cam.maxZoom)
        InfoRow("Vídeo máx.", cam.videoMax)
    }

    if (cam.physicalLenses.isNotEmpty()) {
        SectionHeader("Lentes físicas · ${cam.title}")
        cam.physicalLenses.forEach { lens -> PhysicalLensCard(lens) }
    }
}

@Composable
private fun LensRows(lens: LensData) {
    InfoRow("Resolución JPEG", lens.maxJpeg)
    InfoRow("Megapíxeles", lens.megapixels)
    InfoRow("Array activo", lens.sensorArray)
    InfoRow("Tamaño sensor", lens.sensorSize)
    InfoRow("Tamaño de píxel", lens.pixelSize)
    InfoRow("Apertura", lens.apertures)
    InfoRow("Focal", lens.focal)
    InfoRow("Equiv. 35 mm", lens.focal35mm)
}

@Composable
private fun PhysicalLensCard(lens: PhysicalLens) {
    val lumen = LocalLumenColors.current
    GlassCard {
        Text(
            text = "Lente física",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = lens.id,
            style = MaterialTheme.typography.bodySmall,
            color = lumen.textTertiary
        )
        DividerLine(Modifier.padding(vertical = 10.dp))
        LensRows(lens.lens)
    }
}

@Composable
private fun SectionHeader(title: String) {
    val lumen = LocalLumenColors.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = lumen.textTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp, top = 8.dp)
    )
}
