package com.kb007.recorder.ui.deck

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.kb007.recorder.ui.theme.KbTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// ======================= VU meter =======================

/** Posición de aguja (0..1) para un nivel en dB VU, escala lineal en tensión como un VU real. */
fun vuPosition(vu: Float): Float {
    val maxV = 10f.pow(3f / 20f)
    val minV = 10f.pow(-20f / 20f)
    val v = 10f.pow(vu.coerceIn(-30f, 3.5f) / 20f)
    return ((v - minV) / (maxV - minV)).coerceIn(-0.02f, 1.03f)
}

/**
 * VU meter de aguja. [levelDbfs] es el nivel en dBFS; 0 VU = -18 dBFS.
 * Balística aproximada a un VU real (~300 ms), con leve sobreoscilación.
 */
@Composable
fun VuMeter(theme: KbTheme, levelDbfs: () -> Float, lit: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    var needle by remember { mutableFloatStateOf(0f) }
    val currentLevel by rememberUpdatedState(levelDbfs)
    LaunchedEffect(Unit) {
        var vel = 0f
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = ((t - last) / 1e9f).coerceAtMost(0.05f)
                    val target = vuPosition(currentLevel() + 18f)
                    // Sistema masa-resorte críticamente casi amortiguado
                    val k = 110f
                    val damping = 17f
                    val acc = k * (target - needle) - damping * vel
                    vel += acc * dt
                    needle = (needle + vel * dt).coerceIn(-0.03f, 1.05f)
                }
                last = t
            }
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.05f)
        // Marco y cara
        drawRoundRect(Color.Black.copy(alpha = 0.6f), cornerRadius = corner)
        val face = Rect(w * 0.04f, h * 0.06f, w * 0.96f, h * 0.94f)
        drawRoundRect(theme.vuFace, face.topLeft, face.size, CornerRadius(w * 0.03f))
        if (lit) drawRoundRect(
            Brush.radialGradient(listOf(theme.vuBacklight.copy(alpha = 0.75f), Color.Transparent), Offset(w / 2, h * 0.9f), w * 0.7f),
            face.topLeft, face.size, CornerRadius(w * 0.03f),
        )
        val pivot = Offset(w / 2f, h * 1.05f)
        val radius = h * 0.68f
        val start = -44f
        val sweep = 88f
        fun angleFor(p: Float) = start + sweep * p
        fun pointAt(deg: Float, r: Float): Offset {
            val a = (deg - 90f) * PI.toFloat() / 180f
            return Offset(pivot.x + cos(a) * r, pivot.y + sin(a) * r)
        }
        clipRect(face.left, face.top, face.right, face.bottom) {
            // Arco de escala (zona roja desde 0 VU)
            val zero = vuPosition(0f)
            drawArc(theme.vuInk, angleFor(0f) - 90f, sweep * zero, false,
                topLeft = pivot - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = Stroke(h * 0.02f))
            drawArc(theme.vuRed, angleFor(zero) - 90f, sweep * (1f - zero), false,
                topLeft = pivot - Offset(radius, radius), size = Size(radius * 2, radius * 2), style = Stroke(h * 0.05f))
            val marks = listOf(-20, -10, -7, -5, -3, -2, -1, 0, 1, 2, 3)
            val textStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = (h * 0.095f).toSp())
            marks.forEach { m ->
                val deg = angleFor(vuPosition(m.toFloat()))
                val red = m >= 0
                drawLine(if (red) theme.vuRed else theme.vuInk, pointAt(deg, radius), pointAt(deg, radius + h * 0.08f), strokeWidth = h * 0.018f)
                if (m in listOf(-20, -10, -5, -3, 0, 3)) {
                    val label = if (m > 0) "+$m" else "$m"
                    val tl = measurer.measure(label, textStyle.copy(color = if (red) theme.vuRed else theme.vuInk))
                    val p = pointAt(deg, radius + h * 0.15f)
                    drawText(tl, topLeft = p - Offset(tl.size.width / 2f, tl.size.height / 2f))
                }
            }
            val vu = measurer.measure("VU", textStyle.copy(color = theme.vuInk, fontSize = (h * 0.16f).toSp()))
            drawText(vu, topLeft = Offset(w / 2f - vu.size.width / 2f, h * 0.52f))
            // Aguja
            rotate(angleFor(needle), pivot) {
                drawLine(Color.Black.copy(alpha = 0.18f), pivot + Offset(h * 0.03f, 0f), Offset(pivot.x + h * 0.03f, pivot.y - radius - h * 0.08f), strokeWidth = h * 0.02f)
                drawLine(theme.vuNeedle, pivot, Offset(pivot.x, pivot.y - radius - h * 0.1f), strokeWidth = h * 0.018f)
            }
            drawCircle(Color.Black, h * 0.12f, Offset(pivot.x, face.bottom))
        }
        // Cristal
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), face.top, face.top + face.height * 0.5f),
            face.topLeft, Size(face.width, face.height * 0.5f), CornerRadius(w * 0.03f))
    }
}

// ======================= Contador mecánico =======================

/**
 * Contador de cinta de 3 dígitos tipo odómetro: el último dígito rueda continuamente y
 * los demás solo giran cuando el de la derecha pasa de 9 a 0.
 */
@Composable
fun TapeCounter(theme: KbTheme, value: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val v = ((value() % 1000f) + 1000f) % 1000f
        val w = size.width
        val h = size.height
        drawRoundRect(Color.Black.copy(alpha = 0.7f), cornerRadius = CornerRadius(h * 0.12f))
        val pad = h * 0.1f
        val cellW = (w - pad * 2) / 3f
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (h * 0.62f).toSp(), color = theme.counterDigit)
        val glyphs = (0..9).map { measurer.measure(it.toString(), style) }
        for (i in 0 until 3) {
            val power = 10f.pow(2 - i)
            val digitValue = v / power
            val base = floor(digitValue)
            // Fracción de giro: solo si todos los dígitos de la derecha están en 9 y rodando
            val lower = v % power
            val frac = if (i == 2) v - floor(v) else {
                val threshold = power - 1f
                if (lower >= threshold) lower - threshold else 0f
            }
            val d = (base.toInt() % 10)
            val left = pad + cellW * i
            val cell = Rect(left + cellW * 0.06f, pad, left + cellW * 0.94f, h - pad)
            drawRoundRect(theme.counterBg, cell.topLeft, cell.size, CornerRadius(h * 0.06f))
            clipRect(cell.left, cell.top, cell.right, cell.bottom) {
                val step = cell.height * 0.95f
                for (k in -1..1) {
                    val g = glyphs[((d + k) % 10 + 10) % 10]
                    val y = cell.center.y - g.size.height / 2f + (k - frac) * step
                    drawText(g, topLeft = Offset(cell.center.x - g.size.width / 2f, y))
                }
                // Sombreado de tambor cilíndrico
                drawRect(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.75f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f),
                    startY = cell.top, endY = cell.bottom,
                ), cell.topLeft, cell.size)
            }
        }
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), 0f, h * 0.5f),
            Offset(pad, pad), Size(w - pad * 2, (h - pad * 2) * 0.45f), CornerRadius(h * 0.06f))
    }
}

// ======================= LED =======================

/** LED con halo. Si [blink] está activo parpadea a ~1,3 Hz. */
@Composable
fun Led(color: Color, on: Boolean, modifier: Modifier = Modifier, blink: Boolean = false) {
    val t = rememberInfiniteTransition(label = "led")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(760, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    Canvas(modifier) {
        val lit = on && (!blink || phase < 0.62f)
        // Encendido suave en los bordes del parpadeo
        val intensity = if (!lit) 0f else if (blink) (1f - exp(-phase * 30f)).coerceIn(0f, 1f) else 1f
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.Black.copy(alpha = 0.55f), r * 1.05f, c)
        if (intensity > 0f) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * intensity), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
        val body = if (intensity > 0f) color else color.copy(red = color.red * 0.28f, green = color.green * 0.28f, blue = color.blue * 0.28f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f + 0.5f * intensity), body), c - Offset(r * 0.3f, r * 0.3f), r * 1.2f), r * 0.8f, c)
    }
}
