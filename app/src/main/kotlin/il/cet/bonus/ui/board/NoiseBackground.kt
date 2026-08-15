package il.cet.bonus.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import il.cet.bonus.R

/**
 * The blue-noise textured backdrop used by the main board screen, applied consistently
 * to every screen in the app (main menu, player setup, bonus mini-games) so the visual
 * style matches throughout instead of only appearing on the board.
 */
@Composable
fun NoiseBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}
