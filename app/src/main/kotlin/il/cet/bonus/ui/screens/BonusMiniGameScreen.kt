package il.cet.bonus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import il.cet.bonus.ui.board.WordDisplayTiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Overlay for one of the 5 confirmed original bonus mini-games (see plan.md
 * "Verified original-game data" / "Confirmed via real gameplay video"). Fixed time/score
 * for the two shared-letter types match the recovered original values (20s/40pts,
 * 30s/100pts); the crossword-build ("בונוס שבץ-נא אישי") mini-game's empty 5x10
 * mini-board, 16-letter rack, 60s timer, and all-or-nothing "sum of built word scores"
 * bonus are confirmed against Hebrew Wikipedia "בונוס (משחק מחשב)". The anagram bonus
 * score now scales with word length (30-100) per the same source; fill-in-blank had
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
            BonusType.SHARED_LETTER_TWO_WORDS -> generator.generateSharedLetter(2, 4)
            BonusType.SHARED_LETTER_THREE_WORDS -> generator.generateSharedLetter(3, 4)
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

    // Plain Dialog + Surface (instead of AlertDialog) because Material3's AlertDialog
    // Surface has a hardcoded ~560dp max-width cap that ignores usePlatformDefaultWidth =
    // false, which is too narrow to show all 22 Hebrew letters in 2 rows of 11 (see the
    // same fix applied to the שאילתא dialog in BoardScreen.kt).
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* must play or time out */ },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxHeight(0.9f).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                val reveal = wrongReveal
                if (reveal != null) {
                    // Wrong/timed-out answer: show the correct word(s) and wait for the
                    // player to acknowledge before actually closing with 0 points.
                    Text("בונוס!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("לא נכון. התשובה הנכונה הייתה:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = reveal, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { finish(BonusOutcome(type, pendingWrongScore)) }) {
                            Text("אישור")
                        }
                    }
                    return@Surface
                }
                Text("בונוס! ($secondsLeft שניות)", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (type) {
                    BonusType.ANAGRAM -> {
                        Text("הרכב מילה מהאותיות:")
                        WordDisplayTiles(anagram?.scrambledLetters?.joinToString("") ?: "")
                        AnagramLetterPicker(
                            availableLetters = anagram?.scrambledLetters ?: emptyList(),
                            value = answer,
                            onValueChange = { answer = it },
                        )
                    }
                    BonusType.FILL_IN_BLANK -> {
                        Text("השלם את המילה:")
                        WordDisplayTiles(fillInBlank?.displayed ?: "")
                        HebrewLetterPicker(value = answer, onValueChange = { answer = it }, showActions = false)
                    }
                    BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS -> {
                        Text("מהי האות המשותפת החסרה?")
                        sharedLetter?.words?.forEachIndexed { i, w ->
                            val idx = sharedLetter.sharedIndexPerWord[i]
                            val masked = w.mapIndexed { ci, c -> if (ci == idx) '_' else c }.joinToString("")
                            WordDisplayTiles(masked)
                        }
                        HebrewLetterPicker(value = answer, onValueChange = { answer = it }, maxLength = 1)
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height((rows * 24).dp),
                        ) {
                            items(rows * cols) { index ->
                                val row = index / cols
                                val col = index % cols
                                val pos = Position(row, col)
                                val placedTile = crosswordPlacements[pos]
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
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
                                        Text(text = placedTile.displayLetter.toString(), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            crosswordRack.forEachIndexed { index, letter ->
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(32.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Delete/clear buttons for FILL_IN_BLANK live here, outside the scrollable
                    // content, so they're always visible regardless of how tall the letter
                    // grid above is.
                    if (type == BonusType.FILL_IN_BLANK) {
                        Row {
                            Button(onClick = { if (answer.isNotEmpty()) answer = answer.dropLast(1) }) { Text("מחק") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { answer = "" }) { Text("נקה") }
                        }
                    } else {
                        Spacer(modifier = Modifier)
                    }
                    Button(onClick = {
                        val correct = when (type) {
                            BonusType.ANAGRAM -> anagram != null && generator.checkAnagram(anagram, answer)
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
                                return@Button
                            }
                        }
                        finish(BonusOutcome(type, score))
                    }) {
                        Text("אישור")
                    }
                }
            }
        }
    }
}

