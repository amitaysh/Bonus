package il.cet.bonus.dictionary

import android.content.Context
import il.cet.bonus.core.dictionary.DictionaryRepository
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Builds the app's [DictionaryRepository] from the bundled Hebrew word list assets.
 * Per user decision, `HebrewWords.txt` (plus the 3/4/5-letter lists) are used completely
 * as-is - no cleanup, dedupe, or sofit-letter "correction" - see DictionaryRepository's
 * class doc for why that's correct rather than a bug.
 */
object DictionaryLoader {
    private val ASSET_FILES = listOf(
        "dictionary/HebrewWords.txt",
        "dictionary/3Letters.txt",
        "dictionary/4Letters.txt",
        "dictionary/5Letters.txt",
    )

    fun create(context: Context): DictionaryRepository = DictionaryRepository {
        sequence {
            for (asset in ASSET_FILES) {
                context.assets.open(asset).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) yield(trimmed)
                        }
                    }
                }
            }
        }
    }
}
