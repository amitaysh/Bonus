package il.cet.bonus.core.dictionary

/**
 * Validates Hebrew words against the bundled word list(s) shipped as Android assets.
 *
 * Per explicit user decision: the word lists (`HebrewWords.txt`, `3Letters.txt`,
 * `4Letters.txt`, `5Letters.txt`) are used completely as-is, with NO cleanup, dedupe, or
 * "sofit" (final-letter) correction applied. This is intentional: the original 1993 Bonus
 * game has no sofit letter tiles at all, so words are only ever built and matched using
 * regular (non-final) letter forms, even at the end of a word. That is correct behavior,
 * not a data bug - do not "fix" it.
 */
class DictionaryRepository(private val loadWords: () -> Sequence<String>) {

    private val words: MutableSet<String> by lazy {
        HashSet<String>().apply { loadWords().forEach { add(it.trim()) } }
    }

    fun isValidWord(word: String): Boolean = word.length > 1 && words.contains(word)

    /** Adds a word at runtime (e.g. after a successful challenge/appeal overrides the dictionary). */
    fun addWord(word: String) {
        words.add(word)
    }
}
