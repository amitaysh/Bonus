package il.cet.bonus.core.bonus

import kotlin.random.Random

/**
 * All possible prizes a player can win when triggering a bonus slot, confirmed against
 * Hebrew Wikipedia "בונוס (משחק מחשב)" (see plan.md): the 5 interactive mini-games (see
 * [BonusType]), 4 flat point awards (1, 25, 40, 100), and 3 special "extra turn" /
 * score-multiplier prizes.
 *
 * There are more possible prizes (12 total) than bonus slots on the board can guarantee
 * triggering in a single game, and the same prize can never be awarded twice in one game -
 * see [drawBonusPrize].
 */
sealed class BonusPrize {
    /** One of the 5 interactive mini-games - see [BonusType]. */
    data class MiniGame(val type: BonusType) : BonusPrize()

    /** A flat point award with no mini-game (1, 25, 40, or 100 points). */
    data class Points(val amount: Int) : BonusPrize()

    /** The triggering player immediately plays another round (turn does not pass). */
    object ExtraTurn : BonusPrize()

    /** The triggering player's score is doubled (x2) for their next 2 rounds. */
    object DoubleScoreNextTwoRounds : BonusPrize()

    /** The triggering player immediately plays another round, and that round's score is x4. */
    object ExtraTurnQuadrupleScore : BonusPrize()
}

/** The full pool of distinct prizes that can ever be drawn in one game. Points values (1,
 * 25, 40, 100) and future/extra-turn prizes confirmed against Hebrew Wikipedia "בונוס
 * (משחק מחשב)" - see plan.md. */
val ALL_BONUS_PRIZES: List<BonusPrize> = BonusType.entries.map { BonusPrize.MiniGame(it) } +
    listOf(
        BonusPrize.Points(1),
        BonusPrize.Points(25),
        BonusPrize.Points(40),
        BonusPrize.Points(100),
        BonusPrize.ExtraTurn,
        BonusPrize.DoubleScoreNextTwoRounds,
        BonusPrize.ExtraTurnQuadrupleScore,
    )

/**
 * Draws a random prize from [ALL_BONUS_PRIZES], excluding any already in [alreadyUsed]
 * (confirmed rule: the same bonus can't be won twice in the same game). If every prize in
 * the pool has already been used, falls back to drawing from the full pool again rather
 * than crashing (should not happen in practice since a game has at most 12 bonus slots).
 */
fun drawBonusPrize(alreadyUsed: Set<BonusPrize>, random: Random = Random.Default): BonusPrize {
    val available = ALL_BONUS_PRIZES.filter { it !in alreadyUsed }
    val pool = available.ifEmpty { ALL_BONUS_PRIZES }
    return pool.random(random)
}
