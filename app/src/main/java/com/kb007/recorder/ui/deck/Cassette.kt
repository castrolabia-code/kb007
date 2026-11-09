package com.kb007.recorder.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.kb007.recorder.ui.theme.KbTheme
import com.kb007.recorder.ui.theme.screw
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Proporción real de un cassette compacto (100,4 × 63,8 mm). */
const val CASSETTE_ASPECT = 100.4f / 63.8f

/** Geometría del cassette en fracciones del ancho/alto, compartida por dibujo y animación. */
object CassetteGeom {
    const val HUB_X_OFFSET = 0.212f   // distancia de cada eje al centro (en anchos)
    const val HUB_Y = 0.445f          // en altos
    const val HUB_R = 0.062f          // radio exterior del cubo (en anchos)
    const val PACK_MIN = 0.072f       // radio del rollo vacío
    const val PACK_MAX = 0.198f       // radio del rollo lleno

    /** Radios de los rollos izq./der. conservando el área de cinta (la cinta tiene espesor constante). */
    fun packRadii(progress: Float): Pair<Float, Float> {
        val a = PACK_MAX * PACK_MAX - PACK_MIN * PACK_MIN
        val l = sqrt(PACK_MIN * PACK_MIN + (1f - progress) * a)
        val r = sqrt(PACK_MIN * PACK_MIN + progress * a)
        return l to r
    }
}

/**
 * Cassette dibujado a mano con Canvas.
 * @param progress 0..1 cinta transferida del carrete izquierdo al derecho.
 * @param leftAngle/rightAngle rotación de cada cubo, en grados.
 */
@Composable
fun Cassette(
    theme: KbTheme,
    title: String?,
    progress: Float,
    leftAngle: Float,
    rightAngle: Float,
    modifier: Modifier = Modifier,
    side: String = "A",
    /** Estética propia de este cassette; null = la del tema del aparato. */
    style: CassetteStyle? = null,
) {
    val measurer = rememberTextMeasurer()
    val st = remember(theme, style) { style ?: CassetteStyle.fromTheme(theme) }
    val handStyle = remember(theme, st) { TextStyle(fontFamily = theme.labelFont, color = st.ink, fontWeight = FontWeight.Medium) }
    val brandStyle = remember(st) { TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, color = st.printInk) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.035f
        val hubL = Offset(w * (0.5f - CassetteGeom.HUB_X_OFFSET), h * CassetteGeom.HUB_Y)
        val hubR = Offset(w * (0.5f + CassetteGeom.HUB_X_OFFSET), h * CassetteGeom.HUB_Y)
        val (rl, rr) = CassetteGeom.packRadii(progress)
        val window = Rect(w * 0.365f, h * 0.36f, w * 0.635f, h * 0.535f)
        val transparent = st.shellAlpha < 1f

        // ---- Sombra y fondo de carcasa ----
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(w * 0.008f, h * 0.02f), size, CornerRadius(corner))
        drawRoundRect(st.shell.copy(alpha = if (transparent) st.shellAlpha * 0.6f else 1f), cornerRadius = CornerRadius(corner))

        // ---- Rollos de cinta y cubos (detrás de la tapa) ----
        drawTapePack(hubL, rl * w, theme.tape)
        drawTapePack(hubR, rr * w, theme.tape)
        drawTapePath(w, h, hubL, rl * w, hubR, rr * w, theme.tape)
        drawHub(hubL, CassetteGeom.HUB_R * w, leftAngle, st.hub)
        drawHub(hubR, CassetteGeom.HUB_R * w, rightAngle, st.hub)

        // ---- Tapa frontal: con agujeros para la ventanita y los cubos ----
        val front = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(corner)))
            addRoundRect(RoundRect(window, CornerRadius(w * 0.012f)))
            addOval(Rect(hubL, CassetteGeom.HUB_R * w * 1.18f))
            addOval(Rect(hubR, CassetteGeom.HUB_R * w * 1.18f))
            addPath(headArea(w, h, inset = 0.012f))
        }
        drawPath(front, st.shell.copy(alpha = st.shellAlpha))
        if (transparent) drawPath(front, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.10f))))

        // ---- Etiqueta (con recorte de ventana y cubos) ----
        val labelRect = Rect(w * 0.065f, h * 0.06f, w * 0.935f, h * 0.70f)
        val label = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(labelRect, CornerRadius(w * 0.012f)))
            addRoundRect(RoundRect(window.inflate(w * 0.012f), CornerRadius(w * 0.02f)))
            addOval(Rect(hubL, CassetteGeom.HUB_R * w * 1.32f))
            addOval(Rect(hubR, CassetteGeom.HUB_R * w * 1.32f))
        }
        drawPath(label, st.label)
        // Dibujo de la etiqueta (zona alrededor de la ventana), propio de cada estilo
        clipPath(label) { drawLabelPattern(st, labelRect, w, h) }
        // Zona blanca para escribir el título (en los diseños que la tapan)
        if (st.pattern == LabelPattern.RAINBOW) {
            drawRoundRect(st.label, Offset(labelRect.left + w * 0.09f, h * 0.085f), Size(labelRect.width - w * 0.13f, h * 0.17f), CornerRadius(w * 0.008f))
        }
        // Renglones para escribir
        for (i in 1..2) {
            val y = h * (0.12f + i * 0.06f)
            drawLine(st.lines, Offset(labelRect.left + w * 0.06f, y), Offset(labelRect.right - w * 0.06f, y), strokeWidth = h * 0.006f)
        }
        // Lado, marca, tipo de cinta y duración (marcas de fantasía)
        drawText(measurer, side, Offset(labelRect.left + w * 0.018f, h * 0.075f),
            style = brandStyle.copy(fontSize = (h * 0.11f).toSp(), color = st.ink))
        val brand = measurer.measure(st.brand, brandStyle.copy(fontSize = (h * 0.055f).toSp(), letterSpacing = (h * 0.008f).toSp()))
        drawText(brand, topLeft = Offset(labelRect.right - w * 0.025f - brand.size.width, h * 0.60f))
        drawText(measurer, "${st.length} · ${st.tapeType}", Offset(labelRect.left + w * 0.03f, h * 0.605f),
            style = brandStyle.copy(fontSize = (h * 0.045f).toSp(), color = st.printInk.copy(alpha = 0.8f)))

        // Título manuscrito (levemente torcido, como escrito a mano)
        val text = title ?: "— sin cassette —"
        val layout = measurer.measure(
            text, handStyle.copy(fontSize = (h * 0.085f).toSp(), color = if (title == null) st.ink.copy(alpha = 0.45f) else st.ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = (labelRect.width * 0.78f).toInt()),
        )
        rotate(st.slant, Offset(w * 0.5f, h * 0.17f)) {
            drawText(layout, topLeft = Offset(w * 0.5f - layout.size.width / 2f, h * 0.215f - layout.size.height))
        }

        // ---- Marco de la ventanita y escala ----
        drawRoundRect(Color.Black.copy(alpha = 0.55f), window.topLeft, window.size, CornerRadius(w * 0.012f), style = Stroke(w * 0.004f))
        for (i in 0..6) {
            val x = window.left + window.width * (0.18f + i * 0.11f)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x, window.bottom - h * 0.025f), Offset(x, window.bottom - h * 0.008f), strokeWidth = w * 0.0025f)
        }

        // ---- Zona de cabezales (trapecio inferior) ----
        val head = headArea(w, h, inset = 0f)
        drawPath(head, st.shell.copy(alpha = if (transparent) 0.5f else 1f))
        drawPath(head, Color.Black.copy(alpha = 0.18f), style = Stroke(w * 0.003f))
        // Agujeros: guías, cabezal y cabrestantes
        val hy = h * 0.915f
        listOf(0.19f, 0.81f).forEach { drawCircle(Color.Black.copy(alpha = 0.75f), w * 0.016f, Offset(w * it, hy)) }
        listOf(0.30f, 0.70f).forEach { drawCircle(Color.Black.copy(alpha = 0.75f), w * 0.012f, Offset(w * it, hy + h * 0.01f)) }
        drawRoundRect(Color.Black.copy(alpha = 0.8f), Offset(w * 0.43f, h * 0.885f), Size(w * 0.14f, h * 0.06f), CornerRadius(w * 0.006f))
        // Cinta visible por las aberturas
        drawLine(theme.tape, Offset(w * 0.17f, h * 0.965f), Offset(w * 0.83f, h * 0.965f), strokeWidth = h * 0.012f)

        // ---- Tornillos ----
        val sr = w * 0.016f
        listOf(Offset(w * 0.035f, h * 0.05f), Offset(w * 0.965f, h * 0.05f), Offset(w * 0.035f, h * 0.95f), Offset(w * 0.965f, h * 0.95f))
            .forEachIndexed { i, o -> screw(o, sr, angleDeg = 15f + i * 27f) }
        screw(Offset(w * 0.5f, h * 0.81f), sr, angleDeg = 40f)

        // ---- Reflejo sobre la tapa ----
        val glare = Path().apply {
            moveTo(w * 0.05f, 0f); lineTo(w * 0.42f, 0f); lineTo(w * 0.18f, h); lineTo(0f, h); close()
        }
        drawPath(glare, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.02f)), Offset.Zero, Offset(w * 0.4f, h)))
        drawRoundRect(Color.Black.copy(alpha = 0.4f), cornerRadius = CornerRadius(corner), style = Stroke(w * 0.004f))
    }
}

/** Dibujo de la etiqueta según el estilo. Se llama ya recortado a la forma de la etiqueta. */
private fun DrawScope.drawLabelPattern(st: CassetteStyle, r: Rect, w: Float, h: Float) {
    val top = h * 0.30f
    val bottom = h * 0.59f
    when (st.pattern) {
        LabelPattern.BAND -> drawRect(st.accent.copy(alpha = 0.88f), Offset(r.left, top), Size(r.width, bottom - top))
        LabelPattern.SOLID -> {
            drawRect(st.accent.copy(alpha = 0.35f), Offset(r.left, top), Size(r.width, bottom - top))
            drawRect(st.accent, Offset(r.left, bottom - h * 0.03f), Size(r.width, h * 0.03f))
        }
        LabelPattern.DOUBLE_STRIPE -> {
            val bh = (bottom - top) / 2f
            drawRect(st.accent, Offset(r.left, top), Size(r.width, bh))
            drawRect(st.accent2, Offset(r.left, top + bh), Size(r.width, bh))
            drawRect(st.label, Offset(r.left, top + bh - h * 0.006f), Size(r.width, h * 0.012f))
        }
        LabelPattern.GRADIENT -> drawRect(
            Brush.horizontalGradient(listOf(st.accent, st.accent2, st.accent), r.left, r.right), Offset(r.left, top), Size(r.width, bottom - top),
        )
        LabelPattern.RAINBOW -> {
            val colors = listOf(Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
            val sh = (r.height) / colors.size
            colors.forEachIndexed { i, c -> drawRect(c, Offset(r.left, r.top + i * sh), Size(r.width, sh + 1f)) }
        }
        LabelPattern.CHECKER -> {
            val cell = (bottom - top) / 6f
            var y = top
            var row = 0
            while (y < bottom - 1f) {
                var x = r.left
                var col = 0
                while (x < r.right) {
                    drawRect(if ((row + col) % 2 == 0) st.accent else st.accent2, Offset(x, y), Size(cell, cell))
                    x += cell; col++
                }
                y += cell; row++
            }
        }
        LabelPattern.DIAGONAL -> {
            drawRect(st.accent2.copy(alpha = 0.25f), Offset(r.left, top), Size(r.width, bottom - top))
            val step = w * 0.05f
            var x = r.left - (bottom - top)
            while (x < r.right) {
                drawLine(st.accent, Offset(x, bottom), Offset(x + (bottom - top), top), strokeWidth = step * 0.45f)
                x += step
            }
        }
        LabelPattern.DOTS -> {
            drawRect(st.accent.copy(alpha = 0.22f), Offset(r.left, top), Size(r.width, bottom - top))
            val step = w * 0.04f
            var y = top + step / 2
            var row = 0
            while (y < bottom) {
                var x = r.left + step / 2 + (if (row % 2 == 0) 0f else step / 2)
                while (x < r.right) { drawCircle(st.accent, step * 0.22f, Offset(x, y)); x += step }
                y += step; row++
            }
        }
    }
}

/** Trapecio inferior del cassette (zona de cabezales). */
private fun headArea(w: Float, h: Float, inset: Float): Path = Path().apply {
    val i = w * inset
    moveTo(w * 0.17f + i, h * 0.84f + i)
    lineTo(w * 0.83f - i, h * 0.84f + i)
    lineTo(w * 0.87f - i, h - i)
    lineTo(w * 0.13f + i, h - i)
    close()
}

private fun DrawScope.drawTapePack(center: Offset, r: Float, tape: Color) {
    drawCircle(Brush.radialGradient(listOf(tape.copy(alpha = 0.9f), tape, tape.copy(red = tape.red * 0.7f, green = tape.green * 0.7f, blue = tape.blue * 0.7f)),
        center, r), r, center)
    // Anillos concéntricos sutiles (capas de cinta)
    var rr = r * 0.95f
    var k = 0
    while (rr > r * 0.45f) {
        drawCircle(Color.White.copy(alpha = if (k % 2 == 0) 0.05f else 0.025f), rr, center, style = Stroke(1.2f))
        rr -= r * 0.07f; k++
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r, center, style = Stroke(1.5f))
}

/** Cubo blanco con 6 dientes, rotado [angle] grados. */
private fun DrawScope.drawHub(c: Offset, r: Float, angle: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.3f), r * 1.04f, c + Offset(r * 0.06f, r * 0.08f))
    drawCircle(color, r, c)
    drawCircle(Color.Black.copy(alpha = 0.15f), r, c, style = Stroke(r * 0.08f))
    drawCircle(Color(0xFF1B1B1B), r * 0.62f, c)
    rotate(angle, c) {
        for (i in 0 until 6) {
            val a = i * PI / 3
            val tip = Offset(c.x + cos(a).toFloat() * r * 0.36f, c.y + sin(a).toFloat() * r * 0.36f)
            val base = Offset(c.x + cos(a).toFloat() * r * 0.66f, c.y + sin(a).toFloat() * r * 0.66f)
            drawLine(color, base, tip, strokeWidth = r * 0.17f)
        }
        // Marca para que se vea el giro aunque los dientes sean simétricos
        translate(c.x, c.y) { drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.07f, Offset(r * 0.82f, 0f)) }
    }
}

/** Cinta desde el rollo izquierdo por las guías inferiores hasta el rollo derecho. */
private fun DrawScope.drawTapePath(w: Float, h: Float, l: Offset, rl: Float, r: Offset, rr: Float, tape: Color) {
    val guideL = Offset(w * 0.19f, h * 0.955f)
    val guideR = Offset(w * 0.81f, h * 0.955f)
    val p = Path().apply {
        moveTo(l.x - rl * 0.25f, l.y + rl * 0.97f)
        lineTo(guideL.x, guideL.y)
        lineTo(guideR.x, guideR.y)
        lineTo(r.x + rr * 0.25f, r.y + rr * 0.97f)
    }
    drawPath(p, tape, style = Stroke(h * 0.012f))
}
