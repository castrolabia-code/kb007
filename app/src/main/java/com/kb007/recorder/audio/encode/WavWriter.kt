package com.kb007.recorder.audio.encode

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV PCM16. La cabecera se reescribe cada ~1 s con los tamaños actuales, así un cierre
 * inesperado deja un archivo reproducible (y [WavWriter.repair] corrige el resto).
 */
class WavWriter(override val workFile: File, private val sampleRate: Int, private val channels: Int) : AudioSink {
    private val raf = RandomAccessFile(workFile, "rw")
    private var dataBytes = 0L
    private var lastHeaderAt = 0L
    private var bytes = ByteArray(8192)

    init {
        raf.setLength(0)
        raf.write(header(sampleRate, channels, 0))
    }

    override fun write(pcm: ShortArray, offset: Int, len: Int) {
        val need = len * 2
        if (bytes.size < need) bytes = ByteArray(need)
        var j = 0
        for (i in offset until offset + len) {
            val v = pcm[i].toInt()
            bytes[j++] = (v and 0xFF).toByte()
            bytes[j++] = ((v shr 8) and 0xFF).toByte()
        }
        raf.seek(44 + dataBytes)
        raf.write(bytes, 0, need)
        dataBytes += need
        val bytesPerSec = sampleRate * channels * 2L
        if (dataBytes - lastHeaderAt >= bytesPerSec) {
            lastHeaderAt = dataBytes
            patchHeader()
        }
    }

    private fun patchHeader() {
        raf.seek(0)
        raf.write(header(sampleRate, channels, dataBytes))
    }

    override fun finish(): File {
        patchHeader()
        raf.fd.sync()
        raf.close()
        return workFile
    }

    override fun abort() { runCatching { raf.close() } }

    companion object {
        fun header(sampleRate: Int, channels: Int, dataLen: Long): ByteArray {
            val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            val dl = dataLen.coerceAtMost(0xFFFFFFFFL - 36).toInt()
            b.put("RIFF".toByteArray()); b.putInt(36 + dl); b.put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
            b.putInt(sampleRate); b.putInt(sampleRate * channels * 2); b.putShort((channels * 2).toShort())
            b.putShort(16)
            b.put("data".toByteArray()); b.putInt(dl)
            return b.array()
        }

        /** Reescribe la cabecera según el tamaño real del archivo. Devuelve la duración en ms. */
        fun repair(file: File, sampleRate: Int, channels: Int): Long {
            RandomAccessFile(file, "rw").use { raf ->
                val frameBytes = channels * 2
                var data = (raf.length() - 44).coerceAtLeast(0)
                data -= data % frameBytes
                raf.setLength(44 + data)
                raf.seek(0)
                raf.write(header(sampleRate, channels, data))
                return data / frameBytes * 1000 / sampleRate
            }
        }

        /** Lee frecuencia/canales/duración de un WAV propio. */
        fun probe(file: File): Triple<Int, Int, Long>? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val h = ByteArray(44); raf.readFully(h)
                val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
                val ch = b.getShort(22).toInt(); val sr = b.getInt(24)
                val data = raf.length() - 44
                Triple(sr, ch, data / (ch * 2) * 1000 / sr)
            }
        }.getOrNull()
    }
}
