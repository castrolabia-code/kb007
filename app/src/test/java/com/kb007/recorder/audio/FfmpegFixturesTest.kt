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

class SynthFixturesTest {
    @Test fun writeMechanicalSounds() {
        val dir = File("build/kbcheck").apply { mkdirs() }
        fun save(name: String, pcm: ShortArray) { val w = WavWriter(File(dir, name), com.kb007.recorder.ui.fx.Synth.RATE, 1); w.write(pcm, 0, pcm.size); w.finish() }
        save("fx_tecla.wav", com.kb007.recorder.ui.fx.Synth.keyClick())
        save("fx_eject.wav", com.kb007.recorder.ui.fx.Synth.ejectClack())
        save("fx_perilla.wav", com.kb007.recorder.ui.fx.Synth.knobTick())
        save("fx_motor.wav", com.kb007.recorder.ui.fx.Synth.motorLoop(false))
        save("fx_motor_rapido.wav", com.kb007.recorder.ui.fx.Synth.motorLoop(true))
    }
}
