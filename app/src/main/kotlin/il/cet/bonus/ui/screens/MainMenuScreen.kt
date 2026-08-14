package il.cet.bonus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.cet.bonus.R
import il.cet.bonus.ui.theme.GameTheme

/**
 * Placeholder main menu screen (RTL, Hebrew) - full navigation to player-setup / game
 * board / settings / hall-of-fame is future work (see `player-setup-flow`, `board-ui`).
 */
@Composable
fun MainMenuScreen(
    currentTheme: GameTheme,
    onToggleTheme: () -> Unit,
    onNewGame: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            Button(onClick = onNewGame, modifier = Modifier.padding(top = 24.dp).width(220.dp)) {
                Text(stringResource(R.string.menu_new_game))
            }
            OutlinedButton(onClick = onToggleTheme, modifier = Modifier.padding(top = 12.dp).width(220.dp)) {
                Text(if (currentTheme == GameTheme.OLD) "עיצוב: ישן (החלף לחדש)" else "עיצוב: חדש (החלף לישן)")
            }
        }
    }
}

