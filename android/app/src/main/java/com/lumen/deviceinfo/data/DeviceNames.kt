package com.lumen.deviceinfo.data

import android.content.Context
import android.util.Log
import com.lumen.deviceinfo.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Resuelve el nombre comercial real del dispositivo a partir de
 * `assets/devices.tsv.gz` (la lista oficial de dispositivos de Google:
 * MODELO \t MARCA \t NOMBRE, ~64.000 entradas).
 *
 * La carga es perezosa y ocurre en un hilo de fondo (IO). El mapa se construye
 * con claves normalizadas (mayúsculas, trim, espacios colapsados) para que la
 * consulta sea insensible a mayúsculas y a espacios repetidos.
 */
object DeviceNames {

    data class Entry(val brand: String, val marketing: String) {
        /**
         * «Marca Nombre» evitando duplicar la marca:
         *  - Honor + "HONOR Magic6 Pro" → "HONOR Magic6 Pro"
         *  - Google + "Pixel 9 Pro"      → "Google Pixel 9 Pro"
         *  - Vivo + "X200 Ultra"         → "Vivo X200 Ultra"
         */
        val display: String
            get() {
                val b = brand.trim()
                val m = marketing.trim()
                if (m.isEmpty()) return b
                if (b.isEmpty()) return m
                return if (m.lowercase(Locale.ROOT).startsWith(b.lowercase(Locale.ROOT))) m else "$b $m"
            }
    }

    @Volatile
    private var cache: Map<String, Entry>? = null

    /**
     * Correcciones puntuales para modelos muy comunes. Se consultan solo si la
     * lista no tiene el modelo; así el dispositivo del usuario nunca se queda
     * sin nombre por un problema de empaquetado o una variante regional.
     */
    private val overrides: Map<String, Entry> = mapOf(
        "V2454A" to Entry("Vivo", "X200 Ultra"),
        "V2454DA" to Entry("Vivo", "X200 Ultra"),
        "BVL-AN16" to Entry("HONOR", "Magic6 Pro"),
        "SM-S928B" to Entry("Samsung", "Galaxy S24 Ultra"),
        "PIXEL 9 PRO" to Entry("Google", "Pixel 9 Pro")
    )

    /** Resuelve usando el asset empaquetado de la app (carga perezosa en IO). */
    fun resolve(context: Context, model: String, device: String): Entry? {
        val map = cache ?: synchronized(this) {
            cache ?: try {
                openAsset(context).use { loadFromStream(it) }.also { cache = it }
            } catch (t: Throwable) {
                emptyMap<String, Entry>().also { cache = it }
            }
        }
        val fromList = lookup(map, model, device)
        val resolved = fromList
            ?: overrides[normalize(model)]
            ?: overrides[normalize(device)]
        if (BuildConfig.DEBUG) {
            Log.d(
                "LumenDeviceNames",
                "entries=${map.size} model=${model.ifBlank { "?" }} device=${device.ifBlank { "?" }} " +
                    "resolved=${resolved?.display ?: "none"} " +
                    "selfCheckV2454A=${map["V2454A"]?.display ?: "none"}"
            )
        }
        return resolved
    }

    /**
     * aapt2 descomprime los assets `.gz` y quita la extensión al empaquetar;
     * por eso se intenta primero `devices.tsv` y luego el `.gz` original.
     */
    private fun openAsset(context: Context): InputStream {
        return try {
            context.assets.open("devices.tsv")
        } catch (t: Throwable) {
            context.assets.open("devices.tsv.gz")
        }
    }

    /** Consulta sobre un mapa ya cargado (sin Context; útil para tests). */
    fun lookup(map: Map<String, Entry>, model: String, device: String): Entry? {
        if (map.isEmpty()) return null
        for (key in candidateKeys(model, device)) {
            map[key]?.let { return it }
        }
        return null
    }

    /** Carga desde un archivo `.gz` por ruta (para tests JVM). */
    fun loadFromFile(path: String): Map<String, Entry> =
        File(path).inputStream().use { loadFromStream(it) }

    /** Carga desde un stream gzip o de texto plano. Nunca lanza. */
    fun loadFromStream(stream: InputStream): Map<String, Entry> {
        val map = HashMap<String, Entry>(70_000)
        try {
            val buffered = BufferedInputStream(stream)
            buffered.mark(4)
            val magic = ByteArray(2)
            val read = buffered.read(magic)
            buffered.reset()
            val text = if (read == 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()) {
                GZIPInputStream(buffered)
            } else {
                buffered
            }
            text.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val parts = line.split('\t')
                    if (parts.size < 3) continue
                    val model = parts[0].trim()
                    val brand = parts[1].trim()
                    val marketing = parts[2].trim()
                    if (model.isEmpty() || marketing.isEmpty()) continue
                    val key = normalize(model)
                    if (key.isEmpty()) continue
                    if (!map.containsKey(key)) map[key] = Entry(brand, marketing)
                }
            }
        } catch (t: Throwable) {
            // devolver vacío: el resolvedor degrada a los fallbacks.
        }
        return map
    }

    /** Normaliza una clave: mayúsculas, trim y espacios colapsados. */
    fun normalize(value: String): String =
        value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /**
     * Candidatos de consulta en orden: modelo, device y variantes con sufijos
     * habituales quitados (dual SIM, 5G/4G/LTE, códigos de región tipo -AN00).
     */
    fun candidateKeys(model: String, device: String): List<String> {
        val out = LinkedHashSet<String>()
        fun offer(raw: String) {
            val n = normalize(raw)
            if (n.isEmpty()) return
            out.add(n)
            strippedVariants(n).forEach { out.add(it) }
        }
        offer(model)
        offer(device)
        return out.toList()
    }

    private val REGION_SUFFIX = Regex("-[A-Za-z0-9]*\\d[A-Za-z0-9]*$")
    private val NET_SUFFIX = Regex("\\s+(5G|4G|LTE|PLUS|PRO|MAX)$", RegexOption.IGNORE_CASE)
    private val DUAL_SUFFIXES = listOf("/DS", " DS", " DUAL SIM", " (DUAL SIM)", "(DUAL SIM)")

    private fun strippedVariants(key: String): List<String> {
        val out = LinkedHashSet<String>()
        for (s in DUAL_SUFFIXES) {
            if (key.endsWith(s, ignoreCase = true)) out.add(key.removeRange(key.length - s.length, key.length).trim())
        }
        NET_SUFFIX.find(key)?.let { out.add(key.substring(0, it.range.first).trim()) }
        REGION_SUFFIX.find(key)?.let { out.add(key.substring(0, it.range.first).trim()) }
        return out.filter { it.isNotEmpty() && it != key }
    }
}
