# Plan 007: Fill YouTube skip queue incrementally (next/prev first)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat 6e75b37..HEAD -- app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeMixedQueueAttachTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: `plans/009-share-inflight-stream-extract.md` (land 009 first so
  incremental `resolveMixedEntry` shares in-flight extracts with prefetch)
- **Category**: perf
- **Planned at**: commit `6e75b37`, 2026-08-28

## Why this matters

After YouTube playlist / mixed Liked playback starts the **current** item,
`fillQueueAroundCurrent` still does `tracks.map { resolveMixedEntry }` — every
track's `StreamInfo.getInfo` — **then** attaches neighbors. Skip/next on the
player or widget has no next `MediaItem` until that map finishes. Thirty
YouTube rows means thirty extracts before skip works.

`planMixedQueueAttach` returns **Skip** when `currentMediaItemCount > 1`. A
one-shot AddAround after the first neighbor is already on the timeline is a
no-op. Incremental fill must add items **as they resolve**, not wait for a
full list then call `planMixedQueueAttach` once.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
  — `startPlayback` prepares one item then launches `fillQueueAroundCurrent`.
  `planMixedQueueAttach` (file-level `internal fun`) used by fill + tests.
- `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeMixedQueueAttachTest.kt`
  — documents Skip when count > 1. **Do not delete** those cases; they stay
  true for the **one-shot** planner. Incremental fill must not depend on
  AddAround after count already > 1.

One-shot planner (keep for mismatch fallback):

```117:143:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
internal fun planMixedQueueAttach(
    currentMediaItemCount: Int,
    currentMediaId: String?,
    resolvedMediaIds: List<String>,
    startIndex: Int,
): MixedQueueAttachPlan {
    if (resolvedMediaIds.isEmpty()) return MixedQueueAttachPlan.Skip
    val safeIndex = startIndex.coerceIn(0, resolvedMediaIds.lastIndex)
    if (currentMediaItemCount <= 0) return MixedQueueAttachPlan.Skip
    if (currentMediaItemCount > 1) return MixedQueueAttachPlan.Skip
    // AddAroundCurrent vs ReplaceAll ...
}
```

Blocking fill (replace this body):

```541:579:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt
    private suspend fun fillQueueAroundCurrent(...) {
        runCatching {
            throwIfPlaybackGenerationStale(expectedGeneration)
            val allItems = withContext(Dispatchers.IO) {
                tracks.map { resolveMixedEntry(it) }
            }
            withContext(Dispatchers.Main.immediate) {
                // planMixedQueueAttach once ...
            }
        }
    }
```

`startPlayback` already `setMediaItem` + `play()` for `tracks[startIndex]` only
(`YouTubePlaybackViewModel.kt` ~522–537). `applyPlayingPlaylistReorder` also
calls `fillQueueAroundCurrent` when ids cannot move in place.

Conventions: JUnit Jupiter; `Dispatchers.IO` for NewPipe; Main for ExoPlayer
mutations; wrap playlist mutations in
`dualPlayerEngine.runWithoutPlaylistChangedSideEffects { }` (same as today);
`throwIfPlaybackGenerationStale` before each resolve **and** before each
player mutate; `CLAUDE.md`: cancel HTTP via downloader, not Job-only — do not
invent a new cancel path here.

Do **not** call `setMediaItems` + `prepare()` mid-playback if add-around /
incremental add keeps the current item playing (comment on
`MixedQueueAttachPlan` already warns ReplaceAll stalls near 00:00).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Attach + fill-order tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeMixedQueueAttachTest"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do **not** start the emulator or drive UI with `adb` unless the operator asks.

## Suggested executor toolkit

- If available: `verification-before-completion` before claiming DONE.

## Scope

**In scope**:

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubePlaybackViewModel.kt`
  (`fillQueueAroundCurrent`, new `internal fun youtubeQueueFillOrder`, maybe a
  tiny attach helper used only by fill)
- `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeMixedQueueAttachTest.kt`
  (add fill-order tests in this file or `YouTubeQueueFillOrderTest.kt` next to it)

**Out of scope**:

- Adjacent-track prefetch in `DualPlayerEngine.triggerAdjacentPreResolution`
  (still a no-op; not this plan).
- Changing `planMixedQueueAttach` Skip-when-count>1 semantics (tests depend on it).
- Local library queue attach / `PlayerViewModel` timeline.
- Rewriting `YouTubePlaybackResolver` or NewPipe.
- Plan 009 extractor mutex (must already be merged).

## Git workflow

- Branch: `advisor/007-youtube-queue-incremental-fill`
- Commits: e.g. `fix(youtube): attach skip neighbors before resolving full queue`
- Do **not** push or open a PR unless the operator asked.

## Steps

### Step 1: Pure fill order

Add `internal fun youtubeQueueFillOrder(trackCount: Int, startIndex: Int): List<Int>`
in `YouTubePlaybackViewModel.kt` (same file as `planMixedQueueAttach`).

Rules when `trackCount <= 1`: empty list.

Otherwise coerce `start` into `0 until trackCount`. Order:

1. `start + 1` if in range (next — skip target)
2. `start - 1` if in range (previous)
3. remaining indices **after** next, ascending (`start+2 .. last`)
4. remaining indices **before** prev, **descending** (`start-2 downTo 0`)

Examples the tests must encode:

- count 5, start 2 → `[3, 1, 4, 0]`
- count 5, start 0 → `[1, 2, 3, 4]`
- count 5, start 4 → `[3, 2, 1, 0]`
- count 1, start 0 → `[]`

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 2: Unit tests for fill order

Add tests (Jupiter) for the four examples above plus `startIndex` out of range
(coerce, do not crash). Keep existing `planMixedQueueAttach` tests unchanged.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeMixedQueueAttachTest"`
(and `--tests "...YouTubeQueueFillOrderTest"` if split) → pass

### Step 3: Incremental fill implementation

Rewrite `fillQueueAroundCurrent` **without** a full-list `tracks.map` on the
happy path:

1. `throwIfPlaybackGenerationStale`
2. For each `index` in `youtubeQueueFillOrder(tracks.size, startIndex)`:
   - stale check
   - `val item = withContext(Dispatchers.IO) { resolveMixedEntry(tracks[index]) }`
   - stale check
   - `withContext(Dispatchers.Main.immediate)`:
     - Read `player = dualPlayerEngine.masterPlayer`
     - If `player.currentMediaItem?.mediaId` is not
       `tracks[startIndex].playbackMediaId()` (same helper already used in
       reorder): **STOP the incremental loop**. Fallback: resolve **remaining
       unresolved** tracks (or all tracks if simpler and still cheaper than
       today only when mismatch is rare) then `planMixedQueueAttach`. If plan
       is `ReplaceAll`, existing ReplaceAll branch is allowed. If `Skip` with
       count>1, do not no-op silently — continue incremental add if current id
       still matches; only ReplaceAll when the playing item is wrong.
     - Else attach **this one item**:
       - If `index > startIndex`: `addMediaItem(player.mediaItemCount, item)`
         (append after current window)
       - If `index < startIndex`: `addMediaItem(0, item)` (prepend; descending
         fill order keeps chronological order — see Why / examples)
     - Always wrap adds in `runWithoutPlaylistChangedSideEffects`
3. Never `setMediaItems` when current id still matches start.

Local tracks in mixed playlists: `resolveMixedEntry` for `PlaylistMixedTrack.Local`
is `MediaItemBuilder.build` (cheap). Same loop is OK.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 4: Reorder path

`applyPlayingPlaylistReorder` already cancels `queueFillJob` and relaunches
`fillQueueAroundCurrent`. No extra API. Confirm the new fill still uses
`expectedGeneration = playGeneration.get()` as today. If reorder rebuilds the
whole timeline via `setMediaItems` elsewhere, do not change that branch.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 5: Lint

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin :app:lintDebug`
→ `BUILD SUCCESSFUL`

## Test plan

- New: `youtubeQueueFillOrder` cases (step 2).
- Existing: `YouTubeMixedQueueAttachTest` one-shot planner cases **must still pass**.
- No instrumented ExoPlayer test (would need emulator). Reviewer: skip/next
  must work after **next** extract, not after last extract.

## Done criteria

- [ ] `grep -n "tracks.map { resolveMixedEntry" YouTubePlaybackViewModel.kt`
      has **no** match inside `fillQueueAroundCurrent` (or equivalent sequential
      full map before first add).
- [ ] Fill order tests pass; attach Skip-when-count>1 tests still pass.
- [ ] Incremental adds use `addMediaItem` / `addMediaItems` of **one** (or
      small) item per resolved neighbor, not one `addMediaItems` of the full
      before/after lists after all HTTP.
- [ ] ReplaceAll only on current-id mismatch (or empty player).
- [ ] No files outside in-scope list.
- [ ] `plans/README.md` row 007 status `DONE`.

## STOP conditions

- `fillQueueAroundCurrent` no longer exists / skip is populated another way.
- Incremental prepend reverses visible queue vs `currentMixedTracks` order —
  fix order using the descending-before rule; if ExoPlayer index vs mixed
  index desyncs `songForMixedIndex`, STOP and report (do not invent a second
  source of truth).
- `runWithoutPlaylistChangedSideEffects` removed.

## Maintenance notes

- Plan 005 / 009: first skip target should hit stream cache / in-flight extract.
- Future: `triggerAdjacentPreResolution` can prefetch `youtubeQueueFillOrder(...)[0]`
  without waiting for this fill to finish.
- Reviewer: widget skip and in-app next must not wait for last playlist row.
