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

## 5. Crossword-build bonus mini-game — full implementation
`BonusType.CROSSWORD_BUILD` is currently a stub in `BonusMiniGameScreen` (always scores
0). The real mechanic ("build a mini-crossword from as many letters as possible; score =
sum of points of all words built") needs its own small board UI, reusing `WordExtractor`
and `Board`-like placement logic from `core`, similar in spirit to the main board but on
a small scratch grid.

## 6. Dynamic time/score tuning for Anagram / Fill-in-blank / Crossword-build
Fixed values for the two shared-letter bonus types (20s/40pts, 30s/100pts) were
confirmed byte-for-byte from `BON.EXE`. The other three types are confirmed *mechanics*
but their exact dynamic time/score formulas were not recoverable from static analysis.
Current placeholder values (45s / 30pts) in `BonusMiniGameScreen` should be tuned if the
original can ever be observed running (e.g. via DOSBox).

## 7. Word-challenge/appeal (עירעור) UI
`ChallengeSystem` (core logic) is implemented, but there's no UI flow yet for a player to
actually dispute a rejected word, request opponent approval, and see the outcome
message. Needs a small dialog wired into `BoardScreen`'s failure path (`InvalidWords`).

## 8. Real app icon / branding
Current launcher icon is a placeholder stylized "ב" tile vector - CET should supply (or
approve) real branding assets.

## 9. Settings screen
No settings screen exists yet (mute toggle, theme toggle is currently only on the main
menu). `menu_settings` string already exists.

## 10. support up to 4 players online (future scope)

## 11. more screen space for grid, move the letters box to the right or left, also present the other player's letter box on the other side as disabled letters.

## 12. after putting a letter in the grid - allow drag and drop it (currently can only click on it to return it to the deck). of course this should be available only before commiting a word

