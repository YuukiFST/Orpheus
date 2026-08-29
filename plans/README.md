# Implementation Plans — Performance / Perceived Latency

Round 1 generated 2026-07-26 against commit `85bcada` (plans 001–005). Round 2
generated 2026-08-28 against commit `6e75b37` (plans 006–010) by a
performance-only advisory audit: cold start, tab/search latency, time-to-first
audio, skip/next.

> Nota (PT-BR): os planos estão em inglês porque são consumidos por agentes
> executores e o código/comentários do repositório são em inglês. Cada plano é
> autocontido: o executor não precisa ler este README para executar, mas deve
> atualizar a linha de status aqui ao terminar.

Default selection for round 2 (operator accepted advisor recommendation):
findings **#1, #2, #3, #4, #6** → plans **006–010**. Do not write plans for
the rest of the 2026-08-28 table unless asked.

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

## Round 2 finding map (planned)

| Finding # | Plan |
|-----------|------|
| 1 Search waits 3 pages | 006 |
| 2 Skip waits for full queue extract | 007 |
| 3 `recordPlayed` before audio | 008 |
| 4 Prefetch + tap no shared extract | 009 |
| 6 Foreground sync ignores 6h | 010 |
