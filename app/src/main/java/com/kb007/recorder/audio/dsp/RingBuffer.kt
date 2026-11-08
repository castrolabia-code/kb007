package com.kb007.recorder.audio.dsp

/**
 * Buffer circular de muestras PCM16 (intercaladas). Guarda los últimos [capacity] valores.
 * Se usa como pre-grabación: al dispararse la palabra clave se vuelca su contenido
 * al principio del archivo.
 */
class RingBuffer(capacity: Int) {
    private var buf = ShortArray(capacity.coerceAtLeast(1))
    private var writePos = 0
    private var filled = 0

    val capacity: Int get() = buf.size
    val size: Int get() = filled

    @Synchronized
    fun resize(newCapacity: Int) {
        if (newCapacity == buf.size) return
        buf = ShortArray(newCapacity.coerceAtLeast(1)); writePos = 0; filled = 0
    }

    @Synchronized
    fun write(src: ShortArray, offset: Int = 0, len: Int = src.size) {
        val n = buf.size
        var o = offset
        var l = len
        if (l >= n) { // solo interesan las últimas n muestras
            o += l - n; l = n
        }
        val first = minOf(l, n - writePos)
        System.arraycopy(src, o, buf, writePos, first)
        if (l > first) System.arraycopy(src, o + first, buf, 0, l - first)
        writePos = (writePos + l) % n
        filled = minOf(n, filled + l)
    }

    /** Devuelve una copia ordenada (de más vieja a más nueva) y vacía el buffer. */
    @Synchronized
    fun drain(): ShortArray {
        val out = snapshot()
        filled = 0; writePos = 0
        return out
    }

    @Synchronized
    fun snapshot(): ShortArray {
        val n = buf.size
        val out = ShortArray(filled)
        val start = (writePos - filled + n) % n
        val first = minOf(filled, n - start)
        System.arraycopy(buf, start, out, 0, first)
        if (filled > first) System.arraycopy(buf, 0, out, first, filled - first)
        return out
    }

    @Synchronized
    fun clear() { filled = 0; writePos = 0 }
}
