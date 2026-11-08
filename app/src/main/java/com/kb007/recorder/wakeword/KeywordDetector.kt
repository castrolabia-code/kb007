package com.kb007.recorder.wakeword

/** Eventos del detector de palabra clave. */
sealed interface KeywordEvent {
    /** Se reconoció la palabra de activación con la confianza indicada. */
    data class Triggered(val text: String, val confidence: Float) : KeywordEvent
    /** Se reconoció la palabra de detención. */
    data class StopWord(val text: String, val confidence: Float) : KeywordEvent
    /** Texto parcial/final escuchado (para el modo "probar palabra clave"). */
    data class Heard(val text: String, val confidence: Float, val accepted: Boolean) : KeywordEvent
    data class Error(val message: String) : KeywordEvent
}

data class KeywordConfig(
    val keyword: String,
    val stopWord: String?,
    /** Confianza mínima (0..1) promedio de las palabras de la frase. */
    val threshold: Float,
)

/**
 * Detector alimentado con PCM16 mono a 16 kHz desde el stream único de micrófono.
 * [feed] se llama desde el hilo de captura: debe encolar y volver enseguida.
 */
interface KeywordDetector {
    fun start(config: KeywordConfig, listener: (KeywordEvent) -> Unit): Boolean
    fun feed(pcm16k: ShortArray, len: Int)
    fun stop()
}

/** Implementación vacía (sin modelo cargado). */
object NoKeywordDetector : KeywordDetector {
    override fun start(config: KeywordConfig, listener: (KeywordEvent) -> Unit) = false
    override fun feed(pcm16k: ShortArray, len: Int) = Unit
    override fun stop() = Unit
}
