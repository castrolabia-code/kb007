package com.kb007.recorder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kb007.recorder.audio.engine.RecMode
import com.kb007.recorder.service.RecorderService

/** Fase 1: pantalla mínima para probar el motor. En la fase 2 se reemplaza por el walkman. */
class MainActivity : ComponentActivity() {

    private val container get() = (application as KbApp).container

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.refreshCapabilities()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val st by container.engine.state.collectAsStateWithLifecycle()
                    val lvl by container.engine.level.collectAsStateWithLifecycle()
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("KB-007 — motor (fase 1)", style = MaterialTheme.typography.titleLarge)
                        Text("Modo: ${st.mode}  ·  Escucha: ${st.listening}  ·  ${st.elapsedMs / 1000}s")
                        LinearProgressIndicator(progress = { lvl.normalized }, Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_RECORD) }) { Text("REC") }
                            Button(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_TOGGLE_PAUSE) }) { Text(if (st.mode == RecMode.PAUSED) "Seguir" else "Pausa") }
                            Button(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_STOP_REC) }) { Text("STOP") }
                        }
                        Button(onClick = {
                            RecorderService.send(this@MainActivity, if (st.listening) RecorderService.ACTION_LISTEN_OFF else RecorderService.ACTION_LISTEN_ON)
                        }) { Text(if (st.listening) "Apagar escucha" else "Encender escucha") }
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
            RecorderService.send(this, RecorderService.ACTION_LISTEN_ON)
        }
    }

    companion object {
        const val EXTRA_RESUME_LISTENING = "resume_listening"
    }
}
