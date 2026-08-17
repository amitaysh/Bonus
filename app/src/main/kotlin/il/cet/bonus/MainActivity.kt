package il.cet.bonus

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import il.cet.bonus.audio.MusicController
import il.cet.bonus.audio.MusicCatalog
import il.cet.bonus.dictionary.DictionaryLoader
import il.cet.bonus.game.GameViewModel
import il.cet.bonus.game.PlayerNamesStore
import il.cet.bonus.ui.screens.BoardScreen
import il.cet.bonus.ui.screens.MainMenuScreen
import il.cet.bonus.ui.screens.SettingsScreen
import il.cet.bonus.ui.theme.BonusTheme
import il.cet.bonus.ui.theme.GameTheme
import il.cet.bonus.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BonusApp()
        }
    }
}

private enum class Screen { MENU, SETTINGS, BOARD }

/**
 * Root composable and navigation flow: main menu -> (settings for player names, or
 * directly to) board. Player names are edited from the settings screen (reachable
 * only from the main menu) and persisted for every subsequent "New Game" - see
 * `PlayerNamesStore`. Hall-of-fame/save-resume are out of scope for v1 (see TODO.md).
 * The whole app is forced to RTL layout direction regardless of system locale, since the
 * app is Hebrew-only.
 */
@Composable
fun BonusApp() {
    val context = LocalContext.current
    val dictionary = remember { DictionaryLoader.create(context) }
    val themeManager = remember { ThemeManager(context) }
    val musicController = remember { MusicController(context) }
    val playerNamesStore = remember { PlayerNamesStore(context) }
    var gameTheme by remember { mutableStateOf(themeManager.current) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        musicController.setTheme(if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW)
        musicController.start()
        onDispose { musicController.release() }
    }

    // Pause/resume music when the app is backgrounded/foregrounded (rather than only
    // stopping on process death), so it doesn't keep playing while minimized.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> musicController.pause()
                Lifecycle.Event.ON_START -> musicController.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val viewModel: GameViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(context.applicationContext as Application, dictionary, musicController) as T
        }
    })

    var screen by remember { mutableStateOf(Screen.MENU) }
    val defaultNameA = androidx.compose.ui.res.stringResource(R.string.player_a_default)
    val defaultNameB = androidx.compose.ui.res.stringResource(R.string.player_b_default)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BonusTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.MENU -> MainMenuScreen(
                        onNewGame = {
                            viewModel.startGame(
                                playerNamesStore.nameA.ifBlank { defaultNameA },
                                playerNamesStore.nameB.ifBlank { defaultNameB },
                            )
                            screen = Screen.BOARD
                        },
                        onSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        store = playerNamesStore,
                        onBack = { screen = Screen.MENU },
                    )
                    Screen.BOARD -> BoardScreen(
                        viewModel = viewModel,
                        onExit = { screen = Screen.MENU },
                        musicTheme = gameTheme,
                        onToggleMusicTheme = {
                            gameTheme = if (gameTheme == GameTheme.OLD) GameTheme.NEW else GameTheme.OLD
                            themeManager.current = gameTheme
                            musicController.setTheme(
                                if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW
                            )
                            musicController.start()
                        },
                    )
                }
            }
        }
    }
}
