package com.kb007.recorder.audio.dsp

/** Cuenta cuánto tiempo seguido el nivel estuvo por debajo de un umbral. */
class SilenceDetector(var thresholdDb: Float = -45f) {
    var silentMs: Long = 0
        private set

    fun feed(level: Level, blockMs: Long) {
        silentMs = if (level.rmsDb < thresholdDb) silentMs + blockMs else 0
    }

    fun reset() { silentMs = 0 }
}
