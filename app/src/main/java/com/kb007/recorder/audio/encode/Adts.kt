package com.kb007.recorder.audio.encode

/** Utilidades para el formato ADTS (AAC "crudo" con cabecera por frame). */
object Adts {
    val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun rateIndex(sampleRate: Int): Int = SAMPLE_RATES.indexOf(sampleRate).also {
        require(it >= 0) { "Frecuencia no soportada por AAC: $sampleRate" }
    }

    /** Cabecera ADTS de 7 bytes (sin CRC) para AAC-LC. */
    fun header(frameDataLen: Int, sampleRate: Int, channels: Int): ByteArray {
        val len = frameDataLen + 7
        val profile = 1 // AAC LC (objectType 2) - 1
        val sr = rateIndex(sampleRate)
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(), // MPEG-4, layer 0, sin CRC
            ((profile shl 6) or (sr shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (len shr 11)).toByte(),
            ((len shr 3) and 0xFF).toByte(),
            (((len and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /** AudioSpecificConfig (csd-0) para AAC-LC. */
    fun audioSpecificConfig(sampleRate: Int, channels: Int): ByteArray {
        val v = (2 shl 11) or (rateIndex(sampleRate) shl 7) or (channels shl 3)
        return byteArrayOf((v shr 8).toByte(), (v and 0xFF).toByte())
    }

    data class Frame(val dataOffset: Int, val dataLen: Int, val sampleRate: Int, val channels: Int)

    /** Recorre los frames completos de un buffer ADTS. Se detiene en el primer frame truncado o corrupto. */
    fun parse(bytes: ByteArray): List<Frame> {
        val out = ArrayList<Frame>()
        var i = 0
        while (i + 7 <= bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) break
            val protectionAbsent = b1 and 1
            val b2 = bytes[i + 2].toInt() and 0xFF
            val b3 = bytes[i + 3].toInt() and 0xFF
            val b4 = bytes[i + 4].toInt() and 0xFF
            val b5 = bytes[i + 5].toInt() and 0xFF
            val srIdx = (b2 shr 2) and 0xF
            val ch = ((b2 and 1) shl 2) or (b3 shr 6)
            val frameLen = ((b3 and 3) shl 11) or (b4 shl 3) or (b5 shr 5)
            val hdr = if (protectionAbsent == 1) 7 else 9
            if (frameLen < hdr || srIdx >= SAMPLE_RATES.size || i + frameLen > bytes.size) break
            out += Frame(i + hdr, frameLen - hdr, SAMPLE_RATES[srIdx], ch)
            i += frameLen
        }
        return out
    }
}
