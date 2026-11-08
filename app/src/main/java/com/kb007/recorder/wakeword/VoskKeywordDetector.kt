package com.kb007.recorder.wakeword

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.kb007.recorder.audio.dsp.LevelMeter
import org.vosk.Recognizer
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Detector de palabra clave 100 % offline con Vosk y gramática restringida.
 *
 * Para ahorrar batería, una compuerta de energía (VAD simple) solo deja pasar audio a Vosk
 * cuando hay sonido por encima del ruido de fondo, más ~300 ms previos (para no cortar el
 * comienzo de la palabra) y ~1,2 s posteriores (para que Vosk detecte el fin de la frase).
 */
class VoskKeywordDetector(private val models: VoskModelManager) : KeywordDetector {

    private class Block(val data: ShortArray)
    private val queue = ArrayBlockingQueue<Block>(250) // ~5 s de bloques de 20 ms
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun start(config: KeywordConfig, listener: (KeywordEvent) -> Unit): Boolean {
        stop()
        if (!models.assetsAvailable() && !models.isLoaded()) {
            listener(KeywordEvent.Error("Falta el modelo de voz en el APK"))
            return false
        }
        running = true
        queue.clear()
        worker = Thread({ run(config, listener) }, "KB007-Vosk").apply { start() }
        return true
    }

    override fun feed(pcm16k: ShortArray, len: Int) {
        if (!running) return
        // Si la cola se llena (CPU muy lenta), se descarta lo más viejo.
        val b = Block(pcm16k.copyOf(len))
        if (!queue.offer(b)) { queue.poll(); queue.offer(b) }
    }

    override fun stop() {
        running = false
        worker?.interrupt()
        worker?.join(1500)
        worker = null
        queue.clear()
    }

    private fun run(cfg: KeywordConfig, emit: (KeywordEvent) -> Unit) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE)
        val rec: Recognizer
        try {
            val model = models.get()
            val unknown = models.unknownWords(cfg.keyword)
            if (unknown.isNotEmpty()) {
                emit(KeywordEvent.Error("El modelo no conoce: ${unknown.joinToString()}. Elegí otra palabra clave."))
                running = false; return
            }
            val stop = cfg.stopWord?.takeIf { models.unknownWords(it).isEmpty() }
            if (cfg.stopWord != null && stop == null) emit(KeywordEvent.Error("La palabra de detención no está en el vocabulario; se ignora.", fatal = false))
            rec = Recognizer(model, 16_000f, KeywordMatcher.grammarJson(cfg.keyword, stop))
            rec.setWords(true)
            rec.setPartialWords(true)
            emit(KeywordEvent.Ready)
            loop(rec, cfg.copy(stopWord = stop), emit)
            rec.close()
        } catch (e: InterruptedException) {
            // detenido
        } catch (t: Throwable) {
            Log.e(TAG, "Fallo del detector", t)
            emit(KeywordEvent.Error("Error del reconocedor de voz: ${t.message}"))
        }
    }

    private fun loop(rec: Recognizer, cfg: KeywordConfig, emit: (KeywordEvent) -> Unit) {
        val preRoll = ArrayDeque<ShortArray>()     // ~300 ms antes de que se abra la compuerta
        var noiseFloor = -60f
        var openUntil = 0L
        var cooldownUntil = 0L
        var feeding = false

        fun handle(json: String, final: Boolean) {
            // Sin confianza por palabra, un parcial no es fiable: lo ignoramos.
            if (!final && !json.contains("\"conf\"")) return
            val words = KeywordMatcher.parse(json)
            if (words.isEmpty()) return
            val now = SystemClock.elapsedRealtime()
            val kw = KeywordMatcher.find(words, cfg.keyword)
            val st = cfg.stopWord?.let { KeywordMatcher.find(words, it) }
            // En parciales exigimos más confianza (son menos estables) para reaccionar antes sin falsos positivos.
            val need = if (final) cfg.threshold else maxOf(cfg.threshold + 0.15f, 0.85f)
            if (final) emit(KeywordEvent.Heard(KeywordMatcher.text(words), kw ?: st ?: 0f, accepted = (kw ?: st ?: 0f) >= cfg.threshold))
            if (now < cooldownUntil) return
            when {
                kw != null && kw >= need -> {
                    emit(KeywordEvent.Triggered(cfg.keyword, kw)); cooldownUntil = now + 2500; rec.reset()
                }
                st != null && st >= need -> {
                    emit(KeywordEvent.StopWord(cfg.stopWord!!, st)); cooldownUntil = now + 2500; rec.reset()
                }
            }
        }

        while (running) {
            val b = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val now = SystemClock.elapsedRealtime()
            val db = LevelMeter.measure(b.data, b.data.size).rmsDb
            // Piso de ruido adaptativo: baja rápido, sube despacio.
            noiseFloor = if (db < noiseFloor) noiseFloor + (db - noiseFloor) * 0.3f else noiseFloor + (db - noiseFloor) * 0.005f
            val voice = db > maxOf(noiseFloor + 9f, -55f)
            if (voice) openUntil = now + 1200

            if (now < openUntil) {
                if (!feeding) {
                    feeding = true
                    while (preRoll.isNotEmpty()) { val p = preRoll.pollFirst()!!; if (rec.acceptWaveForm(p, p.size)) handle(rec.result, true) }
                }
                if (rec.acceptWaveForm(b.data, b.data.size)) handle(rec.result, true)
                else handle(rec.partialResult, false)
            } else {
                if (feeding) {
                    feeding = false
                    handle(rec.finalResult, true) // cierra la frase pendiente
                }
                preRoll.addLast(b.data)
                while (preRoll.size > 15) preRoll.pollFirst()
            }
        }
    }

    companion object { private const val TAG = "KB007/Keyword" }
}
