package com.kb007.recorder.audio.dsp

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Ganancia digital de entrada (Android no expone la ganancia analógica del micrófono).
 * Por encima de ~-3 dBFS aplica una saturación suave (tanh) para evitar recortes duros.
 */
class SoftGain(gainDb: Float = 0f) {
    @Volatile var gainDb: Float = gainDb
        set(value) { field = value; factor = 10.0.pow(value / 20.0).toFloat() }
    private var factor = 10.0.pow(gainDb / 20.0).toFloat()

    fun apply(pcm: ShortArray, len: Int) {
        val g = factor
        if (g == 1f) return
        val knee = 0.7f * 32767f
        for (i in 0 until len) {
            val x = pcm[i] * g
            val ax = abs(x)
            val y = if (ax <= knee) x else {
                val over = (ax - knee) / (32767f - knee)
                val shaped = knee + (32767f - knee) * tanh(over.toDouble()).toFloat()
                if (x < 0) -shaped else shaped
            }
            pcm[i] = y.toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
