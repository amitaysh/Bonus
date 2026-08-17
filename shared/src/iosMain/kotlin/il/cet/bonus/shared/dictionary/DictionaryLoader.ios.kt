package il.cet.bonus.shared.dictionary

import il.cet.bonus.core.dictionary.DictionaryRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.NSUTF8StringEncoding

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object DictionaryLoader {
    private val assetFiles = listOf("dictionary/HebrewWords", "dictionary/3Letters", "dictionary/4Letters", "dictionary/5Letters")

    actual fun create(): DictionaryRepository {
        val words = assetFiles.flatMap { base ->
            val path = NSBundle.mainBundle.pathForResource(base, "txt") ?: return@flatMap emptyList<String>()
            val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String? ?: return@flatMap emptyList<String>()
            content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
        return DictionaryRepository { words.asSequence() }
    }
}
