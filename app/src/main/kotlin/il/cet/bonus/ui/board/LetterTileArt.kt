package il.cet.bonus.ui.board

import il.cet.bonus.R
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile

/**
 * Maps each [Letter] (and the unassigned joker) to its original 1993 tile artwork
 * (the Letters folder's jpg files from the C# reference project, migrated verbatim - each image already
 * has the letter glyph and its score baked in, matching the real game pixel-for-pixel).
 */
object LetterTileArt {
    private val byLetter: Map<Letter, Int> = mapOf(
        Letter.ALEF to R.drawable.alef,
        Letter.BET to R.drawable.bet,
        Letter.GIMEL to R.drawable.gimel,
        Letter.DALED to R.drawable.daled,
        Letter.HEY to R.drawable.hey,
        Letter.VAV to R.drawable.vav,
        Letter.ZAIN to R.drawable.zain,
        Letter.HET to R.drawable.het,
        Letter.TET to R.drawable.tet,
        Letter.YUD to R.drawable.yud,
        Letter.KAF to R.drawable.kaf,
        Letter.LAMED to R.drawable.lamed,
        Letter.MEM to R.drawable.mem,
        Letter.NUN to R.drawable.nun,
        Letter.SAMEH to R.drawable.sameh,
        Letter.AIN to R.drawable.ain,
        Letter.PE to R.drawable.pe,
        Letter.ZADIK to R.drawable.zadik,
        Letter.KUF to R.drawable.kuf,
        Letter.REISH to R.drawable.reish,
        Letter.SHIN to R.drawable.shin,
        Letter.TAV to R.drawable.tav,
    )

    /** The unassigned-joker tile artwork (jester icon), shown before a joker's letter is chosen. */
    val jokerDrawable: Int = R.drawable.joker

    /** Resolves the tile artwork to display for [tile]. A joker that has already been
     * assigned a letter shows that letter's artwork (still visually flagged as a joker
     * via a gold border by the caller); an unassigned joker shows [jokerDrawable]. */
    fun drawableFor(tile: Tile): Int = when (tile) {
        is Tile.LetterTile -> byLetter.getValue(tile.letter)
        is Tile.JokerTile -> tile.chosenLetter?.let { byLetter.getValue(it) } ?: jokerDrawable
    }

    fun isJoker(tile: Tile): Boolean = tile is Tile.JokerTile
}
