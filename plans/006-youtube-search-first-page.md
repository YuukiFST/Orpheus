# Plan 006: Paint YouTube search after the first result page

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat 6e75b37..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModelHistoryTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `6e75b37`, 2026-08-28

## Why this matters

YouTube Search UI stays in `isLoading` until NewPipe finishes **up to three**
search pages (`MAX_SEARCH_PAGES = 3`). The first page already has enough rows
to scroll. Extra `SearchInfo.getMoreItems` round trips delay first paint and
delay `prefetchTopResult` (plan 005). Emitting page 1 immediately, then
appending pages 2–3, cuts time-to-first-row without shrinking the eventual
list.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
  — `search()` waits on `performSearch`, which caches **only after** the page loop.
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
  — `executeSearch` awaits `searchRepository.search(trimmed)` then updates UI
  and calls `prefetchTopResult`.
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt`
  — cache key / hit tests via `createForTests()` + `search()`.
- `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModelHistoryTest.kt`
  — mocks `searchRepository.search(any())` as a **suspend** returning a list.

Page loop (do not delete the extra pages; emit earlier):

```69:97:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt
    private fun performSearch(trimmedQuery: String, cacheKey: String): List<YouTubeTrack> {
        youTubeInitializer.ensureInitialized()
        return youTubeDownloader.runAsSearch {
            // ...
            consume(searchInfo.relatedItems)
            var nextPage = searchInfo.nextPage
            var pagesFetched = 1
            while (nextPage != null && pagesFetched < MAX_SEARCH_PAGES) {
                // getMoreItems ...
            }
            searchCache.put(cacheKey, results)
            results
        }
    }
```

`search()` today is a single `Deferred<List<YouTubeTrack>>` coalesced in
`inFlightSearches`. Keep coalescing for the **final** list. Page-1 paint is
only required for the coroutine that **starts** the search (a second waiter
for the same key may `await` the full list; duplicate typing of the same
query is rare).

Conventions: Kotlin official style; JUnit Jupiter (`org.junit.jupiter.api.Test`);
Timber for logs; do not wrap `SmartImage` in `key()`; cancel NewPipe/OkHttp
`Call` via existing `cancelActiveRequest()`, not Job-only.

`CLAUDE.md`: image target sizes stay constants from `SmartImage.kt`. This plan
does not touch images.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Unit tests (search) | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeSearchRepositoryCacheTest" --tests "com.yuukifst.orpheus.data.youtube.YouTubeSearchMergeTest" --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeSearchViewModelHistoryTest"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do **not** start the emulator or drive UI with `adb` unless the operator asks.

## Suggested executor toolkit

- If available: `verification-before-completion` before claiming DONE.
- Do not start `agent-browser` / emulator for this plan.

## Scope

**In scope**:

- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchMergeTest.kt` (create)
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt` (only if `search()` signature change requires it)
- `app/src/test/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModelHistoryTest.kt` (mocks if `search` stays suspend wrapping progressive collect)

**Out of scope**:

- Replacing NewPipe with Innertube.
- Changing `MAX_SEARCH_PAGES` to 1 (that shrinks the list; this plan is first-paint).
- `YouTubeDownloaderImpl` slot/cancel policy (already done in plan 002).
- Local library search (`MusicRepositoryImpl.searchAll`).
- `plans/005-*` prefetch logic except calling `prefetchTopResult` on the **first**
  non-empty emission (same function, earlier).

## Git workflow

- Branch: `advisor/006-youtube-search-first-page`
- Commits: conventional, e.g. `fix(youtube): emit search results after first NewPipe page`
- Do **not** push or open a PR unless the operator asked.

## Steps

### Step 1: Extract pure merge helper

In `YouTubeSearchRepository.kt` (or a small `YouTubeSearchMerge.kt` in the same
package if the repository file is already hard to navigate), add:

```kotlin
internal fun mergeYouTubeSearchTracks(
    existing: List<YouTubeTrack>,
    incoming: List<YouTubeTrack>,
): List<YouTubeTrack> {
    val seen = existing.map { it.videoId }.toMutableSet()
    val out = existing.toMutableList()
    for (track in incoming) {
        if (seen.add(track.videoId)) out.add(track)
    }
    return out
}
```

Use this inside `consume` / page appends so page 2–3 cannot duplicate page 1
ids (same `linkedSetOf` behavior as today).

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Unit-test the helper

Create `YouTubeSearchMergeTest.kt` (JUnit Jupiter). Cases:

- empty existing + two tracks → both, order preserved
- existing already has `videoId` → incoming duplicate dropped
- incoming empty → existing unchanged

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeSearchMergeTest"`
→ tests pass

### Step 3: Progressive search API

Keep `suspend fun search(query: String): List<YouTubeTrack>` as
`searchProgressive(query).last()` (or collect last) so
`YouTubeSearchRepositoryCacheTest` and any other `search()` callers still get
the **full** 1–3 page list.

Add `fun searchProgressive(query: String): Flow<List<YouTubeTrack>>` that:

1. Same cache key / blank query / `searchCache.get` short-circuit as `search()`
   (cache hit: **one** emission of the cached list, then complete).
2. Same in-flight `Deferred` for the **complete** list (second waiter awaits
   full result; do not start a second NewPipe search).
3. Inside `performSearch` / `runAsSearch`, after `consume(searchInfo.relatedItems)`
   and `pagesFetched = 1`:
   - `searchCache.put(cacheKey, snapshotOfPage1)`
   - emit that snapshot to the Flow (use `callbackFlow` / `channelFlow` /
     `MutableSharedFlow` replay 0 extraBuffer — pick one and collect it from
     `searchProgressive` on `Dispatchers.IO`).
4. Continue the existing `while (nextPage != null && pagesFetched < MAX_SEARCH_PAGES)`
   loop; after the loop `searchCache.put` the full list and emit full list.
5. If `nextPage == null` after page 1, emit once (page 1 == full); do not
   double-emit identical lists (`distinctUntilChanged` on the Flow is OK).

If wiring a Flow through blocking `runAsSearch` is awkward, an
`onFirstPage: (List<YouTubeTrack>) -> Unit` callback invoked from `performSearch`
**before** the while-loop is acceptable. `searchProgressive` must still be a
`Flow` the ViewModel can `collect`.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 4: ViewModel collects progressive results

In `YouTubeSearchViewModel.executeSearch`:

- Cache-only path unchanged (`searchCachedOnly` then return).
- Network path: `collect` `searchProgressive(trimmed)` (or collect callback
  emissions) with the existing `requestId != latestSearchRequestId.get()` guard
  on **every** emission.
- First non-empty emission: `isLoading = false`, set `results`,
  `prefetchTopResult(results)` **once** per requestId (remember a
  `Boolean` / videoId so page-2 emission does not cancel/restart prefetch of
  the same first `videoId`).
- Later emissions: update `results` only (list grows); do not flip
  `isLoading` back to true.
- `saveHistory` still runs once after the Flow **completes** successfully
  (same as today after full search), not on page 1, so debounce typing does
  not persist more often.
- Cancellation / `cancelActiveRequest` when `activeNetworkQuery` changes:
  keep current behavior.

Update `YouTubeSearchViewModelHistoryTest` mocks: if the ViewModel still calls
`search()`, keep `coEvery { searchRepository.search(...) }`. If it calls
`searchProgressive`, mock a `flow { emit(listOf(sampleTrack)) }` (or two
emits). Existing tests must still pass: debounce does not insert history;
explicit `search()` does.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.presentation.viewmodel.YouTubeSearchViewModelHistoryTest" --tests "com.yuukifst.orpheus.data.youtube.YouTubeSearchRepositoryCacheTest"`
→ pass

### Step 5: Lint + compile

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin :app:lintDebug`
→ `BUILD SUCCESSFUL`

## Test plan

- New: `YouTubeSearchMergeTest` (step 2).
- Existing: `YouTubeSearchRepositoryCacheTest` — `search()` still returns the
  **complete** cached list after a full `performSearch` (seed + get).
- Existing: `YouTubeSearchViewModelHistoryTest` — history rules unchanged.
- Do not add a live NewPipe integration test.

## Done criteria

- [ ] `search()` / cache tests still pass; first UI update no longer requires
      pages 2–3 (code: emit/callback after first `consume(relatedItems)`).
- [ ] `MAX_SEARCH_PAGES` still 3; extra pages still fetched.
- [ ] `prefetchTopResult` runs on first non-empty page-1 list, not only after
      page 3.
- [ ] `grep -n "searchCache.put" YouTubeSearchRepository.kt` shows a put
      after page 1 **and** after the loop (or equivalent overwrite).
- [ ] No files outside in-scope list (`git status`).
- [ ] `plans/README.md` row 006 status `DONE`.

## STOP conditions

- `performSearch` no longer uses `SearchInfo.getInfo` / `getMoreItems` (API
  drift).
- Making page-1 emit requires changing `YouTubeDownloaderImpl` global cancel
  in a way that kills the same search's page-2 request — report instead of
  inventing a second downloader.
- ViewModel tests cannot be updated without rewriting the whole ViewModel.

## Maintenance notes

- Reviewer: list must **grow** (or stay equal), never shrink, when pages 2–3
  arrive; `videoId` dedupe.
- Future next-result prefetch (not this plan) should key off the visible
  first row after page 1, not wait for page 3.
- If YouTube starts returning an empty first page with a valid `nextPage`,
  still emit empty then fill — do not skip emit.
