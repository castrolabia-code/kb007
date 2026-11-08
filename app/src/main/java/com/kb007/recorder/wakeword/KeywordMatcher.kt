package com.kb007.recorder.wakeword

import org.json.JSONObject
import java.text.Normalizer

/** Palabra reconocida con su confianza (0..1). */
data class HeardWord(val word: String, val conf: Float)

/**
 * Lógica pura (sin Android ni Vosk) para decidir si un resultado contiene la frase clave.
 * Compara sin acentos ni mayúsculas y exige que las palabras aparezcan seguidas.
 */
object KeywordMatcher {

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-zñ0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    /** Tokens de una frase tal como se escriben en la gramática (minúsculas, conservando acentos). */
    fun grammarTokens(phrase: String): List<String> =
        phrase.lowercase().trim().replace(Regex("[^\\p{L}0-9 ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Gramática JSON de Vosk: las frases válidas + "[unk]" para todo lo demás (reduce falsos positivos). */
    fun grammarJson(vararg phrases: String?): String {
        val items = phrases.filterNotNull().map { grammarTokens(it).joinToString(" ") }.filter { it.isNotEmpty() }.distinct()
        return (items + "[unk]").joinToString(separator = ",", prefix = "[", postfix = "]") { JSONObject.quote(it) }
    }

    /** Parsea un resultado JSON de Vosk (final o parcial con setWords/setPartialWords). */
    fun parse(json: String): List<HeardWord> = runCatching {
        val o = JSONObject(json)
        val arr = o.optJSONArray("result") ?: o.optJSONArray("partial_result")
        if (arr != null) List(arr.length()) { i ->
            val w = arr.getJSONObject(i)
            HeardWord(w.getString("word"), w.optDouble("conf", 1.0).toFloat())
        } else {
            (o.optString("text").ifEmpty { o.optString("partial") }).split(" ").filter { it.isNotBlank() }.map { HeardWord(it, 1f) }
        }
    }.getOrDefault(emptyList())

    /**
     * Busca [phrase] como secuencia contigua en [words]. Devuelve la confianza promedio de esa
     * secuencia, o null si no aparece.
     */
    fun find(words: List<HeardWord>, phrase: String): Float? {
        val target = normalize(phrase).split(" ").filter { it.isNotEmpty() }
        if (target.isEmpty() || words.size < target.size) return null
        val norm = words.map { normalize(it.word) }
        var best: Float? = null
        for (i in 0..words.size - target.size) {
            if ((target.indices).all { norm[i + it] == target[it] }) {
                val avg = (target.indices).map { words[i + it].conf }.average().toFloat()
                if (best == null || avg > best) best = avg
            }
        }
        return best
    }

    fun text(words: List<HeardWord>) = words.joinToString(" ") { it.word }.replace("[unk]", "…")
}
