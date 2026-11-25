# Plan 015: A Library tap starts audio before the full queue is built

> **Executor instructions**: Follow this plan step by step.
> Run every verification command and confirm the expected result before moving to the next step.
> If anything in the "STOP conditions" section occurs, stop and report; do not improvise.
> When done, update the status row for this plan in `plans/README.md`, unless a reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat b8d4cc1..HEAD -- app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`
> Plan 013 adds `onMiniPlayerDismissStarted()` near line 451 of the same file; that diff is expected and shifts the line numbers below by a few lines.
> For any other change, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: plans/013-hush-audio-when-mini-player-dismiss-starts.md (same file; land 013 first to avoid a merge conflict)
- **Category**: perf
- **Planned at**: commit `b8d4cc1`, 2026-11-25

## Why this matters

Tapping a song in the Library tab (and in Home "Your Mix") silences the previous song at once, but the tapped song starts only after the whole library queue has been built: a Room query for every sorted song id, then a pass that resolves each id to a `Song`.
The user hears silence for that whole time, and it grows with the library.
The code already knows how to play one item first and attach the rest of the queue around it afterwards (`internalPlaySongs` does exactly that), but only after the queue exists.
After this plan the tapped song starts on the tap, the queue is built in parallel, and the rest of the queue is attached around the playing item without restarting it.

Not measured: the advisor read the code but could not run the app, so the size of the gain is unknown; it is the duration of `queueProvider()` for the user's library.

## Current state

One file is involved: `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`.

The Library entry point, `PlayerViewModel.kt:727-743`:

```kotlin
    fun showAndPlaySongFromLibrary(
        song: Song,
        queueName: String = "Library",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build full library queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentSongSortOption
            val storageFilter = playerUiState.value.currentStorageFilter
            val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)
            resolvePlaybackQueueFromSortedIds(sortedIds)
        }
    }
```

The wait, `PlayerViewModel.kt:782-845`:

```kotlin
    private fun launchLatestFullQueuePlayback(
        song: Song,
        queueName: String,
        isVoluntaryPlay: Boolean,
        failureMessage: String,
        queueProvider: suspend () -> List<Song>
    ) {
        cancelPendingFullQueuePlayback()
        cancelPendingDirectPlayback()
        applyImmediatePlaybackUi(
            song = song,
            queueSongs = listOf(song),
            queueName = queueName,
        )
        val requestToken = fullQueuePlaybackToken

        fullQueuePlaybackJob = viewModelScope.launch {
            try {
                val fullQueue = queueProvider()
                throwIfFullQueuePlaybackRequestIsStale(requestToken)
                handOffFullQueuePlayback(
                    song = song,
                    fullQueue = fullQueue,
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestToken != fullQueuePlaybackToken) {
                    return@launch
                }

                Timber.e(error, failureMessage, song.id)
                val fallbackQueue = libraryStateHolder.allSongs.value.takeIf { songs ->
                    songs.isNotEmpty() && songs.any { it.id == song.id }
                } ?: listOf(song)
                handOffFullQueuePlayback(
                    song = song,
                    fullQueue = fallbackQueue,
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay
                )
            }
        }
    }

    private fun handOffFullQueuePlayback(
        song: Song,
        fullQueue: List<Song>,
        queueName: String,
        isVoluntaryPlay: Boolean
    ) {
        // Clear the job ref before playSongs() cancels pending full-queue work;
        // otherwise this coroutine cancels itself and playback never starts.
        fullQueuePlaybackJob = null
        showAndPlaySong(
            song = song,
            contextSongs = fullQueue.ifEmpty { listOf(song) },
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            cancelPendingQueueBuild = false
        )
    }
```

The hush that `applyImmediatePlaybackUi` runs first, `PlayerViewModel.kt:3344-3358`:

```kotlin
    private fun hushCurrentAudioForTrackSwitch() {
        mediaController?.playWhenReady = false
        mediaController?.pause()
        runCatching { dualPlayerEngine.hushImmediateAudio() }
    }

    private fun applyImmediatePlaybackUi(
        song: Song,
        queueSongs: List<Song>,
        queueName: String,
        mediaItemIndex: Int = queueSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
    ) {
        // Cut prior audio on the same main-thread turn as the tap, before queue
        // hydrate / MediaItem prepare can finish.
        hushCurrentAudioForTrackSwitch()
```

The existing "play one item, attach the rest" pattern, `PlayerViewModel.kt:3545-3573` inside `internalPlaySongs`. Model the new code on it:

```kotlin
        val startMediaItem = buildResolvedPlaybackMediaItem(effectiveStartSong)

        val playSongsAction = {
            dualPlayerEngine.cancelNext()
            val enginePlayer = dualPlayerEngine.masterPlayer

            enginePlayer.setMediaItem(startMediaItem, 0L)
            enginePlayer.prepare()
            enginePlayer.play()
            _playerUiState.update { it.copy(isLoadingInitialSongs = false) }

            if (songsToPlay.size > 1) {
                pendingQueueSegmentsJob?.cancel()
                pendingQueueSegmentsJob = viewModelScope.launch {
                    val preparedSegments = preparePlaybackQueueSegments(
                        songsToPlay = songsToPlay,
                        startSongId = effectiveStartSong.id,
                        playlistId = playlistId
                    )
                    withContext(Dispatchers.Main.immediate) {
                        attachPreparedQueueSegmentsIfCurrent(
                            player = dualPlayerEngine.masterPlayer,
                            startSongId = effectiveStartSong.id,
                            preparedSegments = preparedSegments
                        )
                    }
                }
            }
        }
```

`attachPreparedQueueSegmentsIfCurrent` (`PlayerViewModel.kt:3474-3503`) already guards itself: it returns unless the player holds exactly one item whose `mediaId` is `startSongId`; after attaching it sets `currentMediaItemIndex` and refreshes the queue shown in the UI from the player.

Helpers that exist and that you will call (all members of `PlayerViewModel`):

- `private fun Song.requiresHydration(): Boolean` (`:2253`)
- `private fun isLocalPlaybackSong(song: Song): Boolean` (`:3437`)
- `fun String.isYouTubeMediaId()` (`utils/YouTubePlaybackUtils.kt:7`, already imported)
- `MediaItemBuilder.build(song: Song): MediaItem` (`utils/MediaItemBuilder.kt:100`, not `suspend`, already imported)
- `private fun incrementSongScore(song: Song)` (`:1626`)
- `private suspend fun reconcileRepeatModeForQueueChange(newQueueName: String, previousQueueName: String)` (`:2642`)
- `private fun throwIfFullQueuePlaybackRequestIsStale(requestToken: Long)` (`:853`)
- `fun resetPredictiveBackState()` (`:1642`)
- `private var pendingQueueSegmentsJob: Job?` (`:688`)

Three facts that shape the design. Do not skip them.

1. **Why the handoff cannot simply go through `showAndPlaySong` after an early start.**
   `showAndPlaySong` (`:2111-2194`) calls `applyImmediatePlaybackUi` again, which pauses the song that just started.
   It then asks `resolveReusablePlaybackTargetIndex`, which answers "reuse index 0" whenever the player's current item is the tapped song; with a one-item player that plays the single song and never attaches the rest of the queue.
   Otherwise it calls `internalPlaySongs`, whose `setMediaItem(startMediaItem, 0L)` restarts the song from zero.
   So the early-start handoff needs its own branch, and its fallback must go through `playSongs`, which never takes the reuse shortcut.
2. **Why the early start uses the engine player directly and skips the controller half of the hush.**
   In Media3 1.10.1 a `MediaController` command is not executed in the caller's stack.
   `MediaControllerImplBase.pause()` hands the command to the session, which only enqueues it (`ConnectedControllersManager.addToCommandQueue`); the queue is flushed by a handler message that the source documents as "handled when the current looper iteration is over".
   `dualPlayerEngine.masterPlayer.play()` runs immediately.
   So "controller pause, then engine play" inside one main-thread turn means the pause is executed after the play.
   The existing `internalPlaySongs` has that same sequence, and the advisor could not tell from code alone whether it is harmless there; this plan does not touch it.
   The early-start path avoids the question: it pauses through `dualPlayerEngine.hushImmediateAudio()` only and starts through `dualPlayerEngine.masterPlayer`, which is the combination `YouTubePlaybackViewModel` already uses (`YouTubePlaybackViewModel.kt:335`, `:423`, `:461`).
3. **Why the early start is limited to Library and to shuffle off.**
   The Library queue is built from `getSongIdsSorted`, which returns local song ids only.
   Liked Songs can contain YouTube items; its handoff goes through `YouTubePlaybackController.playMixedPlaylist`, which hushes and restarts the start item, so an early start there would be heard twice.
   With shuffle on, the handoff may reshuffle through `playSongs`, which also restarts the start item.
4. **Why a tap on the song that is already loaded is excluded.**
   Today that tap goes through the reuse branch of `showAndPlaySong` and resumes the song where it is.
   An early start would restart it from zero, which is a behavior change this plan does not want.

Conventions: private helpers are `private fun` / `private suspend fun` with a short English comment that says why. Log with `Timber`. Kotlin code style is `official`.

## Commands you will need

Commands come from `CONTRIBUTING.md`. The advisor did not run them (no JDK 21 or Android SDK on the advisor's machine).
Point `JAVA_HOME` at a JDK 21 on your machine; on Windows use `gradlew.bat`.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| ViewModel unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Run the ViewModel unit tests once **before** your first edit and keep the result: they are the baseline this change must not break.
Do not start an emulator or drive UI with `adb` unless the operator asks.

## Scope

**In scope** (the only file you modify):

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`: the functions named in the steps below, and nothing else in the file.

**Out of scope** (do not touch):

- `showAndPlaySongFromFavorites` and the Liked Songs path. See fact 3.
- `showAndPlaySong`, `internalPlaySongs`, `internalPlayMixedSongs`, `playSongs`, `attachPreparedQueueSegmentsIfCurrent`, `preparePlaybackQueueSegments`: call them, do not edit them.
- `YouTubePlaybackViewModel.kt`, `DualPlayerEngine.kt`, `MusicService.kt`, `PlaybackStateHolder.kt`.
- The queue provider lambda inside `showAndPlaySongFromLibrary` (what the queue contains stays the same).
- The callers of `showAndPlaySongFromLibrary` in `LibrarySongsTab.kt`, `LibrarySongsAndFavoritesTabs.kt`, `HomeScreen.kt`.

## Git workflow

- Branch: `perf/015-library-tap-starts-audio-before-queue-build`
- One commit per step that compiles, or one commit for the whole plan. Conventional Commits, matching history such as `perf(youtube): defer history writes and attach skip neighbors first`.
  Suggested: `perf(player): start a library tap before the full queue is built`
- No `Co-Authored-By:` trailer and no tool attribution in the message (`CONTRIBUTING.md`).
- Do not push or open a PR unless the operator instructed it.

## Steps

### Step 1: Let the hush skip the controller

Give `hushCurrentAudioForTrackSwitch` and `applyImmediatePlaybackUi` a parameter that defaults to today's behavior, so every existing caller is unchanged.

```kotlin
    private fun hushCurrentAudioForTrackSwitch(viaController: Boolean = true) {
        if (viaController) {
            mediaController?.playWhenReady = false
            mediaController?.pause()
        }
        runCatching { dualPlayerEngine.hushImmediateAudio() }
    }

    private fun applyImmediatePlaybackUi(
        song: Song,
        queueSongs: List<Song>,
        queueName: String,
        mediaItemIndex: Int = queueSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
        hushViaController: Boolean = true,
    ) {
        // (existing comment stays)
        hushCurrentAudioForTrackSwitch(viaController = hushViaController)
```

Change nothing else in `applyImmediatePlaybackUi`.

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Add the early-start helpers

Add these three private functions directly above `launchLatestFullQueuePlayback`:

```kotlin
    // A tapped local song can start alone, before its queue exists, only when the handoff
    // is guaranteed to attach around it: shuffle off and a plain local item. A tap on the
    // song that is already loaded keeps the resume-in-place path.
    private fun canStartTappedSongAlone(song: Song): Boolean =
        mediaController != null &&
            !playbackStateHolder.stablePlayerState.value.isShuffleEnabled &&
            !song.id.isYouTubeMediaId() &&
            !song.requiresHydration() &&
            isLocalPlaybackSong(song) &&
            dualPlayerEngine.masterPlayer.currentMediaItem?.mediaId != song.id

    // Engine player only: a MediaController command is queued by the session and would
    // land after this play().
    private fun startTappedSongAlone(song: Song) {
        dualPlayerEngine.cancelNext()
        val enginePlayer = dualPlayerEngine.masterPlayer
        enginePlayer.setMediaItem(MediaItemBuilder.build(song), 0L)
        enginePlayer.prepare()
        enginePlayer.play()
    }

    private fun canAttachQueueAroundEarlyStart(song: Song, fullQueue: List<Song>): Boolean {
        val enginePlayer = dualPlayerEngine.masterPlayer
        return enginePlayer.mediaItemCount == 1 &&
            enginePlayer.currentMediaItem?.mediaId == song.id &&
            fullQueue.any { it.id == song.id } &&
            fullQueue.none { it.id.isYouTubeMediaId() || it.requiresHydration() }
    }
```

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL` (unused-function warnings are expected until Step 4)

### Step 3: Add the attach-only handoff

Add this private function directly below `handOffFullQueuePlayback`.
It mirrors what `showAndPlaySong` + `internalPlaySongs` do for a local queue, minus the hush and minus `setMediaItem`/`prepare`/`play`:

```kotlin
    // The tapped song is already playing alone: attach the rest of the queue around it
    // instead of restarting it.
    private suspend fun attachFullQueueAroundEarlyStart(
        song: Song,
        fullQueue: List<Song>,
        queueName: String,
        isVoluntaryPlay: Boolean,
        requestToken: Long,
    ) {
        if (isVoluntaryPlay) incrementSongScore(song)
        // Same value showAndPlaySong reads at this point of the existing path.
        val previousQueueName = _playerUiState.value.currentQueueSourceName
        reconcileRepeatModeForQueueChange(queueName, previousQueueName)
        throwIfFullQueuePlaybackRequestIsStale(requestToken)
        playbackStateHolder.clearUserQueueTail()

        pendingQueueSegmentsJob?.cancel()
        pendingQueueSegmentsJob = viewModelScope.launch {
            val preparedSegments = preparePlaybackQueueSegments(
                songsToPlay = fullQueue,
                startSongId = song.id,
                playlistId = null
            )
            withContext(Dispatchers.Main.immediate) {
                attachPreparedQueueSegmentsIfCurrent(
                    player = dualPlayerEngine.masterPlayer,
                    startSongId = song.id,
                    preparedSegments = preparedSegments
                )
            }
        }
        resetPredictiveBackState()
    }
```

Do not call `applyImmediatePlaybackUi` here: `attachPreparedQueueSegmentsIfCurrent` already updates the index and the queue shown in the UI once the items are attached.

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 4: Wire `launchLatestFullQueuePlayback` and `handOffFullQueuePlayback`

`launchLatestFullQueuePlayback`: add the parameter `allowEarlyStart: Boolean = false` (before `queueProvider`, so the trailing lambda still works), decide the early start **before** `applyImmediatePlaybackUi` runs, and start early when allowed.
Target shape of the changed part:

```kotlin
    private fun launchLatestFullQueuePlayback(
        song: Song,
        queueName: String,
        isVoluntaryPlay: Boolean,
        failureMessage: String,
        allowEarlyStart: Boolean = false,
        queueProvider: suspend () -> List<Song>
    ) {
        cancelPendingFullQueuePlayback()
        cancelPendingDirectPlayback()
        val earlyStarted = allowEarlyStart && canStartTappedSongAlone(song)
        applyImmediatePlaybackUi(
            song = song,
            queueSongs = listOf(song),
            queueName = queueName,
            hushViaController = !earlyStarted,
        )
        if (earlyStarted) startTappedSongAlone(song)
        val requestToken = fullQueuePlaybackToken
```

Pass `earlyStarted` and `requestToken` to **both** `handOffFullQueuePlayback` calls in that function (the normal one and the one in the `catch (error: Exception)` fallback).

`handOffFullQueuePlayback`: make it `private suspend fun`, add the two parameters, and put two branches in front of the existing `showAndPlaySong` call:

```kotlin
    private suspend fun handOffFullQueuePlayback(
        song: Song,
        fullQueue: List<Song>,
        queueName: String,
        isVoluntaryPlay: Boolean,
        earlyStarted: Boolean,
        requestToken: Long,
    ) {
        if (earlyStarted && canAttachQueueAroundEarlyStart(song, fullQueue)) {
            attachFullQueueAroundEarlyStart(
                song = song,
                fullQueue = fullQueue,
                queueName = queueName,
                isVoluntaryPlay = isVoluntaryPlay,
                requestToken = requestToken,
            )
            return
        }
        // Clear the job ref before playSongs() cancels pending full-queue work;
        // otherwise this coroutine cancels itself and playback never starts.
        fullQueuePlaybackJob = null
        if (earlyStarted) {
            // The player holds only the early-started item, so showAndPlaySong would reuse
            // it and never load the queue. playSongs rebuilds the queue unconditionally.
            if (isVoluntaryPlay) incrementSongScore(song)
            playSongs(
                songsToPlay = fullQueue.takeIf { songs -> songs.any { it.id == song.id } } ?: listOf(song),
                startSong = song,
                queueName = queueName,
            )
            resetPredictiveBackState()
            return
        }
        showAndPlaySong(
            song = song,
            contextSongs = fullQueue.ifEmpty { listOf(song) },
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            cancelPendingQueueBuild = false
        )
    }
```

The existing comment and `fullQueuePlaybackJob = null` stay, moved below the first branch; the `showAndPlaySong(...)` call is unchanged.
On the attach branch the job must stay referenced so a newer tap can cancel it, which is why the first branch returns before the job ref is cleared.
The `earlyStarted` fallback restarts the song once (audible as a restart); it is reached only when the built queue cannot be attached, for example when it holds a song that needs hydration.

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 5: Enable it for the Library entry point only

In `showAndPlaySongFromLibrary`, add `allowEarlyStart = true` to the `launchLatestFullQueuePlayback` call.
Leave `showAndPlaySongFromFavorites` without the argument.

**Verify**: `grep -n "allowEarlyStart = true" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` → exactly 1 match, inside `showAndPlaySongFromLibrary`
**Verify**: `grep -c "startTappedSongAlone(song)" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` → `2` (1 declaration, 1 call)

### Step 6: Full gate

**Verify**: `./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` → `BUILD SUCCESSFUL`, and the same tests pass as in the baseline run.

## Test plan

- No new automated test.
  The effect is the time between a tap and the first audio, which has no deterministic end-to-end reproduction without a device, and the owner's standard is end-to-end tests only.
- Existing suites must stay green, compared against the baseline you recorded: `com.yuukifst.orpheus.presentation.viewmodel.*` (includes `PlayerViewModelTest`, `PlayerViewModelHydrationTest`, `PlaybackStateHolderTest`).
  A test that was green in the baseline and is red after your change means your change broke it: fix the code, do not edit the test.
- Manual check for the owner on a device (list it in your report, do not run it yourself):
  1. Library tab, shuffle off, large library: tap a song. It starts at once and keeps playing without a restart or a pause blip.
  2. A moment later open the queue: the full library is there, the tapped song is highlighted at its real position, next and previous work.
  3. Tap a second song quickly after the first: only the second plays; the queue belongs to the second tap.
  4. Shuffle on: tapping a Library song behaves exactly as before this plan.
  5. Liked Songs tab: behaves exactly as before this plan.
  6. Home "Your Mix": same as check 1.
  7. Tap the Library song that is already playing: it behaves exactly as before this plan (no restart added by this plan).

## Done criteria

- [ ] `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.*"` → `BUILD SUCCESSFUL`, no test that passed in the baseline fails
- [ ] `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL`
- [ ] Step 5 greps return the expected counts
- [ ] `git diff b8d4cc1 --stat -- app/src/main/java` lists `PlayerViewModel.kt` only for this plan's commit(s)
- [ ] `showAndPlaySong`, `internalPlaySongs` and `attachPreparedQueueSegmentsIfCurrent` bodies are unchanged in the diff
- [ ] `plans/README.md` row 015 status is `DONE`

## STOP conditions

Stop and report back if:

- `launchLatestFullQueuePlayback`, `handOffFullQueuePlayback`, `hushCurrentAudioForTrackSwitch` or the `internalPlaySongs` excerpt do not match the live code (drift beyond the plan 013 addition).
- `attachPreparedQueueSegmentsIfCurrent` no longer returns early when the player is not on the single start item; the attach-only handoff depends on that guard.
- `musicRepository.getSongIdsSorted` can now return ids of non-local items (YouTube, remote sources); then fact 3 is false and the Library path is not safe for an early start.
- A ViewModel test that passed in the baseline fails and the cause is the intended behavior change (for example a test asserting that no player call happens before the queue provider returns). Report the test name and the assertion; do not rewrite it.
- A verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Unverified by the advisor: the claim in fact 2 comes from reading the Media3 1.10.1 source, not from a device run. If the owner's manual check 1 shows the song starting and then pausing by itself, a queued controller command is still reaching the session after the early start; report it instead of adding delays.
- Existing behavior, kept on purpose: on the Library path `previousQueueName` equals the new queue name, because the tap-time UI update has already written it when `showAndPlaySong` reads it (`PlayerViewModel.kt:2129`). `reconcileRepeatModeForQueueChange` is therefore a no-op when leaving Liked Songs for Library. The attach branch reads the same value so that nothing changes; whether that is a bug is a question for the owner, not for this plan.
- `fun playSongs(songsToPlay, startSong, queueName, playlistId)` (`PlayerViewModel.kt:3177`) is the fallback after an early start; it hushes, hydrates and restarts the song. If its signature changed, treat it as drift.
- The early start replaces the player's playlist with one item on every Library tap, then re-attaches the rest. For libraries over 500 songs the code already did that on every tap (the reuse check fails when the UI queue has not been published yet); for smaller libraries this adds one rebuild of at most 500 items.
- Deferred: the same early start for Liked Songs. It needs `YouTubePlaybackController.startPlayback` to skip its `setMediaItem` when the start item is already playing, which is a change to the mixed-queue path.
- Reviewer: check that no path reaches `attachFullQueueAroundEarlyStart` without `startTappedSongAlone` having run in the same tap, and that `fullQueuePlaybackJob` is still nulled on the non-early path.
