package com.kb007.recorder.audio.encode

import com.kb007.recorder.data.prefs.AudioFormatType
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Conversión WAV → M4A (AAC) para compartir archivos livianos (p. ej. por WhatsApp). */
object Transcoder {

    data class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val dataBytes: Long)

    /** Lee los chunks RIFF hasta "data" y deja el stream posicionado al inicio del audio. */
    fun readWavHeader(input: InputStream): WavInfo {
        fun readN(n: Int): ByteArray {
            val b = ByteArray(n); var o = 0
            while (o < n) { val r = input.read(b, o, n - o); require(r > 0) { "WAV truncado" }; o += r }
            return b
        }
        val riff = readN(12)
        require(String(riff, 0, 4) == "RIFF" && String(riff, 8, 4) == "WAVE") { "No es un WAV" }
        var sr = 0; var ch = 0; var bits = 0
        while (true) {
            val hdr = readN(8)
            val id = String(hdr, 0, 4)
            val len = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (id == "fmt ") {
                val f = ByteBuffer.wrap(readN(len.toInt())).order(ByteOrder.LITTLE_ENDIAN)
                require(f.getShort(0).toInt() == 1) { "Solo se admite WAV PCM" }
                ch = f.getShort(2).toInt(); sr = f.getInt(4); bits = f.getShort(14).toInt()
            } else if (id == "data") {
                require(sr > 0 && bits == 16) { "Solo se admite PCM de 16 bits" }
                return WavInfo(sr, ch, bits, len)
            } else {
                input.skip(len + (len and 1))
            }
        }
    }

    /**
     * Convierte un WAV PCM16 a M4A. [progress] recibe 0..1.
     * Devuelve el archivo .m4a dentro de [outDir].
     */
    fun wavToM4a(input: InputStream, outDir: File, baseName: String, bitrateKbps: Int = 128, progress: (Float) -> Unit = {}): File {
        BufferedInputStream(input, 64 * 1024).use { bin ->
            val info = readWavHeader(bin)
            val rate = if (info.sampleRate in Adts.SAMPLE_RATES) info.sampleRate else error("Frecuencia no soportada: ${info.sampleRate}")
            val spec = EncodeSpec(AudioFormatType.AAC_M4A, rate, info.channels.coerceIn(1, 2), bitrateKbps)
            outDir.mkdirs()
            val enc = AacAdtsEncoder(File(outDir, "$baseName.aac"), spec)
            val buf = ByteArray(32 * 1024)
            val shorts = ShortArray(buf.size / 2)
            var done = 0L
            var carry = -1 // byte suelto entre lecturas
            try {
                while (true) {
                    val n = bin.read(buf)
                    if (n <= 0) break
                    var i = 0; var k = 0
                    if (carry >= 0) { shorts[k++] = ((buf[0].toInt() shl 8) or carry).toShort(); i = 1; carry = -1 }
                    while (i + 1 < n) { shorts[k++] = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort(); i += 2 }
                    if (i < n) carry = buf[i].toInt() and 0xFF
                    enc.write(shorts, 0, k)
                    done += n
                    if (info.dataBytes > 0) progress((done.toFloat() / info.dataBytes).coerceAtMost(1f))
                }
                return enc.finish()
            } catch (t: Throwable) {
                enc.abort(); throw t
            }
        }
    }
}
