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
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as KbApp).container
    val settings: StateFlow<AppSettings> = c.settings.state
    val level get() = c.engine.level
    val engineState get() = c.engine.state

    private val _caps = MutableStateFlow(DeviceCapabilities.UNKNOWN)
    val caps: StateFlow<DeviceCapabilities> = _caps

    init {
        viewModelScope.launch(Dispatchers.Default) { _caps.value = c.capabilities() }
    }

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
