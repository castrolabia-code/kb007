package com.kb007.recorder.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.db.RecordingStatus
import com.kb007.recorder.data.prefs.TriggerType
import com.kb007.recorder.ui.deck.Cassette
import com.kb007.recorder.ui.deck.formatTime
import com.kb007.recorder.ui.theme.KbTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Acciones de la lista (separadas del ViewModel para poder previsualizar). */
interface LibraryActions {
    fun back()
    fun setQuery(q: String)
    fun setSort(s: SortBy)
    fun load(r: Recording)
    fun rename(r: Recording, title: String)
    fun delete(r: Recording)
    fun share(r: Recording, toWhatsApp: Boolean, convert: Boolean)
    fun needsConversion(r: Recording): Boolean
    fun whatsAppInstalled(): Boolean
}

private val esDate = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale("es", "AR"))

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale("es"), "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale("es"), "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(Locale("es"), "%.0f kB", bytes / 1e3)
    else -> "$bytes B"
}

@Composable
fun LibraryScreen(ui: LibraryUi, theme: KbTheme, actions: LibraryActions, message: String?) {
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    var sharing by remember { mutableStateOf<Pair<Recording, Boolean>?>(null) } // (grabación, aWhatsApp) pendiente de decidir conversión

    fun requestShare(r: Recording, wa: Boolean) {
        if (actions.needsConversion(r)) sharing = r to wa else actions.share(r, wa, convert = false)
    }

    Box(Modifier.fillMaxSize().shelfBackground(theme).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            // ---------- Barra superior ----------
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al grabador", tint = theme.label) }
                Column(Modifier.weight(1f)) {
                    Text("Estante de cassettes", style = TextStyle(color = theme.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = theme.uiFont))
                    Text("${ui.total} grabaciones", style = TextStyle(color = theme.label.copy(alpha = 0.7f), fontSize = 12.sp))
                }
            }
            OutlinedTextField(
                value = ui.query, onValueChange = actions::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar por nombre o palabra clave") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x33000000), unfocusedContainerColor = Color(0x33000000),
                    focusedTextColor = theme.label, unfocusedTextColor = theme.label,
                    focusedBorderColor = theme.accent, unfocusedBorderColor = theme.label.copy(alpha = 0.3f),
                    focusedLeadingIconColor = theme.label, unfocusedLeadingIconColor = theme.label.copy(alpha = 0.7f),
                    focusedPlaceholderColor = theme.label.copy(alpha = 0.5f), unfocusedPlaceholderColor = theme.label.copy(alpha = 0.5f),
                ),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ordenar:", color = theme.label.copy(alpha = 0.7f), fontSize = 13.sp)
                SortBy.entries.forEach { s ->
                    val sel = ui.sort == s
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) theme.accent else Color(0x33000000))
                            .clickable { actions.setSort(s) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.label, color = if (sel) Color.White else theme.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (sel) Icon(if (ui.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            if (ui.descending) "descendente" else "ascendente", Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }

            if (ui.items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (ui.total == 0) "El estante está vacío.\nVolvé al grabador y pulsá REC." else "Ninguna grabación coincide con «${ui.query}».",
                        color = theme.label.copy(alpha = 0.8f), fontSize = 16.sp, style = TextStyle(fontFamily = theme.labelFont),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(ui.items, key = { it.id }) { r ->
                        CassetteCase(
                            r, theme, waInstalled = actions.whatsAppInstalled(),
                            onLoad = { actions.load(r) }, onRename = { renaming = r }, onDelete = { deleting = r },
                            onWhatsApp = { requestShare(r, true) }, onShare = { requestShare(r, false) },
                        )
                    }
                }
            }
        }

        if (message != null) {
            Text(message, Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6101010)).padding(horizontal = 16.dp, vertical = 10.dp), color = Color(0xFFF3E9D2))
        }
    }

    // ---------- Diálogos ----------
    renaming?.let { r ->
        var text by remember(r.id) { mutableStateOf(r.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar cassette") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Nombre") }) },
            confirmButton = { TextButton(onClick = { actions.rename(r, text); renaming = null }, enabled = text.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar «${r.title}»?") },
            text = { Text("Se borra el archivo de audio. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { actions.delete(r); deleting = null }) { Text("Eliminar", color = Color(0xFFE57373)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
    sharing?.let { (r, wa) ->
        AlertDialog(
            onDismissRequest = { sharing = null },
            title = { Text("Archivo WAV sin comprimir") },
            text = {
                Text("Este audio pesa ${formatSize(r.sizeBytes)}. " +
                    (if (wa) "WhatsApp lo envía como documento y tarda en subir. " else "") +
                    "¿Lo convierto a M4A (AAC 128 kbps), que pesa unas 10 veces menos?")
            },
            confirmButton = { TextButton(onClick = { actions.share(r, wa, convert = true); sharing = null }) { Text("Convertir y enviar") } },
            dismissButton = { TextButton(onClick = { actions.share(r, wa, convert = false); sharing = null }) { Text("Enviar WAV igual") } },
        )
    }
    ui.converting?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Convirtiendo a M4A…") },
            text = { LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
}

/** Fondo de estante: madera oscura con tablas horizontales. */
private fun Modifier.shelfBackground(theme: KbTheme): Modifier = this
    .background(Brush.verticalGradient(listOf(theme.screenTop, theme.screenBottom)))
    .drawBehind {
        // Tablas del estante: una línea de sombra y una de luz cada ~190 dp
        val step = 190.dp.toPx()
        var y = step * 0.8f
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = 0.28f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y + 6.dp.toPx()), Offset(size.width, y + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            y += step
        }
    }

/** Una grabación presentada como estuche de cassette con su tarjeta (J-card). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CassetteCase(
    r: Recording,
    theme: KbTheme,
    waInstalled: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onWhatsApp: () -> Unit,
    onShare: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(onClick = onLoad),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cassette(theme, r.title, progress = 0.5f, leftAngle = (r.id * 37 % 360).toFloat(), rightAngle = (r.id * 71 % 360).toFloat(),
                modifier = Modifier.width(118.dp).aspectRatio(com.kb007.recorder.ui.deck.CASSETTE_ASPECT))
            Spacer(Modifier.width(12.dp))
            // J-card
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(theme.label).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(theme.labelStripe))
                Text(r.title, style = TextStyle(fontFamily = theme.labelFont, fontSize = 18.sp, color = theme.labelInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(esDate.format(Date(r.createdAt)).replaceFirstChar { it.uppercase() }, style = TextStyle(fontSize = 12.sp, color = theme.labelInk.copy(alpha = 0.8f)))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag(formatTime(r.durationMs), theme)
                    Tag(formatSize(r.sizeBytes), theme)
                    Tag(if (r.trigger == TriggerType.KEYWORD) "CLAVE" + (r.keyword?.let { " «$it»" } ?: "") else "MANUAL", theme,
                        highlight = r.trigger == TriggerType.KEYWORD)
                    if (r.status == RecordingStatus.RECOVERED) Tag("RECUPERADA", theme, highlight = true)
                }
                Text(r.qualityLabel, style = TextStyle(fontSize = 11.sp, color = theme.labelInk.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0x33000000)).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            CaseAction(Icons.Filled.PlayArrow, "Cargar", theme, onLoad)
            CaseAction(Icons.Filled.Edit, "Renombrar", theme, onRename)
            if (waInstalled) CaseAction(Icons.Filled.Chat, "WhatsApp", theme, onWhatsApp, tint = Color(0xFF7BE495))
            CaseAction(Icons.Filled.Share, "Compartir", theme, onShare)
            CaseAction(Icons.Filled.Delete, "Eliminar", theme, onDelete, tint = Color(0xFFFF8A80))
        }
    }
}

@Composable
private fun Tag(text: String, theme: KbTheme, highlight: Boolean = false) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(4.dp)).background(if (highlight) theme.labelStripe else theme.labelInk.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (highlight) Color.White else theme.labelInk),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CaseAction(icon: ImageVector, label: String, theme: KbTheme, onClick: () -> Unit, tint: Color = theme.label) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp).widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}
