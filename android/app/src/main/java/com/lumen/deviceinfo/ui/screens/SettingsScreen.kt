package com.lumen.deviceinfo.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.ui.components.GlassCard
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: LumenViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Apariencia")
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Modo oscuro", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.themeMode == ThemeMode.DARK,
                    onCheckedChange = { dark ->
                        vm.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Seguir sistema", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.themeMode == ThemeMode.SYSTEM,
                    onCheckedChange = { use ->
                        vm.setThemeMode(if (use) ThemeMode.SYSTEM else ThemeMode.LIGHT)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        SectionHeader("Datos")
        GlassCard {
            ActionRow("Exportar informe") {
                val report = vm.buildReport()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Lumen — Informe del dispositivo")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir informe"))
            }
        }

        SectionHeader("Acerca de")
        GlassCard {
            InfoRow("App", "Lumen")
            InfoRow("Versión", com.lumen.deviceinfo.BuildConfig.VERSION_NAME)
            InfoRow("Creador", "Andrés Mag · Cuba")
            InfoRow("Diseño", "ColorOS 17")
            InfoRow("Privacidad", "Sin anuncios ni telemetría")
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
