package il.cet.bonus.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import il.cet.bonus.core.board.Board
import il.cet.bonus.core.game.LetterBag

/**
 * Minimal proof-of-concept Compose Multiplatform screen shared between Android and iOS.
 *
 * This intentionally does NOT re-implement the full game UI yet (that lives in the
 * existing Android-only `app` module and is a much larger follow-up port). Its purpose
 * is to prove the end-to-end KMP + Compose Multiplatform + Xcode toolchain actually
 * works - including calling real `core` game logic from shared UI code - before
 * investing in porting every screen.
 */
@Composable
fun SharedApp() {
    val board = Board()
    val bag = LetterBag()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Bonus - iOS Proof of Concept", style = MaterialTheme.typography.headlineSmall)
                Text("Board size: ${board.size} x ${board.size}")
                Text("Letters remaining in bag: ${bag.tilesLeft}")
                Text("Shared core module running natively on this platform ✅")
            }
        }
    }
}
