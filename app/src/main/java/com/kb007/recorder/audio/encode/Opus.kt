package com.kb007.recorder.audio.encode

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Opus {
    /** Muestras (a 48 kHz) que representa un paquete Opus, según su byte TOC (RFC 6716 §3.1). */
    fun packetSamples48k(packet: ByteArray): Int {
        if (packet.isEmpty()) return 0
        val toc = packet[0].toInt() and 0xFF
        val config = toc shr 3
        // Duración de un frame en décimas de ms
        val frameTenthsMs = when {
            config < 12 -> intArrayOf(100, 200, 400, 600)[config % 4]      // SILK
            config < 16 -> intArrayOf(100, 200)[config % 2]                // Híbrido
            else -> intArrayOf(25, 50, 100, 200)[config % 4]               // CELT
        }
        val frames = when (toc and 3) {
            0 -> 1
            1, 2 -> 2
            else -> if (packet.size > 1) packet[1].toInt() and 0x3F else 0
        }
        return frames * frameTenthsMs * 48 / 10
    }

    /** Cabecera de identificación "OpusHead" (RFC 7845 §5.1). */
    fun head(channels: Int, preSkip: Int, inputRate: Int): ByteArray =
        ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray()); put(1); put(channels.toByte())
            putShort(preSkip.toShort()); putInt(inputRate); putShort(0); put(0)
        }.array()

    /** Cabecera de comentarios "OpusTags". */
    fun tags(vendor: String = "KB-007"): ByteArray {
        val v = vendor.toByteArray()
        return ByteBuffer.allocate(8 + 4 + v.size + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusTags".toByteArray()); putInt(v.size); put(v); putInt(0)
        }.array()
    }

    /** Frecuencias de entrada que acepta el encoder Opus de Android. */
    val SUPPORTED_RATES = intArrayOf(8000, 12000, 16000, 24000, 48000)

    /** Frecuencia de captura efectiva para Opus (las no soportadas se suben a 48 kHz). */
    fun captureRateFor(requested: Int): Int = if (requested in SUPPORTED_RATES) requested else 48_000
}
