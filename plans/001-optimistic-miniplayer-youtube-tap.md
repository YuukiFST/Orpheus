# Plan 001: Publish the mini player optimistically on YouTube search tap

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 85bcada..HEAD -- app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`
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

When the user taps a song in the YouTube Search tab, the mini player bar does
not appear until the YouTube stream URL has been resolved over the network
(NewPipe `StreamInfo.getInfo`), a Room row has been written, and ExoPlayer has
been prepared. In practice that is several hundred milliseconds to multiple
seconds, and the app looks frozen — the tap produces no visible result at all.

The local-library play path already solves this: `PlayerViewModel.showAndPlaySong`
calls `applyImmediatePlaybackUi(...)`, which publishes `currentSong` and makes
the sheet visible *before* any playback work starts. The YouTube path simply
never adopted that pattern. After this plan, tapping a YouTube result shows the
mini player in the next frame, with the title, channel and thumbnail already
known from the search result, while resolution continues in the background.

## Current state

### Files

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
  — contains `YouTubePlaybackController` (a `@Singleton`, despite the file name)
  and the `playOnce` / `startPlayback` / `publishPlaybackState` chain. This is
  the file you will change.
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`
  — collects `youTubePlaybackController.queueUpdates` and, in
  `applyYouTubeQueueUpdate`, is what actually makes the sheet visible. Read-only
  reference for this plan except for one optional collapse-guard fix in step 4.
- `app/src/main/java/com/yuukifst/orpheus/presentation/components/MiniPlayerVisibilityPolicy.kt`
  — the visibility gate. Read-only.
- `app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt`
  — the tap entry point (`onPlay = { viewModel.playOnce(track) }` at line 238).
  Read-only.

### The tap chain today

`YouTubeSearchScreen.kt:444-445` → `YouTubeSearchViewModel.playOnce` (line 226)
→ `YouTubePlaybackController.playOnce`.

```89:107:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
    suspend fun playOnce(track: YouTubeTrack): Boolean {
        return runCatching {
            cachedTrackRepository.recordPlayed(track)
            listeningStatsTracker.onVoluntarySelection(track.mediaId)
            val entry = PlaylistMixedTrack.YouTube(track = track, sortOrder = 0)
            currentMixedTracks = listOf(entry)
            sessionStopOnEnd = true
            startPlayback(
                tracks = currentMixedTracks,
                startIndex = 0,
                repeatMode = Player.REPEAT_MODE_OFF,
                stopOnEnd = true,
                queueName = "YouTube",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _playbackErrors.emit(userFacingPlaybackError(error))
        }.isSuccess
    }
```

`startPlayback` resolves the stream on IO **before** touching UI state:

```190:215:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
    private suspend fun startPlayback(
        tracks: List<PlaylistMixedTrack>,
        startIndex: Int,
        repeatMode: Int,
        stopOnEnd: Boolean,
        queueName: String,
    ) {
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        currentMixedTracks = tracks
        retryCountForCurrentItem = 0
        queueFillJob?.cancel()

        val startItem = withContext(Dispatchers.IO) {
            resolveMixedEntry(tracks[safeIndex])
        }

        withContext(Dispatchers.Main.immediate) {
            val player = dualPlayerEngine.masterPlayer
            attachPlaybackListener(player, stopOnEnd)
            player.shuffleModeEnabled = false
            player.repeatMode = repeatMode
            player.setMediaItem(startItem, 0L)
            player.prepare()
            player.play()
            publishPlaybackState(safeIndex, repeatMode, false, queueName)
        }
```

`publishPlaybackState` is where `currentSong` finally becomes non-null — the only
thing that makes the mini player visible:

```321:343:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
    private fun publishPlaybackState(
        index: Int,
        repeatMode: Int,
        isShuffleEnabled: Boolean,
        queueName: String = "YouTube",
    ) {
        val startSong = songForMixedIndex(index)
        startSong?.let { song ->
            syncListeningStats(dualPlayerEngine.masterPlayer, song, forceNewSession = true)
        }
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = startSong,
                currentMediaItemIndex = index,
                isPlaying = true,
                playWhenReady = true,
                totalDuration = startSong?.duration?.coerceAtLeast(0L) ?: 0L,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
            )
        }
        publishQueueUpdate(index, queueName)
    }
```

### Why the mini player waits

`MiniPlayerVisibilityPolicy.shouldShowPlayerContent` gates on `currentSongId != null`:

```7:11:app/src/main/java/com/yuukifst/orpheus/presentation/components/MiniPlayerVisibilityPolicy.kt
    fun shouldShowPlayerContent(
        currentSongId: String?,
        showDismissUndoBar: Boolean,
        dismissJustCommitted: Boolean = false,
    ): Boolean = currentSongId != null && !showDismissUndoBar && !dismissJustCommitted
```

`currentSong` comes from `playbackStateHolder.stablePlayerState`, read by
`UnifiedPlayerSheetV2` (line 160-162, 226-237) and by the bottom bar in
`MainActivity.kt:735-753`. Sheet *visibility* (`_isSheetVisible`) is set by
`PlayerViewModel.applyYouTubeQueueUpdate`, which runs on every
`queueUpdates` emission:

```1082:1096:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    private fun applyYouTubeQueueUpdate(update: YouTubePlaybackQueueUpdate) {
        _playerUiState.update { state ->
            state.copy(
                currentPlaybackQueue = update.songs.toPersistentList(),
                currentQueueSourceName = update.queueName,
            )
        }
        playbackStateHolder.updateStablePlayerState { state ->
            state.copy(currentMediaItemIndex = update.currentIndex)
        }
        setSheetVisibleUnlessDismissUndoPending()
        if (_sheetState.value == PlayerSheetState.EXPANDED) {
            _sheetState.value = PlayerSheetState.COLLAPSED
        }
    }
```

So publishing `currentSong` **and** emitting one `queueUpdates` event early is
sufficient to show the mini player. No new plumbing into `PlayerViewModel` or
`MainActivity` is needed.

### The pattern to copy (local library path)

```3280:3299:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    private fun applyImmediatePlaybackUi(
        song: Song,
        queueSongs: List<Song>,
        queueName: String,
        mediaItemIndex: Int = queueSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
    ) {
        // Invalidate any in-flight dismiss ACTION_CLEAR_PLAYBACK for the prior session.
        PlaybackClearGeneration.bump()
        _dismissJustCommitted.value = false
        setMiniPlayerDismissing(false)
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                currentMediaItemIndex = mediaItemIndex,
                isPlaying = true,
                playWhenReady = true,
                totalDuration = song.duration.coerceAtLeast(0L),
            )
        }
        setSheetVisibleUnlessDismissUndoPending()
```

The optimistic `Song` for a YouTube track already exists as an extension in the
same file — it carries title, channel, thumbnail URL and duration from the search
result, so the mini player has everything it needs to render:

```39:53:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
internal fun YouTubeTrack.toPlaybackSong(filePath: String? = null): Song = Song(
    id = mediaId,
    title = effectiveTitle,
    artist = channelName,
    artistId = -1L,
    album = "YouTube",
    albumId = -1L,
    path = filePath.orEmpty(),
    contentUriString = filePath.orEmpty(),
    albumArtUriString = thumbnailUrl.takeIf { it.isNotBlank() },
    duration = durationMs,
    mimeType = null,
    bitrate = null,
    sampleRate = null,
)
```

### CRITICAL constraint — do not touch `dualPlayerEngine` in the optimistic path

`DualPlayerEngine.masterPlayer` is a getter that *constructs ExoPlayer*:

```484:488:app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt
    val masterPlayer: Player
        get() {
            initialize()
            return playerA
        }
```

`publishPlaybackState` currently reads `dualPlayerEngine.masterPlayer` (via
`syncListeningStats`). If your new optimistic publish does the same, it will
build ExoPlayer on the main thread **before** the frame that shows the mini
player, which defeats the entire plan. The optimistic publish must not reference
`dualPlayerEngine` at all.

### Repo conventions that apply

- Log with **Timber**, never `android.util.Log` (see `CONTRIBUTING.md`).
- Unit tests use **JUnit Jupiter** (`org.junit.jupiter.api.Test`), not JUnit 4.
- Publish UI state **before** writing to the database (`CLAUDE.md` performance rules).
- Prefer small pure helper functions for anything you want to unit test — see the
  existing `shouldReplaceQueueForSearchPlay` / `isSearchQueueName` top-level
  helpers in `YouTubePlaybackViewModel.kt:31-37` and their test in
  `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlayOnceIsolationTest.kt`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` | exit 0, all pass |
| Single test class | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeOptimisticPlaybackTest"` | exit 0 |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | exit 0 |

Run all Gradle commands from the repo root.

**Do NOT** start an emulator, run `adb`, install the APK, or drive the UI. The
maintainer tests on a physical device (`CLAUDE.md`, hard rule).

## Scope

**In scope**:
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
- `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeOptimisticPlaybackTest.kt` (create)
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` — **only** the
  `applyYouTubeQueueUpdate` function, and only if step 4 turns out to be needed.

**Out of scope** (do NOT touch, even though they look related):
- `UnifiedPlayerSheetV2.kt`, `MainActivity.kt`, `MiniPlayerVisibilityPolicy.kt` — the
  visibility gate already works; the fix belongs in the producer, not the consumer.
- Any animation duration (`Motion.kt`, `SheetMotionController.kt`, `SheetThemeState.kt`).
  The 250 ms slide-in runs *after* the state flip and is intentional polish.
- `YouTubeStreamExtractor.kt`, `YouTubePlaybackResolver.kt` — resolution itself is
  not being changed here. Prefetching is plan 005.
- `YouTubeSearchViewModel.kt` / `YouTubeSearchScreen.kt` — the call site is fine.
- `DualPlayerEngine.kt`.

## Git workflow

- Branch: `advisor/001-optimistic-miniplayer-youtube-tap`
- One commit per step is fine; squash is also fine. Message style from `git log`
  is a short imperative subject line, no `Co-Authored-By:` trailer (see
  `CONTRIBUTING.md`).
- Do NOT push or open a PR unless the operator asks.

## Steps

### Step 1: Add a pure helper that builds the optimistic state transform

At the top of `YouTubePlaybackViewModel.kt`, next to the existing internal
helpers (`shouldReplaceQueueForSearchPlay`, `isSearchQueueName`,
`toPlaybackSong`), add two `internal` declarations:

```kotlin
internal data class OptimisticYouTubePlaybackUi(
    val song: Song,
    val mediaItemIndex: Int,
    val queueName: String,
)

/**
 * The mini player is gated on `currentSong != null`, so the search-result
 * metadata is published before the stream is resolved. Must not read the
 * player: `DualPlayerEngine.masterPlayer` builds ExoPlayer on first access.
 */
internal fun optimisticUiForTrack(
    track: YouTubeTrack,
    index: Int = 0,
    queueName: String = "YouTube",
): OptimisticYouTubePlaybackUi = OptimisticYouTubePlaybackUi(
    song = track.toPlaybackSong(),
    mediaItemIndex = index.coerceAtLeast(0),
    queueName = queueName,
)
```

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 2: Publish the optimistic state at the very top of `playOnce`

Rewrite `YouTubePlaybackController.playOnce` so that, in order:

1. The optimistic entry is installed into `currentMixedTracks` and
   `sessionStopOnEnd = true` (unchanged semantics, just moved first).
2. `publishOptimisticPlaybackState(...)` runs on `Dispatchers.Main.immediate`
   (see below) — this is the frame that shows the mini player.
3. `listeningStatsTracker.onVoluntarySelection(track.mediaId)` (in-memory, cheap).
4. `cachedTrackRepository.recordPlayed(track)` is moved **off** the critical path
   into `scope.launch { ... }` — it is a Room read + upsert and nothing in the UI
   waits on it. Wrap it in `runCatching` so a DB failure cannot surface as a
   playback error.
5. `startPlayback(...)` as before.
6. On failure, roll back (step 3) and emit the error as today.

Target shape:

```kotlin
    suspend fun playOnce(track: YouTubeTrack): Boolean {
        val previousSong = playbackStateHolder.stablePlayerState.value.currentSong
        val optimistic = optimisticUiForTrack(track)
        currentMixedTracks = listOf(PlaylistMixedTrack.YouTube(track = track, sortOrder = 0))
        sessionStopOnEnd = true
        withContext(Dispatchers.Main.immediate) {
            publishOptimisticPlaybackState(optimistic)
        }
        listeningStatsTracker.onVoluntarySelection(track.mediaId)
        scope.launch { runCatching { cachedTrackRepository.recordPlayed(track) } }

        return runCatching {
            startPlayback(
                tracks = currentMixedTracks,
                startIndex = 0,
                repeatMode = Player.REPEAT_MODE_OFF,
                stopOnEnd = true,
                queueName = "YouTube",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            rollbackOptimisticPlaybackState(track, previousSong)
            _playbackErrors.emit(userFacingPlaybackError(error))
        }.isSuccess
    }
```

And add the publisher, which deliberately does **not** touch `dualPlayerEngine`:

```kotlin
    private fun publishOptimisticPlaybackState(optimistic: OptimisticYouTubePlaybackUi) {
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = optimistic.song,
                currentMediaItemIndex = optimistic.mediaItemIndex,
                isPlaying = true,
                playWhenReady = true,
                totalDuration = optimistic.song.duration.coerceAtLeast(0L),
            )
        }
        publishQueueUpdate(optimistic.mediaItemIndex, optimistic.queueName)
    }
```

`publishQueueUpdate` already exists (line 345) and reads only
`currentMixedTracks`, which you set immediately above — so the emission carries
the correct single-song queue and triggers
`PlayerViewModel.applyYouTubeQueueUpdate` → `setSheetVisibleUnlessDismissUndoPending()`.

Notes:
- Keep `playOnce`'s return type and `Boolean` semantics identical; callers are
  `YouTubeSearchViewModel.playOnce` (line 226) and
  `YouTubePlaybackController.addToQueue` (line 117).
- `startPlayback`'s own `publishPlaybackState` at the end stays as-is. It will
  publish the same values a second time; `StateFlow` deduplicates equal values,
  so this is a no-op re-emission and is the correct place for the
  `syncListeningStats` call that needs the real player.

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 3: Add the rollback so a failed resolution does not leave a phantom mini player

Add to `YouTubePlaybackController`:

```kotlin
    private suspend fun rollbackOptimisticPlaybackState(
        track: YouTubeTrack,
        previousSong: Song?,
    ) = withContext(Dispatchers.Main.immediate) {
        val current = playbackStateHolder.stablePlayerState.value.currentSong
        if (current?.id != track.mediaId) return@withContext
        currentMixedTracks = emptyList()
        sessionStopOnEnd = false
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = previousSong,
                isPlaying = false,
                playWhenReady = false,
                totalDuration = previousSong?.duration?.coerceAtLeast(0L) ?: 0L,
            )
        }
    }
```

The `current?.id != track.mediaId` guard is load-bearing: if the user tapped a
second song while the first was resolving, the failure of the first must not
clobber the second. Timber-log the rollback at `w` level with the video id so a
device report can confirm it fired.

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 4: Confirm the queue-update collapse guard does not fight the optimistic emission

Read `PlayerViewModel.applyYouTubeQueueUpdate` (lines 1082-1096, quoted above).
It collapses the sheet when it is `EXPANDED`. With the optimistic emission this
now fires one extra time per tap, at a moment when the full player may be open.

- If the sheet is *collapsed* (the normal search-tap case) this is a no-op — do
  nothing and move on.
- Only if you can show a concrete regression (an already-expanded full player
  collapsing on a YouTube tap that previously did not collapse) may you change
  this function, and then the only allowed change is to make the collapse
  conditional on the current song actually changing. Note that the second
  emission from `publishPlaybackState` also reaches this function today, so the
  collapse already happens once per tap; extra emissions with the same song are
  expected to be harmless.

**Verify**: no code change required in the common case. Record in your report
which branch you took.

### Step 5: Add unit tests for the new pure helper and the ordering contract

Create
`app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeOptimisticPlaybackTest.kt`,
modeled structurally on the existing
`app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlayOnceIsolationTest.kt`
(same package, JUnit Jupiter, no Android framework objects).

Cases:
1. `optimisticUiForTrack` maps a `YouTubeTrack` to a `Song` whose `id` equals
   `track.mediaId`, `title` equals `track.effectiveTitle`, `artist` equals
   `track.channelName`, `albumArtUriString` equals the thumbnail URL, and
   `duration` equals `track.durationMs`.
2. `optimisticUiForTrack` with a blank `thumbnailUrl` yields
   `albumArtUriString == null` (the `takeIf { it.isNotBlank() }` behavior the mini
   player relies on to fall back to a placeholder).
3. A negative `index` is coerced to 0.
4. The resulting `Song.id` satisfies the app's YouTube-media-id predicate, so the
   optimistic song is still recognized as a YouTube track downstream. Use the
   existing helper — find it with
   `rg -n "fun String.isYouTubeMediaId" app/src/main/java` and import it. If that
   helper is not accessible from a unit test, drop this case rather than changing
   its visibility.

Construct `YouTubeTrack` the way `YouTubeSearchRepositoryCacheTest.kt:12-20` does
(named arguments: `videoId`, `title`, `channelName`, `thumbnailUrl`, `durationMs`).

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeOptimisticPlaybackTest"`
→ exit 0, 3 or 4 tests pass.

### Step 6: Full verification

Run, in order:

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin`
2. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest`
3. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug`

All three must exit 0. Then confirm no `dualPlayerEngine` reference leaked into
the optimistic path:

`rg -n "dualPlayerEngine" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`

Expected: matches only inside `addToQueue`, `startPlayback`, `publishPlaybackState`,
`attachPlaybackListener`'s error/retry paths and `songForMixedIndex`-adjacent code
— **no match inside `playOnce`, `publishOptimisticPlaybackState`, or
`rollbackOptimisticPlaybackState`**.

## Test plan

- New file: `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeOptimisticPlaybackTest.kt`,
  cases as listed in step 5 (happy-path mapping, blank thumbnail, negative index
  coercion, YouTube media-id recognition).
- Structural pattern to follow: `YouTubePlayOnceIsolationTest.kt` (pure functions,
  `org.junit.jupiter.api.Assertions`).
- Do **not** attempt to unit test `playOnce` end to end. It depends on
  `DualPlayerEngine`, Room and NewPipe; mocking that graph is more risk than
  value here. The behavioral verification is the maintainer's on-device test.
- Verification: `:app:testDebugUnitTest` → all pass, including the new tests.

## Done criteria

ALL must hold:

- [ ] `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` exits 0
- [ ] `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` exits 0 and includes the new `YouTubeOptimisticPlaybackTest`
- [ ] `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` exits 0
- [ ] In `playOnce`, `playbackStateHolder.updateStablePlayerState` is reached before any `withContext(Dispatchers.IO)` and before `cachedTrackRepository.recordPlayed`
- [ ] `rg -n "recordPlayed" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt` shows the `playOnce` call inside a `scope.launch { ... }`
- [ ] No `dualPlayerEngine` reference inside `playOnce` / `publishOptimisticPlaybackState` / `rollbackOptimisticPlaybackState`
- [ ] `git status` shows only the in-scope files modified
- [ ] `plans/README.md` status row for 001 updated

## STOP conditions

Stop and report back (do not improvise) if:

- The code at any "Current state" excerpt does not match the live file.
- `playbackStateHolder.updateStablePlayerState` turns out not to be safe to call
  from `Dispatchers.Main.immediate` outside a player callback (e.g. it asserts a
  player thread). In that case report what it requires instead of working around it.
- Publishing the optimistic state makes an existing unit test fail — especially
  anything in `PlayerViewModelTest.kt`. Report the failing assertion; do not
  weaken the test.
- You find that something other than `currentSong != null` also gates mini player
  visibility for YouTube tracks specifically (e.g. a check on `song.path` being
  non-empty). The optimistic `Song` has an empty `path`; if a gate depends on
  that, report it rather than inventing a fake path.
- The fix appears to require touching `UnifiedPlayerSheetV2.kt` or `MainActivity.kt`.

## Maintenance notes

- The invariant to protect in review: **`playOnce` publishes UI state before it
  awaits anything.** Any future addition to `playOnce` (analytics, DB, network)
  must go after the optimistic publish or into `scope.launch`.
- `publishPlaybackState` and `publishOptimisticPlaybackState` now overlap. If
  someone adds a field to one, they must consider the other; a divergence shows
  up as a field that flickers to its real value ~1 s after the mini player appears.
- The optimistic `Song` has `path = ""` and `duration` taken from search metadata,
  which YouTube sometimes reports as 0 for live/premiere items. The progress bar
  will correct itself when the real duration arrives from ExoPlayer.
- Deferred out of this plan on purpose: prewarming ExoPlayer when the YouTube
  Search screen opens, and prefetching the stream URL of the first result
  (plan 005). Both shorten the *audio* start time; this plan only fixes the
  *visual* latency.
