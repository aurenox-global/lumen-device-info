package com.lumen.deviceinfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.deviceinfo.LumenViewModel
import com.lumen.deviceinfo.data.Benchmark
import com.lumen.deviceinfo.ui.components.InfoRow
import com.lumen.deviceinfo.ui.components.LumenCard
import com.lumen.deviceinfo.ui.components.SectionHeader
import com.lumen.deviceinfo.ui.theme.MonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TestsScreen(vm: LumenViewModel) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(mapOf<String, Benchmark.BenchResult>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Benchmarks")
        LumenCard {
            Text(
                text = "Los benchmarks ejecutan cálculos locales y miden el rendimiento del dispositivo. No se envían datos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BenchButton("CPU 1 hilo", running, Modifier.weight(1f)) {
                    running = "cpu_single"
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { vm.benchmark.runCpuSingle() }
                        vm.benchmark.saveResult(r)
                        results = results + (r.id to r)
                        running = null
                    }
                }
                BenchButton("CPU multi", running, Modifier.weight(1f)) {
                    running = "cpu_multi"
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { vm.benchmark.runCpuMulti() }
                        vm.benchmark.saveResult(r)
                        results = results + (r.id to r)
                        running = null
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BenchButton("Memoria", running, Modifier.weight(1f)) {
                    running = "memory"
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { vm.benchmark.runMemory() }
                        vm.benchmark.saveResult(r)
                        results = results + (r.id to r)
                        running = null
                    }
                }
                BenchButton("Storage", running, Modifier.weight(1f)) {
                    running = "storage"
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { vm.benchmark.runStorage() }
                        vm.benchmark.saveResult(r)
                        results = results + (r.id to r)
                        running = null
                    }
                }
            }
        }

        val ids = listOf("cpu_single", "cpu_multi", "memory", "storage")
        ids.forEach { id ->
            val label = when (id) {
                "cpu_single" -> "CPU 1 hilo"
                "cpu_multi" -> "CPU multihilo"
                "memory" -> "Memoria"
                else -> "Almacenamiento"
            }
            SectionHeader(label)
            LumenCard {
                if (running == id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Ejecutando…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (results.containsKey(id)) {
                    val r = results[id]!!
                    InfoRow("Puntuación", r.score)
                    InfoRow("Detalle", r.detail)
                } else {
                    Text(
                        "Sin ejecutar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val history = vm.benchmark.history(id)
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Historial: ${history.joinToString(" · ") { it.score }}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BenchButton(label: String, running: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = running == null,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
