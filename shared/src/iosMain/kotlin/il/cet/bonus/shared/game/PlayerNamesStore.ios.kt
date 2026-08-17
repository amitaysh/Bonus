package il.cet.bonus.shared.game

import platform.Foundation.NSUserDefaults

actual class PlayerNamesStore actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults
    actual var nameA: String
        get() = defaults.stringForKey("player_a_name") ?: ""
        set(value) { defaults.setObject(value, forKey = "player_a_name") }
    actual var nameB: String
        get() = defaults.stringForKey("player_b_name") ?: ""
        set(value) { defaults.setObject(value, forKey = "player_b_name") }
}
