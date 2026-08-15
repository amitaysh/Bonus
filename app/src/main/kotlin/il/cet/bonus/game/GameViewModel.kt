package il.cet.bonus.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import il.cet.bonus.audio.MusicController
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
class GameViewModel(
    application: Application,
    val dictionary: DictionaryRepository,
    private val musicController: MusicController,
) : AndroidViewModel(application) {

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

    // --- TEMPORARY DEBUG-ONLY helper, remove once bonus mini-games are fully verified ---
    /** Index into [BonusType.entries] of the next debug-triggered mini-game; null once
     * every type has been shown at least once via [debugTriggerNextBonus]. */
    private var debugBonusIndex: Int = 0

    /** Debug-only: triggers the next bonus mini-game type in sequence (ANAGRAM,
     * FILL_IN_BLANK, SHARED_LETTER_TWO_WORDS, CROSSWORD_BUILD, SHARED_LETTER_THREE_WORDS),
     * one per tap, for manual testing without needing to actually land on a bonus slot.
     * Reports "no more puzzles" via [lastMessage] once all types have been shown. */
    fun debugTriggerNextBonus() {
        val types = BonusType.entries
        if (debugBonusIndex >= types.size) {
            lastMessage = "אין עוד חידות בונוס (debug)"
            return
        }
        bonusTriggeringPlayerIndex = currentPlayerIndex
        pendingBonusType = types[debugBonusIndex]
        debugBonusIndex++
    }
    // --- end TEMPORARY DEBUG-ONLY helper ---

    /** Snapshot shown by the end-of-turn summary popup (per original's "words + score
     * gained" popup): the words just scored, this turn's total, and the scoring
     * player's name. Cleared once dismissed, at which point any pending bonus-slot
     * prize/mini-game and the turn switch actually take effect. */
    data class TurnSummary(val playerName: String, val words: List<String>, val scoreDelta: Int)

    var pendingTurnSummary by mutableStateOf<TurnSummary?>(null)
        private set

    /** Deferred until [pendingTurnSummary] is dismissed, so the bonus/mini-game popup
     * never overlaps the turn summary popup. */
    private var deferredAfterTurnSummary: (() -> Unit)? = null


    /** The player who triggered the currently pending bonus mini-game (see
     * [pendingBonusType]) - used by [BonusMiniGameScreen]'s crossword-build completion
     * summary, which needs the player's name for its "X, אתה מקבל Y נקודות." line. */
    val bonusTriggeringPlayerName: String get() = players[bonusTriggeringPlayerIndex].name

    /** (Re)starts a fresh game: brand-new board/bag/engine (so no tiles, locks, used-bonus-slots,
     * or bag contents survive from a previous game), plus all per-game UI/turn state reset. */
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
        // A new game always (re)starts the regular background music, in case the
        // previous game ended via game-over (which stops it until a new game starts -
        // see playGameOver's doc comment).
        musicController.resume()
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

    /** Drags an already-placed (not yet committed) tile from [from] to [to] without
     * returning it to the rack first, so its joker-letter assignment (if any) and the
     * pending-jokerChoice popup target both move with it directly. No-op if [from] isn't
     * a pending tile, [to] is already occupied, or [to] isn't a placeable square. */
    fun movePendingTile(from: Position, to: Position) {
        if (from == to) return
        val tile = pending[from] ?: return
        if (!board.isPlaceable(to) || to in pending || board.tileAt(to) != null) return
        pending = pending - from + (to to tile)
        if (pendingJokerChoice == from) pendingJokerChoice = to
    }

    /** Ends the game immediately (used when the player presses "יציאה" / exit-game),
     * same end-state as running out of rack tiles - shows the win/tie popup with the
     * game-over music, per confirmed end-game rule (exit button also ends the game, not
     * just a silent bag depletion). */
    fun endGameManually() {
        if (gameOver) return
        gameOver = true
        SfxPlayer.playGameOver(getApplication(), musicController)
    }


    private var awardedBonusPrizes: Set<BonusPrize> = emptySet()

    /** Set when a non-mini-game prize (extra turn / multiplier) grants the current player
     * another immediate turn, so [completeTurn] knows not to pass the turn to the opponent. */
    private var skipTurnSwitchOnce: Boolean = false

    fun completeTurn() {
        if (pendingJokerChoice != null) {
            lastError = "יש לבחור אות עבור הג'וקר לפני סיום התור"
            return
        }
        // A player may validly end their turn without placing any letters (pass) - they
        // score 0 points for the turn and play simply moves to the other player.
        if (pending.isEmpty()) {
            passTurn()
            return
        }
        val result = engine.proposeMove(pending)
        applyMoveResult(result, appealedMove = null)
    }

    /** Player ends their turn without placing any tiles: scores 0, no tiles drawn, and
     * (per the confirmed end-game rule) play simply passes to the other player - unless
     * that emptied the game via a rack running out, which can't happen on a pass. */
    private fun passTurn() {
        val scoringPlayerIndex = currentPlayerIndex
        val scoringPlayerName = players[scoringPlayerIndex].name
        lastError = null
        pendingAppeal = null
        movesPlayed += 1
        skipTurnSwitchOnce = false
        SfxPlayer.playEndOfTurn(getApplication(), musicController)
        deferredAfterTurnSummary = {
            lastMessage = null
            currentPlayerIndex = 1 - scoringPlayerIndex
        }
        pendingTurnSummary = TurnSummary(
            playerName = scoringPlayerName,
            words = emptyList(),
            scoreDelta = 0,
        )
    }

    private fun applyMoveResult(result: Result<il.cet.bonus.core.game.TurnResult>, appealedMove: Map<Position, Tile>?) {
        result.onSuccess { turn ->
            val scoringPlayerIndex = currentPlayerIndex
            val scoringPlayerName = players[scoringPlayerIndex].name
            // Capture the multiplier BEFORE it's consumed/cleared by withScoredTurn, so
            // the end-of-turn summary popup can show the actual (multiplied) points
            // awarded instead of the raw word score - otherwise an active X2/X4 bonus
            // silently doubled/quadrupled the player's real score while the popup kept
            // showing the un-multiplied number, making the multiplier look broken.
            val activeMultiplier = players[scoringPlayerIndex].scoreMultiplier
            players = players.toMutableList().also {
                it[currentPlayerIndex] = it[currentPlayerIndex].withScoredTurn(turn.scoreDelta)
            }
            val awardedScoreDelta = turn.scoreDelta * activeMultiplier
            val placedCount = appealedMove?.size ?: pending.size
            val drawn = bag.draw(placedCount)
            setRack(currentPlayerIndex, currentRack + drawn)
            pending = emptyMap()
            lastError = null
            pendingAppeal = null
            movesPlayed += 1
            skipTurnSwitchOnce = false
            // Note: no separate "tick" tone here anymore - playing playTurnAccepted()
            // (a ToneGenerator blip) at the same time as the real end-of-turn music clip
            // below caused an audible double "tick + music" overlap. The real audio cue
            // (regular or bonus) below IS the turn-accepted sound now.
            // Per user feedback: the end-of-turn cue (regular or bonus variant) must
            // play right when the player clicks "complete turn" (i.e. now), not when the
            // score-summary popup is later dismissed - and only ONE of the two cues
            // should ever play for a given turn (previously both could fire: the bonus
            // cue from the deferred bonus-prize draw, plus the regular cue in
            // dismissTurnSummary, overlapping audibly).
            if (turn.triggeredBonusSlots.isNotEmpty()) {
                SfxPlayer.playBonusWon(getApplication(), musicController)
            } else {
                SfxPlayer.playEndOfTurn(getApplication(), musicController)
            }
            // Defer the bonus-prize draw and turn switch until the end-of-turn score
            // summary popup (words scored + points gained, per the original game's
            // "המשך" popup) is dismissed, so the two popups never overlap.
            deferredAfterTurnSummary = {
                if (turn.triggeredBonusSlots.isNotEmpty()) {
                    bonusTriggeringPlayerIndex = scoringPlayerIndex
                    applyBonusPrize(drawBonusPrize(awardedBonusPrizes, Random.Default))
                } else {
                    lastMessage = null
                }
                // Per confirmed original rule: the game ends only when one of the
                // *players* runs out of letters in their own rack (not when the shared
                // bag empties, since each player may still hold a full rack of tiles
                // drawn earlier - see TODO.md end-game item).
                if (racks.any { it.isEmpty() }) {
                    gameOver = true
                    SfxPlayer.playGameOver(getApplication(), musicController)
                } else if (!skipTurnSwitchOnce) {
                    currentPlayerIndex = 1 - scoringPlayerIndex
                }
            }
            pendingTurnSummary = TurnSummary(
                playerName = scoringPlayerName,
                words = turn.newWords.map { it.word },
                scoreDelta = awardedScoreDelta,
            )
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

    /** Dismisses the end-of-turn score-summary popup and runs whatever was deferred
     * behind it (bonus-prize draw / mini-game launch / turn switch / game-over check).
     * The end-of-turn / bonus-won audio cue itself now plays immediately in
     * [applyMoveResult] (right when the player clicks "complete turn"), not here. */
    fun dismissTurnSummary() {
        pendingTurnSummary = null
        val action = deferredAfterTurnSummary
        deferredAfterTurnSummary = null
        action?.invoke()
    }


    /**
     * Resolves a drawn [BonusPrize]. Mini-games are shown as an overlay (existing flow via
     * [pendingBonusType] + [applyBonusOutcome]); every other prize is resolved immediately.
     */
    private fun applyBonusPrize(prize: BonusPrize) {
        awardedBonusPrizes = awardedBonusPrizes + prize
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
