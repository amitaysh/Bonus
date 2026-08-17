package il.cet.bonus.shared.ui.theme

enum class GameTheme { OLD, NEW }

expect class ThemeManager() {
    var current: GameTheme
}
