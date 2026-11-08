package com.kb007.recorder.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kb007.recorder.audio.capture.DeviceCapabilities
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.data.prefs.AudioPreset
import com.kb007.recorder.ui.settings.SettingsActions
import com.kb007.recorder.ui.settings.SettingsScreen
import com.kb007.recorder.ui.settings.khz
import com.kb007.recorder.ui.theme.KbThemes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h2400dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val actions = object : SettingsActions {
        override fun back() = Unit
        override fun update(f: (AppSettings) -> AppSettings) = Unit
        override fun preset(p: AudioPreset) = Unit
        override fun pickFolder() = Unit
        override fun levelDb() = -24f
    }

    @Test fun settingsLong() {
        val caps = DeviceCapabilities(unprocessed = false, noiseSuppressor = true, autoGain = true, echoCanceler = false,
            stereo = true, externalMic = false, opus = true, sampleRates = listOf(16_000, 22_050, 44_100, 48_000))
        compose.mainClock.autoAdvance = false
        compose.setContent { SettingsScreen(AppSettings(silenceStopSec = 10), caps, KbThemes.reporter, actions, "/storage/emulated/0/Android/data/com.kb007.recorder/files/Music/KB-007") }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_reporter.png")
    }

    @Test fun khzLabels() {
        assertEquals("22,05 kHz", khz(22_050)); assertEquals("44,1 kHz", khz(44_100)); assertEquals("48 kHz", khz(48_000))
    }
}
