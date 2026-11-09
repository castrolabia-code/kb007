package com.kb007.recorder.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.ui.settings.KeywordSection
import com.kb007.recorder.ui.settings.KeywordTestDialog
import com.kb007.recorder.ui.settings.VocabCheck
import com.kb007.recorder.ui.theme.KbThemes
import com.kb007.recorder.wakeword.KeywordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas para material de difusión: sección de palabra clave y prueba en vivo. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1500dp-xxhdpi")
class KeywordScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keywordSection() {
        compose.setContent {
            Column(Modifier.background(Color(0xFF111111)).padding(16.dp)) {
                KeywordSection(AppSettings(keyword = "atención grabadora", stopKeyword = "basta"), KbThemes.reporter,
                    VocabCheck(emptyList(), emptyList()), batteryExempt = true, update = {}, onTest = {}, onBattery = {})
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/keyword_section.png")
    }

    @Test @Config(qualifiers = "w411dp-h700dp-xxhdpi")
    fun keywordTest() {
        compose.setContent {
            KeywordTestDialog("atención grabadora", 0.6f, ready = true, heard = listOf(
                KeywordEvent.Heard("hola qué tal", 0f, false),
                KeywordEvent.Heard("atención grabadora", 0.97f, true),
                KeywordEvent.Heard("…", 0f, false),
                KeywordEvent.Heard("atención grabadora", 0.94f, true),
            ), lastError = null, onClose = {})
        }
        compose.onAllNodes(androidx.compose.ui.test.isRoot())[1].captureRoboImage("build/outputs/roborazzi/keyword_test.png")
    }
}
