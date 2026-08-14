package il.cet.bonus.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Tile
import androidx.compose.foundation.layout.ExperimentalLayoutApi

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
) {
    Column {
        Text(text = value.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall)
        // Hardcoded 11-per-row so all 22 letters fill exactly 2 even rows; FlowRow (unlike
        // a manually chunked Row) measures each row's actual content so no tile clips even
        // if the row's total width would exceed the container - it just overflows visibly
        // instead of stopping wrapping, but with 11 fixed and adequate dialog width this
        // fits cleanly in 2 rows.
        FlowRow(maxItemsInEachRow = 11) {
            Letter.entries.forEach { letter ->
                LetterTileButton(letter) {
                    val next = value + letter.hebrew
                    onValueChange(if (maxLength != null) next.takeLast(maxLength) else next)
                }
            }
        }
        if (showActions) {
            Row {
                Button(onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) }) { Text("מחק") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onValueChange("") }) { Text("נקה") }
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
fun AnagramLetterPicker(availableLetters: List<Char>, value: String, onValueChange: (String) -> Unit) {
    // Remaining pool = all letters minus those already consumed into `value` (multiset diff).
    val remaining = availableLetters.toMutableList().also { pool ->
        value.forEach { c -> pool.remove(c) }
    }
    Column {
        Text(text = value.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall)
        FlowRow {
            remaining.forEach { c ->
                val letter = Letter.fromHebrew(c)
                if (letter != null) {
                    LetterTileButton(letter) { onValueChange(value + c) }
                }
            }
        }
        Row {
            Button(onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) }) { Text("מחק") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onValueChange("") }) { Text("נקה") }
        }
    }
}

/**
 * Renders a word (with `_` marking hidden/blank letters) as a row of real letter-tile
 * images, with a plain empty bordered tile standing in for each blank spot - used by the
 * fill-in-blank and shared-letter bonus mini-games so the puzzle is shown with the same
 * tile artwork as everywhere else instead of plain text with underscores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDisplayTiles(displayed: String) {
    FlowRow {
        displayed.forEach { c ->
            if (c == '_') {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(44.dp)
                        .border(1.dp, Color.DarkGray)
                        .background(Color(0xFFEDEDED)),
                )
            } else {
                val letter = Letter.fromHebrew(c)
                if (letter != null) {
                    Image(
                        painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                        contentDescription = c.toString(),
                        modifier = Modifier.padding(2.dp).size(44.dp),
                    )
                }
            }
        }
    }
}

/** A single tappable letter tile rendered with the real original tile artwork (see
 * [LetterTileArt]), so every letter-entry surface in the app looks consistent with the
 * board/rack tiles instead of a generic keyboard button. */
@Composable
private fun LetterTileButton(letter: Letter, onClick: () -> Unit) {
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
            .size(44.dp)
            .pointerInput(letter) {
                detectTapGestures { currentOnClick() }
            },
    )
}
