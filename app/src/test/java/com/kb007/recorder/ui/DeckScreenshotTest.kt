package com.kb007.recorder.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kb007.recorder.ui.deck.DeckActions
import com.kb007.recorder.ui.deck.DeckKey
import com.kb007.recorder.ui.deck.DeckScreen
import com.kb007.recorder.ui.deck.DeckUi
import com.kb007.recorder.ui.deck.Transport
import com.kb007.recorder.ui.fx.DeckFx
import com.kb007.recorder.ui.theme.KbTheme
import com.kb007.recorder.ui.theme.KbThemes
import com.kb007.recorder.ui.theme.LocalKbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas de la pantalla principal para revisar el diseño sin un teléfono. Salida: build/outputs/roborazzi */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DeckScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val fakeActions = object : DeckActions {
        override fun levelDb() = -16f
        override fun onKey(key: DeckKey) = Unit
        override fun toggleListening() = Unit
    }

    private val recording = DeckUi(
        transport = Transport.RECORD, locked = setOf(DeckKey.REC, DeckKey.PLAY), tapeTitle = "Ensayo jueves",
        hasTape = true, progress = 0.32f, tapeSpeed = 1f, timeMs = 754_000, recording = true,
        listening = true, detectorReady = true, micOpen = true, status = "GRABANDO", tapeId = -3,
    )
    private val playing = DeckUi(
        transport = Transport.PLAY, locked = setOf(DeckKey.PLAY), tapeTitle = "Nota para Juan",
        hasTape = true, progress = 0.78f, tapeSpeed = 1f, timeMs = 95_000, durationMs = 128_000,
        status = "REPRODUCIENDO", tapeId = 5,
    )
    private val empty = DeckUi(status = "SIN CASSETTE")

    private fun shot(theme: KbTheme, ui: DeckUi, name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKbTheme provides theme) {
                DeckScreen(fakeActions, theme, DeckFx(RuntimeEnvironment.getApplication()), ui, {}, {}, message = null)
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun metalRecording() = shot(KbThemes.metal80, recording, "deck_metal80_rec")

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun sportPlaying() = shot(KbThemes.sport, playing, "deck_sport_play")

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun reporterEmpty() = shot(KbThemes.reporter, empty, "deck_reporter_empty")

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun woodRecording() = shot(KbThemes.wood70, recording.copy(progress = 0.7f), "deck_wood70_rec")

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun smallPhone() = shot(KbThemes.metal80, playing, "deck_small_360x640")

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet() = shot(KbThemes.wood70, playing, "deck_tablet_800x1280")

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() = shot(KbThemes.sport, recording, "deck_landscape")
}
