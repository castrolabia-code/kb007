package com.kb007.recorder

import android.app.Application
import android.util.Log
import com.kb007.recorder.di.AppContainer
import com.kb007.recorder.service.KbNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KbApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        KbNotification.createChannels(this)
        // Al arrancar el proceso no puede haber una grabación viva: lo que figure "en curso"
        // quedó cortado por un cierre inesperado. Lo reparamos y publicamos.
        container.appScope.launch(Dispatchers.IO) {
            val n = runCatching { container.recovery.recoverAll(activeId = container.engine.state.value.recordingId) }
                .onFailure { Log.e("KB007", "Recuperación fallida", it) }.getOrDefault(0)
            if (n > 0) Log.i("KB007", "Grabaciones recuperadas: $n")
        }
    }
}
