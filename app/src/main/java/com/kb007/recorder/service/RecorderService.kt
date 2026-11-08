package com.kb007.recorder.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kb007.recorder.KbApp
import com.kb007.recorder.audio.engine.EngineEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground Service de tipo "microphone". Mantiene vivo el proceso mientras se escucha la
 * palabra clave o se graba, incluso con la pantalla apagada o la app cerrada.
 *
 * Android 14+: solo puede INICIARSE con la app en primer plano (o desde una acción de su
 * propia notificación ya visible). Por eso la escucha se activa desde la app.
 */
class RecorderService : LifecycleService() {

    private val container get() = (application as KbApp).container
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        KbNotification.createChannels(this)
        val engine = container.engine

        // Notificación siempre sincronizada con el estado del motor.
        lifecycleScope.launch {
            combine(engine.state, container.settings.state.map { it.keyword }) { s, kw -> s to kw }
                .distinctUntilChanged { a, b -> a.first.copy(elapsedMs = 0) == b.first.copy(elapsedMs = 0) && a.second == b.second }
                .collect { (s, kw) ->
                    if (inForeground) getSystemService(NotificationManager::class.java)
                        .notify(KbNotification.ID_SERVICE, KbNotification.build(this@RecorderService, s, kw))
                }
        }
        // Cuando no queda nada que hacer, el servicio se detiene solo (con un margen).
        lifecycleScope.launch {
            engine.state.map { it.isActive }.distinctUntilChanged().collectLatest { active ->
                if (active) acquireWakeLock() else {
                    delay(2000)
                    stopSelf()
                }
            }
        }
        // Avisos: vibración/beep al detectar la palabra; errores como notificación.
        lifecycleScope.launch {
            engine.events.collect { e ->
                when (e) {
                    is EngineEvent.KeywordTriggered -> keywordFeedback()
                    is EngineEvent.Message -> if (e.isError && !container.appVisible) {
                        getSystemService(NotificationManager::class.java)
                            .notify(KbNotification.ID_ALERT, KbNotification.alert(this@RecorderService, e.text))
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!goForeground()) { stopSelf(); return START_NOT_STICKY }
        val engine = container.engine
        when (intent?.action) {
            ACTION_LISTEN_ON -> engine.startListening()
            ACTION_LISTEN_OFF -> engine.stopListening()
            ACTION_RECORD -> engine.startRecording()
            ACTION_STOP_REC -> engine.stopRecording()
            ACTION_TOGGLE_PAUSE -> engine.togglePause()
        }
        // Si el sistema mata el proceso no lo recreamos solo: no podría reabrir el micrófono
        // desde segundo plano en Android 14+. La recuperación de archivos la hace KbApp.
        return START_NOT_STICKY
    }

    private fun goForeground(): Boolean {
        if (inForeground) return true
        return try {
            val n = KbNotification.build(this, container.engine.state.value, container.settings.state.value.keyword)
            ServiceCompat.startForeground(
                this, KbNotification.ID_SERVICE, n,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
            inForeground = true
            true
        } catch (t: Throwable) {
            // SecurityException (sin permiso) o ForegroundServiceStartNotAllowedException (app en segundo plano)
            Log.e(TAG, "No se pudo iniciar el servicio en primer plano", t)
            false
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KB007:mic").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // tope de seguridad: 12 h
            }
    }

    private fun keywordFeedback() {
        val s = container.settings.state.value
        if (s.keywordVibrate) {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
        }
        if (s.keywordBeep) runCatching {
            // Beep corto y bajo (queda registrado levemente en la grabación).
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            lifecycleScope.launch { delay(400); tg.release() }
        }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        inForeground = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KB007/Service"
        const val ACTION_LISTEN_ON = "kb007.LISTEN_ON"
        const val ACTION_LISTEN_OFF = "kb007.LISTEN_OFF"
        const val ACTION_RECORD = "kb007.RECORD"
        const val ACTION_STOP_REC = "kb007.STOP_REC"
        const val ACTION_TOGGLE_PAUSE = "kb007.TOGGLE_PAUSE"

        /** Envía un comando. Iniciarlo así solo es válido con la app visible (o si ya corre). */
        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, RecorderService::class.java).setAction(action)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (t: Throwable) {
                Log.e(TAG, "No se pudo iniciar el servicio", t)
            }
        }
    }
}
