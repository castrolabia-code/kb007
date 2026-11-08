package com.kb007.recorder.data

import android.net.Uri
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.db.RecordingDao
import com.kb007.recorder.data.db.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Operaciones sobre grabaciones: metadatos (Room) + archivo (StorageManager). */
class RecordingsRepository(private val dao: RecordingDao, private val storage: StorageManager) {

    val finished: Flow<List<Recording>> = dao.observeFinished()

    fun observe(id: Long): Flow<Recording?> = dao.observe(id)

    suspend fun get(id: Long) = dao.get(id)

    suspend fun rename(r: Recording, newTitle: String) = withContext(Dispatchers.IO) {
        val title = newTitle.trim().ifEmpty { r.title }
        val newUri = if (r.uri.isNotEmpty()) storage.rename(Uri.parse(r.uri), title).toString() else r.uri
        dao.update(r.copy(title = title, uri = newUri))
    }

    suspend fun delete(r: Recording) = withContext(Dispatchers.IO) {
        if (r.uri.isNotEmpty()) storage.delete(Uri.parse(r.uri))
        dao.delete(r.id)
    }

    /** Quita de la lista grabaciones cuyo archivo ya no existe (borrado desde fuera de la app). */
    suspend fun pruneMissing(list: List<Recording>) = withContext(Dispatchers.IO) {
        list.filter { it.status != RecordingStatus.RECORDING && it.uri.isNotEmpty() && !storage.exists(Uri.parse(it.uri)) }
            .forEach { dao.delete(it.id) }
    }
}
