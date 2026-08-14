package il.cet.bonus.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile

/**
 * Reproduces the C# reference's `TwoCross`/`ThreeBy3` bonus board layouts (see
 * `BonusForm.cs` `PopulateLettersOnPuzzle`), using the real letter-tile artwork
 * ([LetterTileArt]) instead of plain text, and sized to comfortably fit a phone screen
 * (smaller tiles + a horizontal-scroll safety net for the staircase, which is the wider
 * of the two shapes).
 */

private fun letterTileImage(c: Char): Int? = Letter.fromHebrew(c)?.let { LetterTileArt.drawableFor(Tile.LetterTile(it)) }

/** [filledLetter], when non-null, is the player's currently-typed guess for this blank -
 * shown as a real tile image instead of an empty box (mirrors WordDisplayTiles'
 * filledAnswer overlay), so tapping a letter visibly fills the missing slot instead of
 * only appearing as separate floating text. */
@Composable
private fun PuzzleTile(letter: Char?, size: Dp, isBlank: Boolean, filledLetter: Char? = null) {
    val effectiveChar = if (isBlank) filledLetter else letter
    if (effectiveChar == null) {
        Box(
            modifier = Modifier
                .padding(1.dp)
                .size(size)
                .border(1.dp, if (isBlank) Color.DarkGray else Color.Transparent)
                .background(if (isBlank) Color(0xFFEDEDED) else Color.Transparent),
        )
    } else {
        val res = letterTileImage(effectiveChar)
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = effectiveChar.toString(),
                modifier = Modifier.padding(1.dp).size(size),
            )
        } else {
            Box(modifier = Modifier.padding(1.dp).size(size).border(1.dp, Color.DarkGray))
        }
    }
}

/**
 * `SHARED_LETTER_THREE_WORDS` ("3 על 3"): 3 rows, one word per row, each row indented by
 * exactly 1 tile-width from the previous one (per product spec: "one word below the
 * other with indentation of 1 letter, so the missing letter's tile is in the same
 * column for all 3 words"). Since the shared letter sits at word0's *last* letter,
 * word1's *middle* letter, and word2's *first* letter (see
 * [BonusPuzzleGenerator.generateSharedLetter]'s fixed `requiredIndices`), a 1-tile shift
 * per row makes all 3 blanks land in the exact same column, forming a vertical line
 * (not a diagonal) - matching the C# reference. [filledLetter] (the player's current
 * single-letter guess) is overlaid into all 3 blanks live, since they all share the
 * same answer.
 */
@Composable
fun ThreeWordsStaircase(puzzle: BonusPuzzleGenerator.SharedLetterPuzzle, tileSize: Dp = 34.dp, filledLetter: Char? = null) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            puzzle.words.forEachIndexed { row, word ->
                Row(modifier = Modifier.padding(start = tileSize * row)) {
                    word.forEachIndexed { col, ch ->
                        PuzzleTile(
                            letter = ch,
                            size = tileSize,
                            isBlank = col == puzzle.sharedIndexPerWord[row],
                            filledLetter = filledLetter,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `SHARED_LETTER_TWO_WORDS` ("שתי וערב"): the two 5-letter words cross at their shared
 * middle (3rd) letter, forming a "+" - matching the C# layout where the horizontal word
 * occupies one row and the vertical word occupies one column, both centered on the same
 * single blank cell (`dynamicBoard[5,2]` in the C# reference). [filledLetter] (the
 * player's current single-letter guess) is shown live in that shared cell.
 */
@Composable
fun TwoWordsCross(puzzle: BonusPuzzleGenerator.SharedLetterPuzzle, tileSize: Dp = 34.dp, filledLetter: Char? = null) {
    require(puzzle.words.size == 2) { "TwoWordsCross expects exactly 2 words" }
    val horizontal = puzzle.words[0]
    val vertical = puzzle.words[1]
    val horizontalSharedCol = puzzle.sharedIndexPerWord[0]
    val verticalSharedRow = puzzle.sharedIndexPerWord[1]

    Box(contentAlignment = Alignment.Center) {
        // Vertical word: full column, transparent spacer at the shared row so the
        // horizontal row's blank tile (drawn on top) is the only thing visible there.
        Column {
            vertical.forEachIndexed { row, ch ->
                if (row == verticalSharedRow) {
                    Box(modifier = Modifier.padding(1.dp).size(tileSize))
                } else {
                    PuzzleTile(letter = ch, size = tileSize, isBlank = false)
                }
            }
        }
        // Horizontal word: full row, overlaid at the box's vertical center - its middle
        // tile (the shared/blank letter) lands exactly on the vertical word's middle row.
        Row {
            horizontal.forEachIndexed { col, ch ->
                PuzzleTile(
                    letter = ch,
                    size = tileSize,
                    isBlank = col == horizontalSharedCol,
                    filledLetter = filledLetter,
                )
            }
        }
    }
}
