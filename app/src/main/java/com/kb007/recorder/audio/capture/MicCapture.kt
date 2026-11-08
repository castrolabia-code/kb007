package com.kb007.recorder.audio.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import com.kb007.recorder.audio.dsp.SoftGain
import com.kb007.recorder.data.prefs.MicSource

/** Configuración del stream único de micrófono. */
data class CaptureConfig(
    val sampleRate: Int,
    val channels: Int,
    val source: MicSource,
    val noiseSuppressor: Boolean,
    val autoGain: Boolean,
    val echoCanceler: Boolean,
)

class MicBusyException(msg: String) : Exception(msg)

/**
 * Dueño exclusivo del [AudioRecord]. Lee bloques de ~20 ms en un hilo de prioridad de audio
 * y los entrega (ya con ganancia digital aplicada) a [onPcm]. El callback debe ser rápido:
 * el motor solo copia y encola.
 */
class MicCapture(
    val config: CaptureConfig,
    private val gain: SoftGain,
    private val onPcm: (pcm: ShortArray, len: Int) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val effects = mutableListOf<AudioEffect>()

    val sessionId: Int get() = record?.audioSessionId ?: 0

    /** Canales que efectivamente entregó el dispositivo (puede ser mono aunque se pida estéreo). */
    var actualChannels: Int = config.channels
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val chMask = if (config.channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(config.sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw IllegalStateException("Formato no soportado: ${config.sampleRate} Hz / ${config.channels} canales")
        val bufBytes = maxOf(minBuf * 4, config.sampleRate * config.channels * 2 / 2) // >= 500 ms
        val rec = AudioRecord(sourceFor(config.source), config.sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw MicBusyException("No se pudo abrir el micrófono (¿lo está usando otra app?)")
        }
        actualChannels = rec.channelCount
        attachEffects(rec.audioSessionId)
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            releaseEffects(); rec.release()
            throw MicBusyException("El micrófono está ocupado por otra app")
        }
        record = rec
        running = true
        thread = Thread({ loop(rec) }, "KB007-Mic").apply { start() }
    }

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val block = config.sampleRate / 50 * actualChannels // 20 ms
        val buf = ShortArray(block)
        try {
            while (running) {
                val n = rec.read(buf, 0, block)
                if (n < 0) {
                    if (!running) break
                    throw IllegalStateException("Error de lectura del micrófono ($n)")
                }
                if (n == 0) continue
                gain.apply(buf, n)
                onPcm(buf, n)
            }
        } catch (t: Throwable) {
            if (running) { Log.e(TAG, "Captura interrumpida", t); onError(t) }
        }
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        thread?.join(1000)
        thread = null
        releaseEffects()
        runCatching { record?.release() }
        record = null
    }

    private fun attachEffects(session: Int) {
        if (config.noiseSuppressor && NoiseSuppressor.isAvailable())
            NoiseSuppressor.create(session)?.let { it.enabled = true; effects += it }
        if (config.autoGain && AutomaticGainControl.isAvailable())
            AutomaticGainControl.create(session)?.let { it.enabled = true; effects += it }
        if (config.echoCanceler && AcousticEchoCanceler.isAvailable())
            AcousticEchoCanceler.create(session)?.let { it.enabled = true; effects += it }
    }

    private fun releaseEffects() {
        effects.forEach { runCatching { it.release() } }
        effects.clear()
    }

    companion object {
        private const val TAG = "KB007/Mic"

        fun sourceFor(s: MicSource): Int = when (s) {
            MicSource.STANDARD -> MediaRecorder.AudioSource.MIC
            MicSource.VOICE -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            MicSource.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
            MicSource.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
        }
    }
}
