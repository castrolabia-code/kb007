package com.kb007.recorder.ui.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.kb007.recorder.ui.fx.DeckFx
import com.kb007.recorder.ui.theme.KbTheme
import com.kb007.recorder.ui.theme.deviceBody
import com.kb007.recorder.ui.theme.screw
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Estado animado de los carretes y del contador (se actualiza en cada frame). */
@Stable
class ReelAnimState {
    var left by mutableFloatStateOf(0f)
    var right by mutableFloatStateOf(0f)
    var counter by mutableFloatStateOf(0f)
    /** Velocidad efectiva con inercia del motor. */
    var speed by mutableFloatStateOf(0f)
}

/** Velocidad angular del cubo con el rollo vacío a velocidad de reproducción (grados/s). */
private const val HUB_DEG_PER_SEC = 260f

@Composable
fun rememberReelAnimation(targetSpeed: () -> Float, progress: () -> Float): ReelAnimState {
    val st = remember { ReelAnimState() }
    val speedFn by rememberUpdatedState(targetSpeed)
    val progFn by rememberUpdatedState(progress)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = ((t - last) / 1e9f).coerceAtMost(0.05f)
                    // Inercia: el motor tarda ~0,15 s en llegar a velocidad (y más en bobinado).
                    val target = speedFn()
                    val tau = if (kotlin.math.abs(target) > 2f || kotlin.math.abs(st.speed) > 2f) 0.35f else 0.12f
                    st.speed += (target - st.speed) * (1f - exp(-dt / tau))
                    if (kotlin.math.abs(st.speed) > 0.001f) {
                        val (rl, rr) = CassetteGeom.packRadii(progFn())
                        // Velocidad lineal de cinta constante → el rollo chico gira más rápido (ω = v / r)
                        val v = HUB_DEG_PER_SEC * CassetteGeom.PACK_MIN * st.speed
                        // Cinta de izquierda a derecha por abajo = ambos cubos giran antihorario
                        st.left -= v / rl * dt
                        st.right -= v / rr * dt
                        st.counter += (v / rr * dt) / 360f * 0.5f
                    }
                }
                last = t
            }
        }
    }
    return st
}

/** Acciones y lecturas en tiempo real que la pantalla necesita (separadas para poder previsualizar). */
interface DeckActions {
    /** Nivel actual en dBFS (grabación/escucha o reproducción). */
    fun levelDb(): Float
    fun onKey(key: DeckKey)
    fun toggleListening()
}

/** La pantalla principal: el aparato completo. */
@Composable
fun DeckScreen(
    vm: DeckActions,
    theme: KbTheme,
    fx: DeckFx,
    ui: DeckUi,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    message: String?,
) {
    val reels = rememberReelAnimation({ ui.tapeSpeed }, { ui.progress })
    LaunchedEffect(ui.transport, ui.paused, ui.recording) {
        fx.motor(running = ui.tapeSpeed != 0f, fast = kotlin.math.abs(ui.tapeSpeed) > 2f, recording = ui.recording)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.screenTop, theme.screenBottom)))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val u = min(maxWidth, maxHeight / 1.36f)
            DeviceBody(vm, theme, fx, ui, reels, u, onOpenLibrary, onOpenSettings)
        }
        if (message != null) {
            Text(
                message,
                Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xE6101010)).padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color(0xFFF3E9D2), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeviceBody(
    vm: DeckActions,
    theme: KbTheme,
    fx: DeckFx,
    ui: DeckUi,
    reels: ReelAnimState,
    u: Dp,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pad = u * 0.045f
    val small = (u.value * 0.026f).sp
    val cornerPx = with(LocalDensity.current) { (u * 0.06f).toPx() }
    Box(
        Modifier.width(u * 0.96f).deviceBody(theme, corner = cornerPx),
    ) {
        Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(u * 0.03f)) {
            // ---------------- Cabecera: marca, LEDs y VU ----------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("KB-007", style = TextStyle(fontFamily = theme.brandFont, fontWeight = theme.brandWeight,
                        fontSize = (u.value * 0.09f).sp, color = theme.ink, letterSpacing = (u.value * 0.004f).sp))
                    Box(Modifier.height(u * 0.008f).width(u * 0.3f).background(theme.accent))
                    Spacer(Modifier.height(u * 0.008f))
                    Text("STEREO CASSETTE RECORDER", style = TextStyle(fontFamily = theme.uiFont, fontSize = (u.value * 0.022f).sp,
                        color = theme.inkMuted, fontWeight = FontWeight.Bold, letterSpacing = (u.value * 0.002f).sp))
                    Text("AUTO-KEY · ANTI-ROLL · 2 MOTOR", style = TextStyle(fontFamily = theme.uiFont, fontSize = (u.value * 0.018f).sp, color = theme.inkMuted))
                    Spacer(Modifier.height(u * 0.025f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 0.025f)) {
                        LedWithLabel("REC", Color(0xFFFF3B30), ui.recording && !ui.paused, blink = true, u = u, theme = theme)
                        LedWithLabel("PAUSA", Color(0xFFFFE082), ui.paused, blink = false, u = u, theme = theme)
                        LedWithLabel("CLAVE", Color(0xFFFFB300), ui.listening, blink = ui.listening && !ui.detectorReady, u = u, theme = theme)
                    }
                }
                VuMeter(theme, { vm.levelDb() },
                    lit = ui.micOpen || ui.transport == Transport.PLAY,
                    modifier = Modifier.width(u * 0.38f).height(u * 0.23f))
            }

            // ---------------- Compartimiento del cassette ----------------
            CassetteBay(theme, ui, reels, u)

            // ---------------- Contador y display ----------------
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 0.03f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TapeCounter(theme, { reels.counter }, Modifier.width(u * 0.22f).height(u * 0.09f)
                        .clickable { reels.counter = 0f; fx.press(DeckFx.Kind.KNOB) })
                    Text("CONTADOR · RESET", style = TextStyle(color = theme.inkMuted, fontSize = (u.value * 0.017f).sp, fontWeight = FontWeight.Bold))
                }
                StatusDisplay(theme, ui, u, Modifier.weight(1f).height(u * 0.09f))
            }

            // ---------------- Controles secundarios ----------------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SmallKnob("LISTA", theme, u * 0.095f, small, onClick = { fx.press(DeckFx.Kind.KNOB); onOpenLibrary() }) { c, s, col ->
                    for (i in -1..1) drawLine(col, Offset(c.x - s, c.y + i * s * 0.55f), Offset(c.x + s, c.y + i * s * 0.55f), strokeWidth = s * 0.28f)
                }
                SmallKnob("AJUSTES", theme, u * 0.095f, small, onClick = { fx.press(DeckFx.Kind.KNOB); onOpenSettings() }) { c, s, col ->
                    drawCircle(col, s * 0.55f, c, style = Stroke(s * 0.3f))
                    for (i in 0 until 8) {
                        val a = i * PI.toFloat() / 4
                        drawLine(col, c + Offset(cos(a) * s * 0.7f, sin(a) * s * 0.7f), c + Offset(cos(a) * s * 1.05f, sin(a) * s * 1.05f), strokeWidth = s * 0.3f)
                    }
                }
                SlideSwitch("PALABRA CLAVE", ui.listening, theme, u * 0.15f, small, onToggle = {
                    fx.press(DeckFx.Kind.KNOB); vm.toggleListening()
                })
            }

            // ---------------- Botonera ----------------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(u * 0.012f)) {
                val keys = listOf(
                    DeckKey.REC to "REC", DeckKey.PLAY to "PLAY", DeckKey.REW to "REW",
                    DeckKey.FF to "FF", DeckKey.STOP to "STOP/EJ", DeckKey.PAUSE to "PAUSA",
                )
                keys.forEach { (k, label) ->
                    MechKey(k, label, theme, locked = k in ui.locked, labelSize = (u.value * 0.022f).sp, modifier = Modifier.weight(1f), onPress = {
                        fx.press(if (k == DeckKey.STOP && ui.transport == Transport.STOP && ui.hasTape) DeckFx.Kind.EJECT else DeckFx.Kind.KEY)
                        vm.onKey(k)
                    })
                }
            }
        }
        // Tornillos del gabinete
        Canvas(Modifier.matchParentSize()) {
            val r = u.toPx() * 0.012f
            val m = u.toPx() * 0.025f
            listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))
                .forEachIndexed { i, o -> screw(o, r, angleDeg = 30f + i * 33f) }
        }
    }
}

@Composable
private fun LedWithLabel(label: String, color: Color, on: Boolean, blink: Boolean, u: Dp, theme: KbTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Led(color, on, Modifier.size(u * 0.03f), blink = blink)
        Spacer(Modifier.height(u * 0.006f))
        Text(label, style = TextStyle(color = theme.inkMuted, fontSize = (u.value * 0.017f).sp, fontWeight = FontWeight.Bold))
    }
}

/** Display de estado: tiempo y modo, con estética de visor iluminado. */
@Composable
private fun StatusDisplay(theme: KbTheme, ui: DeckUi, u: Dp, modifier: Modifier) {
    val glow = Color(0xFFFFB74D)
    Box(
        modifier.clip(RoundedCornerShape(u * 0.015f)).background(Color(0xFF0B0A08)).padding(horizontal = u * 0.02f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            val time = formatTime(ui.timeMs) + if (ui.durationMs > 0) " / " + formatTime(ui.durationMs) else ""
            Text(time, style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (u.value * 0.04f).sp, color = glow),
                maxLines = 1)
            Text(ui.status, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (u.value * 0.02f).sp, color = glow.copy(alpha = 0.75f)),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Compartimiento con tapa transparente. Al cambiar de cassette, el viejo sale y el nuevo entra
 * deslizándose; sin cassette se ven los ejes y el cabezal.
 */
@Composable
private fun CassetteBay(theme: KbTheme, ui: DeckUi, reels: ReelAnimState, u: Dp) {
    val slide = remember { Animatable(0f) }
    var shownTitle by remember { mutableStateOf(ui.tapeTitle) }
    var shownHasTape by remember { mutableStateOf(ui.hasTape) }
    LaunchedEffect(ui.tapeId, ui.hasTape) {
        if (shownHasTape) slide.animateTo(1f, tween(260)) // sale el cassette anterior
        shownHasTape = ui.hasTape
        shownTitle = ui.tapeTitle
        if (ui.hasTape) {
            slide.snapTo(1f)
            slide.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        } else slide.snapTo(0f)
    }
    // El título puede cambiar sin cambiar de cassette (renombrar).
    LaunchedEffect(ui.tapeTitle) { if (ui.hasTape) shownTitle = ui.tapeTitle }

    Box(
        Modifier.fillMaxWidth().aspectRatio(1.52f)
            .clip(RoundedCornerShape(u * 0.03f))
            .background(theme.windowFrame)
            .padding(u * 0.018f)
            .clip(RoundedCornerShape(u * 0.02f))
            .background(Color(0xFF0D0D0E)),
        contentAlignment = Alignment.Center,
    ) {
        // Mecanismo del fondo (visible sin cassette)
        Canvas(Modifier.fillMaxSize()) { drawEmptyMechanism(reels) }
        if (shownHasTape) {
            Cassette(
                theme, shownTitle, ui.progress, reels.left, reels.right,
                Modifier.fillMaxWidth(0.88f).aspectRatio(CASSETTE_ASPECT)
                    .graphicsLayer {
                        translationY = -size.height * 1.25f * slide.value
                        alpha = 1f - slide.value * 0.6f
                    },
            )
        }
        // Tapa: vidrio tintado con reflejo y serigrafía
        Canvas(Modifier.fillMaxSize()) {
            drawRect(theme.glassTint)
            drawRect(Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.22f), 0.18f to Color.White.copy(alpha = 0.05f), 0.19f to Color.Transparent,
                0.55f to Color.Transparent, 0.56f to Color.White.copy(alpha = 0.07f), 0.66f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width, size.height),
            ))
        }
        Text("DOLBY-FREE · TYPE I NORMAL", Modifier.align(Alignment.TopEnd).padding(u * 0.012f),
            style = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = (u.value * 0.016f).sp, fontWeight = FontWeight.Bold))
    }
}

/** Ejes dentados y cabezal del aparato. */
private fun DrawScope.drawEmptyMechanism(reels: ReelAnimState) {
    val w = size.width
    val h = size.height
    val cw = w * 0.88f
    val ch = cw / CASSETTE_ASPECT
    val left = (w - cw) / 2
    val top = (h - ch) / 2
    val y = top + ch * CassetteGeom.HUB_Y
    listOf(left + cw * (0.5f - CassetteGeom.HUB_X_OFFSET) to reels.left, left + cw * (0.5f + CassetteGeom.HUB_X_OFFSET) to reels.right).forEach { (x, ang) ->
        val c = Offset(x, y)
        drawCircle(Color(0xFF2A2A2C), cw * 0.05f, c)
        rotate(ang, c) {
            for (i in 0 until 6) {
                val a = i * PI.toFloat() / 3
                drawLine(Color(0xFFB0B4BA), c, c + Offset(cos(a) * cw * 0.03f, sin(a) * cw * 0.03f), strokeWidth = cw * 0.008f)
            }
        }
        drawCircle(Color(0xFFD0D3D8), cw * 0.012f, c)
    }
    // Cabezal, rodillo de presión y cabrestante
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE0E3E7), Color(0xFF7E848C))), Offset(left + cw * 0.43f, top + ch * 0.86f), Size(cw * 0.14f, ch * 0.12f), CornerRadius(cw * 0.01f))
    drawCircle(Color(0xFF151515), cw * 0.022f, Offset(left + cw * 0.70f, top + ch * 0.93f))
    drawCircle(Color(0xFFC9CDD2), cw * 0.006f, Offset(left + cw * 0.70f, top + ch * 0.93f))
    drawRoundRect(Color(0xFF3A3A3C), Offset(0f, 0f), size, CornerRadius(8f), style = Stroke(3f))
}
