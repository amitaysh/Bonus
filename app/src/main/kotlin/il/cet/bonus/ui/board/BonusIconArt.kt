package il.cet.bonus.ui.board

import androidx.compose.ui.graphics.Color
import il.cet.bonus.core.board.Board

/**
 * Visual identity for each of the 12 peripheral bonus slots (see [Board.BonusSlot]).
 * The original 1993 game marks each bonus slot with a distinct little icon (fruit / card
 * suit / gem) inside a yellow tile, floating just outside the main grid - confirmed from
 * `bonus.mp4` gameplay footage. We don't have the original bitmap art for most of these
 * (only `star.jpg` was recovered), so the rest are rendered as Unicode glyphs on a
 * matching yellow beveled tile - visually equivalent, not a reproduction of any specific
 * copyrighted bitmap.
 *
 * Which exact icon sits on which specific slot is not a gameplay-relevant detail (any
 * bonus slot behaves identically); assignment below is fixed only for a consistent look.
 */
enum class BonusIcon(val glyph: String, val tint: Color) {
    HEART("\u2764", Color(0xFFE63946)),
    GRAPES_GREEN("\uD83C\uDF47", Color(0xFF6A994E)),
    GRAPES_PURPLE("\uD83C\uDF47", Color(0xFF6A4C93)),
    APPLE_RED("\uD83C\uDF4E", Color(0xFFD62828)),
    APPLE_GREEN("\uD83C\uDF4F", Color(0xFF52B788)),
    DIAMOND("\u2666", Color(0xFF457B9D)),
    PLUM("\u25CF", Color(0xFF6A4C93)),
    STAR("", Color(0xFFFFC300)), // rendered from the real bonus_star.jpg bitmap instead of a glyph
    CLOVER("\u2663", Color(0xFF2D6A4F)),
    PEAR("\uD83C\uDF50", Color(0xFF80B918)),
    SPADE("\u2660", Color(0xFF1D3557)),
    BELL("\uD83D\uDD14", Color(0xFFE9C46A)),
    ;
}

/** Fixed, stable assignment of an icon to each of the board's 12 (edge, alignIndex) slots. */
object BonusIconAssignment {
    private val order = listOf(
        Board.Edge.TOP to 1, Board.Edge.TOP to 3, Board.Edge.TOP to 7,
        Board.Edge.RIGHT to 1, Board.Edge.RIGHT to 3, Board.Edge.RIGHT to 7,
        Board.Edge.BOTTOM to 7, Board.Edge.BOTTOM to 3, Board.Edge.BOTTOM to 1,
        Board.Edge.LEFT to 7, Board.Edge.LEFT to 3, Board.Edge.LEFT to 1,
    )
    private val icons = BonusIcon.entries

    private val map: Map<Pair<Board.Edge, Int>, BonusIcon> = order.zip(icons).toMap()

    fun iconFor(slot: Board.BonusSlot): BonusIcon =
        map.getValue(slot.edge to slot.alignIndex)
}
