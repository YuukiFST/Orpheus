# Plan 013: Cut audio when the mini-player dismiss swipe commits

> **Executor instructions**: Follow this plan step by step.
> Run every verification command and confirm the expected result before moving to the next step.
> If anything in the "STOP conditions" section occurs, stop and report; do not improvise.
> When done, update the status row for this plan in `plans/README.md`, unless a reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat b8d4cc1..HEAD -- app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/UnifiedPlayerSheetV2.kt app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/MiniPlayerDismissGestureHandler.kt`
> If any of these files changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `b8d4cc1`, 2026-11-25

## Why this matters

Swiping the mini player sideways closes the current song.
Today the audio is stopped only after the 150 ms slide-out animation finishes, plus the time for the dismiss coroutine to run, so the song is still heard while the mini player is already leaving the screen.
The gesture handler already fires an `onDismissStarted` callback at the instant the swipe passes the dismiss threshold, before the animation.
After this plan that callback also pauses the audio, so the sound stops when the finger lifts; the existing clear (queue, service, widget) still runs after the animation, unchanged.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/presentation/components/scoped/MiniPlayerDismissGestureHandler.kt`: the swipe gesture. Read-only for this plan.
- `app/src/main/java/com/yuukifst/orpheus/presentation/components/UnifiedPlayerSheetV2.kt`: wires the handler callbacks to `PlayerViewModel`.
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`: owns the dismiss state and the audio hush helper.

`MiniPlayerDismissGestureHandler.kt:107-126`, the commit point (`OrpheusMotion.DurationQuick` is `150`, `ui/theme/Motion.kt:20`):

```kotlin
    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.4f
        if (abs(accumulatedDragX) > dismissThreshold && !dismissTriggered) {
            dismissTriggered = true
            onDismissStarted()
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(
                        durationMillis = OrpheusMotion.DurationQuick,
                        easing = OrpheusMotion.EaseSmoothOut
                    )
                )
                onDismissPlaylistAndShowUndo()
```

`UnifiedPlayerSheetV2.kt:467-476`, the wiring:

```kotlin
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() },
        onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) }
    )
```

`PlayerViewModel.kt:451-453`:

```kotlin
    fun setMiniPlayerDismissing(dismissing: Boolean) {
        _isMiniPlayerDismissing.value = dismissing
    }
```

`PlayerViewModel.kt:3344-3348`, the existing hush helper (pauses, keeps the queue):

```kotlin
    private fun hushCurrentAudioForTrackSwitch() {
        mediaController?.playWhenReady = false
        mediaController?.pause()
        runCatching { dualPlayerEngine.hushImmediateAudio() }
    }
```

The later clear, `clearPlaybackForDismiss()` (`PlayerViewModel.kt:467-497`), stops and clears both players and sends `MusicService.ACTION_CLEAR_PLAYBACK`. It stays as is.

Conventions: public ViewModel functions are plain `fun name()` with a short English comment that says why. Kotlin code style is `official`.

## Commands you will need

Commands come from `CONTRIBUTING.md`. The advisor did not run them (no JDK 21 or Android SDK on the advisor's machine).
Point `JAVA_HOME` at a JDK 21 on your machine; on Windows use `gradlew.bat`.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| ViewModel unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do not start an emulator or drive UI with `adb` unless the operator asks.

## Scope

**In scope** (the only files you modify):

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`: add one public function next to `setMiniPlayerDismissing`.
- `app/src/main/java/com/yuukifst/orpheus/presentation/components/UnifiedPlayerSheetV2.kt`: change the `onDismissStarted` lambda only.

**Out of scope** (do not touch):

- `MiniPlayerDismissGestureHandler.kt`: the threshold, the animation and the callback order stay as they are.
- `clearPlaybackForDismiss()`, `dismissPlaylistAndShowUndo()`, `PlaylistDismissUndoStateHolder.kt`: the clear path after the animation is unchanged.
- `MusicService.kt`.
- The other callers of `setMiniPlayerDismissing` (they pass `false` to reset the flag).

## Git workflow

- Branch: `perf/013-hush-audio-when-mini-player-dismiss-starts`
- One commit. Conventional Commits, matching history such as `fix(player): drop unliked songs from the Liked queue`.
  Suggested: `perf(player): cut audio when the mini-player dismiss swipe commits`
- No `Co-Authored-By:` trailer and no tool attribution in the message (`CONTRIBUTING.md`).
- Do not push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add `onMiniPlayerDismissStarted` to `PlayerViewModel`

Directly below `setMiniPlayerDismissing` (`PlayerViewModel.kt:451-453`), add:

```kotlin
    // The swipe is past the dismiss threshold: go silent now instead of after the
    // slide-out animation. The full clear still runs in dismissPlaylistAndShowUndo().
    fun onMiniPlayerDismissStarted() {
        setMiniPlayerDismissing(true)
        hushCurrentAudioForTrackSwitch()
    }
```

`hushCurrentAudioForTrackSwitch` is `private` in the same class, so no visibility change is needed.

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Call it from the sheet

In `UnifiedPlayerSheetV2.kt`, change the `onDismissStarted` argument of `rememberMiniPlayerDismissGestureHandler`:

```kotlin
        onDismissStarted = { playerViewModel.onMiniPlayerDismissStarted() }
```

**Verify**: `grep -rn "onMiniPlayerDismissStarted" app/src/main/java` → exactly 2 matches (the declaration and the call)
**Verify**: `grep -n "onDismissStarted = { playerViewModel.setMiniPlayerDismissing(true) }" app/src/main/java/com/yuukifst/orpheus/presentation/components/UnifiedPlayerSheetV2.kt` → no matches

### Step 3: Full gate

**Verify**: `./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` → `BUILD SUCCESSFUL`

## Test plan

- No new automated test.
  The change moves a pause earlier inside a gesture; it has no deterministic end-to-end reproduction without a device, and the owner's standard is end-to-end tests only.
- Existing suites must stay green: `com.yuukifst.orpheus.presentation.viewmodel.*` (includes `PlayerViewModelTest`, `PlaylistDismissUndoStateHolderTest`, `ImmediatePlaybackHushTest`).
- Manual check for the owner on a device (list it in your report, do not run it yourself):
  1. Play a song, swipe the mini player away: audio stops as the finger lifts, the mini player slides out and does not come back.
  2. Tap another song right after the dismiss: it plays normally and the mini player returns.
  3. Swipe less than 40% of the screen and release: the mini player springs back and audio keeps playing.

## Done criteria

- [ ] `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL`
- [ ] Step 2 greps return the expected counts
- [ ] `git status --short` lists only `PlayerViewModel.kt`, `UnifiedPlayerSheetV2.kt` and `plans/README.md`
- [ ] `plans/README.md` row 013 status is `DONE`

## STOP conditions

Stop and report back if:

- `onDragEnd` no longer calls `onDismissStarted()` only inside the `abs(accumulatedDragX) > dismissThreshold` branch; then the callback is not a commit signal and pausing there would cut audio on a cancelled swipe.
- `hushCurrentAudioForTrackSwitch` was removed, renamed or now clears the queue.
- `onDismissStarted` is wired to something other than `setMiniPlayerDismissing(true)` in the live code.
- A verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Known cosmetic effect: the pause makes the play/pause icon flip to "play" during the 150 ms slide-out. Report it to the owner; do not add state to hide it in this plan.
- Known edge, existing before this plan: a new drag that starts during the slide-out cancels the animation job, so the dismiss never completes. With this plan the song is then paused with the mini player still visible; pressing play resumes it.
- Reviewer: confirm the hush is only reachable from the committed-dismiss branch.
