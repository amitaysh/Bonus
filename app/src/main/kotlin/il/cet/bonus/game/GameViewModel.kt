package il.cet.bonus.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import il.cet.bonus.core.board.Board
import il.cet.bonus.core.dictionary.DictionaryRepository
import il.cet.bonus.core.game.GameEngine
import il.cet.bonus.core.game.LetterBag
import il.cet.bonus.core.game.LockAction
import il.cet.bonus.core.game.MoveException
import il.cet.bonus.core.game.Player
import il.cet.bonus.core.game.SwapAction
import il.cet.bonus.core.model.Position
import il.cet.bonus.core.model.Tile
import il.cet.bonus.core.bonus.BonusOutcome
import il.cet.bonus.core.bonus.BonusPrize
import il.cet.bonus.core.bonus.BonusType
import il.cet.bonus.core.bonus.drawBonusPrize
import il.cet.bonus.audio.SfxPlayer
import kotlin.random.Random

/** Number of tiles each player holds in their rack. Confirmed original rule (Hebrew
 * Wikipedia "בונוס (משחק מחשב)"): "כל שחקן מקבל אוסף של שמונה אותיות" - 8 tiles, not 7. */
private const val RACK_SIZE = 8

/** Snapshot needed to let the player appeal (עירעור) a single rejected word: the exact
 * placement that was rejected (so it can be committed if the appeal succeeds), and the
 * rejected word text (for the appeal dialog to display, and to guard the "only one
 * rejected word" rule per [il.cet.bonus.core.game.ChallengeSystem]). */
data class PendingAppeal(val move: Map<Position, Tile>, val rejectedWord: String)

/**
 * Drives a single local pass-and-play game: board state, two players, their racks, the
 * pending (not-yet-committed) tile placements for the current turn, and turn actions
 * (place word, swap letters, place a lock). UI-only concern (selection/drag state) is
 * layered separately in BoardScreen; this class owns all rules-affecting state.
 */
class GameViewModel(val dictionary: DictionaryRepository) : ViewModel() {

    val board = Board()
    private val bag = LetterBag()
    private val engine = GameEngine(board, dictionary)

    var players by mutableStateOf(listOf(Player(0, ""), Player(1, "")))
        private set
    var currentPlayerIndex by mutableStateOf(0)
        private set
    var racks by mutableStateOf(listOf<List<Tile>>(emptyList(), emptyList()))
        private set

    /** Tiles the current player has tentatively placed this turn, not yet committed. */
    var pending by mutableStateOf<Map<Position, Tile>>(emptyMap())
        private set

    var lastError by mutableStateOf<String?>(null)
        private set
    var lastMessage by mutableStateOf<String?>(null)
        private set
    var gameOver by mutableStateOf(false)
        private set

    /** Set when the current move was rejected for an invalid-word reason and the player
     * may appeal (עירעור) - per [il.cet.bonus.core.game.ChallengeSystem], only allowed
     * when exactly one word was rejected. Cleared once the popup is dismissed either way. */
    var pendingAppeal by mutableStateOf<PendingAppeal?>(null)
        private set

    /** Set when a joker was just placed on the board and the player must pick which
     * letter it represents before the turn can be completed. */
    var pendingJokerChoice by mutableStateOf<Position?>(null)
        private set

    /** Set right after a turn scores non-mini-game bonus points/extra-turn/multiplier
     * prizes, so the UI can show a celebratory popup (distinct from the mini-game overlay
     * driven by [pendingBonusType]). */
    var pendingBonusCelebration by mutableStateOf<String?>(null)
        private set

    /** True when the bonus/mini-game outcome shown in [pendingBonusCelebration] scored
     * zero points (e.g. an incorrect mini-game answer) - the UI shows a sad popup instead
     * of a happy one in that case. */
    var lastBonusScoreWasZero by mutableStateOf(false)
        private set

    /** Number of completed turns so far, shown as the "מהלכים" (moves) HUD counter, per
     * confirmed original HUD layout (see plan.md "Confirmed via real gameplay video"). */
    var movesPlayed by mutableStateOf(0)
        private set

    /** Tiles remaining in the draw bag, shown as the "קופה" (pot/bag) HUD indicator. */
    val tilesRemainingInBag: Int get() = bag.tilesLeft

    /** Set right after a turn triggers an (as-yet unplayed) bonus mini-game overlay. */
    var pendingBonusType by mutableStateOf<BonusType?>(null)
        private set
    private var bonusTriggeringPlayerIndex: Int = 0

    fun startGame(playerAName: String, playerBName: String) {
        players = listOf(Player(0, playerAName), Player(1, playerBName))
        currentPlayerIndex = 0
        racks = listOf(bag.draw(RACK_SIZE), bag.draw(RACK_SIZE))
        pending = emptyMap()
        gameOver = false
        awardedBonusPrizes = emptySet()
        skipTurnSwitchOnce = false
    }

    val currentRack: List<Tile> get() = racks.getOrElse(currentPlayerIndex) { emptyList() }
    val currentPlayer: Player get() = players[currentPlayerIndex]

    /** Moves a tile from the current player's rack onto a pending board position (tap-to-place or drop target).
     * If the tile is an unassigned joker, immediately prompts the player to pick which
     * letter it stands for via [pendingJokerChoice] (resolved by [chooseJokerLetter]). */
    fun placeFromRack(rackIndex: Int, pos: Position) {
        val rack = currentRack
        if (rackIndex !in rack.indices) return
        if (!board.isPlaceable(pos) || pos in pending) return
        val tile = rack[rackIndex]
        val newRack = rack.toMutableList().also { it.removeAt(rackIndex) }
        setRack(currentPlayerIndex, newRack)
        pending = pending + (pos to tile)
        if (tile is Tile.JokerTile && tile.chosenLetter == null) {
            pendingJokerChoice = pos
        }
    }

    /** Resolves the joker-letter picker popup for the tile at [pos], assigning [letter] to it. */
    fun chooseJokerLetter(pos: Position, letter: il.cet.bonus.core.model.Letter) {
        val tile = pending[pos] as? Tile.JokerTile ?: return
        pending = pending + (pos to tile.copy(chosenLetter = letter))
        pendingJokerChoice = null
    }

    /** Returns a tentatively-placed tile back to the rack (tap/drag a pending tile away).
     * A joker that had a letter assigned reverts to being unassigned - it's still the same
     * joker, so the player is free to change their mind and assign a different letter (or
     * a different square) next time it's placed. */
    fun returnPendingToRack(pos: Position) {
        val tile = pending[pos] ?: return
        pending = pending - pos
        if (pendingJokerChoice == pos) pendingJokerChoice = null
        val restored = if (tile is Tile.JokerTile) Tile.JokerTile(chosenLetter = null) else tile
        setRack(currentPlayerIndex, currentRack + restored)
    }

    /** All prize identities already awarded this game - a prize can never repeat (confirmed rule). */
    private var awardedBonusPrizes: Set<BonusPrize> = emptySet()

    /** Set when a non-mini-game prize (extra turn / multiplier) grants the current player
     * another immediate turn, so [completeTurn] knows not to pass the turn to the opponent. */
    private var skipTurnSwitchOnce: Boolean = false

    fun completeTurn() {
        if (pending.isEmpty()) {
            lastError = "לא הונחו אותיות"
            return
        }
        if (pendingJokerChoice != null) {
            lastError = "יש לבחור אות עבור הג'וקר לפני סיום התור"
            return
        }
        val result = engine.proposeMove(pending)
        applyMoveResult(result, appealedMove = null)
    }

    private fun applyMoveResult(result: Result<il.cet.bonus.core.game.TurnResult>, appealedMove: Map<Position, Tile>?) {
        result.onSuccess { turn ->
            players = players.toMutableList().also {
                it[currentPlayerIndex] = it[currentPlayerIndex].withScoredTurn(turn.scoreDelta)
            }
            val placedCount = appealedMove?.size ?: pending.size
            val drawn = bag.draw(placedCount)
            setRack(currentPlayerIndex, currentRack + drawn)
            pending = emptyMap()
            lastError = null
            pendingAppeal = null
            movesPlayed += 1
            skipTurnSwitchOnce = false
            SfxPlayer.playTurnAccepted()
            if (turn.triggeredBonusSlots.isNotEmpty()) {
                bonusTriggeringPlayerIndex = currentPlayerIndex
                applyBonusPrize(drawBonusPrize(awardedBonusPrizes, Random.Default))
            } else {
                lastMessage = null
            }
            if (bag.isEmpty()) {
                gameOver = true
            } else if (!skipTurnSwitchOnce) {
                currentPlayerIndex = 1 - currentPlayerIndex
            }
        }.onFailure { err ->
            SfxPlayer.playTurnRejected()
            lastError = describeError(err)
            // Offer an appeal (עירעור) only when this was a fresh dictionary rejection
            // (not itself the result of a failed appeal) with exactly one rejected word,
            // per ChallengeSystem's confirmed rule.
            val moveError = (err as? MoveException)?.error
            pendingAppeal = if (appealedMove == null && moveError is il.cet.bonus.core.game.MoveError.InvalidWords &&
                moveError.words.size == 1
            ) {
                PendingAppeal(pending, moveError.words.single())
            } else {
                null
            }
        }
    }

    /** Player disputes the rejection shown in [pendingAppeal] and wins the appeal
     * (עירעור) - the disputed word is accepted anyway per [il.cet.bonus.core.game.ChallengeSystem]. */
    fun appealAccept() {
        val appeal = pendingAppeal ?: return
        val result = engine.forceMove(appeal.move)
        applyMoveResult(result, appealedMove = appeal.move)
    }

    /** Player discards the rejected move instead of appealing: tiles return to the rack
     * unchanged and the popup is dismissed. */
    fun appealDiscard() {
        pendingAppeal = null
        pending.keys.toList().forEach { returnPendingToRack(it) }
        lastError = null
    }

    /** Dismisses the current rejection popup without appealing (for rejections that
     * aren't eligible for appeal, e.g. structural errors or more than one bad word). */
    fun dismissError() {
        lastError = null
        pendingAppeal = null
    }


    /**
     * Resolves a drawn [BonusPrize]. Mini-games are shown as an overlay (existing flow via
     * [pendingBonusType] + [applyBonusOutcome]); every other prize is resolved immediately.
     */
    private fun applyBonusPrize(prize: BonusPrize) {
        awardedBonusPrizes = awardedBonusPrizes + prize
        SfxPlayer.playBonusWon()
        val triggeringPlayer = players[bonusTriggeringPlayerIndex]
        when (prize) {
            is BonusPrize.MiniGame -> {
                lastMessage = "זכית בבונוס!"
                pendingBonusType = prize.type
            }
            is BonusPrize.Points -> {
                players = players.toMutableList().also {
                    it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withAddedScore(prize.amount)
                }
                lastMessage = il.cet.bonus.core.game.ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE
                    .format(triggeringPlayer.name, prize.amount)
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
                players = players.toMutableList().also {
                    it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withMultiplier(2, 2)
                }
                lastMessage = "${triggeringPlayer.name} זוכה/ת בניקוד כפול (X2) לשני המהלכים הבאים!"
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
            BonusPrize.ExtraTurnQuadrupleScore -> {
                skipTurnSwitchOnce = true
                players = players.toMutableList().also {
                    it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withMultiplier(4, 1)
                }
                lastMessage = "${triggeringPlayer.name} זוכה/ת במהלך נוסף בניקוד פי 4 (X4)!"
                lastBonusScoreWasZero = false
                pendingBonusCelebration = lastMessage
            }
        }
    }

    /** Dismisses the celebratory bonus-win popup shown after a non-mini-game prize. */
    fun dismissBonusCelebration() {
        pendingBonusCelebration = null
    }

    /** Applies the outcome of a played bonus mini-game to the triggering player's score. */
    fun applyBonusOutcome(outcome: BonusOutcome) {
        val triggeringPlayer = players[bonusTriggeringPlayerIndex]
        players = players.toMutableList().also {
            it[bonusTriggeringPlayerIndex] = it[bonusTriggeringPlayerIndex].withAddedScore(outcome.awardedScore)
        }
        // Reuse the exact confirmed original wording (see ChallengeSystem.kt doc).
        lastMessage = il.cet.bonus.core.game.ChallengeSystem.BONUS_AWARD_MESSAGE_TEMPLATE
            .format(triggeringPlayer.name, outcome.awardedScore)
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
        val replacements = SwapAction.swap(bag, toSwap)
        setRack(currentPlayerIndex, rack + replacements)
        players = players.toMutableList().also {
            it[currentPlayerIndex] = it[currentPlayerIndex].withAddedScore(-SwapAction.COST)
        }
    }

    private fun setRack(playerIndex: Int, tiles: List<Tile>) {
        racks = racks.toMutableList().also { it[playerIndex] = tiles }
    }

    private fun describeError(err: Throwable): String {
        val moveError = (err as? MoveException)?.error
        return when (moveError) {
            is il.cet.bonus.core.game.MoveError.NotAligned -> "יש להניח את האותיות בשורה או בעמודה אחת"
            is il.cet.bonus.core.game.MoveError.NeedsAtLeastTwoLettersFirstMove -> "יש להניח לפחות 2 אותיות במהלך הראשון"
            is il.cet.bonus.core.game.MoveError.NotAttachedToExistingWords -> "יש לחבר את המילה החדשה למילים הקיימות"
            is il.cet.bonus.core.game.MoveError.GapsInLine -> "לא ניתן להשאיר רווחים במילה"
            is il.cet.bonus.core.game.MoveError.NoBonusOnFirstMove -> "לא ניתן לזכות בבונוס במהלך הראשון"
            is il.cet.bonus.core.game.MoveError.OccupiedOrLockedSquare -> "המשבצת תפוסה או נעולה"
            is il.cet.bonus.core.game.MoveError.InvalidWords -> {
                // Reuse the exact confirmed original wording (see ChallengeSystem.kt doc).
                il.cet.bonus.core.game.ChallengeSystem.REJECTION_MESSAGE_TEMPLATE.format(currentPlayer.name)
            }
            null -> err.message ?: "שגיאה לא ידועה"
        }
    }
}
