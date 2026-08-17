package il.cet.bonus.shared.ui.theme

import android.content.Context
import il.cet.bonus.shared.PlatformContextHolder

actual class ThemeManager actual constructor() {
    private val prefs = PlatformContextHolder.context.getSharedPreferences("bonus_theme_prefs", Context.MODE_PRIVATE)
    actual var current: GameTheme
        get() = if (prefs.getString("selected_theme", GameTheme.NEW.name) == GameTheme.OLD.name) GameTheme.OLD else GameTheme.NEW
        set(value) = prefs.edit().putString("selected_theme", value.name).apply()
}
