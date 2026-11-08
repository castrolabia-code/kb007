package com.kb007.recorder.audio.dsp

/**
 * Convierte PCM16 intercalado (1 o 2 canales, cualquier frecuencia >= destino)
 * a mono a [outRate] Hz, en streaming.
 *
 * Usa promedio por áreas (box filter fraccional): cada muestra de salida es el promedio
 * ponderado exacto de las muestras de entrada que caen en su intervalo. Actúa como un
 * filtro anti-aliasing sencillo, suficiente para alimentar el reconocedor de voz.
 */
class MonoResampler(private val inRate: Int, private val channels: Int, private val outRate: Int = 16_000) {
    private val ratio = inRate.toDouble() / outRate
    private var acc = 0.0
    private var accW = 0.0
    private var pos = 0.0
    private var scratch = ShortArray(0)

    init { require(inRate >= outRate) { "Solo se admite bajar la frecuencia" } }

    /** Procesa [len] muestras intercaladas y devuelve un array (reutilizado) y su largo útil. */
    fun process(src: ShortArray, len: Int): Pair<ShortArray, Int> {
        val frames = len / channels
        val maxOut = (frames / ratio).toInt() + 2
        if (scratch.size < maxOut) scratch = ShortArray(maxOut)
        var outN = 0
        var i = 0
        for (f in 0 until frames) {
            val x: Double = if (channels == 2) (src[i] + src[i + 1]) / 2.0 else src[i].toDouble()
            i += channels
            if (inRate == outRate) { scratch[outN++] = x.toInt().toShort(); continue }
            val remaining = ratio - pos
            if (remaining > 1.0) {
                acc += x; accW += 1.0; pos += 1.0
            } else {
                acc += x * remaining; accW += remaining
                scratch[outN++] = (acc / accW).toInt().coerceIn(-32768, 32767).toShort()
                val leftover = 1.0 - remaining
                acc = x * leftover; accW = leftover; pos = leftover
            }
        }
        return scratch to outN
    }
}

/** Mezcla estéreo → mono (para cuando el destino necesita mono a la misma frecuencia). */
fun downmixToMono(src: ShortArray, len: Int, channels: Int): ShortArray {
    if (channels == 1) return src.copyOf(len)
    val out = ShortArray(len / 2)
    var j = 0
    var i = 0
    while (i + 1 < len) { out[j++] = ((src[i] + src[i + 1]) / 2).toShort(); i += 2 }
    return out
}
