package com.kb007.recorder.audio

import com.kb007.recorder.audio.encode.OggWriter
import com.kb007.recorder.audio.encode.Opus
import com.kb007.recorder.audio.encode.WavWriter
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Genera archivos de muestra en build/kbcheck para validarlos con herramientas externas
 * (ffprobe/ffmpeg). No afirma nada por sí mismo: el chequeo lo hace el script de verificación.
 */
class FfmpegFixturesTest {
    private val dir = File("build/kbcheck").apply { mkdirs() }

    @Test fun writeOggOpusFixture() {
        FileOutputStream(File(dir, "silencio_5s.ogg")).use { fos ->
            val w = OggWriter(fos)
            w.writeHeaderPacket(Opus.head(1, 312, 48_000), bos = true)
            w.writeHeaderPacket(Opus.tags(), bos = false)
            var g = 0L
            // 250 paquetes CELT de 20 ms "vacíos" (TOC 0xF8 + frame de 0 bytes = DTX) → 5 s
            repeat(250) { g += Opus.packetSamples48k(byteArrayOf(0xF8.toByte())); w.writeAudioPacket(byteArrayOf(0xF8.toByte()), g) }
            w.finish(g)
        }
    }

    @Test fun writeWavFixture() {
        val w = WavWriter(File(dir, "tono_2s.wav"), 44_100, 2)
        val pcm = ShortArray(44_100 * 2 * 2) { i -> (8000 * sin(2 * PI * 440 * (i / 2) / 44_100.0)).toInt().toShort() }
        w.write(pcm, 0, pcm.size)
        w.finish()
    }
}
