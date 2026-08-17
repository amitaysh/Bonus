package il.cet.bonus.shared.game

import android.content.Context
import il.cet.bonus.shared.PlatformContextHolder

actual class PlayerNamesStore actual constructor() {
    private val prefs = PlatformContextHolder.context.getSharedPreferences("bonus_player_names_prefs", Context.MODE_PRIVATE)
    actual var nameA: String
        get() = prefs.getString("player_a_name", null).orEmpty()
        set(value) = prefs.edit().putString("player_a_name", value).apply()
    actual var nameB: String
        get() = prefs.getString("player_b_name", null).orEmpty()
        set(value) = prefs.edit().putString("player_b_name", value).apply()
}
