package il.cet.bonus.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import il.cet.bonus.shared.ui.board.BevelButton
import il.cet.bonus.shared.ui.board.NoiseBackground
import il.cet.bonus.shared.ui.screens.MainMenuScreen

/**
 * Shared (commonMain) app entry point used by both the Android and iOS hosts.
 *
 * This currently renders the real, ported [MainMenuScreen] (same Compose UI code as
 * the Android app, using Compose Multiplatform resources) as proof that actual shared
 * UI - not just business logic - now runs identically on both platforms. Tapping
 * "New Game"/"Settings" shows a placeholder message for now: porting the full in-game
 * board (drag & drop tiles, bonus mini-games, Media3 audio, etc.) is a much larger,
 * separate follow-up effort tracked in plan.md.
 *
 * Matches the Android app's global RTL layout direction (see MainActivity.kt), which
 * is forced regardless of system locale since the whole app is Hebrew-only.
 */
@Composable
fun SharedApp() {
    var showPlaceholder by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            if (showPlaceholder) {
                PlaceholderScreen(onBack = { showPlaceholder = false })
            } else {
                MainMenuScreen(
                    onNewGame = { showPlaceholder = true },
                    onSettings = { showPlaceholder = true },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(onBack: () -> Unit) {
    NoiseBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Board screen not ported to iOS yet")
            BevelButton(
                text = "חזרה",
                onClick = onBack,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
