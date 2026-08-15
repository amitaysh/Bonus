package il.cet.bonus.ui.board

import il.cet.bonus.R
import il.cet.bonus.core.board.Board

/**
 * Visual identity for each of the 12 peripheral bonus slots (see [Board.BonusSlot]).
 * The original 1993 game marks each bonus slot with a distinct little icon (fruit / card
 * suit / gem) inside a yellow tile, floating just outside the main grid. These icons were
 * screenshotted directly from real gameplay footage (`full_grid.png`, a full-board capture)
 * and cropped into individual per-slot bitmaps - see the `bonus_*` drawables.
 *
 * Which exact icon sits on which specific slot IS gameplay-relevant now: the order below
 * matches the real board's layout exactly (see `full_grid.png`):
 * - TOP: heart, plum, grapes (green) at align indices 1, 3, 7
 * - LEFT: apple (red), diamond, apple (green) at align indices 1, 3, 7
 * - RIGHT: grapes (purple), cherry, spade at align indices 2, 6, 8
 * - BOTTOM: star, clover, pear at align indices 2, 6, 8
 */
enum class BonusIcon(val drawableRes: Int) {
    HEART(R.drawable.bonus_heart),
    PLUM(R.drawable.bonus_plum),
    GRAPES_GREEN(R.drawable.bonus_grapes_green),
    APPLE_RED(R.drawable.bonus_apple_red),
    DIAMOND(R.drawable.bonus_diamond),
    APPLE_GREEN(R.drawable.bonus_apple_green),
    GRAPES_PURPLE(R.drawable.bonus_grapes_purple),
    CHERRY(R.drawable.bonus_cherry),
    SPADE(R.drawable.bonus_spade),
    STAR(R.drawable.bonus_star),
    CLOVER(R.drawable.bonus_clover),
    PEAR(R.drawable.bonus_pear),
    ;
}

/** Fixed assignment of an icon to each of the board's 12 (edge, alignIndex) slots,
 * matching the real 1993 game's board layout exactly (confirmed from `full_grid.png`). */
object BonusIconAssignment {
    private val map: Map<Pair<Board.Edge, Int>, BonusIcon> = mapOf(
        (Board.Edge.TOP to 1) to BonusIcon.HEART,
        (Board.Edge.TOP to 3) to BonusIcon.PLUM,
        (Board.Edge.TOP to 7) to BonusIcon.GRAPES_GREEN,
        (Board.Edge.LEFT to 1) to BonusIcon.APPLE_RED,
        (Board.Edge.LEFT to 3) to BonusIcon.DIAMOND,
        (Board.Edge.LEFT to 7) to BonusIcon.APPLE_GREEN,
        (Board.Edge.RIGHT to 2) to BonusIcon.GRAPES_PURPLE,
        (Board.Edge.RIGHT to 6) to BonusIcon.CHERRY,
        (Board.Edge.RIGHT to 8) to BonusIcon.SPADE,
        (Board.Edge.BOTTOM to 2) to BonusIcon.STAR,
        (Board.Edge.BOTTOM to 6) to BonusIcon.CLOVER,
        (Board.Edge.BOTTOM to 8) to BonusIcon.PEAR,
    )

    fun iconFor(slot: Board.BonusSlot): BonusIcon =
        map.getValue(slot.edge to slot.alignIndex)
}
