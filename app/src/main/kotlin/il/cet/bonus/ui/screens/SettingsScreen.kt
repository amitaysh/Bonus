package il.cet.bonus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import il.cet.bonus.R
import il.cet.bonus.game.PlayerNamesStore
import il.cet.bonus.ui.board.BevelButton
import il.cet.bonus.ui.board.NoiseBackground

/**
 * Settings screen, reachable only from the main menu (not shown per-game). Lets the
 * user edit and persist the two player names used for every subsequent "New Game".
 * Names are single-line (Enter moves focus to the next field / dismisses the
 * keyboard) rather than allowing multi-line input.
 */
@Composable
fun SettingsScreen(store: PlayerNamesStore, onBack: () -> Unit) {
    var nameA by remember { mutableStateOf(store.nameA) }
    var nameB by remember { mutableStateOf(store.nameB) }
    val defaultA = stringResource(R.string.player_a_default)
    val defaultB = stringResource(R.string.player_b_default)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequesterB = remember { FocusRequester() }

    fun save() {
        store.nameA = nameA.trim()
        store.nameB = nameB.trim()
    }

    NoiseBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.menu_settings))
            Text(stringResource(R.string.player_names_title), modifier = Modifier.padding(top = 16.dp))
            // Solid white background (instead of transparent default) so the text is
            // legible against the blue noise-texture background behind it.
            OutlinedTextField(
                value = nameA,
                onValueChange = { nameA = it },
                label = { Text(defaultA) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusRequesterB.requestFocus() }),
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 12.dp)
                    .focusRequester(focusRequesterB)
                    .background(Color.White, RoundedCornerShape(4.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                ),
            )
            BevelButton(
                text = stringResource(R.string.menu_back),
                onClick = {
                    save()
                    onBack()
                },
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
