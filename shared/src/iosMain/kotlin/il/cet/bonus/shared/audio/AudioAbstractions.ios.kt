package il.cet.bonus.shared.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.SystemSoundID
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObject

/** Built-in iOS system alert sound ID (a short "tock" tone) used as the closest
 * platform equivalent to Android's [android.media.ToneGenerator] reject buzz. */
private const val SYSTEM_SOUND_ID_VIBRATE_OR_ALERT: SystemSoundID = 1105u

@OptIn(ExperimentalForeignApi::class)
actual class MusicController actual constructor() {
    private var theme: MusicCatalog.Theme = MusicCatalog.Theme.NEW
    private var player: AVAudioPlayer? = null
    private var playlistDelegate: PlaylistDelegate? = null
    private var playlist: List<String> = emptyList()
    private var index = 0
    private var observersRegistered = false

    actual fun setTheme(theme: MusicCatalog.Theme) {
        this.theme = theme
        playlist = emptyList()
    }

    actual fun start() {
        if (playlist.isEmpty()) reshufflePlaylist()
        playCurrent()
        registerObservers()
    }

    private fun reshufflePlaylist(startIndex: Int = 0) {
        playlist = MusicCatalog.playlist(theme).shuffled()
        index = startIndex
    }

    private fun playCurrent() {
        val file = playlist.getOrNull(index) ?: return
        val name = file.substringBeforeLast('.')
        val ext = file.substringAfterLast('.')
        // Compose Multiplatform bundles composeResources/files/* under a top-level
        // "files/" directory in the app bundle, not at the bundle root.
        val url = NSBundle.mainBundle.URLForResource(name, ext, "files") ?: return
        player?.stop()
        val delegate = PlaylistDelegate { advance() }
        playlistDelegate = delegate
        player = AVAudioPlayer(contentsOfURL = url, error = null)?.apply {
            this.delegate = delegate
            prepareToPlay()
            play()
        }
    }

    /** Advances to the next track, reshuffling (fresh random order) once the whole
     * playlist has been played through - mirrors the Android ExoPlayer shuffle mode. */
    private fun advance() {
        val nextIndex = index + 1
        if (nextIndex >= playlist.size) {
            reshufflePlaylist()
        } else {
            index = nextIndex
        }
        playCurrent()
    }

    actual fun pause() { player?.pause() }
    actual fun resume() { player?.play() ?: start() }

    actual fun release() {
        unregisterObservers()
        player?.stop()
        player = null
    }

    private fun registerObservers() {
        if (observersRegistered) return
        observersRegistered = true
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, null) { _ -> pause() }
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillEnterForegroundNotification, null, null) { _ -> resume() }
    }

    private fun unregisterObservers() {
        if (!observersRegistered) return
        observersRegistered = false
    }
}

/** Advances the playlist to the next track once the current one finishes playing
 * (or fails to decode). */
private class PlaylistDelegate(private val onFinished: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onFinished()
    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) = onFinished()
}

@OptIn(ExperimentalForeignApi::class)
actual class SfxPlayer actual constructor() {
    // A short one-shot AVAudioPlayer plus its delegate must be kept alive (as a
    // property, not a local var) for the duration of playback, otherwise ARC may
    // deallocate them before the completion callback fires.
    private var oneShotPlayer: AVAudioPlayer? = null
    private var oneShotDelegate: OneShotDelegate? = null

    actual fun playTurnRejected() {
        // No original SFX asset exists for this cue (see Android's SfxPlayer using
        // ToneGenerator for the same reason) - play a short built-in system alert
        // sound as the closest iOS equivalent to a platform "buzz" tone.
        AudioServicesPlaySystemSound(SYSTEM_SOUND_ID_VIBRATE_OR_ALERT)
    }

    actual fun playBonusWon(musicController: MusicController) = playOneShotDucked("end_turn_bonus", musicController, resumeAfter = true)
    actual fun playEndOfTurn(musicController: MusicController) = playOneShotDucked("end_turn_regular", musicController, resumeAfter = true)
    actual fun playGameOver(musicController: MusicController) = playOneShotDucked("appl3", musicController, resumeAfter = false)

    private fun playOneShotDucked(name: String, musicController: MusicController, resumeAfter: Boolean) {
        musicController.pause()
        val url = NSBundle.mainBundle.URLForResource(name, "wav", "files")
        if (url == null) {
            if (resumeAfter) musicController.resume()
            return
        }
        val delegate = OneShotDelegate { if (resumeAfter) musicController.resume() }
        oneShotDelegate = delegate
        oneShotPlayer = AVAudioPlayer(contentsOfURL = url, error = null)?.apply {
            this.delegate = delegate
            prepareToPlay()
            if (!play()) {
                if (resumeAfter) musicController.resume()
            }
        }
        if (oneShotPlayer == null && resumeAfter) musicController.resume()
    }
}

/** Minimal [AVAudioPlayerDelegateProtocol] implementation invoking [onFinished] once
 * the one-shot SFX clip finishes playing (or fails to decode), used to resume the
 * background music that was paused/ducked out for the clip's duration. */
private class OneShotDelegate(private val onFinished: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onFinished()
    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) = onFinished()
}
