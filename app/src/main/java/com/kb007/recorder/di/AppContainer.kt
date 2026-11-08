package com.kb007.recorder.di

import android.content.Context
import com.kb007.recorder.audio.capture.DeviceCapabilities
import com.kb007.recorder.audio.engine.RecorderEngine
import com.kb007.recorder.audio.engine.RecoveryManager
import com.kb007.recorder.data.RecordingsRepository
import com.kb007.recorder.data.StorageManager
import com.kb007.recorder.data.db.KbDatabase
import com.kb007.recorder.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: un único lugar donde se crean los singletons. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(context, appScope) }
    val db by lazy { KbDatabase.create(context) }
    val storage by lazy { StorageManager(context) }
    val recordings by lazy { RecordingsRepository(db.recordings(), storage) }
    val recovery by lazy { RecoveryManager(db.recordings(), storage, settings) }

    @Volatile private var caps: DeviceCapabilities? = null
    /** Capacidades del equipo (se recalculan tras conceder el permiso de micrófono). */
    fun capabilities(): DeviceCapabilities = caps ?: DeviceCapabilities.probe(context).also { caps = it }
    fun refreshCapabilities() { caps = DeviceCapabilities.probe(context) }

    val engine by lazy {
        RecorderEngine(context, settings, db.recordings(), storage, appScope, ::capabilities)
    }

    /** true mientras alguna Activity está visible (para decidir si avisar por notificación). */
    @Volatile var appVisible: Boolean = false
}
