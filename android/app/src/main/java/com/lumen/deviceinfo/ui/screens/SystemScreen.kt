package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.LocalLumenColors

@Composable
fun SystemScreen(vm: LumenViewModel, onOpenMore: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MoreOptionsCard(onClick = onOpenMore)

        SectionHeader("Android")
        GlassCard {
            InfoRow("Versión", state.system.androidVersion)
            InfoRow("SDK", state.system.sdk)
            InfoRow("Parche de seguridad", state.system.securityPatch)
            InfoRow("Build ID", state.system.buildId)
            InfoRow("Build display", state.system.buildDisplay)
            InfoRow("Fingerprint", state.system.fingerprint)
        }

        SectionHeader("Kernel y hardware")
        GlassCard {
            InfoRow("Kernel", state.system.kernel)
            InfoRow("Bootloader", state.system.bootloader)
            InfoRow("Board", state.system.board)
            InfoRow("Hardware", state.system.hardware)
            InfoRow("SoC", state.system.soc)
        }

        SectionHeader("Dispositivo")
        GlassCard {
            InfoRow("Nombre comercial", state.summary.name)
            DividerLine()
            InfoRow("Fabricante", state.system.manufacturer)
            InfoRow("Marca", state.system.brand)
            InfoRow("Modelo", state.system.model)
            InfoRow("Código", state.system.device)
            InfoRow("Uptime", state.system.uptime)
        }

        SectionHeader("Regional")
        GlassCard {
            InfoRow("ABIs", state.system.abis)
            InfoRow("Locale", state.system.locale)
            InfoRow("Zona horaria", state.system.timezone)
        }

        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Acceso a las secciones secundarias (Red, Sensores, Apps, Tests y Ajustes).
 * Antes vivía como quinta pestaña de la barra inferior; con 4 pestañas la
 * barra respira y este botón deja claro dónde está el resto.
 */
@Composable
private fun MoreOptionsCard(onClick: () -> Unit) {
    val lumen = LocalLumenColors.current
    val primary = MaterialTheme.colorScheme.primary
    GlassCard(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = "Más opciones",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cámaras, red, sensores, apps, tests y ajustes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = lumen.textTertiary
            )
        }
    }
}
