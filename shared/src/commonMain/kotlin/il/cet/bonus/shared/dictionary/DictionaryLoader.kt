package il.cet.bonus.shared.dictionary

import il.cet.bonus.core.dictionary.DictionaryRepository

expect object DictionaryLoader {
    fun create(): DictionaryRepository
}
