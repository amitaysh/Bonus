package il.cet.bonus.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Plays the old/new theme music playlist via Media3 ExoPlayer, auto-advancing through
 * whichever tracks are available for the active theme (see MusicCatalog's doc comment
 * about the incomplete old/new pairing - this controller does not assume 1:1 pairing).
 */
class MusicController(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    private var theme: MusicCatalog.Theme = MusicCatalog.Theme.NEW
    private var muted: Boolean = false

    private fun player(): ExoPlayer = exoPlayer ?: ExoPlayer.Builder(context).build().also {
        it.repeatMode = Player.REPEAT_MODE_ALL
        it.shuffleModeEnabled = true
        exoPlayer = it
    }

    fun setTheme(theme: MusicCatalog.Theme) {
        this.theme = theme
        reloadPlaylist()
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        player().volume = if (muted) 0f else 1f
    }

    fun start() {
        reloadPlaylist()
        player().playWhenReady = true
    }

    fun stop() {
        exoPlayer?.stop()
    }

    /** Temporarily pauses playback without resetting the playlist/position - used to duck
     * out for a short one-shot sting (turn-end cue, game-over applause) via [resume]. */
    fun pause() {
        exoPlayer?.playWhenReady = false
    }

    /** Resumes playback from where [pause] left off. */
    fun resume() {
        exoPlayer?.playWhenReady = true
    }

    fun next() {
        player().seekToNextMediaItem()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun reloadPlaylist() {
        val p = player()
        val items = MusicCatalog.playlist(theme).map { resId ->
            MediaItem.fromUri("android.resource://${context.packageName}/$resId")
        }
        p.setMediaItems(items)
        p.prepare()
        p.volume = if (muted) 0f else 1f
    }
}
