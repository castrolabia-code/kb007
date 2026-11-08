package com.kb007.recorder.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pinta el cuerpo del aparato con la textura del tema. Todo se calcula una vez por tamaño
 * (drawWithCache), así no cuesta nada en cada frame de animación.
 */
fun Modifier.deviceBody(theme: KbTheme, corner: Float): Modifier = drawWithCache {
    val shape = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
    }
    val rnd = Random(7)
    // Precalculamos las vetas/líneas para que sean estables entre frames.
    val lines: List<Triple<Float, Float, Color>> = when (theme.material) {
        BodyMaterial.BRUSHED_METAL -> List((size.height / 1.6f).toInt().coerceAtMost(1400)) {
            val y = rnd.nextFloat() * size.height
            val a = rnd.nextFloat() * 0.10f
            Triple(y, rnd.nextFloat() * 1.2f + 0.4f, if (rnd.nextBoolean()) Color.White.copy(alpha = a) else Color.Black.copy(alpha = a * 0.7f))
        }
        BodyMaterial.MATTE_BLACK -> List(((size.width * size.height) / 90f).toInt().coerceAtMost(9000)) {
            Triple(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height, Color.White.copy(alpha = rnd.nextFloat() * 0.05f))
        }
        else -> emptyList()
    }
    val grain: List<Path> = if (theme.material == BodyMaterial.WOOD) List(46) { i ->
        Path().apply {
            val base = size.height * i / 46f + rnd.nextFloat() * 8f
            val amp = 4f + rnd.nextFloat() * 14f
            val freq = 0.004f + rnd.nextFloat() * 0.01f
            val phase = rnd.nextFloat() * 6f
            moveTo(0f, base)
            var x = 0f
            while (x <= size.width) {
                lineTo(x, base + amp * sin(x * freq + phase) + 3f * sin(x * freq * 5f + phase * 2))
                x += 6f
            }
        }
    } else emptyList()

    onDrawBehind {
        clipPath(shape) {
            // Base: degradé vertical
            drawRect(Brush.verticalGradient(listOf(theme.bodyLight, theme.bodyDark)))
            when (theme.material) {
                BodyMaterial.BRUSHED_METAL -> {
                    lines.forEach { (y, w, c) -> drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = w) }
                    // Reflejo anisotrópico típico del aluminio cepillado
                    drawRect(Brush.horizontalGradient(
                        0f to Color.Transparent, 0.35f to Color.White.copy(alpha = 0.18f),
                        0.5f to Color.Transparent, 0.8f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent,
                    ))
                }
                BodyMaterial.GLOSSY_PLASTIC -> {
                    drawRect(Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.35f), 0.25f to Color.White.copy(alpha = 0.05f), 0.6f to Color.Transparent,
                        start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.5f),
                    ))
                }
                BodyMaterial.WOOD -> {
                    grain.forEachIndexed { i, p ->
                        drawPath(p, theme.bodyEdge.copy(alpha = if (i % 3 == 0) 0.35f else 0.15f), style = Stroke(width = if (i % 5 == 0) 3f else 1.4f))
                    }
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.15f))))
                }
                BodyMaterial.MATTE_BLACK -> {
                    lines.forEach { (x, y, c) -> drawRect(c, Offset(x, y), Size(1.2f, 1.2f)) }
                }
            }
        }
        // Bisel: luz arriba, sombra abajo
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.45f))),
            cornerRadius = CornerRadius(corner), style = Stroke(width = 5f),
        )
        drawRoundRect(theme.bodyEdge, cornerRadius = CornerRadius(corner), style = Stroke(width = 1.5f))
    }
}

/** Tornillo de cabeza Phillips. */
fun DrawScope.screw(center: Offset, r: Float, metal: Color = Color(0xFFBFC3C8), angleDeg: Float = 20f) {
    drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.12f, center + Offset(r * 0.12f, r * 0.15f))
    drawCircle(Brush.radialGradient(listOf(Color.White, metal, metal.copy(red = metal.red * 0.6f, green = metal.green * 0.6f, blue = metal.blue * 0.6f)),
        center = center - Offset(r * 0.3f, r * 0.3f), radius = r * 1.4f), r, center)
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = (kotlin.math.cos(rad) * r * 0.62f).toFloat()
    val dy = (kotlin.math.sin(rad) * r * 0.62f).toFloat()
    val slot = Color.Black.copy(alpha = 0.6f)
    drawLine(slot, center - Offset(dx, dy), center + Offset(dx, dy), strokeWidth = r * 0.22f)
    drawLine(slot, center - Offset(-dy, dx), center + Offset(-dy, dx), strokeWidth = r * 0.22f)
}
