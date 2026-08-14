package il.cet.bonus.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import il.cet.bonus.R
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.game.GameViewModel
import il.cet.bonus.ui.board.BevelButton
import il.cet.bonus.ui.board.BonusIcon
import il.cet.bonus.ui.board.BonusIconAssignment
import il.cet.bonus.ui.board.HebrewLetterPicker
import il.cet.bonus.ui.board.LcdDisplay
import il.cet.bonus.ui.board.LetterTileArt

private typealias BoardEdge = il.cet.bonus.core.board.Board.Edge

/**
 * The main board screen, rebuilt to closely match the real 1993 Bonus DOS UI (confirmed
 * pixel-for-pixel from `bonus.mp4` gameplay footage): noisy blue backdrop, beveled gray
 * button bar, gray raised tile artwork (using the migrated original Letters jpg files),
 * and the 12 bonus icons floating *outside* the main 10x10 grid rather than colored-in
 * grid cells. Supports both tap-to-place and drag-and-drop tile input.
 */
@Composable
fun BoardScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bonusGenerator = remember { il.cet.bonus.bonus.BonusWordBankLoader.create(context) }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableMapOf<Position, Rect>() }
    var showQueryDialog by remember { mutableStateOf(false) }
    var showLetterTable by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Main content row: side control column + player panels + board. Moving the
            // top button bar into a narrow side column (instead of a full-width row above
            // the board) frees up most of the screen's limited vertical space for the grid.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Side controls: menu/query/letter-table/complete-turn buttons, moves
                // counter and remaining-tiles readout - all stacked vertically.
                Column(
                    modifier = Modifier.width(110.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LcdDisplay(value = "%02d".format(viewModel.movesPlayed))
                    BevelButton(text = stringResource(R.string.menu_exit), modifier = Modifier.fillMaxWidth(), onClick = onExit)
                    BevelButton(text = stringResource(R.string.query_word), modifier = Modifier.fillMaxWidth(), onClick = { showQueryDialog = true })
                    BevelButton(text = stringResource(R.string.letter_table), modifier = Modifier.fillMaxWidth(), onClick = { showLetterTable = true })
                    BevelButton(text = stringResource(R.string.complete_turn), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.completeTurn() })
                    Image(
                        painter = painterResource(R.drawable.joker),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    LcdDisplay(value = "${viewModel.tilesRemainingInBag}")
                }

                PlayerPanel(
                    name = viewModel.players[0].name,
                    score = viewModel.players[0].score,
                    isCurrent = viewModel.currentPlayerIndex == 0,
                    modifier = Modifier.width(100.dp),
                )

                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    BoardGrid(
                        viewModel = viewModel,
                        selectedRackIndex = selectedRackIndex,
                        onSelectionConsumed = { selectedRackIndex = null },
                        cellBounds = cellBounds,
                    )

                    // Status text overlaid on top of the board so it doesn't steal vertical
                    // space from the grid.
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (viewModel.gameOver) {
                            val winner = viewModel.players.maxByOrNull { it.score }
                            Text(
                                text = if (viewModel.players[0].score == viewModel.players[1].score) {
                                    "תיקו! ${viewModel.players[0].score} נקודות"
                                } else {
                                    "${winner?.name} ניצח עם ${winner?.score} נקודות!"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                            )
                        }
                    }
                }

                PlayerPanel(
                    name = viewModel.players[1].name,
                    score = viewModel.players[1].score,
                    isCurrent = viewModel.currentPlayerIndex == 1,
                    modifier = Modifier.width(100.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rack row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C4A63), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF0E2233), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RackRow(
                    viewModel = viewModel,
                    selectedRackIndex = selectedRackIndex,
                    onSelect = { selectedRackIndex = if (selectedRackIndex == it) null else it },
                    cellBounds = cellBounds,
                )
            }
        }
    }

    if (showQueryDialog) {
        var queryText by remember { mutableStateOf("") }
        // Plain Dialog + Surface (instead of AlertDialog) because Material3's AlertDialog
        // Surface has a hardcoded ~560dp max width regardless of usePlatformDefaultWidth,
        // which was preventing the dialog from ever actually growing wide enough to fit
        // all 22 letters in 2 rows.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showQueryDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.widthIn(max = 620.dp).padding(16.dp),
                shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("שאילתא - בדיקת מילים", style = MaterialTheme.typography.headlineSmall)
                    // Pinned above the scrollable letter picker (outside its scroll area)
                    // so the result is always visible without needing to scroll down to see it.
                    queryResult?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HebrewLetterPicker(value = queryText, onValueChange = { queryText = it }, showActions = false)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        Button(onClick = { showQueryDialog = false; queryResult = null }) { Text(stringResource(R.string.menu_exit)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            queryResult = if (viewModel.dictionary.isValidWord(queryText)) "המלה תקינה!" else "המלה אינה תקינה."
                        }) { Text("בדוק") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { queryText = "" }) { Text("נקה") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { if (queryText.isNotEmpty()) queryText = queryText.dropLast(1) }) { Text("מחק") }
                    }
                }
            }
        }
    }

    if (showLetterTable) {
        AlertDialog(
            onDismissRequest = { showLetterTable = false },
            title = { Text(stringResource(R.string.letter_table)) },
            text = {
                Column {
                    il.cet.bonus.core.model.Letter.entries.forEach { letter ->
                        Text("${letter.hebrew}   ניקוד: ${letter.score}   כמות: ${letter.tileCount}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLetterTable = false }) { Text(stringResource(R.string.menu_exit)) }
            },
        )
    }

    // Rejected-move popup: shown for every move rejection. When the rejection was for
    // exactly one invalid word, offers an appeal (עירעור) that accepts the word anyway
    // (per ChallengeSystem's confirmed rule); otherwise only a dismiss button is shown.
    viewModel.lastError?.let { message ->
        val appeal = viewModel.pendingAppeal
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("המהלך נדחה") },
            text = { Text(message) },
            confirmButton = {
                if (appeal != null) {
                    Button(onClick = { viewModel.appealAccept() }) {
                        Text(il.cet.bonus.core.game.ChallengeSystem.DISPUTE_BUTTON_LABEL)
                    }
                } else {
                    Button(onClick = { viewModel.dismissError() }) {
                        Text(il.cet.bonus.core.game.ChallengeSystem.CONTINUE_BUTTON_LABEL)
                    }
                }
            },
            dismissButton = {
                if (appeal != null) {
                    Button(onClick = { viewModel.appealDiscard() }) { Text("ויתור") }
                }
            },
        )
    }

    // Joker letter-choice popup: shown right after a joker is placed, so the player picks
    // which letter it stands for before completing the turn. Choosing again (by returning
    // the tile to the rack and re-placing it) is always possible - see returnPendingToRack.
    viewModel.pendingJokerChoice?.let { pos ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* must choose a letter */ },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.widthIn(max = 620.dp).padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("בחר אות עבור הג'וקר", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        // Hardcoded to 4 equal rows (6 letters each, last row shorter) so every
                        // tile renders at the same fixed size regardless of row length.
                        il.cet.bonus.core.model.Letter.entries.chunked(6).forEach { rowLetters ->
                            Row {
                                rowLetters.forEach { letter ->
                                    Image(
                                        painter = painterResource(LetterTileArt.drawableFor(Tile.LetterTile(letter))),
                                        contentDescription = letter.hebrew.toString(),
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(44.dp)
                                            .pointerInput(letter) {
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

    // Happy celebratory popup for non-mini-game bonus prizes (points / extra turn /
    // score multiplier); a sad popup instead when the resolved bonus/mini-game outcome
    // scored zero points (e.g. player answered a bonus mini-game incorrectly).
    viewModel.pendingBonusCelebration?.let { message ->
        val isSad = viewModel.lastBonusScoreWasZero
        AlertDialog(
            onDismissRequest = { viewModel.dismissBonusCelebration() },
            title = {
                val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse,
                    ),
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
            confirmButton = {
                Button(onClick = { viewModel.dismissBonusCelebration() }) { Text(if (isSad) "בסדר" else "יאי!") }
            },
        )
    }


    viewModel.pendingBonusType?.let { type ->
        BonusMiniGameScreen(
            type = type,
            generator = bonusGenerator,
            dictionary = viewModel.dictionary,
            onFinished = { outcome -> viewModel.applyBonusOutcome(outcome) },
        )
    }
}

@Composable
private fun PlayerPanel(name: String, score: Int, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(targetValue = if (isCurrent) 1f else 0f, animationSpec = tween(400), label = "turnGlow")
    Column(
        modifier = modifier
            .padding(4.dp)
            .background(Color(0xFF1B4058), RoundedCornerShape(8.dp))
            .border(
                BorderStroke((1.5 + glow * 1.5).dp, Color(0xFFCFFF04).copy(alpha = 0.4f + glow * 0.6f)),
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            color = if (isCurrent) Color(0xFFCFFF04) else Color.White,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        if (isCurrent) {
            Text(text = "\u25B2 תורך", color = Color(0xFFCFFF04), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LcdDisplay(value = "$score")
    }
}

/**
 * The 10x10 grid plus the 12 peripheral bonus-icon tiles (rendered *outside* the grid
 * boundary, one tile-width beyond each aligned edge cell - confirmed layout from
 * gameplay footage). Sized dynamically to fill the available space via
 * [BoxWithConstraints] so it adapts to any screen/tablet size.
 */
@Composable
private fun BoardGrid(
    viewModel: GameViewModel,
    selectedRackIndex: Int?,
    onSelectionConsumed: () -> Unit,
    cellBounds: MutableMap<Position, Rect>,
) {
    val board = viewModel.board
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val marginCells = 1.1f // room for the peripheral bonus icons + a little breathing room
        val totalCellsAcross = board.size + marginCells * 2
        val cellSize: Dp = minOf(maxWidth, maxHeight) / totalCellsAcross
        val boardPixelSize = cellSize * board.size
        val margin = cellSize * marginCells

        Box(
            modifier = Modifier.size(boardPixelSize + margin * 2),
            contentAlignment = Alignment.TopStart,
        ) {
            // Main grid.
            Column(
                modifier = Modifier.offset(margin, margin),
            ) {
                for (row in 0 until board.size) {
                    Row {
                        for (col in 0 until board.size) {
                            val pos = Position(row, col)
                            val committedTile = board.tileAt(pos)
                            val pendingTile = viewModel.pending[pos]
                            val tile = committedTile ?: pendingTile
                            val isLocked = board.isLocked(pos)
                            val isPending = pendingTile != null

                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .padding(1.dp)
                                    .background(
                                        when {
                                            isLocked -> Color(0xFF1A1A1A)
                                            else -> Color(0xFF2C6E8E)
                                        },
                                    )
                                    .border(0.5.dp, Color(0xFF16344A))
                                    .onGloballyPositioned { coords -> cellBounds[pos] = coords.boundsInRoot() }
                                    .pointerInput(pos, selectedRackIndex, pendingTile) {
                                        detectTapGestures {
                                            if (pendingTile != null) {
                                                viewModel.returnPendingToRack(pos)
                                            } else {
                                                selectedRackIndex?.let { idx ->
                                                    viewModel.placeFromRack(idx, pos)
                                                    onSelectionConsumed()
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tile != null) {
                                    val scale by animateFloatAsState(
                                        targetValue = 1f,
                                        animationSpec = tween(180),
                                        label = "tileAppear",
                                    )
                                    Image(
                                        painter = painterResource(LetterTileArt.drawableFor(tile)),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { scaleX = scale; scaleY = scale }
                                            .then(
                                                if (isPending) Modifier.border(2.dp, Color(0xFFCFFF04)) else Modifier,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Peripheral bonus squares. These are drawn just outside the 10x10 grid but
            // are genuine, independently-placeable board squares (board.positionFor(slot)
            // returns a distinct virtual Position just beyond the grid, not a proxy for an
            // interior cell - see Board.kt doc) - a player can place a letter directly on
            // one, and it participates in word-building exactly like any interior square.
            // So each of these boxes behaves exactly like an interior grid cell: it shows
            // its own tile (committed or pending) when occupied, supports tap-to-return,
            // and falls back to the bonus icon artwork only while empty.
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
                    modifier = Modifier
                        .offset(offsetX, offsetY)
                        .size(cellSize)
                        .padding(1.dp)
                        .background(Color(0xFFF2B705), RoundedCornerShape(3.dp))
                        .border(1.dp, Color(0xFF8A6200), RoundedCornerShape(3.dp))
                        .onGloballyPositioned { coords -> cellBounds[pos] = coords.boundsInRoot() }
                        .pointerInput(pos, selectedRackIndex, pendingTile) {
                            detectTapGestures {
                                if (pendingTile != null) {
                                    viewModel.returnPendingToRack(pos)
                                } else {
                                    selectedRackIndex?.let { idx ->
                                        viewModel.placeFromRack(idx, pos)
                                        onSelectionConsumed()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (tile != null) {
                        val scale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(180),
                            label = "bonusTileAppear",
                        )
                        Image(
                            painter = painterResource(LetterTileArt.drawableFor(tile)),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .then(
                                    if (isPending) Modifier.border(2.dp, Color(0xFFCFFF04)) else Modifier,
                                ),
                        )
                    } else if (icon == BonusIcon.STAR) {
                        Image(
                            painter = painterResource(R.drawable.bonus_star),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
                    } else {
                        Text(text = icon.glyph, color = icon.tint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RackRow(
    viewModel: GameViewModel,
    selectedRackIndex: Int?,
    onSelect: (Int) -> Unit,
    cellBounds: Map<Position, Rect>,
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    val tileSize = 44.dp

    viewModel.currentRack.forEachIndexed { index, tile ->
        var tileRootTopLeft by remember { mutableStateOf(Offset.Zero) }
        val halfTilePx = with(density) { (tileSize / 2).toPx() }

        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(tileSize)
                .border(
                    width = if (selectedRackIndex == index) 2.dp else 0.dp,
                    color = Color(0xFFCFFF04),
                )
                .onGloballyPositioned { coords -> tileRootTopLeft = coords.boundsInRoot().topLeft }
                .graphicsLayer {
                    if (draggingIndex == index) {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                    }
                }
                .pointerInput(index) {
                    detectTapGestures {
                        onSelect(index)
                    }
                }
                .pointerInput(index) {
                    // A regular (non-long-press) drag: pressing and immediately dragging
                    // moves the tile, matching normal drag-and-drop expectations.
                    detectDragGestures(
                        onDragStart = {
                            draggingIndex = index
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                        },
                        onDragEnd = {
                            // Compare the tile's current on-screen *center* (root coords) against
                            // each board cell's root bounds - fixes the previous density-unaware
                            // hardcoded-pixel-offset bug that made drag-and-drop miss its target.
                            val dropCenter = tileRootTopLeft + dragOffset + Offset(halfTilePx, halfTilePx)
                            val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                            if (target != null) viewModel.placeFromRack(index, target.key)
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(LetterTileArt.drawableFor(tile)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

