# Bonus — Project Architecture & Handover Guide

This document describes the structure, responsibilities, and conventions of the
**Bonus** codebase, for a team taking over ownership of the project. It is
meant to be read top-to-bottom once and then used as a reference.

## 1. What this project is

"Bonus" is a digital remake of a 1993 Hebrew word-tile board game (think
Scrabble-like mechanics, but with the original game's own bonus-square rules,
mini-games, scoring table, and Hebrew word lists). The original DOS game
(`BON.EXE`, with a `BONUS.DEF` save format) is the source of truth for rules;
wherever code comments reference "verified against BON.EXE" or similar, it
means the value/rule was recovered by analyzing the original binary/assets,
not invented for this remake.

Current shipped scope (v1) is **local pass-and-play only**: two human players
on one device, no save/resume, no networking, no AI opponent. See `TODO.md`
for the full list of confirmed-but-deferred features (save/resume, hall of
fame, online multiplayer, AI opponent, etc.) and any known bugs/polish items.

The project is a **Kotlin Multiplatform (KMP)** codebase supporting **Android**
(shipped/primary) and **iOS** (in-progress migration, see §7). Both targets
render UI with **Jetpack Compose / Compose Multiplatform**.

## 2. Module map

```
bonus-android/
├── core/       — pure Kotlin game engine (no UI, no platform APIs)
├── shared/     — Compose Multiplatform UI + platform-abstraction layer (Android + iOS)
├── app/        — the actual shipped Android application (native Compose UI, own copy)
├── iosApp/     — thin SwiftUI wrapper that hosts the `shared` module's Compose UI on iOS
├── TODO.md     — deferred features / known issues, curated by the team
└── ARCHITECTURE.md — this file
```

**Important architectural nuance:** `app` (the shipped Android app) does
**not** depend on `:shared`. It has its own, hand-maintained copy of the
screens/audio/loaders (`app/src/main/kotlin/il/cet/bonus/...`), and only
depends on `:core` for the game engine. The `shared` module is a newer,
in-progress effort (see git branch `feature/ios-kmp-migration`) to unify
Android+iOS behind one Compose Multiplatform UI, so that iOS can be supported
without hand-writing SwiftUI. **As of this writing, `app` and `shared`
contain two parallel copies of the UI layer** (screens, theme, audio
abstractions, board/tile rendering, dictionary/word-bank loaders) that must
currently be kept in sync manually when making UI changes. Eventually the
intent is likely for `app` to be replaced by (or to just wrap) `:shared`,
collapsing the duplication — but that migration was not complete at the time
of this handover.

---

## 3. `core` module — the game engine

Pure Kotlin, no Android/iOS/Compose dependencies. Multiplatform target (JVM +
Android + iOS) built with `kotlin("multiplatform")`. This is the single
source of truth for game rules and is shared by both `app` and `shared`.

Package root: `il.cet.bonus.core`

### `model/` — core data types
- **`Letter.kt`** — Hebrew letter enum with each letter's score value and bag
  count, verified against `BON.EXE`. No sofit (final-form) letters exist as
  separate tiles. Also defines the joker's count/score and a lookup-by-char
  helper.
- **`Tile.kt`** — sealed tile hierarchy: `LetterTile` (fixed letter) and
  `JokerTile` (assignable letter after being placed; displays `*` until
  assigned).
- **`Square.kt`** — board coordinate (`Position`), square type (`NORMAL` vs.
  `BONUS`), and the lock model (locks last 3 or 5 moves before opening).

### `board/`
- **`Board.kt`** — the 10×10 grid plus the peripheral bonus slots (bonus
  squares live outside the main grid's coordinate bounds but are still
  "real" placeable squares). Owns tile placement/removal, lock countdown
  ticking, and tracks which bonus slots have already been used.
- **`WordExtractor.kt`** — given a position→tile map, extracts every
  contiguous horizontal and vertical word on the board. Used by scoring and
  by move validation.

### `dictionary/`
- **`DictionaryRepository.kt`** — in-memory word-validity checker. Loads a
  `Sequence<String>` of words into a `Set`, trims them, and supports adding
  words at runtime (used by the appeal/challenge system to accept a disputed
  word for the rest of the game).

### `game/`
- **`LetterBag.kt`** — the shared draw bag; builds the full tile pool from
  `Letter.entries` plus jokers, and exposes draw/return operations plus
  remaining-count tracking.
- **`GameEngine.kt`** — the core per-turn validator/scorer. Enforces: tiles
  must be aligned in one row/column, first-move-must-touch-center (or
  equivalent starting rule), new tiles must connect to existing tiles (after
  the first move), no gaps in a placed word, dictionary validation of every
  word formed, scoring (including bonus-square multipliers), and detection of
  triggered bonus slots. Produces `MoveError` (rejection reasons) or
  `TurnResult` (accepted turn + score). Also exposes `forceMove()` to let the
  appeal/challenge flow override a rejected word.
- **`ChallengeSystem.kt`** — encodes the "עירעור" (appeal/dispute) mechanic:
  exact Hebrew UI copy, the rule that only a single disputed word can be
  challenged at a time, and the score delta applied for a correct vs.
  incorrect dispute.
- **`LockAction.kt`** — rules for placing a "lock" on a square (used by the UI
  layer via `GameViewModel`), kept separate from normal word-placement logic.
- **`SwapAction.kt`** — rules/cost for exchanging rack tiles for new ones from
  the bag, kept separate from turn scoring.
- **`Player.kt`** — per-player state: score, active multiplier (x2/x4 bonus
  effects), etc. Shared by both Android and shared `GameViewModel`s.

### `bonus/`
- **`BonusType.kt`** — the enum of verified bonus mini-game types recovered
  from `BON.EXE` (comments explicitly call out which mini-game ideas were
  *not* confirmed and were rejected as speculative): `ANAGRAM`/`ANAGRAM_5`/
  `ANAGRAM_6`/`ANAGRAM_7` (fixed scores 30/50/75/100, dynamic time),
  `SHARED_LETTER_TWO_WORDS` (20s/40pts) and `SHARED_LETTER_THREE_WORDS`
  (30s/100pts, both fixed and confirmed byte-for-byte from `BON.EXE`), and
  `FILL_IN_BLANK`/`CROSSWORD_BUILD` (both fully dynamic time+score, not fixed
  constants). A few dynamic-timing types still use provisional placeholder
  values pending further verification — see `TODO.md`'s item on dynamic
  time/score tuning.
- **`BonusPrize.kt`** — the full set of possible outcomes when a player lands
  on/uses a bonus slot: a mini-game, a flat point prize, an extra turn, a
  "double score for next two rounds" effect, or a "x4 + extra turn" prize.
- **`BonusPuzzleGenerator.kt`** — generates the actual puzzle content for each
  mini-game (anagram, fill-in-the-blank, shared-letter puzzles, and the
  crossword-build mode), and validates player answers / computes the
  crossword-build score.

### Tests (`core/src/commonTest`)
- **`game/GameEngineTest.kt`** — unit tests for move validation/scoring rules.
- **`bonus/BonusPuzzleGeneratorTest.kt`** — unit tests for puzzle
  generation/validation.

Run just this module's tests with:
```
./gradlew :core:testDebugUnitTest    # or :core:allTests for all KMP targets
```

---

## 4. `shared` module — Compose Multiplatform UI + platform abstractions

Package root: `il.cet.bonus.shared`. KMP module (Android + iOS targets),
depends on `:core`, uses Compose Multiplatform (runtime, foundation,
material3, ui, resources) plus coroutines. On Android it additionally uses
Media3 ExoPlayer for music. Builds an iOS static framework named `Shared`.

This module exists to let the **same** Compose UI run natively on both
Android and iOS, bridging platform differences via Kotlin `expect`/`actual`
declarations.

### Common code (`shared/src/commonMain`)
- **`SharedApp.kt`** — the Compose Multiplatform app root. Owns simple
  screen-state navigation (menu / settings / board), theme switching,
  background music + SFX wiring, dictionary loading, player names, and the
  shared `GameViewModel`.
- **`game/GameViewModel.kt`** — shared game-state holder: current racks,
  pending tile placements (tiles placed but not yet committed), appeal state,
  bonus celebration/mini-game state, end-of-turn summary, game-over state.
  Bridges the `core` engine to the Compose UI and to the audio abstractions.
- **`game/PlayerNamesStore.kt`** — `expect` declaration for persisting the two
  player names.
- **`dictionary/DictionaryLoader.kt`** — `expect` loader that feeds
  `DictionaryRepository` from a platform-specific source.
- **`bonus/BonusWordBankLoader.kt`** — `expect` loader that feeds
  `BonusPuzzleGenerator` with the length-bucketed word-bank files
  (`3Letters.txt` … `7Letters.txt`).
- **`audio/AudioAbstractions.kt`** — shared audio API: `MusicTheme`,
  `MusicTrack`, `MusicCatalog`, and `expect` `MusicController` /`SfxPlayer`
  types, plus Compose-friendly `rememberMusicController()` /
  `rememberSfxPlayer()`.
- **`ui/theme/ThemeManager.kt`** — `expect` wrapper around persisted
  light/dark or old/new theme selection.
- **`ui/theme/BonusTheme.kt`** — Material theme/styling definitions.

### Shared UI (`shared/src/commonMain/.../ui/`)
- **`screens/MainMenuScreen.kt`** — RTL Hebrew main menu (New Game, Settings).
- **`screens/BoardScreen.kt`** — the main game screen: board grid, both
  players' racks, side HUD controls, move-completion flow, query/appeal
  dialog, letter-value table popup, bonus-slot flow, drag-and-drop tile
  placement.
- **`screens/BonusMiniGameScreen.kt`** — full-screen overlay for all bonus
  mini-games (anagram, fill-in-blank, shared-letter, crossword-build),
  including timers.
- **`screens/SettingsScreen.kt`** — player name editor.
- **`ui/board/BevelWidgets.kt`** — DOS-style beveled button/chrome widgets
  matching the original game's visual style.
- **`ui/board/BonusIconArt.kt`** — maps bonus types/prizes to their artwork.
- **`ui/board/HebrewLetterPicker.kt`** — Hebrew character input helper (used
  in query/appeal and bonus puzzle input).
- **`ui/board/LetterTileArt.kt`** — renders letter tiles as images (not plain
  text) using shared drawable resources.
- **`ui/board/NoiseBackground.kt`** — the shared blue-noise textured
  background used behind every screen.
- **`ui/board/SharedLetterPuzzleViews.kt`** — composables specific to the
  shared-letter and crossword-build mini-game UIs.

### Shared resources (`shared/src/commonMain/composeResources/`)
- `drawable/` — letter tile images, bonus icons, background texture (36
  drawable resources at time of writing).
- `files/dictionary/` — the bundled word lists, used identically to the
  Android app's assets copy: `HebrewWords.txt` (main dictionary, ~134k
  lines), plus `3Letters.txt`…`7Letters.txt` (length-bucketed word banks
  used by bonus mini-games).
- `values/strings.xml` — shared localized (Hebrew) UI strings.

### Android actuals (`shared/src/androidMain`)
- **`PlatformContextHolder.kt`** — holds the Android `Context` for use by
  other `actual` implementations.
- **`audio/AudioAbstractions.android.kt`** — `MediaPlayer`/`ToneGenerator`
  backed `MusicController`/`SfxPlayer`.
- **`dictionary/DictionaryLoader.android.kt`** — reads dictionary files from
  Android assets.
- **`bonus/BonusWordBankLoader.android.kt`** — reads word-bank files from
  Android assets.
- **`game/PlayerNamesStore.android.kt`** — `SharedPreferences`-backed.
- **`ui/theme/ThemeManager.android.kt`** — `SharedPreferences`-backed.

### iOS actuals (`shared/src/iosMain`)
- **`MainViewController.kt`** — exposes a `UIViewController` hosting
  `SharedApp()` via `ComposeUIViewController`; this is the entry point iOS
  calls into.
- **`audio/AudioAbstractions.ios.kt`** — `AVAudioPlayer`-backed, with a
  system alert sound fallback for SFX.
- **`dictionary/DictionaryLoader.ios.kt`** — loads dictionary files bundled
  as compose resources.
- **`bonus/BonusWordBankLoader.ios.kt`** — loads word-bank files bundled as
  compose resources.
- **`game/PlayerNamesStore.ios.kt`** — `NSUserDefaults`-backed.
- **`ui/theme/ThemeManager.ios.kt`** — `NSUserDefaults`-backed.

### `expect`/`actual` bridge summary
| Abstraction | Android impl | iOS impl |
|---|---|---|
| `DictionaryLoader` | reads Android assets | reads bundled compose resources |
| `BonusWordBankLoader` | reads Android assets | reads bundled compose resources |
| `MusicController` | `MediaPlayer` (in `shared`); Media3 ExoPlayer (in `app`, see §5) | `AVAudioPlayer` |
| `SfxPlayer` | `ToneGenerator` + `MediaPlayer` | `AVAudioPlayer` + system alert sound |
| `ThemeManager` | `SharedPreferences` | `NSUserDefaults` |
| `PlayerNamesStore` | `SharedPreferences` | `NSUserDefaults` |

---

## 5. `app` module — the shipped Android application

Package root: `il.cet.bonus`. `applicationId`/`namespace` =
`il.cet.bonus`. Standard Android application module (not KMP), Compose UI,
depends only on `:core` (does **not** depend on `:shared` — see the
duplication note in §2).

- **`MainActivity.kt`** — Android entry point and Compose root. Forces RTL
  layout, wires up Android-specific loaders/controllers, hosts the Android
  `GameViewModel`, and does the same simple menu/settings/board navigation
  as `SharedApp()` does in the `shared` module. Also observes the lifecycle
  to pause/resume music when backgrounded.
- **`game/GameViewModel.kt`** — Android `AndroidViewModel` version of the
  game-state holder; functionally mirrors `shared`'s `GameViewModel` but is
  wired to Android's `Application` and the Android-native audio classes
  below (not the `expect`/`actual` abstractions).
- **`audio/MusicController.kt`** — playlist controller built on **Media3
  ExoPlayer**; loads "old" vs. "new" theme playlists from `res/raw` and
  shuffles/loops them.
- **`audio/SfxPlayer.kt`** — `ToneGenerator` for the rejection buzz;
  `MediaPlayer` for one-shot end-of-turn / bonus-won / game-over clips.
  Ducks (pauses) background music during one-shot clips and resumes it after.
- **`audio/MusicCatalog.kt`** — static catalog mapping theme name → ordered
  list of raw-resource music tracks.
- **`dictionary/DictionaryLoader.kt`** — reads dictionary `.txt` files from
  `app/src/main/assets/dictionary/`.
- **`bonus/BonusWordBankLoader.kt`** — reads the length-bucketed word-bank
  files from assets, with additional sofit-letter filtering/normalization.
- **`game/PlayerNamesStore.kt`** — `SharedPreferences`-backed player name
  persistence.
- **`ui/screens/*`, `ui/theme/*`, `ui/board/*`** — Android-native copies of
  the same screens/composables described in §4 (`BoardScreen`,
  `MainMenuScreen`, `BonusMiniGameScreen`, `SettingsScreen`, bevel widgets,
  bonus icon art, Hebrew letter picker, letter tile art, noise background).

### Android resources/assets
- `app/src/main/res/drawable/` — tile images and bonus artwork (Android
  copy, parallel to `shared`'s compose resources).
- `app/src/main/res/raw/` — all music (`*_old.mp3`/`*_new.mp3` theme
  playlists) and SFX (`appl3.wav`, `end_turn_regular.wav`,
  `end_turn_bonus.wav`).
- `app/src/main/res/values/` — colors, strings, theme XML.
- `app/src/main/assets/dictionary/` — `HebrewWords.txt` (main dictionary,
  ~134k lines) plus `3Letters.txt`…`7Letters.txt` word banks (parallel copy
  of the files also bundled in `shared`'s compose resources).
- `app/src/main/AndroidManifest.xml` — single launcher activity, locked to
  landscape orientation, RTL supported, backup enabled, handles its own
  config changes (rotation/etc. don't restart the activity).

---

## 6. `iosApp` — thin SwiftUI wrapper

This is intentionally minimal — essentially no game logic or UI lives here;
it just hosts the `shared` module's Compose UI.

- **`iosApp/iOSApp.swift`** — SwiftUI `App` entry point; runs
  `UITestsLaunch.configure()` then shows `ContentView()`.
- **`iosApp/ContentView.swift`** — a thin `UIViewControllerRepresentable`
  that embeds the Kotlin/Compose view controller exposed by
  `shared`'s `MainViewController.kt`. Ignores top/bottom safe areas (the game
  runs landscape, edge-to-edge).
- **`iosApp/UITestsLaunch.swift`** — launch helper for UI testing.
- **`Info.plist`** — standard iOS app metadata.
- **`project.yml`** — [XcodeGen](https://github.com/yonaskolb/XcodeGen)
  config used to (re)generate `iosApp.xcodeproj` reproducibly; also wires a
  prebuild script step that compiles/signs the KMP `Shared` framework from
  Gradle before Xcode builds the app.
- **`iosApp.xcodeproj/`** — the generated Xcode project (generated by
  XcodeGen from `project.yml`; don't hand-edit `project.pbxproj` unless
  necessary — regenerate via XcodeGen instead where possible).

**Status:** this iOS target exists on the `feature/ios-kmp-migration` branch
and represents in-progress work, not yet a finished/released iOS app.

---

## 7. Build system

- **`settings.gradle.kts`** — root project name `Bonus`; includes `:app`,
  `:core`, `:shared`. Repositories: Google, Maven Central, and the
  JetBrains Compose dev Maven repo (needed for Compose Multiplatform
  snapshot/dev artifacts).
- **`build.gradle.kts`** (root) — declares plugin versions: Android Gradle
  Plugin `8.6.1`, Kotlin `2.0.21`, Compose Multiplatform `1.7.0`. No plugins
  applied at the root itself.
- **`gradle.properties`** — `-Xmx2048m` JVM heap, AndroidX enabled,
  non-transitive R classes, official Kotlin code style, and two
  `nowarn`-style flags suppressing known-benign KMP/Android source-set
  layout compatibility warnings.
- **`core/build.gradle.kts`** — `kotlin("multiplatform")` + Android library
  plugin. Targets: JVM, Android, and iOS (`iosX64`, `iosArm64`,
  `iosSimulatorArm64`). `commonTest` uses `kotlin("test")`.
- **`shared/build.gradle.kts`** — KMP + Android library + Compose
  Multiplatform plugins. Targets Android plus an iOS static framework named
  `Shared`. Common dependencies: `:core`, Compose (runtime, foundation,
  material3, ui, resources), coroutines. Android-only dependencies: Compose
  UI tooling, Media3 ExoPlayer/common.
- **`app/build.gradle.kts`** — standard Android application module with
  Compose. Depends on `:core` only. Uses the Compose BOM, Activity Compose,
  `lifecycle-viewmodel-compose`, and Media3 ExoPlayer/common.

No Gradle version catalog (`libs.versions.toml`) is used — dependency
versions are inlined directly in each `build.gradle.kts`.

### Common Gradle commands
```
./gradlew :app:assembleDebug        # build the shipped Android app (debug)
./gradlew :app:installDebug         # build + install on a connected device/emulator
./gradlew :core:testDebugUnitTest   # run core engine unit tests
./gradlew :shared:assemble          # build the shared KMP module (for iOS work)
```
iOS builds go through Xcode/XcodeGen (`iosApp/project.yml`), which invokes
Gradle to produce the `Shared.framework` as part of its build phase.

---

## 8. Key cross-cutting architectural patterns

### State management
Both the Android-native (`app`) and shared (`shared`) implementations use a
`GameViewModel` as the single app state machine, holding: the `Board`,
`LetterBag`, and `GameEngine` instances; the two `Player`s and whose turn it
is; each player's rack and any pending (uncommitted) tile placements;
error/rejection messages; appeal/dispute state; joker-letter-assignment
state; active bonus mini-game/celebration state; the end-of-turn score
summary; and game-over state. UI screens observe this view model and call
into it for every user action; the view model in turn calls into `core`
(`GameEngine`, `LetterBag`, `Board`, etc.) to actually validate/apply moves.

### Navigation
There is no navigation library — just a simple enum/sealed screen-state
(`MENU` / `SETTINGS` / `BOARD`) switched over in `MainActivity` (Android) or
`SharedApp()` (shared/iOS).

### Dictionary & word-bank loading
- Main dictionary: `HebrewWords.txt` (~134k words), loaded into
  `DictionaryRepository` at startup via the platform's `DictionaryLoader`.
- Bonus mini-game word banks: separate length-bucketed files
  (`3Letters.txt` through `7Letters.txt`), loaded via `BonusWordBankLoader`
  into `BonusPuzzleGenerator`.
- These files are duplicated in two places today: Android assets
  (`app/src/main/assets/dictionary/`) and shared compose resources
  (`shared/src/commonMain/composeResources/files/dictionary/`) — **keep both
  in sync when editing word lists** (see `TODO.md` item on Hebrew wordlist
  cleanup).
- No de-duplication/cleanup has been done on these lists; sofit (final)
  letter forms are intentionally left as-is in the dictionary text even
  though the physical tile set (`Letter.kt`) has no separate sofit tiles.

### Audio system
- **Music**: a catalog (`MusicCatalog`) of "old"/"new" theme playlists;
  a `MusicController` shuffles/loops the current theme's tracks
  continuously. Android's shipped `app` module uses Media3 ExoPlayer for
  this; the `shared` module's Android `actual` uses plain `MediaPlayer`
  instead (another point of divergence between the two parallel
  implementations — worth reconciling if/when the migration completes).
- **SFX**: a synthesized `ToneGenerator` buzz for rejected moves, and
  one-shot `MediaPlayer`/`AVAudioPlayer` clips for end-of-turn,
  end-of-turn-with-bonus, and game-over. All SFX clips duck (pause)
  background music for their duration and then resume it automatically.
- The end-of-turn/game-over SFX clips (`end_turn_regular.wav`,
  `end_turn_bonus.wav`, `appl3.wav`) were extracted from either the
  original DOS game's `.VOC` assets or the team's own recorded gameplay
  footage — see `TODO.md` history (items 20/24) for provenance if a cue
  ever needs re-deriving.

### Bonus mini-games
Landing on/using a bonus board slot yields a `BonusPrize` (flat points,
extra turn, score multiplier, or one of the `BonusType` mini-games:
anagram, fill-in-the-blank, shared-letter puzzle, or crossword-build).
`BonusPuzzleGenerator` builds the actual puzzle content from the word-bank
files, and `BonusMiniGameScreen` renders/drives the interactive overlay.

### Visual style
The UI intentionally mimics the original 1993 DOS game's look: a tiled blue
"noise" background (`NoiseBackground`) behind every screen, beveled/chrome
button widgets (`BevelWidgets`), and image-based letter tiles
(`LetterTileArt`) rather than plain text, all sourced from bundled
drawable/image resources rather than drawn procedurally.

---

## 9. Things a new team should know before touching this code

1. **`app` vs `shared` duplication is real and current.** Until the KMP/iOS
   migration is finished and `app` is switched to actually consume
   `:shared`, any UI/behavior fix likely needs to be applied **twice** (once
   under `app/src/main/kotlin/il/cet/bonus/...`, once under
   `shared/src/commonMain/kotlin/il/cet/bonus/shared/...`) if it should show
   up on both platforms. Check `TODO.md` and recent commits before assuming
   one copy is "the" canonical one.
2. **Game rules are derived from the original DOS game, not invented.**
   Before changing scoring, bag composition, bonus mechanics, or challenge
   rules in `core`, read the surrounding code comments — many values were
   painstakingly reverse-engineered from `BON.EXE`/`BONUS.DEF` and are not
   arbitrary.
3. **Dictionary/word-bank files exist in two copies** (Android assets +
   shared compose resources) and must be kept in sync manually.
4. **`iosApp.xcodeproj` is generated by XcodeGen** from `project.yml` —
   prefer editing `project.yml` and regenerating over hand-editing the
   `.pbxproj` where possible.
5. See `TODO.md` for the current list of deliberately-deferred features and
   known outstanding issues (including the two most recent: Hebrew wordlist
   quality cleanup, and missing joker-tile artwork/animation).
