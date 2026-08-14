package il.cet.bonus.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Lightweight turn-feedback cues (accept / reject) using the platform [ToneGenerator],
 * since no original SFX assets were recovered (only background music tracks exist in the
 * migrated C# `Resources/` folder). Kept deliberately separate from [MusicController],
 * which owns the continuous old/new theme background music.
 */
object SfxPlayer {
    private val toneGenerator: ToneGenerator by lazy {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    }

    /** Short upward chime played when a turn is successfully completed and scored. */
    fun playTurnAccepted() {
        runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }

    /** Short buzz played when a proposed move is rejected. */
    fun playTurnRejected() {
        runCatching { toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 200) }
    }

    /** Distinct fanfare-ish tone played when a bonus prize is won. */
    fun playBonusWon() {
        runCatching { toneGenerator.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 300) }
    }
}
