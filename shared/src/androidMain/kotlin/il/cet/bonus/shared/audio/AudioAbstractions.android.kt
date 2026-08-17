package il.cet.bonus.shared.audio

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import il.cet.bonus.shared.PlatformContextHolder

actual class MusicController actual constructor() {
    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<String> = emptyList()
    private var index = 0
    private var theme: MusicCatalog.Theme = MusicCatalog.Theme.NEW

    actual fun setTheme(theme: MusicCatalog.Theme) {
        this.theme = theme
        playlist = MusicCatalog.playlist(theme).shuffled()
        index = 0
        start()
    }

    actual fun start() {
        if (playlist.isEmpty()) playlist = MusicCatalog.playlist(theme).shuffled()
        playCurrent()
    }

    private fun playCurrent() {
        mediaPlayer?.release()
        val fileName = playlist.getOrNull(index) ?: return
        val resName = fileName.substringBeforeLast('.')
        val resId = PlatformContextHolder.context.resources.getIdentifier(resName, "raw", PlatformContextHolder.context.packageName)
        if (resId == 0) return
        mediaPlayer = MediaPlayer.create(PlatformContextHolder.context, resId).apply {
            setOnCompletionListener {
                val nextIndex = index + 1
                if (nextIndex >= playlist.size) {
                    // Reshuffle for a fresh random order once the whole playlist has played.
                    playlist = MusicCatalog.playlist(theme).shuffled()
                    index = 0
                } else {
                    index = nextIndex
                }
                playCurrent()
            }
            start()
        }
    }

    actual fun pause() { mediaPlayer?.pause() }
    actual fun resume() { mediaPlayer?.start() ?: start() }
    actual fun release() { mediaPlayer?.release(); mediaPlayer = null }
}

actual class SfxPlayer actual constructor() {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    actual fun playTurnRejected() { runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200) } }
    actual fun playBonusWon(musicController: MusicController) = playOneShot("end_turn_bonus", musicController, true)
    actual fun playEndOfTurn(musicController: MusicController) = playOneShot("end_turn_regular", musicController, true)
    actual fun playGameOver(musicController: MusicController) = playOneShot("appl3", musicController, false)

    private fun playOneShot(resName: String, musicController: MusicController, resume: Boolean) {
        musicController.pause()
        val resId = PlatformContextHolder.context.resources.getIdentifier(resName, "raw", PlatformContextHolder.context.packageName)
        runCatching {
            MediaPlayer.create(PlatformContextHolder.context, resId).apply {
                setOnCompletionListener {
                    it.release()
                    if (resume) musicController.resume()
                }
                start()
            }
        }.onFailure { if (resume) musicController.resume() }
    }
}
