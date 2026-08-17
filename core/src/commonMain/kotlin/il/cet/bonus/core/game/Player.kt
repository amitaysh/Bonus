package il.cet.bonus.core.game

data class Player(
    val id: Int,
    val name: String,
    val score: Int = 0,
    /** Multiplier applied to this player's next scored turn(s) - from a "double score" or
     * "quadruple score" bonus prize (see BonusPrize). 1 = no active multiplier. */
    val scoreMultiplier: Int = 1,
    /** How many more of this player's turns [scoreMultiplier] still applies to. */
    val multiplierRoundsRemaining: Int = 0,
) {
    fun withAddedScore(delta: Int): Player = copy(score = score + delta)

    /** Applies [scoreMultiplier] to [rawDelta] and, if a multiplier is active, ticks down
     * [multiplierRoundsRemaining], clearing the multiplier once it reaches zero. */
    fun withScoredTurn(rawDelta: Int): Player {
        val multiplied = rawDelta * scoreMultiplier
        val roundsLeft = (multiplierRoundsRemaining - 1).coerceAtLeast(0)
        return copy(
            score = score + multiplied,
            scoreMultiplier = if (roundsLeft == 0) 1 else scoreMultiplier,
            multiplierRoundsRemaining = roundsLeft,
        )
    }

    /** Grants a score multiplier for this player's next [rounds] turns. */
    fun withMultiplier(multiplier: Int, rounds: Int): Player =
        copy(scoreMultiplier = multiplier, multiplierRoundsRemaining = rounds)
}
