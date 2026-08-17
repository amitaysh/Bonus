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

    data class AnagramPuzzle(
        val scrambledLetters: List<Char>,
        val answer: String,
        // For 6/7-letter anagrams, the first and last letters are pre-placed and
        // cannot be changed by the player - per product decision, only the middle
        // letters are scrambled/guessable. Empty for 4/5-letter anagrams.
        val lockedPrefix: String = "",
        val lockedSuffix: String = "",
    )

    /** Picks a random word of [length] and scrambles its letters for the player to rebuild.
     * If [lockEnds] is true (used for 6/7-letter words), the first and last letters are
     * fixed/shown in place and only the middle letters are scrambled. */
    fun generateAnagram(length: Int, lockEnds: Boolean = false): AnagramPuzzle? {
        val word = randomWord(length) ?: return null
        if (lockEnds && word.length >= 3) {
            val middle = word.substring(1, word.length - 1).toList()
            var scrambled = middle
            if (middle.size > 1) {
                do {
                    scrambled = scrambled.shuffled(random)
                } while (scrambled.joinToString("") == middle.joinToString(""))
            }
            return AnagramPuzzle(
                scrambledLetters = scrambled,
                answer = word,
                lockedPrefix = word.first().toString(),
                lockedSuffix = word.last().toString(),
            )
        }
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
     *
     * For locked-ends puzzles ([AnagramPuzzle.lockedPrefix]/[lockedSuffix] non-empty),
     * [guess] is expected to be only the *middle* letters the player rearranged (the
     * locked first/last letters are fixed and not part of the guess) - this reconstructs
     * the full word before validating.
     */
    fun checkAnagram(puzzle: AnagramPuzzle, guess: String, dictionary: DictionaryRepository? = null): Boolean {
        if (puzzle.lockedPrefix.isNotEmpty() || puzzle.lockedSuffix.isNotEmpty()) {
            if (guess.toList().sorted() != puzzle.scrambledLetters.sorted()) return false
            val fullWord = puzzle.lockedPrefix + guess + puzzle.lockedSuffix
            if (fullWord == puzzle.answer) return true
            return dictionary?.isValidWord(fullWord) ?: false
        }
        if (guess.toList().sorted() != puzzle.scrambledLetters.sorted()) return false
        if (guess == puzzle.answer) return true
        return dictionary?.isValidWord(guess) ?: false
    }

    /**
     * Bonus awarded for a correct anagram solve. Fixed per-length values per product
     * decision: 4 letters = 30, 5 = 50, 6 = 75, 7 = 100.
     */
    fun anagramScore(wordLength: Int): Int = when (wordLength) {
        4 -> 30
        5 -> 50
        6 -> 75
        7 -> 100
        else -> (30 + (wordLength - 3).coerceAtLeast(0) * 14).coerceIn(30, 100)
    }

    // ---- Fill in the blank (single word) ----

    data class FillInBlankPuzzle(val displayed: String, val blankIndices: List<Int>, val answer: String)

    /** Picks a random word and hides [blanksCount] of its letters (default: ~1/3 of the word). */
    fun generateFillInBlank(length: Int, blanksCount: Int = (length / 3).coerceAtLeast(1)): FillInBlankPuzzle? {
        val word = randomWord(length) ?: return null
        val indices = word.indices.shuffled(random).take(blanksCount).sorted()
        val displayed = word.mapIndexed { i, c -> if (i in indices) '_' else c }.joinToString("")
        return FillInBlankPuzzle(displayed, indices, word)
    }

    /**
     * Accepts either the exact original [puzzle] answer, or - per product decision -
     * ANY other letter choice that still reconstructs a valid dictionary word (e.g. the
     * player picks a different but equally valid letter for the blank(s), forming a
     * different real word than the one originally hidden). Matches [checkAnagram]'s
     * dictionary-fallback pattern; [dictionary] is optional so existing unit tests using
     * a tiny fixture bank (or none) keep working via the exact-answer check.
     */
    fun checkFillInBlank(puzzle: FillInBlankPuzzle, filledLetters: List<Char>, dictionary: DictionaryRepository? = null): Boolean {
        if (filledLetters.size != puzzle.blankIndices.size) return false
        val reconstructed = puzzle.displayed.toCharArray()
        puzzle.blankIndices.forEachIndexed { i, idx -> reconstructed[idx] = filledLetters[i] }
        val word = reconstructed.concatToString()
        if (word == puzzle.answer) return true
        return dictionary?.isValidWord(word) ?: false
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

    /**
     * A guess is correct if using it as the shared letter turns EVERY word in the puzzle
     * into a valid dictionary word - not merely if it matches the letter the generator
     * originally picked (mirrors [checkAnagram]/[checkFillInBlank]'s "any valid word is
     * accepted" behavior). Falls back to exact-letter-match only if no [dictionary] is
     * supplied (e.g. in tests using a tiny fixture bank).
     */
    fun checkSharedLetter(puzzle: SharedLetterPuzzle, guess: Char, dictionary: DictionaryRepository? = null): Boolean {
        if (guess == puzzle.sharedLetter) return true
        if (dictionary == null) return false
        return puzzle.words.indices.all { i ->
            val word = puzzle.words[i]
            val idx = puzzle.sharedIndexPerWord[i]
            if (idx !in word.indices) return@all false
            val candidate = word.toCharArray().also { it[idx] = guess }.concatToString()
            dictionary.isValidWord(candidate)
        }
    }

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
     * Per-word result for the crossword-build summary popup ("free word building"
     * completion screen - see TODO/free_sum.png reference): each formed word alongside
     * its raw point value and whether it's a valid dictionary word. [valid] here is
     * purely informational for the summary UI; the actual awarded score still follows
     * the all-or-nothing rule (see [scoreCrosswordBonus]).
     */
    data class CrosswordWordResult(val word: String, val score: Int, val valid: Boolean)

    /**
     * Lists every word formed on [finalBoard] (rows + columns) with its score and
     * validity, for the crossword-build completion summary (shows a checkmark per valid
     * word, matching the original game's "free word building" results popup).
     */
    fun crosswordWordResults(finalBoard: Map<Position, Tile>, dictionary: DictionaryRepository): List<CrosswordWordResult> {
        val allWords = WordExtractor.rowWords(finalBoard) + WordExtractor.columnWords(finalBoard)
        return allWords.map { placed ->
            CrosswordWordResult(
                word = placed.word,
                score = placed.cells.sumOf { finalBoard.getValue(it).score },
                valid = dictionary.isValidWord(placed.word),
            )
        }
    }

    /**
     * Validates the player's final mini-board layout against [dictionary]. Per the
     * confirmed all-or-nothing rule ("אם יש אפילו מילה אחת שאיננה חוקית, השחקן אינו מקבל
     * שום ניקוד"), returns the sum of scores of every word formed only if ALL formed words
     * (length > 1) are valid; otherwise returns 0.
     */
    fun scoreCrosswordBonus(finalBoard: Map<Position, Tile>, dictionary: DictionaryRepository): Int {
        val results = crosswordWordResults(finalBoard, dictionary)
        if (results.isEmpty()) return 0
        if (results.any { !it.valid }) return 0
        return results.sumOf { it.score }
    }

    private fun randomWord(length: Int): String? = wordsByLength[length]?.randomOrNull(random)

    /**
     * Builds a random pool of [size] distinct letters (10-12 per the original game's
     * "letter completion" bonus screens - see plan.md/TODO), always including every
     * letter in [required] so the puzzle remains solvable, padded with random distractor
     * letters and shuffled so the correct letter's position isn't predictable. Used by
     * the fill-in-blank and shared-letter bonus mini-games instead of showing the full
     * 22-letter alphabet.
     */
    fun buildLetterKeyboard(required: Collection<Letter>, size: Int = 12): List<Letter> {
        val distinctRequired = required.distinct()
        val pool = distinctRequired.toMutableList()
        val distractors = Letter.entries.filter { it !in distinctRequired }.shuffled(random)
        var i = 0
        while (pool.size < size && i < distractors.size) {
            pool.add(distractors[i])
            i++
        }
        return pool.shuffled(random)
    }
}
