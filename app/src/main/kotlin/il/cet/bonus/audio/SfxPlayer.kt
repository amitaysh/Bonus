package il.cet.bonus.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import il.cet.bonus.R

/**
 * Lightweight turn-feedback cues (accept / reject) using the platform [ToneGenerator],
 * since no original SFX assets were recovered for those (only background music tracks
 * exist in the migrated C# `Resources/` folder). Kept deliberately separate from
 * [MusicController], which owns the continuous old/new theme background music.
 *
 * [playEndOfTurn] and [playBonusWon] now use real cues extracted directly from the
 * original game's `bonus.mp4` gameplay footage audio track (per user-confirmed
 * timestamps): 3:00-3:02 for the regular end-of-turn cue (`end_turn_regular.wav`) and
 * 6:49-6:54 (minus a leading 0.5s of silence) for the end-of-turn-with-bonus cue
 * (`end_turn_bonus.wav`) - replacing the earlier synthesized [ToneGenerator] placeholder
 * cues (an earlier attempt using a migrated `GONG1.VOC` file was also rejected by the
 * user - see git history).
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

    /** Plays the real "bonus won" cue extracted from the original gameplay footage
     * (6:49-6:54 in `bonus.mp4`) - ducks the background [musicController] out for the
     * clip's duration then resumes it, same pattern as [playEndOfTurn]. */
    fun playBonusWon(context: Context, musicController: MusicController) {
        playOneShotDucked(context, R.raw.end_turn_bonus, musicController)
    }

    /** Plays once the end-of-turn score-summary popup is dismissed and control actually
     * passes to the other player - the real cue extracted from the original gameplay
     * footage (3:00-3:02 in `bonus.mp4`). Ducks out the background [musicController] for
     * the cue's duration, then resumes it, so the two never play simultaneously. */
    fun playEndOfTurn(context: Context, musicController: MusicController) {
        playOneShotDucked(context, R.raw.end_turn_regular, musicController)
    }

    private fun playOneShotDucked(context: Context, resId: Int, musicController: MusicController) {
        musicController.pause()
        runCatching {
            val mp = MediaPlayer.create(context, resId)
            mp.setOnCompletionListener {
                it.release()
                musicController.resume()
            }
            mp.start()
        }.onFailure {
            // If playback failed to even start, don't leave the music paused forever.
            musicController.resume()
        }
    }

    /** Plays the migrated original "APPL3" cue once the game truly ends (a player's rack
     * runs empty - see GameViewModel's end-game rule), behind the win/tie celebration
     * popup. Uses a one-shot [MediaPlayer] (not [MusicController]'s ExoPlayer) since this
     * is a short one-off sting, not looping background music; released on completion.
     * Stops the background [musicController] for the game's duration (until a new game
     * starts, see GameViewModel.startGame) rather than resuming it automatically. */
    fun playGameOver(context: Context, musicController: MusicController) {
        musicController.pause()
        runCatching {
            MediaPlayer.create(context, R.raw.appl3).apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }
}

