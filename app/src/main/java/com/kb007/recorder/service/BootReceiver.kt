package com.kb007.recorder.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kb007.recorder.KbApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tras reiniciar: Android 14/15 prohíbe abrir un servicio de micrófono desde BOOT_COMPLETED.
 * Alternativa: si la escucha estaba activa, mostramos un aviso que con un toque la reanuda.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as KbApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.container.settings.current()
                if (s.listeningEnabled && s.resumeAfterBoot) {
                    KbNotification.createChannels(context)
                    context.getSystemService(NotificationManager::class.java)
                        .notify(KbNotification.ID_RESUME, KbNotification.resumeAfterBoot(context))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
