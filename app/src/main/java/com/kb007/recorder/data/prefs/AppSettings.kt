package com.kb007.recorder.data.prefs

/** Formatos de salida disponibles. */
enum class AudioFormatType(
    val label: String,
    val extension: String,
    val mime: String,
    val compressed: Boolean,
) {
    AAC_M4A("M4A / AAC", "m4a", "audio/mp4", true),
    OPUS_OGG("OGG / Opus", "ogg", "audio/ogg", true),
    WAV_PCM("WAV PCM", "wav", "audio/wav", false);
}

/** Fuente de micrófono (se traduce a MediaRecorder.AudioSource). */
enum class MicSource(val label: String) {
    STANDARD("Estándar"),
    VOICE("Optimizado para voz"),
    UNPROCESSED("Sin procesar"),
    CAMCORDER("Cámara / externo"),
}

/** Qué hacer cuando entra una llamada durante la grabación. */
enum class CallPolicy(val label: String) {
    PAUSE("Pausar y reanudar al cortar"),
    SAVE_AND_STOP("Guardar y detener"),
}

/** Destino final de los archivos. */
enum class StorageTarget(val label: String) {
    APP_PRIVATE("Carpeta de la app"),
    PUBLIC_MUSIC("Música/KB-007 (pública)"),
    CUSTOM_TREE("Carpeta elegida…"),
}

/** Origen de una grabación. */
enum class TriggerType(val label: String) { MANUAL("Manual"), KEYWORD("Palabra clave") }

val SAMPLE_RATES = listOf(16_000, 22_050, 44_100, 48_000)
val BITRATES_KBPS = listOf(64, 96, 128, 192, 256)

/** Preset rápido: rellena varios campos de audio de una vez. */
enum class AudioPreset(
    val label: String,
    val format: AudioFormatType,
    val sampleRate: Int,
    val bitrateKbps: Int,
    val channels: Int,
    val source: MicSource,
    val ns: Boolean,
    val agc: Boolean,
) {
    VOICE_NOTE("Voz / nota rápida", AudioFormatType.AAC_M4A, 22_050, 64, 1, MicSource.VOICE, true, true),
    MEETING("Reunión", AudioFormatType.AAC_M4A, 44_100, 96, 1, MicSource.STANDARD, true, true),
    MUSIC("Música / ensayo", AudioFormatType.AAC_M4A, 48_000, 192, 2, MicSource.UNPROCESSED, false, false),
    MAX("Máxima calidad", AudioFormatType.WAV_PCM, 48_000, 256, 2, MicSource.UNPROCESSED, false, false),
}

/** Todas las preferencias de la app en un objeto inmutable. */
data class AppSettings(
    // --- Audio ---
    val format: AudioFormatType = AudioFormatType.AAC_M4A,
    val sampleRate: Int = 44_100,
    val bitrateKbps: Int = 128,
    val channels: Int = 1,
    val micSource: MicSource = MicSource.STANDARD,
    val noiseSuppressor: Boolean = false,
    val autoGain: Boolean = false,
    val echoCanceler: Boolean = false,
    /** Ganancia digital en dB (-12 .. +24). */
    val gainDb: Float = 0f,
    /** Corte por silencio en segundos (0 = desactivado). */
    val silenceStopSec: Int = 0,
    /** Umbral de silencio en dBFS. */
    val silenceThresholdDb: Float = -45f,
    /** Duración máxima en minutos (0 = sin límite). */
    val maxDurationMin: Int = 0,
    val callPolicy: CallPolicy = CallPolicy.PAUSE,
    // --- Almacenamiento ---
    val storageTarget: StorageTarget = StorageTarget.APP_PRIVATE,
    val customTreeUri: String? = null,
    /** Tokens: {fecha} {hora} {modo} {n} */
    val namePattern: String = "KB007_{fecha}_{hora}",
    // --- Interfaz ---
    val themeId: String = "metal80",
    val mechSounds: Boolean = true,
    val haptics: Boolean = true,
    // --- Palabra clave ---
    val keyword: String = "grabadora",
    val stopKeyword: String = "",
    /** 0..1: confianza mínima por palabra reconocida. */
    val keywordThreshold: Float = 0.6f,
    val preRollSec: Int = 3,
    /** Silencio tras el cual se corta una grabación disparada por palabra clave (0 = no). */
    val keywordSilenceStopSec: Int = 8,
    val keywordBeep: Boolean = true,
    val keywordVibrate: Boolean = true,
    /** Estado persistido del modo escucha (para avisar tras un reinicio). */
    val listeningEnabled: Boolean = false,
    val resumeAfterBoot: Boolean = true,
    val onboardingDone: Boolean = false,
)
