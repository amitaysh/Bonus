package il.cet.bonus.core.model

/**
 * A single Hebrew letter tile definition.
 *
 * Scores are verified 1:1 against the original 1993 DOS "Bonus" executable
 * (BON.EXE v0.64, letter score table extracted via reverse engineering).
 * Tile counts (bag composition) were corrected against the same source -
 * the original bag has 82 tiles total (80 letters + 2 jokers).
 *
 * There are no sofit (final-form) letter tiles in the original game; words
 * are validated using regular letter forms only, even at the end of a word.
 */
enum class Letter(val hebrew: Char, val score: Int, val tileCount: Int) {
    ALEF('א', 7, 3),
    BET('ב', 4, 3),
    GIMEL('ג', 8, 2),
    DALED('ד', 5, 3),
    HEY('ה', 2, 5),
    VAV('ו', 1, 7),
    ZAIN('ז', 9, 2),
    HET('ח', 4, 3),
    TET('ט', 7, 3),
    YUD('י', 1, 6),
    KAF('כ', 7, 3),
    LAMED('ל', 4, 4),
    MEM('מ', 2, 5),
    NUN('נ', 3, 4),
    SAMEH('ס', 6, 3),
    AIN('ע', 5, 3),
    PE('פ', 5, 3),
    ZADIK('צ', 7, 3),
    KUF('ק', 4, 3),
    REISH('ר', 3, 4),
    SHIN('ש', 4, 3),
    TAV('ת', 2, 5);

    companion object {
        /** Number of joker (blank) tiles in the bag, per the original game. */
        const val JOKER_COUNT: Int = 2

        /** Joker tiles score 0 points regardless of the letter they represent. */
        const val JOKER_SCORE: Int = 0

        /** Total tiles in the bag: sum of all letter tiles + jokers. */
        val TOTAL_TILE_COUNT: Int = entries.sumOf { it.tileCount } + JOKER_COUNT

        fun fromHebrew(c: Char): Letter? = entries.firstOrNull { it.hebrew == c }
    }
}
