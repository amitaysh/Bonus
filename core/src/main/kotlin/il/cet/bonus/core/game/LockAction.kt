package il.cet.bonus.core.game

import il.cet.bonus.core.board.Board
import il.cet.bonus.core.model.Position

/**
 * The "lock" (מנעול) action: instead of placing letters, a player may spend their turn
 * placing a lock on any empty, unlocked square. Confirmed original mechanic, entirely
 * absent from the C# proxy. The lock explodes automatically after 3 or 5 moves (chosen
 * when placed) - see `Board.tickLocks` / `Lock.tick`, invoked once per completed turn.
 */
object LockAction {
    sealed class LockError {
        object SquareNotEmpty : LockError()
        object InvalidDuration : LockError()
    }

    fun placeLock(board: Board, pos: Position, totalMoves: Int): Result<Unit> {
        if (totalMoves != 3 && totalMoves != 5) {
            return Result.failure(IllegalArgumentException("Locks explode after 3 or 5 moves"))
        }
        if (!board.isPlaceable(pos)) {
            return Result.failure(IllegalStateException("Square is occupied or already locked"))
        }
        board.placeLock(pos, totalMoves)
        return Result.success(Unit)
    }
}
