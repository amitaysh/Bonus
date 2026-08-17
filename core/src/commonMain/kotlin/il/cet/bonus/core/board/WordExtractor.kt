package il.cet.bonus.core.board

import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile

/**
 * Extracts contiguous horizontal ("row") and vertical ("column") words from the board.
 *
 * Ported from `ShabetzNa.GetXWords`/`GetYWords` (C# reference implementation): scans
 * cells sorted by row-then-column (for row-words) and column-then-row (for column-words),
 * splitting on gaps, and only keeping runs of length > 1 as words.
 */
object WordExtractor {

    data class PlacedWord(val word: String, val cells: List<Position>)

    /** Words read left-to-right along each row. */
    fun rowWords(occupied: Map<Position, Tile>): List<PlacedWord> =
        scanWords(occupied, primary = { it.row }, secondary = { it.col }, isRowScan = true)

    /** Words read top-to-bottom along each column. */
    fun columnWords(occupied: Map<Position, Tile>): List<PlacedWord> =
        scanWords(occupied, primary = { it.col }, secondary = { it.row }, isRowScan = false)

    private fun scanWords(
        occupied: Map<Position, Tile>,
        primary: (Position) -> Int,
        secondary: (Position) -> Int,
        isRowScan: Boolean,
    ): List<PlacedWord> {
        if (occupied.isEmpty()) return emptyList()

        val sorted = occupied.keys.sortedWith(compareBy(primary, secondary))
        val words = mutableListOf<PlacedWord>()
        var currentWord = StringBuilder()
        var currentCells = mutableListOf<Position>()
        var lastPrimary = primary(sorted.first())
        var lastSecondary = secondary(sorted.first()) - 1

        fun flush() {
            if (currentWord.length > 1) {
                words.add(PlacedWord(currentWord.toString(), currentCells.toList()))
            }
            currentWord = StringBuilder()
            currentCells = mutableListOf()
        }

        for (pos in sorted) {
            val p = primary(pos)
            val s = secondary(pos)
            val tile = occupied.getValue(pos)
            val contiguous = (p == lastPrimary) && (s == lastSecondary + 1)
            if (!contiguous) flush()
            currentWord.append(tile.displayLetter)
            currentCells.add(pos)
            lastPrimary = p
            lastSecondary = s
        }
        flush()
        return words
    }
}
