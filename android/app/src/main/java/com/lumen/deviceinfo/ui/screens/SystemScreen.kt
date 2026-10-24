package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.SectionHeader

@Composable
fun SystemScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            InfoRow("Nombre del dispositivo", state.system.deviceName)
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
