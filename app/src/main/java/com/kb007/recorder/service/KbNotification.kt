package com.kb007.recorder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.kb007.recorder.MainActivity
import com.kb007.recorder.R
import com.kb007.recorder.audio.engine.EngineState
import com.kb007.recorder.audio.engine.RecMode

/** Notificación persistente con estética KB-007 (LEDs, contador y botones). */
object KbNotification {
    const val CHANNEL_SERVICE = "kb007_service"
    const val CHANNEL_ALERTS = "kb007_alerts"
    const val ID_SERVICE = 7
    const val ID_ALERT = 8
    const val ID_RESUME = 9

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, ctx.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.channel_service_desc); setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.channel_alerts_desc)
            }
        )
    }

    private fun openApp(ctx: Context, resumeListening: Boolean = false): PendingIntent =
        PendingIntent.getActivity(
            ctx, if (resumeListening) 2 else 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_RESUME_LISTENING, resumeListening),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getService(
            ctx, req, Intent(ctx, RecorderService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun statusText(s: EngineState, keyword: String): String = when {
        s.mode == RecMode.RECORDING -> if (s.trigger?.name == "KEYWORD") "● Grabando (palabra clave)" else "● Grabando"
        s.mode == RecMode.PAUSED -> "❚❚ ${s.pauseReason?.label ?: "Pausa"}"
        s.finalizing -> "Guardando grabación…"
        s.listening && s.inCall -> "Escucha suspendida durante la llamada"
        s.listening -> "Escuchando «$keyword»"
        else -> "Listo"
    }

    fun build(ctx: Context, s: EngineState, keyword: String): android.app.Notification {
        val recording = s.mode != RecMode.IDLE
        val views = RemoteViews(ctx.packageName, R.layout.notif_kb).apply {
            setImageViewResource(R.id.led_rec, if (s.mode == RecMode.RECORDING) R.drawable.led_red else R.drawable.led_off)
            setImageViewResource(R.id.led_kw, if (s.listening) R.drawable.led_amber else R.drawable.led_off)
            setTextViewText(R.id.status, statusText(s, keyword))
            if (recording) {
                setViewVisibility(R.id.chrono, View.VISIBLE)
                val base = if (s.mode == RecMode.RECORDING) s.chronoBase else SystemClock.elapsedRealtime() - s.elapsedMs
                setChronometer(R.id.chrono, base, null, s.mode == RecMode.RECORDING)
            } else setViewVisibility(R.id.chrono, View.GONE)
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_cassette)
            .setColor(0xFFD32F2F.toInt())
            .setContentTitle("KB-007")
            .setContentText(statusText(s, keyword))
            .setCustomContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (recording) {
            b.addAction(0, "Detener", action(ctx, RecorderService.ACTION_STOP_REC, 10))
            b.addAction(0, if (s.mode == RecMode.PAUSED) "Reanudar" else "Pausa", action(ctx, RecorderService.ACTION_TOGGLE_PAUSE, 11))
        } else if (s.listening) {
            b.addAction(0, "Grabar ya", action(ctx, RecorderService.ACTION_RECORD, 12))
        }
        if (s.listening) b.addAction(0, "Detener escucha", action(ctx, RecorderService.ACTION_LISTEN_OFF, 13))
        return b.build()
    }

    /** Aviso tras reinicio: el sistema no permite reabrir el micrófono solo; hay que tocar. */
    fun resumeAfterBoot(ctx: Context): android.app.Notification =
        NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_cassette)
            .setColor(0xFFFFB300.toInt())
            .setContentTitle("KB-007: escucha en pausa")
            .setContentText("Tocá para reanudar la escucha de la palabra clave")
            .setContentIntent(openApp(ctx, resumeListening = true))
            .setAutoCancel(true)
            .build()

    fun alert(ctx: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_cassette)
            .setColor(0xFFD32F2F.toInt())
            .setContentTitle("KB-007")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
}
