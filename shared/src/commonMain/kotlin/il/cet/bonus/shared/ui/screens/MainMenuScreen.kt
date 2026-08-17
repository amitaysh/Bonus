package il.cet.bonus.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bonus.shared.generated.resources.Res
import bonus.shared.generated.resources.app_name
import bonus.shared.generated.resources.menu_new_game
import bonus.shared.generated.resources.menu_settings
import il.cet.bonus.shared.ui.board.BevelButton
import il.cet.bonus.shared.ui.board.NoiseBackground
import org.jetbrains.compose.resources.stringResource

/**
 * Main menu screen (RTL, Hebrew): new game + settings (player names). Hall-of-fame /
 * save-resume are still out of scope for v1 (see TODO.md).
 *
 * Shared (commonMain) port of the original Android-only `MainMenuScreen`, using
 * Compose Multiplatform resources (`Res.string`) instead of Android's `R.string`/
 * `stringResource(Int)`, so this works identically on Android and iOS.
 */
@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
) {
    NoiseBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(Res.string.app_name), style = MaterialTheme.typography.displayMedium)
            BevelButton(
                text = stringResource(Res.string.menu_new_game),
                onClick = onNewGame,
                modifier = Modifier.padding(top = 24.dp).width(220.dp),
            )
            BevelButton(
                text = stringResource(Res.string.menu_settings),
                onClick = onSettings,
                modifier = Modifier.padding(top = 12.dp).width(220.dp),
            )
        }
    }
}
