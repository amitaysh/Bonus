package il.cet.bonus.shared.ui.board

import bonus.shared.generated.resources.Res
import bonus.shared.generated.resources.bonus_apple_green
import bonus.shared.generated.resources.bonus_apple_red
import bonus.shared.generated.resources.bonus_cherry
import bonus.shared.generated.resources.bonus_clover
import bonus.shared.generated.resources.bonus_diamond
import bonus.shared.generated.resources.bonus_grapes_green
import bonus.shared.generated.resources.bonus_grapes_purple
import bonus.shared.generated.resources.bonus_heart
import bonus.shared.generated.resources.bonus_pear
import bonus.shared.generated.resources.bonus_plum
import bonus.shared.generated.resources.bonus_spade
import bonus.shared.generated.resources.bonus_star
import il.cet.bonus.core.board.Board
import org.jetbrains.compose.resources.DrawableResource

enum class BonusIcon(val drawableRes: DrawableResource) {
    HEART(Res.drawable.bonus_heart),
    PLUM(Res.drawable.bonus_plum),
    GRAPES_GREEN(Res.drawable.bonus_grapes_green),
    APPLE_RED(Res.drawable.bonus_apple_red),
    DIAMOND(Res.drawable.bonus_diamond),
    APPLE_GREEN(Res.drawable.bonus_apple_green),
    GRAPES_PURPLE(Res.drawable.bonus_grapes_purple),
    CHERRY(Res.drawable.bonus_cherry),
    SPADE(Res.drawable.bonus_spade),
    STAR(Res.drawable.bonus_star),
    CLOVER(Res.drawable.bonus_clover),
    PEAR(Res.drawable.bonus_pear),
}

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

    fun iconFor(slot: Board.BonusSlot): BonusIcon = map.getValue(slot.edge to slot.alignIndex)
}
