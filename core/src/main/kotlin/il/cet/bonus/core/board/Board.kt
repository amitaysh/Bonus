package il.cet.bonus.core.board

import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.SquareType
import il.cet.bonus.core.model.Tile

/**
 * The main NxN play grid, plus the peripheral "bonus slots" that surround it.
 *
 * CONFIRMED against real 1993 gameplay footage (`bonus.mp4`, pixel-measured grid lines,
 * see plan.md "Confirmed via real gameplay video"): the board is **10x10**, and the 3
 * peripheral bonus slots per edge align with columns/rows **1, 3, 7** (0-indexed on the
 * 10-wide/10-tall grid) - exactly matching the layout below. Bonus squares are NOT part
 * of the main 10x10 grid, but they ARE genuine, independently-placeable board squares in
 * their own right (a player can place a letter directly on a bonus square, and that
 * letter participates in word-building exactly like any other square) - they are simply
 * positioned one cell beyond the corresponding edge. Modeled as virtual [Position]s just
 * outside the grid bounds (row -1 / row `size` / col -1 / col `size`) so the existing
 * row/column word-scanning logic (which just sorts by row/col) naturally treats a bonus
 * square as an extension of the row or column it's aligned to.
 */
class Board(val size: Int = DEFAULT_SIZE) {

    enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    data class BonusSlot(val edge: Edge, val alignIndex: Int)

    private val grid: Array<Array<Tile?>> = Array(size) { arrayOfNulls(size) }
    private val locks: MutableMap<Position, il.cet.bonus.core.model.Lock> = mutableMapOf()

    /** Peripheral bonus slots surrounding the board - see class doc: each is its own square. */
    val bonusSlots: List<BonusSlot> = Edge.entries.flatMap { edge ->
        BONUS_ALIGN_INDICES.map { idx -> BonusSlot(edge, idx) }
    }

    private val usedBonusSlots: MutableSet<BonusSlot> = mutableSetOf()

    /** Tiles placed directly on bonus squares (kept separate from the main grid array since
     * bonus squares live outside the main grid's row/col index range). */
    private val bonusTiles: MutableMap<Position, Tile> = mutableMapOf()

    /**
     * The virtual board position of a bonus slot's own square - one cell beyond the
     * matching edge, aligned with [BonusSlot.alignIndex]. Placing the first or last
     * letter of a new word here is what triggers that bonus (per confirmed rule: "a
     * prize triggers when a player places the first or last letter of a word on a bonus
     * square"). This is a real placeable square, not a proxy for the nearest interior cell.
     */
    fun positionFor(slot: BonusSlot): Position = when (slot.edge) {
        Edge.TOP -> Position(-1, slot.alignIndex)
        Edge.BOTTOM -> Position(size, slot.alignIndex)
        Edge.LEFT -> Position(slot.alignIndex, -1)
        Edge.RIGHT -> Position(slot.alignIndex, size)
    }

    /** Reverse lookup: is [pos] the square for some (unused) bonus slot? */
    fun bonusSlotAt(pos: Position): BonusSlot? =
        bonusSlots.firstOrNull { positionFor(it) == pos }

    fun tileAt(pos: Position): Tile? =
        if (isInBounds(pos)) grid.getOrNull(pos.row)?.getOrNull(pos.col) else bonusTiles[pos]

    fun isInBounds(pos: Position): Boolean = pos.row in 0 until size && pos.col in 0 until size

    fun isLocked(pos: Position): Boolean = locks.containsKey(pos)

    fun isPlaceable(pos: Position): Boolean =
        (isInBounds(pos) || bonusSlotAt(pos) != null) && tileAt(pos) == null && !isLocked(pos)

    fun placeTile(pos: Position, tile: Tile) {
        require(isPlaceable(pos)) { "Square $pos is not placeable (occupied or locked)" }
        if (isInBounds(pos)) {
            grid[pos.row][pos.col] = tile
        } else {
            bonusTiles[pos] = tile
        }
    }

    fun removeTile(pos: Position) {
        if (isInBounds(pos)) {
            grid[pos.row][pos.col] = null
        } else {
            bonusTiles.remove(pos)
        }
    }

    /** All bonus-square positions that currently hold a tile, with their tile. */
    fun occupiedBonusTiles(): Map<Position, Tile> = bonusTiles.toMap()

    /** Places a lock at [pos]. Lock explodes automatically after [totalMoves] turn-advances. */
    fun placeLock(pos: Position, totalMoves: Int) {
        require(totalMoves == 3 || totalMoves == 5) { "Locks explode after 3 or 5 moves" }
        require(tileAt(pos) == null && !isLocked(pos)) { "Cannot lock an occupied or already-locked square" }
        locks[pos] = il.cet.bonus.core.model.Lock(totalMoves, totalMoves)
    }

    /** Advances all locks by one move, removing any that have exploded. Call once per completed turn. */
    fun tickLocks() {
        val toRemove = mutableListOf<Position>()
        val toUpdate = mutableMapOf<Position, il.cet.bonus.core.model.Lock>()
        for ((pos, lock) in locks) {
            val next = lock.tick()
            if (next == null) toRemove.add(pos) else toUpdate[pos] = next
        }
        toRemove.forEach { locks.remove(it) }
        toUpdate.forEach { (pos, lock) -> locks[pos] = lock }
    }

    /**
     * Marks a bonus slot as triggered/used (each bonus slot can only trigger once per game,
     * matching the C# `_bonusBar` used-flag behavior).
     */
    fun markBonusSlotUsed(slot: BonusSlot) {
        usedBonusSlots.add(slot)
    }

    fun isBonusSlotUsed(slot: BonusSlot): Boolean = slot in usedBonusSlots

    companion object {
        const val DEFAULT_SIZE = 10
        val BONUS_ALIGN_INDICES = listOf(1, 3, 7)
    }
}
