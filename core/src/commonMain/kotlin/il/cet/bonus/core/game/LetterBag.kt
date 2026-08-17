package il.cet.bonus.core.game

import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile
import kotlin.random.Random

/**
 * The draw bag: 80 letter tiles (corrected counts, see Letter.kt) + 2 jokers = 82 total,
 * matching the verified original bag composition.
 */
class LetterBag(random: Random = Random.Default) {
    private val random = random
    private val remaining: MutableList<Tile> = buildList {
        Letter.entries.forEach { letter ->
            repeat(letter.tileCount) { add(Tile.LetterTile(letter)) }
        }
        repeat(Letter.JOKER_COUNT) { add(Tile.JokerTile()) }
    }.toMutableList()

    val tilesLeft: Int get() = remaining.size

    fun isEmpty(): Boolean = remaining.isEmpty()

    /** Draws up to [count] random tiles, removing them from the bag. May return fewer if the bag runs low. */
    fun draw(count: Int): List<Tile> {
        val drawn = mutableListOf<Tile>()
        repeat(count) {
            if (remaining.isEmpty()) return@repeat
            val idx = random.nextInt(remaining.size)
            drawn.add(remaining.removeAt(idx))
        }
        return drawn
    }

    /** Returns tiles to the bag (e.g. after a letter-swap action). */
    fun returnTiles(tiles: List<Tile>) {
        remaining.addAll(tiles)
    }
}
