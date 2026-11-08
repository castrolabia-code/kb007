package com.kb007.recorder.audio.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Nivel de un bloque PCM16 en dBFS (RMS y pico). */
data class Level(val rmsDb: Float, val peakDb: Float) {
    /** Normalizado 0..1 en una escala de -60..0 dBFS (para el VU). */
    val normalized: Float get() = ((rmsDb + 60f) / 60f).coerceIn(0f, 1f)
    val clipping: Boolean get() = peakDb > -0.5f
    companion object { val SILENT = Level(-90f, -90f) }
}

object LevelMeter {
    fun measure(pcm: ShortArray, len: Int): Level {
        if (len <= 0) return Level.SILENT
        var sum = 0.0
        var peak = 0
        for (i in 0 until len) {
            val v = pcm[i].toInt()
            sum += (v * v).toDouble()
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
        }
        val rms = sqrt(sum / len)
        return Level(toDb(rms), toDb(peak.toDouble()))
    }

    fun toDb(amplitude: Double): Float = (20 * log10(max(amplitude, 1.0) / 32768.0)).toFloat()
}
