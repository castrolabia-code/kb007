package com.kb007.recorder.ui.deck

import androidx.compose.ui.graphics.Color
import com.kb007.recorder.ui.theme.KbTheme

/** Dibujo de la etiqueta del cassette. */
enum class LabelPattern { BAND, DOUBLE_STRIPE, GRADIENT, RAINBOW, CHECKER, DIAGONAL, DOTS, SOLID }

/**
 * Estética de un cassette individual. Cada grabación tiene la suya (automática o elegida),
 * así en el estante no se confunden. Las marcas son de fantasía: no hay logos reales.
 */
data class CassetteStyle(
    val name: String,
    val shell: Color,
    val shellAlpha: Float,
    val label: Color,
    val accent: Color,
    val accent2: Color,
    val lines: Color,
    val ink: Color,          // "lapicera" con la que está escrito el título
    val printInk: Color,     // texto impreso (marca, lado, tipo)
    val hub: Color,
    val pattern: LabelPattern,
    val brand: String,
    val tapeType: String,
    val length: String,
    /** Inclinación de la letra manuscrita (grados). */
    val slant: Float,
) {
    companion object {
        val ALL: List<CassetteStyle> = listOf(
            CassetteStyle("Clásico azul", Color(0xFFCFD8E3), 0.38f, Color(0xFFF4F1E8), Color(0xFF1F6FD1), Color(0xFF1F6FD1),
                Color(0xFFB9C7D8), Color(0xFF1A2A6C), Color(0xFF1F6FD1), Color(0xFFF2F2F2), LabelPattern.BAND, "KB-007", "NORMAL", "C-60", -1.8f),
            CassetteStyle("Rojo fuego", Color(0xFF1E1E1E), 1f, Color(0xFFFFFFFF), Color(0xFFE53935), Color(0xFFFFB300),
                Color(0xFFFFCDD2), Color(0xFF111111), Color(0xFFE53935), Color(0xFFFFFFFF), LabelPattern.DOUBLE_STRIPE, "FERRO", "TYPE I", "C-90", 1.2f),
            CassetteStyle("Arcoíris 80", Color(0xFFF5F5F5), 1f, Color(0xFF212121), Color(0xFFE53935), Color(0xFF1E88E5),
                Color(0xFF616161), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFF5F5F5), LabelPattern.RAINBOW, "SPECTRA", "HI-FI", "C-60", -2.5f),
            CassetteStyle("Cromo plata", Color(0xFF9EA3AA), 0.55f, Color(0xFFDADDE1), Color(0xFF37474F), Color(0xFF90A4AE),
                Color(0xFFB0BEC5), Color(0xFF263238), Color(0xFF263238), Color(0xFFECEFF1), LabelPattern.GRADIENT, "CHROMA", "CrO₂", "C-90", 0.6f),
            CassetteStyle("Verde menta", Color(0xFFB2DFDB), 0.45f, Color(0xFFF1F8E9), Color(0xFF26A69A), Color(0xFF80CBC4),
                Color(0xFFC8E6C9), Color(0xFF1B5E20), Color(0xFF00695C), Color(0xFFFFFFFF), LabelPattern.DOTS, "MINT", "NORMAL", "C-46", -1f),
            CassetteStyle("Metal dorado", Color(0xFF2A2116), 1f, Color(0xFF3B2F1E), Color(0xFFD4AF37), Color(0xFFFFE082),
                Color(0xFF6D5A35), Color(0xFFFFE9A8), Color(0xFFD4AF37), Color(0xFFD4AF37), LabelPattern.DIAGONAL, "AURUM", "METAL", "C-60", 2f),
            CassetteStyle("Rosa chicle", Color(0xFFF8BBD0), 0.6f, Color(0xFFFFFFFF), Color(0xFFEC407A), Color(0xFFAB47BC),
                Color(0xFFF8BBD0), Color(0xFF6A1B9A), Color(0xFFEC407A), Color(0xFFFFFFFF), LabelPattern.GRADIENT, "BUBBLE", "TYPE I", "C-60", -3f),
            CassetteStyle("Ajedrez", Color(0xFF424242), 1f, Color(0xFFFAFAFA), Color(0xFF212121), Color(0xFFFAFAFA),
                Color(0xFFBDBDBD), Color(0xFFC62828), Color(0xFF212121), Color(0xFFE0E0E0), LabelPattern.CHECKER, "ROOK", "NORMAL", "C-90", 1.5f),
            CassetteStyle("Naranja ácido", Color(0xFFFF9800), 0.5f, Color(0xFFFFF8E1), Color(0xFFFF6D00), Color(0xFF5D4037),
                Color(0xFFFFE0B2), Color(0xFF3E2723), Color(0xFFE65100), Color(0xFFFFF8E1), LabelPattern.DOUBLE_STRIPE, "ZUMO", "HI-BIAS", "C-60", -0.5f),
            CassetteStyle("Noche azul", Color(0xFF0D1B2A), 1f, Color(0xFF1B263B), Color(0xFF00E5FF), Color(0xFF7C4DFF),
                Color(0xFF415A77), Color(0xFFE0E1DD), Color(0xFF00E5FF), Color(0xFF778DA9), LabelPattern.BAND, "NOCTURNO", "CrO₂", "C-90", 2.4f),
            CassetteStyle("Kraft", Color(0xFF8D6E63), 1f, Color(0xFFD7B98E), Color(0xFF6D4C41), Color(0xFF3E2723),
                Color(0xFFBCA27A), Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFFEFEBE9), LabelPattern.SOLID, "KRAFT", "NORMAL", "C-46", -2f),
            CassetteStyle("Lima transparente", Color(0xFFC6FF00), 0.35f, Color(0xFFFFFFFF), Color(0xFF64DD17), Color(0xFF212121),
                Color(0xFFDCEDC8), Color(0xFF212121), Color(0xFF33691E), Color(0xFFFFFFFF), LabelPattern.DIAGONAL, "NEÓN", "HI-FI", "C-60", 0.8f),
        )

        /**
         * Estilo de una grabación: el elegido a mano ([chosen] >= 0) o uno automático que rota
         * con el número de grabación, así dos cassettes seguidos nunca se ven iguales.
         */
        fun forRecording(id: Long, chosen: Int): CassetteStyle =
            if (chosen in ALL.indices) ALL[chosen] else ALL[(((id - 1) % ALL.size + ALL.size) % ALL.size).toInt()]

        fun autoIndex(id: Long): Int = (((id - 1) % ALL.size + ALL.size) % ALL.size).toInt()

        /** Cassette "de la casa" con los colores del tema del aparato (vista previa de temas). */
        fun fromTheme(t: KbTheme) = CassetteStyle(
            "Tema", t.shell, t.shellAlpha, t.label, t.labelStripe, t.labelStripe, t.labelLines, t.labelInk, t.labelStripe,
            t.hub, LabelPattern.BAND, "KB-007", "NORMAL", "C-60", -1.8f,
        )
    }
}
