package com.kb007.recorder.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Material del cuerpo del aparato (define cómo se pinta la textura). */
enum class BodyMaterial { BRUSHED_METAL, GLOSSY_PLASTIC, WOOD, MATTE_BLACK }

/**
 * Un tema visual completo del aparato. Para agregar uno nuevo basta con crear otro
 * [KbTheme] y sumarlo a [KbThemes.all]; ningún otro archivo necesita cambios.
 */
data class KbTheme(
    val id: String,
    val name: String,
    val description: String,
    // Fondo de pantalla alrededor del aparato
    val screenTop: Color,
    val screenBottom: Color,
    // Cuerpo
    val material: BodyMaterial,
    val bodyLight: Color,
    val bodyDark: Color,
    val bodyEdge: Color,
    val panel: Color,          // franjas/paneles secundarios
    val accent: Color,         // color de marca (franja, detalles)
    val accent2: Color,
    val ink: Color,            // serigrafía sobre el cuerpo
    val inkMuted: Color,
    // Ventana del cassette
    val windowFrame: Color,
    val glassTint: Color,
    // Cassette
    val shell: Color,          // carcasa
    val shellAlpha: Float,     // < 1 = carcasa transparente
    val label: Color,
    val labelStripe: Color,
    val labelLines: Color,
    val labelInk: Color,
    val hub: Color,
    val tape: Color,
    // Teclas
    val keyTop: Color,
    val keySide: Color,
    val keyIcon: Color,
    val recKeyTop: Color,
    val recKeySide: Color,
    // Instrumentos
    val counterBg: Color,
    val counterDigit: Color,
    val vuFace: Color,
    val vuInk: Color,
    val vuRed: Color,
    val vuNeedle: Color,
    val vuBacklight: Color,
    // Tipografías
    val brandFont: FontFamily,
    val brandWeight: FontWeight,
    val uiFont: FontFamily,
    val labelFont: FontFamily,
)

val LocalKbTheme = staticCompositionLocalOf { KbThemes.metal80 }
