package com.kb007.recorder.ui.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Sonidos mecánicos y vibración de la botonera. */
class DeckFx(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Volatile var hapticsEnabled = true
    @Volatile var soundsEnabled = true

    enum class Kind { KEY, EJECT, KNOB }

    fun press(kind: Kind) {
        if (hapticsEnabled) haptic(kind)
    }

    private fun haptic(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> when (kind) {
                Kind.KEY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Kind.EJECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                Kind.KNOB -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            else -> VibrationEffect.createOneShot(if (kind == Kind.EJECT) 35 else 15, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        v.vibrate(effect)
    }

    /** Zumbido del motor mientras la cinta corre (se implementa en la fase de sonidos). */
    fun motor(running: Boolean, fast: Boolean, recording: Boolean) = Unit

    fun release() = Unit
}
