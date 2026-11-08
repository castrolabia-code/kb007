package com.kb007.recorder.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.ui.theme.KbTheme
import com.kb007.recorder.wakeword.KeywordEvent

/** Estado de la validación de vocabulario. null = verificando / modelo no cargado. */
data class VocabCheck(val unknownKeyword: List<String>?, val unknownStop: List<String>?, val modelMissing: Boolean = false)

@Composable
fun KeywordSection(
    s: AppSettings,
    theme: KbTheme,
    vocab: VocabCheck,
    batteryExempt: Boolean,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onTest: () -> Unit,
    onBattery: () -> Unit,
) {
    Section("Palabra clave (offline)", theme) {
        Hint("Con el switch PALABRA CLAVE del aparato encendido, KB-007 escucha (sin internet) y empieza a grabar al oír esta frase, incluso con la pantalla apagada.")
        Label("Frase de activación")
        OutlinedTextField(s.keyword, { v -> update { it.copy(keyword = v.take(40)) } }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors(theme))
        VocabLine(vocab.unknownKeyword, vocab.modelMissing, required = true)
        Hint("Consejo: 1 a 3 palabras poco comunes en una charla (ej. «grabadora», «atención grabadora»). Palabras cortas o frecuentes dan falsos positivos.")

        Label("Palabra de detención (opcional)")
        OutlinedTextField(s.stopKeyword, { v -> update { it.copy(stopKeyword = v.take(40)) } }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("p. ej. «basta»", color = Color(0xFF8A847B)) }, colors = fieldColors(theme))
        if (s.stopKeyword.isNotBlank()) VocabLine(vocab.unknownStop, vocab.modelMissing, required = false)

        Label("Sensibilidad: confianza mínima ${(s.keywordThreshold * 100).toInt()} %")
        Slider(s.keywordThreshold, { v -> update { it.copy(keywordThreshold = (Math.round(v * 20) / 20f)) } }, valueRange = 0.3f..0.95f, colors = sliderColors(theme))
        Hint("Más bajo = reacciona más fácil (y se equivoca más). Más alto = solo con la frase bien clara.")

        Button(onClick = onTest, colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = onAccent(theme))) { Text("Probar palabra clave") }

        Label("Pre-grabación")
        val pre = listOf(0, 2, 3, 4, 5)
        Chips(pre.map { if (it == 0) "No" else "$it s" }, pre.indexOf(s.preRollSec).coerceAtLeast(0), theme) { i -> update { it.copy(preRollSec = pre[i]) } }
        Hint("Incluye en el archivo lo que se dijo justo antes de la palabra clave.")

        Label("Cortar tras silencio (grabaciones por palabra clave)")
        val sil = listOf(0, 5, 8, 15, 30, 60)
        Chips(sil.map { if (it == 0) "No" else "$it s" }, sil.indexOf(s.keywordSilenceStopSec).coerceAtLeast(0), theme) { i -> update { it.copy(keywordSilenceStopSec = sil[i]) } }

        SwitchRow("Vibrar al detectar", null, s.keywordVibrate, true, theme) { v -> update { it.copy(keywordVibrate = v) } }
        SwitchRow("Beep corto al detectar", "Suena bajo; puede quedar registrado al comienzo", s.keywordBeep, true, theme) { v -> update { it.copy(keywordBeep = v) } }
        SwitchRow("Avisar para reanudar tras reiniciar", "Android no permite reabrir el micrófono solo al encender el teléfono: aparece un aviso para reactivarlo con un toque", s.resumeAfterBoot, true, theme) { v -> update { it.copy(resumeAfterBoot = v) } }

        Label("Batería")
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(if (batteryExempt) Color(0xFF66BB6A) else Color(0xFFFFB300)))
            Text(if (batteryExempt) "  Sin restricciones de batería" else "  Optimización de batería activa", color = Color(0xFFF1EBE0), fontSize = 14.sp)
        }
        Hint("Para escuchar con la pantalla apagada durante horas, Android (y sobre todo Xiaomi, Samsung, Huawei) necesita que KB-007 no esté optimizada. Consumo estimado: 3–8 % de batería por hora de escucha.")
        if (!batteryExempt) TextButton(onClick = onBattery) { Text("Quitar optimización de batería", color = theme.accent) }
    }
}

@Composable
private fun VocabLine(unknown: List<String>?, modelMissing: Boolean, required: Boolean) {
    val (txt, color) = when {
        modelMissing -> "No se encontró el modelo de voz en esta instalación." to Color(0xFFE57373)
        unknown == null -> "Verificando vocabulario…" to Color(0xFFB9B1A5)
        unknown.isEmpty() -> "✓ Todas las palabras están en el vocabulario del modelo" to Color(0xFF81C784)
        else -> "✗ El modelo no conoce: ${unknown.joinToString()}" + (if (required) ". No se va a poder detectar." else ".") to Color(0xFFE57373)
    }
    Text(txt, color = color, fontSize = 12.sp)
}

/** Diálogo de prueba: muestra en vivo lo que oye el reconocedor. */
@Composable
fun KeywordTestDialog(keyword: String, threshold: Float, ready: Boolean, heard: List<KeywordEvent.Heard>, lastError: String?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Probar «$keyword»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ready) "Escuchando… decí la frase varias veces, a distintas distancias." else "Cargando el modelo de voz…", fontSize = 14.sp)
                Text("Umbral: ${(threshold * 100).toInt()} %. En esta prueba no se graba nada.", fontSize = 12.sp, color = Color(0xFF9E978D))
                lastError?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp) }
                heard.takeLast(6).reversed().forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (h.accepted) Color(0x3366BB6A) else Color(0x22FFFFFF)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (h.accepted) "✓" else "·", fontWeight = FontWeight.Bold, color = if (h.accepted) Color(0xFF81C784) else Color.Gray)
                        Text("  ${h.text}", Modifier.weight(1f), fontSize = 14.sp)
                        if (h.confidence > 0f) Text("${(h.confidence * 100).toInt()} %", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Listo") } },
    )
}

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

/** Pide la exclusión de optimización de batería (permitido para apps de uso personal fuera de Play Store). */
@SuppressLint("BatteryLife")
fun requestIgnoreBattery(ctx: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    try {
        ctx.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
