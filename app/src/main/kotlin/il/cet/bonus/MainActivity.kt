package il.cet.bonus

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import il.cet.bonus.audio.MusicController
import il.cet.bonus.audio.MusicCatalog
import il.cet.bonus.dictionary.DictionaryLoader
import il.cet.bonus.game.GameViewModel
import il.cet.bonus.ui.screens.BoardScreen
import il.cet.bonus.ui.screens.MainMenuScreen
import il.cet.bonus.ui.screens.PlayerSetupScreen
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

private enum class Screen { MENU, PLAYER_SETUP, BOARD }

/**
 * Root composable and navigation flow for v1 scope: main menu -> player setup -> board.
 * Settings/hall-of-fame/save-resume are out of scope for v1 (see TODO.md).
 * The whole app is forced to RTL layout direction regardless of system locale, since the
 * app is Hebrew-only.
 */
@Composable
fun BonusApp() {
    val context = LocalContext.current
    val dictionary = remember { DictionaryLoader.create(context) }
    val themeManager = remember { ThemeManager(context) }
    val musicController = remember { MusicController(context) }
    var gameTheme by remember { mutableStateOf(themeManager.current) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        musicController.setTheme(if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW)
        musicController.start()
        onDispose { musicController.release() }
    }

    val viewModel: GameViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(dictionary) as T
        }
    })

    var screen by remember { mutableStateOf(Screen.MENU) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BonusTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.MENU -> MainMenuScreen(
                        currentTheme = gameTheme,
                        onToggleTheme = {
                            gameTheme = if (gameTheme == GameTheme.OLD) GameTheme.NEW else GameTheme.OLD
                            themeManager.current = gameTheme
                            musicController.setTheme(
                                if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW
                            )
                            musicController.start()
                        },
                        onNewGame = { screen = Screen.PLAYER_SETUP },
                    )
                    Screen.PLAYER_SETUP -> PlayerSetupScreen(onStart = { a, b ->
                        viewModel.startGame(a, b)
                        screen = Screen.BOARD
                    })
                    Screen.BOARD -> BoardScreen(viewModel = viewModel, onExit = { screen = Screen.MENU })
                }
            }
        }
    }
}
