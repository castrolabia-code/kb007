package com.kb007.recorder.audio.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer

object Remuxer {
    /** Lee frames ADTS completos de un stream. Devuelve la cantidad de frames leídos. */
    fun forEachAdtsFrame(input: InputStream, onFrame: (data: ByteArray, len: Int, sampleRate: Int, channels: Int) -> Unit): Int {
        val hdr = ByteArray(9)
        var buf = ByteArray(8192)
        var count = 0
        while (true) {
            if (!readFully(input, hdr, 7)) break
            val b1 = hdr[1].toInt() and 0xFF
            if ((hdr[0].toInt() and 0xFF) != 0xFF || (b1 and 0xF0) != 0xF0) break
            val b2 = hdr[2].toInt() and 0xFF
            val b3 = hdr[3].toInt() and 0xFF
            val b4 = hdr[4].toInt() and 0xFF
            val b5 = hdr[5].toInt() and 0xFF
            val srIdx = (b2 shr 2) and 0xF
            val ch = ((b2 and 1) shl 2) or (b3 shr 6)
            val frameLen = ((b3 and 3) shl 11) or (b4 shl 3) or (b5 shr 5)
            val hdrLen = if (b1 and 1 == 1) 7 else 9
            if (hdrLen == 9 && !readFully(input, hdr, 2)) break
            val dataLen = frameLen - hdrLen
            if (dataLen <= 0 || srIdx >= Adts.SAMPLE_RATES.size) break
            if (buf.size < dataLen) buf = ByteArray(dataLen)
            if (!readFully(input, buf, dataLen)) break // frame truncado por un corte: se descarta
            onFrame(buf, dataLen, Adts.SAMPLE_RATES[srIdx], ch)
            count++
        }
        return count
    }

    /** Frecuencia y canales de la primera cabecera ADTS del archivo. */
    fun probeAdts(file: File): Pair<Int, Int>? = runCatching {
        FileInputStream(file).use { input ->
            val h = ByteArray(7)
            if (!readFully(input, h, 7)) return null
            if ((h[0].toInt() and 0xFF) != 0xFF || (h[1].toInt() and 0xF0) != 0xF0) return null
            val b2 = h[2].toInt() and 0xFF
            val srIdx = (b2 shr 2) and 0xF
            val ch = ((b2 and 1) shl 2) or ((h[3].toInt() and 0xFF) shr 6)
            if (srIdx >= Adts.SAMPLE_RATES.size || ch == 0) null else Adts.SAMPLE_RATES[srIdx] to ch
        }
    }.getOrNull()

    private fun readFully(input: InputStream, b: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = input.read(b, off, len - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    /**
     * Copia los frames AAC de un archivo ADTS a un contenedor MP4 (.m4a) sin recodificar.
     * Devuelve la duración en ms. Lanza excepción si no hay audio válido.
     */
    fun adtsToM4a(adts: File, m4a: File): Long {
        val (sr, ch) = probeAdts(adts) ?: (0 to 0)
        require(sr > 0) { "Archivo ADTS sin audio: ${adts.name}" }

        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(Adts.audioSpecificConfig(sr, ch)))
        }
        m4a.delete()
        val muxer = MediaMuxer(m4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val track = muxer.addTrack(fmt)
        muxer.start()
        val info = MediaCodec.BufferInfo()
        var bb = ByteBuffer.allocate(8192)
        var frames = 0L
        BufferedInputStream(FileInputStream(adts), 64 * 1024).use { input ->
            forEachAdtsFrame(input) { data, len, _, _ ->
                if (bb.capacity() < len) bb = ByteBuffer.allocate(len)
                bb.clear(); bb.put(data, 0, len); bb.flip()
                info.set(0, len, frames * 1024L * 1_000_000L / sr, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(track, bb, info)
                frames++
            }
        }
        muxer.stop(); muxer.release()
        return frames * 1024L * 1000L / sr
    }
}
