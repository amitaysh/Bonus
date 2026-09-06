# TODO — Bonus Android (post-v1)

v1 scope is **local pass-and-play only** (2 human players, one device). The following
were explicitly deferred, per the confirmed original game's feature set and the user's
decisions during planning (see the session's `plan.md` for full context). All are
confirmed to have existed in the original 1993 game (v1 of the original even supported
computer-vs-computer play), so they are legitimate, expected follow-ups, not new scope.

## 1. Save / resume a game
The original supported saving/loading up to ~10 in-progress games (`BONUS.DEF`).
Suggested approach: serialize `GameViewModel`'s state (board occupancy, racks, scores,
current player, bag remaining, locks) to local storage (Room or a simple JSON blob via
DataStore). Add a `menu_resume_game` entry point (string already exists in `strings.xml`).

## 2. Hall of fame / high scores
The original had a "טבלת אלופים" (Hall of Fame) screen, likely backed by `BONUS.CMP`.
Suggested approach: local Room table of `(playerName, score, date)`, sorted descending,
capped at a reasonable N entries. `menu_hall_of_fame` string already exists.

## 3. Remote / online multiplayer
Not in v1. The engine (`core` module) is already UI/Android-agnostic and turn-based,
which should make this tractable later: introduce a `GameSession` abstraction (local vs.
networked) that wraps `GameEngine` + turn submission/sync, so the `app` module's
`GameViewModel` doesn't need to know whether the opponent is local or remote.

## 4. AI / computer opponent
Confirmed the original supported human vs. computer and even computer vs. computer in
v1. Suggested approach: a `PlayerController` interface (`HumanController` today,
`AiController` later) that produces moves; the AI would need its own word-generation
strategy over the same dictionary/word-bank assets already bundled. Note: the appeal/
challenge system (`ChallengeSystem`) already models the "no opponent-approval step
needed in human-vs-computer" rule for when this lands.

## 8. Real app icon / branding
Current launcher icon is a placeholder stylized "ב" tile vector - CET should supply (or
approve) real branding assets.

## 9. Settings screen
No settings screen exists yet (mute toggle, theme toggle is currently only on the main
menu). `menu_settings` string already exists.

## 10. support up to 4 players online (future scope)

## 25. fix Hebrew wordlists
The Hebrew wordlists still contain many weird/non-standard Hebrew words, and are
probably missing some regular classic words. Needs a review/cleanup pass over the
bundled dictionary/word-bank assets.

## 26. fix graphics and animation - missing joker character
The joker character graphic is missing everywhere it should appear, including on
bonus screens and elsewhere. Needs art assets and wiring wherever letter tiles are
rendered.

---

## 5. Crossword-build bonus mini-game — visual polish [DONE]
`BonusType.CROSSWORD_BUILD` now renders with a bordered mini-crossword grid and
`LetterTileArt` jpg tiles for both rack and placed letters (matching the main board's
look), letters keep their original rack position when picked, and the completion
summary popup keeps the total score + confirm button pinned above a scrollable word
list so it's never lost off-screen.

## 6. Dynamic time/score tuning for Anagram / Fill-in-blank / Crossword-build [DONE]
Fixed values for the two shared-letter bonus types (20s/40pts, 30s/100pts) were
confirmed byte-for-byte from `BON.EXE`. The other three types are confirmed *mechanics*
but their exact dynamic time/score formulas were not recoverable from static analysis.
Current placeholder values (45s / 30pts) in `BonusMiniGameScreen` should be tuned if the
original can ever be observed running (e.g. via DOSBox).

## 7. Word-challenge/appeal (עירעור) UI [DONE]
`BoardScreen`'s rejected-move popup detects the single-invalid-word case and offers a
dispute (עירעור) button wired to `GameViewModel.appealAccept()`/`appealDiscard()`, which
accept the disputed word per `ChallengeSystem`'s confirmed human-vs-human rule.

## 11. more screen space for grid, move the letters box to the right or left, also present the other player's letter box on the other side as disabled letters. (example: /Users/amshahar/dev/pics/full_board.png) [DONE]
Reworked `BoardScreen`'s top-level layout into a single full-height Row:
side-controls | player0 panel + vertical 2-column rack | board (weight 1f,
now much larger) | player1 panel + vertical 2-column rack. New `VerticalRack`
composable replaces the old horizontal rack strip; renders the non-active
player's rack dimmed (alpha 0.45) with pointer input disabled.

## 12. after putting a letter in the grid - allow drag and drop it (currently can only click on it to return it to the deck). of course this should be available only before commiting a word [DONE]
Added `GameViewModel.movePendingTile(from, to)` plus a drag-gesture modifier
on pending (not-yet-committed) board tiles in `BoardGrid`, so an already-placed
tile can be dragged directly to a new empty cell without returning to the rack.
Verified on-device via adb swipe: tile moved from its cell to a target cell.

## 13. Letter-table (טבלת אותיות) popup should use jpg letters, not a string list [DONE]
Reworked into a 4-column grid of real jpg `LetterTileArt` tiles (via a custom
`Dialog`+`Surface` instead of `AlertDialog`, whose fixed width was too narrow), each
tile paired with its score/count below it, all 22 letters fitting on-screen with no
scrolling needed.

## 14. all background should be blue noise like main screen. [DONE]
Added shared `NoiseBackground` composable (Box + full-screen bg_texture image +
content slot); applied to `MainMenuScreen`, `PlayerSetupScreen`, and
`BonusMiniGameScreen` (Surface made transparent so noise shows through).
`BoardScreen` already had its own equivalent inline background.

## 15. allow drag and drop in the free-word-building mini game, also inside the grid after placement. [DONE]
`BonusMiniGameScreen`'s CROSSWORD_BUILD layout now supports full drag-and-drop:
rack tiles can be dragged directly onto a grid cell (in addition to
tap-to-select/tap-to-place), and already-placed grid tiles can be dragged
directly to a different empty cell without returning to the rack first -
mirroring the main board's `movePendingTile`. Uses the same floating-overlay
z-order pattern as `BoardScreen.BoardGrid` so the dragged tile always renders
on top instead of behind sibling rack/grid rows.

## 16. end-of-turn music, maybe can extract from the original game here /Users/amshahar/dev/tmp/originalBonus [DONE]
`SfxPlayer.playEndOfTurn()` plays whenever the end-of-turn score-summary popup
is dismissed and control actually passes to the other player (not on an
extra-turn continuation or game-over). First attempt used a migrated
`GONG1.VOC` cue extracted from the DOS assets, but the user reported it was
the wrong sound and there's no way to audibly verify candidate `.VOC` files
from this environment - reverted to a distinct synthesized `ToneGenerator`
cue instead of guessing again among unverified original files. Revisit with
an audibly-confirmed original sound if/when one can be identified.

## 17. exit and start new game doesn't really start new game, it keeps the same letters with same game state. also should ask the user if he sure he wants to exit. [DONE]
Root cause: `GameViewModel.board`/`bag`/`engine` were `val`s created once in the
constructor and never recreated, so `startGame()` reused the same board/bag
across games. `startGame()` now recreates a brand-new `Board()`/`LetterBag()`/
`GameEngine()` plus resets every per-game UI/turn state field. Also added an
"האם אתה בטוח שברצונך לצאת?" confirmation dialog before the exit button
actually navigates back to the main menu.

## 18. bonus for 2x or 4x should be marked somewhere so it will be clear, like these /Users/amshahar/dev/pics/x2.png and /Users/amshahar/dev/pics/x4.png [DONE]
`PlayerPanel` now shows a green "X2"/"X4" badge under the score readout
whenever that player has an active `scoreMultiplier` (from the
double-score/quadruple-score bonus prizes), so the bonus stays visible for as
long as it's in effect instead of only flashing in the win popup.

## 19. end-of-turn score summary popup (added while implementing #15-18, not originally numbered) [DONE]
Added a "words scored + points gained" popup shown right after every
successfully committed turn (per the original game's post-move popup, see
end_turn.png reference), pinned in front of any subsequent bonus-slot
prize/mini-game popup so the two never overlap; dismissing it (המשך button)
is what actually triggers the deferred bonus draw and turn switch.

## 20. wrong sound for end-of-turn music. convert all the sound files to .wav from here /Users/amshahar/dev/tmp/originalBonus and I will select the right one. [DONE]
All 18 `.VOC` cues found under `/Users/amshahar/dev/tmp/originalBonus/bonus/` were
converted to `.wav` (via `ffmpeg`) and placed in `/Users/amshahar/dev/tmp/originalBonus_wav/`:
APPL3, EF12, EF2, EF7, GONG1-1, GONG1, JAZZ1, JAZZ2, JUDGE2, LASER1, LASER2, LASER3,
TIK7, TR7, XILO3, XILO8, XL7, XL9. Waiting on the user to listen and pick the correct
end-of-turn cue before wiring it into `SfxPlayer.playEndOfTurn()`.

## 21. add description text to the num-of-turns and to the remaining-letters counters. [DONE]
`LcdDisplay` already had an unused `label` param; wired it up for both side-panel
counters in `BoardScreen` - `moves_counter_label` ("מספר מהלכים") under the moves
counter and `tiles_remaining_label` ("אותיות בשקית") under the tiles-remaining-in-bag
counter - and fixed the label's rendering (was using the ambient `LocalContentColor`,
invisible against the dark noise background) to render in white, centered, matching
the rest of the HUD's on-background text color.

## 22. end-game rule + win/tie popup + game-over music (new, from user request) [DONE]
Game now ends only when a player's own rack runs empty (not the shared bag
emptying, since each player can still hold a full rack drawn earlier) OR when
the exit-game button is confirmed (`GameViewModel.endGameManually()`, called
from the exit-confirmation dialog instead of navigating away silently). On
game-over, a happy popup shows the winner (or tie) with the same noise-texture
background as the board, plays the migrated `APPL3.wav` (now
`app/src/main/res/raw/appl3.wav`) as a one-shot game-over sting via
`SfxPlayer.playGameOver()`, and only returns to the main menu once dismissed.

## 23. music ducking + HUD label/visibility fixes (new, from user request) [DONE]
- Background music (`MusicController`) now pauses whenever the turn-end cue or
  game-over sting plays, instead of overlapping them: added `pause()`/`resume()`
  to `MusicController`; `SfxPlayer.playEndOfTurn` ducks out for the cue's
  duration then auto-resumes, `SfxPlayer.playGameOver` stops it entirely until
  `GameViewModel.startGame` (new game) explicitly resumes it.
- `LcdDisplay`'s label now renders *above* the counter box (was below), so
  "מספר מהלכים" reads correctly as a caption over the moves counter.
- The remaining-letters counter/label was actually already wired but pushed off
  the visible column (fixed items overflowing the narrow side column); made
  the side-controls column scrollable (`verticalScroll`) so both counters are
  always reachable regardless of screen height.

## 24. real end-of-turn / bonus-win audio extracted from gameplay video (new, from user request) [DONE]
Extracted the actual cues from `/Users/amshahar/Downloads/bonus.mp4` (the user's
own recorded gameplay footage) at user-confirmed timestamps: 3:00-3:02 for the
regular end-of-turn cue, 6:49-6:54 (with the first 0.5s trimmed per user
follow-up) for the end-of-turn-with-bonus cue. Copied into
`app/src/main/res/raw/end_turn_regular.wav` and `end_turn_bonus.wav`, wired into
`SfxPlayer.playEndOfTurn`/`playBonusWon` (replacing the earlier synthesized
`ToneGenerator` placeholders) via a shared one-shot-with-music-ducking helper -
background music pauses while the clip plays and resumes automatically on
completion.

