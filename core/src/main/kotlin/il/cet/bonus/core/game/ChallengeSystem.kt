package il.cet.bonus.core.game

/**
 * The word-challenge/appeal system (עירעור), confirmed from BON.EXE text and entirely
 * absent from the C# proxy. A player may dispute the system's rejection of a word, but
 * only when their move had exactly one rejected word. In human-vs-human play the appeal
 * requires the opponent's approval before being resolved; in human-vs-computer play there
 * is no such approval step. No appeals are allowed during bonus mini-games.
 *
 * Outcome messages recovered verbatim: "צדקת בשיפוט" (you were right to dispute) awards
 * +1 point; "טעית בשיפוט" (you were wrong to dispute) costs -1 point.
 *
 * Additional exact wording confirmed directly from real gameplay footage (see plan.md
 * "Confirmed via real gameplay video"): the rejection dialog itself (shown before any
 * appeal is even raised) reads "<שם השחקן>, אתה אינך מקבל אף נקודה אחת" ("<player>, you
 * don't get even one point"), with "ערעור" (dispute) / "המשך" (continue) buttons - see
 * `REJECTION_MESSAGE_TEMPLATE` below for reuse in the UI layer.
 */
object ChallengeSystem {
    const val CORRECT_DISPUTE_DELTA: Int = 1
    const val INCORRECT_DISPUTE_DELTA: Int = -1

    /** Exact wording confirmed from gameplay footage; `%s` is the player's name. */
    const val REJECTION_MESSAGE_TEMPLATE: String = "%s, אתה אינך מקבל אף נקודה אחת."
    const val DISPUTE_BUTTON_LABEL: String = "ערעור"
    const val CONTINUE_BUTTON_LABEL: String = "המשך"

    /** Exact wording confirmed from gameplay footage for a successful bonus outcome; `%s` = player name, `%d` = points. */
    const val BONUS_AWARD_MESSAGE_TEMPLATE: String = "%s, אתה מקבל %d נקודות."

    enum class OpponentKind { HUMAN, COMPUTER }

    data class ChallengeRequest(val disputedWord: String, val opponentKind: OpponentKind)

    sealed class ChallengeError {
        object MoreThanOneWordRejected : ChallengeError()
        object NotDuringBonusMiniGame : ChallengeError()
    }

    /** Only a single rejected word may be challenged per move. */
    fun canChallenge(rejectedWords: List<String>, isDuringBonusMiniGame: Boolean): Result<ChallengeRequest> {
        if (isDuringBonusMiniGame) {
            return Result.failure(IllegalStateException("Appeals are not allowed during bonus mini-games"))
        }
        if (rejectedWords.size != 1) {
            return Result.failure(IllegalStateException("Can only dispute a move with exactly one rejected word"))
        }
        return Result.success(ChallengeRequest(rejectedWords.single(), OpponentKind.HUMAN))
    }

    /**
     * Resolves a challenge. [wordIsActuallyValid] represents the ruling (e.g. approved by
     * the opponent, or in human-vs-computer play, decided by an authoritative dictionary
     * check / referee). Returns the score delta to apply to the challenging player.
     */
    fun resolve(wordIsActuallyValid: Boolean): Int =
        if (wordIsActuallyValid) CORRECT_DISPUTE_DELTA else INCORRECT_DISPUTE_DELTA
}
