package il.cet.bonus.shared.bonus

import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object BonusWordBankLoader {
    private val files = mapOf(3 to "3Letters", 4 to "4Letters", 5 to "5Letters", 6 to "6Letters", 7 to "7Letters")
    private val sofitLetters = setOf('ם', 'ן', 'ץ', 'ף', 'ך')

    actual fun create(): BonusPuzzleGenerator {
        // See DictionaryLoader.ios.kt: composeResources/files/* end up under a
        // top-level "files/" directory in the app bundle, not at the bundle root.
        val words = files.mapValues { (_, base) ->
            val path = NSBundle.mainBundle.pathForResource(base, "txt", "files/dictionary")
                ?: return@mapValues emptyList<String>()
            val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String? ?: return@mapValues emptyList<String>()
            content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && it.none { c -> c in sofitLetters } }.toList()
        }
        return BonusPuzzleGenerator(words)
    }
}
