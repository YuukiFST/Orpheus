# Implementation Plans — Performance / Perceived Latency

Generated 2026-07-26 against commit `85bcada` by an advisory audit of the four
areas the maintainer prioritized:

1. Mini player (player navbar) must appear the instant a YouTube search result is tapped.
2. YouTube Search results must appear faster.
3. App must open faster.
4. Buttons must respond faster to touch.

> Nota (PT-BR): os planos estão em inglês porque são consumidos por agentes
> executores e o código/comentários do repositório são em inglês. Cada plano é
> autocontido: o executor não precisa ler este README para executar, mas deve
> atualizar a linha de status aqui ao terminar.

Execute in the order below unless the dependency notes say otherwise. Each
executor: read the plan fully before starting, honor its STOP conditions, and
update your row when done.

## Execution order & status

| Plan | Title | Priority | Effort | Risk | Depends on | Status |
|------|-------|----------|--------|------|------------|--------|
| 001 | Publish the mini player optimistically on YouTube search tap | P1 | M | MED | — | DONE |
| 002 | Cut YouTube Search latency (debounce, suggestion cache, HTTP call slot) | P1 | M | MED | — | DONE |
| 003 | Make taps feel instant (press feedback + optimistic toggles) | P1 | M | MED | — | TODO |
| 004 | Trim cold start (defer non-first-frame work) | P2 | M | MED | — | TODO |
| 005 | Prefetch the stream URL of the first search result | P3 | S | MED | 001, 002 | TODO |

## Dependency notes

- **005 requires 001 and 002.** Prefetching a stream URL issues extra NewPipe
  HTTP work. Today `YouTubeDownloaderImpl` keeps a single `activeCall` and
  cancels the previous one on every new request (`YouTubeDownloaderImpl.kt:38-42`),
  so a prefetch would cancel an in-flight search. Plan 002 removes that
  global cancel; plan 001 makes the win measurable.
- 001, 002, 003 and 004 touch disjoint files and can be executed in parallel by
  different agents. 003 and 004 both touch `PlayerViewModel.kt`, but different
  functions — if run in parallel, expect a merge conflict window there.

## What was NOT audited

- Local-library scroll/jank paths, `ArtistDetailScreen` / `GenreDetailScreen`
  animations, Room query shape, memory trimming. Those are covered by the older
  document [app/performance_analysis.md](../app/performance_analysis.md); several
  of its findings have since been fixed (state slices exist, `EnhancedSongListItem`
  now uses one shared `updateTransition`, `onTrimMemory` is implemented in
  `OrpheusApplication.kt:133-166`). Treat that file as historical, not current.
- Navidrome / Jellyfin remote sources.
- On-device measurement. Per `CLAUDE.md`, no emulator or `adb` UI driving was
  performed; every plan's verification gate is compile / lint / unit test only.

## Findings considered and rejected

- **"Baseline profiles are pending"** (claimed in `CLAUDE.md`): stale.
  `app/src/release/generated/baselineProfiles/baseline-prof.txt` has 40,082 lines
  and `startup-prof.txt` 35,016 lines. Nothing to do beyond fixing the CLAUDE.md
  sentence (covered as a one-line step in plan 004).
- **Migrating `allSongs` to full Paging3**: high risk, touches genres, daily mix,
  stats and AI playlist generation. Not worth doing for the four priorities in
  this round.
- **Adding `contentType` to the YouTube results `LazyColumn`**: the list has a
  single item type, so `contentType` would not change reuse behavior.
- **Replacing NewPipe with a direct Innertube client**: would plausibly cut
  search latency further, but it is a data-layer rewrite with anti-bot/captcha
  risk. Out of scope; revisit only if plan 002 proves insufficient.
- **Removing the 250 ms sheet-open animation** to make the mini player appear
  faster: rejected. The animation runs *after* the state flips; killing it would
  cost polish without fixing the actual delay (plan 001 does).
