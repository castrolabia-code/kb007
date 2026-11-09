package com.kb007.recorder.ui.deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kb007.recorder.KbApp
import com.kb007.recorder.audio.engine.EngineEvent
import com.kb007.recorder.audio.engine.EngineState
import com.kb007.recorder.audio.engine.RecMode
import com.kb007.recorder.audio.playback.PlayerState
import com.kb007.recorder.audio.playback.Wind
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.prefs.TriggerType
import com.kb007.recorder.service.RecorderService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.exp

enum class DeckKey { REC, PLAY, REW, FF, STOP, PAUSE }
enum class Transport { STOP, PLAY, RECORD, REW, FF }

/** Todo lo que la pantalla del aparato necesita para dibujarse. */
data class DeckUi(
    val transport: Transport = Transport.STOP,
    val paused: Boolean = false,
    val locked: Set<DeckKey> = emptySet(),
    val tapeTitle: String? = null,
    val hasTape: Boolean = false,
    /** 0 = toda la cinta en el carrete izquierdo, 1 = toda en el derecho. */
    val progress: Float = TAPE_MIN,
    /** Velocidad de cinta relativa (1 = reproducción, ±14 = bobinado, 0 = detenida). */
    val tapeSpeed: Float = 0f,
    val timeMs: Long = 0,
    val durationMs: Long = 0,
    val recording: Boolean = false,
    val keywordTriggered: Boolean = false,
    val listening: Boolean = false,
    val detectorReady: Boolean = false,
    val micOpen: Boolean = false,
    val status: String = "",
    /** Cambia cada vez que se inserta un cassette distinto (dispara la animación de carga). */
    val tapeId: Long = 0,
    /** Estética del cassette cargado (cada grabación tiene la suya). */
    val tapeStyle: CassetteStyle? = null,
) {
    companion object {
        const val TAPE_MIN = 0.04f
        const val TAPE_SPAN = 0.90f
    }
}

class DeckViewModel(app: Application) : AndroidViewModel(app), DeckActions {
    private val c = (app as KbApp).container
    private val engine = c.engine
    private val player = c.player

    /** Pausa de reproducción (la de grabación la lleva el motor). */
    private val playPaused = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val level get() = engine.level
    val playLevelDb get() = player.levelDb
    val settings get() = c.settings.state

    val ui: StateFlow<DeckUi> = combine(engine.state, player.state, playPaused) { e, p, pp -> build(e, p, pp) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeckUi())

    init {
        viewModelScope.launch {
            engine.events.collect { ev ->
                when (ev) {
                    is EngineEvent.Saved -> {
                        // Al terminar de grabar, el cassette recién grabado queda cargado (al final, como en la realidad).
                        c.recordings.get(ev.id)?.let { player.load(it, atEnd = true) }
                        _messages.tryEmit("Guardado: ${ev.title}")
                    }
                    is EngineEvent.Message -> _messages.tryEmit(ev.text)
                    is EngineEvent.KeywordTriggered -> _messages.tryEmit("Palabra clave detectada: «${ev.text}»")
                }
            }
        }
    }

    private fun build(e: EngineState, p: PlayerState, pp: Boolean): DeckUi {
        val recording = e.mode != RecMode.IDLE
        val transport = when {
            recording -> Transport.RECORD
            p.wind == Wind.FF -> Transport.FF
            p.wind == Wind.REW -> Transport.REW
            p.playing || pp -> Transport.PLAY
            else -> Transport.STOP
        }
        val paused = (recording && e.mode == RecMode.PAUSED) || (!recording && pp)
        val locked = buildSet {
            when (transport) {
                Transport.RECORD -> { add(DeckKey.REC); add(DeckKey.PLAY) }
                Transport.PLAY -> add(DeckKey.PLAY)
                Transport.REW -> add(DeckKey.REW)
                Transport.FF -> add(DeckKey.FF)
                Transport.STOP -> Unit
            }
            if (paused) add(DeckKey.PAUSE)
        }
        val progress = when {
            recording -> DeckUi.TAPE_MIN + DeckUi.TAPE_SPAN * (1f - exp(-e.elapsedMs / 480_000f))
            p.loaded != null -> DeckUi.TAPE_MIN + DeckUi.TAPE_SPAN * p.fraction
            else -> DeckUi.TAPE_MIN
        }
        val speed = when {
            paused -> 0f
            transport == Transport.RECORD || transport == Transport.PLAY -> 1f
            transport == Transport.FF -> 14f
            transport == Transport.REW -> -14f
            else -> 0f
        }
        val status = when {
            recording && e.mode == RecMode.PAUSED -> e.pauseReason?.label?.uppercase() ?: "PAUSA"
            recording -> if (e.trigger == TriggerType.KEYWORD) "GRABANDO · CLAVE" else "GRABANDO"
            e.finalizing -> "GUARDANDO…"
            transport == Transport.PLAY && pp -> "PAUSA"
            transport == Transport.PLAY -> "REPRODUCIENDO"
            transport == Transport.FF -> "AVANCE RÁPIDO"
            transport == Transport.REW -> "REBOBINANDO"
            e.listening && e.inCall -> "ESCUCHA EN PAUSA (LLAMADA)"
            e.listening -> "ESPERANDO PALABRA CLAVE"
            p.loaded != null -> "LISTO"
            else -> "SIN CASSETTE"
        }
        return DeckUi(
            transport = transport, paused = paused, locked = locked,
            tapeTitle = if (recording) e.title else p.loaded?.title,
            hasTape = recording || p.loaded != null,
            progress = progress.coerceIn(0f, 1f), tapeSpeed = speed,
            timeMs = if (recording) e.elapsedMs else p.positionMs,
            durationMs = if (recording) 0 else p.durationMs,
            recording = recording, keywordTriggered = recording && e.trigger == TriggerType.KEYWORD,
            listening = e.listening, detectorReady = e.detectorReady, micOpen = e.micOpen,
            status = status,
            tapeId = if (recording) -(e.recordingId ?: 1) else (p.loaded?.id ?: 0),
            tapeStyle = when {
                recording && e.recordingId != null -> CassetteStyle.forRecording(e.recordingId, -1)
                p.loaded != null -> CassetteStyle.forRecording(p.loaded.id, p.loaded.cassetteStyle)
                else -> null
            },
        )
    }

    /** Pulsación de una tecla de la botonera. */
    override fun levelDb(): Float =
        if (ui.value.transport == Transport.PLAY) player.levelDb.value else engine.level.value.rmsDb

    override fun onKey(key: DeckKey) {
        val u = ui.value
        val ctx = getApplication<Application>()
        when (key) {
            DeckKey.REC -> if (!u.recording) {
                playPaused.value = false
                player.eject()
                RecorderService.send(ctx, RecorderService.ACTION_RECORD)
            }
            DeckKey.PLAY -> when {
                u.recording -> Unit
                u.transport == Transport.PLAY && u.paused -> { playPaused.value = false; player.play() }
                player.state.value.loaded != null -> startPlay()
                else -> viewModelScope.launch {
                    // Sin cassette: cargamos la grabación más reciente.
                    val latest = c.recordings.finished.first().firstOrNull()
                    if (latest == null) _messages.tryEmit("No hay grabaciones todavía. Pulsá REC para grabar.")
                    else { player.load(latest); player.play() }
                }
            }
            DeckKey.PAUSE -> when {
                u.recording -> RecorderService.send(ctx, RecorderService.ACTION_TOGGLE_PAUSE)
                u.transport == Transport.PLAY && !u.paused -> { player.pause(); playPaused.value = true }
                u.transport == Transport.PLAY && u.paused -> { playPaused.value = false; player.play() }
            }
            DeckKey.STOP -> when {
                u.recording -> RecorderService.send(ctx, RecorderService.ACTION_STOP_REC)
                u.transport != Transport.STOP -> { playPaused.value = false; player.stop() }
                u.hasTape -> player.eject() // segundo STOP = EJECT
            }
            DeckKey.REW, DeckKey.FF -> {
                if (u.recording) return
                if (player.state.value.loaded == null) { _messages.tryEmit("Cargá un cassette desde la LISTA"); return }
                playPaused.value = false
                val dir = if (key == DeckKey.FF) Wind.FF else Wind.REW
                if (u.transport == Transport.REW && dir == Wind.REW || u.transport == Transport.FF && dir == Wind.FF) player.stopWind()
                else player.wind(dir)
            }
        }
    }

    /** PLAY con la cinta al final: rebobina sola y después reproduce. */
    private fun startPlay() {
        playPaused.value = false
        if (player.state.value.atEnd) player.wind(Wind.REW) { player.play() } else player.play()
    }

    fun load(rec: Recording) {
        if (ui.value.recording) { _messages.tryEmit("Detené la grabación antes de cargar otro cassette"); return }
        playPaused.value = false
        player.load(rec)
    }

    override fun toggleListening() {
        val ctx = getApplication<Application>()
        RecorderService.send(ctx, if (ui.value.listening) RecorderService.ACTION_LISTEN_OFF else RecorderService.ACTION_LISTEN_ON)
    }

    fun markBatteryAsked() = viewModelScope.launch { c.settings.update { it.copy(batteryAsked = true) } }

    /** Teclas que no hacen nada en el estado actual (rebotan sin trabarse). */
    fun isInert(key: DeckKey): Boolean = when (key) {
        DeckKey.REC -> ui.value.recording
        else -> false
    }
}
