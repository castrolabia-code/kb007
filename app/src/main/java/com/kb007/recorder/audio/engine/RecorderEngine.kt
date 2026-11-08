package com.kb007.recorder.audio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.kb007.recorder.audio.capture.CaptureConfig
import com.kb007.recorder.audio.capture.DeviceCapabilities
import com.kb007.recorder.audio.capture.MicCapture
import com.kb007.recorder.audio.dsp.Level
import com.kb007.recorder.audio.dsp.LevelMeter
import com.kb007.recorder.audio.dsp.MonoResampler
import com.kb007.recorder.audio.dsp.RingBuffer
import com.kb007.recorder.audio.dsp.SilenceDetector
import com.kb007.recorder.audio.dsp.SoftGain
import com.kb007.recorder.audio.encode.AudioSink
import com.kb007.recorder.audio.encode.EncodeSpec
import com.kb007.recorder.audio.encode.Opus
import com.kb007.recorder.data.StorageManager
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.db.RecordingDao
import com.kb007.recorder.data.db.RecordingStatus
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.CallPolicy
import com.kb007.recorder.data.prefs.MicSource
import com.kb007.recorder.data.prefs.SettingsRepository
import com.kb007.recorder.data.prefs.TriggerType
import com.kb007.recorder.wakeword.KeywordConfig
import com.kb007.recorder.wakeword.KeywordDetector
import com.kb007.recorder.wakeword.KeywordEvent
import com.kb007.recorder.wakeword.NoKeywordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Motor de grabación de KB-007.
 *
 * Un único [MicCapture] (un solo AudioRecord) alimenta a la vez:
 *  - el medidor de nivel (VU),
 *  - el buffer circular de pre-grabación,
 *  - el detector de palabra clave (remuestreado a 16 kHz mono),
 *  - y, si hay una grabación en curso, el hilo escritor que codifica a disco.
 *
 * Todas las operaciones de control se serializan en [control] (un solo hilo lógico),
 * así no hay carreras entre la UI, la notificación y el detector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val dao: RecordingDao,
    private val storage: StorageManager,
    private val scope: CoroutineScope,
    private val capabilities: () -> DeviceCapabilities,
) {
    private val control = Dispatchers.Default.limitedParallelism(1)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _level = MutableStateFlow(Level.SILENT)
    /** Nivel de entrada en tiempo real (~50 actualizaciones por segundo). */
    val level: StateFlow<Level> = _level.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EngineEvent> = _events

    /** Detector de palabra clave (Vosk; se inyecta desde AppContainer). */
    var keywordDetector: KeywordDetector = NoKeywordDetector

    private val _keywordEvents = MutableSharedFlow<KeywordEvent>(extraBufferCapacity = 32)
    /** Todo lo que escucha el detector (para "probar palabra clave"). */
    val keywordEvents: SharedFlow<KeywordEvent> = _keywordEvents

    /** Modo prueba: reconoce y reporta, pero no dispara grabaciones. */
    @Volatile var keywordDryRun: Boolean = false

    // ---- Estado interno (solo se toca desde [control], salvo lo marcado @Volatile) ----
    private var mic: MicCapture? = null
    private val gain = SoftGain()
    private val preRoll = RingBuffer(1)
    private var resampler: MonoResampler? = null
    @Volatile private var session: Session? = null
    @Volatile private var feedKeyword = false
    /** El detector fue iniciado y no falló (aunque el modelo todavía esté cargando). */
    private var detectorRunning = false
    private var monitorJob: Job? = null
    private var monitoring = false
    private var focusRequest: AudioFocusRequest? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    /** Datos de la grabación en curso. */
    private class Session(
        val id: Long,
        val title: String,
        val trigger: TriggerType,
        val spec: EncodeSpec,
        val sink: AudioSink,
        val queue: ArrayBlockingQueue<ShortArray>,
        val writer: Thread,
        val silence: SilenceDetector,
        val silenceStopMs: Long,
        val maxDurationMs: Long,
    ) {
        @Volatile var paused = false
        @Volatile var frames = 0L
        @Volatile var dropped = 0
        @Volatile var writerError: Throwable? = null
    }

    init {
        // Si cambian los ajustes de audio mientras solo se escucha, reabrimos el micrófono.
        scope.launch {
            settings.state.map { captureConfigFor(it) to it.gainDb }.distinctUntilChanged().drop(1).collect { (cfg, g) ->
                gain.gainDb = g
                withContext(control) {
                    val m = mic ?: return@withContext
                    if (session == null && m.config != cfg) { closeMic(); openMic() }
                }
            }
        }
        scope.launch {
            settings.state.map { KeywordConfig(it.keyword, it.stopKeyword.ifBlank { null }, it.keywordThreshold) }
                .distinctUntilChanged().drop(1).collect {
                    withContext(control) { if (_state.value.listening) restartDetector() }
                }
        }
    }

    // ======================= API pública (asíncrona) =======================

    fun startListening() = scope.launch(control) { doStartListening() }
    fun stopListening() = scope.launch(control) { doStopListening() }
    fun startRecording(trigger: TriggerType = TriggerType.MANUAL, keyword: String? = null) =
        scope.launch(control) { doStartRecording(trigger, keyword) }
    fun stopRecording() = scope.launch(control) { doStopRecording() }
    fun pause() = scope.launch(control) { doPause(PauseReason.USER) }
    fun resume() = scope.launch(control) { doResume(PauseReason.USER) }
    /** Abre el micrófono solo para medir nivel (pantalla de ajustes). No graba nada. */
    fun setMonitoring(on: Boolean) = scope.launch(control) {
        monitoring = on
        if (on) openMic()
        else if (session == null && !_state.value.listening) closeMic()
    }
    fun togglePause() = scope.launch(control) {
        if (_state.value.mode == RecMode.PAUSED) doResume(PauseReason.USER) else doPause(PauseReason.USER)
    }

    // ======================= Micrófono =======================

    private fun captureConfigFor(s: AppSettings): CaptureConfig {
        val caps = capabilities()
        val rate = if (s.format == AudioFormatType.OPUS_OGG) Opus.captureRateFor(s.sampleRate) else s.sampleRate
        val source = if (s.micSource == MicSource.UNPROCESSED && !caps.unprocessed) MicSource.VOICE else s.micSource
        return CaptureConfig(
            sampleRate = rate,
            channels = if (s.channels == 2 && caps.stereo) 2 else 1,
            source = source,
            noiseSuppressor = s.noiseSuppressor && caps.noiseSuppressor,
            autoGain = s.autoGain && caps.autoGain,
            echoCanceler = s.echoCanceler && caps.echoCanceler,
        )
    }

    private fun openMic(): Boolean {
        if (mic != null) return true
        val s = settings.state.value
        val cfg = captureConfigFor(s)
        gain.gainDb = s.gainDb
        val m = MicCapture(cfg, gain, ::onPcm) { t -> scope.launch(control) { onMicError(t) } }
        return try {
            m.start()
            mic = m
            val ch = m.actualChannels
            preRoll.resize(cfg.sampleRate * ch * s.preRollSec.coerceIn(0, 10).coerceAtLeast(1))
            resampler = MonoResampler(cfg.sampleRate, ch, 16_000)
            registerRecordingCallback()
            _state.update { it.copy(micOpen = true) }
            startMonitor()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo abrir el micrófono", t)
            emit(EngineEvent.Message(t.message ?: "No se pudo abrir el micrófono", isError = true))
            false
        }
    }

    private fun closeMic() {
        monitorJob?.cancel(); monitorJob = null
        mic?.stop()
        mic = null
        resampler = null
        preRoll.clear()
        unregisterRecordingCallback()
        _level.value = Level.SILENT
        _state.update { it.copy(micOpen = false) }
    }

    private suspend fun onMicError(t: Throwable) {
        Log.e(TAG, "Error de micrófono", t)
        val wasListening = _state.value.listening
        if (session != null) {
            doStopRecording()
            emit(EngineEvent.Message("Se interrumpió el micrófono. La grabación se guardó.", isError = true))
        }
        closeMic()
        if (wasListening) {
            delay(1500)
            if (!openMic()) {
                stopDetector()
                _state.update { it.copy(listening = false) }
                emit(EngineEvent.Message("No se pudo retomar la escucha: micrófono ocupado", isError = true))
            }
        }
    }

    /** Hilo de captura: debe ser rápido. */
    private fun onPcm(pcm: ShortArray, len: Int) {
        val lvl = LevelMeter.measure(pcm, len)
        _level.value = lvl
        val s = session
        if (s != null && !s.paused) {
            val copy = pcm.copyOf(len)
            if (!s.queue.offer(copy)) s.dropped++
            s.frames += len / (mic?.actualChannels ?: 1)
            s.silence.feed(lvl, 20)
        } else if (_state.value.listening) {
            preRoll.write(pcm, 0, len)
        }
        if (feedKeyword) {
            resampler?.let { r ->
                val (out, n) = r.process(pcm, len)
                if (n > 0) keywordDetector.feed(out, n)
            }
        }
    }

    // ======================= Escucha (palabra clave) =======================

    private suspend fun doStartListening() {
        if (_state.value.listening) return
        if (!openMic()) return
        _state.update { it.copy(listening = true) }
        settings.update { it.copy(listeningEnabled = true) }
        restartDetector()
    }

    private suspend fun doStopListening() {
        if (!_state.value.listening) return
        stopDetector()
        _state.update { it.copy(listening = false) }
        settings.update { it.copy(listeningEnabled = false) }
        if (session == null && !monitoring) closeMic()
    }

    private fun restartDetector() {
        stopDetector()
        val s = settings.state.value
        val cfg = KeywordConfig(s.keyword.trim(), s.stopKeyword.trim().ifBlank { null }, s.keywordThreshold)
        val ok = cfg.keyword.isNotEmpty() && keywordDetector.start(cfg) { ev ->
            _keywordEvents.tryEmit(ev)
            scope.launch(control) { onKeyword(ev) }
        }
        detectorRunning = ok
        feedKeyword = ok && !_state.value.inCall
        // "Listo" recién cuando el modelo cargó (evento Ready); mientras tanto el LED ámbar parpadea.
        _state.update { it.copy(detectorReady = false) }
        if (!ok) emit(EngineEvent.Message(if (cfg.keyword.isEmpty()) "Configurá una palabra clave en Ajustes" else "El detector de palabra clave no está disponible", isError = true))
    }

    private fun stopDetector() {
        feedKeyword = false
        detectorRunning = false
        keywordDetector.stop()
        _state.update { it.copy(detectorReady = false) }
    }

    private suspend fun onKeyword(ev: KeywordEvent) {
        when (ev) {
            is KeywordEvent.Ready -> _state.update { it.copy(detectorReady = true) }
            is KeywordEvent.Triggered -> if (!keywordDryRun && session == null && _state.value.listening && !_state.value.inCall) {
                emit(EngineEvent.KeywordTriggered(ev.text))
                doStartRecording(TriggerType.KEYWORD, ev.text)
            }
            is KeywordEvent.StopWord -> if (!keywordDryRun && session != null) doStopRecording()
            is KeywordEvent.Error -> {
                emit(EngineEvent.Message(ev.message, isError = true))
                if (ev.fatal) { feedKeyword = false; detectorRunning = false; _state.update { it.copy(detectorReady = false) } }
            }
            is KeywordEvent.Heard -> Unit
        }
    }

    // ======================= Grabación =======================

    private suspend fun doStartRecording(trigger: TriggerType, keyword: String?) {
        if (session != null) return
        if (storage.freeBytes() < MIN_FREE_BYTES) {
            emit(EngineEvent.Message("Espacio insuficiente para grabar", isError = true)); return
        }
        if (!openMic()) return
        val m = mic ?: return
        val s = settings.state.value
        val spec = EncodeSpec(s.format, m.config.sampleRate, m.actualChannels, s.bitrateKbps)
        val now = System.currentTimeMillis()
        val seq = withContext(Dispatchers.IO) { dao.count() } + 1
        val title = storage.buildName(s.namePattern, trigger, seq, java.util.Date(now))

        val id = withContext(Dispatchers.IO) {
            dao.insert(
                Recording(
                    title = title, format = s.format, sampleRate = spec.sampleRate, channels = spec.channels,
                    bitrateKbps = s.bitrateKbps, createdAt = now, trigger = trigger, keyword = keyword,
                )
            )
        }
        val sink = try {
            withContext(Dispatchers.IO) { AudioSink.create(spec, storage.workDir, "rec_$id") }
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo crear el codificador", t)
            withContext(Dispatchers.IO) { dao.delete(id) }
            emit(EngineEvent.Message("No se pudo iniciar el codificador ${s.format.label}", isError = true))
            if (!_state.value.listening) closeMic()
            return
        }
        withContext(Dispatchers.IO) { dao.get(id)?.let { dao.update(it.copy(workPath = sink.workFile.absolutePath)) } }

        val queue = ArrayBlockingQueue<ShortArray>(QUEUE_BLOCKS)
        val silenceSec = if (trigger == TriggerType.KEYWORD && s.keywordSilenceStopSec > 0) s.keywordSilenceStopSec else s.silenceStopSec
        lateinit var sess: Session
        val writer = Thread({ writerLoop(sess) }, "KB007-Writer")
        sess = Session(
            id, title, trigger, spec, sink, queue, writer,
            SilenceDetector(s.silenceThresholdDb), silenceSec * 1000L, s.maxDurationMin * 60_000L,
        )
        // Pre-grabación: lo dicho justo antes (y durante) la palabra clave.
        if (trigger == TriggerType.KEYWORD) {
            val pre = preRoll.drain()
            if (pre.isNotEmpty()) { queue.offer(pre); sess.frames += pre.size / spec.channels }
        } else preRoll.clear()

        session = sess
        writer.start()
        if (trigger == TriggerType.MANUAL) requestFocus()
        _state.update {
            it.copy(
                mode = RecMode.RECORDING, recordingId = id, title = title, trigger = trigger, elapsedMs = 0,
                chronoBase = SystemClock.elapsedRealtime() - sess.frames * 1000 / spec.sampleRate, pauseReason = null,
            )
        }
    }

    private fun writerLoop(s: Session) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        try {
            while (true) {
                val block = s.queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                if (block === POISON) break
                s.sink.write(block, 0, block.size)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error escribiendo audio", t)
            s.writerError = t
            scope.launch(control) { if (session === s) doStopRecording() }
        }
    }

    private suspend fun doStopRecording() {
        val s = session ?: return
        session = null
        abandonFocus()
        _state.update { it.copy(mode = RecMode.IDLE, pauseReason = null, finalizing = true, recordingId = null) }
        if (!_state.value.listening && !monitoring) closeMic()

        withContext(Dispatchers.IO) {
            s.queue.put(POISON)
            s.writer.join(10_000)
            val durationMs = s.frames * 1000 / s.spec.sampleRate
            try {
                if (s.writerError != null) throw s.writerError!!
                val finalFile = s.sink.finish()
                val pub = storage.publish(finalFile, s.title, s.spec.format.mime, settings.state.value.storageTarget, settings.state.value.customTreeUri)
                dao.get(s.id)?.let {
                    dao.update(it.copy(uri = pub.uri.toString(), workPath = "", durationMs = durationMs, sizeBytes = pub.sizeBytes, status = RecordingStatus.DONE))
                }
                emit(EngineEvent.Saved(s.id, s.title))
                if (s.dropped > 0) Log.w(TAG, "Se descartaron ${s.dropped} bloques por cola llena")
            } catch (t: Throwable) {
                Log.e(TAG, "Fallo al finalizar; se intenta recuperar", t)
                runCatching { s.sink.abort() }
                val ok = RecoveryManager(dao, storage, settings).recoverOne(s.id)
                emit(EngineEvent.Message(if (ok) "La grabación se recuperó parcialmente" else "No se pudo guardar la grabación", isError = !ok))
            }
        }
        _state.update { it.copy(finalizing = false, elapsedMs = 0) }
    }

    private fun doPause(reason: PauseReason) {
        val s = session ?: return
        if (s.paused) return
        s.paused = true
        _state.update { it.copy(mode = RecMode.PAUSED, pauseReason = reason) }
    }

    private fun doResume(reason: PauseReason) {
        val s = session ?: return
        val current = _state.value.pauseReason
        // Una pausa del usuario solo la levanta el usuario; las automáticas, su propia causa o el usuario.
        if (!s.paused || (current != reason && reason != PauseReason.USER)) return
        s.paused = false
        s.silence.reset()
        _state.update {
            it.copy(mode = RecMode.RECORDING, pauseReason = null,
                chronoBase = SystemClock.elapsedRealtime() - s.frames * 1000 / s.spec.sampleRate)
        }
    }

    // ======================= Vigilancia periódica =======================

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(control) {
            var tick = 0
            while (isActive && mic != null) {
                delay(250)
                tick++
                val s = session
                if (s != null) {
                    val elapsed = s.frames * 1000 / s.spec.sampleRate
                    _state.update { it.copy(elapsedMs = elapsed) }
                    if (s.maxDurationMs > 0 && elapsed >= s.maxDurationMs) {
                        emit(EngineEvent.Message("Duración máxima alcanzada")); doStopRecording(); continue
                    }
                    if (s.silenceStopMs > 0 && !s.paused && s.silence.silentMs >= s.silenceStopMs) {
                        emit(EngineEvent.Message("Corte automático por silencio")); doStopRecording(); continue
                    }
                    if (tick % 20 == 0 && storage.freeBytes() < MIN_FREE_BYTES) {
                        emit(EngineEvent.Message("Espacio insuficiente: grabación guardada", isError = true)); doStopRecording(); continue
                    }
                }
                if (tick % 2 == 0) checkCall()
            }
        }
    }

    /** Llamadas: modo de audio IN_CALL / IN_COMMUNICATION (también cubre llamadas de WhatsApp, etc.). */
    private suspend fun checkCall() {
        val mode = audioManager.mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        val was = _state.value.inCall
        if (inCall == was) return
        _state.update { it.copy(inCall = inCall) }
        feedKeyword = !inCall && detectorRunning
        if (inCall) {
            if (session != null) {
                when (settings.state.value.callPolicy) {
                    CallPolicy.PAUSE -> doPause(PauseReason.CALL)
                    CallPolicy.SAVE_AND_STOP -> { doStopRecording(); emit(EngineEvent.Message("Llamada entrante: grabación guardada")) }
                }
            }
        } else doResume(PauseReason.CALL)
    }

    // ======================= Foco de audio y micrófono silenciado =======================

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                scope.launch(control) {
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> doPause(PauseReason.FOCUS)
                        AudioManager.AUDIOFOCUS_GAIN -> doResume(PauseReason.FOCUS)
                        // Pérdida permanente (p. ej. el usuario abre música): seguimos grabando.
                    }
                }
            }.build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Android 10+: avisa si el sistema silenció nuestro micrófono porque otra app lo tomó. */
    private fun registerRecordingCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || recordingCallback != null) return
        val cb = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val sid = mic?.sessionId ?: return
                val mine = configs.firstOrNull { it.clientAudioSessionId == sid } ?: return
                val silenced = mine.isClientSilenced
                scope.launch(control) {
                    if (silenced) doPause(PauseReason.SILENCED) else doResume(PauseReason.SILENCED)
                }
            }
        }
        audioManager.registerAudioRecordingCallback(cb, null)
        recordingCallback = cb
    }

    private fun unregisterRecordingCallback() {
        recordingCallback?.let { audioManager.unregisterAudioRecordingCallback(it) }
        recordingCallback = null
    }

    private fun emit(e: EngineEvent) { _events.tryEmit(e) }

    companion object {
        private const val TAG = "KB007/Engine"
        private const val MIN_FREE_BYTES = 30L * 1024 * 1024
        /** ~20 s de bloques de 20 ms antes de descartar (el escritor nunca debería atrasarse tanto). */
        private const val QUEUE_BLOCKS = 1000
        private val POISON = ShortArray(0)
    }
}
