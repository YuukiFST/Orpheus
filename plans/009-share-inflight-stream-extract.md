# Plan 009: Share in-flight YouTube stream extracts (prefetch + tap)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat 6e75b37..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamPrefetchTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (land **before** plan 007)
- **Category**: perf
- **Planned at**: commit `6e75b37`, 2026-08-28

## Why this matters

`extractBestAudio` only reuses work after `streamCache` is filled. Prefetch
(search first result, Liked `prefetchStreams`) and a tap that starts before
that extract finishes each call `StreamInfo.getInfo` for the same
`videoId`+quality. Job cancel on the prefetch coroutine does **not** cancel
OkHttp, so the user still pays two extracts. Search already coalesces in-flight
queries (`YouTubeSearchRepository`); stream extract must match that idea.

This plan must land before incremental queue fill (007): 007 issues many
`resolveMixedEntry` calls that also go through this extractor.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt`
  — cache hit, then always `runAsStream { StreamInfo.getInfo(...) }`.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
  — in-flight map + `Mutex` (copy the **intent**, not the caller-scoped `async`).
- Callers (do **not** change unless a signature break forces it):
  `YouTubePlaybackResolver.kt`, `YouTubeSearchViewModel.prefetchTopResult`,
  `YouTubePlaybackController.prefetchStreams`, `YouTubeDownloadRepository`.
- Tests: `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamPrefetchTest.kt`
  — `LruCache` is inert under Robolectric default values; do **not** assert
  cache hits via `seedStreamCacheForTests` in unit tests.

Cache miss path today:

```30:47:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt
    suspend fun extractBestAudio(videoId: String): YouTubeStreamResult = withContext(Dispatchers.IO) {
        val quality = currentQuality()
        val cacheKey = streamCacheKey(videoId, quality)
        val now = System.currentTimeMillis()
        streamCache.get(cacheKey)?.takeIf { it.isValid(now) }?.result?.let { return@withContext it }

        youTubeInitializer.ensureInitialized()
        val info = youTubeDownloader.runAsStream {
            StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        }
        // ... put cache, return result
    }
```

Search in-flight **intent** (do not copy `coroutineScope { async }` onto the
caller — cancelling prefetch would cancel a shared `Deferred` parented to that
scope):

```35:40:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt
        val shared = inFlightMutex.withLock {
            inFlightSearches[key]?.takeIf { it.isActive }
        }
        if (shared != null) {
            return@withContext shared.await()
        }
```

Conventions: `@Singleton` extractor; `streamCacheKey(videoId, quality)` already
exists; `prefetchBestAudio` already calls `extractBestAudio` — sharing there is
enough. Keep `extractBestAudioWithRetry` as two `extractBestAudio` calls (retry
must not join a **failed** in-flight; after failure the map entry must be gone).

JUnit Jupiter (`org.junit.jupiter.api.Test`), not JUnit 4. See
`YouTubeStreamPrefetchTest.kt`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Prefetch tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeStreamPrefetchTest" --tests "com.yuukifst.orpheus.data.youtube.YouTubeInFlightShareTest"` | `BUILD SUCCESSFUL`, tests pass |
| Stream selection (no break) | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeAudioStreamSelectionTest"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do **not** start the emulator or drive UI with `adb` unless the operator asks.

## Suggested executor toolkit

- If available: `verification-before-completion` before claiming DONE.

## Scope

**In scope**:

- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt`
- New `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeInFlightShare.kt`
  (small `internal` helper, testable without NewPipe)
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeInFlightShareTest.kt`
- Existing `YouTubeStreamPrefetchTest.kt` only if signatures used there change
  (they should not)

**Out of scope**:

- Changing `YouTubeDownloaderImpl` call slots / `runAsStream`.
- Incremental queue fill (007).
- Search first-page paint (006).
- Rewriting NewPipe / Innertube.
- Asserting `LruCache` hits in JVM unit tests (inert; plan 005 already documented).

## Git workflow

- Branch: `advisor/009-share-inflight-stream-extract`
- Commits: e.g. `fix(youtube): coalesce in-flight stream extracts for prefetch and play`
- Do **not** push or open a PR unless the operator asked.

## Steps

### Step 1: `YouTubeInFlightShare`

New file, package `com.yuukifst.orpheus.data.youtube`:

```kotlin
internal class YouTubeInFlightShare<K, V> {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    /**
     * [scope] must outlive callers (extractor-owned SupervisorJob + IO).
     * Do not pass a ViewModel/prefetch Job as [scope] — cancelling the waiter
     * must not cancel other waiters' work.
     */
    suspend fun share(
        key: K,
        scope: CoroutineScope,
        compute: suspend () -> V,
    ): V {
        val deferred = mutex.withLock {
            inFlight[key]?.takeIf { it.isActive } ?: scope.async {
                compute()
            }.also { inFlight[key] = it }
        }
        try {
            return deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight[key] === deferred && deferred.isCompleted) {
                    inFlight.remove(key)
                }
            }
        }
    }
}
```

Use `kotlinx.coroutines.async` / `Deferred` / `Mutex` like search. Failed
`compute()` must complete the `Deferred` exceptionally so the `finally` removes
the entry (retry can start fresh).

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 2: Wire extractor

On `YouTubeStreamExtractor`:

- `private val extractScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- `private val inFlightExtracts = YouTubeInFlightShare<String, YouTubeStreamResult>()`

Refactor `extractBestAudio`:

1. `quality` + `cacheKey` + TTL cache hit (unchanged).
2. Else `inFlightExtracts.share(cacheKey, extractScope) { performExtract(videoId, quality, cacheKey) }`
   where `performExtract` is the current NewPipe + `streamCache.put` body.
3. Stay on `Dispatchers.IO` (`withContext` around the whole function is fine).

Do **not** wrap `share` in `coroutineScope { async }` from the caller.

`prefetchBestAudio` stays a thin `extractBestAudio` wrapper.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 3: Unit tests for the helper

`YouTubeInFlightShareTest.kt`, JUnit Jupiter + `kotlinx.coroutines.test.runTest`
(already used in this module; if `runTest` is missing, `runBlocking` +
`delay` is OK).

Required cases:

1. Two concurrent `share` with the same key: `compute` runs **once**
   (`AtomicInteger`).
2. After success, a third `share` runs `compute` again (map cleared) — or if
   you keep a completed Deferred, STOP and switch to remove-on-complete as
   specified (retry after failure needs a new compute).
3. Failed `compute` (throw): both waiters see the error; a later `share` runs
   `compute` again.

Do **not** call NewPipe in this test.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeInFlightShareTest"`
→ pass

### Step 4: Existing tests + lint

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeStreamPrefetchTest" --tests "com.yuukifst.orpheus.data.youtube.YouTubeAudioStreamSelectionTest" --tests "com.yuukifst.orpheus.data.youtube.YouTubeInFlightShareTest"`
→ `BUILD SUCCESSFUL`

## Test plan

- New: `YouTubeInFlightShareTest` (oracle for coalesce / retry).
- Existing prefetch blank-id tests still pass.
- Reviewer: tap during first-result prefetch should not start a second
  `StreamInfo.getInfo` for the same cache key (log/`Timber` optional; not required).

## Done criteria

- [ ] `extractBestAudio` uses `YouTubeInFlightShare` keyed by `streamCacheKey`.
- [ ] Shared work is parented to extractor `SupervisorJob`, not the caller Job.
- [ ] Failed extracts leave no stuck in-flight entry (retry works).
- [ ] `YouTubeInFlightShareTest` passes; prefetch + audio-selection tests pass.
- [ ] No files outside in-scope list.
- [ ] `plans/README.md` row 009 status `DONE`.

## STOP conditions

- `runAsStream` is **not** safe to share across two logical callers (global
  single `activeCall` that would cancel the other) — report with file:line;
  do not land coalescing that makes tap cancel search. Plan 002 was supposed
  to remove global search/stream cancel collision; if it is back, stop.
- `LruCache` unit tests cannot prove network coalesce — do not add a fake
  "hit" test that calls `extractBestAudio` against the network.

## Maintenance notes

- Prefetch cancel must not abort an in-flight extract another waiter needs.
- Plan 007 will call `extractBestAudio` many times; this map is the reason 009
  is a dependency.
- Quality changes (`youtubeAudioQualityFlow`) use a different cache key — two
  qualities may extract twice; that is correct.
