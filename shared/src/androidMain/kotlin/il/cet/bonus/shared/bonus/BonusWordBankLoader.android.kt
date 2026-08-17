package il.cet.bonus.shared.bonus

import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import il.cet.bonus.shared.PlatformContextHolder
import java.io.BufferedReader
import java.io.InputStreamReader

actual object BonusWordBankLoader {
    private val files = mapOf(3 to "dictionary/3Letters.txt", 4 to "dictionary/4Letters.txt", 5 to "dictionary/5Letters.txt", 6 to "dictionary/6Letters.txt", 7 to "dictionary/7Letters.txt")
    actual fun create(): BonusPuzzleGenerator {
        val words = files.mapValues { (_, asset) ->
            PlatformContextHolder.context.assets.open(asset).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { it.map(String::trim).filter(String::isNotEmpty).toList() }
            }
        }
        return BonusPuzzleGenerator(words)
    }
}
