# Implementation Plans — Performance / Perceived Latency

Round 1 generated 2026-07-26 against commit `85bcada` (plans 001–005). Round 2
generated 2026-08-28 against commit `6e75b37` (plans 006–010) by a
performance-only advisory audit: cold start, tab/search latency, time-to-first
audio, skip/next.
Round 3 generated 2026-11-25 against commit `b8d4cc1` (plans 011–015) by a performance-only advisory audit: time from tap to first audio, next/previous, how fast audio stops when the user closes playback, general responsiveness.

> Nota (PT-BR): os planos estão em inglês porque são consumidos por agentes
> executores e o código/comentários do repositório são em inglês. Cada plano é
> autocontido: o executor não precisa ler este README para executar, mas deve
> atualizar a linha de status aqui ao terminar.

Default selection for round 2 (operator accepted advisor recommendation):
findings **#1, #2, #3, #4, #6** → plans **006–010**. Do not write plans for
the rest of the 2026-08-28 table unless asked.

Selection for round 3 (operator accepted advisor recommendation): findings **#1, #2, #3, #4, #5** → plans **011, 012, 013, 015, 014**.
Do not write plans for the rest of the 2026-11-25 table unless asked.

Execute **TODO** plans in the **Recommended execution order** below. Each
executor: read the plan fully before starting, honor its STOP conditions, and
update your row when done.

## Execution order & status

| Plan | Title | Priority | Effort | Risk | Depends on | Status |
|------|-------|----------|--------|------|------------|--------|
| 001 | Publish the mini player optimistically on YouTube search tap | P1 | M | MED | — | DONE |
| 002 | Cut YouTube Search latency (debounce, suggestion cache, HTTP call slot) | P1 | M | MED | — | DONE |
| 003 | Make taps feel instant (press feedback + optimistic toggles) | P1 | M | MED | — | DONE |
| 004 | Trim cold start (defer non-first-frame work) | P2 | M | MED | — | DONE |
| 005 | Prefetch the stream URL of the first search result | P3 | S | MED | 001, 002 | DONE |
| 006 | Paint YouTube search after first NewPipe page | P1 | M | LOW | — | DONE |
| 007 | Fill YouTube skip queue incrementally (next/prev first) | P1 | L | MED | 009 | DONE |
| 008 | Defer `recordPlayed` off the YouTube playlist start path | P1 | S | LOW | — | DONE |
| 009 | Share in-flight YouTube stream extracts (prefetch + tap) | P1 | S | LOW | — | DONE |
| 010 | Honor 6h interval on foreground library catch-up sync | P2 | S | LOW | — | DONE |
| 011 | Silence audio before the unload snapshot write (Recents swipe) | P1 | S | LOW | — | DONE |
| 012 | Start playback after 1 s of buffer, not 2.5 s (Media3 defaults) | P1 | S | LOW | — | DONE |
| 013 | Cut audio when the mini-player dismiss swipe commits | P1 | S | LOW | — | DONE |
| 014 | Preload the next playlist item on the master player | P1 | S | MED | 012 | DONE |
| 015 | A Library tap starts audio before the full queue is built | P2 | M | MED | 013 | TODO |

## Recommended execution order (round 3)

**011 → 012 → 013 → 014 → 015**

- **011, 012, 013 first:** each is a few lines in one place, LOW risk, and each removes a wait the user hears on every use.
- **014 after 012:** both edit `DualPlayerEngine.kt`; sequential avoids a merge conflict.
- **015 last:** the only M-sized plan, MED risk, and it edits `PlayerViewModel.kt` after 013 has landed there.
- One plan per branch and per commit, so the owner can revert a single plan if a device check fails.

## Recommended execution order (round 2)

**008 → 009 → 006 → 007 → 010**

- **008 first:** smallest, disjoint files, unblocks playlist time-to-first-audio
  without touching extract/search.
- **009 before 007:** incremental `resolveMixedEntry` must share in-flight
  extracts with prefetch; otherwise skip fill duplicates HTTP.
- **006** independent of 007/008; can run in parallel with 008 or 010 after 009
  if two agents, but 006 and 009 both touch YouTube HTTP — prefer 009 first if
  sequential.
- **010 last:** cold-start I/O, no YouTube merge conflict.

## Dependency notes

- **005 requires 001 and 002** (historical). Prefetching a stream URL issues
  extra NewPipe HTTP work. Plan 002 removed global cancel of the previous
  downloader call; plan 001 made the win measurable.
- **007 requires 009.** `fillQueueAroundCurrent` today waits for every track's
  `StreamInfo.getInfo` before attach. Incremental fill issues many extracts;
  without 009, prefetch + skip collide.
- **006, 008, 010** have no plan dependencies. 006 and 009 both live under
  `data/youtube/` — sequential if one agent.
- 001–004 were parallelizable; 003 and 004 both touched `PlayerViewModel.kt`.
- **014 requires 012** only because both edit `DualPlayerEngine.kt` (012: `buildAdaptiveLoadControl` and the companion object; 014: the companion object and three call sites).
- **015 requires 013** only because both edit `PlayerViewModel.kt` (013 adds one function near `setMiniPlayerDismissing`; 015 changes the full-queue playback functions).
- **011** touches `MusicService.kt` only and has no plan dependency.

## What was NOT audited (round 3, 2026-11-25)

- On-device measurement.
  The advisor's machine has no JDK 21 and no Android SDK, so no build, lint or test was run and no latency was measured; every gain in plans 011–015 is derived from reading code and the Media3 1.10.1 source.
- Each of plans 011–015 lists manual device checks for the owner; they are the only validation of the audible result.
- Cold start, search, Navidrome / Jellyfin sources, correctness, security, test coverage.
- Unverified observation, not planned: `internalPlaySongs` calls `mediaController.pause()` and `dualPlayerEngine.masterPlayer.play()` in the same main-thread turn.
  By the Media3 1.10.1 source the controller command is flushed after that turn.
  The advisor could not tell from code whether this is harmless; see plan 015, fact 2.
- Unverified observation, not planned: leaving Liked Songs through a Library tap passes the new queue name as `previousQueueName`, so the saved repeat mode is not restored; see plan 015, maintenance notes.

## What was NOT audited (round 2, 2026-08-28)

- Navidrome / Jellyfin remote sources.
- On-device measurement. Per `CLAUDE.md`, no emulator or `adb` UI driving;
  every plan's verification gate is compile / lint / unit test only.
- Correctness, security, test-coverage, DX, docs (focus was performance only).
- Historical UI jank list in [app/performance_analysis.md](../app/performance_analysis.md)
  — treat as historical. Slices, paging tabs, `updateTransition`, `onTrimMemory`,
  `file_path` index already in tree at `6e75b37`.

## Findings considered and rejected

Round 1 (`85bcada`):

- **"Baseline profiles are pending"** (claimed in `CLAUDE.md`): stale.
  Generated profiles are committed. Nothing to do beyond the one-line CLAUDE.md
  fix in plan 004.
- **Migrating `allSongs` to full Paging3**: high risk; genres, daily mix, stats,
  AI playlist generation.
- **Adding `contentType` to the YouTube results `LazyColumn`**: single item type.
- **Replacing NewPipe with a direct Innertube client**: data-layer rewrite +
  anti-bot risk. Revisit only if 002/006 still insufficient.
- **Removing the 250 ms sheet-open animation**: delay is state, not animation.

Round 2 (`6e75b37`) — logged so they are not re-audited as new work:

- **NewPipe `ensureInitialized` on every cold start** (`OrpheusApplication.kt`):
  real CPU, but local-only users still need extractor if they open Search later;
  lazy-init is a separate product call.
- **`ensureLibrarySortDefaults` always `dataStore.edit`**: small; not in top 5.
- **Duplicate playback-queue snapshot decode** (PlayerViewModel +
  PlaybackStateHolder): S, low user-visible win vs 008/010.
- **Detail/playlist screens collect full `stablePlayerState`**: real recomposition;
  S–M across many screens; deferred this round.
- **`getSongsPaginated` / `getAllSongs` `SELECT *` (lyrics)**: projection exists;
  deferred (Room query pass).
- **Local search ALL = 4–5 queries per keystroke**: deferred.
- **Literal `Size(256,256)` vs `SmartImage` constants**: CLAUDE.md regression;
  S, not in top 5.
- **Artist `animateItem` per song on expand**: visual; MED risk; not in top 5.
- **Folders tab in-memory tree**: L; out of “music starts / skip”.
- **PlayerViewModel Hilt split / NavHost split / baseline regen**: L / needs
  device. Operator did not select them.

Round 3 (`b8d4cc1`), logged so they are not re-audited as new work:

- **Stats persistence blocks the stop path**: rejected. `ListeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)` ignores the parameter (`ListeningStatsTracker.kt:270`).
- **`CoilBitmapLoader` has no cache**: rejected. `MediaSession.Builder` in Media3 1.10.1 always wraps a custom `BitmapLoader` in `CacheBitmapLoader`.
- **Replace the default HTTP data source with `OkHttpDataSource`**: needs a new dependency (`media3-datasource-okhttp`); owner decision, not planned.
- **Reuse the prepared auxiliary player on a manual skip while crossfade is on**: L, rewrites the player swap; deferred.
- **Early start for Liked Songs taps**: deferred; needs a change to the mixed-queue path (see plan 015, maintenance notes).
- **Findings not selected this round** (real, lower leverage for "music starts / skips / stops"):
  full queue snapshot written to the shared `settings` DataStore every 1.5 s / 20 s;
  list stagger of up to 280 ms plus a 250 ms fade on scroll;
  the player sheet root reading per-frame values;
  album color animation recomposing the whole player;
  duplicate per-track work (palette extracted three times, carousel prefetch always on, widget pipeline with no widgets);
  playback history rewritten and fsynced on every track;
  YouTube extract issuing four serial requests;
  ReplayGain not caching "no tag" and its flows lacking `distinctUntilChanged`.

## Round 3 finding map (planned)

| Finding # | Plan |
|-----------|------|
| 1 Recents swipe blocks up to 750 ms before audio stops | 011 |
| 2 `bufferForPlaybackMs` 2 500 vs Media3 default 1 000 | 012 |
| 3 Mini-player swipe stops audio only after the 150 ms animation | 013 |
| 4 Library tap waits for the full queue before audio | 015 |
| 5 Next item never preloaded | 014 |

## Round 2 finding map (planned)

| Finding # | Plan |
|-----------|------|
| 1 Search waits 3 pages | 006 |
| 2 Skip waits for full queue extract | 007 |
| 3 `recordPlayed` before audio | 008 |
| 4 Prefetch + tap no shared extract | 009 |
| 6 Foreground sync ignores 6h | 010 |
