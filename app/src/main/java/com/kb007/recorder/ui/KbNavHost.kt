package com.kb007.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kb007.recorder.ui.deck.DeckScreen
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
            DeckScreen(
                vm = deckVm, theme = theme, fx = fx, ui = ui, message = message,
                onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIBRARY) { Placeholder("Lista de grabaciones (fase 3)") }
        composable(Routes.SETTINGS) { Placeholder("Ajustes (fase 4)") }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFF1B1A18)), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFFF3E9D2))
    }
}
