package com.kb007.recorder.audio.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.kb007.recorder.audio.encode.OpusOggEncoder
import com.kb007.recorder.data.prefs.SAMPLE_RATES

/** Qué soporta este teléfono. Se calcula una vez (requiere permiso de micrófono para probar estéreo). */
data class DeviceCapabilities(
    val unprocessed: Boolean,
    val noiseSuppressor: Boolean,
    val autoGain: Boolean,
    val echoCanceler: Boolean,
    val stereo: Boolean,
    val externalMic: Boolean,
    val opus: Boolean,
    val sampleRates: List<Int>,
) {
    companion object {
        fun probe(context: Context): DeviceCapabilities {
            val am = context.getSystemService(AudioManager::class.java)
            val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val external = inputs.any {
                it.type in setOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                )
            }
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            val stereoByDevice = inputs.any { d -> d.channelCounts.any { it >= 2 } }
            val stereo = if (granted) probeStereo() else stereoByDevice
            val rates = SAMPLE_RATES.filter {
                AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
            }
            return DeviceCapabilities(
                unprocessed = unprocessed,
                noiseSuppressor = NoiseSuppressor.isAvailable(),
                autoGain = AutomaticGainControl.isAvailable(),
                echoCanceler = AcousticEchoCanceler.isAvailable(),
                stereo = stereo,
                externalMic = external,
                opus = OpusOggEncoder.isSupported(),
                sampleRates = rates.ifEmpty { listOf(16_000, 44_100) },
            )
        }

        @Suppress("MissingPermission")
        private fun probeStereo(): Boolean = runCatching {
            val min = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            val r = AudioRecord(MediaRecorder.AudioSource.CAMCORDER, 48_000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, min)
            val ok = r.state == AudioRecord.STATE_INITIALIZED && r.channelCount == 2
            r.release(); ok
        }.getOrDefault(false)

        val UNKNOWN = DeviceCapabilities(false, false, false, false, false, false, false, SAMPLE_RATES)
    }
}
