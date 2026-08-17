package il.cet.bonus.game

import android.content.Context

/** Persists the two player names across app launches, edited from the Settings
 * screen and read back when starting a new game. */
class PlayerNamesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var nameA: String
        get() = prefs.getString(KEY_NAME_A, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME_A, value).apply()

    var nameB: String
        get() = prefs.getString(KEY_NAME_B, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME_B, value).apply()

    companion object {
        private const val PREFS_NAME = "bonus_player_names_prefs"
        private const val KEY_NAME_A = "player_a_name"
        private const val KEY_NAME_B = "player_b_name"
    }
}
