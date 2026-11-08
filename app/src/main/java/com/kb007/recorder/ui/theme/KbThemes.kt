package com.kb007.recorder.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Registro de temas. Agregar un tema = agregar un val y sumarlo a [all]. */
object KbThemes {

    /** 1. Años 80 metálico: aluminio cepillado y cassette transparente. */
    val metal80 = KbTheme(
        id = "metal80", name = "Metálico 80", description = "Aluminio cepillado, cassette transparente",
        screenTop = Color(0xFF2B2E33), screenBottom = Color(0xFF111215),
        material = BodyMaterial.BRUSHED_METAL,
        bodyLight = Color(0xFFDADDE1), bodyDark = Color(0xFF9EA3AA), bodyEdge = Color(0xFF5E636B),
        panel = Color(0xFF2E3136), accent = Color(0xFF1F6FD1), accent2 = Color(0xFFE53935),
        ink = Color(0xFF1E2126), inkMuted = Color(0xFF4A4F57),
        windowFrame = Color(0xFF26292E), glassTint = Color(0x332A3440),
        shell = Color(0xFFCFD8E3), shellAlpha = 0.38f,
        label = Color(0xFFF4F1E8), labelStripe = Color(0xFF1F6FD1), labelLines = Color(0xFFB9C7D8), labelInk = Color(0xFF1A2A6C),
        hub = Color(0xFFF2F2F2), tape = Color(0xFF4A2E1E),
        keyTop = Color(0xFFE6E8EB), keySide = Color(0xFF8E939A), keyIcon = Color(0xFF2A2D32),
        recKeyTop = Color(0xFFE53935), recKeySide = Color(0xFF8E1C1A),
        counterBg = Color(0xFF15171A), counterDigit = Color(0xFFF1F1F1),
        vuFace = Color(0xFFF3E8C8), vuInk = Color(0xFF2A2A2A), vuRed = Color(0xFFD32F2F), vuNeedle = Color(0xFF1A1A1A), vuBacklight = Color(0xFFFFE9A8),
        brandFont = FontFamily.SansSerif, brandWeight = FontWeight.Black, uiFont = FontFamily.SansSerif, labelFont = FontFamily.Cursive,
    )

    /** 2. Retro deportivo: plástico amarillo con detalles rojos. */
    val sport = KbTheme(
        id = "sport", name = "Retro deportivo", description = "Plástico amarillo y rojo, a prueba de agua (casi)",
        screenTop = Color(0xFF263238), screenBottom = Color(0xFF0E1417),
        material = BodyMaterial.GLOSSY_PLASTIC,
        bodyLight = Color(0xFFFFD740), bodyDark = Color(0xFFF2B300), bodyEdge = Color(0xFFB37F00),
        panel = Color(0xFF263238), accent = Color(0xFFE53935), accent2 = Color(0xFF263238),
        ink = Color(0xFF212121), inkMuted = Color(0xFF5D4A00),
        windowFrame = Color(0xFF212121), glassTint = Color(0x22FFC107),
        shell = Color(0xFF263238), shellAlpha = 1f,
        label = Color(0xFFFFFFFF), labelStripe = Color(0xFFE53935), labelLines = Color(0xFFFFCDD2), labelInk = Color(0xFF263238),
        hub = Color(0xFFFFFFFF), tape = Color(0xFF3E2618),
        keyTop = Color(0xFF37474F), keySide = Color(0xFF151C20), keyIcon = Color(0xFFFFD740),
        recKeyTop = Color(0xFFE53935), recKeySide = Color(0xFF7F1513),
        counterBg = Color(0xFF212121), counterDigit = Color(0xFFFFD740),
        vuFace = Color(0xFFFFFFFF), vuInk = Color(0xFF212121), vuRed = Color(0xFFE53935), vuNeedle = Color(0xFFE53935), vuBacklight = Color(0xFFFFF8E1),
        brandFont = FontFamily.SansSerif, brandWeight = FontWeight.ExtraBold, uiFont = FontFamily.SansSerif, labelFont = FontFamily.Cursive,
    )

    /** 3. Negro profesional de periodista: sobrio, con cromados. */
    val reporter = KbTheme(
        id = "reporter", name = "Negro periodista", description = "Negro mate con cromados, sobrio y profesional",
        screenTop = Color(0xFF1C1C1E), screenBottom = Color(0xFF050505),
        material = BodyMaterial.MATTE_BLACK,
        bodyLight = Color(0xFF34353A), bodyDark = Color(0xFF17181B), bodyEdge = Color(0xFF8C9096),
        panel = Color(0xFF0D0D0F), accent = Color(0xFFC9CDD2), accent2 = Color(0xFFFF5252),
        ink = Color(0xFFE6E6E6), inkMuted = Color(0xFF9A9DA3),
        windowFrame = Color(0xFFB8BCC2), glassTint = Color(0x44000000),
        shell = Color(0xFF1B1B1D), shellAlpha = 1f,
        label = Color(0xFFE8E4DA), labelStripe = Color(0xFF3A3A3A), labelLines = Color(0xFFBDB6A6), labelInk = Color(0xFF111111),
        hub = Color(0xFFD9D9D9), tape = Color(0xFF3B2416),
        keyTop = Color(0xFFC7CBD1), keySide = Color(0xFF5F646B), keyIcon = Color(0xFF151515),
        recKeyTop = Color(0xFFC62828), recKeySide = Color(0xFF5E1010),
        counterBg = Color(0xFF000000), counterDigit = Color(0xFFE0E0E0),
        vuFace = Color(0xFF101112), vuInk = Color(0xFFE0E0E0), vuRed = Color(0xFFFF5252), vuNeedle = Color(0xFFFFB74D), vuBacklight = Color(0xFF2A2116),
        brandFont = FontFamily.Monospace, brandWeight = FontWeight.Bold, uiFont = FontFamily.SansSerif, labelFont = FontFamily.Cursive,
    )

    /** 4. Madera y crema setentosa. */
    val wood70 = KbTheme(
        id = "wood70", name = "Madera 70", description = "Nogal, crema y perillas de bronce",
        screenTop = Color(0xFF3E2A1E), screenBottom = Color(0xFF1A110C),
        material = BodyMaterial.WOOD,
        bodyLight = Color(0xFF8D5A35), bodyDark = Color(0xFF5C3920), bodyEdge = Color(0xFF2E1B0F),
        panel = Color(0xFFF1E6CC), accent = Color(0xFFD9822B), accent2 = Color(0xFF7A9A3A),
        ink = Color(0xFFF7EBCF), inkMuted = Color(0xFFE2CBA0),
        windowFrame = Color(0xFFC8A15A), glassTint = Color(0x33FFB74D),
        shell = Color(0xFFE09A3E), shellAlpha = 0.55f,
        label = Color(0xFFF7EBCF), labelStripe = Color(0xFFD9822B), labelLines = Color(0xFFE2C99A), labelInk = Color(0xFF4A2A12),
        hub = Color(0xFFF7EBCF), tape = Color(0xFF3A2112),
        keyTop = Color(0xFFF1E6CC), keySide = Color(0xFFA88F63), keyIcon = Color(0xFF3B2414),
        recKeyTop = Color(0xFFC0392B), recKeySide = Color(0xFF6E1B12),
        counterBg = Color(0xFF2A1A10), counterDigit = Color(0xFFF7EBCF),
        vuFace = Color(0xFFFBE3A8), vuInk = Color(0xFF3B2414), vuRed = Color(0xFFC0392B), vuNeedle = Color(0xFF2A1A10), vuBacklight = Color(0xFFFFC870),
        brandFont = FontFamily.Serif, brandWeight = FontWeight.Bold, uiFont = FontFamily.Serif, labelFont = FontFamily.Cursive,
    )

    val all: List<KbTheme> = listOf(metal80, sport, reporter, wood70)

    fun byId(id: String): KbTheme = all.firstOrNull { it.id == id } ?: metal80
}
