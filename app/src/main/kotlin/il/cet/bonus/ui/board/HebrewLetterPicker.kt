package il.cet.bonus.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/** Default tile size used across the app, including the bonus mini-game screen - it's now
 * a dedicated full-screen destination (see `BonusMiniGameScreen`) with plenty of room, so
 * there's no need to shrink tiles to fit the puzzle plus the full on-screen alphabet. */
private val DEFAULT_TILE_SIZE = 44.dp

/**
 * A tap-to-append Hebrew letter picker built entirely out of the original tile artwork
 * (per explicit user request: always use the letter-tile pictures, never a generated
 * keyboard/button) - each tile shows the real letter image (with its score baked in).
 * Shows every letter of the alphabet plus backspace/clear; [value] accumulates the
 * tapped letters in order. Used by the word-query ("שאילתא") dialog and the bonus
 * mini-game word-guess dialogs (fill-in-blank, shared-letter).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HebrewLetterPicker(
    value: String,
    onValueChange: (String) -> Unit,
    showActions: Boolean = true,
    // When non-null, caps how many letters can be accumulated in `value` - used by the
    // single-missing-letter bonus (SHARED_LETTER_TWO_WORDS/THREE_WORDS expect exactly one
    // letter) so tapping a new letter replaces the previous choice instead of appending.
    maxLength: Int? = null,
    tileSize: Dp = DEFAULT_TILE_SIZE,
    // The bonus mini-games show the typed answer inline inside the puzzle's own word
    // tiles (see WordDisplayTiles' `filledAnswer`), so the standalone text preview here
    // would be redundant floating text - callers that already show the built word
    // elsewhere pass false to save vertical space and avoid a confusing duplicate.
    showValuePreview: Boolean = true,
    // The letter pool to show as tappable tiles - defaults to the full 22-letter
    // alphabet (used by the word-query dialog and joker letter chooser), but the
    // "letter completion" bonus mini-games (fill-in-blank / shared-letter) pass a
    // smaller 10-12 letter subset (always including the correct letter(s)) instead of
    // the full keyboard, matching the original game's puzzle screens.
    letters: List<Letter> = Letter.entries,
    // Rendered to the side of the keyboard (not on its own line below it) - e.g. the
    // delete/clear/confirm buttons for the bonus mini-game screen. Sharing the
    // keyboard's own vertical space (instead of reserving a whole extra row just for
    // these buttons) frees up more room for the keyboard itself on a phone screen.
    actions: @Composable () -> Unit = {},
) {
    Column {
        if (showValuePreview) {
            Text(text = value.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Let FlowRow wrap naturally based on the *actual* available width instead of
            // a fixed item-count-per-row: a hardcoded "11 per row" (sized for the full
            // 22-letter alphabet keyboard) badly overflowed the screen for the bonus
            // mini-games, which share this row with the puzzle grid/actions and have much
            // less horizontal space - the fixed count caused tiles to run off-screen,
            // requiring an extra scroll to reach the last row (see no_room.png bug report).
            FlowRow(modifier = Modifier.weight(1f, fill = false)) {
                letters.forEach { letter ->
                    LetterTileButton(letter, size = tileSize) {
                        val next = value + letter.hebrew
                        onValueChange(if (maxLength != null) next.takeLast(maxLength) else next)
                    }
                }
            }
            actions()
        }
        if (showActions) {
            Row {
                BevelButton(text = "מחק", onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) })
                Spacer(modifier = Modifier.width(8.dp))
                BevelButton(text = "נקה", onClick = { onValueChange("") })
            }
        }
    }
}

/**
 * Anagram-specific picker: only the given [availableLetters] can be tapped (each letter
 * tile disappears from the pool once used, matching the constraint that the answer must
 * use exactly those letters), plus backspace/clear to return a used letter to the pool.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnagramLetterPicker(
    availableLetters: List<Char>,
    value: String,
    onValueChange: (String) -> Unit,
    tileSize: Dp = DEFAULT_TILE_SIZE,
    showValuePreview: Boolean = true,
    // See HebrewLetterPicker's `actions` param - rendered beside the keyboard instead of
    // on its own line, to save vertical space on a phone screen.
    actions: @Composable () -> Unit = {},
) {
    // Remaining pool = all letters minus those already consumed into `value` (multiset diff).
    val remaining = availableLetters.toMutableList().also { pool ->
        value.forEach { c -> pool.remove(c) }
    }
    Column {
        if (showValuePreview) {
            Text(text = value.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(modifier = Modifier.weight(1f, fill = false)) {
                remaining.forEach { c ->
                    val letter = Letter.fromHebrew(c)
                    if (letter != null) {
                        LetterTileButton(letter, size = tileSize) { onValueChange(value + c) }
                    }
                }
            }
            actions()
        }
        Row {
            BevelButton(text = "מחק", onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) })
            Spacer(modifier = Modifier.width(8.dp))
            BevelButton(text = "נקה", onClick = { onValueChange("") })
        }
    }
}

/**
 * Renders a word (with `_` marking hidden/blank letters) as a row of real letter-tile
 * images, with a plain empty bordered tile standing in for each *still-unfilled* blank -
 * used by the fill-in-blank/anagram/shared-letter bonus mini-games so the puzzle is shown
 * with the same tile artwork as everywhere else instead of plain text with underscores.
 *
 * [filledAnswer], if non-empty, overlays the player's typed letters (in order) into the
 * blank slots as they're typed - e.g. after typing 2 letters for a 3-blank word, the
 * first 2 blanks show the typed letter tiles and the 3rd still shows an empty box. This
 * mirrors the C# reference's drag-a-letter-into-the-slot behavior (BonusForm.cs
 * PutLetterOnBoard) instead of showing the typed answer as separate floating text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDisplayTiles(displayed: String, filledAnswer: String = "", tileSize: Dp = DEFAULT_TILE_SIZE) {
    var blanksSeen = 0
    FlowRow {
        displayed.forEach { c ->
            if (c == '_') {
                val filledChar = filledAnswer.getOrNull(blanksSeen)
                blanksSeen++
                if (filledChar != null) {
                    val letter = Letter.fromHebrew(filledChar)
                    if (letter != null) {
                        Image(
                            painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                            contentDescription = filledChar.toString(),
                            modifier = Modifier.padding(2.dp).size(tileSize),
                        )
                        return@forEach
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(tileSize)
                        .border(1.dp, Color.DarkGray)
                        .background(Color(0xFFEDEDED)),
                )
            } else {
                val letter = Letter.fromHebrew(c)
                if (letter != null) {
                    Image(
                        painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                        contentDescription = c.toString(),
                        modifier = Modifier.padding(2.dp).size(tileSize),
                    )
                }
            }
        }
    }
}

/**
 * Renders [letters] as a single vertical column of tappable tiles - used to place half
 * the letter-completion keyboard on each side of the puzzle grid (see
 * [BonusMiniGameScreen]'s per-case layouts), matching the original game's screenshots
 * where the letter pool flanks the puzzle instead of sitting in a wide row below it
 * (which didn't fit on a phone screen without extra scrolling - see no_room.png bug).
 */
@Composable
fun LetterSideColumn(
    letters: List<Letter>,
    tileSize: Dp = DEFAULT_TILE_SIZE,
    onLetterTap: (Letter) -> Unit,
) {
    Column {
        letters.forEach { letter ->
            LetterTileButton(letter, size = tileSize) { onLetterTap(letter) }
        }
    }
}

/**
 * Renders [letters] as a single flat, wrapping row of tappable tiles - used by the
 * single-word letter-completion puzzles (fill-in-blank, 3-word shared-letter) which,
 * per product decision, should show one plain 10-12 letter keyboard instead of a
 * keyboard split across both sides of the puzzle (that split layout is reserved for the
 * 2-word "cross" shared-letter puzzle only, matching the original game's screens).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleLetterKeyboardRow(
    letters: List<Letter>,
    tileSize: Dp = DEFAULT_TILE_SIZE,
    modifier: Modifier = Modifier,
    onLetterTap: (Letter) -> Unit,
) {
    FlowRow(modifier = modifier) {
        letters.forEach { letter ->
            LetterTileButton(letter, size = tileSize) { onLetterTap(letter) }
        }
    }
}

/** A single tappable letter tile rendered with the real original tile artwork (see
 * [LetterTileArt]), so every letter-entry surface in the app looks consistent with the
 * board/rack tiles instead of a generic keyboard button. */
@Composable
private fun LetterTileButton(letter: Letter, size: Dp = DEFAULT_TILE_SIZE, onClick: () -> Unit) {
    // pointerInput(letter) only restarts the gesture-detector coroutine when `letter`
    // changes - since `letter` never changes across recompositions here, the coroutine
    // (and the onClick closure it captured on first composition) stayed alive forever,
    // silently using a stale `value`/`onValueChange` and causing typed letters to
    // clobber each other on long words. rememberUpdatedState keeps the callback fresh
    // without needing to restart pointerInput on every value change.
    val currentOnClick by rememberUpdatedState(onClick)
    Image(
        painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
        contentDescription = letter.hebrew.toString(),
        modifier = Modifier
            .padding(2.dp)
            .size(size)
            .pointerInput(letter) {
                detectTapGestures { currentOnClick() }
            },
    )
}
