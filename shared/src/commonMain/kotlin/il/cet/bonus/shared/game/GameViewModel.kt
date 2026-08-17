package il.cet.bonus.shared.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import il.cet.bonus.core.board.Board
import il.cet.bonus.core.bonus.BonusOutcome
import il.cet.bonus.core.bonus.BonusPrize
import il.cet.bonus.core.bonus.BonusType
import il.cet.bonus.core.bonus.drawBonusPrize
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.game.GameEngine
import il.cet.bonus.core.game.LetterBag
import il.cet.bonus.core.game.LockAction
import il.cet.bonus.core.game.MoveError
import il.cet.bonus.core.game.MoveException
import il.cet.bonus.core.game.ChallengeSystem
import il.cet.bonus.core.game.Player
import il.cet.bonus.core.game.SwapAction
import il.cet.bonus.core.model.Letter
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.shared.audio.MusicController
import il.cet.bonus.shared.audio.SfxPlayer
import kotlin.random.Random

private const val RACK_SIZE = 8

data class PendingAppeal(val move: Map<Position, Tile>, val rejectedWord: String)

class GameViewModel(
    val dictionary: DictionaryRepository,
    private val musicController: MusicController,
    private val sfxPlayer: SfxPlayer,
) {
    var board = Board()
        private set
    private var bag = LetterBag()
    private var engine = GameEngine(board, dictionary)

    var players by mutableStateOf(listOf(Player(0, ""), Player(1, "")))
        private set
    var currentPlayerIndex by mutableStateOf(0)
        private set
    var racks by mutableStateOf(listOf<List<Tile>>(emptyList(), emptyList()))
        private set
    var pending by mutableStateOf<Map<Position, Tile>>(emptyMap())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var lastMessage by mutableStateOf<String?>(null)
        private set
    var gameOver by mutableStateOf(false)
        private set
    var pendingAppeal by mutableStateOf<PendingAppeal?>(null)
        private set
    var pendingJokerChoice by mutableStateOf<Position?>(null)
        private set
    var pendingBonusCelebration by mutableStateOf<String?>(null)
        private set
    var lastBonusScoreWasZero by mutableStateOf(false)
        private set
    var movesPlayed by mutableStateOf(0)
        private set
    val tilesRemainingInBag: Int get() = bag.tilesLeft
    var pendingBonusType by mutableStateOf<BonusType?>(null)
        private set
    private var bonusTriggeringPlayerIndex = 0
    private var debugBonusIndex = 0
    data class TurnSummary(val playerName: String, val words: List<String>, val scoreDelta: Int)
    var pendingTurnSummary by mutableStateOf<TurnSummary?>(null)
        private set
    private var deferredAfterTurnSummary: (() -> Unit)? = null
    val bonusTriggeringPlayerName: String get() = players[bonusTriggeringPlayerIndex].name
    private var awardedBonusPrizes: Set<BonusPrize> = emptySet()
    private var skipTurnSwitchOnce = false

    fun debugTriggerNextBonus() {
        val types = BonusType.entries
        if (debugBonusIndex >= types.size) {
            lastMessage = "אין עוד חידות בונוס (debug)"
            return
        }
        bonusTriggeringPlayerIndex = currentPlayerIndex
        pendingBonusType = types[debugBonusIndex++]
    }

    fun startGame(playerAName: String, playerBName: String) {
        board = Board()
        bag = LetterBag()
        engine = GameEngine(board, dictionary)
        players = listOf(Player(0, playerAName), Player(1, playerBName))
        currentPlayerIndex = 0
        racks = listOf(bag.draw(RACK_SIZE), bag.draw(RACK_SIZE))
        pending = emptyMap()
        gameOver = false
        awardedBonusPrizes = emptySet()
        skipTurnSwitchOnce = false
        lastError = null
        lastMessage = null
        pendingAppeal = null
        pendingJokerChoice = null
        pendingBonusCelebration = null
        lastBonusScoreWasZero = false
        movesPlayed = 0
        pendingBonusType = null
        bonusTriggeringPlayerIndex = 0
        musicController.resume()
    }

    val currentRack: List<Tile> get() = racks.getOrElse(currentPlayerIndex) { emptyList() }
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun placeFromRack(rackIndex: Int, pos: Position) {
        val rack = currentRack
        if (rackIndex !in rack.indices) return
        if (!board.isPlaceable(pos) || pos in pending) return
        val tile = rack[rackIndex]
        setRack(currentPlayerIndex, rack.toMutableList().also { it.removeAt(rackIndex) })
        pending = pending + (pos to tile)
        if (tile is Tile.JokerTile && tile.chosenLetter == null) pendingJokerChoice = pos
    }

    fun chooseJokerLetter(pos: Position, letter: Letter) {
        val tile = pending[pos] as? Tile.JokerTile ?: return
        pending = pending + (pos to tile.copy(chosenLetter = letter))
        pendingJokerChoice = null
    }

    fun returnPendingToRack(pos: Position) {
        val tile = pending[pos] ?: return
        pending = pending - pos
        if (pendingJokerChoice == pos) pendingJokerChoice = null
        setRack(currentPlayerIndex, currentRack + if (tile is Tile.JokerTile) Tile.JokerTile(chosenLetter = null) else tile)
    }

    fun movePendingTile(from: Position, to: Position) {
        if (from == to) return
        val tile = pending[from] ?: return
        if (!board.isPlaceable(to) || to in pending || board.tileAt(to) != null) return
        pending = pending - from + (to to tile)
        if (pendingJokerChoice == from) pendingJokerChoice = to
    }

    fun endGameManually() {
        if (gameOver) return
        gameOver = true
        sfxPlayer.playGameOver(musicController)
    }

    fun completeTurn() {
        if (pendingJokerChoice != null) {
            lastError = "יש לבחור אות עבור הג'וקר לפני סיום התור"
            return
        }
        if (pending.isEmpty()) {
            passTurn()
            return
        }
        applyMoveResult(engine.proposeMove(pending), null)
    }

    private fun passTurn() {
        val scoringPlayerIndex = currentPlayerIndex
        val scoringPlayerName = players[scoringPlayerIndex].name
        lastError = null
        pendingAppeal = null
        movesPlayed += 1
        skipTurnSwitchOnce = false
        sfxPlayer.playEndOfTurn(musicController)
        deferredAfterTurnSummary = { lastMessage = null; currentPlayerIndex = 1 - scoringPlayerIndex }
        pendingTurnSummary = TurnSummary(scoringPlayerName, emptyList(), 0)
    }

    private fun applyMoveResult(result: Result<il.cet.bonus.core.game.TurnResult>, appealedMove: Map<Position, Tile>?) {
        result.onSuccess { turn ->
            val scoringPlayerIndex = currentPlayerIndex
            val scoringPlayerName = players[scoringPlayerIndex].name
            val activeMultiplier = players[scoringPlayerIndex].scoreMultiplier
            players = players.toMutableList().also { it[currentPlayerIndex] = it[currentPlayerIndex].withScoredTurn(turn.scoreDelta) }
            val awardedScoreDelta = turn.scoreDelta * activeMultiplier
            val placedCount = appealedMove?.size ?: pending.size
            setRack(currentPlayerIndex, currentRack + bag.draw(placedCount))
            pending = emptyMap()
            lastError = null
            pendingAppeal = null
            movesPlayed += 1
            skipTurnSwitchOnce = false
            if (turn.triggeredBonusSlots.isNotEmpty()) sfxPlayer.playBonusWon(musicController) else sfxPlayer.playEndOfTurn(musicController)
            deferredAfterTurnSummary = {
                if (turn.triggeredBonusSlots.isNotEmpty()) {
                    bonusTriggeringPlayerIndex = scoringPlayerIndex
                    applyBonusPrize(drawBonusPrize(awardedBonusPrizes, Random.Default))
                } else lastMessage = null
                if (racks.any { it.isEmpty() }) {
                    gameOver = true
                    sfxPlayer.playGameOver(musicController)
                } else if (!skipTurnSwitchOnce) {
                    currentPlayerIndex = 1 - scoringPlayerIndex
                }
            }
            pendingTurnSummary = TurnSummary(scoringPlayerName, turn.newWords.map { it.word }, awardedScoreDelta)
        }.onFailure { err ->
            sfxPlayer.playTurnRejected()
            lastError = describeError(err)
            val moveError = (err as? MoveException)?.error
            pendingAppeal = if (appealedMove == null && moveError is MoveError.InvalidWords && moveError.words.size == 1) {
                PendingAppeal(pending, moveError.words.single())
            } else null
        }
    }

    fun appealAccept() {
        val appeal = pendingAppeal ?: return
        applyMoveResult(engine.forceMove(appeal.move), appeal.move)
    }

    fun appealDiscard() {
        pendingAppeal = null
        pending.keys.toList().forEach(::returnPendingToRack)
        lastError = null
    }

    fun dismissError() {
        lastError = null
        pendingAppeal = null
    }

    fun dismissTurnSummary() {
        pendingTurnSummary = null
        deferredAfterTurnSummary.also { deferredAfterTurnSummary = null }?.invoke()
    }

    private fun applyBonusPrize(prize: BonusPrize) {
        awardedBonusPrizes = awardedBonusPrizes + prize
        val triggeringPlayer = players[bonusTriggeringPlayerIndex]
        when (prize) {
            is BonusPrize.MiniGame -> {
                lastMessage = "זכית בבונוס!"
                pendingBonusType = prize.type
            }
            is BonusPrize.Points -> {
                players = players.toMutableList().also { it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withAddedScore(prize.amount) }
                lastMessage = ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE.replace("%s", triggeringPlayer.name).replace("%d", prize.amount.toString())
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
            BonusPrize.ExtraTurn -> {
                skipTurnSwitchOnce = true
                lastMessage = "${triggeringPlayer.name} זוכה/ת במהלך נוסף!"
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
            BonusPrize.DoubleScoreNextTwoRounds -> {
                players = players.toMutableList().also { it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withMultiplier(2, 2) }
                lastMessage = "${triggeringPlayer.name} זוכה/ת בניקוד כפול (X2) לשני המהלכים הבאים!"
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
            BonusPrize.ExtraTurnQuadrupleScore -> {
                skipTurnSwitchOnce = true
                players = players.toMutableList().also { it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withMultiplier(4, 1) }
                lastMessage = "${triggeringPlayer.name} זוכה/ת במהלך נוסף בניקוד פי 4 (X4)!"
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
        }
    }

    fun dismissBonusCelebration() {
        pendingBonusCelebration = null
    }

    fun applyBonusOutcome(outcome: BonusOutcome) {
        val triggeringPlayer = players[bonusTriggeringPlayerIndex]
        players = players.toMutableList().also { it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withAddedScore(outcome.awardedScore) }
        lastMessage = ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE.replace("%s", triggeringPlayer.name).replace("%d", outcome.awardedScore.toString())
        pendingBonusType = null
        lastBonusScoreWasZero = outcome.awardedScore <= 0
        pendingBonusCelebration = lastMessage
    }

    fun placeLock(pos: Position, totalMoves: Int) {
        LockAction.placeLock(board, pos, totalMoves).onFailure { lastError = it.message }
    }

    fun swapLetters(rackIndices: List<Int>) {
        if (!SwapAction.canSwap(currentPlayer.score)) {
            lastError = "ניתן לבצע החלפה רק אם ברשותך לפחות 25 נקודות!"
            return
        }
        if (bag.isEmpty()) {
            lastError = "לא נותרו בקופה אותיות להחלפה!"
            return
        }
        val rack = currentRack.toMutableList()
        val toSwap = rackIndices.mapNotNull { rack.getOrNull(it) }
        toSwap.forEach { rack.remove(it) }
        setRack(currentPlayerIndex, rack + SwapAction.swap(bag, toSwap))
        players = players.toMutableList().also { it[currentPlayerIndex] = it[currentPlayerIndex].withAddedScore(-SwapAction.COST) }
    }

    private fun setRack(playerIndex: Int, tiles: List<Tile>) {
        racks = racks.toMutableList().also { it[playerIndex] = tiles }
    }

    private fun describeError(err: Throwable): String {
        val moveError = (err as? MoveException)?.error
        return when (moveError) {
            is MoveError.NotAligned -> "יש להניח את האותיות בשורה או בעמודה אחת"
            is MoveError.NeedsAtLeastTwoLettersFirstMove -> "יש להניח לפחות 2 אותיות במהלך הראשון"
            is MoveError.NotAttachedToExistingWords -> "יש לחבר את המילה החדשה למילים הקיימות"
            is MoveError.GapsInLine -> "לא ניתן להשאיר רווחים במילה"
            is MoveError.NoBonusOnFirstMove -> "לא ניתן לזכות בבונוס במהלך הראשון"
            is MoveError.OccupiedOrLockedSquare -> "המשבצת תפוסה או נעולה"
            is MoveError.InvalidWords -> ChallengeSystem.REJECTION_MESSAGE_TEMPLATE.replace("%s", currentPlayer.name)
            null -> err.message ?: "שגיאה לא ידועה"
        }
    }
}
