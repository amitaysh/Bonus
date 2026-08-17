package il.cet.bonus.shared.bonus

import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object BonusWordBankLoader {
    private val files = mapOf(3 to "dictionary/3Letters", 4 to "dictionary/4Letters", 5 to "dictionary/5Letters", 6 to "dictionary/6Letters", 7 to "dictionary/7Letters")
    private val sofitLetters = setOf('ם', 'ן', 'ץ', 'ף', 'ך')

    actual fun create(): BonusPuzzleGenerator {
        val words = files.mapValues { (_, base) ->
            val path = NSBundle.mainBundle.pathForResource(base, "txt") ?: return@mapValues emptyList<String>()
            val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String? ?: return@mapValues emptyList<String>()
            content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && it.none { c -> c in sofitLetters } }.toList()
        }
        return BonusPuzzleGenerator(words)
    }
}
