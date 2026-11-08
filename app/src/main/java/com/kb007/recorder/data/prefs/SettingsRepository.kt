package com.kb007.recorder.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kb007_settings")

/** Lectura/escritura de [AppSettings] sobre DataStore Preferences. */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    private object K {
        val format = stringPreferencesKey("format")
        val sampleRate = intPreferencesKey("sample_rate")
        val bitrate = intPreferencesKey("bitrate")
        val channels = intPreferencesKey("channels")
        val source = stringPreferencesKey("source")
        val ns = booleanPreferencesKey("ns")
        val agc = booleanPreferencesKey("agc")
        val aec = booleanPreferencesKey("aec")
        val gain = floatPreferencesKey("gain_db")
        val silenceStop = intPreferencesKey("silence_stop")
        val silenceThr = floatPreferencesKey("silence_thr")
        val maxDur = intPreferencesKey("max_dur")
        val callPolicy = stringPreferencesKey("call_policy")
        val storage = stringPreferencesKey("storage")
        val tree = stringPreferencesKey("tree_uri")
        val pattern = stringPreferencesKey("name_pattern")
        val theme = stringPreferencesKey("theme")
        val sounds = booleanPreferencesKey("sounds")
        val haptics = booleanPreferencesKey("haptics")
        val keyword = stringPreferencesKey("keyword")
        val stopKeyword = stringPreferencesKey("stop_keyword")
        val kwThr = floatPreferencesKey("kw_thr")
        val preRoll = intPreferencesKey("pre_roll")
        val kwSilence = intPreferencesKey("kw_silence")
        val kwBeep = booleanPreferencesKey("kw_beep")
        val kwVibrate = booleanPreferencesKey("kw_vibrate")
        val listening = booleanPreferencesKey("listening")
        val resumeBoot = booleanPreferencesKey("resume_boot")
        val onboarding = booleanPreferencesKey("onboarding")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, def: E): E =
        this[key]?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def

    val flow: Flow<AppSettings> = store.data.map { fromPrefs(it) }

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            format = p.enum(K.format, d.format),
            sampleRate = p[K.sampleRate] ?: d.sampleRate,
            bitrateKbps = p[K.bitrate] ?: d.bitrateKbps,
            channels = p[K.channels] ?: d.channels,
            micSource = p.enum(K.source, d.micSource),
            noiseSuppressor = p[K.ns] ?: d.noiseSuppressor,
            autoGain = p[K.agc] ?: d.autoGain,
            echoCanceler = p[K.aec] ?: d.echoCanceler,
            gainDb = p[K.gain] ?: d.gainDb,
            silenceStopSec = p[K.silenceStop] ?: d.silenceStopSec,
            silenceThresholdDb = p[K.silenceThr] ?: d.silenceThresholdDb,
            maxDurationMin = p[K.maxDur] ?: d.maxDurationMin,
            callPolicy = p.enum(K.callPolicy, d.callPolicy),
            storageTarget = p.enum(K.storage, d.storageTarget),
            customTreeUri = p[K.tree],
            namePattern = p[K.pattern] ?: d.namePattern,
            themeId = p[K.theme] ?: d.themeId,
            mechSounds = p[K.sounds] ?: d.mechSounds,
            haptics = p[K.haptics] ?: d.haptics,
            keyword = p[K.keyword] ?: d.keyword,
            stopKeyword = p[K.stopKeyword] ?: d.stopKeyword,
            keywordThreshold = p[K.kwThr] ?: d.keywordThreshold,
            preRollSec = p[K.preRoll] ?: d.preRollSec,
            keywordSilenceStopSec = p[K.kwSilence] ?: d.keywordSilenceStopSec,
            keywordBeep = p[K.kwBeep] ?: d.keywordBeep,
            keywordVibrate = p[K.kwVibrate] ?: d.keywordVibrate,
            listeningEnabled = p[K.listening] ?: d.listeningEnabled,
            resumeAfterBoot = p[K.resumeBoot] ?: d.resumeAfterBoot,
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
        )
    }

    /** Estado siempre disponible (para el servicio y la UI). */
    val state: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun current(): AppSettings = flow.first()

    /** Aplica una transformación y persiste todos los campos. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val s = transform(fromPrefs(p))
            p[K.format] = s.format.name
            p[K.sampleRate] = s.sampleRate
            p[K.bitrate] = s.bitrateKbps
            p[K.channels] = s.channels
            p[K.source] = s.micSource.name
            p[K.ns] = s.noiseSuppressor
            p[K.agc] = s.autoGain
            p[K.aec] = s.echoCanceler
            p[K.gain] = s.gainDb
            p[K.silenceStop] = s.silenceStopSec
            p[K.silenceThr] = s.silenceThresholdDb
            p[K.maxDur] = s.maxDurationMin
            p[K.callPolicy] = s.callPolicy.name
            p[K.storage] = s.storageTarget.name
            if (s.customTreeUri != null) p[K.tree] = s.customTreeUri else p.remove(K.tree)
            p[K.pattern] = s.namePattern
            p[K.theme] = s.themeId
            p[K.sounds] = s.mechSounds
            p[K.haptics] = s.haptics
            p[K.keyword] = s.keyword
            p[K.stopKeyword] = s.stopKeyword
            p[K.kwThr] = s.keywordThreshold
            p[K.preRoll] = s.preRollSec
            p[K.kwSilence] = s.keywordSilenceStopSec
            p[K.kwBeep] = s.keywordBeep
            p[K.kwVibrate] = s.keywordVibrate
            p[K.listening] = s.listeningEnabled
            p[K.resumeBoot] = s.resumeAfterBoot
            p[K.onboarding] = s.onboardingDone
        }
    }

    /** Aplica un preset rápido. */
    suspend fun applyPreset(preset: AudioPreset) = update {
        it.copy(
            format = preset.format, sampleRate = preset.sampleRate, bitrateKbps = preset.bitrateKbps,
            channels = preset.channels, micSource = preset.source,
            noiseSuppressor = preset.ns, autoGain = preset.agc,
        )
    }
}
