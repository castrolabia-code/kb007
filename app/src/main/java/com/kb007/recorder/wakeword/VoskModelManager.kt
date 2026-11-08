package com.kb007.recorder.wakeword

import android.content.Context
import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import java.io.File

/**
 * Prepara y carga el modelo Vosk en español. El modelo viaja dentro del APK
 * (assets/model-es) y la primera vez se copia a la memoria interna, porque Vosk
 * necesita rutas de archivo reales.
 */
class VoskModelManager(private val context: Context) {
    private var model: Model? = null
    private val lock = Any()

    /** JNA: función del API C de Vosk que el binding de Java no expone. */
    private interface VoskExtra : Library {
        fun vosk_model_find_word(model: Pointer, word: String): Int
    }
    private val extra: VoskExtra? by lazy { runCatching { Native.load("vosk", VoskExtra::class.java) }.getOrNull() }

    fun assetsAvailable(): Boolean = runCatching { context.assets.list(ASSET_DIR)?.isNotEmpty() == true }.getOrDefault(false)

    /** Carga (y si hace falta, descomprime) el modelo. Bloqueante: llamar fuera del hilo principal. */
    fun get(): Model = synchronized(lock) {
        model?.let { return it }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val dir = ensureUnpacked()
        val t = System.currentTimeMillis()
        Model(dir.absolutePath).also {
            model = it
            Log.i(TAG, "Modelo cargado en ${System.currentTimeMillis() - t} ms")
        }
    }

    fun isLoaded() = model != null

    private fun ensureUnpacked(): File {
        val dir = File(context.filesDir, "vosk/$VERSION")
        val marker = File(dir, ".listo")
        if (marker.exists()) return dir
        require(assetsAvailable()) { "El modelo de voz no está incluido en el APK (ver README: tools/fetch_model.sh)" }
        dir.deleteRecursively(); dir.mkdirs()
        copyAssetDir(ASSET_DIR, dir)
        marker.writeText("ok")
        // Borra versiones viejas del modelo
        File(context.filesDir, "vosk").listFiles()?.filter { it.name != VERSION }?.forEach { it.deleteRecursively() }
        return dir
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            context.assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it, 64 * 1024) } }
            return
        }
        dest.mkdirs()
        for (c in children) copyAssetDir("$assetPath/$c", File(dest, c))
    }

    /** Palabras de [phrase] que el modelo NO conoce (no se pueden reconocer). */
    fun unknownWords(phrase: String): List<String> {
        val m = get()
        val x = extra ?: return emptyList()
        return KeywordMatcher.grammarTokens(phrase).filter { x.vosk_model_find_word(m.pointer, it) < 0 }
    }

    companion object {
        private const val TAG = "KB007/Vosk"
        const val ASSET_DIR = "model-es"
        const val VERSION = "vosk-model-small-es-0.42"
    }
}
