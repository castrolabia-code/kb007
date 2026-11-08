package com.kb007.recorder.audio.engine

import android.util.Log
import com.kb007.recorder.audio.encode.OggWriter
import com.kb007.recorder.audio.encode.Remuxer
import com.kb007.recorder.audio.encode.WavWriter
import com.kb007.recorder.data.StorageManager
import com.kb007.recorder.data.db.RecordingDao
import com.kb007.recorder.data.db.RecordingStatus
import com.kb007.recorder.data.prefs.SettingsRepository
import java.io.File

/**
 * Recupera grabaciones que quedaron a medias (cierre forzado, batería agotada, crash).
 * Los archivos de trabajo están en formatos tolerantes a cortes, así que se pueden cerrar bien:
 *  - .aac (ADTS) → se reempaqueta a .m4a
 *  - .ogg        → se trunca en la última página válida y se marca fin
 *  - .wav        → se corrige la cabecera
 */
class RecoveryManager(
    private val dao: RecordingDao,
    private val storage: StorageManager,
    private val settings: SettingsRepository,
) {
    /** Recupera todas las grabaciones en estado RECORDING excepto [activeId]. Devuelve cuántas. */
    suspend fun recoverAll(activeId: Long?): Int {
        var n = 0
        for (r in dao.inProgress()) {
            if (r.id == activeId) continue
            if (recoverOne(r.id)) n++
        }
        cleanOrphans()
        return n
    }

    suspend fun recoverOne(id: Long): Boolean {
        val r = dao.get(id) ?: return false
        val work = File(r.workPath)
        if (r.workPath.isEmpty() || !work.exists() || work.length() == 0L) {
            dao.delete(id); work.delete(); return false
        }
        return try {
            val (finalFile, durationMs) = when (work.extension) {
                "aac" -> {
                    val m4a = File(work.parentFile, work.nameWithoutExtension + ".m4a")
                    val d = Remuxer.adtsToM4a(work, m4a)
                    work.delete(); m4a to d
                }
                "ogg" -> {
                    val g = OggWriter.repair(work)
                    require(g >= 0) { "Ogg sin páginas válidas" }
                    work to g * 1000 / 48_000
                }
                "wav" -> work to WavWriter.repair(work, r.sampleRate, r.channels)
                "m4a" -> work to r.durationMs // ya se había reempaquetado
                else -> error("Formato desconocido: ${work.extension}")
            }
            val s = settings.current()
            val pub = storage.publish(finalFile, r.title, r.format.mime, s.storageTarget, s.customTreeUri)
            dao.update(r.copy(uri = pub.uri.toString(), workPath = "", durationMs = durationMs, sizeBytes = pub.sizeBytes, status = RecordingStatus.RECOVERED))
            true
        } catch (t: Throwable) {
            Log.e("KB007/Recovery", "No se pudo recuperar ${r.workPath}", t)
            dao.update(r.copy(status = RecordingStatus.FAILED))
            false
        }
    }

    /** Borra archivos de trabajo que no pertenecen a ninguna grabación en curso. */
    private suspend fun cleanOrphans() {
        val referenced = dao.inProgress().map { File(it.workPath).nameWithoutExtension }.toSet()
        storage.workDir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension !in referenced && System.currentTimeMillis() - f.lastModified() > 60_000) f.delete()
        }
    }
}
