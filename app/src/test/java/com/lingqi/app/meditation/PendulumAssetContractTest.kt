package com.lingqi.app.meditation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendulumAssetContractTest {
    private val moduleRoot = sequenceOf(File("app"), File("."))
        .first { File(it, "src/main").isDirectory }

    @Test
    fun derivedPendulumCuesAreShortMono44100PcmWavFiles() {
        assertWavContract("pendulum_tick.wav", "C55825F02A3951EFCC1643F9661B20AD037F687212CB96F4A70BE83AA04B2808")
        assertWavContract("pendulum_tock.wav", "372311BA8F3E0E7A3C82EFCFF58C7A1ED4BCE162DE544821E99CF49805FCC112")
    }

    @Test
    fun licenseManifestRecordsOfficialSourceAndDerivation() {
        val manifest = File(moduleRoot, "src/main/assets/audio/licenses.json").readText()
        assertTrue(manifest.contains("https://pixabay.com/sound-effects/film-special-effects-old-clock-ticking-352288/"))
        assertTrue(manifest.contains("Universfield"))
        assertTrue(manifest.contains("448DCD18210ABF5D7685A58FA6FF0B987E54EA6884D9E3AD7A9187BBFA357F93"))
        assertTrue(manifest.contains("7.630"))
        assertTrue(manifest.contains("7.944"))
        assertTrue(manifest.contains("C55825F02A3951EFCC1643F9661B20AD037F687212CB96F4A70BE83AA04B2808"))
        assertTrue(manifest.contains("372311BA8F3E0E7A3C82EFCFF58C7A1ED4BCE162DE544821E99CF49805FCC112"))
    }

    private fun assertWavContract(filename: String, expectedSha256: String) {
        val file = File(moduleRoot, "src/main/res/raw/$filename")
        assertTrue("Missing $filename", file.isFile)
        val bytes = file.readBytes()
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())

        val fmtOffset = findChunk(bytes, "fmt ")
        val dataOffset = findChunk(bytes, "data")
        val fmt = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, fmt.getShort(fmtOffset + 8).toInt())
        assertEquals(1, fmt.getShort(fmtOffset + 10).toInt())
        assertEquals(44_100, fmt.getInt(fmtOffset + 12))
        assertEquals(16, fmt.getShort(fmtOffset + 22).toInt())

        val dataSize = fmt.getInt(dataOffset + 4)
        val durationSeconds = dataSize / 2.0 / 44_100.0
        assertTrue("$filename is longer than 300 ms", durationSeconds <= 0.300)
        val samples = ByteBuffer.wrap(bytes, dataOffset + 8, dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        var nonZero = false
        var peak = 0
        while (samples.hasRemaining()) {
            val value = kotlin.math.abs(samples.get().toInt())
            if (value > 0) nonZero = true
            if (value > peak) peak = value
        }
        assertTrue("$filename is silent", nonZero)
        assertTrue("$filename clips", peak < Short.MAX_VALUE)
        assertEquals(expectedSha256, sha256(bytes))
    }

    private fun findChunk(bytes: ByteArray, id: String): Int {
        for (index in 12 until bytes.size - 8) {
            if (bytes.copyOfRange(index, index + 4).decodeToString() == id) return index
        }
        error("Missing WAV chunk $id")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02X".format(it) }
}
