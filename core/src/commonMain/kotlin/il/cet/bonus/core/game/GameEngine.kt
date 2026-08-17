package il.cet.bonus.core.game

import il.cet.bonus.core.board.Board
import il.cet.bonus.core.board.WordExtractor
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile

/** Reasons a proposed move can be rejected, ported from `ValidateNewWordsStructure` in ShabetzNa.cs. */
sealed class MoveError {
    object NotAligned : MoveError()
    object NeedsAtLeastTwoLettersFirstMove : MoveError()
    object NotAttachedToExistingWords : MoveError()
    object GapsInLine : MoveError()
    object NoBonusOnFirstMove : MoveError()
    object OccupiedOrLockedSquare : MoveError()
    data class InvalidWords(val words: List<String>) : MoveError()
}

data class TurnResult(
    val newWords: List<WordExtractor.PlacedWord>,
    val scoreDelta: Int,
    val triggeredBonusSlots: List<Board.BonusSlot>,
)

/**
 * Orchestrates a single turn: validates a proposed set of tile placements per the
 * confirmed original rules (ported from ShabetzNa.cs's ValidateNewWordsStructure /
 * HandleScore, adjusted to drop the unconfirmed C# score-multiplier/extra-turn bonuses),
 * scores it, and reports any newly-triggered bonus slots (for the caller to launch the
 * corresponding mini-game) and lock bookkeeping.
 *
 * This class only implements the "place letters and score" flow. The lock-placement
 * action and the challenge/appeal flow are separate, alternative turn actions modeled
 * in `LockAction` and `ChallengeSystem` respectively.
 */
class GameEngine(
    val board: Board,
    private val dictionary: DictionaryRepository,
) {
    private var hasAnyWordBeenPlaced = false

    fun proposeMove(newTiles: Map<Position, Tile>): Result<TurnResult> = proposeMove(newTiles, bypassDictionary = false)

    /**
     * Commits [newTiles] as if the player successfully appealed (עירעור) a dictionary
     * rejection - every structural rule (alignment, gaps, attachment, no-bonus-on-first-move)
     * still applies, but the dictionary word-validity check is skipped so the disputed
     * word(s) are accepted anyway. Used by the UI's "appeal" action on the rejection popup.
     */
    fun forceMove(newTiles: Map<Position, Tile>): Result<TurnResult> = proposeMove(newTiles, bypassDictionary = true)

    private fun proposeMove(newTiles: Map<Position, Tile>, bypassDictionary: Boolean): Result<TurnResult> {
        if (newTiles.isEmpty()) return Result.failure(IllegalArgumentException("No tiles placed"))

        for (pos in newTiles.keys) {
            if (!board.isPlaceable(pos)) return moveError(MoveError.OccupiedOrLockedSquare)
        }

        val rows = newTiles.keys.map { it.row }.distinct()
        val cols = newTiles.keys.map { it.col }.distinct()
        val singleRow = rows.size == 1
        val singleCol = cols.size == 1
        if (newTiles.size > 1 && !singleRow && !singleCol) {
            return moveError(MoveError.NotAligned)
        }

        val isFirstMove = !hasAnyWordBeenPlaced
        if (isFirstMove) {
            if (newTiles.size < 2) return moveError(MoveError.NeedsAtLeastTwoLettersFirstMove)
            // Confirmed rule (user correction, no reverse-engineered "center" rule exists in
            // classic Bonus): the first word simply needs to land on the grid; it just must
            // not use any bonus tile - bonus tiles only become usable from the second word on.
            if (newTiles.keys.any { board.bonusSlotAt(it) != null }) {
                return moveError(MoveError.NoBonusOnFirstMove)
            }
        }

        // Build the "occupied after this move" view without mutating the board yet.
        val existingOccupied = occupiedTiles()
        val occupiedAfter = existingOccupied + newTiles

        // Gap check: along the line spanned by the new tiles, every cell between min and
        // max index (inclusive) must be occupied (new or pre-existing) - ported from VerifySpaces.
        if (newTiles.size > 1) {
            val line = if (singleRow) {
                val row = rows.single()
                val minCol = newTiles.keys.minOf { it.col }
                val maxCol = newTiles.keys.maxOf { it.col }
                (minCol..maxCol).map { Position(row, it) }
            } else {
                val col = cols.single()
                val minRow = newTiles.keys.minOf { it.row }
                val maxRow = newTiles.keys.maxOf { it.row }
                (minRow..maxRow).map { Position(it, col) }
            }
            if (line.any { it !in occupiedAfter }) return moveError(MoveError.GapsInLine)
        }

        // Attachment check: every subsequent move must touch at least one pre-existing tile
        // (orthogonally adjacent to a new tile, or a new tile fills a gap between old tiles).
        if (!isFirstMove) {
            val touchesExisting = newTiles.keys.any { pos ->
                neighbours(pos).any { it in existingOccupied }
            }
            if (!touchesExisting) return moveError(MoveError.NotAttachedToExistingWords)
        }

        val rowWords = WordExtractor.rowWords(occupiedAfter)
        val colWords = WordExtractor.columnWords(occupiedAfter)
        val allWords = rowWords + colWords
        val newWords = allWords.filter { placed -> placed.cells.any { it in newTiles.keys } }

        val invalid = newWords.map { it.word }.filter { !dictionary.isValidWord(it) }
        if (invalid.isNotEmpty() && !bypassDictionary) return moveError(MoveError.InvalidWords(invalid))

        // Commit.
        newTiles.forEach { (pos, tile) -> board.placeTile(pos, tile) }
        hasAnyWordBeenPlaced = true
        board.tickLocks()

        val scoreDelta = newWords.sumOf { placed -> placed.cells.sumOf { occupiedAfter.getValue(it).score } }

        val triggered = newTiles.keys.mapNotNull { pos -> board.bonusSlotAt(pos) }
            .filter { !board.isBonusSlotUsed(it) }
            .filter { slot -> newWords.any { w -> w.cells.firstOrNull() == board.positionFor(slot) || w.cells.lastOrNull() == board.positionFor(slot) } }
        triggered.forEach { board.markBonusSlotUsed(it) }

        return Result.success(TurnResult(newWords, scoreDelta, triggered))
    }

    private fun neighbours(pos: Position): List<Position> = listOf(
        Position(pos.row - 1, pos.col),
        Position(pos.row + 1, pos.col),
        Position(pos.row, pos.col - 1),
        Position(pos.row, pos.col + 1),
    )

    private fun occupiedTiles(): Map<Position, Tile> {
        val map = mutableMapOf<Position, Tile>()
        for (r in 0 until board.size) {
            for (c in 0 until board.size) {
                val pos = Position(r, c)
                board.tileAt(pos)?.let { map[pos] = it }
            }
        }
        // Bonus squares are genuine placeable squares too (see Board doc) - include any
        // tiles already committed there so words extending onto/through them are scored.
        map.putAll(board.occupiedBonusTiles())
        return map
    }

    private fun moveError(error: MoveError): Result<TurnResult> = Result.failure(MoveException(error))
}

class MoveException(val error: MoveError) : Exception(error.toString())
