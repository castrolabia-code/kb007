package com.kb007.recorder.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kb007.recorder.ui.theme.KbTheme

fun hasPermission(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun hasMic(ctx: Context) = hasPermission(ctx, Manifest.permission.RECORD_AUDIO)

fun needsNotificationPermission(ctx: Context) =
    Build.VERSION.SDK_INT >= 33 && !hasPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)

/** Paso a paso: explica para qué es cada permiso antes de pedirlo. */
@Composable
fun PermissionScreen(theme: KbTheme, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(if (hasMic(ctx)) 1 else 0) }
    var denials by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (step == 0) {
            if (granted) step = 1 else denials++
        } else onDone()
    }
    if (step == 1 && !needsNotificationPermission(ctx)) { onDone(); return }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.screenTop, theme.screenBottom))).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 460.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF3E9D2)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("KB-007", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF2B2420))
            if (step == 0) {
                Text("Necesito el micrófono", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2420))
                Text(
                    "Es una grabadora: sin micrófono no puede grabar ni escuchar tu palabra clave. " +
                        "El audio se procesa solo en tu teléfono; nada se envía a internet.",
                    color = Color(0xFF4A3F37), fontSize = 15.sp,
                )
                if (denials >= 2) {
                    Text("Android ya no muestra el pedido. Activalo a mano en Ajustes › Permisos › Micrófono.", color = Color(0xFFB3261E), fontSize = 14.sp)
                    Button(onClick = { openAppSettings(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2420))) { Text("Abrir ajustes de la app") }
                    TextButton(onClick = { if (hasMic(ctx)) step = 1 }) { Text("Ya lo activé", color = Color(0xFF2B2420)) }
                } else {
                    Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Permitir micrófono") }
                }
            } else {
                Text("Avisos en la barra de notificaciones", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2420))
                Text(
                    "Mientras graba o escucha la palabra clave con la pantalla apagada, KB-007 muestra una notificación fija " +
                        "con el estado y botones para detener o grabar. Android la exige para que el micrófono siga funcionando en segundo plano.",
                    color = Color(0xFF4A3F37), fontSize = 15.sp,
                )
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2420))) { Text("Permitir notificaciones") }
                TextButton(onClick = onDone) { Text("Ahora no", color = Color(0xFF4A3F37)) }
            }
        }
    }
}

fun openAppSettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
