package il.cet.bonus.shared.ui.board

import bonus.shared.generated.resources.Res
import bonus.shared.generated.resources.ain
import bonus.shared.generated.resources.alef
import bonus.shared.generated.resources.bet
import bonus.shared.generated.resources.daled
import bonus.shared.generated.resources.gimel
import bonus.shared.generated.resources.het
import bonus.shared.generated.resources.hey
import bonus.shared.generated.resources.joker
import bonus.shared.generated.resources.kaf
import bonus.shared.generated.resources.kuf
import bonus.shared.generated.resources.lamed
import bonus.shared.generated.resources.mem
import bonus.shared.generated.resources.nun
import bonus.shared.generated.resources.pe
import bonus.shared.generated.resources.reish
import bonus.shared.generated.resources.sameh
import bonus.shared.generated.resources.shin
import bonus.shared.generated.resources.tav
import bonus.shared.generated.resources.tet
import bonus.shared.generated.resources.vav
import bonus.shared.generated.resources.yud
import bonus.shared.generated.resources.zadik
import bonus.shared.generated.resources.zain
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile
import org.jetbrains.compose.resources.DrawableResource

/**
 * Maps each [Letter] (and the unassigned joker) to its original 1993 tile artwork
 * (the Letters folder's jpg files from the C# reference project, migrated verbatim - each image already
 * has the letter glyph and its score baked in, matching the real game pixel-for-pixel).
 *
 * Shared (commonMain) port of the original Android-only `LetterTileArt`, using
 * Compose Multiplatform resources (`DrawableResource`/`Res.drawable`) instead of Android's
 * `R.drawable` integer ids, so this works identically on Android and iOS.
 */
object LetterTileArt {
    private val byLetter: Map<Letter, DrawableResource> = mapOf(
        Letter.ALEF to Res.drawable.alef,
        Letter.BET to Res.drawable.bet,
        Letter.GIMEL to Res.drawable.gimel,
        Letter.DALED to Res.drawable.daled,
        Letter.HEY to Res.drawable.hey,
        Letter.VAV to Res.drawable.vav,
        Letter.ZAIN to Res.drawable.zain,
        Letter.HET to Res.drawable.het,
        Letter.TET to Res.drawable.tet,
        Letter.YUD to Res.drawable.yud,
        Letter.KAF to Res.drawable.kaf,
        Letter.LAMED to Res.drawable.lamed,
        Letter.MEM to Res.drawable.mem,
        Letter.NUN to Res.drawable.nun,
        Letter.SAMEH to Res.drawable.sameh,
        Letter.AIN to Res.drawable.ain,
        Letter.PE to Res.drawable.pe,
        Letter.ZADIK to Res.drawable.zadik,
        Letter.KUF to Res.drawable.kuf,
        Letter.REISH to Res.drawable.reish,
        Letter.SHIN to Res.drawable.shin,
        Letter.TAV to Res.drawable.tav,
    )

    /** The unassigned-joker tile artwork (jester icon), shown before a joker's letter is chosen. */
    val jokerDrawable: DrawableResource = Res.drawable.joker

    /** Resolves the tile artwork to display for [tile]. A joker that has already been
     * assigned a letter shows that letter's artwork (still visually flagged as a joker
     * via a gold border by the caller); an unassigned joker shows [jokerDrawable]. */
    fun drawableFor(tile: Tile): DrawableResource = when (tile) {
        is Tile.LetterTile -> byLetter.getValue(tile.letter)
        is Tile.JokerTile -> tile.chosenLetter?.let { byLetter.getValue(it) } ?: jokerDrawable
    }

    fun isJoker(tile: Tile): Boolean = tile is Tile.JokerTile
}
