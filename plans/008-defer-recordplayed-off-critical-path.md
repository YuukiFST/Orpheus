# Plan 008: Defer YouTube `recordPlayed` off the audio critical path

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat 6e75b37..HEAD -- app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeCachedTrackRepository.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `6e75b37`, 2026-08-28

## Why this matters

`playOnce` already records history **after** optimistic UI, on a fire-and-forget
coroutine. `playPlaylist` and `playMixedPlaylist` still `forEach { recordPlayed }`
**awaited** on the caller before `startPlayback`. `recordPlayed` is Room I/O
(`Dispatchers.IO`). A 40-track YouTube playlist pays 40 writes before NewPipe
even extracts the start item. Same history still happens; it must not block
time-to-first-audio.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
  — `YouTubePlaybackController.playOnce` / `playPlaylist` / `playMixedPlaylist`.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeCachedTrackRepository.kt`
  — `suspend fun recordPlayed(track: YouTubeTrack)` (Room). Do **not** change
  its signature unless tests force it; this plan only changes **when** it is
  called.

Correct pattern (`playOnce`):

```306:307:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
        listeningStatsTracker.onVoluntarySelection(track.mediaId)
        scope.launch { runCatching { cachedTrackRepository.recordPlayed(track) } }
```

Blocking pattern (remove the await, keep the writes):

```396:411:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
        runCatching {
            val mixed = tracks.mapIndexed { index, track ->
                PlaylistMixedTrack.YouTube(track = track, sortOrder = index)
            }
            mixed.forEach { entry ->
                cachedTrackRepository.recordPlayed(entry.track)
            }
            sessionStopOnEnd = false
            startPlayback(...)
        }
```

```444:451:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
            tracks.forEach { entry ->
                if (entry is PlaylistMixedTrack.YouTube) {
                    cachedTrackRepository.recordPlayed(entry.track)
                }
            }
            sessionStopOnEnd = stopOnEnd
            startPlayback(...)
```

Conventions: `scope` on the controller is
`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` — launching
`recordPlayed` from it is OK because `recordPlayed` switches to IO. Wrap in
`runCatching`; never let history failures cancel `startPlayback`. Do not
change `listeningStatsTracker` (separate from Room history).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Existing YouTube playback tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeMixedQueueAttachTest"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do **not** start the emulator or drive UI with `adb` unless the operator asks.

## Suggested executor toolkit

- If available: `verification-before-completion` before claiming DONE.

## Scope

**In scope**:

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
- Optional tiny helper in that same file, e.g.
  `internal fun youtubeTracksToRecord(tracks: List<PlaylistMixedTrack>): List<YouTubeTrack>`
- Optional `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeRecordPlayedTracksTest.kt`
  if you extract the helper (filter YouTube entries, preserve order).

**Out of scope**:

- Changing `YouTubeCachedTrackRepository` schema / what `recordPlayed` stores.
- Incremental queue fill (plan 007).
- Stream extract in-flight sharing (plan 009).
- `playOnce` (already correct). Do not "optimize" it further.

## Git workflow

- Branch: `advisor/008-defer-recordplayed-off-critical-path`
- Commits: e.g. `fix(youtube): record playlist plays off the startPlayback path`
- Do **not** push or open a PR unless the operator asked.

## Steps

### Step 1: Helper (optional but preferred)

```kotlin
internal fun youtubeTracksToRecord(tracks: List<PlaylistMixedTrack>): List<YouTubeTrack> =
    tracks.mapNotNull { (it as? PlaylistMixedTrack.YouTube)?.track }
```

For `playPlaylist`, after building `mixed`, call
`youtubeTracksToRecord(mixed)` (all YouTube).

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 2: Fire-and-forget before `startPlayback`

Add a private method on `YouTubePlaybackController`:

```kotlin
private fun scheduleRecordPlayed(tracks: List<YouTubeTrack>) {
    if (tracks.isEmpty()) return
    scope.launch {
        tracks.forEach { track ->
            runCatching { cachedTrackRepository.recordPlayed(track) }
        }
    }
}
```

Use it from:

- `playOnce` — `scheduleRecordPlayed(listOf(track))` **instead of** the inline
  `scope.launch` (behavior identical; one path).
- `playPlaylist` — `scheduleRecordPlayed(youtubeTracksToRecord(mixed))` then
  `startPlayback`. **No** `forEach { recordPlayed }` in the `runCatching` that
  wraps `startPlayback`.
- `playMixedPlaylist` — same, YouTube entries only.

`scheduleRecordPlayed` must run **after** optimistic UI (already true in
`playPlaylist` / `playMixedPlaylist` today) and **must not** be `await`ed
before `startPlayback`.

Do **not** `join` the Job. Sequential `forEach` inside the launch is OK
(history order); the point is it does not block extract.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 3: Helper tests (if Step 1 landed)

JUnit Jupiter: empty list; mixed Local+YouTube keeps only YouTube order;
all-YouTube playlist maps 1:1.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeRecordPlayedTracksTest"`
→ pass (skip this command if you did not add the file)

### Step 4: Lint

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeMixedQueueAttachTest"`
→ `BUILD SUCCESSFUL`

## Test plan

- New: helper tests if extracted.
- No fake Room test required.
- Reviewer: first audio of a YouTube playlist must not wait on N Room writes.

## Done criteria

- [ ] `playPlaylist` / `playMixedPlaylist` do not `await` `recordPlayed` before
      `startPlayback` (`grep -n "recordPlayed" YouTubePlaybackViewModel.kt`
      shows calls only inside `scope.launch` / `scheduleRecordPlayed`).
- [ ] Mixed playlists still skip `PlaylistMixedTrack.Local`.
- [ ] `playOnce` still records (via the shared helper).
- [ ] No files outside in-scope list.
- [ ] `plans/README.md` row 008 status `DONE`.

## STOP conditions

- `recordPlayed` is required to finish before `startPlayback` for a **correctness**
  reason documented in a new comment (e.g. mediaId uniqueness) — report; do not
  keep blocking "just in case".
- `cachedTrackRepository` is no longer injected on the controller.

## Maintenance notes

- Failures stay swallowed (`runCatching`) like `playOnce` today.
- Do not batch-insert unless `YouTubeCachedTrackRepository` already has a bulk
  API — out of scope.
