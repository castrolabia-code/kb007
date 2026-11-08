package com.kb007.recorder.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kb007.recorder.KbApp
import com.kb007.recorder.data.db.Recording
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class SortBy(val label: String) { DATE("Fecha"), NAME("Nombre"), DURATION("Duración") }

data class LibraryUi(
    val items: List<Recording> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val sort: SortBy = SortBy.DATE,
    val descending: Boolean = true,
    /** Conversión en curso para compartir (0..1), o null. */
    val converting: Float? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as KbApp).container
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortBy.DATE)
    private val desc = MutableStateFlow(true)
    private val converting = MutableStateFlow<Float?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val ui: StateFlow<LibraryUi> = combine(c.recordings.finished, query, sort, desc, converting) { list, q, s, d, conv ->
        val nq = normalize(q)
        val filtered = if (nq.isBlank()) list else list.filter { normalize(it.title).contains(nq) || (it.keyword?.let { k -> normalize(k).contains(nq) } ?: false) }
        val cmp: Comparator<Recording> = when (s) {
            SortBy.DATE -> compareBy { it.createdAt }
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortBy.DURATION -> compareBy { it.durationMs }
        }
        LibraryUi(filtered.sortedWith(if (d) cmp.reversed() else cmp), list.size, q, s, d, conv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUi())

    init {
        // Limpia entradas cuyo archivo se borró por fuera de la app.
        viewModelScope.launch { c.recordings.pruneMissing(c.recordings.finished.first()) }
    }

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: SortBy) {
        if (sort.value == s) desc.value = !desc.value else { sort.value = s; desc.value = s != SortBy.NAME }
    }

    fun rename(r: Recording, title: String) = viewModelScope.launch {
        c.recordings.rename(r, title)
        // Si es el cassette cargado en el aparato, actualizamos su etiqueta.
        val loaded = c.player.state.value.loaded
        if (loaded?.id == r.id) c.recordings.get(r.id)?.let { c.player.updateLoaded(it) }
    }

    fun delete(r: Recording) = viewModelScope.launch {
        if (c.player.state.value.loaded?.id == r.id) c.player.eject()
        c.recordings.delete(r)
        _messages.tryEmit("Eliminada: ${r.title}")
    }

    fun whatsAppInstalled() = c.share.whatsAppPackage() != null
    fun needsConversion(r: Recording) = c.share.shouldOfferConversion(r)

    /** Comparte. [toWhatsApp] = directo a WhatsApp; [convert] = convertir WAV a M4A antes. */
    fun share(r: Recording, toWhatsApp: Boolean, convert: Boolean) = viewModelScope.launch {
        try {
            var uri: Uri? = null
            var mime: String? = null
            if (convert) {
                converting.value = 0f
                uri = c.share.convertForSharing(r) { converting.value = it }
                mime = "audio/mp4"
            }
            if (toWhatsApp) {
                if (!c.share.shareToWhatsApp(r, uri, mime)) _messages.tryEmit("WhatsApp no está instalado")
            } else c.share.shareWithChooser(r, uri, mime)
        } catch (t: Throwable) {
            _messages.tryEmit("No se pudo compartir: ${t.message}")
        } finally {
            converting.value = null
        }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
