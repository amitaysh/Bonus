package il.cet.bonus.shared.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import il.cet.bonus.core.bonus.BonusOutcome
import il.cet.bonus.core.bonus.BonusPuzzleGenerator
import il.cet.bonus.core.bonus.BonusType
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.game.ChallengeSystem
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.shared.ui.board.AnagramLetterPicker
import il.cet.bonus.shared.ui.board.BevelButton
import il.cet.bonus.shared.ui.board.LetterSideColumn
import il.cet.bonus.shared.ui.board.LetterTileArt
import il.cet.bonus.shared.ui.board.NoiseBackground
import il.cet.bonus.shared.ui.board.SingleLetterKeyboardRow
import il.cet.bonus.shared.ui.board.ThreeWordsStaircase
import il.cet.bonus.shared.ui.board.TwoWordsCross
import il.cet.bonus.shared.ui.board.WordDisplayTiles
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun BonusMiniGameScreen(
    type: BonusType,
    generator: BonusPuzzleGenerator,
    dictionary: DictionaryRepository,
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
                BonusType.CROSSWORD_BUILD -> 60
                else -> 45
            },
        )
    }
    var finished by remember { mutableStateOf(false) }
    val fixedScore = when (type) {
        BonusType.SHARED_LETTER_TWO_WORDS -> 40
        BonusType.SHARED_LETTER_THREE_WORDS -> 100
        else -> 30
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
            BonusType.SHARED_LETTER_TWO_WORDS -> generator.generateSharedLetter(2, 5)
            BonusType.SHARED_LETTER_THREE_WORDS -> generator.generateSharedLetter(3, 3)
            else -> null
        }
    }
    val crossword = remember { if (type == BonusType.CROSSWORD_BUILD) generator.generateCrosswordBonus() else null }
    var crosswordPlacements by remember { mutableStateOf<Map<Position, Tile>>(emptyMap()) }
    var crosswordRackSlots by remember { mutableStateOf<List<Letter?>>(crossword?.rackLetters ?: emptyList()) }
    var crosswordPlacementSource by remember { mutableStateOf<Map<Position, Int>>(emptyMap()) }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }
    val fillInBlankKeyboard = remember(fillInBlank) {
        fillInBlank?.let { puzzle ->
            val requiredLetters = puzzle.blankIndices.mapNotNull { idx -> Letter.fromHebrew(puzzle.answer[idx]) }
            generator.buildLetterKeyboard(requiredLetters)
        } ?: emptyList()
    }
    val sharedLetterKeyboard = remember(sharedLetter) {
        sharedLetter?.let { puzzle ->
            generator.buildLetterKeyboard(listOfNotNull(Letter.fromHebrew(puzzle.sharedLetter)))
        } ?: emptyList()
    }
    var wrongReveal by remember { mutableStateOf<String?>(null) }
    var pendingWrongScore by remember { mutableStateOf(0) }
    var crosswordSummary by remember { mutableStateOf<List<BonusPuzzleGenerator.CrosswordWordResult>?>(null) }
    var crosswordFinalScore by remember { mutableStateOf(0) }

    fun finish(outcome: BonusOutcome) {
        if (!finished) {
            finished = true
            onFinished(outcome)
        }
    }

    fun onConfirm() {
        if (type == BonusType.CROSSWORD_BUILD) {
            val results = generator.crosswordWordResults(crosswordPlacements, dictionary)
            crosswordFinalScore = if (results.isNotEmpty() && results.all { it.valid }) results.sumOf { it.score } else 0
            crosswordSummary = results
            return
        }
        val correct = when (type) {
            BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> anagram != null && generator.checkAnagram(anagram, answer, dictionary)
            BonusType.FILL_IN_BLANK -> fillInBlank != null && generator.checkFillInBlank(fillInBlank, answer.toList(), dictionary)
            BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS -> sharedLetter != null && answer.length == 1 && generator.checkSharedLetter(sharedLetter, answer[0], dictionary)
            BonusType.CROSSWORD_BUILD -> true
        }
        val score = when {
            type in listOf(BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7) -> if (correct) generator.anagramScore(anagram?.answer?.length ?: 0) else 0
            correct -> fixedScore
            else -> 0
        }
        if (!correct) {
            val correctWords = when (type) {
                BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> anagram?.answer
                BonusType.FILL_IN_BLANK -> fillInBlank?.answer
                BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS -> sharedLetter?.words?.joinToString(", ")
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
                    val correctWords = when (type) {
                        BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> anagram?.answer
                        BonusType.FILL_IN_BLANK -> fillInBlank?.answer
                        BonusType.SHARED_LETTER_TWO_WORDS, BonusType.SHARED_LETTER_THREE_WORDS -> sharedLetter?.words?.joinToString(", ")
                        else -> null
                    }
                    if (correctWords != null) {
                        pendingWrongScore = 0
                        wrongReveal = correctWords
                    } else finish(BonusOutcome(type, 0))
                }
            }
        }
        onDispose { job.cancel() }
    }

    NoiseBackground {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                val reveal = wrongReveal
                if (reveal != null) {
                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xCC1B1B1B), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text("בונוס!", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("לא נכון. התשובה הנכונה הייתה:", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(reveal, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            BevelButton(text = "אישור", onClick = { finish(BonusOutcome(type, pendingWrongScore)) })
                        }
                    }
                    return@Column
                }
                crosswordSummary?.let { summary ->
                    Column(modifier = Modifier.fillMaxWidth().weight(1f, true).background(Color(0xCC1B1B1B), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text("בונוס!", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE.replace("%s", playerName).replace("%d", crosswordFinalScore.toString()), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            BevelButton(text = "המשך", onClick = { finish(BonusOutcome(type, crosswordFinalScore)) })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (summary.isEmpty()) {
                            Text("לא נבנו מילים.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().weight(1f, true).verticalScroll(rememberScrollState())) {
                                summary.forEach { result ->
                                    Row(modifier = Modifier.fillMaxWidth().background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    val rows = crossword?.rows ?: 5
                    val cols = crossword?.cols ?: 10
                    val density = LocalDensity.current
                    val cellBoundsMap = remember { mutableMapOf<Position, Rect>() }
                    val rackBoundsMap = remember { mutableMapOf<Int, Rect>() }
                    var draggingRackIndex by remember { mutableStateOf<Int?>(null) }
                    var draggingFromPos by remember { mutableStateOf<Position?>(null) }
                    var dragOffset by remember { mutableStateOf(Offset.Zero) }
                    var containerRootTopLeft by remember { mutableStateOf(Offset.Zero) }

                    fun placeAt(pos: Position) {
                        val placedTile = crosswordPlacements[pos]
                        if (placedTile != null) {
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

                    fun dropRackTileAt(idx: Int, pos: Position) {
                        if (crosswordPlacements[pos] != null) return
                        val letter = crosswordRackSlots.getOrNull(idx) ?: return
                        crosswordPlacements = crosswordPlacements + (pos to Tile.LetterTile(letter))
                        crosswordPlacementSource = crosswordPlacementSource + (pos to idx)
                        crosswordRackSlots = crosswordRackSlots.toMutableList().also { it[idx] = null }
                        if (selectedRackIndex == idx) selectedRackIndex = null
                    }

                    fun movePlacedTile(from: Position, to: Position) {
                        if (from == to) return
                        val tile = crosswordPlacements[from] ?: return
                        if (crosswordPlacements[to] != null) return
                        val sourceIdx = crosswordPlacementSource[from] ?: return
                        crosswordPlacements = crosswordPlacements - from + (to to tile)
                        crosswordPlacementSource = crosswordPlacementSource - from + (to to sourceIdx)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PuzzleLabel("בונוס - בנה מילים! ($secondsLeft שניות)", style = MaterialTheme.typography.titleMedium)
                        ConfirmButton(onClick = ::onConfirm)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f, true).onGloballyPositioned { coords -> containerRootTopLeft = coords.boundsInRoot().topLeft }) {
                        val tileSize = minOf(maxWidth / (cols + 2), maxHeight / (rows + 2))
                        @Composable
                        fun RackTile(index: Int) {
                            val letter = crosswordRackSlots.getOrNull(index)
                            val halfTilePx = with(density) { (tileSize / 2).toPx() }
                            Box(
                                modifier = Modifier.size(tileSize).onGloballyPositioned { coords -> rackBoundsMap[index] = coords.boundsInRoot() }
                                    .then(if (letter != null) Modifier.border(width = if (selectedRackIndex == index) 2.dp else 0.dp, color = if (selectedRackIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent) else Modifier)
                                    .then(if (letter != null) Modifier.pointerInput(index, letter) { detectTapGestures { selectedRackIndex = if (selectedRackIndex == index) null else index } }.pointerInput(index, letter) {
                                        detectDragGestures(
                                            onDragStart = { draggingRackIndex = index; dragOffset = Offset.Zero },
                                            onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                            onDragEnd = {
                                                val origin = rackBoundsMap[index]?.topLeft ?: Offset.Zero
                                                val dropCenter = origin + dragOffset + Offset(halfTilePx, halfTilePx)
                                                val target = cellBoundsMap.entries.firstOrNull { it.value.contains(dropCenter) }
                                                if (target != null) dropRackTileAt(index, target.key)
                                                draggingRackIndex = null; dragOffset = Offset.Zero
                                            },
                                            onDragCancel = { draggingRackIndex = null; dragOffset = Offset.Zero },
                                        )
                                    } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (letter != null && draggingRackIndex != index) {
                                    Image(painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))), contentDescription = letter.hebrew.toString(), modifier = Modifier.fillMaxSize())
                                }
                            }
                        }

                        val rackSize = crosswordRackSlots.size
                        val topCount = minOf(5, rackSize)
                        val leftCount = minOf(3, rackSize - topCount)
                        val rightCount = minOf(3, rackSize - topCount - leftCount)
                        val topRange = 0 until topCount
                        val leftRange = topCount until (topCount + leftCount)
                        val rightRange = (topCount + leftCount) until (topCount + leftCount + rightCount)
                        val bottomRange = (topCount + leftCount + rightCount) until rackSize

                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Row(horizontalArrangement = Arrangement.Center) { topRange.forEach { RackTile(it) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column { leftRange.forEach { RackTile(it) } }
                                Box(modifier = Modifier.border(2.dp, Color(0xFF1A1A2E)).background(Color(0xFF1E5F82)).padding(2.dp)) {
                                    LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.height(tileSize * rows).width(tileSize * cols), userScrollEnabled = false) {
                                        items(rows * cols) { index ->
                                            val row = index / cols
                                            val col = index % cols
                                            val pos = Position(row, col)
                                            val placedTile = crosswordPlacements[pos]
                                            val halfTilePx = with(density) { (tileSize / 2).toPx() }
                                            Box(
                                                modifier = Modifier.size(tileSize).padding(1.dp).border(0.5.dp, Color(0xFF0D3A52)).background(Color(0xFF2D7DA8))
                                                    .onGloballyPositioned { coords -> cellBoundsMap[pos] = coords.boundsInRoot() }
                                                    .pointerInput(pos, selectedRackIndex, placedTile) { detectTapGestures { placeAt(pos) } }
                                                    .then(if (placedTile != null) Modifier.pointerInput(pos, placedTile) {
                                                        detectDragGestures(
                                                            onDragStart = { draggingFromPos = pos; dragOffset = Offset.Zero },
                                                            onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                                            onDragEnd = {
                                                                val origin = cellBoundsMap[pos]?.topLeft ?: Offset.Zero
                                                                val dropCenter = origin + dragOffset + Offset(halfTilePx, halfTilePx)
                                                                val target = cellBoundsMap.entries.firstOrNull { it.value.contains(dropCenter) }
                                                                if (target != null) movePlacedTile(pos, target.key)
                                                                draggingFromPos = null; dragOffset = Offset.Zero
                                                            },
                                                            onDragCancel = { draggingFromPos = null; dragOffset = Offset.Zero },
                                                        )
                                                    } else Modifier),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (placedTile != null && draggingFromPos != pos) {
                                                    Image(painter = painterResource(LetterTileArt.drawableFor(placedTile)), contentDescription = placedTile.displayLetter.toString(), modifier = Modifier.fillMaxSize())
                                                }
                                            }
                                        }
                                    }
                                }
                                Column { rightRange.forEach { RackTile(it) } }
                            }
                            Row(horizontalArrangement = Arrangement.Center) { bottomRange.forEach { RackTile(it) } }
                        }

                        draggingRackIndex?.let { idx ->
                            val letter = crosswordRackSlots.getOrNull(idx)
                            val origin = rackBoundsMap[idx]?.topLeft?.minus(containerRootTopLeft)
                            if (letter != null && origin != null) {
                                Box(modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).zIndex(10f).absoluteOffset { IntOffset((origin.x + dragOffset.x).roundToInt(), (origin.y + dragOffset.y).roundToInt()) }.size(tileSize)) {
                                    Image(painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))), contentDescription = letter.hebrew.toString(), modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        draggingFromPos?.let { fromPos ->
                            val tile = crosswordPlacements[fromPos]
                            val origin = cellBoundsMap[fromPos]?.topLeft?.minus(containerRootTopLeft)
                            if (tile != null && origin != null) {
                                Box(modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).zIndex(10f).absoluteOffset { IntOffset((origin.x + dragOffset.x).roundToInt(), (origin.y + dragOffset.y).roundToInt()) }.size(tileSize)) {
                                    Image(painter = painterResource(LetterTileArt.drawableFor(tile)), contentDescription = tile.displayLetter.toString(), modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    return@Column
                }

                PuzzleLabel("בונוס! ($secondsLeft שניות)", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth().weight(1f, true).verticalScroll(rememberScrollState())) {
                    when (type) {
                        BonusType.ANAGRAM, BonusType.ANAGRAM_5, BonusType.ANAGRAM_6, BonusType.ANAGRAM_7 -> {
                            PuzzleLabel("הרכב מילה מהאותיות:")
                            val middleLen = anagram?.scrambledLetters?.size ?: 0
                            WordDisplayTiles(displayed = (anagram?.lockedPrefix ?: "") + "_".repeat(middleLen) + (anagram?.lockedSuffix ?: ""), filledAnswer = answer)
                            AnagramLetterPicker(availableLetters = anagram?.scrambledLetters ?: emptyList(), value = answer, onValueChange = { answer = it }, showValuePreview = false, actions = { ConfirmButton(onClick = ::onConfirm) })
                        }
                        BonusType.FILL_IN_BLANK -> {
                            PuzzleLabel("השלם את המילה:")
                            WordDisplayTiles(displayed = fillInBlank?.displayed ?: "", filledAnswer = answer)
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleLetterKeyboardRow(letters = fillInBlankKeyboard, tileSize = 34.dp) { letter ->
                                val next = answer + letter.hebrew
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
                            PuzzleLabel("מהי האות המשותפת החסרה?")
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                sharedLetter?.let { ThreeWordsStaircase(it, filledLetter = answer.firstOrNull()) }
                                Spacer(modifier = Modifier.height(8.dp))
                                SingleLetterKeyboardRow(letters = sharedLetterKeyboard, tileSize = 34.dp) { letter -> answer = letter.hebrew.toString() }
                                Spacer(modifier = Modifier.height(8.dp))
                                ConfirmButton(onClick = ::onConfirm)
                            }
                        }
                        BonusType.CROSSWORD_BUILD -> Unit
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfirmButton(onClick: () -> Unit) = BevelButton(text = "אישור", onClick = onClick)

@Composable
private fun PuzzleLabel(text: String, style: TextStyle = MaterialTheme.typography.bodyLarge, modifier: Modifier = Modifier) {
    Text(text = text, style = style, color = Color.White, fontWeight = FontWeight.Bold, modifier = modifier.background(Color(0xAA1B1B1B), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}
