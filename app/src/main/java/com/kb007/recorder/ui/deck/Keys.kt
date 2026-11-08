package com.kb007.recorder.ui.deck

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.kb007.recorder.ui.theme.KbTheme

/** Ícono serigrafiado de cada tecla. */
private fun DrawScope.keyIcon(key: DeckKey, c: Offset, s: Float, color: Color) {
    when (key) {
        DeckKey.REC -> drawCircle(color, s * 0.42f, c)
        DeckKey.PLAY -> drawPath(Path().apply {
            moveTo(c.x - s * 0.32f, c.y - s * 0.42f); lineTo(c.x + s * 0.46f, c.y); lineTo(c.x - s * 0.32f, c.y + s * 0.42f); close()
        }, color)
        DeckKey.STOP -> {
            drawRect(color, Offset(c.x - s * 0.36f, c.y - s * 0.12f), Size(s * 0.72f, s * 0.5f))
            drawPath(Path().apply { // símbolo de EJECT arriba
                moveTo(c.x - s * 0.36f, c.y - s * 0.24f); lineTo(c.x, c.y - s * 0.58f); lineTo(c.x + s * 0.36f, c.y - s * 0.24f); close()
            }, color)
        }
        DeckKey.PAUSE -> {
            drawRect(color, Offset(c.x - s * 0.32f, c.y - s * 0.4f), Size(s * 0.2f, s * 0.8f))
            drawRect(color, Offset(c.x + s * 0.12f, c.y - s * 0.4f), Size(s * 0.2f, s * 0.8f))
        }
        DeckKey.FF, DeckKey.REW -> {
            val dir = if (key == DeckKey.FF) 1f else -1f
            for (k in 0..1) {
                val ox = c.x + dir * (k * s * 0.38f - s * 0.2f)
                drawPath(Path().apply {
                    moveTo(ox - dir * s * 0.22f, c.y - s * 0.36f); lineTo(ox + dir * s * 0.24f, c.y); lineTo(ox - dir * s * 0.22f, c.y + s * 0.36f); close()
                }, color)
            }
        }
    }
}

/**
 * Tecla mecánica: se hunde al presionar, queda trabada ([locked]) a media carrera
 * y proyecta sombra según la altura. La acción se dispara al tocar fondo (pulsación).
 */
@Composable
fun MechKey(
    key: DeckKey,
    label: String,
    theme: KbTheme,
    locked: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    labelSize: TextUnit,
) {
    var finger by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val target = when { finger -> 1f; locked -> 0.62f; else -> 0f }
    val travel by animateFloatAsState(
        target,
        if (finger) spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), // rebote al soltar
        label = "travel",
    )
    val isRec = key == DeckKey.REC
    val top = if (isRec) theme.recKeyTop else theme.keyTop
    val side = if (isRec) theme.recKeySide else theme.keySide
    // PLAY lleva el color de acento del tema, salvo que no contraste con la tecla.
    val icon = when {
        isRec -> Color.White.copy(alpha = 0.92f)
        key == DeckKey.PLAY && kotlin.math.abs(theme.accent.luminance() - top.luminance()) > 0.25f -> theme.accent
        else -> theme.keyIcon
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = TextStyle(color = theme.ink, fontSize = labelSize, fontWeight = FontWeight.Bold, fontFamily = theme.uiFont, letterSpacing = labelSize * 0.1f))
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    stateDescription = if (locked) "trabada" else "suelta"
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        finger = true
                        currentOnPress()
                        tryAwaitRelease()
                        finger = false
                    })
                },
        ) {
            val w = size.width
            val h = size.height
            val depth = h * 0.22f
            val inset = w * 0.04f
            val y = depth * travel
            val faceH = h - depth
            // Ranura en el cuerpo
            drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(0f, depth * 0.55f), Size(w, h - depth * 0.55f), CornerRadius(w * 0.08f))
            // Sombra proyectada (se achica al hundirse)
            drawRoundRect(Color.Black.copy(alpha = 0.35f * (1f - travel)), Offset(inset + w * 0.03f, y + depth * 0.4f), Size(w - inset * 2, faceH + depth * (1 - travel) * 0.5f), CornerRadius(w * 0.08f))
            // Lateral (frente de la tecla) visible según la altura
            drawRoundRect(Brush.verticalGradient(listOf(side, side.copy(alpha = 0.85f))), Offset(inset, y), Size(w - inset * 2, faceH + depth * (1f - travel)), CornerRadius(w * 0.08f))
            // Cara superior
            drawRoundRect(
                Brush.verticalGradient(listOf(lighten(top, 0.28f), top, darken(top, 0.12f)), y, y + faceH),
                Offset(inset, y), Size(w - inset * 2, faceH), CornerRadius(w * 0.08f),
            )
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(inset + w * 0.04f, y + faceH * 0.06f), Size(w - inset * 2 - w * 0.08f, faceH * 0.12f), CornerRadius(w * 0.05f))
            drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(inset, y), Size(w - inset * 2, faceH), CornerRadius(w * 0.08f), style = Stroke(1.5f))
            keyIcon(key, Offset(w / 2f, y + faceH * 0.55f), faceH * 0.42f, icon)
        }
    }
}

fun lighten(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)
fun darken(c: Color, f: Float) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)

// ======================= Controles secundarios =======================

/** Botoncito redondo moleteado (LISTA, AJUSTES). */
@Composable
fun SmallKnob(
    label: String,
    theme: KbTheme,
    knobSize: Dp,
    labelSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: DrawScope.(Offset, Float, Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessHigh), label = "knob")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(knobSize * 0.08f)) {
        Canvas(
            Modifier.size(knobSize)
                .semantics { contentDescription = label; role = Role.Button }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { pressed = true; val ok = tryAwaitRelease(); pressed = false; if (ok) onClick() })
                },
        ) {
            val r = size.minDimension / 2f
            val c = center + Offset(0f, r * 0.06f * press)
            drawCircle(Color.Black.copy(alpha = 0.45f * (1 - press)), r * 0.95f, center + Offset(r * 0.08f, r * 0.12f))
            drawCircle(Color.Black.copy(alpha = 0.6f), r * 0.98f, c)
            drawCircle(Brush.radialGradient(listOf(lighten(theme.keyTop, 0.35f), theme.keyTop, darken(theme.keyTop, 0.35f)), c - Offset(r * 0.3f, r * 0.35f), r * 1.4f), r * 0.88f, c)
            // Moleteado
            for (i in 0 until 36) {
                val a = i * 10f * Math.PI.toFloat() / 180f
                drawLine(Color.Black.copy(alpha = 0.22f), c + Offset(kotlin.math.cos(a) * r * 0.74f, kotlin.math.sin(a) * r * 0.74f),
                    c + Offset(kotlin.math.cos(a) * r * 0.87f, kotlin.math.sin(a) * r * 0.87f), strokeWidth = r * 0.04f)
            }
            glyph(c, r * 0.4f, theme.keyIcon)
        }
        Text(label, style = TextStyle(color = theme.ink, fontSize = labelSize, fontWeight = FontWeight.Bold, fontFamily = theme.uiFont))
    }
}

/** Interruptor deslizante con LED (modo palabra clave). */
@Composable
fun SlideSwitch(
    label: String,
    on: Boolean,
    theme: KbTheme,
    width: Dp,
    labelSize: TextUnit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pos by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "switch")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(width * 0.06f)) {
        Canvas(
            Modifier.width(width).height(width * 0.46f)
                .semantics { contentDescription = label; role = Role.Switch; stateDescription = if (on) "activado" else "desactivado" }
                .pointerInput(Unit) { detectTapGestures { onToggle() } },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Color.Black.copy(alpha = 0.75f), cornerRadius = CornerRadius(h / 2))
            drawRoundRect(theme.panel, Offset(h * 0.12f, h * 0.12f), Size(w - h * 0.24f, h * 0.76f), CornerRadius(h * 0.38f))
            val knobW = w * 0.46f
            val x = h * 0.12f + (w - h * 0.24f - knobW) * pos
            drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(x + h * 0.04f, h * 0.18f), Size(knobW, h * 0.76f), CornerRadius(h * 0.3f))
            drawRoundRect(Brush.verticalGradient(listOf(lighten(theme.keyTop, 0.4f), darken(theme.keyTop, 0.2f))), Offset(x, h * 0.12f), Size(knobW, h * 0.76f), CornerRadius(h * 0.3f))
            for (i in -1..1) {
                val lx = x + knobW / 2 + i * knobW * 0.16f
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(lx, h * 0.3f), Offset(lx, h * 0.7f), strokeWidth = h * 0.06f)
            }
        }
        Text(label, style = TextStyle(color = theme.ink, fontSize = labelSize, fontWeight = FontWeight.Bold, fontFamily = theme.uiFont))
    }
}
