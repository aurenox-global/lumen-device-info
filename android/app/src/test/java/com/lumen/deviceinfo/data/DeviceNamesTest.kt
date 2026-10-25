package com.lumen.deviceinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamesTest {

    private val map by lazy {
        // El asset se lee por ruta de archivo (directorio de trabajo = módulo `app`).
        DeviceNames.loadFromFile("src/main/assets/devices.tsv.gz")
    }

    @Test
    fun assetLoadsWithExpectedSize() {
        assertEquals(64_009, map.size)
    }

    @Test
    fun resolvesVivoX200Ultra() {
        assertEquals("Vivo X200 Ultra", DeviceNames.lookup(map, "V2454A", "V2454A")?.display)
    }

    @Test
    fun resolvesHonorMagic6ProWithoutDuplicatedBrand() {
        assertEquals("HONOR Magic6 Pro", DeviceNames.lookup(map, "BVL-AN16", "BVL-AN16")?.display)
    }

    @Test
    fun resolvesGalaxyS24Ultra() {
        assertEquals("Samsung Galaxy S24 Ultra", DeviceNames.lookup(map, "SM-S928B", "SM-S928B")?.display)
    }

    @Test
    fun resolvesPixel9ProWithGoogleBrand() {
        assertEquals("Google Pixel 9 Pro", DeviceNames.lookup(map, "PIXEL 9 PRO", "PIXEL 9 PRO")?.display)
    }

    @Test
    fun normalizationIsCaseAndSpaceInsensitive() {
        // La consulta en minúsculas y con espacios repetidos debe normalizarse.
        assertEquals("Google Pixel 9 Pro", DeviceNames.lookup(map, "  pixel  9 pro ", "caiman")?.display)
    }

    @Test
    fun unknownModelReturnsNull() {
        assertNull(DeviceNames.lookup(map, "LUMEN-DOES-NOT-EXIST-999", "lumen_does_not_exist"))
    }
}
