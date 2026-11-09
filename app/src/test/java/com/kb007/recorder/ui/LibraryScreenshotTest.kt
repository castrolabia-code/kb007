package com.kb007.recorder.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.db.RecordingStatus
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.TriggerType
import com.kb007.recorder.ui.library.LibraryActions
import com.kb007.recorder.ui.library.LibraryScreen
import com.kb007.recorder.ui.library.LibraryUi
import com.kb007.recorder.ui.library.SortBy
import com.kb007.recorder.ui.theme.KbThemes
import org.junit.Rule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val items = listOf(
        Recording(1, "Ensayo jueves", "file:///x", "", AudioFormatType.AAC_M4A, 48_000, 2, 192, 754_000, 18_200_000, 1_762_000_000_000, TriggerType.MANUAL),
        Recording(2, "Idea de canción", "file:///y", "", AudioFormatType.OPUS_OGG, 48_000, 1, 64, 95_000, 760_000, 1_761_900_000_000, TriggerType.KEYWORD, "grabadora"),
        Recording(3, "Reunión de equipo", "file:///z", "", AudioFormatType.WAV_PCM, 44_100, 1, 0, 3_725_000, 328_000_000, 1_761_500_000_000, TriggerType.MANUAL, status = RecordingStatus.RECOVERED),
        Recording(4, "Clase de guitarra", "file:///w", "", AudioFormatType.AAC_M4A, 44_100, 1, 128, 1_800_000, 28_000_000, 1_761_400_000_000, TriggerType.MANUAL),
        Recording(5, "Nota para Juan", "file:///v", "", AudioFormatType.AAC_M4A, 22_050, 1, 64, 42_000, 340_000, 1_761_300_000_000, TriggerType.KEYWORD, "grabadora"),
        Recording(6, "Entrevista", "file:///u", "", AudioFormatType.AAC_M4A, 48_000, 1, 96, 2_400_000, 29_000_000, 1_761_200_000_000, TriggerType.MANUAL, cassetteStyle = 7),
    )

    private val actions = object : LibraryActions {
        override fun back() = Unit
        override fun setQuery(q: String) = Unit
        override fun setSort(s: SortBy) = Unit
        override fun load(r: Recording) = Unit
        override fun setStyle(r: Recording, style: Int) = Unit
        override fun rename(r: Recording, title: String) = Unit
        override fun delete(r: Recording) = Unit
        override fun share(r: Recording, toWhatsApp: Boolean, convert: Boolean) = Unit
        override fun needsConversion(r: Recording) = false
        override fun whatsAppInstalled() = true
    }

    @Test fun shelfMetal() {
        compose.setContent { LibraryScreen(LibraryUi(items, 6), KbThemes.metal80, actions, null) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/library_metal80.png")
    }

    @Test fun styleCatalog() {
        compose.setContent {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF222222))) {
                com.kb007.recorder.ui.deck.CassetteStyle.ALL.chunked(3).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { st ->
                            com.kb007.recorder.ui.deck.Cassette(KbThemes.metal80, st.name, 0.4f, 0f, 30f,
                                androidx.compose.ui.Modifier.padding(6.dp).width(125.dp).aspectRatio(com.kb007.recorder.ui.deck.CASSETTE_ASPECT), style = st)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/cassette_styles.png")
    }

    @Test fun shelfWood() {
        compose.setContent { LibraryScreen(LibraryUi(items, 6, sort = SortBy.DURATION), KbThemes.wood70, actions, null) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/library_wood70.png")
    }
}
