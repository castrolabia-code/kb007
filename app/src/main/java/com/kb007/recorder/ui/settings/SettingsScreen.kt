package com.kb007.recorder.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kb007.recorder.audio.capture.DeviceCapabilities
import com.kb007.recorder.audio.encode.EncodeSpec
import com.kb007.recorder.audio.encode.Opus
import com.kb007.recorder.data.StorageManager
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.AudioPreset
import com.kb007.recorder.data.prefs.BITRATES_KBPS
import com.kb007.recorder.data.prefs.CallPolicy
import com.kb007.recorder.data.prefs.MicSource
import com.kb007.recorder.data.prefs.SAMPLE_RATES
import com.kb007.recorder.data.prefs.StorageTarget
import com.kb007.recorder.data.prefs.TriggerType
import com.kb007.recorder.ui.deck.CASSETTE_ASPECT
import com.kb007.recorder.ui.deck.Cassette
import com.kb007.recorder.ui.theme.KbTheme
import com.kb007.recorder.ui.theme.KbThemes
import com.kb007.recorder.ui.theme.deviceBody
import java.util.Date

/** Callbacks de la pantalla de ajustes. */
interface SettingsActions {
    fun back()
    fun update(f: (AppSettings) -> AppSettings)
    fun preset(p: AudioPreset)
    fun pickFolder()
    fun levelDb(): Float
}

@Composable
fun SettingsScreen(
    s: AppSettings,
    caps: DeviceCapabilities,
    theme: KbTheme,
    actions: SettingsActions,
    appFolder: String,
    extraSections: @Composable ColumnScope.() -> Unit = {},
) {
    val ink = Color(0xFFF1EBE0)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.screenTop, theme.screenBottom))).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = ink) }
                Text("Ajustes", style = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = theme.uiFont))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AudioSections(s, caps, theme, actions)
                extraSections()
                StorageSection(s, theme, actions, appFolder)
                InterfaceSection(s, theme, actions)
            }
        }
    }
}

// ============================ Audio ============================

@Composable
private fun AudioSections(s: AppSettings, caps: DeviceCapabilities, theme: KbTheme, a: SettingsActions) {
    Section("Presets rápidos", theme) {
        Chips(AudioPreset.entries.map { it.label }, selected = AudioPreset.entries.indexOfFirst { matches(s, it) }, theme) {
            a.preset(AudioPreset.entries[it])
        }
        Hint("Un preset completa formato, frecuencia, bitrate, canales, fuente y procesamiento. Después podés ajustar cada uno.")
    }

    Section("Formato", theme) {
        val formats = AudioFormatType.entries
        Chips(formats.map { it.label }, formats.indexOf(s.format), theme,
            enabled = formats.map { it != AudioFormatType.OPUS_OGG || caps.opus }) { i -> a.update { it.copy(format = formats[i]) } }
        Hint(when (s.format) {
            AudioFormatType.AAC_M4A -> "Recomendado: liviano, compatible con todo y con WhatsApp."
            AudioFormatType.OPUS_OGG -> "El más eficiente para voz. WhatsApp lo muestra como audio."
            AudioFormatType.WAV_PCM -> "Sin compresión: máxima fidelidad, archivos muy pesados (~5 MB por minuto a 44,1 kHz mono)."
        })
        if (!caps.opus) Hint("OGG/Opus necesita Android 10 o superior.")
    }

    Section("Frecuencia de muestreo", theme) {
        val rates = SAMPLE_RATES
        Chips(rates.map { khz(it) }, rates.indexOf(s.sampleRate), theme, enabled = rates.map { it in caps.sampleRates }) { i ->
            a.update { it.copy(sampleRate = rates[i]) }
        }
        if (s.format == AudioFormatType.OPUS_OGG && Opus.captureRateFor(s.sampleRate) != s.sampleRate)
            Hint("Opus no admite ${khz(s.sampleRate)}: se grabará a 48 kHz.")
    }

    Section("Bitrate", theme) {
        val enabled = s.format.compressed
        Chips(BITRATES_KBPS.map { "$it kbps" }, BITRATES_KBPS.indexOf(s.bitrateKbps), theme, enabled = BITRATES_KBPS.map { enabled }) { i ->
            a.update { it.copy(bitrateKbps = BITRATES_KBPS[i]) }
        }
        if (!enabled) Hint("WAV no usa bitrate (es PCM sin comprimir).")
        else {
            val eff = EncodeSpec(s.format, s.sampleRate, s.channels, s.bitrateKbps).effectiveBitrate / 1000
            if (eff < s.bitrateKbps) Hint("A ${khz(s.sampleRate)} el máximo de AAC es $eff kbps; se usará ese valor.")
        }
    }

    Section("Canales", theme) {
        Chips(listOf("Mono", "Estéreo"), if (s.channels == 2) 1 else 0, theme, enabled = listOf(true, caps.stereo)) { i ->
            a.update { it.copy(channels = i + 1) }
        }
        if (!caps.stereo) Hint("Este equipo no ofrece captura estéreo con el micrófono integrado.")
    }

    Section("Fuente de micrófono", theme) {
        MicSource.entries.forEach { src ->
            val ok = src != MicSource.UNPROCESSED || caps.unprocessed
            val note = when (src) {
                MicSource.STANDARD -> "Micrófono principal con el procesamiento por defecto del equipo"
                MicSource.VOICE -> "Ajustado para voz (el mismo que usa el reconocimiento de voz)"
                MicSource.UNPROCESSED -> if (ok) "Señal cruda, sin filtros: ideal para música" else "No disponible en este equipo"
                MicSource.CAMCORDER -> if (caps.externalMic) "Micrófono de cámara o externo conectado" else "Micrófono de cámara (suele ser estéreo)"
            }
            RadioRow(src.label, note, s.micSource == src, ok, theme) { a.update { it.copy(micSource = src) } }
        }
    }

    Section("Procesamiento", theme) {
        SwitchRow("Supresión de ruido", avail(caps.noiseSuppressor), s.noiseSuppressor, caps.noiseSuppressor, theme) { v -> a.update { it.copy(noiseSuppressor = v) } }
        SwitchRow("Control automático de ganancia", avail(caps.autoGain), s.autoGain, caps.autoGain, theme) { v -> a.update { it.copy(autoGain = v) } }
        SwitchRow("Cancelación de eco", avail(caps.echoCanceler), s.echoCanceler, caps.echoCanceler, theme) { v -> a.update { it.copy(echoCanceler = v) } }
    }

    Section("Ganancia de entrada", theme) {
        Text("%+.0f dB".format(s.gainDb), color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Slider(s.gainDb, { v -> a.update { it.copy(gainDb = Math.round(v).toFloat()) } }, valueRange = -12f..24f, steps = 35, colors = sliderColors(theme))
        LiveLevel(a::levelDb, theme)
        Hint("Ganancia digital (Android no permite ajustar la del micrófono). Hablá normal: la barra debería quedar en la zona verde, sin llegar al rojo.")
    }

    Section("Corte automático", theme) {
        Label("Detener tras silencio")
        val silences = listOf(0, 5, 10, 30, 60, 120)
        Chips(silences.map { if (it == 0) "No" else "$it s" }, silences.indexOf(s.silenceStopSec).coerceAtLeast(0), theme) { i ->
            a.update { it.copy(silenceStopSec = silences[i]) }
        }
        if (s.silenceStopSec > 0) {
            Label("Umbral de silencio: %.0f dBFS".format(s.silenceThresholdDb))
            Slider(s.silenceThresholdDb, { v -> a.update { it.copy(silenceThresholdDb = Math.round(v).toFloat()) } }, valueRange = -70f..-20f, colors = sliderColors(theme))
        }
        Label("Duración máxima")
        val maxes = listOf(0, 15, 30, 60, 120, 240)
        Chips(maxes.map { if (it == 0) "Sin límite" else if (it < 60) "$it min" else "${it / 60} h" }, maxes.indexOf(s.maxDurationMin).coerceAtLeast(0), theme) { i ->
            a.update { it.copy(maxDurationMin = maxes[i]) }
        }
    }

    Section("Llamadas", theme) {
        CallPolicy.entries.forEach { p -> RadioRow(p.label, null, s.callPolicy == p, true, theme) { a.update { it.copy(callPolicy = p) } } }
        Hint("Durante una llamada Android silencia el micrófono para otras apps; KB-007 lo detecta (también en llamadas de WhatsApp).")
    }
}

private fun matches(s: AppSettings, p: AudioPreset) = s.format == p.format && s.sampleRate == p.sampleRate &&
    (s.bitrateKbps == p.bitrateKbps || !p.format.compressed) && s.channels == p.channels && s.micSource == p.source

private fun avail(ok: Boolean) = if (ok) "Disponible en este equipo" else "No disponible en este equipo"

fun khz(rate: Int): String = if (rate % 1000 == 0) "${rate / 1000} kHz"
else String.format(java.util.Locale.US, "%.2f", rate / 1000f).trimEnd('0').replace('.', ',') + " kHz"

// ============================ Almacenamiento ============================

@Composable
private fun StorageSection(s: AppSettings, theme: KbTheme, a: SettingsActions, appFolder: String) {
    Section("Almacenamiento", theme) {
        RadioRow(StorageTarget.APP_PRIVATE.label, appFolder.substringAfter("/Android/").let { "Android/$it" }, s.storageTarget == StorageTarget.APP_PRIVATE, true, theme) {
            a.update { it.copy(storageTarget = StorageTarget.APP_PRIVATE) }
        }
        RadioRow(StorageTarget.PUBLIC_MUSIC.label, "Visible para otras apps de música y en la PC", s.storageTarget == StorageTarget.PUBLIC_MUSIC, true, theme) {
            a.update { it.copy(storageTarget = StorageTarget.PUBLIC_MUSIC) }
        }
        RadioRow(StorageTarget.CUSTOM_TREE.label, s.customTreeUri?.let { android.net.Uri.decode(it).substringAfterLast(':') }?.let { "Carpeta: $it" } ?: "Elegí una carpeta del teléfono o de la tarjeta SD",
            s.storageTarget == StorageTarget.CUSTOM_TREE, true, theme) { a.pickFolder() }
        Hint("Mientras graba, el archivo se escribe en la memoria interna de la app (así se puede recuperar si se corta). Al terminar se mueve al destino elegido.")
        Spacer(Modifier.height(6.dp))
        Label("Nombre automático del archivo")
        OutlinedTextField(
            s.namePattern, { v -> a.update { it.copy(namePattern = v.take(80)) } }, Modifier.fillMaxWidth(), singleLine = true,
            colors = fieldColors(theme),
        )
        Hint("Vista previa: ${StorageManager.formatName(s.namePattern, TriggerType.MANUAL, 12, Date())}\nComodines: {fecha} {hora} {modo} {n}")
    }
}

// ============================ Interfaz ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceSection(s: AppSettings, theme: KbTheme, a: SettingsActions) {
    Section("Interfaz", theme) {
        Label("Tema del aparato")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KbThemes.all.forEach { t -> ThemeCard(t, selected = t.id == s.themeId) { a.update { it.copy(themeId = t.id) } } }
        }
        SwitchRow("Sonidos mecánicos", "Clic de teclas, «clack» de expulsión y zumbido de motor", s.mechSounds, true, theme) { v -> a.update { it.copy(mechSounds = v) } }
        SwitchRow("Vibración", "Respuesta háptica en cada tecla", s.haptics, true, theme) { v -> a.update { it.copy(haptics = v) } }
    }
}

@Composable
private fun ThemeCard(t: KbTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) t.accent else Color.White.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).deviceBody(t, corner = 0f), contentAlignment = Alignment.Center) {
            Cassette(t, "Lado A", 0.4f, 0f, 30f, Modifier.width(104.dp).aspectRatio(CASSETTE_ASPECT))
        }
        Column(Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(8.dp)) {
            Text(t.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = t.uiFont)
            Text(t.description, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
        }
    }
}

// ============================ Componentes ============================

@Composable
fun Section(title: String, theme: KbTheme, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x66000000))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 16.dp).background(theme.accent))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), color = Color(0xFFF1EBE0), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.2.sp, fontFamily = theme.uiFont)
        }
        content()
    }
}

@Composable fun Hint(text: String) = Text(text, color = Color(0xFFB9B1A5), fontSize = 12.sp, lineHeight = 16.sp)
@Composable fun Label(text: String) = Text(text, color = Color(0xFFE6DFD3), fontSize = 14.sp, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: Int, theme: KbTheme, enabled: List<Boolean> = options.map { true }, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val en = enabled.getOrElse(i) { true }
            Text(
                label,
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) theme.accent else Color.White.copy(alpha = if (en) 0.10f else 0.03f))
                    .clickable(enabled = en) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = when { sel -> onAccent(theme); en -> Color(0xFFF1EBE0); else -> Color(0xFF6E6860) },
                fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, theme: KbTheme, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color(0xFFF1EBE0) else Color(0xFF7A746C), fontSize = 15.sp)
            subtitle?.let { Text(it, color = Color(0xFFB9B1A5), fontSize = 12.sp) }
        }
        Switch(checked && enabled, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = theme.accent, checkedThumbColor = onAccent(theme)))
    }
}

@Composable
fun RadioRow(title: String, subtitle: String?, selected: Boolean, enabled: Boolean, theme: KbTheme, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = theme.accent))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color(0xFFF1EBE0) else Color(0xFF7A746C), fontSize = 15.sp)
            subtitle?.let { Text(it, color = Color(0xFFB9B1A5), fontSize = 12.sp) }
        }
    }
}

@Composable
fun sliderColors(theme: KbTheme) = SliderDefaults.colors(
    thumbColor = theme.accent, activeTrackColor = theme.accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.18f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

/** Texto legible sobre el color de acento del tema. */
fun onAccent(theme: KbTheme): Color = if (theme.accent.luminance() > 0.5f) Color(0xFF111111) else Color.White

@Composable
fun fieldColors(theme: KbTheme) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF1EBE0), unfocusedTextColor = Color(0xFFF1EBE0),
    focusedBorderColor = theme.accent, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
)

/** Medidor de nivel de entrada en vivo (barra con zonas verde / ámbar / roja). */
@Composable
fun LiveLevel(levelDb: () -> Float, theme: KbTheme) {
    var shown by remember { mutableFloatStateOf(-90f) }
    var peak by remember { mutableFloatStateOf(-90f) }
    LaunchedEffect(Unit) {
        var lastPeakAt = 0L
        while (true) withFrameNanos { t ->
            val l = levelDb()
            shown = if (l > shown) l else shown - 0.8f // caída suave
            if (l > peak || t - lastPeakAt > 1_200_000_000L) { peak = l; lastPeakAt = t }
        }
    }
    val frac = ((shown + 60f) / 60f).coerceIn(0f, 1f)
    val color = when { shown > -6f -> Color(0xFFE53935); shown > -18f -> Color(0xFFFFB300); else -> Color(0xFF66BB6A) }
    Column {
        LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = color, trackColor = Color(0x33FFFFFF))
        Text("Nivel: %.0f dBFS · pico %.0f".format(shown.coerceAtLeast(-60f), peak.coerceAtLeast(-60f)),
            color = Color(0xFFB9B1A5), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
