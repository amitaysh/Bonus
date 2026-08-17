package il.cet.bonus.shared.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

@OptIn(ExperimentalForeignApi::class)
actual class MusicController actual constructor() {
    private var theme: MusicCatalog.Theme = MusicCatalog.Theme.NEW
    private var player: AVAudioPlayer? = null
    private var observersRegistered = false

    actual fun setTheme(theme: MusicCatalog.Theme) {
        this.theme = theme
    }

    actual fun start() {
        val file = MusicCatalog.playlist(theme).firstOrNull() ?: return
        val name = file.substringBeforeLast('.')
        val ext = file.substringAfterLast('.')
        val url = NSBundle.mainBundle.URLForResource(name, ext) ?: return
        player?.stop()
        player = AVAudioPlayer(contentsOfURL = url, error = null)?.apply {
            numberOfLoops = -1
            prepareToPlay()
            play()
        }
        registerObservers()
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

actual class SfxPlayer actual constructor() {
    actual fun playTurnRejected() {}
    actual fun playBonusWon(musicController: MusicController) {}
    actual fun playEndOfTurn(musicController: MusicController) {}
    actual fun playGameOver(musicController: MusicController) {}
}
