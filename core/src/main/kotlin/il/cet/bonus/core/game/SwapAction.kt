package il.cet.bonus.core.game

/**
 * Letter-swap ("החלפה") action, confirmed original rule: costs 25 points and requires
 * the player to already have at least 25 points to use it. Swapping returns the chosen
 * tiles to the bag and draws the same number of new random tiles.
 */
object SwapAction {
    const val COST: Int = 25

    fun canSwap(playerScore: Int): Boolean = playerScore >= COST

    /** Returns [tilesToSwap] to the bag and draws the same count of replacements. */
    fun swap(bag: LetterBag, tilesToSwap: List<il.cet.bonus.core.model.Tile>): List<il.cet.bonus.core.model.Tile> {
        bag.returnTiles(tilesToSwap)
        return bag.draw(tilesToSwap.size)
    }
}
