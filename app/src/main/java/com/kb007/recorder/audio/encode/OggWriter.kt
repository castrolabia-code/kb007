package com.kb007.recorder.audio.encode

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Escritor mínimo de contenedor Ogg (RFC 3533). Agrupa paquetes en páginas de ~1 s:
 * si la app se cierra de golpe, como mucho se pierde la última página.
 */
class OggWriter(private val out: OutputStream, private val serial: Int = (System.nanoTime() and 0x7FFFFFFF).toInt()) {
    private var pageSeq = 0
    private val pending = ArrayList<ByteArray>()
    private var pendingBytes = 0
    private var lastGranule = 0L

    /** Escribe un paquete en su propia página (para cabeceras). */
    fun writeHeaderPacket(packet: ByteArray, bos: Boolean) {
        writePage(listOf(packet), 0, if (bos) FLAG_BOS else 0)
    }

    /** Agrega un paquete de audio. [granule] = posición (en muestras a 48 kHz) al final del paquete. */
    fun writeAudioPacket(packet: ByteArray, granule: Long, flushIfOlderThan: Long = 48_000) {
        pending += packet
        pendingBytes += packet.size
        val segs = pending.sumOf { it.size / 255 + 1 }
        val shouldFlush = segs > 200 || pendingBytes > 16_000 || granule - lastGranule >= flushIfOlderThan
        if (shouldFlush) {
            writePage(pending.toList(), granule, 0)
            pending.clear(); pendingBytes = 0; lastGranule = granule
        } else {
            pendingGranule = granule
        }
    }
    private var pendingGranule = 0L

    /** Cierra el stream marcando fin (EOS). */
    fun finish(finalGranule: Long) {
        writePage(pending.toList(), if (pending.isEmpty()) finalGranule else maxOf(pendingGranule, finalGranule), FLAG_EOS)
        pending.clear(); pendingBytes = 0
        out.flush()
    }

    private fun writePage(packets: List<ByteArray>, granule: Long, flags: Int) {
        val lacing = ByteArrayOutputStream()
        val body = ByteArrayOutputStream()
        for (p in packets) {
            var n = p.size
            while (n >= 255) { lacing.write(255); n -= 255 }
            lacing.write(n)
            body.write(p)
        }
        val lac = lacing.toByteArray()
        val page = ByteArray(27 + lac.size + body.size())
        "OggS".toByteArray().copyInto(page, 0)
        page[4] = 0
        page[5] = flags.toByte()
        for (i in 0 until 8) page[6 + i] = (granule ushr (8 * i)).toByte()
        for (i in 0 until 4) page[14 + i] = (serial ushr (8 * i)).toByte()
        for (i in 0 until 4) page[18 + i] = (pageSeq ushr (8 * i)).toByte()
        page[26] = lac.size.toByte()
        lac.copyInto(page, 27)
        body.toByteArray().copyInto(page, 27 + lac.size)
        val crc = crc(page, 0, page.size)
        for (i in 0 until 4) page[22 + i] = (crc ushr (8 * i)).toByte()
        out.write(page)
        out.flush()
        pageSeq++
    }

    companion object {
        const val FLAG_CONTINUED = 0x01
        const val FLAG_BOS = 0x02
        const val FLAG_EOS = 0x04

        private val TABLE = IntArray(256).also { t ->
            for (i in 0 until 256) {
                var r = i shl 24
                repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
                t[i] = r
            }
        }

        /** CRC-32 de Ogg (polinomio 0x04C11DB7, sin reflejar, init 0). */
        fun crc(b: ByteArray, off: Int, len: Int): Int {
            var c = 0
            for (i in off until off + len) c = (c shl 8) xor TABLE[((c ushr 24) xor (b[i].toInt() and 0xFF)) and 0xFF]
            return c
        }

        /**
         * Recupera un Ogg cortado de golpe: trunca en la última página completa y válida,
         * y la marca como final (EOS). Devuelve el último granule (muestras a 48 kHz) o -1.
         */
        fun repair(file: File): Long {
            RandomAccessFile(file, "rw").use { raf ->
                val len = raf.length()
                var pos = 0L
                var lastPageStart = -1L
                var lastPageLen = 0
                var lastGranule = -1L
                val hdr = ByteArray(27)
                while (pos + 27 <= len) {
                    raf.seek(pos); raf.readFully(hdr)
                    if (String(hdr, 0, 4) != "OggS") break
                    val nSeg = hdr[26].toInt() and 0xFF
                    if (pos + 27 + nSeg > len) break
                    val lac = ByteArray(nSeg); raf.readFully(lac)
                    val bodyLen = lac.sumOf { it.toInt() and 0xFF }
                    val pageLen = 27 + nSeg + bodyLen
                    if (pos + pageLen > len) break
                    val page = ByteArray(pageLen)
                    raf.seek(pos); raf.readFully(page)
                    val stored = (0 until 4).fold(0) { acc, i -> acc or ((page[22 + i].toInt() and 0xFF) shl (8 * i)) }
                    for (i in 22 until 26) page[i] = 0
                    if (crc(page, 0, pageLen) != stored) break
                    var g = 0L
                    for (i in 0 until 8) g = g or ((hdr[6 + i].toLong() and 0xFF) shl (8 * i))
                    lastPageStart = pos; lastPageLen = pageLen; lastGranule = g
                    pos += pageLen
                }
                if (lastPageStart < 0) return -1
                raf.setLength(lastPageStart + lastPageLen)
                // Marcar EOS y recalcular CRC de la última página
                val page = ByteArray(lastPageLen)
                raf.seek(lastPageStart); raf.readFully(page)
                page[5] = (page[5].toInt() or FLAG_EOS).toByte()
                for (i in 22 until 26) page[i] = 0
                val c = crc(page, 0, lastPageLen)
                for (i in 0 until 4) page[22 + i] = (c ushr (8 * i)).toByte()
                raf.seek(lastPageStart); raf.write(page)
                return lastGranule
            }
        }
    }
}
