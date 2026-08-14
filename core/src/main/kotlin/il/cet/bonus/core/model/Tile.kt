package il.cet.bonus.core.model

/** A tile drawn from the bag: either a regular letter, or a joker standing in for a letter. */
sealed class Tile {
    abstract val score: Int
    abstract val displayLetter: Char

    data class LetterTile(val letter: Letter) : Tile() {
        override val score: Int get() = letter.score
        override val displayLetter: Char get() = letter.hebrew
    }

    /** A joker tile. Once placed, [chosenLetter] records which letter it represents. */
    data class JokerTile(val chosenLetter: Letter? = null) : Tile() {
        override val score: Int get() = Letter.JOKER_SCORE
        override val displayLetter: Char get() = chosenLetter?.hebrew ?: JOKER_GLYPH

        companion object {
            /** Placeholder glyph shown on an unassigned joker tile. */
            const val JOKER_GLYPH: Char = '*'
        }
    }
}
