package il.cet.bonus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import il.cet.bonus.core.bonus.BonusOutcome
import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import il.cet.bonus.core.bonus.BonusType
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.ui.board.AnagramLetterPicker
import il.cet.bonus.ui.board.HebrewLetterPicker
import il.cet.bonus.ui.board.ThreeWordsStaircase
import il.cet.bonus.ui.board.TwoWordsCross
import il.cet.bonus.ui.board.WordDisplayTiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Dedicated full-screen bonus mini-game (one of the 5 confirmed original puzzle types -
 * see plan.md "Verified original-game data" / "Confirmed via real gameplay video"). Per
 * explicit product decision, only the *puzzle* bonuses get this dedicated screen; the
 * non-puzzle prizes (flat point awards, extra-turn, score multipliers) stay as the
 * existing lightweight celebration popup in BoardScreen (see GameViewModel.applyBonusPrize
 * / BoardScreen's bonus AlertDialog) since they need no player interaction at all.
 *
 * This used to be a modal Dialog capped at 620dp wide, which wasn't tall enough to show
 * the puzzle plus the full 22-letter on-screen alphabet without an inner scroll - a
 * dedicated full-screen surface (this composable is rendered as an overlay inside
 * BoardScreen's root fillMaxSize Box) gives it the whole screen instead, so nothing needs
 * to be scrolled or shrunk to fit.
 *
 * Fixed time/score for the two shared-letter types match the recovered original values
 * (20s/40pts, 30s/100pts); the crossword-build ("בונוס שבץ-נא אישי") mini-game's empty
 * 5x10 mini-board, 16-letter rack, 60s timer, and all-or-nothing "sum of built word
 * scores" bonus are confirmed against Hebrew Wikipedia "בונוס (משחק מחשב)". The anagram
 * bonus score now scales with word length (30-100) per the same source; fill-in-blank had
 * confirmed *mechanics* but no recovered exact time/score formula, so a placeholder is used.
 */
@Composable
fun BonusMiniGameScreen(
    type: BonusType,
    generator: BonusPuzzleGenerator,
    dictionary: DictionaryRepository,
    onFinished: (BonusOutcome) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf("") }
    var secondsLeft by remember {
        mutableStateOf(
            when (type) {
                BonusType.SHARED_LETTER_TWO_WORDS -> 20
                BonusType.SHARED_LETTER_THREE_WORDS -> 30
                BonusType.CROSSWORD_BUILD -> 60 // confirmed from gameplay footage
                else -> 45 // placeholder for dynamic types, see class doc
            }
        )
    }
    var finished by remember { mutableStateOf(false) }

    val fixedScore = when (type) {
        BonusType.SHARED_LETTER_TWO_WORDS -> 40
        BonusType.SHARED_LETTER_THREE_WORDS -> 100
        else -> 30 // placeholder for FILL_IN_BLANK; ANAGRAM uses generator.anagramScore(...) instead
    }

    val anagram = remember { if (type == BonusType.ANAGRAM) generator.generateAnagram(4) else null }
    val fillInBlank = remember { if (type == BonusType.FILL_IN_BLANK) generator.generateFillInBlank(5) else null }
    val sharedLetter = remember {
        when (type) {
            // Word lengths match the C# reference exactly: TwoCross always uses 5-letter
            // words (WordList5Letters), ThreeBy3 always uses 3-letter words
            // (WordList3Letters) - see BonusForm.cs InitBonusTiles.
            BonusType.SHARED_LETTER_TWO_WORDS -> generator.generateSharedLetter(2, 5)
            BonusType.SHARED_LETTER_THREE_WORDS -> generator.generateSharedLetter(3, 3)
            else -> null
        }
    }
    val crossword = remember { if (type == BonusType.CROSSWORD_BUILD) generator.generateCrosswordBonus() else null }
    var crosswordPlacements by remember { mutableStateOf<Map<Position, Tile>>(emptyMap()) }
    var crosswordRack by remember { mutableStateOf(crossword?.rackLetters ?: emptyList()) }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }

    // When the player answers a word-guessing bonus (anagram / fill-in-blank / shared-letter)
    // incorrectly, we don't finish immediately with 0 points - instead we reveal the correct
    // word(s) and wait for the player to acknowledge ("אישור") before actually finishing.
    var wrongReveal by remember { mutableStateOf<String?>(null) }
    var pendingWrongScore by remember { mutableStateOf(0) }

    fun finish(outcome: BonusOutcome) {
        if (!finished) {
            finished = true
            onFinished(outcome)
        }
    }

    // Shared "אישור" (confirm) handler for every puzzle type - extracted so it can be
    // invoked from a small button placed *beside* the on-screen keyboard (see `actions`
    // param on HebrewLetterPicker/AnagramLetterPicker) instead of on its own row below
    // it, freeing up a full row of vertical space on a phone screen.
    fun onConfirm() {
        val correct = when (type) {
            BonusType.ANAGRAM -> anagram != null && generator.checkAnagram(anagram, answer, dictionary)
            BonusType.FILL_IN_BLANK -> fillInBlank != null &&
                generator.checkFillInBlank(fillInBlank, answer.toList())
            BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS ->
                sharedLetter != null && answer.length == 1 &&
                    generator.checkSharedLetter(sharedLetter, answer[0])
            BonusType.CROSSWORD_BUILD -> true // scored separately below
        }
        val score = when {
            type == BonusType.CROSSWORD_BUILD -> generator.scoreCrosswordBonus(crosswordPlacements, dictionary)
            type == BonusType.ANAGRAM -> if (correct) generator.anagramScore(anagram?.answer?.length ?: 0) else 0
            correct -> fixedScore
            else -> 0
        }
        if (!correct && type != BonusType.CROSSWORD_BUILD) {
            // Reveal the correct word(s) instead of closing immediately.
            val correctWords = when (type) {
                BonusType.ANAGRAM -> anagram?.answer
                BonusType.FILL_IN_BLANK -> fillInBlank?.answer
                BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS ->
                    sharedLetter?.words?.joinToString(", ")
                else -> null
            }
            if (correctWords != null) {
                pendingWrongScore = score
                wrongReveal = correctWords
                return
            }
        }
        finish(BonusOutcome(type, score))
    }

    // A bonus mini-game must be played (or time out) - swallow the system back button
    // instead of letting it pop the whole board screen, matching the old Dialog's
    // non-dismissable behavior (onDismissRequest was already a no-op).
    BackHandler(enabled = true) {}

    DisposableEffect(Unit) {
        val job = scope.launch {
            while (secondsLeft > 0 && !finished && wrongReveal == null) {
                delay(1000)
                secondsLeft--
            }
            if (!finished && wrongReveal == null && secondsLeft <= 0) {
                if (type == BonusType.CROSSWORD_BUILD) {
                    finish(BonusOutcome(type, generator.scoreCrosswordBonus(crosswordPlacements, dictionary)))
                } else {
                    // Timed out without a correct answer: reveal the correct word(s) and
                    // wait for the player to acknowledge before actually finishing with 0.
                    val correctWords = when (type) {
                        BonusType.ANAGRAM -> anagram?.answer
                        BonusType.FILL_IN_BLANK -> fillInBlank?.answer
                        BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS ->
                            sharedLetter?.words?.joinToString(", ")
                        else -> null
                    }
                    if (correctWords != null) {
                        pendingWrongScore = 0
                        wrongReveal = correctWords
                    } else {
                        finish(BonusOutcome(type, 0))
                    }
                }
            }
        }
        onDispose { job.cancel() }
    }

    // Full-screen dedicated surface (see class doc) instead of a size-capped modal
    // Dialog - rendered as an overlay inside BoardScreen's root Box, so it covers the
    // whole screen exactly like a real destination would, without needing Navigation.
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val reveal = wrongReveal
            if (reveal != null) {
                // Wrong/timed-out answer: show the correct word(s) and wait for the
                // player to acknowledge before actually closing with 0 points.
                Text("בונוס!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("לא נכון. התשובה הנכונה הייתה:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = reveal, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { finish(BonusOutcome(type, pendingWrongScore)) }) {
                        Text("אישור")
                    }
                }
                return@Column
            }
            Text("בונוס! ($secondsLeft שניות)", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                // fill = true (not false) so this middle section is bounded to exactly
                // the remaining vertical space between the header and the bottom
                // action row - otherwise its natural (unbounded) height could exceed
                // the screen and require scrolling to see the last alphabet row.
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (type) {
                BonusType.ANAGRAM -> {
                    // Build-the-word row: shows the answer as it's typed (letters
                    // fill left-to-right), separate from the scrambled source-letter
                    // pool below - mirrors the C# reference's drag-into-slot behavior
                    // instead of only showing the typed answer as floating text.
                    Text("הרכב מילה מהאותיות:")
                    WordDisplayTiles(
                        displayed = "_".repeat(anagram?.answer?.length ?: 0),
                        filledAnswer = answer,
                    )
                    AnagramLetterPicker(
                        availableLetters = anagram?.scrambledLetters ?: emptyList(),
                        value = answer,
                        onValueChange = { answer = it },
                        showValuePreview = false,
                        actions = { ConfirmButton(onClick = ::onConfirm) },
                    )
                }
                BonusType.FILL_IN_BLANK -> {
                    Text("השלם את המילה:")
                    WordDisplayTiles(
                        displayed = fillInBlank?.displayed ?: "",
                        filledAnswer = answer,
                    )
                    HebrewLetterPicker(
                        value = answer,
                        onValueChange = { answer = it },
                        showActions = false,
                        showValuePreview = false,
                        // Cap input to the number of blanks - otherwise extra taps
                        // would silently break the exact-length match in
                        // checkFillInBlank even though the display looks full.
                        maxLength = fillInBlank?.blankIndices?.size,
                        actions = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { if (answer.isNotEmpty()) answer = answer.dropLast(1) }) { Text("מחק") }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(onClick = { answer = "" }) { Text("נקה") }
                                Spacer(modifier = Modifier.height(4.dp))
                                ConfirmButton(onClick = ::onConfirm)
                            }
                        },
                    )
                }
                BonusType.SHARED_LETTER_TWO_WORDS -> {
                    // "שתי וערב": two 5-letter words crossing at their shared middle
                    // letter, rendered as a "+" - matching the C# TwoCross layout.
                    Text("מהי האות המשותפת החסרה?")
                    sharedLetter?.let { TwoWordsCross(it, filledLetter = answer.firstOrNull()) }
                    HebrewLetterPicker(
                        value = answer,
                        onValueChange = { answer = it },
                        maxLength = 1,
                        showValuePreview = false,
                        actions = { ConfirmButton(onClick = ::onConfirm) },
                    )
                }
                BonusType.SHARED_LETTER_THREE_WORDS -> {
                    // "3 על 3": three 3-letter words stacked vertically, each row
                    // indented by 1 tile so the missing letter's column lines up for
                    // all 3 words - matching the C# ThreeBy3 layout.
                    Text("מהי האות המשותפת החסרה?")
                    sharedLetter?.let { ThreeWordsStaircase(it, filledLetter = answer.firstOrNull()) }
                    HebrewLetterPicker(
                        value = answer,
                        onValueChange = { answer = it },
                        maxLength = 1,
                        showValuePreview = false,
                        actions = { ConfirmButton(onClick = ::onConfirm) },
                    )
                }
                BonusType.CROSSWORD_BUILD -> {
                    // Confirmed real flow ("בונוס שבץ-נא אישי" - Hebrew Wikipedia "בונוס
                    // (משחק מחשב)"): an empty 5x10 mini-board, 16 freely-drawn rack
                    // letters (repeats allowed), 1 minute to build any words; bonus =
                    // sum of all formed word scores, but ONLY if every formed word is
                    // valid - otherwise the player gets 0 points.
                    Text("בנה כמה שיותר מילים באמצעות האותיות הנתונות:")
                    val rows = crossword?.rows ?: 5
                    val cols = crossword?.cols ?: 10
                    // Now a full-screen dedicated screen instead of a cramped popup -
                    // bump the grid/rack tiles up from the old 24dp/32dp so they're
                    // comfortably tappable with the extra room available.
                    val gridTileSize = 36.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(gridTileSize * rows),
                    ) {
                        items(rows * cols) { index ->
                            val row = index / cols
                            val col = index % cols
                            val pos = Position(row, col)
                            val placedTile = crosswordPlacements[pos]
                            Box(
                                modifier = Modifier
                                    .size(gridTileSize)
                                    .border(0.5.dp, Color.Gray)
                                    .background(Color(0xFFF0D9A0))
                                    .pointerInput(pos, selectedRackIndex, placedTile) {
                                        detectTapGestures {
                                            if (placedTile != null) {
                                                // Return this placement to the rack.
                                                crosswordRack = crosswordRack + (Letter.fromHebrew(placedTile.displayLetter) ?: return@detectTapGestures)
                                                crosswordPlacements = crosswordPlacements - pos
                                            } else {
                                                selectedRackIndex?.let { idx ->
                                                    val letter = crosswordRack.getOrNull(idx) ?: return@detectTapGestures
                                                    crosswordPlacements = crosswordPlacements + (pos to Tile.LetterTile(letter))
                                                    crosswordRack = crosswordRack.toMutableList().also { it.removeAt(idx) }
                                                    selectedRackIndex = null
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (placedTile != null) {
                                    Text(text = placedTile.displayLetter.toString(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        crosswordRack.forEachIndexed { index, letter ->
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(40.dp)
                                    .border(
                                        width = if (selectedRackIndex == index) 2.dp else 0.5.dp,
                                        color = if (selectedRackIndex == index) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    )
                                    .background(Color(0xFFF0D9A0))
                                    .pointerInput(index) {
                                        detectTapGestures {
                                            selectedRackIndex = if (selectedRackIndex == index) null else index
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = letter.hebrew.toString())
                            }
                        }
                    }
                }
            }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // For the 4 keyboard-based puzzles the "אישור" (confirm) button now lives
            // beside their on-screen keyboard (see `actions` param usage above) instead
            // of on a dedicated row here - this row is only needed for CROSSWORD_BUILD,
            // which has no keyboard of its own.
            if (type == BonusType.CROSSWORD_BUILD) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ConfirmButton(onClick = ::onConfirm)
                }
            }
        }
    }
}

/** Small "אישור" (confirm) button shared by all bonus mini-game answer surfaces. */
@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    Button(onClick = onClick) { Text("אישור") }
}
