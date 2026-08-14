package il.cet.bonus.core.model

/** Zero-based board coordinate. */
data class Position(val row: Int, val col: Int)

/**
 * Static square type painted on the board. Bonus squares award a mini-game "prize"
 * when a player places the first or last letter of a newly formed word on them
 * (confirmed from BON.EXE game text). All other squares are [NORMAL].
 *
 * NOTE: the exact original board dimensions/bonus-square layout were not recoverable
 * from static analysis of BON.EXE alone (likely stored in the .MZP graphics or drawn
 * procedurally) - board size and bonus square positions here are proxied from the
 * user's own C# ShabetzNa reference implementation until verified otherwise
 * (see plan.md "Verified original-game data").
 */
enum class SquareType {
    NORMAL,
    BONUS,
}

/**
 * A lock placed by a player on a square. Confirmed original mechanic (not present in
 * the C# proxy): any player may place a lock on any empty square on their turn. A
 * locked square blocks all other letters from being placed on it until the lock
 * "explodes" and disappears after [movesRemaining] reaches 0.
 */
data class Lock(val totalMoves: Int, val movesRemaining: Int) {
    init {
        require(totalMoves == 3 || totalMoves == 5) {
            "Locks explode after 3 or 5 moves in the original game, got $totalMoves"
        }
    }

    fun tick(): Lock? {
        val remaining = movesRemaining - 1
        return if (remaining <= 0) null else copy(movesRemaining = remaining)
    }
}

/** A single square on the board: its static type, an optional placed tile, and an optional lock. */
data class Square(
    val type: SquareType = SquareType.NORMAL,
    val tile: Tile? = null,
    val lock: Lock? = null,
) {
    val isOccupied: Boolean get() = tile != null
    val isLocked: Boolean get() = lock != null
    /** A letter may be placed here only if it's empty and not locked. */
    val isPlaceable: Boolean get() = !isOccupied && !isLocked
}
