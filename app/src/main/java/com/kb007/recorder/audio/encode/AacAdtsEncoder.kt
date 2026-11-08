package com.kb007.recorder.audio.encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * AAC-LC con MediaCodec. Durante la grabación escribe ADTS (cada frame es autónomo, así
 * un corte no arruina el archivo). Al terminar se reempaqueta a .m4a sin recodificar.
 */
class AacAdtsEncoder(override val workFile: File, private val spec: EncodeSpec) : AudioSink {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val fos = FileOutputStream(workFile)
    private val out = BufferedOutputStream(fos, 64 * 1024)
    private val info = MediaCodec.BufferInfo()
    private var samplesQueued = 0L
    private var flushCounter = 0

    init {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, spec.sampleRate, spec.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, spec.effectiveBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
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
            val sb = buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            sb.put(pcm, o, n)
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
                if (!endOfStream || ++waits > 200) return else continue // máx. ~2 s esperando el EOS
            }
            if (idx < 0) continue // cambio de formato / buffers
            val b = codec.getOutputBuffer(idx)!!
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                val data = ByteArray(info.size)
                b.position(info.offset); b.get(data)
                out.write(Adts.header(data.size, spec.sampleRate, spec.channels))
                out.write(data)
                // Vaciamos a disco cada ~50 frames (~1 s) para limitar pérdidas ante un corte.
                if (++flushCounter % 50 == 0) out.flush()
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
        out.flush(); runCatching { fos.fd.sync() }; out.close()
        val m4a = File(workFile.parentFile, workFile.nameWithoutExtension + ".m4a")
        Remuxer.adtsToM4a(workFile, m4a)
        workFile.delete()
        return m4a
    }

    override fun abort() {
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { out.close() }
    }
}
