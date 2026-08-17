package il.cet.bonus.shared.dictionary

import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.shared.PlatformContextHolder
import java.io.BufferedReader
import java.io.InputStreamReader

actual object DictionaryLoader {
    private val assetFiles = listOf("dictionary/HebrewWords.txt", "dictionary/3Letters.txt", "dictionary/4Letters.txt", "dictionary/5Letters.txt")
    actual fun create(): DictionaryRepository = DictionaryRepository {
        sequence {
            for (asset in assetFiles) {
                PlatformContextHolder.context.assets.open(asset).use { input ->
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
