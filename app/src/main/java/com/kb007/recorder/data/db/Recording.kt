package com.kb007.recorder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.TriggerType

enum class RecordingStatus { RECORDING, DONE, RECOVERED, FAILED }

/** Metadatos de una grabación. El audio vive en [uri] (file://, content:// de MediaStore o SAF). */
@Entity(tableName = "recordings", indices = [Index("createdAt"), Index("status")])
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Uri definitiva (vacía mientras se graba). */
    val uri: String = "",
    /** Archivo temporal de trabajo mientras se graba (para recuperación). */
    val workPath: String = "",
    val format: AudioFormatType,
    val sampleRate: Int,
    val channels: Int,
    val bitrateKbps: Int,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val createdAt: Long,
    val trigger: TriggerType,
    val keyword: String? = null,
    val status: RecordingStatus = RecordingStatus.RECORDING,
    /** Estética del cassette elegida a mano (índice en CassetteStyle.ALL); -1 = automática. */
    @ColumnInfo(defaultValue = "-1") val cassetteStyle: Int = -1,
) {
    /** Descripción corta de formato y calidad, p.ej. "M4A · 44,1 kHz · 128 kbps · mono". */
    val qualityLabel: String
        get() {
            val khz = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else String.format(java.util.Locale.US, "%.2f", sampleRate / 1000f).trimEnd('0').replace('.', ',')
            val br = if (format.compressed) " · $bitrateKbps kbps" else ""
            val ch = if (channels == 2) "estéreo" else "mono"
            return "${format.extension.uppercase()} · $khz kHz$br · $ch"
        }
}
