package com.kb007.recorder.audio.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opus (encoder del sistema, Android 10+) dentro de un contenedor Ogg escrito por nosotros. */
class OpusOggEncoder(override val workFile: File, private val spec: EncodeSpec) : AudioSink {
    private val codec: MediaCodec
    private val fos = FileOutputStream(workFile)
    private val ogg = OggWriter(BufferedOutputStream(fos, 32 * 1024))
    private val info = MediaCodec.BufferInfo()
    private var samplesQueued = 0L
    private var granule = 0L
    private var headerWritten = false
    private var preSkip = 312 // valor típico de libopus (6,5 ms a 48 kHz)

    init {
        check(isSupported()) { "Opus requiere Android 10 o superior" }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, spec.sampleRate, spec.channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, spec.effectiveBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    private fun writeHeadersOnce() {
        if (headerWritten) return
        headerWritten = true
        // csd-1 = retardo del codec en nanosegundos (si el encoder lo informa)
        runCatching {
            codec.outputFormat.getByteBuffer("csd-1")?.let { b ->
                val ns = b.order(ByteOrder.LITTLE_ENDIAN).getLong(0)
                if (ns in 1..100_000_000) preSkip = (ns * 48_000 / 1_000_000_000L).toInt()
            }
        }
        ogg.writeHeaderPacket(Opus.head(spec.channels, preSkip, spec.sampleRate), bos = true)
        ogg.writeHeaderPacket(Opus.tags(), bos = false)
    }

    override fun write(pcm: ShortArray, offset: Int, len: Int) {
        var o = offset
        var remaining = len
        while (remaining > 0) {
            val idx = codec.dequeueInputBuffer(10_000)
            if (idx < 0) { drain(false); continue }
            val buf = codec.getInputBuffer(idx)!!
            buf.clear()
            val n = minOf(remaining, buf.remaining() / 2)
            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, o, n)
            val ptsUs = samplesQueued / spec.channels * 1_000_000L / spec.sampleRate
            codec.queueInputBuffer(idx, 0, n * 2, ptsUs, 0)
            samplesQueued += n
            o += n; remaining -= n
            drain(false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        var waits = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream || ++waits > 200) return else continue
            }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { writeHeadersOnce(); continue }
            if (idx < 0) continue
            val b: ByteBuffer = codec.getOutputBuffer(idx)!!
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                writeHeadersOnce()
                val data = ByteArray(info.size)
                b.position(info.offset); b.get(data)
                granule += Opus.packetSamples48k(data)
                ogg.writeAudioPacket(data, granule)
            }
            codec.releaseOutputBuffer(idx, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    override fun finish(): File {
        val idx = codec.dequeueInputBuffer(100_000)
        if (idx >= 0) {
            val ptsUs = samplesQueued / spec.channels * 1_000_000L / spec.sampleRate
            codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        }
        runCatching { codec.stop() }; codec.release()
        writeHeadersOnce()
        ogg.finish(granule)
        runCatching { fos.fd.sync() }
        fos.close()
        return workFile
    }

    override fun abort() {
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { fos.close() }
    }

    companion object {
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
