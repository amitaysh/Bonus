package il.cet.bonus.ui.theme

import android.content.Context

/** Visual + music theme selection ("old"/"new"), persisted across app launches. */
enum class GameTheme { OLD, NEW }

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var current: GameTheme
        get() = if (prefs.getString(KEY_THEME, DEFAULT.name) == GameTheme.OLD.name) GameTheme.OLD else GameTheme.NEW
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    companion object {
        private const val PREFS_NAME = "bonus_theme_prefs"
        private const val KEY_THEME = "selected_theme"
        val DEFAULT = GameTheme.NEW
    }
}
