package il.cet.bonus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import il.cet.bonus.core.bonus.BonusOutcome
import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import il.cet.bonus.core.bonus.BonusType
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.ui.board.AnagramLetterPicker
import il.cet.bonus.ui.board.BevelButton
import il.cet.bonus.ui.board.HebrewLetterPicker
import il.cet.bonus.ui.board.LetterSideColumn
import il.cet.bonus.ui.board.SingleLetterKeyboardRow
import il.cet.bonus.ui.board.ThreeWordsStaircase
import il.cet.bonus.ui.board.TwoWordsCross
import il.cet.bonus.ui.board.WordDisplayTiles
import kotlin.math.roundToInt
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
    // The triggering player's name, used only by the crossword-build ("free word
    // building") completion summary popup for its "X, אתה מקבל Y נקודות." line (matching
    // the original game's results screen - see free_sum.png reference).
    playerName: String = "",
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

    val anagram = remember {
        when (type) {
            BonusType.ANAGRAM -> generator.generateAnagram(4)
            BonusType.ANAGRAM_5 -> generator.generateAnagram(5)
            BonusType.ANAGRAM_6 -> generator.generateAnagram(6, lockEnds = true)
            BonusType.ANAGRAM_7 -> generator.generateAnagram(7, lockEnds = true)
            else -> null
        }
    }
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
    // Fixed-size rack: index = the letter's original position (matching the original
    // game's layout, see free_bonus.png). A `null` slot means that letter is currently
    // placed on the board - it reappears in its OWN original slot when picked back up,
    // instead of being appended to the end, so the rack never reorders itself.
    var crosswordRackSlots by remember { mutableStateOf<List<Letter?>>(crossword?.rackLetters ?: emptyList()) }
    // Maps each placed board position back to the rack slot index it came from, so
    // returning a tile to the rack restores it to its original position.
    var crosswordPlacementSource by remember { mutableStateOf<Map<Position, Int>>(emptyMap()) }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }

    // Limited 10-12 letter keyboard for the "letter completion" puzzles (fill-in-blank /
    // shared-letter), always including the correct letter(s) - per product decision, the
    // full 22-letter alphabet should NOT be shown for these, matching the original game's
    // puzzle screens (see TODO.md #1 / bonus_letters.png reference).
    val fillInBlankKeyboard = remember(fillInBlank) {
        fillInBlank?.let { puzzle ->
            val requiredLetters = puzzle.blankIndices.mapNotNull { idx -> Letter.fromHebrew(puzzle.answer[idx]) }
            generator.buildLetterKeyboard(requiredLetters)
        } ?: emptyList()
    }
    val sharedLetterKeyboard = remember(sharedLetter) {
        sharedLetter?.let { puzzle ->
            val required = Letter.fromHebrew(puzzle.sharedLetter)
            generator.buildLetterKeyboard(listOfNotNull(required))
        } ?: emptyList()
    }

    // When the player answers a word-guessing bonus (anagram / fill-in-blank / shared-letter)
    // incorrectly, we don't finish immediately with 0 points - instead we reveal the correct
    // word(s) and wait for the player to acknowledge ("אישור") before actually finishing.
    var wrongReveal by remember { mutableStateOf<String?>(null) }
    var pendingWrongScore by remember { mutableStateOf(0) }

    // Crossword-build ("free word building") completion summary: lists every word the
    // player formed with a checkmark/cross for valid/invalid, plus the final awarded
    // score - shown instead of finishing immediately, matching the original game's
    // results popup (see free_sum.png reference).
    var crosswordSummary by remember { mutableStateOf<List<BonusPuzzleGenerator.CrosswordWordResult>?>(null) }
    var crosswordFinalScore by remember { mutableStateOf(0) }

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
        if (type == BonusType.CROSSWORD_BUILD) {
            val results = generator.crosswordWordResults(crosswordPlacements, dictionary)
            crosswordFinalScore = if (results.isNotEmpty() && results.all { it.valid }) results.sumOf { it.score } else 0
            crosswordSummary = results
            return
        }
        val correct = when (type) {
            BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 ->
                anagram != null && generator.checkAnagram(anagram, answer, dictionary)
            BonusType.FILL_IN_BLANK -> fillInBlank != null &&
                generator.checkFillInBlank(fillInBlank, answer.toList(), dictionary)
            BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS ->
                sharedLetter != null && answer.length == 1 &&
                    generator.checkSharedLetter(sharedLetter, answer[0], dictionary)
            BonusType.CROSSWORD_BUILD -> true // handled above
        }
        val score = when {
            type == BonusType.ANAGRAM || type == BonusType.ANAGRAM_5 ||
                type == BonusType.ANAGRAM_6 || type == BonusType.ANAGRAM_7 ->
                if (correct) generator.anagramScore(anagram?.answer?.length ?: 0) else 0
            correct -> fixedScore
            else -> 0
        }
        if (!correct) {
            // Reveal the correct word(s) instead of closing immediately.
            val correctWords = when (type) {
                BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> anagram?.answer
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
            while (secondsLeft > 0 && !finished && wrongReveal == null && crosswordSummary == null) {
                delay(1000)
                secondsLeft--
            }
            if (!finished && wrongReveal == null && crosswordSummary == null && secondsLeft <= 0) {
                if (type == BonusType.CROSSWORD_BUILD) {
                    val results = generator.crosswordWordResults(crosswordPlacements, dictionary)
                    crosswordFinalScore = if (results.isNotEmpty() && results.all { it.valid }) results.sumOf { it.score } else 0
                    crosswordSummary = results
                } else {
                    // Timed out without a correct answer: reveal the correct word(s) and
                    // wait for the player to acknowledge before actually finishing with 0.
                    val correctWords = when (type) {
                        BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> anagram?.answer
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
    // Backed by the same blue-noise texture as the rest of the app for visual consistency.
    il.cet.bonus.ui.board.NoiseBackground {
    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val reveal = wrongReveal
            if (reveal != null) {
                // Wrong/timed-out answer: show the correct word(s) and wait for the
                // player to acknowledge before actually closing with 0 points.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC1B1B1B), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text("בונוס!", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("לא נכון. התשובה הנכונה הייתה:", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = reveal, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BevelButton(text = "אישור", onClick = { finish(BonusOutcome(type, pendingWrongScore)) })
                    }
                }
                return@Column
            }
            val summary = crosswordSummary
            if (summary != null) {
                // Crossword-build ("free word building") completion summary: lists every
                // formed word with its score and a check/cross for valid/invalid, then
                // the final awarded score line - matches the original game's results
                // popup (see free_sum.png reference). The total score + confirm button
                // are placed at the TOP (not after the word list) and the word list is
                // independently scrollable, so with many words the button is always
                // visible without needing to scroll to the bottom. The whole popup sits
                // on a dark semi-transparent panel so it stays legible over the noise
                // background texture.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .background(Color(0xCC1B1B1B), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text("בונוס!", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = il.cet.bonus.core.game.ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE.format(playerName, crosswordFinalScore),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BevelButton(text = "המשך", onClick = { finish(BonusOutcome(type, crosswordFinalScore)) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (summary.isEmpty()) {
                        Text("לא נבנו מילים.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = true)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            summary.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(if (result.valid) "✔ ${result.word}" else "✘ ${result.word}", color = if (result.valid) Color(0xFF69F0AE) else Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                    Text(result.score.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
                return@Column
            }
            if (type == BonusType.CROSSWORD_BUILD) {
                // Dedicated compact full-screen layout (no scrolling): a thin header
                // row (title + timer + confirm button) on top, then the mini-board
                // maximized to fill all remaining space, with the rack tiles arranged
                // tightly around it. Tile sizes are computed from the available space
                // (via BoxWithConstraints) so everything always fits on one screen.
                val rows = crossword?.rows ?: 5
                val cols = crossword?.cols ?: 10
                val density = LocalDensity.current
                // Maps each on-screen rack-tile / grid-cell to its root-coordinate bounds
                // (for both kinds of drag-and-drop: rack->grid, and grid->grid), and
                // tracks which rack tile / which grid cell is currently being dragged
                // (mirroring BoardScreen's overlay-based drag pattern - see its
                // BoardGrid comments for why a simple graphicsLayer/zIndex on the cell
                // itself isn't enough to escape sibling-row occlusion).
                val cellBoundsMap = remember { mutableMapOf<Position, Rect>() }
                val rackBoundsMap = remember { mutableMapOf<Int, Rect>() }
                var draggingRackIndex by remember { mutableStateOf<Int?>(null) }
                var draggingFromPos by remember { mutableStateOf<Position?>(null) }
                var dragOffset by remember { mutableStateOf(Offset.Zero) }
                var containerRootTopLeft by remember { mutableStateOf(Offset.Zero) }

                fun placeAt(pos: Position) {
                    val placedTile = crosswordPlacements[pos]
                    if (placedTile != null) {
                        // Return this placement to the rack SLOT it originally came
                        // from (not appended to the end) so the rack never reorders.
                        val sourceIdx = crosswordPlacementSource[pos] ?: return
                        val letter = Letter.fromHebrew(placedTile.displayLetter) ?: return
                        crosswordRackSlots = crosswordRackSlots.toMutableList().also { it[sourceIdx] = letter }
                        crosswordPlacements = crosswordPlacements - pos
                        crosswordPlacementSource = crosswordPlacementSource - pos
                    } else {
                        selectedRackIndex?.let { idx ->
                            val letter = crosswordRackSlots.getOrNull(idx) ?: return@let
                            crosswordPlacements = crosswordPlacements + (pos to Tile.LetterTile(letter))
                            crosswordPlacementSource = crosswordPlacementSource + (pos to idx)
                            crosswordRackSlots = crosswordRackSlots.toMutableList().also { it[idx] = null }
                            selectedRackIndex = null
                        }
                    }
                }

                // Drops a rack tile (by its slot index) directly onto [pos] via drag
                // (as opposed to tap-to-select then tap-to-place via [placeAt]). No-op
                // if the target cell is already occupied.
                fun dropRackTileAt(idx: Int, pos: Position) {
                    if (crosswordPlacements[pos] != null) return
                    val letter = crosswordRackSlots.getOrNull(idx) ?: return
                    crosswordPlacements = crosswordPlacements + (pos to Tile.LetterTile(letter))
                    crosswordPlacementSource = crosswordPlacementSource + (pos to idx)
                    crosswordRackSlots = crosswordRackSlots.toMutableList().also { it[idx] = null }
                    if (selectedRackIndex == idx) selectedRackIndex = null
                }

                // Drags an already-placed tile directly from one grid cell to another,
                // without returning it to the rack first (matching the main board's
                // movePendingTile behavior - see TODO.md item about grid drag-and-drop).
                fun movePlacedTile(from: Position, to: Position) {
                    if (from == to) return
                    val tile = crosswordPlacements[from] ?: return
                    if (crosswordPlacements[to] != null) return
                    val sourceIdx = crosswordPlacementSource[from] ?: return
                    crosswordPlacements = crosswordPlacements - from + (to to tile)
                    crosswordPlacementSource = crosswordPlacementSource - from + (to to sourceIdx)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PuzzleLabel(
                        text = "בונוס - בנה מילים! ($secondsLeft שניות)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ConfirmButton(onClick = ::onConfirm)
                }
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .onGloballyPositioned { coords -> containerRootTopLeft = coords.boundsInRoot().topLeft },
                ) {
                    // Reserve exactly 1 extra row/column of space on each side for the
                    // rack (top/bottom are a single row of tiles, left/right are a
                    // single column of tiles), then split the min of the width- and
                    // height-derived tile sizes so the whole layout always fits
                    // without scrolling, however small the available space gets.
                    val tileSize = minOf(maxWidth / (cols + 2), maxHeight / (rows + 2))

                    @Composable
                    fun RackTile(index: Int) {
                        val letter = crosswordRackSlots.getOrNull(index)
                        val halfTilePx = with(density) { (tileSize / 2).toPx() }
                        Box(
                            modifier = Modifier
                                .size(tileSize)
                                .onGloballyPositioned { coords -> rackBoundsMap[index] = coords.boundsInRoot() }
                                .then(
                                    if (letter != null) {
                                        Modifier.border(
                                            width = if (selectedRackIndex == index) 2.dp else 0.dp,
                                            color = if (selectedRackIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (letter != null) {
                                        Modifier
                                            .pointerInput(index, letter) {
                                                detectTapGestures {
                                                    selectedRackIndex = if (selectedRackIndex == index) null else index
                                                }
                                            }
                                            .pointerInput(index, letter) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingRackIndex = index
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffset += amount
                                                    },
                                                    onDragEnd = {
                                                        val origin = rackBoundsMap[index]?.topLeft ?: Offset.Zero
                                                        val dropCenter = origin + dragOffset + Offset(halfTilePx, halfTilePx)
                                                        val target = cellBoundsMap.entries.firstOrNull { it.value.contains(dropCenter) }
                                                        if (target != null) dropRackTileAt(index, target.key)
                                                        draggingRackIndex = null
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDragCancel = {
                                                        draggingRackIndex = null
                                                        dragOffset = Offset.Zero
                                                    },
                                                )
                                            }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Skip drawing locally while being dragged - the floating
                            // overlay below renders it instead, so it isn't visually
                            // clipped/occluded by sibling rows/columns during the drag
                            // (same issue and fix as BoardScreen's BoardGrid overlay).
                            if (letter != null && draggingRackIndex != index) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        il.cet.bonus.ui.board.LetterTileArt.drawableFor(Tile.LetterTile(letter)),
                                    ),
                                    contentDescription = letter.hebrew.toString(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // Rack letters are arranged AROUND the mini-board in FIXED sections
                    // (top/left/right/bottom), matching the original game's layout (see
                    // free_bonus.png: 5 top, 3 left, 3 right, 5 bottom for the default
                    // 16-letter rack) - section boundaries are based on the rack's
                    // original slot indices, not on how many letters remain, so already-
                    // placed (now-empty) slots stay in place instead of collapsing.
                    val rackSize = crosswordRackSlots.size
                    val topCount = minOf(5, rackSize)
                    val leftCount = minOf(3, rackSize - topCount)
                    val rightCount = minOf(3, rackSize - topCount - leftCount)
                    val topRange = 0 until topCount
                    val leftRange = topCount until (topCount + leftCount)
                    val rightRange = (topCount + leftCount) until (topCount + leftCount + rightCount)
                    val bottomRange = (topCount + leftCount + rightCount) until rackSize

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            topRange.forEach { i -> RackTile(i) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                leftRange.forEach { i -> RackTile(i) }
                            }
                            // Bordered blue mini-crossword grid matching the original
                            // game's look (see free_bonus.png), with real jpg letter-tile
                            // artwork for filled cells instead of plain text.
                            Box(
                                modifier = Modifier
                                    .border(2.dp, Color(0xFF1A1A2E))
                                    .background(Color(0xFF1E5F82))
                                    .padding(2.dp),
                            ) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(cols),
                                    modifier = Modifier.height(tileSize * rows).width(tileSize * cols),
                                    userScrollEnabled = false,
                                ) {
                                    items(rows * cols) { index ->
                                        val row = index / cols
                                        val col = index % cols
                                        val pos = Position(row, col)
                                        val placedTile = crosswordPlacements[pos]
                                        val halfTilePx = with(density) { (tileSize / 2).toPx() }
                                        Box(
                                            modifier = Modifier
                                                .size(tileSize)
                                                .padding(1.dp)
                                                .border(0.5.dp, Color(0xFF0D3A52))
                                                .background(Color(0xFF2D7DA8))
                                                .onGloballyPositioned { coords -> cellBoundsMap[pos] = coords.boundsInRoot() }
                                                .pointerInput(pos, selectedRackIndex, placedTile) {
                                                    detectTapGestures { placeAt(pos) }
                                                }
                                                .then(
                                                    if (placedTile != null) {
                                                        Modifier.pointerInput(pos, placedTile) {
                                                            detectDragGestures(
                                                                onDragStart = {
                                                                    draggingFromPos = pos
                                                                    dragOffset = Offset.Zero
                                                                },
                                                                onDrag = { change, amount ->
                                                                    change.consume()
                                                                    dragOffset += amount
                                                                },
                                                                onDragEnd = {
                                                                    val origin = cellBoundsMap[pos]?.topLeft ?: Offset.Zero
                                                                    val dropCenter = origin + dragOffset + Offset(halfTilePx, halfTilePx)
                                                                    val target = cellBoundsMap.entries.firstOrNull { it.value.contains(dropCenter) }
                                                                    if (target != null) movePlacedTile(pos, target.key)
                                                                    draggingFromPos = null
                                                                    dragOffset = Offset.Zero
                                                                },
                                                                onDragCancel = {
                                                                    draggingFromPos = null
                                                                    dragOffset = Offset.Zero
                                                                },
                                                            )
                                                        }
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            // Skip drawing locally while being dragged - see the floating
                                            // overlay below (mirrors BoardScreen's BoardGrid drag-overlay fix).
                                            if (placedTile != null && draggingFromPos != pos) {
                                                androidx.compose.foundation.Image(
                                                    painter = androidx.compose.ui.res.painterResource(
                                                        il.cet.bonus.ui.board.LetterTileArt.drawableFor(placedTile),
                                                    ),
                                                    contentDescription = placedTile.displayLetter.toString(),
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Column {
                                rightRange.forEach { i -> RackTile(i) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.Center) {
                            bottomRange.forEach { i -> RackTile(i) }
                        }
                    }

                    // Floating overlay for a rack tile currently being drag-dropped onto
                    // the grid - rendered as the last child of this BoxWithConstraints
                    // (a sibling of the Column above), so its zIndex correctly places it
                    // above every rack tile and grid cell during the drag.
                    draggingRackIndex?.let { idx ->
                        val letter = crosswordRackSlots.getOrNull(idx)
                        val origin = rackBoundsMap[idx]?.topLeft?.minus(containerRootTopLeft)
                        if (letter != null && origin != null) {
                            Box(
                                // AbsoluteAlignment.TopLeft (not the parent's default
                                // TopStart): TopStart's un-offset anchor is physically
                                // mirrored under the app's global RTL setting, so combining
                                // it with our physically-top-left absoluteOffset math sent
                                // the tile flying off to the wrong side of the screen.
                                modifier = Modifier
                                    .align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
                                    .zIndex(10f)
                                    .absoluteOffset {
                                        IntOffset((origin.x + dragOffset.x).roundToInt(), (origin.y + dragOffset.y).roundToInt())
                                    }
                                    .size(tileSize),
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        il.cet.bonus.ui.board.LetterTileArt.drawableFor(Tile.LetterTile(letter)),
                                    ),
                                    contentDescription = letter.hebrew.toString(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // Same overlay pattern for a tile being dragged from one grid cell
                    // to another.
                    draggingFromPos?.let { fromPos ->
                        val tile = crosswordPlacements[fromPos]
                        val origin = cellBoundsMap[fromPos]?.topLeft?.minus(containerRootTopLeft)
                        if (tile != null && origin != null) {
                            Box(
                                modifier = Modifier
                                    .align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
                                    .zIndex(10f)
                                    .absoluteOffset {
                                        IntOffset((origin.x + dragOffset.x).roundToInt(), (origin.y + dragOffset.y).roundToInt())
                                    }
                                    .size(tileSize),
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        il.cet.bonus.ui.board.LetterTileArt.drawableFor(tile),
                                    ),
                                    contentDescription = tile.displayLetter.toString(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                return@Column
            }
            PuzzleLabel("בונוס! ($secondsLeft שניות)", style = MaterialTheme.typography.headlineMedium)
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
                BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> {
                    // Build-the-word row: shows the answer as it's typed (letters
                    // fill left-to-right), separate from the scrambled source-letter
                    // pool below - mirrors the C# reference's drag-into-slot behavior
                    // instead of only showing the typed answer as floating text.
                    // For 6/7-letter anagrams, the first/last letters are locked/shown
                    // as-is (not part of the blanks) - only the middle letters are typed.
                    PuzzleLabel("הרכב מילה מהאותיות:")
                    val middleLen = anagram?.scrambledLetters?.size ?: 0
                    WordDisplayTiles(
                        displayed = (anagram?.lockedPrefix ?: "") + "_".repeat(middleLen) + (anagram?.lockedSuffix ?: ""),
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
                    PuzzleLabel("השלם את המילה:")
                    WordDisplayTiles(
                        displayed = fillInBlank?.displayed ?: "",
                        filledAnswer = answer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Letter keyboard below the grid (not beside it) - per user
                    // feedback, only the two-word "cross" puzzle should split its
                    // keyboard across both sides; the single-word completion puzzle
                    // shows one plain 10-12 letter keyboard row/wrap underneath.
                    SingleLetterKeyboardRow(
                        letters = fillInBlankKeyboard,
                        tileSize = 34.dp,
                    ) { letter ->
                        val next = (answer + letter.hebrew)
                        answer = fillInBlank?.blankIndices?.size?.let { next.takeLast(it) } ?: next
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BevelButton(text = "מחק", onClick = { if (answer.isNotEmpty()) answer = answer.dropLast(1) })
                        BevelButton(text = "נקה", onClick = { answer = "" })
                        ConfirmButton(onClick = ::onConfirm)
                    }
                }
                BonusType.SHARED_LETTER_TWO_WORDS -> {
                    // "שתי וערב": two 5-letter words crossing at their shared middle
                    // letter, rendered as a "+" - matching the C# TwoCross layout.
                    PuzzleLabel("מהי האות המשותפת החסרה?")
                    val (leftLetters, rightLetters) = remember(sharedLetterKeyboard) {
                        sharedLetterKeyboard.chunked((sharedLetterKeyboard.size + 1) / 2).let { it.getOrElse(0) { emptyList() } to it.getOrElse(1) { emptyList() } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LetterSideColumn(letters = leftLetters, tileSize = 34.dp) { letter -> answer = letter.hebrew.toString() }
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            sharedLetter?.let { TwoWordsCross(it, filledLetter = answer.firstOrNull()) }
                            Spacer(modifier = Modifier.height(8.dp))
                            ConfirmButton(onClick = ::onConfirm)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        LetterSideColumn(letters = rightLetters, tileSize = 34.dp) { letter -> answer = letter.hebrew.toString() }
                    }
                }
                BonusType.SHARED_LETTER_THREE_WORDS -> {
                    // "3 על 3": three 3-letter words stacked vertically, each row
                    // indented by 1 tile so the missing letter's column lines up for
                    // all 3 words - matching the C# ThreeBy3 layout. Per user feedback,
                    // this puzzle (like FILL_IN_BLANK) uses a single flat keyboard below
                    // the grid, not split across both sides - only the 2-word cross
                    // puzzle uses that split layout.
                    PuzzleLabel("מהי האות המשותפת החסרה?")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        sharedLetter?.let { ThreeWordsStaircase(it, filledLetter = answer.firstOrNull()) }
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleLetterKeyboardRow(
                            letters = sharedLetterKeyboard,
                            tileSize = 34.dp,
                        ) { letter -> answer = letter.hebrew.toString() }
                        Spacer(modifier = Modifier.height(8.dp))
                        ConfirmButton(onClick = ::onConfirm)
                    }
                }
                // Handled separately above (dedicated full-screen layout, see the
                // `if (type == BonusType.CROSSWORD_BUILD)` branch earlier) - this
                // branch is unreachable at runtime but kept so `when` stays exhaustive.
                BonusType.CROSSWORD_BUILD -> {}
            }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // For the 4 keyboard-based puzzles the "אישור" (confirm) button now lives
            // beside their on-screen keyboard (see `actions` param usage above) instead
            // of on a dedicated row here.
        }
    }
    }
}

/** Small "אישור" (confirm) button shared by all bonus mini-game answer surfaces. */
@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    BevelButton(text = "אישור", onClick = onClick)
}

/**
 * Prompt/instruction text for the bonus mini-game screens, rendered on a dark
 * semi-transparent pill so it stays legible over the noisy background texture
 * (plain [Text] with no background was hard to read there).
 */
@Composable
private fun PuzzleLabel(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0xAA1B1B1B), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
