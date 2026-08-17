package il.cet.bonus.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small collection of "beveled gray plastic" widgets that reproduce the classic 1993
 * Bonus DOS UI chrome (confirmed pixel-for-pixel from `bonus.mp4` gameplay footage):
 * raised gray 3D buttons, and black LCD-style digit readouts (moves counter, timer,
 * scoreboard). Shared by both the OLD (classic) and NEW (modern) in-game themes so far,
 * since the user asked for a faithful recreation first and foremost.
 */
private val bevelLight = Color(0xFFE8E8E8)
private val bevelMid = Color(0xFFB8B8C0)
private val bevelDark = Color(0xFF6E6E78)

@Composable
fun BevelButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(if (enabled) 3.dp else 0.dp, RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(
                    if (enabled) listOf(bevelLight, bevelMid) else listOf(Color(0xFFCCCCCC), Color(0xFF999999)),
                ),
                RoundedCornerShape(4.dp),
            )
            .border(1.dp, bevelDark, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
            style = TextStyle(
                color = Color(0xFF1A1A2E),
                fontSize = if (text.length > 10) 10.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
        )
    }
}

/** Black LCD-style digit readout, e.g. moves counter / timer / score - matches the
 * yellow-green digital font used throughout the original game's HUD. */
@Composable
fun LcdDisplay(
    value: String,
    modifier: Modifier = Modifier,
    digitColor: Color = Color(0xFFCFFF04),
    label: String? = null,
) {
    label?.let {
        Text(
            text = it,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
    Box(
        modifier = modifier
            .background(Color(0xFFC9C9D2), RoundedCornerShape(4.dp))
            .border(1.dp, bevelDark, RoundedCornerShape(4.dp))
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF101010), RoundedCornerShape(2.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = TextStyle(
                    color = digitColor,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
