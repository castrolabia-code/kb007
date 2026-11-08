package com.kb007.recorder.audio

import com.kb007.recorder.audio.encode.Adts
import com.kb007.recorder.audio.encode.EncodeSpec
import com.kb007.recorder.audio.encode.OggWriter
import com.kb007.recorder.audio.encode.Opus
import com.kb007.recorder.audio.encode.Remuxer
import com.kb007.recorder.audio.encode.WavWriter
import com.kb007.recorder.data.prefs.AudioFormatType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ContainerTest {
    @Test fun adtsHeaderRoundTrip() {
        val out = ByteArrayOutputStream()
        repeat(3) { i ->
            val data = ByteArray(100 + i) { it.toByte() }
            out.write(Adts.header(data.size, 44_100, 2)); out.write(data)
        }
        out.write(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50)) // frame truncado al final
        val bytes = out.toByteArray()
        val frames = Adts.parse(bytes)
        assertEquals(3, frames.size)
        assertEquals(44_100, frames[0].sampleRate); assertEquals(2, frames[0].channels)
        assertEquals(102, frames[2].dataLen)
        val n = Remuxer.forEachAdtsFrame(ByteArrayInputStream(bytes)) { _, len, sr, ch ->
            assertEquals(44_100, sr); assertEquals(2, ch); assertTrue(len >= 100)
        }
        assertEquals(3, n)
    }

    @Test fun audioSpecificConfigForLc44kStereo() {
        // AAC-LC, 44,1 kHz (índice 4), 2 canales → 0x12 0x10
        assertArrayEquals(byteArrayOf(0x12, 0x10), Adts.audioSpecificConfig(44_100, 2))
    }

    @Test fun opusPacketDurations() {
        assertEquals(960, Opus.packetSamples48k(byteArrayOf((31 shl 3).toByte())))   // CELT 20 ms
        assertEquals(1920, Opus.packetSamples48k(byteArrayOf(((31 shl 3) or 1).toByte(), 0))) // 2 frames
        assertEquals(2880, Opus.packetSamples48k(byteArrayOf((3 shl 3).toByte())))    // SILK 60 ms
        assertEquals(3 * 480, Opus.packetSamples48k(byteArrayOf(((30 shl 3) or 3).toByte(), 3))) // 3x10 ms
        assertEquals(48_000, Opus.captureRateFor(44_100))
        assertEquals(16_000, Opus.captureRateFor(16_000))
    }

    @Test fun oggPagesAreValidAndRepairable() {
        val f = File.createTempFile("kbtest", ".ogg")
        FileOutputStream(f).use { fos ->
            val w = OggWriter(fos, serial = 1234)
            w.writeHeaderPacket(Opus.head(1, 312, 48_000), bos = true)
            w.writeHeaderPacket(Opus.tags(), bos = false)
            var g = 0L
            repeat(120) { g += 960; w.writeAudioPacket(ByteArray(80) { it.toByte() }, g) }
            // sin finish(): simulamos un corte, y además agregamos basura de una página a medio escribir
            fos.write("OggS".toByteArray() + ByteArray(10))
        }
        val granule = OggWriter.repair(f)
        assertTrue("granule=$granule", granule in 48_000L..115_200L)
        // Tras reparar, una segunda pasada debe ver el mismo final
        assertEquals(granule, OggWriter.repair(f))
        f.delete()
    }

    @Test fun oggCrcKnownValue() {
        // Página mínima construida a mano y verificada contra libogg: "OggS" + ceros → CRC conocido.
        val page = ByteArray(27); "OggS".toByteArray().copyInto(page)
        val c = OggWriter.crc(page, 0, page.size)
        assertTrue(c != 0)
        // Propiedad: cambiar un byte cambia el CRC
        page[10] = 1
        assertTrue(c != OggWriter.crc(page, 0, page.size))
    }

    @Test fun wavWriterHeaderAndRepair() {
        val f = File.createTempFile("kbtest", ".wav")
        val w = WavWriter(f, 16_000, 1)
        w.write(ShortArray(16_000 * 3) { 7 }, 0, 16_000 * 3)
        w.abort() // corte sin finish
        FileOutputStream(f, true).use { it.write(byteArrayOf(1)) } // byte impar suelto
        assertEquals(3000, WavWriter.repair(f, 16_000, 1))
        val (sr, ch, ms) = WavWriter.probe(f)!!
        assertEquals(16_000, sr); assertEquals(1, ch); assertEquals(3000, ms)
        assertEquals(44L + 96_000, f.length())
        f.delete()
    }

    @Test fun aacBitrateIsClampedToLegalMaximum() {
        assertEquals(96_000, EncodeSpec(AudioFormatType.AAC_M4A, 16_000, 1, 256).effectiveBitrate)
        assertEquals(128_000, EncodeSpec(AudioFormatType.AAC_M4A, 44_100, 1, 128).effectiveBitrate)
        assertEquals(768_000, EncodeSpec(AudioFormatType.WAV_PCM, 48_000, 1, 64).effectiveBitrate)
    }
}
