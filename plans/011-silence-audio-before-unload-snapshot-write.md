# Plan 011: Silence audio before the unload snapshot write

> **Executor instructions**: Follow this plan step by step.
> Run every verification command and confirm the expected result before moving to the next step.
> If anything in the "STOP conditions" section occurs, stop and report; do not improvise.
> When done, update the status row for this plan in `plans/README.md`, unless a reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat b8d4cc1..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/service/MusicService.kt app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`
> If either file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `b8d4cc1`, 2026-11-25

## Why this matters

When the user swipes the app away from Recents, `MusicService.onTaskRemoved` calls `stopPlaybackAndUnload(awaitSnapshotWrite = true)`.
That function blocks the main thread in `runBlocking` for up to 750 ms waiting for the playback snapshot to reach disk, and only afterwards stops the players.
The user hears the song keep playing for that whole wait after closing the app.
The snapshot is already captured in memory (queue, index, position) before the wait starts, so nothing requires audio to keep running during it.
After this plan the audio goes silent first and the disk wait happens in silence; the restored queue and position are unchanged.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/service/MusicService.kt`: the playback service. `stopPlaybackAndUnload` is the only function this plan changes.
- `app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`: owns the two ExoPlayer instances. Read-only for this plan; you call one existing function on it.

`MusicService.kt:2376-2424` today:

```kotlin
    private fun stopPlaybackAndUnload(
        reason: String,
        preservePlaybackSnapshot: Boolean = true,
        awaitSnapshotWrite: Boolean = false,
    ) {
        Timber.tag(TAG).d(
            "Stopping playback and unloading service. reason=%s",
            reason
        )
        isPlaybackUnloadInProgress = true
        followUpMediaSessionUiRefreshJob?.cancel()
        mediaSessionButtonRefreshJob?.cancel()
        debouncedWidgetUpdateJob?.cancel()
        playbackSnapshotPersistJob?.cancel()
        playbackSnapshotTickerJob?.cancel()

        val sessionToRelease = mediaSession
        val player = sessionToRelease?.player ?: engine.masterPlayer

        clearHeadsetReconnectResume()
        playbackTimerController.release()

        if (preservePlaybackSnapshot) {
            persistPlaybackSnapshotOnUnload()
        } else {
            clearPlaybackSnapshotOnUnload()
        }
        if (awaitSnapshotWrite) {
            // appScope runs on IO, so blocking main here cannot deadlock the write.
            runBlocking {
                withTimeoutOrNull(PLAYBACK_SNAPSHOT_UNLOAD_WRITE_TIMEOUT_MS) {
                    playbackSnapshotUnloadWriteJob?.join()
                }
            }
        }

        listeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        engine.stopAndClearAllPlayers()

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        requestWidgetFullUpdate(force = true)
        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }
```

Facts you can rely on (verified at `b8d4cc1`):

- `PLAYBACK_SNAPSHOT_UNLOAD_WRITE_TIMEOUT_MS = 750L` (`MusicService.kt:242`).
- `persistPlaybackSnapshotOnUnload()` (`MusicService.kt:2426-2429`) calls `capturePlaybackSnapshotFromPlayer(playWhenReadyOverride = false)` synchronously, then launches the disk write on `appScope`. The position and queue are therefore read **before** it returns.
- `schedulePlaybackSnapshotPersist` returns early while `isPlaybackUnloadInProgress` is true (`MusicService.kt:1500-1503`), so a pause callback cannot overwrite the unload snapshot.
- `DualPlayerEngine.hushImmediateAudio()` (`DualPlayerEngine.kt:589-597`) pauses both players without clearing the queue and without creating players:

```kotlin
    fun hushImmediateAudio() {
        if (!::playerA.isInitialized) return
        playerA.playWhenReady = false
        playerA.pause()
        playerB?.let { auxiliary ->
            auxiliary.playWhenReady = false
            auxiliary.pause()
        }
    }
```

Conventions: log with `Timber`, never `android.util.Log`. Kotlin code style is `official`. Comments explain why, in English.

## Commands you will need

Commands come from `CONTRIBUTING.md`. The advisor did not run them (no JDK 21 or Android SDK on the advisor's machine).
Point `JAVA_HOME` at a JDK 21 on your machine; on Windows use `gradlew.bat`.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Service unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.*"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do not start an emulator or drive UI with `adb` unless the operator asks.

## Scope

**In scope** (the only file you modify):

- `app/src/main/java/com/yuukifst/orpheus/data/service/MusicService.kt`, function `stopPlaybackAndUnload` only.

**Out of scope** (do not touch):

- `DualPlayerEngine.kt`. `hushImmediateAudio()` already does what is needed.
- `PLAYBACK_SNAPSHOT_UNLOAD_WRITE_TIMEOUT_MS` and the `runBlocking` wait itself. The wait protects the snapshot against process death right after `stopSelf()`.
- `clearPlaybackKeepService`, `pausePlaybackKeepUi`, `onDestroy`. They have no blocking wait.
- `ListeningStatsTracker`. Its `forceSynchronousPersistence` parameter is unused (`ListeningStatsTracker.kt:270`), so that call does not block.

## Git workflow

- Branch: `perf/011-silence-audio-before-unload-snapshot-write`
- One commit. Conventional Commits, matching history such as `fix(player): reopen the app paused where it was closed`.
  Suggested: `perf(player): silence audio before the unload snapshot write`
- No `Co-Authored-By:` trailer and no tool attribution in the message (`CONTRIBUTING.md`).
- Do not push or open a PR unless the operator instructed it.

## Steps

### Step 1: Hush right after the snapshot is captured

In `stopPlaybackAndUnload`, insert the hush between the snapshot block and the `if (awaitSnapshotWrite)` block.
Target shape:

```kotlin
        if (preservePlaybackSnapshot) {
            persistPlaybackSnapshotOnUnload()
        } else {
            clearPlaybackSnapshotOnUnload()
        }
        // The snapshot above already holds queue and position: go silent now so the
        // disk wait below (up to 750 ms on a Recents swipe) is not heard as late audio.
        engine.hushImmediateAudio()
        if (awaitSnapshotWrite) {
```

Leave every other line of the function in its current order, including `engine.stopAndClearAllPlayers()` after the wait.

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Confirm the order

**Verify**: `grep -n "engine.hushImmediateAudio()\|runBlocking {\|engine.stopAndClearAllPlayers()" app/src/main/java/com/yuukifst/orpheus/data/service/MusicService.kt`
→ inside `stopPlaybackAndUnload`, the `hushImmediateAudio` line number is lower than the `runBlocking {` line number, which is lower than the `stopAndClearAllPlayers` line number.

### Step 3: Full gate

**Verify**: `./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.*"` → `BUILD SUCCESSFUL`

## Test plan

- No new automated test.
  The change is an ordering of side effects inside an Android `Service`; it has no deterministic end-to-end reproduction without a device, and the owner's standard is end-to-end tests only.
- Existing suites must stay green: `com.yuukifst.orpheus.data.service.*` (includes `NotificationDismissCommandTest`, `PlaybackSnapshotUrisTest`, `PlaybackClearGenerationTest`).
- Manual check for the owner on a device (list it in your report, do not run it yourself):
  1. Play a song, swipe the app away from Recents: audio stops with no audible tail.
  2. Reopen the app: same track, paused, at the position where it was closed.
  3. Repeat during a crossfade: both tracks stop.

## Done criteria

- [ ] `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.*"` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL`
- [ ] Step 2 grep shows `hushImmediateAudio` before `runBlocking {` inside `stopPlaybackAndUnload`
- [ ] `git status --short` lists only `MusicService.kt` and `plans/README.md`
- [ ] `plans/README.md` row 011 status is `DONE`

## STOP conditions

Stop and report back if:

- `stopPlaybackAndUnload` does not match the excerpt above (drift).
- `persistPlaybackSnapshotOnUnload` no longer captures the snapshot synchronously before returning; then pausing first could change the stored position, and the order needs a new decision.
- `DualPlayerEngine.hushImmediateAudio` was removed or now clears media items.
- A verification fails twice after a reasonable fix attempt.

## Maintenance notes

- If the snapshot capture ever moves into the async write job, capture must still happen before the hush, or the stored `playWhenReady`/position may shift.
- Reviewer: check that nothing was added between the capture and the hush.
- Deferred: the snapshot itself is a full-queue JSON blob in the shared `settings` DataStore (round 3 finding 6, not planned here). Moving it to its own file would also shorten this wait.
