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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
fun BoardScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    musicTheme: il.cet.bonus.ui.theme.GameTheme,
    onToggleMusicTheme: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bonusGenerator = remember { il.cet.bonus.bonus.BonusWordBankLoader.create(context) }
    var selectedRackIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableMapOf<Position, Rect>() }
    var showQueryDialog by remember { mutableStateOf(false) }
    var showLetterTable by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<String?>(null) }
    var containerRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    // Rack-tile drag state lifted up to this top-level screen (instead of kept local to
    // VerticalRack) so the floating dragged-tile overlay can be rendered as the very last
    // child of the outermost Box - otherwise, being a sibling deep inside the sizeable Row
    // (VerticalRack -> BoardGrid), it was painted *behind* the board grid during the drag.
    var rackDrag by remember { mutableStateOf<RackDragInfo?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { coords -> containerRootTopLeft = coords.boundsInRoot().topLeft },
    ) {
        Image(
            painter = painterResource(R.drawable.bg_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // --- TEMPORARY DEBUG-ONLY button, remove once bonus mini-games are fully verified ---
        // Triggers the next bonus mini-game type in sequence on each tap, for manual
        // testing without needing to land on an actual bonus slot.
        androidx.compose.material3.Button(
            onClick = { viewModel.debugTriggerNextBonus() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).zIndex(10f),
        ) {
            Text("DEBUG בונוס")
        }
        // --- end TEMPORARY DEBUG-ONLY button ---

        Row(modifier = Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Side controls: menu/query/letter-table/complete-turn buttons, moves
            // counter and remaining-tiles readout - all stacked vertically in a narrow
            // column so the grid can take up the rest of the screen.
            Column(
                modifier = Modifier.width(100.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LcdDisplay(value = "%02d".format(viewModel.movesPlayed), label = stringResource(R.string.moves_counter_label))
                BevelButton(text = stringResource(R.string.menu_exit), modifier = Modifier.fillMaxWidth(), onClick = { showExitConfirm = true })
                BevelButton(text = stringResource(R.string.query_word), modifier = Modifier.fillMaxWidth(), onClick = { showQueryDialog = true })
                BevelButton(text = stringResource(R.string.letter_table), modifier = Modifier.fillMaxWidth(), onClick = { showLetterTable = true })
                BevelButton(
                    text = if (musicTheme == il.cet.bonus.ui.theme.GameTheme.OLD) "מוזיקה: ישנה" else "מוזיקה: חדשה",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onToggleMusicTheme,
                )
                BevelButton(text = stringResource(R.string.complete_turn), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.completeTurn() })
                LcdDisplay(value = "${viewModel.tilesRemainingInBag}", label = stringResource(R.string.tiles_remaining_label))
            }

            // Left side: player 0's panel + their rack, stacked vertically alongside the
            // grid (instead of a full-width row below it) so the board can use nearly the
            // full remaining screen height. Only the *current* player's rack is
            // interactive; the other player's rack is shown disabled (dimmed, no taps/drags)
            // - matching the original game's two side letter-boxes.
            Column(
                modifier = Modifier.width(96.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
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
                BoardGrid(
                    viewModel = viewModel,
                    selectedRackIndex = selectedRackIndex,
                    onSelectionConsumed = { selectedRackIndex = null },
                    cellBounds = cellBounds,
                )

            }

            // Right side: player 1's panel + rack, mirroring the left side.
            Column(
                modifier = Modifier.width(96.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
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

        // Floating overlay for a rack tile currently being drag-dropped onto the board,
        // rendered as the very last child of the screen's outermost Box so it always
        // paints above the grid, regardless of where in the Row hierarchy the rack lives.
        rackDrag?.let { drag ->
            val originPx = drag.tileRootTopLeft.minus(containerRootTopLeft)
            Box(
                // AbsoluteAlignment.TopLeft (not TopStart): under the app's RTL layout,
                // TopStart's un-offset anchor is physically top-right, so combining it with
                // our physically-top-left absoluteOffset math sent the tile far off to the
                // wrong side. Absolute.TopLeft anchors at the true top-left corner,
                // matching the coordinate space absoluteOffset uses.
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .zIndex(20f)
                    .absoluteOffset {
                        IntOffset(
                            (originPx.x + drag.dragOffset.x).roundToInt(),
                            (originPx.y + drag.dragOffset.y).roundToInt(),
                        )
                    }
                    .size(drag.tileSize),
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
                        BevelButton(text = stringResource(R.string.menu_exit), onClick = { showQueryDialog = false; queryResult = null })
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
        // Plain Dialog + Surface (not AlertDialog) so we can lay the 22 letters out in a
        // wide multi-column grid of real jpg tiles and have them all fit on screen at once,
        // with no scrolling needed - matching the original game's letter-table look. The
        // dialog is sized as a fraction of the available screen (via BoxWithConstraints)
        // and each tile's size is derived from that, so all rows always fit uniformly
        // instead of the last row(s) getting visually squeezed/cut off.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLetterTable = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(0.92f), contentAlignment = Alignment.Center) {
                val dialogWidth = maxWidth
                val dialogHeight = maxHeight
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(dialogWidth, dialogHeight),
                    shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.letter_table), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        val letters = il.cet.bonus.core.model.Letter.entries
                        val columns = 5
                        val rows = (letters.size + columns - 1) / columns
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val cellWidth = maxWidth / columns
                            val cellHeight = maxHeight / rows
                            val tileSize = minOf(cellWidth * 0.55f, cellHeight * 0.6f)
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                                letters.chunked(columns).forEach { rowLetters ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        rowLetters.forEach { letter ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Image(
                                                    painter = painterResource(
                                                        il.cet.bonus.ui.board.LetterTileArt.drawableFor(
                                                            il.cet.bonus.core.model.Tile.LetterTile(letter),
                                                        ),
                                                    ),
                                                    contentDescription = letter.hebrew.toString(),
                                                    modifier = Modifier.size(tileSize),
                                                )
                                                Text(
                                                    text = "ניקוד: ${letter.score}   כמות: ${letter.tileCount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BevelButton(text = stringResource(R.string.menu_exit), onClick = { showLetterTable = false })
                    }
                }
            }
        }
    }

    // Exit confirmation popup: asking before leaving avoids accidentally discarding
    // an in-progress game (there's no save/resume yet - see TODO.md item 1). Confirming
    // ends the current game (same end-state as running out of rack tiles) so the
    // win/tie celebration popup + game-over music are shown before actually navigating
    // away - per confirmed end-game rule (exit button also ends the game).
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("יציאה מהמשחק") },
            text = { Text("האם אתה בטוח שברצונך לצאת? המשחק הנוכחי לא יישמר.") },
            confirmButton = {
                BevelButton(text = "צא", onClick = { showExitConfirm = false; viewModel.endGameManually() })
            },
            dismissButton = {
                BevelButton(text = "ביטול", onClick = { showExitConfirm = false })
            },
        )
    }

    // Game-over popup (per confirmed end-game rule: triggered either by the exit-game
    // button or by a player's rack running empty, never by the shared bag emptying) -
    // shows the winner (or tie) with a happy background, blocking further play until
    // dismissed back to the main menu.
    if (viewModel.gameOver) {
        Dialog(onDismissRequest = { onExit() }) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.bg_texture),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .border(4.dp, Color(0xFFFFD54F), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val winner = viewModel.players.maxByOrNull { it.score }
                    Text(
                        text = "🎉 המשחק נגמר! 🎉",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
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
                    BevelButton(text = stringResource(R.string.menu_exit), onClick = { onExit() })
                }
            }
        }
    }

    // End-of-turn score summary popup (per original game's post-move popup): lists the
    // word(s) just scored and the points gained, blocking further play until dismissed -
    // any pending bonus-slot prize/mini-game and the turn switch are deferred behind it.
    viewModel.pendingTurnSummary?.let { summary ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.dismissTurnSummary() }) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .border(4.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                color = Color(0xFFCFCFCF),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    summary.words.forEach { word ->
                        Text(
                            text = word,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A2E),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${summary.playerName} מקבל/ת ${summary.scoreDelta} נקודות.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    BevelButton(text = "המשך", onClick = { viewModel.dismissTurnSummary() })
                }
            }
        }
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
                    BevelButton(
                        text = il.cet.bonus.core.game.ChallengeSystem.DISPUTE_BUTTON_LABEL,
                        onClick = { viewModel.appealAccept() },
                    )
                } else {
                    BevelButton(
                        text = il.cet.bonus.core.game.ChallengeSystem.CONTINUE_BUTTON_LABEL,
                        onClick = { viewModel.dismissError() },
                    )
                }
            },
            dismissButton = {
                if (appeal != null) {
                    BevelButton(text = "ויתור", onClick = { viewModel.appealDiscard() })
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
                BevelButton(text = "המשך", onClick = { viewModel.dismissBonusCelebration() })
            },
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
private fun PlayerPanel(
    name: String,
    score: Int,
    isCurrent: Boolean,
    scoreMultiplier: Int = 1,
    modifier: Modifier = Modifier,
) {
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
        // Active score-multiplier badge (from a "double score"/"quadruple score" bonus
        // prize - see BonusPrize/GameViewModel.withMultiplier), matching the original
        // game's X2/X4 marker cards (x2.png / x4.png reference).
        if (scoreMultiplier > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFFCFFF04), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "X$scoreMultiplier",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
    val density = LocalDensity.current
    var draggingFrom by remember { mutableStateOf<Position?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var boardRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val marginCells = 1.1f // room for the peripheral bonus icons + a little breathing room
        val totalCellsAcross = board.size + marginCells * 2
        val cellSize: Dp = minOf(maxWidth, maxHeight) / totalCellsAcross
        val boardPixelSize = cellSize * board.size
        val margin = cellSize * marginCells
        val halfCellPx = with(density) { (cellSize / 2).toPx() }

        Box(
            modifier = Modifier
                .size(boardPixelSize + margin * 2)
                .onGloballyPositioned { coords -> boardRootTopLeft = coords.boundsInRoot().topLeft },
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
                            var tileRootTopLeft by remember { mutableStateOf(Offset.Zero) }

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
                                    .onGloballyPositioned { coords ->
                                        cellBounds[pos] = coords.boundsInRoot()
                                        tileRootTopLeft = coords.boundsInRoot().topLeft
                                    }
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
                                    }
                                    .then(
                                        // Only tentatively-placed (not yet committed) tiles can be
                                        // dragged to reposition them - matching the "before committing
                                        // a word" restriction. A regular (non-long-press) drag moves it.
                                        if (isPending) {
                                            Modifier.pointerInput(pos, pendingTile) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingFrom = pos
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffset += amount
                                                    },
                                                    onDragEnd = {
                                                        val dropCenter = tileRootTopLeft + dragOffset + Offset(halfCellPx, halfCellPx)
                                                        val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                                        if (target != null) viewModel.movePendingTile(pos, target.key)
                                                        draggingFrom = null
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDragCancel = {
                                                        draggingFrom = null
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
                                // While this exact tile is being dragged, hide it here and render
                                // a floating copy on top of everything (see overlay below the grid) -
                                // otherwise the tile would be clipped behind neighboring board rows.
                                if (tile != null && draggingFrom != pos) {
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

            // Floating overlay for the tile currently being dragged, rendered above the
            // grid rows and bonus slots so it never appears clipped behind other cells.
            draggingFrom?.let { fromPos ->
                val draggedTile = viewModel.pending[fromPos]
                if (draggedTile != null) {
                    // Use the cell's actual measured root position (converted back to
                    // local/board-relative coordinates) rather than a manually computed
                    // margin + cellSize*col/row offset - the app forces RTL layout, so
                    // columns are mirrored on screen and a naive LTR-assuming formula
                    // drags the tile in the wrong horizontal direction.
                    val cellRootTopLeft = cellBounds[fromPos]?.topLeft
                    val cellPxOffset = cellRootTopLeft?.minus(boardRootTopLeft) ?: with(density) {
                        Offset(margin.toPx() + cellSize.toPx() * fromPos.col, margin.toPx() + cellSize.toPx() * fromPos.row)
                    }
                    // Use AbsoluteAlignment.TopLeft (not the parent's default TopStart)
                    // since TopStart's un-offset anchor is physically mirrored under this
                    // app's global RTL setting - combining that mirrored anchor with our
                    // physically-top-left absoluteOffset math sent the tile flying off to
                    // the wrong side of the screen.
                    Box(
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .zIndex(10f)
                            .absoluteOffset {
                                IntOffset(
                                    (cellPxOffset.x + dragOffset.x).roundToInt(),
                                    (cellPxOffset.y + dragOffset.y).roundToInt(),
                                )
                            }
                            .size(cellSize)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(LetterTileArt.drawableFor(draggedTile)),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color(0xFFCFFF04)),
                        )
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
                        // No extra background/border here: when empty, the bonus icon
                        // artwork below already includes its own yellow tile background
                        // (cropped directly from the real game's screenshot), so adding
                        // another yellow box+border around it created a double-frame look.
                        // When occupied, the letter tile art draws its own tile chrome too.
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
                        }
                        .then(
                            // Same drag-to-reposition support the interior grid cells have -
                            // bonus slots are genuine board positions too, so a tentatively
                            // placed tile here should be draggable onto another cell exactly
                            // like an interior one, not just tap-to-return-to-rack.
                            if (isPending) {
                                Modifier.pointerInput(pos, pendingTile) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingFrom = pos
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                        },
                                        onDragEnd = {
                                            val dropCenter = cellBounds[pos]!!.topLeft + dragOffset + Offset(halfCellPx, halfCellPx)
                                            val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                            if (target != null) viewModel.movePendingTile(pos, target.key)
                                            draggingFrom = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggingFrom = null
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
                    // While this exact tile is being dragged, hide it here - the floating
                    // overlay (rendered above, keyed off draggingFrom/cellBounds) shows it
                    // instead, matching the interior-cell drag behavior.
                    if (tile != null && draggingFrom != pos) {
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
                    } else if (tile == null) {
                        Image(
                            painter = painterResource(icon.drawableRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** Info about a rack tile currently being dragged, lifted up to BoardScreen so the
 * floating overlay can be rendered above the entire screen (fixing the "tile visible
 * behind the grid" z-order bug that occurred when the overlay lived only within the
 * VerticalRack composable, a sibling deep inside the Row). */
private data class RackDragInfo(
    val playerIndex: Int,
    val index: Int,
    val tile: Tile,
    val tileRootTopLeft: Offset,
    val dragOffset: Offset,
    val tileSize: Dp,
)

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
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2C4A63), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF0E2233), RoundedCornerShape(6.dp))
            .padding(6.dp)
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
                        modifier = Modifier
                            .size(tileSize)
                            .border(
                                width = if (selectedRackIndex == index) 2.dp else 0.dp,
                                color = Color(0xFFCFFF04),
                            )
                            .onGloballyPositioned { coords -> tileRootTopLeft = coords.boundsInRoot().topLeft }
                            .then(
                                if (enabled) {
                                    Modifier
                                        .pointerInput(index) {
                                            detectTapGestures { onSelect(index) }
                                        }
                                        .pointerInput(index) {
                                            // Accumulate the drag offset in a local variable
                                            // instead of reading it back from the `rackDrag`
                                            // parameter: this whole gesture-detector coroutine
                                            // is launched once per `index` key and keeps running
                                            // with its original captured closures across
                                            // recompositions, so re-reading the (stale) `rackDrag`
                                            // parameter mid-gesture always saw null/outdated
                                            // state and the tile never visibly moved.
                                            var localOffset = Offset.Zero
                                            detectDragGestures(
                                                onDragStart = {
                                                    localOffset = Offset.Zero
                                                    onRackDragChange(
                                                        RackDragInfo(playerIndex, index, tile, tileRootTopLeft, localOffset, tileSize),
                                                    )
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    localOffset += amount
                                                    onRackDragChange(
                                                        RackDragInfo(playerIndex, index, tile, tileRootTopLeft, localOffset, tileSize),
                                                    )
                                                },
                                                onDragEnd = {
                                                    // Compare the tile's current on-screen *center* (root coords) against
                                                    // each board cell's root bounds - fixes the previous density-unaware
                                                    // hardcoded-pixel-offset bug that made drag-and-drop miss its target.
                                                    val dropCenter = tileRootTopLeft + localOffset + Offset(halfTilePx, halfTilePx)
                                                    val target = cellBounds.entries.firstOrNull { it.value.contains(dropCenter) }
                                                    if (target != null) viewModel.placeFromRack(index, target.key)
                                                    onRackDragChange(null)
                                                },
                                                onDragCancel = { onRackDragChange(null) },
                                            )
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // While this tile is being dragged, hide it here - the floating
                        // overlay (rendered at the BoardScreen top level) shows it instead,
                        // so it's never clipped/occluded behind the board grid.
                        if (!isDragging) {
                            Image(
                                painter = painterResource(LetterTileArt.drawableFor(tile)),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

