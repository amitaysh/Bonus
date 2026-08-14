package il.cet.bonus.core.game

import il.cet.bonus.core.board.Board
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameEngineTest {

    private fun engine(words: Set<String>): GameEngine {
        val dict = DictionaryRepository { words.asSequence() }
        return GameEngine(Board(), dict)
    }

    private fun tile(c: Char): Tile = Tile.LetterTile(Letter.fromHebrew(c)!!)

    @Test
    fun `first move must have at least two letters`() {
        val eng = engine(setOf("שלום"))
        val result = eng.proposeMove(mapOf(Position(0, 0) to tile('ש')))
        assertTrue(result.isFailure)
    }

    @Test
    fun `first move does not need to cover the center`() {
        // Confirmed via original gameplay footage: classic Bonus has no "first word must
        // cover center" rule - the first word just needs to land on the grid (and avoid
        // bonus tiles). Placing away from center should succeed.
        val eng = engine(setOf("של"))
        val move = mapOf(
            Position(2, 2) to tile('ש'),
            Position(2, 3) to tile('ל'),
        )
        val result = eng.proposeMove(move)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `valid first move through center scores correctly`() {
        val eng = engine(setOf("של"))
        val center = 5
        val move = mapOf(
            Position(center, center - 1) to tile('ש'),
            Position(center, center) to tile('ל'),
        )
        val result = eng.proposeMove(move)
        assertTrue(result.isSuccess)
        val turn = result.getOrThrow()
        assertEquals(1, turn.newWords.size)
        assertEquals("של", turn.newWords.first().word)
        val expectedScore = Letter.SHIN.score + Letter.LAMED.score
        assertEquals(expectedScore, turn.scoreDelta)
    }

    @Test
    fun `first move rejected if word not in dictionary`() {
        val eng = engine(setOf("אחר"))
        val center = 5
        val move = mapOf(
            Position(center, center - 1) to tile('ש'),
            Position(center, center) to tile('ל'),
        )
        val result = eng.proposeMove(move)
        assertTrue(result.isFailure)
    }

    @Test
    fun `second move must attach to existing words`() {
        val eng = engine(setOf("של", "לב"))
        val center = 5
        eng.proposeMove(
            mapOf(
                Position(center, center - 1) to tile('ש'),
                Position(center, center) to tile('ל'),
            )
        ).getOrThrow()

        // Disconnected move far from existing tiles should fail.
        val disconnected = eng.proposeMove(
            mapOf(
                Position(0, 0) to tile('ל'),
                Position(0, 1) to tile('ב'),
            )
        )
        assertTrue(disconnected.isFailure)

        // Attached move (extends the ל at center downward) should succeed.
        val attached = eng.proposeMove(mapOf(Position(center + 1, center) to tile('ב')))
        assertTrue(attached.isSuccess)
    }

    @Test
    fun `locks block placement until they expire`() {
        val board = Board()
        val pos = Position(6, 6)
        LockAction.placeLock(board, pos, 3).getOrThrow()
        assertFalse(board.isPlaceable(pos))
        board.tickLocks()
        board.tickLocks()
        assertFalse(board.isPlaceable(pos))
        board.tickLocks()
        assertTrue(board.isPlaceable(pos))
    }

    @Test
    fun `bonus slot cannot trigger on the first move`() {
        val board = Board()
        val bonusPos = board.positionFor(board.bonusSlots.first { it.edge == Board.Edge.TOP })
        val dict = DictionaryRepository { sequenceOf("של") }
        val eng = GameEngine(board, dict)
        val center = board.size / 2
        // Build a word from the top-edge bonus position through the center - not realistic
        // geometry, but exercises the "no bonus on first move" rule directly instead.
        val move = mapOf(bonusPos to tile('ש'), Position(bonusPos.row, bonusPos.col + 1) to tile('ל'))
        val result = eng.proposeMove(move)
        assertTrue(result.isFailure)
    }
}
