package il.cet.bonus.core.bonus

import il.cet.bonus.core.board.WordExtractor
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import kotlin.random.Random

/**
 * Generates and validates the dynamic bonus mini-games. Mirrors the C# reference's use
 * of dedicated 3/4/5-letter word lists (`3Letters.txt`/`4Letters.txt`/`5Letters.txt`) as
 * a separate word bank for puzzle generation, distinct from the full dictionary used to
 * validate board words.
 */
class BonusPuzzleGenerator(
    private val wordsByLength: Map<Int, List<String>>,
    private val random: Random = Random.Default,
) {
    // ---- Anagram ----

    data class AnagramPuzzle(val scrambledLetters: List<Char>, val answer: String)

    /** Picks a random word of [length] and scrambles its letters for the player to rebuild. */
    fun generateAnagram(length: Int): AnagramPuzzle? {
        val word = randomWord(length) ?: return null
        var letters = word.toList()
        do {
            letters = letters.shuffled(random)
        } while (letters.joinToString("") == word && letters.size > 1)
        return AnagramPuzzle(letters, word)
    }

    /**
     * A guess is correct if it uses exactly the given letters (any multiset permutation)
     * AND is a real word - not merely if it matches the specific word the generator
     * happened to pick. The puzzle's purpose is to assemble *any* valid word from the
     * given letters, not to guess the computer's arbitrary choice (e.g. given
     * מ,ה,ר,ג both "מהגר" and "גרמה" must be accepted). Falls back to exact-match-only
     * if no [dictionary] is supplied (e.g. in tests using a tiny fixture bank).
     */
    fun checkAnagram(puzzle: AnagramPuzzle, guess: String, dictionary: DictionaryRepository? = null): Boolean {
        if (guess.toList().sorted() != puzzle.scrambledLetters.sorted()) return false
        if (guess == puzzle.answer) return true
        return dictionary?.isValidWord(guess) ?: false
    }

    /**
     * Bonus awarded for a correct anagram solve, scaled by word length per the confirmed
     * rule ("סכום הבונוס שיקבל תלוי באורך המילה... מ־30 ועד 100 נקודות" - Hebrew Wikipedia
     * "בונוס (משחק מחשב)"). The exact original formula wasn't recovered, so this linearly
     * scales 3-letter words to 30 points and 8+-letter words to 100 points.
     */
    fun anagramScore(wordLength: Int): Int =
        (30 + (wordLength - 3).coerceAtLeast(0) * 14).coerceIn(30, 100)

    // ---- Fill in the blank (single word) ----

    data class FillInBlankPuzzle(val displayed: String, val blankIndices: List<Int>, val answer: String)

    /** Picks a random word and hides [blanksCount] of its letters (default: ~1/3 of the word). */
    fun generateFillInBlank(length: Int, blanksCount: Int = (length / 3).coerceAtLeast(1)): FillInBlankPuzzle? {
        val word = randomWord(length) ?: return null
        val indices = word.indices.shuffled(random).take(blanksCount).sorted()
        val displayed = word.mapIndexed { i, c -> if (i in indices) '_' else c }.joinToString("")
        return FillInBlankPuzzle(displayed, indices, word)
    }

    fun checkFillInBlank(puzzle: FillInBlankPuzzle, filledLetters: List<Char>): Boolean {
        if (filledLetters.size != puzzle.blankIndices.size) return false
        val reconstructed = puzzle.displayed.toCharArray()
        puzzle.blankIndices.forEachIndexed { i, idx -> reconstructed[idx] = filledLetters[i] }
        return String(reconstructed) == puzzle.answer
    }

    // ---- Shared letter (2 or 3 words sharing one missing letter) ----

    data class SharedLetterPuzzle(val words: List<String>, val sharedIndexPerWord: List<Int>, val sharedLetter: Char)

    /**
     * Builds a puzzle where [wordCount] words (2 or 3) each hide the same shared letter
     * at a *fixed* position per word - matching the C# reference's exact layouts, not an
     * arbitrary shared occurrence:
     * - 2 words (`TwoCross`, both 5 letters): the shared letter must be each word's
     *   middle (index 2) letter, so the "+" cross's blank is exactly at the intersection.
     * - 3 words (`ThreeBy3`, all 3 letters): the shared letter must be word0's *last*
     *   letter (index 2), word1's *middle* letter (index 1), and word2's *first* letter
     *   (index 0) - this is what makes each row's first letter align under the previous
     *   row's last letter, forming the diagonal staircase.
     * Falls back to null if no combination of words satisfying these fixed positions can
     * be found from the word bank (best-effort search, bounded attempts).
     */
    fun generateSharedLetter(wordCount: Int, length: Int, maxAttempts: Int = 200): SharedLetterPuzzle? {
        val bank = wordsByLength[length] ?: return null
        if (bank.size < wordCount) return null
        val requiredIndices = when (wordCount) {
            2 -> listOf(length / 2, length / 2)
            3 -> listOf(length - 1, length / 2, 0)
            else -> return null
        }
        repeat(maxAttempts) {
            val shuffled = bank.shuffled(random)
            // For each candidate shared letter, try to pick one distinct word per slot
            // whose letter at that slot's required index equals the candidate.
            val wordsBySlotAndLetter = Array(wordCount) { slot ->
                shuffled.filter { it.length == length && it.getOrNull(requiredIndices[slot]) != null }
                    .groupBy { it[requiredIndices[slot]] }
            }
            val commonLetters = wordsBySlotAndLetter[0].keys.toMutableSet()
            for (slot in 1 until wordCount) commonLetters.retainAll(wordsBySlotAndLetter[slot].keys)
            for (letterCandidate in commonLetters.shuffled(random)) {
                val used = HashSet<String>()
                val candidate = ArrayList<String>(wordCount)
                var ok = true
                for (slot in 0 until wordCount) {
                    val pick = wordsBySlotAndLetter[slot][letterCandidate]?.firstOrNull { it !in used }
                    if (pick == null) { ok = false; break }
                    used.add(pick)
                    candidate.add(pick)
                }
                if (ok) return SharedLetterPuzzle(candidate, requiredIndices, letterCandidate)
            }
        }
        return null
    }

    fun checkSharedLetter(puzzle: SharedLetterPuzzle, guess: Char): Boolean = guess == puzzle.sharedLetter

    // ---- Crossword build ("בונוס שבץ-נא אישי" / personal scrabble bonus) ----

    /**
     * An empty 5x10 mini-board (confirmed via Hebrew Wikipedia "בונוס (משחק מחשב)" - see
     * plan.md: "מופיע לוח משחק קטן בגודל של 5 על 10 משבצות, והשחקן מקבל מהמחשב 16 אותיות
     * שנבחרו אוטומטית (אפשר שמקצתן יופיעו יותר מפעם אחת)"), plus a rack of 16 randomly
     * drawn letters (repeats allowed) for the player to freely build words with within 60s.
     */
    data class CrosswordBonusPuzzle(
        val rows: Int,
        val cols: Int,
        val rackLetters: List<Letter>,
    )

    /**
     * Generates a personal-scrabble bonus puzzle: an empty [rows]x[cols] board (5x10 per
     * the confirmed original) and a rack of [rackSize] (16 per the confirmed original)
     * random letters, independently drawn so repeats are possible.
     */
    fun generateCrosswordBonus(rows: Int = 5, cols: Int = 10, rackSize: Int = 16): CrosswordBonusPuzzle {
        val rackLetters = (1..rackSize).map { Letter.entries.random(random) }
        return CrosswordBonusPuzzle(rows, cols, rackLetters)
    }

    /**
     * Validates the player's final mini-board layout against [dictionary]. Per the
     * confirmed all-or-nothing rule ("אם יש אפילו מילה אחת שאיננה חוקית, השחקן אינו מקבל
     * שום ניקוד"), returns the sum of scores of every word formed only if ALL formed words
     * (length > 1) are valid; otherwise returns 0.
     */
    fun scoreCrosswordBonus(finalBoard: Map<Position, Tile>, dictionary: DictionaryRepository): Int {
        val rowWords = WordExtractor.rowWords(finalBoard)
        val colWords = WordExtractor.columnWords(finalBoard)
        val allWords = rowWords + colWords
        if (allWords.isEmpty()) return 0
        if (allWords.any { !dictionary.isValidWord(it.word) }) return 0
        return allWords.sumOf { placed -> placed.cells.sumOf { finalBoard.getValue(it).score } }
    }

    private fun randomWord(length: Int): String? = wordsByLength[length]?.randomOrNull(random)
}
