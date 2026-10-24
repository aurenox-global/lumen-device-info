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
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader

@Composable
fun NetworkScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Conexión")
        LumenCard {
            InfoRow("Transporte", state.network.transport)
            InfoRow("Interfaz", state.network.interfaceName)
            InfoRow("Ancho de banda", state.network.linkSpeed)
            InfoRow("Señal", state.network.signal)
            InfoRow("WiFi SSID", state.network.wifiSsid)
        }

        SectionHeader("Operador")
        LumenCard {
            InfoRow("Operador", state.network.operator)
            InfoRow("Tipo de red", state.network.networkType)
            InfoRow("Roaming", state.network.roaming)
            InfoRow("SIM operador", state.network.simOperator)
            InfoRow("SIM estado", state.network.simState)
        }

        SectionHeader("Direcciones")
        LumenCard {
            InfoRow("IPv4", state.network.ipv4)
            InfoRow("IPv6", state.network.ipv6)
            InfoRow("DNS", state.network.dns)
            InfoRow("MTU", state.network.mtu)
        }

        Spacer(Modifier.height(4.dp))
    }
}
