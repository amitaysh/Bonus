package il.cet.bonus.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.cet.bonus.R
import il.cet.bonus.ui.board.BevelButton
import il.cet.bonus.ui.board.NoiseBackground

/**
 * Main menu screen (RTL, Hebrew): new game + settings (player names). Hall-of-fame /
 * save-resume are still out of scope for v1 (see TODO.md).
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
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            BevelButton(
                text = stringResource(R.string.menu_new_game),
                onClick = onNewGame,
                modifier = Modifier.padding(top = 24.dp).width(220.dp),
            )
            BevelButton(
                text = stringResource(R.string.menu_settings),
                onClick = onSettings,
                modifier = Modifier.padding(top = 12.dp).width(220.dp),
            )
        }
    }
}

