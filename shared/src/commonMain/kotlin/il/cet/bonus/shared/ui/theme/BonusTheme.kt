package il.cet.bonus.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App-wide theming. Distinct from the in-game "old"/"new" visual theme (which affects
 * board/tile skins and music - see `theme-system` todo); this is just the Material
 * shell theme for menus/dialogs.
 */
private val BonusLightColors = lightColorScheme(
    primary = Color(0xFFE63946),
    secondary = Color(0xFF1B4332),
)

private val BonusDarkColors = darkColorScheme(
    primary = Color(0xFFE63946),
    secondary = Color(0xFF52B788),
)

@Composable
fun BonusTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (useDarkTheme) BonusDarkColors else BonusLightColors
    MaterialTheme(colorScheme = colors, content = content)
}
