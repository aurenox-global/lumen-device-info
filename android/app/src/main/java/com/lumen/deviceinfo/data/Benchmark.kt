package com.lumen.deviceinfo.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.Random
import kotlin.math.sqrt

/**
 * Benchmarks ligeros de CPU, memoria y almacenamiento con puntuación estable.
 * Los resultados se guardan en SharedPreferences como historial.
 */
class Benchmark(private val context: Context) {

    data class BenchResult(val id: String, val label: String, val score: String, val detail: String)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("benchmark_history", Context.MODE_PRIVATE)

    fun runCpuSingle(): BenchResult {
        val seconds = 2.0
        var iterations = 0L
        val end = System.nanoTime() + (seconds * 1_000_000_000).toLong()
        var x = 1.0000001
        while (System.nanoTime() < end) {
            x = x * 1.0000001 + 0.0000001
            if (x > 1e10) x = 1.0
            iterations++
        }
        val score = iterations / seconds
        return BenchResult(
            "cpu_single",
            "CPU 1 hilo",
            formatScore(score),
            "%.0f ops/s".format(score)
        )
    }

    fun runCpuMulti(): BenchResult {
        val seconds = 2.0
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        var total = 0L
        val end = System.nanoTime() + (seconds * 1_000_000_000).toLong()
        val workers = (0 until threads).map {
            Thread {
                var iterations = 0L
                var x = 1.0000001
                while (System.nanoTime() < end) {
                    x = x * 1.0000001 + 0.0000001
                    if (x > 1e10) x = 1.0
                    iterations++
                }
                synchronized(this@Benchmark) { total += iterations }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        val score = total / seconds
        return BenchResult(
            "cpu_multi",
            "CPU multihilo ($threads hilos)",
            formatScore(score),
            "%.0f ops/s".format(score)
        )
    }

    fun runMemory(): BenchResult {
        val size = 32 * 1024 * 1024 // 32 MB
        val arr = IntArray(size / 4)
        val start = System.nanoTime()
        var sum = 0
        for (i in arr.indices) {
            arr[i] = i
            sum += arr[i]
        }
        val writeMs = (System.nanoTime() - start) / 1_000_000.0
        val start2 = System.nanoTime()
        var sum2 = 0L
        for (i in arr.indices) sum2 += arr[i]
        val readMs = (System.nanoTime() - start2) / 1_000_000.0
        val mbps = (size / 1024.0 / 1024.0) / ((writeMs + readMs) / 1000.0)
        return BenchResult(
            "memory",
            "Memoria",
            formatScore(mbps),
            "%.0f MB/s (lect+esc)".format(mbps)
        )
    }

    fun runStorage(): BenchResult {
        val file = File(context.cacheDir, "bench_${System.currentTimeMillis()}.tmp")
        val size = 32 * 1024 * 1024 // 32 MB
        val data = ByteArray(1024 * 1024) // 1 MB chunk
        Random(42).nextBytes(data)
        return try {
            val start = System.nanoTime()
            file.outputStream().use { out ->
                repeat(size / data.size) { out.write(data) }
            }
            val writeMs = (System.nanoTime() - start) / 1_000_000.0

            val start2 = System.nanoTime()
            var checksum = 0L
            val buf = ByteArray(1024 * 1024)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    for (i in 0 until read) checksum += buf[i]
                }
            }
            val readMs = (System.nanoTime() - start2) / 1_000_000.0
            val mbps = (size / 1024.0 / 1024.0) / ((writeMs + readMs) / 1000.0)
            BenchResult(
                "storage",
                "Almacenamiento",
                formatScore(mbps),
                "%.0f MB/s (lect+esc)".format(mbps)
            )
        } catch (e: Exception) {
            BenchResult("storage", "Almacenamiento", "0", "Error")
        } finally {
            try { file.delete() } catch (e: Exception) { }
        }
    }

    private fun formatScore(v: Double): String = "%.2f".format(v)

    fun saveResult(r: BenchResult) {
        try {
            val key = "history_${r.id}"
            val existing = prefs.getString(key, null) ?: ""
            val entry = "${System.currentTimeMillis()}|${r.score}|${r.detail}"
            val updated = (listOf(entry) + existing.split("\n").filter { it.isNotBlank() }).take(5).joinToString("\n")
            prefs.edit().putString(key, updated).apply()
        } catch (e: Exception) { }
    }

    data class HistoryEntry(val timestamp: Long, val score: String, val detail: String)

    fun history(id: String): List<HistoryEntry> {
        return try {
            val raw = prefs.getString("history_$id", null) ?: return emptyList()
            raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 3) {
                    HistoryEntry(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2])
                } else null
            }
        } catch (e: Exception) { emptyList() }
    }
}
