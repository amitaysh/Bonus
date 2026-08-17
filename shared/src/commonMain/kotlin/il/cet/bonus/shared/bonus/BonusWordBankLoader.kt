package il.cet.bonus.shared.bonus

import il.cet.bonus.core.bonus.BonusPuzzleGenerator

expect object BonusWordBankLoader {
    fun create(): BonusPuzzleGenerator
}
