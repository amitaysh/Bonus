package il.cet.bonus.shared.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bonus.shared.generated.resources.Res
import bonus.shared.generated.resources.complete_turn
import bonus.shared.generated.resources.letter_table
import bonus.shared.generated.resources.menu_exit
import bonus.shared.generated.resources.moves_counter_label
import bonus.shared.generated.resources.query_word
import bonus.shared.generated.resources.tiles_remaining_label
import il.cet.bonus.core.board.Board
import il.cet.bonus.core.game.ChallengeSystem
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.shared.audio.SfxPlayer
import il.cet.bonus.shared.bonus.BonusWordBankLoader
import il.cet.bonus.shared.game.GameViewModel
import il.cet.bonus.shared.ui.board.BevelButton
import il.cet.bonus.shared.ui.board.BonusIconAssignment
import il.cet.bonus.shared.ui.board.HebrewLetterPicker
import il.cet.bonus.shared.ui.board.LcdDisplay
import il.cet.bonus.shared.ui.board.LetterTileArt
import il.cet.bonus.shared.ui.board.NoiseBackground
import il.cet.bonus.shared.ui.theme.GameTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private typealias BoardEdge = Board.Edge
private const val DEBUG_BONUS_BUTTON_ENABLED = false

@Composable
fun BoardScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    musicTheme: GameTheme,
    onToggleMusicTheme: () -> Unit,
) {
    val bonusGenerator = remember { BonusWordBankLoader.create() }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableMapOf<Position, Rect>() }
    var showQueryDialog by remember { mutableStateOf(false) }
    var showLetterTable by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<String?>(null) }
    var containerRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var rackDrag by remember { mutableStateOf<RackDragInfo?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { coords -> containerRootTopLeft = coords.boundsInRoot().topLeft },
    ) {
        NoiseBackground { }

        if (DEBUG_BONUS_BUTTON_ENABLED) {
            androidx.compose.material3.Button(
                onClick = { viewModel.debugTriggerNextBonus() },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).zIndex(10f),
            ) {
                Text("DEBUG בונוס")
            }
        }

        Row(modifier = Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(100.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LcdDisplay(value = viewModel.movesPlayed.toString().padStart(2, '0'), label = stringResource(Res.string.moves_counter_label))
                BevelButton(text = stringResource(Res.string.menu_exit), modifier = Modifier.fillMaxWidth(), onClick = { showExitConfirm = true })
                BevelButton(text = stringResource(Res.string.query_word), modifier = Modifier.fillMaxWidth(), onClick = { showQueryDialog = true })
                BevelButton(text = stringResource(Res.string.letter_table), modifier = Modifier.fillMaxWidth(), onClick = { showLetterTable = true })
                BevelButton(
                    text = if (musicTheme == GameTheme.OLD) "מוזיקה: ישנה" else "מוזיקה: חדשה",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onToggleMusicTheme,
                )
                BevelButton(text = stringResource(Res.string.complete_turn), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.completeTurn() })
                LcdDisplay(value = "${viewModel.tilesRemainingInBag}", label = stringResource(Res.string.tiles_remaining_label))
            }

            Column(modifier = Modifier.width(96.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerPanel(
                    name = viewModel.players[0].name,
                    score = viewModel.players[0].score,
                    isCurrent = viewModel.currentPlayerIndex == 0,
                    scoreMultiplier = viewModel.players[0].scoreMultiplier,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                VerticalRack(
                    rack = viewModel.racks.getOrElse(0) { emptyList() },
                    enabled = viewModel.currentPlayerIndex == 0,
                    selectedRackIndex = if (viewModel.currentPlayerIndex == 0) selectedRackIndex else null,
                    onSelect = { selectedRackIndex = if (selectedRackIndex == it) null else it },
                    viewModel = viewModel,
                    cellBounds = cellBounds,
                    playerIndex = 0,
                    rackDrag = rackDrag,
                    onRackDragChange = { rackDrag = it },
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                BoardGrid(viewModel, selectedRackIndex, { selectedRackIndex = null }, cellBounds)
            }

            Column(modifier = Modifier.width(96.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerPanel(
                    name = viewModel.players[1].name,
                    score = viewModel.players[1].score,
                    isCurrent = viewModel.currentPlayerIndex == 1,
                    scoreMultiplier = viewModel.players[1].scoreMultiplier,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                VerticalRack(
                    rack = viewModel.racks.getOrElse(1) { emptyList() },
                    enabled = viewModel.currentPlayerIndex == 1,
                    selectedRackIndex = if (viewModel.currentPlayerIndex == 1) selectedRackIndex else null,
                    onSelect = { selectedRackIndex = if (selectedRackIndex == it) null else it },
                    viewModel = viewModel,
                    cellBounds = cellBounds,
                    playerIndex = 1,
                    rackDrag = rackDrag,
                    onRackDragChange = { rackDrag = it },
                )
            }
        }

        rackDrag?.let { drag ->
            val originPx = drag.tileRootTopLeft.minus(containerRootTopLeft)
            Box(
                modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).zIndex(20f).absoluteOffset {
                    IntOffset((originPx.x + drag.dragOffset.x).roundToInt(), (originPx.y + drag.dragOffset.y).roundToInt())
                }.size(drag.tileSize),
            ) {
                Image(
                    painter = painterResource(LetterTileArt.drawableFor(drag.tile)),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFFCFFF04)),
                )
            }
        }
    }

    if (showQueryDialog) {
        var queryText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showQueryDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.widthIn(max = 620.dp).padding(16.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("שאילתא - בדיקת מילים", style = MaterialTheme.typography.headlineSmall)
                    queryResult?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        HebrewLetterPicker(value = queryText, onValueChange = { queryText = it }, showActions = false)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        BevelButton(text = stringResource(Res.string.menu_exit), onClick = { showQueryDialog = false; queryResult = null })
                        Spacer(modifier = Modifier.width(8.dp))
                        BevelButton(text = "בדוק", onClick = {
                            queryResult = if (viewModel.dictionary.isValidWord(queryText)) "המלה תקינה!" else "המלה אינה תקינה."
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        BevelButton(text = "נקה", onClick = { queryText = "" })
                        Spacer(modifier = Modifier.width(8.dp))
                        BevelButton(text = "מחק", onClick = { if (queryText.isNotEmpty()) queryText = queryText.dropLast(1) })
                    }
                }
            }
        }
    }

    if (showLetterTable) {
        Dialog(onDismissRequest = { showLetterTable = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(0.92f), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(maxWidth, maxHeight), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.letter_table), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        val letters = Letter.entries
                        val columns = 5
                        val rows = (letters.size + columns - 1) / columns
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val cellWidth = maxWidth / columns
                            val cellHeight = maxHeight / rows
                            val tileSize = minOf(cellWidth * 0.55f, cellHeight * 0.6f)
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                                letters.chunked(columns).forEach { rowLetters ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        rowLetters.forEach { letter ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(
                                                    painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                                                    contentDescription = letter.hebrew.toString(),
                                                    modifier = Modifier.size(tileSize),
                                                )
                                                Text(text = "ניקוד: ${letter.score}   כמות: ${letter.tileCount}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BevelButton(text = stringResource(Res.string.menu_exit), onClick = { showLetterTable = false })
                    }
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("יציאה מהמשחק") },
            text = { Text("האם אתה בטוח שברצונך לצאת? המשחק הנוכחי לא יישמר.") },
            confirmButton = { BevelButton(text = "צא", onClick = { showExitConfirm = false; viewModel.endGameManually() }) },
            dismissButton = { BevelButton(text = "ביטול", onClick = { showExitConfirm = false }) },
        )
    }

    if (viewModel.gameOver) {
        Dialog(onDismissRequest = { onExit() }) {
            Box(contentAlignment = Alignment.Center) {
                NoiseBackground(modifier = Modifier.widthIn(max = 420.dp).border(4.dp, Color(0xFFFFD54F), RoundedCornerShape(12.dp))) {
                    Column(modifier = Modifier.widthIn(max = 420.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val winner = viewModel.players.maxByOrNull { it.score }
                        Text("🎉 המשחק נגמר! 🎉", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (viewModel.players[0].score == viewModel.players[1].score) {
                                "תיקו! ${viewModel.players[0].score} נקודות"
                            } else {
                                "${winner?.name} ניצח/ה עם ${winner?.score} נקודות!"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        BevelButton(text = stringResource(Res.string.menu_exit), onClick = { onExit() })
                    }
                }
            }
        }
    }

    viewModel.pendingTurnSummary?.let { summary ->
        Dialog(onDismissRequest = { viewModel.dismissTurnSummary() }) {
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).border(4.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                color = Color(0xFFCFCFCF),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    summary.words.forEach { word ->
                        Text(word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("${summary.playerName} מקבל/ת ${summary.scoreDelta} נקודות.", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1A1A2E))
                    Spacer(modifier = Modifier.height(20.dp))
                    BevelButton(text = "המשך", onClick = { viewModel.dismissTurnSummary() })
                }
            }
        }
    }

    viewModel.lastError?.let { message ->
        val appeal = viewModel.pendingAppeal
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("המהלך נדחה") },
            text = { Text(message) },
            confirmButton = {
                if (appeal != null) {
                    BevelButton(text = ChallengeSystem.DISPUTE_BUTTON_LABEL, onClick = { viewModel.appealAccept() })
                } else {
                    BevelButton(text = ChallengeSystem.CONTINUE_BUTTON_LABEL, onClick = { viewModel.dismissError() })
                }
            },
            dismissButton = {
                if (appeal != null) {
                    BevelButton(text = "ויתור", onClick = { viewModel.appealDiscard() })
                }
            },
        )
    }

    viewModel.pendingJokerChoice?.let { pos ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.widthIn(max = 620.dp).padding(16.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("בחר אות עבור הג'וקר", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Letter.entries.chunked(6).forEach { rowLetters ->
                            Row {
                                rowLetters.forEach { letter ->
                                    Image(
                                        painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                                        contentDescription = letter.hebrew.toString(),
                                        modifier = Modifier.padding(3.dp).size(44.dp).pointerInput(letter) {
                                            detectTapGestures { viewModel.chooseJokerLetter(pos, letter) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewModel.pendingBonusCelebration?.let { message ->
        val isSad = viewModel.lastBonusScoreWasZero
        AlertDialog(
            onDismissRequest = { viewModel.dismissBonusCelebration() },
            title = {
                val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                    label = "bounceScale",
                )
                Text(
                    text = if (isSad) "😢 חבל... 😢" else "🎉 בונוס! 🎉",
                    color = if (isSad) Color(0xFFFF8A80) else Color(0xFFCFFF04),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce },
                )
            },
            text = { Text(message) },
            confirmButton = { BevelButton(text = "המשך", onClick = { viewModel.dismissBonusCelebration() }) },
        )
    }

    viewModel.pendingBonusType?.let { type ->
        BonusMiniGameScreen(
            type = type,
            generator = bonusGenerator,
            dictionary = viewModel.dictionary,
            playerName = viewModel.bonusTriggeringPlayerName,
            onFinished = { outcome -> viewModel.applyBonusOutcome(outcome) },
        )
    }
}

@Composable
private fun PlayerPanel(name: String, score: Int, isCurrent: Boolean, scoreMultiplier: Int, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(targetValue = if (isCurrent) 1f else 0f, animationSpec = tween(400), label = "turnGlow")
    Column(
        modifier = modifier.padding(4.dp).background(Color(0xFF1B4058), RoundedCornerShape(8.dp)).border(
            BorderStroke((1.5 + glow * 1.5).dp, Color(0xFFCFFF04).copy(alpha = 0.4f + glow * 0.6f)),
            RoundedCornerShape(8.dp),
        ).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, color = if (isCurrent) Color(0xFFCFFF04) else Color.White, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        if (isCurrent) Text("▲ תורך", color = Color(0xFFCFFF04), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        LcdDisplay(value = "$score")
        if (scoreMultiplier > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.background(Color(0xFF2E7D32), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFCFFF04), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("X$scoreMultiplier", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoardGrid(viewModel: GameViewModel, selectedRackIndex: Int?, onSelectionConsumed: () -> Unit, cellBounds: MutableMap<Position, Rect>) {
    val board = viewModel.board
    val density = LocalDensity.current
    var draggingFrom by remember { mutableStateOf<Position?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var boardRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val marginCells = 1.1f
        val totalCellsAcross = board.size + marginCells * 2
        val cellSize: Dp = minOf(maxWidth, maxHeight) / totalCellsAcross
        val boardPixelSize = cellSize * board.size
        val margin = cellSize * marginCells
        val halfCellPx = with(density) { (cellSize / 2).toPx() }

        Box(modifier = Modifier.size(boardPixelSize + margin * 2).onGloballyPositioned { coords -> boardRootTopLeft = coords.boundsInRoot().topLeft }, contentAlignment = Alignment.TopStart) {
            Column(modifier = Modifier.offset(margin, margin)) {
                for (row in 0 until board.size) {
                    Row {
                        for (col in 0 until board.size) {
                            val pos = Position(row, col)
                            val committedTile = board.tileAt(pos)
                            val pendingTile = viewModel.pending[pos]
                            val tile = committedTile ?: pendingTile
                            val isLocked = board.isLocked(pos)
                            val isPending = pendingTile != null
                            var tileRootTopLeft by remember { mutableStateOf(Offset.Zero) }
                            Box(
                                modifier = Modifier.size(cellSize).padding(1.dp).background(if (isLocked) Color(0xFF1A1A1A) else Color(0xFF2C6E8E)).border(0.5.dp, Color(0xFF16344A))
                                    .onGloballyPositioned { coords -> cellBounds[pos] = coords.boundsInRoot(); tileRootTopLeft = coords.boundsInRoot().topLeft }
                                    .pointerInput(pos, selectedRackIndex, pendingTile) {
                                        detectTapGestures {
                                            if (pendingTile != null) viewModel.returnPendingToRack(pos) else selectedRackIndex?.let { idx -> viewModel.placeFromRack(idx, pos); onSelectionConsumed() }
                                        }
                                    }
                                    .then(if (isPending) Modifier.pointerInput(pos, pendingTile) {
                                        detectDragGestures(
                                            onDragStart = { draggingFrom = pos; dragOffset = Offset.Zero },
                                            onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                            onDragEnd = {
                                                val dropCenter = tileRootTopLeft + dragOffset + Offset(halfCellPx, halfCellPx)
                                                val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                                if (target != null) viewModel.movePendingTile(pos, target.key)
                                                draggingFrom = null; dragOffset = Offset.Zero
                                            },
                                            onDragCancel = { draggingFrom = null; dragOffset = Offset.Zero },
                                        )
                                    } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tile != null && draggingFrom != pos) {
                                    val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(180), label = "tileAppear")
                                    Image(
                                        painter = painterResource(LetterTileArt.drawableFor(tile)),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.then(if (isPending) Modifier.border(2.dp, Color(0xFFCFFF04)) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            draggingFrom?.let { fromPos ->
                val draggedTile = viewModel.pending[fromPos]
                if (draggedTile != null) {
                    val cellRootTopLeft = cellBounds[fromPos]?.topLeft
                    val cellPxOffset = cellRootTopLeft?.minus(boardRootTopLeft) ?: with(density) { Offset(margin.toPx() + cellSize.toPx() * fromPos.col, margin.toPx() + cellSize.toPx() * fromPos.row) }
                    Box(modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).zIndex(10f).absoluteOffset {
                        IntOffset((cellPxOffset.x + dragOffset.x).roundToInt(), (cellPxOffset.y + dragOffset.y).roundToInt())
                    }.size(cellSize).padding(1.dp), contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(LetterTileArt.drawableFor(draggedTile)), contentDescription = null, modifier = Modifier.fillMaxSize().border(2.dp, Color(0xFFCFFF04)))
                    }
                }
            }

            board.bonusSlots.forEach { slot ->
                val pos = board.positionFor(slot)
                val committedTile = board.tileAt(pos)
                val pendingTile = viewModel.pending[pos]
                val tile = committedTile ?: pendingTile
                val isPending = pendingTile != null
                val icon = BonusIconAssignment.iconFor(slot)
                val (offsetX, offsetY) = when (slot.edge) {
                    BoardEdge.TOP -> Pair(margin + cellSize * slot.alignIndex, margin - cellSize)
                    BoardEdge.BOTTOM -> Pair(margin + cellSize * slot.alignIndex, margin + boardPixelSize)
                    BoardEdge.LEFT -> Pair(margin - cellSize, margin + cellSize * slot.alignIndex)
                    BoardEdge.RIGHT -> Pair(margin + boardPixelSize, margin + cellSize * slot.alignIndex)
                }
                Box(
                    modifier = Modifier.offset(offsetX, offsetY).size(cellSize).onGloballyPositioned { coords -> cellBounds[pos] = coords.boundsInRoot() }
                        .pointerInput(pos, selectedRackIndex, pendingTile) {
                            detectTapGestures {
                                if (pendingTile != null) viewModel.returnPendingToRack(pos) else selectedRackIndex?.let { idx -> viewModel.placeFromRack(idx, pos); onSelectionConsumed() }
                            }
                        }
                        .then(if (isPending) Modifier.pointerInput(pos, pendingTile) {
                            detectDragGestures(
                                onDragStart = { draggingFrom = pos; dragOffset = Offset.Zero },
                                onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                onDragEnd = {
                                    val dropCenter = cellBounds[pos]!!.topLeft + dragOffset + Offset(halfCellPx, halfCellPx)
                                    val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                    if (target != null) viewModel.movePendingTile(pos, target.key)
                                    draggingFrom = null; dragOffset = Offset.Zero
                                },
                                onDragCancel = { draggingFrom = null; dragOffset = Offset.Zero },
                            )
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (tile != null && draggingFrom != pos) {
                        val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(180), label = "bonusTileAppear")
                        Image(
                            painter = painterResource(LetterTileArt.drawableFor(tile)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.then(if (isPending) Modifier.border(2.dp, Color(0xFFCFFF04)) else Modifier),
                        )
                    } else if (tile == null) {
                        Image(painter = painterResource(icon.drawableRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private data class RackDragInfo(val playerIndex: Int, val index: Int, val tile: Tile, val tileRootTopLeft: Offset, val dragOffset: Offset, val tileSize: Dp)

@Composable
private fun VerticalRack(
    rack: List<Tile>,
    enabled: Boolean,
    selectedRackIndex: Int?,
    onSelect: (Int) -> Unit,
    viewModel: GameViewModel,
    cellBounds: Map<Position, Rect>,
    playerIndex: Int,
    rackDrag: RackDragInfo?,
    onRackDragChange: (RackDragInfo?) -> Unit,
) {
    val density = LocalDensity.current
    val tileSize = 40.dp
    val columns = 2
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF2C4A63), RoundedCornerShape(6.dp)).border(1.dp, Color(0xFF0E2233), RoundedCornerShape(6.dp)).padding(6.dp)
            .then(if (!enabled) Modifier.graphicsLayer { alpha = 0.45f } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rack.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowTiles.forEachIndexed { colIndex, tile ->
                    val index = rowIndex * columns + colIndex
                    var tileRootTopLeft by remember { mutableStateOf(Offset.Zero) }
                    val halfTilePx = with(density) { (tileSize / 2).toPx() }
                    val isDragging = rackDrag?.playerIndex == playerIndex && rackDrag.index == index
                    Box(
                        modifier = Modifier.size(tileSize).border(width = if (selectedRackIndex == index) 2.dp else 0.dp, color = Color(0xFFCFFF04))
                            .onGloballyPositioned { coords -> tileRootTopLeft = coords.boundsInRoot().topLeft }
                            .then(if (enabled) Modifier.pointerInput(index) { detectTapGestures { onSelect(index) } }.pointerInput(index) {
                                var localOffset = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { localOffset = Offset.Zero; onRackDragChange(RackDragInfo(playerIndex, index, tile, tileRootTopLeft, localOffset, tileSize)) },
                                    onDrag = { change, amount -> change.consume(); localOffset += amount; onRackDragChange(RackDragInfo(playerIndex, index, tile, tileRootTopLeft, localOffset, tileSize)) },
                                    onDragEnd = {
                                        val dropCenter = tileRootTopLeft + localOffset + Offset(halfTilePx, halfTilePx)
                                        val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                        if (target != null) viewModel.placeFromRack(index, target.key)
                                        onRackDragChange(null)
                                    },
                                    onDragCancel = { onRackDragChange(null) },
                                )
                            } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isDragging) {
                            Image(painter = painterResource(LetterTileArt.drawableFor(tile)), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
