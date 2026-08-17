package il.cet.bonus.core.bonus

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BonusPuzzleGeneratorTest {

    private val bank = mapOf(
        3 to listOf("דוב", "סוס", "שיר"),
        4 to listOf("שלום", "תפוח"),
        5 to listOf("שולחן", "שולים", "בולים"),
    )

    @Test
    fun `anagram puzzle can be solved with the correct word`() {
        val gen = BonusPuzzleGenerator(bank, Random(42))
        val puzzle = gen.generateAnagram(3)
        assertNotNull(puzzle)
        assertTrue(gen.checkAnagram(puzzle, puzzle.answer))
        assertEquals(puzzle.scrambledLetters.sorted(), puzzle.answer.toList().sorted())
    }

    @Test
    fun `anagram accepts any valid dictionary word using the same letters not just the generated answer`() {
        // Letters מ,ה,ר,ג can form both "מהגר" and "גרמה" - both are real words, so
        // either should be accepted, matching the puzzle's actual goal (build *a* valid
        // word from the given letters, not guess the computer's specific pick).
        val gen = BonusPuzzleGenerator(mapOf(4 to listOf("מהגר")), Random(1))
        val puzzle = gen.generateAnagram(4)
        assertNotNull(puzzle)
        val dict = il.cet.bonus.core.dictionary.DictionaryRepository { sequenceOf("מהגר", "גרמה") }
        assertTrue(gen.checkAnagram(puzzle, "גרמה", dict))
        assertTrue(gen.checkAnagram(puzzle, "מהגר", dict))
        // A word that isn't a permutation of the given letters must still be rejected.
        assertTrue(!gen.checkAnagram(puzzle, "שלום", dict))
        // Without a dictionary, only the exact generated answer is accepted (backward-compatible default).
        assertTrue(!gen.checkAnagram(puzzle, "גרמה"))
    }

    @Test
    fun `fill in blank can be reconstructed`() {
        val gen = BonusPuzzleGenerator(bank, Random(1))
        val puzzle = gen.generateFillInBlank(4, blanksCount = 2)
        assertNotNull(puzzle)
        val correctLetters = puzzle.blankIndices.map { puzzle.answer[it] }
        assertTrue(gen.checkFillInBlank(puzzle, correctLetters))
        assertTrue(!gen.checkFillInBlank(puzzle, correctLetters.reversed().map { if (it == correctLetters.first()) 'x' else it }))
    }

    @Test
    fun `shared letter puzzle finds a common letter across words`() {
        val gen = BonusPuzzleGenerator(bank, Random(7))
        val puzzle = gen.generateSharedLetter(wordCount = 2, length = 3)
        assertNotNull(puzzle)
        assertTrue(gen.checkSharedLetter(puzzle, puzzle.sharedLetter))
        puzzle.words.forEachIndexed { i, w -> assertEquals(puzzle.sharedLetter, w[puzzle.sharedIndexPerWord[i]]) }
    }

    @Test
    fun `crossword bonus puzzle gives an empty 5x10 board and a 16 letter rack`() {
        val gen = BonusPuzzleGenerator(bank, Random(3))
        val puzzle = gen.generateCrosswordBonus()
        assertEquals(5, puzzle.rows)
        assertEquals(10, puzzle.cols)
        assertEquals(16, puzzle.rackLetters.size)
    }

    @Test
    fun `crossword bonus scores only when all formed words are valid`() {
        val gen = BonusPuzzleGenerator(bank)
        val dict = il.cet.bonus.core.dictionary.DictionaryRepository { sequenceOf("שלג") }
        val board = mapOf(
            il.cet.bonus.core.model.Position(0, 0) to tile('ש'),
            il.cet.bonus.core.model.Position(0, 1) to tile('ל'),
            il.cet.bonus.core.model.Position(0, 2) to tile('ג'),
        )
        val score = gen.scoreCrosswordBonus(board, dict)
        assertTrue(score > 0)

        val invalidDict = il.cet.bonus.core.dictionary.DictionaryRepository { sequenceOf("אחר") }
        assertEquals(0, gen.scoreCrosswordBonus(board, invalidDict))
    }

    private fun tile(c: Char) = il.cet.bonus.core.model.Tile.LetterTile(il.cet.bonus.core.model.Letter.fromHebrew(c)!!)
}
