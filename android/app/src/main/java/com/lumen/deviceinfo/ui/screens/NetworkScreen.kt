package com.lumen.deviceinfo.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.data.NA
import com.lumen.deviceinfo.ui.components.DividerLine
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.Mint
import com.lumen.deviceinfo.ui.theme.MonoFamily

@Composable
fun NetworkScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val sim = state.sim
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Concedido o denegado: releemos; el estado real manda.
        vm.refreshNow()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("SIM y eSIM")
        LumenCard {
            InfoRow("eSIM compatible", sim.esimSupported)
            InfoRow("eUICC habilitado", sim.euiccEnabled)
            InfoRow("Ranuras SIM", sim.slots)
            InfoRow(
                "SIMs activas",
                if (sim.permission == "Concedido") sim.activeCount else "Permiso requerido"
            )
            if (sim.subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                sim.subscriptions.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (sim.permission == "Concedido") {
                Text(
                    text = "Permiso concedido · operador y ranuras leídos con READ_PHONE_STATE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mint
                )
            } else {
                if (sim.permission == "No concedido") {
                    Text(
                        text = "Permiso no concedido",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Mint)
                ) {
                    Text(
                        text = "Permitir para leer SIMs",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MonoFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Mint
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DividerLine()
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Leer el EID o los perfiles eSIM requiere privilegios del operador o del sistema; " +
                    "no es posible en una app normal. Aquí solo se consulta la compatibilidad pública.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
