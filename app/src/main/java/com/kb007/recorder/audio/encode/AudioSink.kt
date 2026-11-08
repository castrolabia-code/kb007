package com.kb007.recorder.audio.encode

import com.kb007.recorder.data.prefs.AudioFormatType
import java.io.File

/** Parámetros efectivos de codificación de una grabación. */
data class EncodeSpec(
    val format: AudioFormatType,
    val sampleRate: Int,
    val channels: Int,
    val bitrateKbps: Int,
) {
    /** Bitrate en bps limitado al máximo que admite cada formato a esa frecuencia. */
    val effectiveBitrate: Int
        get() = when (format) {
            // AAC-LC: máx. 6144 bits por canal cada 1024 muestras → 6 * fs por canal.
            AudioFormatType.AAC_M4A -> minOf(bitrateKbps * 1000, 6 * sampleRate * channels)
            AudioFormatType.OPUS_OGG -> (bitrateKbps * 1000).coerceIn(6_000, 510_000)
            AudioFormatType.WAV_PCM -> sampleRate * channels * 16
        }
}

/**
 * Destino de PCM16 intercalado durante una grabación.
 * Escribe a [workFile] en un formato que tolera cortes bruscos; [finish] produce el archivo final.
 */
interface AudioSink {
    val workFile: File
    /** Llamado solo desde el hilo escritor. */
    fun write(pcm: ShortArray, offset: Int, len: Int)
    /** Cierra y devuelve el archivo final (puede ser el mismo [workFile]). */
    fun finish(): File
    /** Cierra sin finalizar (se descarta). */
    fun abort()

    companion object {
        fun create(spec: EncodeSpec, dir: File, baseName: String): AudioSink = when (spec.format) {
            AudioFormatType.WAV_PCM -> WavWriter(File(dir, "$baseName.wav"), spec.sampleRate, spec.channels)
            AudioFormatType.AAC_M4A -> AacAdtsEncoder(File(dir, "$baseName.aac"), spec)
            AudioFormatType.OPUS_OGG -> OpusOggEncoder(File(dir, "$baseName.ogg"), spec)
        }
    }
}
