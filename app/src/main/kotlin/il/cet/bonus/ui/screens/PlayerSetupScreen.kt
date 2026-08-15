package il.cet.bonus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.cet.bonus.R
import il.cet.bonus.ui.board.BevelButton
import il.cet.bonus.ui.board.NoiseBackground

/** Local 2-player name entry, all Hebrew per requirements. */
@Composable
fun PlayerSetupScreen(onStart: (String, String) -> Unit) {
    var nameA by remember { mutableStateOf("") }
    var nameB by remember { mutableStateOf("") }
    val defaultA = stringResource(R.string.player_a_default)
    val defaultB = stringResource(R.string.player_b_default)

    NoiseBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.player_names_title))
            // Solid white background (instead of transparent default) so the text is
            // legible against the blue noise-texture background behind it.
            OutlinedTextField(
                value = nameA,
                onValueChange = { nameA = it },
                label = { Text(defaultA) },
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 12.dp)
                    .background(Color.White, RoundedCornerShape(4.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                ),
            )
            OutlinedTextField(
                value = nameB,
                onValueChange = { nameB = it },
                label = { Text(defaultB) },
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 12.dp)
                    .background(Color.White, RoundedCornerShape(4.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                ),
            )
            BevelButton(
                text = stringResource(R.string.start_game),
                onClick = { onStart(nameA.ifBlank { defaultA }, nameB.ifBlank { defaultB }) },
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
