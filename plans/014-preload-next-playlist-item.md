# Plan 014: Preload the next playlist item on the master player

> **Executor instructions**: Follow this plan step by step.
> Run every verification command and confirm the expected result before moving to the next step.
> If anything in the "STOP conditions" section occurs, stop and report; do not improvise.
> When done, update the status row for this plan in `plans/README.md`, unless a reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat b8d4cc1..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt gradle/libs.versions.toml`
> Plan 012 edits `buildAdaptiveLoadControl()` and the companion object of the same file; that diff is expected.
> For any other change, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: MED
- **Depends on**: plans/012-playback-start-buffer-media3-defaults.md (same file; land 012 first to avoid a merge conflict)
- **Category**: perf
- **Planned at**: commit `b8d4cc1`, 2026-11-25

## Why this matters

When the user presses "next", the next track is prepared only at that moment: open the source, read the container, fill the start buffer.
For a YouTube item that also means resolving the stream URL in the data source, opening a new HTTPS connection and downloading the first seconds, all after the tap.
Media3 has playlist preloading for exactly this: `ExoPlayer.setPreloadConfiguration(PreloadConfiguration)` buffers the start of the next playlist item while the current one plays.
It is disabled by default and this repo never enables it.
After this plan the master player keeps about 5 s of the next item buffered, so a skip (and an automatic advance without crossfade) starts from memory.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`: owns two ExoPlayer instances. `playerA` is the master (the one the MediaSession exposes); `playerB` is the auxiliary player used only to pre-buffer and fade in the crossfade target. After a crossfade the two swap roles.
- `gradle/libs.versions.toml:35`: `media3Session = "1.10.1"`.

Facts verified against the Media3 `1.10.1` source (`libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java`):

```java
  /** Configuration options for preloading playlist items. */
  @UnstableApi
  class PreloadConfiguration {
    /** Default preload configuration that disables playlist preloading. */
    public static final PreloadConfiguration DEFAULT =
        new PreloadConfiguration(/* targetPreloadDurationUs= */ C.TIME_UNSET);
    public PreloadConfiguration(long targetPreloadDurationUs) { ... }
  }

  @UnstableApi
  void setPreloadConfiguration(PreloadConfiguration preloadConfiguration);
```

`grep -rn "PreloadConfiguration" app/src/main` returns nothing at `b8d4cc1`.

The class is already opted in to unstable Media3 APIs (`DualPlayerEngine.kt:174`): `@OptIn(UnstableApi::class)`.

The three places where the master role is assigned:

`DualPlayerEngine.kt:630-634`, inside `initialize()`:

```kotlin
        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)

        _activeAudioSessionId.value = playerA.audioSessionId
```

`DualPlayerEngine.kt:798-803`, inside `rebuildPlayersPreservingMasterState(...)`:

```kotlin
        playerA = buildPlayer()

        addMasterPlayerListeners(playerA)
        playerA.volume = volume
        playerA.pauseAtEndOfMediaItems = pauseAtEnd
        playerA.playbackParameters = playbackParameters
```

`DualPlayerEngine.kt:1190-1198`, inside `performOverlapTransition(...)`, the role swap after a crossfade:

```kotlin
        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeWindowStartIndex = preparedWindowStartIndex
        activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue
        resetPreparedWindowState()

        playerA.pauseAtEndOfMediaItems = false
        playerB?.pauseAtEndOfMediaItems = false
        addMasterPlayerListeners(playerA)
```

The auxiliary player is created by the same `buildPlayer()` (`DualPlayerEngine.kt:957-963`), which never sets a preload configuration, so a fresh auxiliary player has preloading disabled:

```kotlin
    private fun getOrCreateAuxiliaryPlayer(): ExoPlayer {
        playerB?.let { return it }
        return buildPlayer().also { player ->
            player.setWakeMode(currentWakeMode)
            playerB = player
        }
    }
```

The class has a private `companion object` with constants (`DualPlayerEngine.kt:180-194`).

Conventions: constants live in that companion with a short comment that says why. Private helpers are `private fun` with a one-line English comment. Log with `Timber`. Kotlin code style is `official`.

## Commands you will need

Commands come from `CONTRIBUTING.md`. The advisor did not run them (no JDK 21 or Android SDK on the advisor's machine).
Point `JAVA_HOME` at a JDK 21 on your machine; on Windows use `gradlew.bat`.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Engine unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do not start an emulator or drive UI with `adb` unless the operator asks.

## Scope

**In scope** (the only file you modify):

- `app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`: one constant, one private helper, three call sites.

**Out of scope** (do not touch):

- `buildPlayer()` itself. Setting the configuration there would also enable preloading on the auxiliary player, which already holds the crossfade target and would buffer one item further for nothing.
- `prepareNext(...)`, `cancelNext()`, `TransitionController.kt`: crossfade scheduling stays as it is.
- `YouTubeStreamDataSourceFactory.kt`, `YouTubeStreamExtractor.kt`, `YouTubePlaybackViewModel.prefetchNextStream`: stream URL prefetch already exists and stays.
- `buildAdaptiveLoadControl()` (plan 012).
- `MusicService.kt`, including `onPlayerError`.

## Git workflow

- Branch: `perf/014-preload-next-playlist-item`
- One commit. Conventional Commits, matching history such as `perf(youtube): defer history writes and attach skip neighbors first`.
  Suggested: `perf(player): preload the next playlist item on the master player`
- No `Co-Authored-By:` trailer and no tool attribution in the message (`CONTRIBUTING.md`).
- Do not push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add the constant

In the `private companion object` of `DualPlayerEngine`, add:

```kotlin
        // How much of the next playlist item the master player buffers ahead, so a skip
        // starts from memory. 5 s of 160 kbps audio is about 100 KB.
        private const val NEXT_ITEM_PRELOAD_DURATION_US = 5_000_000L
```

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Add the role helper

Add this private function next to `getOrCreateAuxiliaryPlayer()`:

```kotlin
    // Only the master preloads its next item; the auxiliary player already holds the
    // crossfade target and would buffer one item further for nothing.
    private fun applyPreloadRoles() {
        if (::playerA.isInitialized) {
            playerA.setPreloadConfiguration(
                ExoPlayer.PreloadConfiguration(NEXT_ITEM_PRELOAD_DURATION_US)
            )
        }
        playerB?.setPreloadConfiguration(ExoPlayer.PreloadConfiguration.DEFAULT)
    }
```

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 3: Call it at the three role assignments

1. In `initialize()`, on the line after `addMasterPlayerListeners(playerA)`, add `applyPreloadRoles()`.
2. In `rebuildPlayersPreservingMasterState(...)`, on the line after `addMasterPlayerListeners(playerA)` and before `playerA.volume = volume`, add `applyPreloadRoles()`.
3. In `performOverlapTransition(...)`, on the line after `addMasterPlayerListeners(playerA)` (the one that follows the `playerA = incomingPlayer` / `playerB = outgoingPlayer` swap), add `applyPreloadRoles()`.

**Verify**: `grep -c "applyPreloadRoles()" app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt` → `4` (1 declaration, 3 calls)
**Verify**: `grep -rn "setPreloadConfiguration" app/src/main/java` → matches only inside `applyPreloadRoles`

### Step 4: Full gate

**Verify**: `./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` → `BUILD SUCCESSFUL`

## Test plan

- No new automated test.
  The change is player configuration; its effect is latency, which has no deterministic end-to-end reproduction without a device, and the owner's standard is end-to-end tests only.
- Existing suites must stay green: `com.yuukifst.orpheus.data.service.player.*` (includes `DualPlayerEngineNextTargetTest`, `AudioOffloadPolicyTest`).
- Manual check for the owner on a device (list it in your report, do not run it yourself). These are the checks the advisor could not do by reading code:
  1. Local queue, crossfade off: press next a few seconds into a track; the next track starts with no perceptible wait.
  2. YouTube queue: same, on Wi-Fi and on mobile data.
  3. Crossfade on: automatic transitions still fade; a manual next still works.
  4. A YouTube queue where the next item is unavailable (deleted or region-blocked video): the **current** track keeps playing to its end and only then the bad item is skipped. If the current track is interrupted, revert this plan and report (see Maintenance notes).
  5. Audio offload device (screen off, long local playback): no new stalls at track boundaries.

## Done criteria

- [ ] `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL`
- [ ] Step 3 greps return the expected counts
- [ ] `buildPlayer()` body is unchanged: `git diff b8d4cc1 -- app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt` shows no hunk inside `private fun buildPlayer()`
- [ ] `git status --short` lists only `DualPlayerEngine.kt` and `plans/README.md`
- [ ] `plans/README.md` row 014 status is `DONE`

## STOP conditions

Stop and report back if:

- `ExoPlayer.PreloadConfiguration` or `setPreloadConfiguration` does not resolve at compile time. Do not add or bump a dependency; report the Media3 version you found.
- Lint fails on the unstable API even though the class carries `@OptIn(UnstableApi::class)`.
- Any of the three call sites does not match its excerpt (drift), or a fourth place assigns `playerA`.
- A verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Unverified by the advisor: how a failed preload of the **next** item is reported. In the Media3 `1.10.1` source the preloading period is prepared from a separate preload pool (`ExoPlayerImplInternal.maybeContinuePreloading`), and the expectation is that its error surfaces only when playback reaches that item. `MusicService.onPlayerError` treats every error as belonging to `player.currentMediaItem`, so an early error would make it re-prepare or skip the current track. Manual check 4 exists to catch this.
- With crossfade on, the master preload and the auxiliary player both fetch the start of the same next item. For local files that is a second file read; for YouTube it is a second download of about 100 KB. Raising `NEXT_ITEM_PRELOAD_DURATION_US` raises that duplicate cost.
- If the engine ever gains a third place that swaps or rebuilds `playerA`, it must call `applyPreloadRoles()`.
- Deferred: reusing the already-prepared auxiliary player for a manual skip when crossfade is on (large change to the swap logic, rejected in round 3).
