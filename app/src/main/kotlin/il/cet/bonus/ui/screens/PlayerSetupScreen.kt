package il.cet.bonus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.cet.bonus.R

/** Local 2-player name entry, all Hebrew per requirements. */
@Composable
fun PlayerSetupScreen(onStart: (String, String) -> Unit) {
    var nameA by remember { mutableStateOf("") }
    var nameB by remember { mutableStateOf("") }
    val defaultA = stringResource(R.string.player_a_default)
    val defaultB = stringResource(R.string.player_b_default)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.player_names_title))
            OutlinedTextField(
                value = nameA,
                onValueChange = { nameA = it },
                label = { Text(defaultA) },
                modifier = Modifier.width(240.dp).padding(top = 12.dp),
            )
            OutlinedTextField(
                value = nameB,
                onValueChange = { nameB = it },
                label = { Text(defaultB) },
                modifier = Modifier.width(240.dp).padding(top = 12.dp),
            )
            Button(
                onClick = { onStart(nameA.ifBlank { defaultA }, nameB.ifBlank { defaultB }) },
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.start_game))
            }
        }
    }
}
