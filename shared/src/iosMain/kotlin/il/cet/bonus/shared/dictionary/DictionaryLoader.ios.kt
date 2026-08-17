package il.cet.bonus.shared.dictionary

import il.cet.bonus.core.dictionary.DictionaryRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.NSUTF8StringEncoding

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object DictionaryLoader {
    private val assetFiles = listOf("HebrewWords", "3Letters", "4Letters", "5Letters")

    actual fun create(): DictionaryRepository {
        // Compose Multiplatform bundles composeResources/files/* into the app bundle
        // under a top-level "files/" directory (not directly at the bundle root), so
        // the lookup must pass "files/dictionary" as the subdirectory.
        val words = assetFiles.flatMap { base ->
            val path = NSBundle.mainBundle.pathForResource(base, "txt", "files/dictionary")
                ?: return@flatMap emptyList<String>()
            val content = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String? ?: return@flatMap emptyList<String>()
            content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
        return DictionaryRepository { words.asSequence() }
    }
}
