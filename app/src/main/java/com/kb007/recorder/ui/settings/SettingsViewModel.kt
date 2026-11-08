package com.kb007.recorder.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kb007.recorder.KbApp
import com.kb007.recorder.audio.capture.DeviceCapabilities
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.data.prefs.AudioPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kb007.recorder.service.RecorderService
import com.kb007.recorder.wakeword.KeywordEvent
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as KbApp).container
    val settings: StateFlow<AppSettings> = c.settings.state
    val level get() = c.engine.level
    val engineState get() = c.engine.state

    private val _caps = MutableStateFlow(DeviceCapabilities.UNKNOWN)
    val caps: StateFlow<DeviceCapabilities> = _caps

    private val _vocab = MutableStateFlow(VocabCheck(null, null))
    val vocab: StateFlow<VocabCheck> = _vocab

    // ---- Prueba de palabra clave ----
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing
    private val _heard = MutableStateFlow<List<KeywordEvent.Heard>>(emptyList())
    val heard: StateFlow<List<KeywordEvent.Heard>> = _heard
    private val _testReady = MutableStateFlow(false)
    val testReady: StateFlow<Boolean> = _testReady
    private val _testError = MutableStateFlow<String?>(null)
    val testError: StateFlow<String?> = _testError
    private var wasListening = false
    private var testJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) { _caps.value = c.capabilities() }
        // Validación de vocabulario: espera a que el usuario deje de tipear.
        @OptIn(FlowPreview::class)
        viewModelScope.launch(Dispatchers.IO) {
            settings.map { it.keyword to it.stopKeyword }.distinctUntilChanged().debounce(400).collect { (kw, stop) ->
                _vocab.value = runCatching {
                    if (!c.voskModels.assetsAvailable() && !c.voskModels.isLoaded()) VocabCheck(null, null, modelMissing = true)
                    else VocabCheck(c.voskModels.unknownWords(kw), if (stop.isBlank()) emptyList() else c.voskModels.unknownWords(stop))
                }.getOrElse { VocabCheck(null, null, modelMissing = true) }
            }
        }
    }

    fun startKeywordTest() {
        if (_testing.value) return
        val ctx = getApplication<Application>()
        wasListening = c.engine.state.value.listening
        _heard.value = emptyList(); _testError.value = null
        _testReady.value = c.engine.state.value.detectorReady
        c.engine.keywordDryRun = true
        _testing.value = true
        testJob = viewModelScope.launch {
            c.engine.keywordEvents.collect { e ->
                when (e) {
                    is KeywordEvent.Heard -> _heard.value = (_heard.value + e).takeLast(20)
                    is KeywordEvent.Ready -> _testReady.value = true
                    is KeywordEvent.Error -> _testError.value = e.message
                    else -> Unit
                }
            }
        }
        if (!wasListening) RecorderService.send(ctx, RecorderService.ACTION_LISTEN_ON)
    }

    fun stopKeywordTest() {
        if (!_testing.value) return
        testJob?.cancel()
        c.engine.keywordDryRun = false
        _testing.value = false
        if (!wasListening) RecorderService.send(getApplication(), RecorderService.ACTION_LISTEN_OFF)
    }

    override fun onCleared() { stopKeywordTest(); super.onCleared() }

    fun update(f: (AppSettings) -> AppSettings) { viewModelScope.launch { c.settings.update(f) } }
    fun applyPreset(p: AudioPreset) { viewModelScope.launch { c.settings.applyPreset(p) } }

    fun setMonitoring(on: Boolean) { c.engine.setMonitoring(on) }

    /** Guarda la carpeta elegida con el selector del sistema (permiso persistente). */
    fun setCustomFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags) }
        update { it.copy(storageTarget = com.kb007.recorder.data.prefs.StorageTarget.CUSTOM_TREE, customTreeUri = uri.toString()) }
    }

    fun appFolderPath(): String = c.storage.appDir.absolutePath
}
