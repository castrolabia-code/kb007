package com.kb007.recorder.audio.engine

import com.kb007.recorder.data.prefs.TriggerType

enum class RecMode { IDLE, RECORDING, PAUSED }

enum class PauseReason(val label: String) {
    USER("Pausa"),
    CALL("Pausado por llamada"),
    FOCUS("Pausado: otra app tomó el audio"),
    SILENCED("Pausado: otra app usa el micrófono"),
}

/** Estado observable del motor (lo consumen la UI, el servicio y la notificación). */
data class EngineState(
    /** Modo palabra clave activo (micrófono abierto escuchando). */
    val listening: Boolean = false,
    val micOpen: Boolean = false,
    val mode: RecMode = RecMode.IDLE,
    val recordingId: Long? = null,
    val title: String? = null,
    val trigger: TriggerType? = null,
    val elapsedMs: Long = 0,
    /** SystemClock.elapsedRealtime() equivalente al inicio (para el cronómetro de la notificación). */
    val chronoBase: Long = 0,
    val pauseReason: PauseReason? = null,
    /** Hay una grabación finalizándose (no cortar el servicio todavía). */
    val finalizing: Boolean = false,
    /** En llamada (escucha suspendida). */
    val inCall: Boolean = false,
    /** Si el detector de palabra clave está realmente operativo (modelo cargado). */
    val detectorReady: Boolean = false,
) {
    val isRecording get() = mode == RecMode.RECORDING
    val isActive get() = listening || mode != RecMode.IDLE || finalizing
}

/** Eventos puntuales para avisos (toasts, sonidos, vibración). */
sealed interface EngineEvent {
    data class Saved(val id: Long, val title: String) : EngineEvent
    data class KeywordTriggered(val text: String) : EngineEvent
    data class Message(val text: String, val isError: Boolean = false) : EngineEvent
}
