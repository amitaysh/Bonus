package il.cet.bonus.bonus

import android.content.Context
import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import java.io.BufferedReader
import java.io.InputStreamReader

/** Loads the dedicated 3/4/5-letter word banks used to generate bonus mini-game puzzles. */
object BonusWordBankLoader {
    private val ASSET_BY_LENGTH = mapOf(
        3 to "dictionary/3Letters.txt",
        4 to "dictionary/4Letters.txt",
        5 to "dictionary/5Letters.txt",
    )

    private val SOFIT_LETTERS = setOf('ם', 'ן', 'ץ', 'ף', 'ך')

    fun create(context: Context): BonusPuzzleGenerator {
        val bank = ASSET_BY_LENGTH.mapValues { (_, asset) ->
            context.assets.open(asset).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        // The game has no sofit (final-form) letter tiles at all (see
                        // Letter.kt), so any word containing a final letter (ם/ן/ץ/ף/ך)
                        // can never actually be built or displayed with tile artwork -
                        // exclude those from the bonus-puzzle word banks so a bonus
                        // mini-game never picks an unrenderable/unanswerable word.
                        .filter { word -> word.none { it in SOFIT_LETTERS } }
                        .toList()
                }
            }
        }
        return BonusPuzzleGenerator(bank)
    }
}
