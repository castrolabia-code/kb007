package com.kb007.recorder.audio.playback

import android.content.Context
import android.media.audiofx.Visualizer
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kb007.recorder.data.db.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Bobinado rápido en curso (sin audio, como un REW/FF real). */
enum class Wind { NONE, REW, FF }

data class PlayerState(
    val loaded: Recording? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val wind: Wind = Wind.NONE,
) {
    val atEnd get() = durationMs > 0 && positionMs >= durationMs - 150
    val atStart get() = positionMs <= 50
    val fraction get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Reproducción de grabaciones con ExoPlayer. Debe usarse desde el hilo principal.
 * REW/FF mueven la posición a ~20x sin audio, como el bobinado de un cassette.
 */
class PlayerController(private val context: Context, private val scope: CoroutineScope) {
    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _levelDb = MutableStateFlow(-90f)
    /** Nivel aproximado de la reproducción (dBFS) para el VU. */
    val levelDb: StateFlow<Float> = _levelDb.asStateFlow()

    private var ticker: Job? = null
    private var windJob: Job? = null
    private var visualizer: Visualizer? = null
    private var onWindFinished: (() -> Unit)? = null

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true, // pausa sola si entra una llamada u otra app toma el audio
        )
        p.setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (isPlaying) startTicker()
            }
            override fun onPlaybackStateChanged(st: Int) {
                if (st == Player.STATE_READY) _state.update { it.copy(durationMs = max(0, p.duration)) }
                if (st == Player.STATE_ENDED) {
                    p.pause()
                    _state.update { it.copy(playing = false, positionMs = it.durationMs) }
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) = attachVisualizer(audioSessionId)
        })
        player = p
    }

    fun load(rec: Recording, atEnd: Boolean = false) {
        stopWind()
        val p = ensurePlayer()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(rec.uri)))
        p.prepare()
        val pos = if (atEnd) rec.durationMs else 0L
        p.seekTo(pos)
        _state.value = PlayerState(loaded = rec, positionMs = pos, durationMs = rec.durationMs)
    }

    /** Actualiza los metadatos del cassette cargado (p. ej. tras renombrar) sin recargar el audio. */
    fun updateLoaded(rec: Recording) {
        val cur = _state.value.loaded ?: return
        if (cur.id != rec.id) return
        if (cur.uri != rec.uri) {
            // El archivo cambió de nombre: recargamos manteniendo la posición.
            val pos = player?.currentPosition ?: 0
            player?.setMediaItem(MediaItem.fromUri(Uri.parse(rec.uri)), pos)
            player?.prepare()
        }
        _state.update { it.copy(loaded = rec) }
    }

    fun eject() {
        stopWind()
        player?.stop()
        player?.clearMediaItems()
        _state.value = PlayerState()
    }

    fun play() {
        val p = player ?: return
        if (_state.value.loaded == null) return
        stopWind()
        if (_state.value.atEnd) p.seekTo(0)
        p.play()
    }

    fun pause() { player?.pause(); refreshPosition() }

    /** STOP de cassette: detiene pero no rebobina. */
    fun stop() { stopWind(); player?.pause(); refreshPosition() }

    /** Inicia bobinado. [then] se ejecuta al llegar al extremo. */
    fun wind(direction: Wind, then: (() -> Unit)? = null) {
        val p = player ?: return
        if (_state.value.loaded == null || direction == Wind.NONE) return
        p.pause()
        windJob?.cancel()
        onWindFinished = then
        _state.update { it.copy(wind = direction) }
        windJob = scope.launch(Dispatchers.Main) {
            val dur = _state.value.durationMs
            // ~20x, con un mínimo para que grabaciones largas no tarden una eternidad
            val stepMs = max(800L, dur / 120)
            var pos = p.currentPosition
            while (isActive) {
                delay(40)
                pos = if (direction == Wind.FF) (pos + stepMs).coerceAtMost(dur) else (pos - stepMs).coerceAtLeast(0)
                _state.update { it.copy(positionMs = pos) }
                if (pos == 0L || pos == dur) break
            }
            p.seekTo(pos)
            _state.update { it.copy(wind = Wind.NONE, positionMs = pos) }
            onWindFinished?.invoke(); onWindFinished = null
        }
    }

    fun stopWind() {
        if (windJob == null) return
        windJob?.cancel(); windJob = null; onWindFinished = null
        player?.seekTo(_state.value.positionMs)
        _state.update { it.copy(wind = Wind.NONE) }
    }

    private fun refreshPosition() {
        player?.let { p -> _state.update { it.copy(positionMs = p.currentPosition.coerceAtLeast(0)) } }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive && player?.isPlaying == true) {
                refreshPosition()
                delay(50)
            }
            refreshPosition()
            _levelDb.value = -90f
        }
    }

    /** Visualizer sobre la sesión de ExoPlayer para mover la aguja al reproducir. */
    private fun attachVisualizer(session: Int) {
        runCatching { visualizer?.release() }
        visualizer = null
        if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) return
        visualizer = runCatching {
            Visualizer(session).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, wave: ByteArray, rate: Int) {
                        var sum = 0.0
                        for (b in wave) { val x = ((b.toInt() and 0xFF) - 128) / 128.0; sum += x * x }
                        val rms = sqrt(sum / wave.size)
                        _levelDb.value = if (rms < 1e-5) -90f else (20 * log10(rms)).toFloat()
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) = Unit
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        }.onFailure { Log.w("KB007/Player", "Visualizer no disponible", it) }.getOrNull()
    }

    fun release() {
        runCatching { visualizer?.release() }
        player?.release(); player = null
    }
}
