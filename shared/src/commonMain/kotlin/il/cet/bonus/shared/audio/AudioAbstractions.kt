package il.cet.bonus.shared.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

enum class MusicTheme { OLD, NEW }

enum class MusicTrack(val fileName: String, val theme: MusicTheme) {
    FELICITY_OLD("felicity_old.mp3", MusicTheme.OLD),
    CONCERTO3_OLD("concerto3_old.mp3", MusicTheme.OLD),
    ENTERTAINER_OLD("entertainer_old.mp3", MusicTheme.OLD),
    MAPLE_LEAF_OLD("maple_leaf_old.mp3", MusicTheme.OLD),
    EGYPTIENNE_OLD("egyptienne_old.mp3", MusicTheme.OLD),
    CANTINA_OLD("cantina_old.mp3", MusicTheme.OLD),
    SOLFEGGIETTO_OLD("solfeggietto_old.mp3", MusicTheme.OLD),
    SAMBMOKA_OLD("sambmoka_old.mp3", MusicTheme.OLD),
    HOOKED_OLD("hooked_old.mp3", MusicTheme.OLD),
    PASSAGE_OLD("passage_old.mp3", MusicTheme.OLD),
    MAMB_OLD("mamb_old.mp3", MusicTheme.OLD),
    BUSINESS_OLD("business_old.mp3", MusicTheme.OLD),
    TITLE_OLD("title_old.mp3", MusicTheme.OLD),
    HELP_OLD("help_old.mp3", MusicTheme.OLD),
    FELICITY_NEW("felicity_new.mp3", MusicTheme.NEW),
    CONCERTO3_NEW("concerto3_new.mp3", MusicTheme.NEW),
    ENTERTAINER_NEW("entertainer_new.mp3", MusicTheme.NEW),
    MAPLE_LEAF_NEW("maple_leaf_new.mp3", MusicTheme.NEW),
    EGYPTIENNE_NEW("egyptienne_new.mp3", MusicTheme.NEW),
    CANTINA_NEW("cantina_new.mp3", MusicTheme.NEW),
    SOLFEGGIETTO_NEW("solfeggietto_new.mp3", MusicTheme.NEW),
    BADINERIE_NEW("badinerie_new.mp3", MusicTheme.NEW),
    MOONLIGHT_SONATA_NEW("moonlight_sonata_new.mp3", MusicTheme.NEW),
    EINE_KLEINE_NACHTMUSIK_NEW("eine_kleine_nachtmusik_new.mp3", MusicTheme.NEW),
    TOREADORS_NEW("toreadors_new.mp3", MusicTheme.NEW),
    PRINCE_OF_DENMARK_NEW("prince_of_denmark_new.mp3", MusicTheme.NEW),
    ROMEO_JULIET_NEW("romeo_juliet_new.mp3", MusicTheme.NEW),
    HALLELUJAH_NEW("hallelujah_new.mp3", MusicTheme.NEW),
    TURKISH_MARCH_NEW("turkish_march_new.mp3", MusicTheme.NEW),
    SYMPHONY_40_NEW("symphony_40_new.mp3", MusicTheme.NEW),
    TCHAIKOVSKY_CONCERTO1_NEW("tchaikovsky_concerto1_new.mp3", MusicTheme.NEW),
    TICO_TICO_NEW("tico_tico_new.mp3", MusicTheme.NEW),
    FANTAISIE_IMPROMPTU_NEW("fantaisie_impromptu_new.mp3", MusicTheme.NEW),
}

object MusicCatalog {
    enum class Theme { OLD, NEW }
    fun playlist(theme: Theme): List<String> = MusicTrack.entries.filter { it.theme == if (theme == Theme.OLD) MusicTheme.OLD else MusicTheme.NEW }.map { it.fileName }
}

expect class MusicController() {
    fun setTheme(theme: MusicCatalog.Theme)
    fun start()
    fun pause()
    fun resume()
    fun release()
}

expect class SfxPlayer() {
    fun playTurnRejected()
    fun playBonusWon(musicController: MusicController)
    fun playEndOfTurn(musicController: MusicController)
    fun playGameOver(musicController: MusicController)
}

@Composable
fun rememberMusicController(): MusicController = remember { MusicController() }

@Composable
fun rememberSfxPlayer(): SfxPlayer = remember { SfxPlayer() }
