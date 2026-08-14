package il.cet.bonus.core.bonus

/**
 * The 5 real bonus mini-game types, recovered verbatim from BON.EXE game text (see
 * plan.md "Verified original-game data"). Intentionally does NOT include the C#
 * proxy's unconfirmed `Score1/25/40/100`, `AnotherTurn`, `AnotherTurn4`, `Next2` types -
 * no textual evidence of those was found in the original.
 */
enum class BonusType {
    /** Build a word from ALL of the given (scrambled) letters. Dynamic time/points. */
    ANAGRAM,

    /** Complete the missing letters in a single shown word. Dynamic time/points. */
    FILL_IN_BLANK,

    /** Fill in one missing letter shared by two words. Fixed 20s / 40pts. */
    SHARED_LETTER_TWO_WORDS,

    /** Build a mini-crossword from as many letters as possible; score = sum of all words formed. */
    CROSSWORD_BUILD,

    /** Fill in one missing letter shared by three words. Fixed 30s / 100pts. */
    SHARED_LETTER_THREE_WORDS,
}

/** Static timing/scoring definition for a bonus type. `null` means "dynamic - computed at play time". */
data class BonusDefinition(
    val type: BonusType,
    val fixedTimeSeconds: Int?,
    val fixedScore: Int?,
)

val BONUS_DEFINITIONS: List<BonusDefinition> = listOf(
    BonusDefinition(BonusType.ANAGRAM, fixedTimeSeconds = null, fixedScore = null),
    BonusDefinition(BonusType.FILL_IN_BLANK, fixedTimeSeconds = null, fixedScore = null),
    BonusDefinition(BonusType.SHARED_LETTER_TWO_WORDS, fixedTimeSeconds = 20, fixedScore = 40),
    BonusDefinition(BonusType.CROSSWORD_BUILD, fixedTimeSeconds = null, fixedScore = null),
    BonusDefinition(BonusType.SHARED_LETTER_THREE_WORDS, fixedTimeSeconds = 30, fixedScore = 100),
)

/** Outcome of playing a bonus mini-game, to be added to the triggering player's score. */
data class BonusOutcome(val type: BonusType, val awardedScore: Int)
