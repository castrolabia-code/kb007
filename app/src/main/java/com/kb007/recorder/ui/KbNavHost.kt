package com.kb007.recorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.prefs.AppSettings
import com.kb007.recorder.data.prefs.AudioPreset
import com.kb007.recorder.ui.settings.SettingsActions
import com.kb007.recorder.ui.settings.SettingsScreen
import com.kb007.recorder.ui.settings.SettingsViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.kb007.recorder.ui.deck.DeckActions
import com.kb007.recorder.ui.deck.DeckScreen
import com.kb007.recorder.ui.settings.KeywordSection
import com.kb007.recorder.ui.settings.KeywordTestDialog
import com.kb007.recorder.ui.settings.isIgnoringBattery
import com.kb007.recorder.ui.settings.requestIgnoreBattery
import com.kb007.recorder.ui.library.LibraryActions
import com.kb007.recorder.ui.library.LibraryScreen
import com.kb007.recorder.ui.library.LibraryViewModel
import com.kb007.recorder.ui.library.SortBy
import com.kb007.recorder.ui.deck.DeckViewModel
import com.kb007.recorder.ui.fx.DeckFx
import com.kb007.recorder.ui.theme.KbTheme
import kotlinx.coroutines.delay

object Routes {
    const val DECK = "deck"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun KbNavHost(theme: KbTheme, fx: DeckFx) {
    val nav = rememberNavController()
    val deckVm: DeckViewModel = viewModel()
    NavHost(nav, startDestination = Routes.DECK) {
        composable(Routes.DECK) {
            val ui by deckVm.ui.collectAsStateWithLifecycle()
            var message by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                deckVm.messages.collect { m -> message = m; delay(2600); if (message == m) message = null }
            }
            val ctx = LocalContext.current
            var askBattery by remember { mutableStateOf(false) }
            val deckActions = remember(deckVm) {
                object : DeckActions by deckVm {
                    override fun toggleListening() {
                        val turningOn = !deckVm.ui.value.listening
                        if (turningOn && !isIgnoringBattery(ctx) && !deckVm.settings.value.batteryAsked) askBattery = true
                        deckVm.toggleListening()
                    }
                }
            }
            if (askBattery) {
                AlertDialog(
                    onDismissRequest = { askBattery = false; deckVm.markBatteryAsked() },
                    title = { Text("Escuchar con la pantalla apagada") },
                    text = { Text("Para que la palabra clave funcione durante horas con la pantalla apagada, Android tiene que dejar a KB-007 sin optimización de batería. Si no, el sistema puede cortar la escucha (sobre todo en Xiaomi, Samsung y Huawei).\n\nConsumo estimado: 3–8 % de batería por hora de escucha.") },
                    confirmButton = { TextButton(onClick = { askBattery = false; deckVm.markBatteryAsked(); requestIgnoreBattery(ctx) }) { Text("Permitir") } },
                    dismissButton = { TextButton(onClick = { askBattery = false; deckVm.markBatteryAsked() }) { Text("Ahora no") } },
                )
            }
            DeckScreen(
                vm = deckActions, theme = theme, fx = fx, ui = ui, message = message,
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIBRARY) {
            val libVm: LibraryViewModel = viewModel()
            val ui by libVm.ui.collectAsStateWithLifecycle()
            var message by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                libVm.messages.collect { m -> message = m; delay(2600); if (message == m) message = null }
            }
            val actions = remember(libVm) {
                object : LibraryActions {
                    override fun back() { nav.popBackStack() }
                    override fun setQuery(q: String) = libVm.setQuery(q)
                    override fun setSort(s: SortBy) = libVm.setSort(s)
                    override fun load(r: Recording) {
                        // "Cargar" = el cassette entra al aparato y se vuelve a la botonera
                        deckVm.load(r); fx.press(DeckFx.Kind.EJECT); nav.popBackStack()
                    }
                    override fun rename(r: Recording, title: String) { libVm.rename(r, title) }
                    override fun setStyle(r: Recording, style: Int) { libVm.setStyle(r, style) }
                    override fun delete(r: Recording) { libVm.delete(r) }
                    override fun share(r: Recording, toWhatsApp: Boolean, convert: Boolean) { libVm.share(r, toWhatsApp, convert) }
                    override fun needsConversion(r: Recording) = libVm.needsConversion(r)
                    override fun whatsAppInstalled() = libVm.whatsAppInstalled()
                }
            }
            LibraryScreen(ui, theme, actions, message)
        }
        composable(Routes.SETTINGS) {
            val setVm: SettingsViewModel = viewModel()
            val s by setVm.settings.collectAsStateWithLifecycle()
            val caps by setVm.caps.collectAsStateWithLifecycle()
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) setVm.setCustomFolder(uri)
            }
            // Micrófono abierto solo para el medidor mientras la pantalla está visible.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) setVm.setMonitoring(true)
                    if (e == Lifecycle.Event.ON_PAUSE) setVm.setMonitoring(false)
                }
                lifecycle.addObserver(obs)
                onDispose { lifecycle.removeObserver(obs); setVm.setMonitoring(false) }
            }
            val actions = remember(setVm) {
                object : SettingsActions {
                    override fun back() { nav.popBackStack() }
                    override fun update(f: (AppSettings) -> AppSettings) = setVm.update(f)
                    override fun preset(p: AudioPreset) = setVm.applyPreset(p)
                    override fun pickFolder() = folderPicker.launch(null)
                    override fun levelDb() = setVm.level.value.rmsDb
                }
            }
            val vocab by setVm.vocab.collectAsStateWithLifecycle()
            val testing by setVm.testing.collectAsStateWithLifecycle()
            val ctx = LocalContext.current
            var batteryExempt by remember { mutableStateOf(isIgnoringBattery(ctx)) }
            LaunchedEffect(lifecycle) {
                lifecycle.currentStateFlow.collect { if (it == Lifecycle.State.RESUMED) batteryExempt = isIgnoringBattery(ctx) }
            }
            SettingsScreen(s, caps, theme, actions, setVm.appFolderPath()) {
                KeywordSection(s, theme, vocab, batteryExempt, update = setVm::update,
                    onTest = setVm::startKeywordTest, onBattery = { requestIgnoreBattery(ctx) })
            }
            if (testing) {
                val heard by setVm.heard.collectAsStateWithLifecycle()
                val ready by setVm.testReady.collectAsStateWithLifecycle()
                val err by setVm.testError.collectAsStateWithLifecycle()
                KeywordTestDialog(s.keyword, s.keywordThreshold, ready, heard, err, onClose = setVm::stopKeywordTest)
            }
        }
    }
}
