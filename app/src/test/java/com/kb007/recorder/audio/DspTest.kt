package com.kb007.recorder.audio

import com.kb007.recorder.audio.dsp.LevelMeter
import com.kb007.recorder.audio.dsp.MonoResampler
import com.kb007.recorder.audio.dsp.RingBuffer
import com.kb007.recorder.audio.dsp.SilenceDetector
import com.kb007.recorder.audio.dsp.SoftGain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspTest {
    @Test fun ringBufferKeepsLastSamplesInOrder() {
        val rb = RingBuffer(5)
        rb.write(shortArrayOf(1, 2, 3))
        rb.write(shortArrayOf(4, 5, 6, 7))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), rb.drain())
        assertEquals(0, rb.size)
        rb.write(ShortArray(12) { it.toShort() })
        assertArrayEquals(shortArrayOf(7, 8, 9, 10, 11), rb.snapshot())
    }

    @Test fun resamplerOutputsExpectedLengthAndPreservesDc() {
        val r = MonoResampler(48_000, 2, 16_000)
        var total = 0
        repeat(50) { // 1 s en bloques de 20 ms estéreo
            val block = ShortArray(960 * 2) { 1000 }
            val (out, n) = r.process(block, block.size)
            for (i in 0 until n) assertEquals(1000, out[i].toInt())
            total += n
        }
        assertTrue("salida=$total", abs(total - 16_000) <= 1)
    }

    @Test fun resampler44kNonIntegerRatio() {
        val r = MonoResampler(44_100, 1, 16_000)
        var total = 0
        repeat(100) { val b = ShortArray(441) { 500 }; total += r.process(b, b.size).second }
        assertTrue("salida=$total", abs(total - 16_000) <= 1)
    }

    @Test fun levelMeterFullScaleSine() {
        val pcm = ShortArray(4800) { (32767 * sin(2 * PI * 1000 * it / 48_000)).toInt().toShort() }
        val l = LevelMeter.measure(pcm, pcm.size)
        assertEquals(-3.0f, l.rmsDb, 0.2f)
        assertTrue(l.clipping)
        assertEquals(-90f, LevelMeter.measure(ShortArray(10), 10).rmsDb, 1f)
    }

    @Test fun softGainNeverWrapsAround() {
        val g = SoftGain(24f)
        val pcm = shortArrayOf(30000, -30000, 100, -100)
        g.apply(pcm, pcm.size)
        assertTrue(pcm[0] > 20000 && pcm[1] < -20000)
        assertEquals(1585.0, pcm[2].toDouble(), 2.0)
    }

    @Test fun silenceDetectorAccumulatesAndResets() {
        val d = SilenceDetector(-45f)
        val quiet = LevelMeter.measure(ShortArray(100) { 10 }, 100)
        val loud = LevelMeter.measure(ShortArray(100) { 10000 }, 100)
        repeat(10) { d.feed(quiet, 20) }
        assertEquals(200L, d.silentMs)
        d.feed(loud, 20)
        assertEquals(0L, d.silentMs)
    }
}
