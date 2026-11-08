package com.kb007.recorder.audio

import com.kb007.recorder.audio.encode.Transcoder
import com.kb007.recorder.audio.encode.WavWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TranscoderTest {
    @Test fun parsesWavWithExtraChunks() {
        val out = ByteArrayOutputStream()
        val h = WavWriter.header(48_000, 2, 400)
        // Insertamos un chunk "LIST" entre "fmt " y "data" como hacen otros grabadores
        out.write(h, 0, 36)
        out.write("LIST".toByteArray()); out.write(byteArrayOf(5, 0, 0, 0)); out.write(ByteArray(6)) // 5 bytes + relleno
        out.write(h, 36, 8)
        out.write(ByteArray(400))
        val input = ByteArrayInputStream(out.toByteArray())
        val info = Transcoder.readWavHeader(input)
        assertEquals(48_000, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(400L, info.dataBytes)
        assertEquals(400, input.available())
    }
}
