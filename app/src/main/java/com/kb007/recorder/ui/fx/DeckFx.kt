package com.kb007.recorder.ui.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.kb007.recorder.audio.encode.WavWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sonidos mecánicos y vibración de la botonera.
 * Todos los sonidos se sintetizan al iniciar (no hay grabaciones de terceros en el APK).
 */
class DeckFx(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Volatile var hapticsEnabled = true
    @Volatile var soundsEnabled = true
        set(value) { field = value; if (!value) stopMotor() }

    enum class Kind { KEY, EJECT, KNOB }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    private val ids = IntArray(3) { 0 }
    private var motorTrack: AudioTrack? = null
    private var motorFast = false

    init {
        Thread({
            runCatching {
                val dir = File(context.cacheDir, "fx").apply { mkdirs() }
                ids[Kind.KEY.ordinal] = pool.load(writeWav(File(dir, "tecla.wav"), Synth.keyClick()), 1)
                ids[Kind.EJECT.ordinal] = pool.load(writeWav(File(dir, "eject.wav"), Synth.ejectClack()), 1)
                ids[Kind.KNOB.ordinal] = pool.load(writeWav(File(dir, "perilla.wav"), Synth.knobTick()), 1)
            }.onFailure { Log.w("KB007/Fx", "No se pudieron generar los sonidos", it) }
        }, "KB007-Fx").start()
    }

    fun press(kind: Kind) {
        if (hapticsEnabled) haptic(kind)
        if (soundsEnabled && ids[kind.ordinal] != 0) {
            val vol = when (kind) { Kind.EJECT -> 0.9f; Kind.KEY -> 0.7f; Kind.KNOB -> 0.5f }
            pool.play(ids[kind.ordinal], vol, vol, 1, 0, 1f)
        }
    }

    private fun haptic(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) when (kind) {
            Kind.KEY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Kind.EJECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Kind.KNOB -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else VibrationEffect.createOneShot(if (kind == Kind.EJECT) 35 else 15, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(effect)
    }

    /**
     * Zumbido suave del motor mientras corre la cinta. Durante la GRABACIÓN no suena:
     * lo captaría el micrófono y quedaría en el archivo.
     */
    fun motor(running: Boolean, fast: Boolean, recording: Boolean) {
        if (!soundsEnabled || !running || recording) { stopMotor(); return }
        if (motorTrack != null && motorFast == fast) return
        stopMotor()
        motorFast = fast
        runCatching {
            val pcm = Synth.motorLoop(fast)
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(Synth.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            t.setVolume(if (fast) 0.35f else 0.22f)
            t.play()
            motorTrack = t
        }.onFailure { Log.w("KB007/Fx", "Motor sin sonido", it) }
    }

    private fun stopMotor() {
        motorTrack?.let { runCatching { it.stop() }; it.release() }
        motorTrack = null
    }

    fun release() {
        stopMotor()
        pool.release()
    }

    private fun writeWav(f: File, pcm: ShortArray): String {
        val w = WavWriter(f, Synth.RATE, 1)
        w.write(pcm, 0, pcm.size)
        w.finish()
        return f.absolutePath
    }
}

/** Síntesis de los sonidos mecánicos. */
internal object Synth {
    const val RATE = 44_100
    private val rnd = Random(7)

    private fun buf(ms: Int) = DoubleArray(RATE * ms / 1000)

    /** Transitorio de plástico/metal: ruido filtrado con caída exponencial. */
    private fun transient(b: DoubleArray, atMs: Double, amp: Double, decayMs: Double, bright: Double) {
        val start = (atMs * RATE / 1000).toInt()
        var lp = 0.0
        for (i in start until b.size) {
            val t = (i - start) / RATE.toDouble() * 1000
            val env = exp(-t / decayMs)
            if (env < 0.001) break
            val n = rnd.nextDouble() * 2 - 1
            lp += (n - lp) * bright // pasa-bajos simple: menos "bright" = más sordo
            b[i] += lp * env * amp
        }
    }

    /** Resonancia del cuerpo (tono que decae). */
    private fun thump(b: DoubleArray, atMs: Double, freq: Double, amp: Double, decayMs: Double) {
        val start = (atMs * RATE / 1000).toInt()
        for (i in start until b.size) {
            val t = (i - start) / RATE.toDouble()
            val env = exp(-t * 1000 / decayMs)
            if (env < 0.001) break
            b[i] += sin(2 * PI * freq * t) * env * amp
        }
    }

    private fun toPcm(b: DoubleArray, gain: Double = 0.9): ShortArray {
        val peak = b.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(b.size) { (b[it] / peak * gain * 32767).toInt().toShort() }
    }

    /** Clic de tecla: contacto + traba del mecanismo + golpe sordo del chasis. */
    fun keyClick(): ShortArray {
        val b = buf(110)
        transient(b, 0.0, 0.8, 2.5, 0.55)
        thump(b, 0.0, 180.0, 0.5, 18.0)
        transient(b, 28.0, 0.55, 3.5, 0.35)    // traba
        thump(b, 28.0, 420.0, 0.25, 10.0)
        return toPcm(b, 0.8)
    }

    /** "Clack" de expulsión: golpe fuerte, resorte y traqueteo de la tapa. */
    fun ejectClack(): ShortArray {
        val b = buf(380)
        transient(b, 0.0, 1.0, 4.0, 0.6)
        thump(b, 0.0, 120.0, 0.8, 35.0)
        transient(b, 45.0, 0.9, 6.0, 0.45)
        thump(b, 45.0, 260.0, 0.5, 25.0)
        for (k in 0 until 5) transient(b, 90.0 + k * 22 + rnd.nextDouble() * 6, 0.35 / (k + 1), 2.0, 0.7) // traqueteo
        // Resorte: barrido descendente muy suave
        for (i in (60 * RATE / 1000) until b.size) {
            val t = (i - 60 * RATE / 1000) / RATE.toDouble()
            b[i] += sin(2 * PI * (900 - 600 * t) * t) * exp(-t * 18) * 0.06
        }
        return toPcm(b, 0.9)
    }

    fun knobTick(): ShortArray {
        val b = buf(40)
        transient(b, 0.0, 0.7, 1.5, 0.75)
        thump(b, 0.0, 900.0, 0.2, 5.0)
        return toPcm(b, 0.6)
    }

    /**
     * Loop de motor (ciclo exacto para que no haga clic al repetirse): fundamental grave,
     * armónicos, zumbido de escobillas y un leve "wow" de la correa.
     */
    fun motorLoop(fast: Boolean): ShortArray {
        val base = if (fast) 100.0 else 50.0   // Hz (múltiplo exacto de 1/0,5 s)
        val seconds = 0.5
        val n = (RATE * seconds).toInt()
        val b = DoubleArray(n)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val wow = 1 + 0.03 * sin(2 * PI * 2 * t)  // 2 Hz, ciclo exacto en 0,5 s
            var x = 0.0
            x += sin(2 * PI * base * t) * 0.5
            x += sin(2 * PI * base * 2 * t) * 0.3
            x += sin(2 * PI * base * 3 * t) * 0.15
            x += sin(2 * PI * (if (fast) 2400.0 else 1200.0) * t) * 0.04 // silbido de escobillas
            lp += ((rnd.nextDouble() * 2 - 1) - lp) * 0.05
            x += lp * 0.25
            b[i] = x * wow
        }
        return toPcm(b, 0.5)
    }
}
