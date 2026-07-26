# Plan 003: Make taps feel instant (press feedback + optimistic toggles)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 85bcada..HEAD -- app/src/main/java/com/yuukifst/orpheus/ui/theme/TerminalModifiers.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/CustomNavigationBarItem.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/ToggleSegmentButton.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/player/AnimatedPlaybackControls.kt app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlaybackStateHolder.kt app/src/main/java/com/yuukifst/orpheus/data/preferences/FullPlayerLoadingTweaks.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `85bcada`, 2026-07-26

## Why this matters

Buttons in this app feel slow for two independent reasons, and both are fixable
without changing the visual language:

1. **The press-down animation is too slow, and there is no ripple to cover it.**
   Most interactive surfaces pass `indication = null` and rely on a custom
   `terminalPressScale` whose press-in tween is 150 ms. Material's ripple appears
   on the *down* event within a frame; a 150 ms scale does not, so the user
   perceives ~150 ms of nothing.
2. **Toggle state waits for a round trip.** `repeat` already flips its state
   optimistically, but play/pause waits for a MediaController IPC callback and
   favorite waits for a Room write plus a Flow emission. The user taps and the
   icon does not change until the platform answers.

There is also a default that makes the full player feel dead during opening:
`FullPlayerLoadingTweaks.delayControls = true` with
`contentAppearThresholdPercent = 98`, meaning the transport controls are not even
composed until the sheet is 98 % open.

After this plan: press feedback lands in the frame of the down-event, play/pause
and favorite flip immediately with a bounded revert if the platform disagrees,
and the full player's controls are present early enough to be tappable.

## Current state

### 1. Press feedback

`terminalPressScale` is the app-wide press affordance. Press-in is
`DurationQuick = 150 ms`; release is `DurationFast = 250 ms` with a strong bounce:

```56:75:app/src/main/java/com/yuukifst/orpheus/ui/theme/TerminalModifiers.kt
fun Modifier.terminalPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    if (!LocalTerminalChrome.current) return@composed this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut)
        } else {
            tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseBounceStrong)
        },
        label = "terminalPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
```

Motion tokens for reference:

```17:24:app/src/main/java/com/yuukifst/orpheus/ui/theme/Motion.kt
object OrpheusMotion {
    const val DurationStagger = 40
    const val DurationMicro = 80
    const val DurationQuick = 150
    const val DurationFast = 250
    const val DurationMedium = 350
    const val DurationSlow = 400
    const val DurationVerySlow = 500
```

`terminalPressScale` is gated on `LocalTerminalChrome`, which is `!isSoftChrome`
(`Theme.kt:411`) and defaults to `false` (`Theme.kt:28`) — i.e. in the soft-chrome
theme there is **no press feedback at all**, because those same call sites also
pass `indication = null`:

```109:116:app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/CustomNavigationBarItem.kt
            .terminalPressScale(interactionSource)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null //ripple(bounded = true, radius = 24.dp) // Contained ripple
            )
```

```211:222:app/src/main/java/com/yuukifst/orpheus/presentation/components/ToggleSegmentButton.kt
    Box(
        modifier = modifier
            .fillMaxSize()
            .terminalPressScale(interactionSource)
            .clip(TerminalCornerShape)
            .background(bgColor)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
            )
```

### 2. Play/pause waits for the player

```4283:4296:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    fun playPause() {
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            playbackStateHolder.playPause()
            return
        }

        if (controller.isPlaying) {
            controller.pause()
        } else {
```

```377:387:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlaybackStateHolder.kt
    fun playPause() {
        val controller = activeLocalPlayer()
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }
```

Neither updates `isPlaying` in `_stablePlayerState`; the UI waits for
`onIsPlayingChanged`.

**The exemplar to copy is in the same file** — `cycleRepeatMode` issues the
command, launches the DB write, and updates state immediately:

```414:424:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlaybackStateHolder.kt
        mediaController?.repeatMode = newMode
        scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
        _stablePlayerState.update { it.copy(repeatMode = newMode) }
    }
```

### 3. The play/pause icon has its own visual-state machine

`AnimatedPlaybackControls` keeps a local `playPauseVisualState` that trails the
real `isPlaying`, with a 220 ms `releaseDelay` used to avoid flicker after
prev/next:

```73:87:app/src/main/java/com/yuukifst/orpheus/presentation/components/player/AnimatedPlaybackControls.kt
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }
```

```181:186:app/src/main/java/com/yuukifst/orpheus/presentation/components/player/AnimatedPlaybackControls.kt
                    .clickable {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
```

This machinery is why you must fix `isPlaying` at the state-holder level (step 3)
rather than hacking the icon: if the source of truth flips immediately, this
component follows within a frame on its own.

### 4. Favorite waits for the DB

```3622:3633:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    fun toggleFavorite() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        viewModelScope.launch {
            if (currentSong.id.isYouTubeMediaId()) {
                val currentlyFavorite = favoriteSongIds.value.contains(currentSong.id)
                musicRepository.setYouTubeFavorite(currentSong, !currentlyFavorite)
                return@launch
            }
            val favoriteSongId = resolveFavoriteSongId(currentSong) ?: return@launch
            val currentlyFavorite = favoriteSongIds.value.contains(favoriteSongId)
            setFavoriteStatusEverywhere(favoriteSongId, !currentlyFavorite)
        }
    }
```

Note `MusicService.kt` around lines 2430-2442 *does* mutate the in-memory
favorite set before the DB write for MediaSession commands — read it and match
its approach rather than inventing a new one.

### 5. Full player controls are gated on 98 % expansion

```3:15:app/src/main/java/com/yuukifst/orpheus/data/preferences/FullPlayerLoadingTweaks.kt
data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = false,
    val delayAlbumCarousel: Boolean = true,
    val delaySongMetadata: Boolean = true,
    val delayProgressBar: Boolean = true,
    val delayControls: Boolean = true,
    val showPlaceholders: Boolean = true,
    val transparentPlaceholders: Boolean = false,
    val applyPlaceholdersOnClose: Boolean = false,
    val switchOnDragRelease: Boolean = true,
    val contentAppearThresholdPercent: Int = 98,
    val contentCloseThresholdPercent: Int = 0
)
```

These defaults are mirrored in `UserPreferencesRepository` (search for
`contentAppearThresholdPercent` and `delayControls` there) — a stored preference
overrides the data-class default, so changing only the data class will not change
behavior for existing installs.

### Repo conventions that apply

- Never call `navController.navigate(...)` directly; use `navigateSafely(...)`
  (`CONTRIBUTING.md`).
- Publish UI state before writing to the database (`CLAUDE.md`).
- Timber for logging; JUnit Jupiter for tests.
- User-facing strings live in `res/values/strings*.xml`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` | exit 0, all pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | exit 0 |

**Do NOT** start an emulator, run `adb`, install the APK, or drive the UI
(`CLAUDE.md` hard rule). Perceived-latency changes are validated by the
maintainer on a physical device.

## Scope

**In scope**:
- `app/src/main/java/com/yuukifst/orpheus/ui/theme/TerminalModifiers.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/CustomNavigationBarItem.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/components/ToggleSegmentButton.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlaybackStateHolder.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` — only
  `playPause()` and `toggleFavorite()` / `toggleFavoriteSpecificSong()`
- `app/src/main/java/com/yuukifst/orpheus/data/preferences/FullPlayerLoadingTweaks.kt`
- `app/src/main/java/com/yuukifst/orpheus/data/preferences/UserPreferencesRepository.kt` — only the
  default values backing `FullPlayerLoadingTweaks`
- `app/src/test/java/com/yuukifst/orpheus/ui/OptimisticToggleTest.kt` (create)

**Out of scope** (do NOT touch, even though they look related):
- `EnhancedSongListItem.kt` — its `detectTapGestures` + `semantics` block is
  deliberate (it carries selection-mode long-press and an explicit a11y node).
  Converting it to `clickable` risks breaking selection mode and TalkBack. If you
  believe row press feedback is needed, report it as a follow-up instead.
- `AppNavigation.kt`'s `BOTTOM_NAV_TRANSITION_DURATION` (380 ms) and
  `Transitions.kt` (250 ms) — navigation timing is a separate design decision;
  the comment at `AppNavigation.kt:591` shows it is already tuned against the
  system animation scale.
- `MorphingPlayPauseIcon.kt` — it uses `MaterialTheme.motionScheme`, which is the
  right thing.
- Shuffle (`PlaybackStateHolder` shuffle path and its 400 ms cooldown). Making
  shuffle optimistic requires reasoning about a queue rebuild that can fail
  halfway; that is a separate plan.
- `MusicService.kt`.

## Git workflow

- Branch: `advisor/003-instant-touch-feedback`
- Commit per step; short imperative subject, no `Co-Authored-By:` trailer.
- Do NOT push or open a PR unless the operator asks.

## Steps

### Step 1: Make the press-down scale land immediately

In `TerminalModifiers.kt`, change the press-in spec of `terminalPressScale` from
`tween(OrpheusMotion.DurationQuick /* 150 */)` to `tween(OrpheusMotion.DurationMicro /* 80 */)`.
Leave the release spec (`DurationFast` + `EaseBounceStrong`) untouched — the
bounce-out is the app's signature and does not block perception of the press.

Do not introduce a new numeric literal; use the existing `OrpheusMotion` token.

**Verify**:
- `rg -n "DurationMicro" app/src/main/java/com/yuukifst/orpheus/ui/theme/TerminalModifiers.kt` → 1 match, inside `terminalPressScale`
- `:app:compileDebugKotlin` → exit 0

### Step 2: Restore an instant indication on the two highest-traffic surfaces

The bottom navigation bar and the player toggle segments are the buttons the user
presses most, and in soft-chrome they currently have **no** press feedback
(`terminalPressScale` no-ops and `indication = null`).

For both `CustomNavigationBarItem.kt` and `ToggleSegmentButton.kt`:

- Replace `indication = null` with an indication that exists only when terminal
  chrome is off, so the terminal look is unchanged:

```kotlin
    val showTerminalChrome = LocalTerminalChrome.current
    val pressIndication = if (showTerminalChrome) null else ripple(bounded = true)
    ...
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,              // keep the existing role per call site
                interactionSource = interactionSource,
                indication = pressIndication,
            )
```

- `ripple` comes from `androidx.compose.material3.ripple`. Check the import style
  already used in this repo with
  `rg -n "import androidx.compose.material3.ripple" app/src/main/java` — the mini
  player already does this (`UnifiedPlayerSheetShared.kt`, `miniPlayerIndication`),
  including the `remember { ripple(...) }` wrapper. Match that: build the
  indication inside `remember` so it is not recreated per recomposition.
- Delete the commented-out `//ripple(bounded = true, radius = 24.dp) // Contained ripple`
  remnant in `CustomNavigationBarItem.kt` rather than leaving two competing hints.
- Do **not** remove `terminalPressScale`; the two mechanisms are mutually
  exclusive by theme.

**Verify**:
- `rg -n "indication = null" app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/CustomNavigationBarItem.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/ToggleSegmentButton.kt` → no matches
- `:app:compileDebugKotlin` → exit 0

### Step 3: Make play/pause optimistic at the source of truth

Add to `PlaybackStateHolder` an optimistic flip that mirrors `cycleRepeatMode`'s
shape, plus a bounded revert so a rejected command cannot leave the icon lying.

1. Add a private field `private var optimisticPlayingRevertJob: Job? = null`.
2. Add:

```kotlin
    /**
     * The transport icon is driven by `isPlaying`, which otherwise only changes
     * when `onIsPlayingChanged` comes back from the player. Flip it locally and
     * let the real callback confirm; revert if it never arrives.
     */
    private fun setOptimisticIsPlaying(isPlaying: Boolean) {
        _stablePlayerState.update { it.copy(isPlaying = isPlaying, playWhenReady = isPlaying) }
        optimisticPlayingRevertJob?.cancel()
        optimisticPlayingRevertJob = scope?.launch {
            delay(OPTIMISTIC_PLAY_STATE_TIMEOUT_MS)
            val actual = activeLocalPlayerOrNull()?.isPlaying ?: return@launch
            if (actual != _stablePlayerState.value.isPlaying) {
                _stablePlayerState.update { it.copy(isPlaying = actual, playWhenReady = actual) }
            }
        }
    }
```

with `private const val OPTIMISTIC_PLAY_STATE_TIMEOUT_MS = 600L` in the file's
companion object (match the existing constant style — the file already has
`SHUFFLE_TOGGLE_COOLDOWN_MS`).

`activeLocalPlayerOrNull()` may not exist. Check with
`rg -n "activeLocalPlayer" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlaybackStateHolder.kt`.
If only `activeLocalPlayer()` exists and it is non-null-returning, use it — but do
**not** create a player just to read `isPlaying`: if the accessor can construct
ExoPlayer (see `DualPlayerEngine.masterPlayer`, which calls `initialize()` in its
getter), guard the revert with a null-safe path that reads `mediaController`
instead. Report which one you used.

3. Call `setOptimisticIsPlaying(...)` from `PlaybackStateHolder.playPause()`
   immediately after issuing the command, with the value the command implies
   (`false` after `pause()`, `true` after `play()`).
4. Do the same in `PlayerViewModel.playPause()`, but only in the two simple
   branches: `controller.pause()` and the final `controller.play()` at the end of
   the `else` block. Do **not** add it to the branches that call
   `internalPlaySongs` / `loadAndPlaySong` / the `getFirstPlayableSong` fallback —
   those already go through `applyImmediatePlaybackUi`, which sets
   `isPlaying = true` itself (`PlayerViewModel.kt:3290-3298`), and a second
   optimistic write there would just be noise. To reach it from the ViewModel,
   expose `setOptimisticIsPlaying` as `internal fun` on `PlaybackStateHolder`.

**Verify**:
- `rg -n "setOptimisticIsPlaying" app/src/main/java` → definition + 3 call sites
- `:app:compileDebugKotlin` → exit 0
- `:app:testDebugUnitTest` → exit 0 (watch for `PlayerViewModelTest` regressions)

### Step 4: Make favorite optimistic

Find where `favoriteSongIds` is produced (`PlayerViewModel.kt` around line 1266,
backed by a Room flow in `MusicRepositoryImpl`). Read
`MusicService.kt:2425-2445` first — it already performs an in-memory favorite
update before the DB write for MediaSession commands, and you should reuse that
same mechanism rather than adding a parallel one.

Requirements:
- On tap, the set that the heart icon reads must change in the same frame.
- The DB write stays in `viewModelScope.launch`.
- If the DB write throws, the optimistic entry must be rolled back and the error
  logged with Timber at `w`.
- Applies to both `toggleFavorite()` and `toggleFavoriteSpecificSong(...)`.

If the optimistic set cannot be expressed without changing the type of
`favoriteSongIds` (e.g. it is a plain `stateIn` of a Room flow with no writable
overlay), the correct shape is a `MutableStateFlow<Set<String>>` of pending
overrides combined with the Room flow, so the DB remains the source of truth and
the override is dropped once the DB agrees. Implement that; do not make the Room
flow writable.

**Verify**:
- `:app:compileDebugKotlin` → exit 0
- `:app:testDebugUnitTest` → exit 0
- `rg -n "setFavoriteStatusEverywhere|setYouTubeFavorite" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` → each call is preceded by an optimistic update in the same function

### Step 5: Let the full player's controls exist before the sheet is fully open

Change the defaults so the transport controls are composed early enough to be
tappable during the open gesture:

- `FullPlayerLoadingTweaks.delayControls`: `true` → `false`
- `FullPlayerLoadingTweaks.contentAppearThresholdPercent`: `98` → `70`
- Mirror both in `UserPreferencesRepository` (find the backing defaults with
  `rg -n "delayControls|contentAppearThresholdPercent" app/src/main/java/com/yuukifst/orpheus/data/preferences/UserPreferencesRepository.kt`).
  Existing installs have a stored value; changing only the data class will not
  affect them, so the repository defaults must change too.

Leave `delayAlbumCarousel`, `delaySongMetadata`, `delayProgressBar`,
`showPlaceholders` and `switchOnDragRelease` alone — those gate heavier content
and are what keep the open animation smooth. Only the controls are being ungated.

**Verify**:
- `rg -n "delayControls|contentAppearThresholdPercent" app/src/main/java` shows `false` and `70` in both files
- `:app:compileDebugKotlin` → exit 0

### Step 6: Add a unit test for the optimistic-toggle decision logic

Where the optimistic logic reduces to a pure decision (which `isPlaying` value a
command implies; whether a pending favorite override should still apply given a
DB value), extract it as an `internal` top-level function and test it in
`app/src/test/java/com/yuukifst/orpheus/ui/OptimisticToggleTest.kt`.

Minimum cases:
1. Pausing implies `isPlaying = false`; playing implies `true`.
2. A pending favorite override for song `X` is dropped once the DB set agrees.
3. A pending favorite override for song `X` is retained while the DB set still
   disagrees.

Model on `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlayOnceIsolationTest.kt`
(pure functions, JUnit Jupiter). If your step 4 implementation contains no pure
function worth testing, say so in your report and write only case 1 — do not
introduce Robolectric or an Android-dependent test to reach coverage.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.ui.OptimisticToggleTest"` → exit 0

### Step 7: Full verification

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin`
2. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest`
3. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug`

All exit 0.

## Test plan

- New: `app/src/test/java/com/yuukifst/orpheus/ui/OptimisticToggleTest.kt` (cases in step 6).
- Existing tests that must keep passing: `PlayerViewModelTest`, everything under
  `app/src/test/java/com/yuukifst/orpheus/presentation/`.
- Structural pattern: `YouTubePlayOnceIsolationTest.kt`.
- Verification: `:app:testDebugUnitTest` → all pass.

## Done criteria

ALL must hold:

- [ ] `:app:compileDebugKotlin` exits 0
- [ ] `:app:testDebugUnitTest` exits 0, new test class present and passing
- [ ] `:app:lintDebug` exits 0
- [ ] `terminalPressScale`'s press-in spec uses `OrpheusMotion.DurationMicro`
- [ ] `rg -n "indication = null" app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/CustomNavigationBarItem.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/ToggleSegmentButton.kt` → no matches
- [ ] `PlaybackStateHolder.playPause()` updates `_stablePlayerState.isPlaying` before returning
- [ ] `toggleFavorite()` updates the favorite set before awaiting the DB write
- [ ] `FullPlayerLoadingTweaks.delayControls == false` and `contentAppearThresholdPercent == 70`, with matching repository defaults
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row for 003 updated

## STOP conditions

Stop and report back (do not improvise) if:

- Adding a ripple to `CustomNavigationBarItem` visibly conflicts with its
  `AnimatedVisibility` selection indicator in a way you can detect from the code
  (e.g. the indicator is drawn in a sibling `Box` that would clip the ripple) —
  report rather than restructuring the layout.
- `PlaybackStateHolder` has no `scope` available at the point where you need to
  launch the revert job, or `activeLocalPlayer()` can construct a player as a
  side effect and no non-constructing accessor exists.
- Making favorites optimistic would require changing the type or nullability of a
  public `PlayerViewModel` property that other screens read. Report the property
  and stop.
- Ungating the controls (step 5) breaks a `DelayedContent` invariant — for example
  if `contentAppearThresholdPercent` is also used as the *close* threshold
  elsewhere. Check `rg -n "contentAppearThresholdPercent" app/src/main/java`
  before changing it.
- `:app:testDebugUnitTest` fails twice after a reasonable fix attempt.

## Maintenance notes

- The optimistic-state pattern now exists in three places (`repeatMode`,
  `isPlaying`, favorites). Any new toggle should follow it: issue the command,
  update state, launch the persistence, revert on disagreement. A reviewer should
  reject new toggles that read their state back from a Flow before updating.
- The 600 ms revert timeout is a guess bounded by "long enough that a healthy
  MediaController has answered, short enough that a wrong icon is not sticky". If
  users report a flickering play icon, that constant is the first suspect.
- `terminalPressScale`'s release bounce (250 ms, `EaseBounceStrong`) is untouched
  on purpose. If the maintainer later wants the release snappier too, that is a
  visual-design decision, not a perf fix.
- Step 5 changes user-visible defaults. Existing users who explicitly changed
  these settings keep their values; only defaults move.
