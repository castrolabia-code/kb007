package com.kb007.recorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kb007.recorder.service.RecorderService
import com.kb007.recorder.ui.KbNavHost
import com.kb007.recorder.ui.fx.DeckFx
import com.kb007.recorder.ui.permissions.PermissionScreen
import com.kb007.recorder.ui.permissions.hasMic
import com.kb007.recorder.ui.theme.KbThemes
import com.kb007.recorder.ui.theme.LocalKbTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as KbApp).container
    private var pendingResumeListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val theme = KbThemes.byId(settings.themeId)
            val ctx = LocalContext.current
            var ready by remember { mutableStateOf(hasMic(ctx)) }
            val fx = remember { DeckFx(ctx) }
            DisposableEffect(Unit) { onDispose { fx.release() } }
            LaunchedEffect(settings.haptics, settings.mechSounds) {
                fx.hapticsEnabled = settings.haptics; fx.soundsEnabled = settings.mechSounds
            }
            CompositionLocalProvider(LocalKbTheme provides theme) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFB300), secondary = Color(0xFFD32F2F))) {
                    if (!ready) PermissionScreen(theme) {
                        container.refreshCapabilities()
                        ready = true
                    } else {
                        LaunchedEffect(Unit) {
                            if (pendingResumeListening) { pendingResumeListening = false; RecorderService.send(ctx, RecorderService.ACTION_LISTEN_ON) }
                        }
                        KbNavHost(theme, fx)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() { super.onStart(); container.appVisible = true }
    override fun onStop() { container.appVisible = false; super.onStop() }

    /** Viene de la notificación "reanudar escucha" tras un reinicio. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_RESUME_LISTENING, false) == true) {
            intent.removeExtra(EXTRA_RESUME_LISTENING)
            if (hasMic(this)) RecorderService.send(this, RecorderService.ACTION_LISTEN_ON) else pendingResumeListening = true
        }
    }

    companion object {
        const val EXTRA_RESUME_LISTENING = "resume_listening"
    }
}
