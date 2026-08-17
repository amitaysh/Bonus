package il.cet.bonus.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import bonus.shared.generated.resources.Res
import bonus.shared.generated.resources.player_a_default
import bonus.shared.generated.resources.player_b_default
import il.cet.bonus.shared.audio.MusicCatalog
import il.cet.bonus.shared.audio.rememberMusicController
import il.cet.bonus.shared.audio.rememberSfxPlayer
import il.cet.bonus.shared.dictionary.DictionaryLoader
import il.cet.bonus.shared.game.GameViewModel
import il.cet.bonus.shared.game.PlayerNamesStore
import il.cet.bonus.shared.ui.screens.BoardScreen
import il.cet.bonus.shared.ui.screens.MainMenuScreen
import il.cet.bonus.shared.ui.screens.SettingsScreen
import il.cet.bonus.shared.ui.theme.BonusTheme
import il.cet.bonus.shared.ui.theme.GameTheme
import il.cet.bonus.shared.ui.theme.ThemeManager
import org.jetbrains.compose.resources.stringResource

private enum class Screen { MENU, SETTINGS, BOARD }

@Composable
fun SharedApp(initialScreenOverride: String? = null) {
    val dictionary = remember { DictionaryLoader.create() }
    val themeManager = remember { ThemeManager() }
    val musicController = rememberMusicController()
    val sfxPlayer = rememberSfxPlayer()
    val playerNamesStore = remember { PlayerNamesStore() }
    var gameTheme by remember { mutableStateOf(themeManager.current) }
    val viewModel = remember { GameViewModel(dictionary, musicController, sfxPlayer) }
    val defaultNameA = stringResource(Res.string.player_a_default)
    val defaultNameB = stringResource(Res.string.player_b_default)
    var screen by remember {
        mutableStateOf(
            when (initialScreenOverride?.lowercase()) {
                "settings" -> Screen.SETTINGS
                "board" -> {
                    viewModel.startGame(playerNamesStore.nameA.ifBlank { defaultNameA }, playerNamesStore.nameB.ifBlank { defaultNameB })
                    Screen.BOARD
                }
                else -> Screen.MENU
            },
        )
    }

    DisposableEffect(Unit) { onDispose { musicController.release() } }
    DisposableEffect(gameTheme) {
        musicController.setTheme(if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW)
        musicController.start()
        onDispose { }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BonusTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.MENU -> MainMenuScreen(
                        onNewGame = {
                            viewModel.startGame(playerNamesStore.nameA.ifBlank { defaultNameA }, playerNamesStore.nameB.ifBlank { defaultNameB })
                            screen = Screen.BOARD
                        },
                        onSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> SettingsScreen(store = playerNamesStore, onBack = { screen = Screen.MENU })
                    Screen.BOARD -> BoardScreen(
                        viewModel = viewModel,
                        onExit = { screen = Screen.MENU },
                        musicTheme = gameTheme,
                        onToggleMusicTheme = {
                            gameTheme = if (gameTheme == GameTheme.OLD) GameTheme.NEW else GameTheme.OLD
                            themeManager.current = gameTheme
                            musicController.setTheme(if (gameTheme == GameTheme.OLD) MusicCatalog.Theme.OLD else MusicCatalog.Theme.NEW)
                            musicController.start()
                        },
                    )
                }
            }
        }
    }
}
