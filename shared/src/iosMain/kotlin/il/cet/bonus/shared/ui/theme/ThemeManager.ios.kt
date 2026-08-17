package il.cet.bonus.shared.ui.theme

import platform.Foundation.NSUserDefaults

actual class ThemeManager actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults
    actual var current: GameTheme
        get() = if ((defaults.stringForKey("selected_theme") ?: GameTheme.NEW.name) == GameTheme.OLD.name) GameTheme.OLD else GameTheme.NEW
        set(value) { defaults.setObject(value.name, forKey = "selected_theme") }
}
