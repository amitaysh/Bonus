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

    /** A guess is correct only if it uses exactly the given letters (any order) and is the target word. */
    fun checkAnagram(puzzle: AnagramPuzzle, guess: String): Boolean =
        guess == puzzle.answer && guess.toList().sorted() == puzzle.scrambledLetters.sorted()

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
     * at some position. Falls back to null if no combination of words sharing a common
     * letter can be found from the word bank (best-effort search, bounded attempts).
     */
    fun generateSharedLetter(wordCount: Int, length: Int, maxAttempts: Int = 200): SharedLetterPuzzle? {
        val bank = wordsByLength[length] ?: return null
        if (bank.size < wordCount) return null
        repeat(maxAttempts) {
            val candidate = bank.shuffled(random).take(wordCount)
            // Try every letter position of the first word as the "shared" position candidate.
            for (letterCandidate in candidate.first().toSet()) {
                if (candidate.all { it.contains(letterCandidate) }) {
                    val indices = candidate.map { it.indexOf(letterCandidate) }
                    return SharedLetterPuzzle(candidate, indices, letterCandidate)
                }
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
