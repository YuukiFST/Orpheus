# Plan 005: Prefetch the stream URL of the first search result

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 85bcada..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: MED
- **Depends on**: `plans/001-optimistic-miniplayer-youtube-tap.md`, `plans/002-youtube-search-latency.md`
- **Category**: perf
- **Planned at**: commit `85bcada`, 2026-07-26

## Why this matters

Plan 001 makes the mini player appear instantly on tap, but **audio** still starts
only after `StreamInfo.getInfo` resolves the track's audio URL over the network.
The first result of a search is by far the most likely to be tapped, and the
extractor already has a 64-entry, 2-hour in-memory cache for exactly this — so
resolving that one track in the background while the user reads the result list
turns the common case into a cache hit and cuts audio start latency to roughly the
ExoPlayer prepare time.

This is P3 because it spends network and CPU on something the user may not tap.
It is bounded to exactly one track per search to keep that cost trivial.

## Current state

### Files

- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt`
  — the resolver and its cache. Add the prefetch entry point here.
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
  — where search results are published; the prefetch trigger goes here.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt`
  — read-only reference for the cancellation semantics that make this safe.

### The cache the prefetch will populate

```20:38:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt
    private val streamCache = LruCache<String, CachedStreamResult>(64)

    suspend fun extractBestAudio(videoId: String): YouTubeStreamResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        streamCache.get(videoId)?.takeIf { it.isValid(now) }?.result?.let { return@withContext it }

        youTubeInitializer.ensureInitialized()
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val best = info.audioStreams
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream available for $videoId")
        val result = YouTubeStreamResult(
            streamUrl = best.content,
            mimeType = best.format?.mimeType,
        )
        streamCache.put(videoId, CachedStreamResult(result, now))
        result
    }
```

```60:62:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt
    private companion object {
        const val STREAM_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
    }
```

`YouTubePlaybackResolver.resolveMediaItem` calls
`streamExtractor.extractBestAudioWithRetry(track.videoId)`
(`YouTubePlaybackResolver.kt:31`), which goes through `extractBestAudio` and
therefore through this cache. Nothing else needs to change for the tap path to
benefit.

### Why this depends on plan 002

Today `YouTubeDownloaderImpl.execute()` cancels the previous in-flight call on
every request:

```38:42:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt
        val call = client.newCall(httpRequest)
        synchronized(this) {
            activeCall?.cancel()
            activeCall = call
        }
```

A background prefetch issued while the user keeps typing would cancel their
in-flight search (or be cancelled by it), making search *worse*. Plan 002 removes
that global cancel. **Do not start this plan until plan 002 is marked DONE in
`plans/README.md`.**

### Repo conventions that apply

- `@Singleton` + constructor injection; Timber for logging.
- Unit tests: JUnit Jupiter; see
  `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt`
  for the `createForTests()` + cache-accessor style.
- Do not add new `Size(...)` literals anywhere (`CLAUDE.md`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` | exit 0, all pass |
| Single class | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeStreamPrefetchTest"` | exit 0 |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | exit 0 |

**Do NOT** start an emulator, run `adb`, install the APK, or drive the UI
(`CLAUDE.md` hard rule).

## Scope

**In scope**:
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamPrefetchTest.kt` (create)

**Out of scope** (do NOT touch):
- `YouTubePlaybackResolver.kt` / `YouTubePlaybackViewModel.kt` — they already read
  through the cache; no change needed.
- `YouTubeDownloaderImpl.kt` — its cancellation semantics are plan 002's job.
- Prefetching more than one result, prefetching on scroll, or prefetching
  thumbnails. Explicitly rejected below.
- Any download / offline feature.

## Git workflow

- Branch: `advisor/005-prefetch-first-result-stream`
- Commit per step; short imperative subject, no `Co-Authored-By:` trailer.
- Do NOT push or open a PR unless the operator asks.

## Steps

### Step 1: Add a non-throwing, cancellation-safe prefetch to the extractor

Add to `YouTubeStreamExtractor`:

```kotlin
    /**
     * Warms [streamCache] for a track the user is likely to tap. Never throws:
     * a prefetch failure must be indistinguishable from not having prefetched.
     * Returns true when the cache now holds a valid entry for [videoId].
     */
    suspend fun prefetchBestAudio(videoId: String): Boolean {
        if (videoId.isBlank()) return false
        if (isCached(videoId)) return true
        return runCatching { extractBestAudio(videoId) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag("YouTubeStreamExtractor").w("Prefetch failed for %s", videoId)
            }
            .isSuccess
    }

    internal fun isCached(videoId: String): Boolean =
        streamCache.get(videoId)?.isValid(System.currentTimeMillis()) == true
```

Notes:
- Rethrow `CancellationException` — swallowing it breaks structured concurrency.
- Use `extractBestAudio`, **not** `extractBestAudioWithRetry`: a prefetch must not
  retry with a 250 ms delay (`YouTubeStreamExtractor.kt:40-47`); the tap path will
  retry if it actually matters.
- Do not log the resolved stream URL. It is a signed, user-identifying URL; log
  the video id only.

**Verify**: `:app:compileDebugKotlin` → exit 0

### Step 2: Trigger the prefetch when results are published

In `YouTubeSearchViewModel`, inject `YouTubeStreamExtractor` (constructor
parameter, following the existing injection list) and add:

```kotlin
    private var prefetchJob: Job? = null

    private fun prefetchTopResult(results: List<YouTubeTrack>) {
        val videoId = results.firstOrNull()?.videoId ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            streamExtractor.prefetchBestAudio(videoId)
        }
    }
```

Call `prefetchTopResult(results)` in `executeSearch` immediately after each
successful `_uiState.update { ... results ... }` — both on the cached fast path
(around line 168) and after the network search (around line 188).

Constraints:
- Exactly one track. Do not loop over the first N.
- Cancel the previous prefetch first — if the user typed a new query, the old
  first result is no longer the likely tap.
- Do **not** await it, do not surface its result in `uiState`, and do not let it
  affect `isLoading`.

**Verify**:
- `rg -n "prefetchTopResult|prefetchBestAudio" app/src/main/java` → definition + 2 call sites + extractor method
- `:app:compileDebugKotlin` → exit 0

### Step 3: Do not prefetch when the user is clearly not going to tap

Skip the prefetch when either holds:
- The results came from the cached fast path **and** the cache already holds the
  stream (`streamExtractor.isCached(videoId)`) — the `prefetchBestAudio`
  early-return already covers this, so no extra code is needed; just confirm it.
- The query is being typed rather than submitted. Concretely: pass a
  `isDebouncedTyping: Boolean` through `executeSearch` (it already receives
  `saveHistory: Boolean`, which is `false` exactly for the debounced-typing path —
  reuse that signal rather than adding a parameter) and skip the prefetch for the
  debounced path if you find that a single query produces more than one results
  publication in practice.

Prefer the simpler behavior: prefetch on every published result set, relying on
step 2's job cancellation. Only add the typing guard if you can point to a code
path where results are published repeatedly for one query. Record which you chose.

**Verify**: `:app:compileDebugKotlin` → exit 0

### Step 4: Add unit tests

Create `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamPrefetchTest.kt`.
You need to exercise the cache logic without touching the network, so:

1. `isCached("unknown")` is `false` on a fresh extractor.
2. `prefetchBestAudio("")` returns `false` and performs no work (blank guard).
3. If (and only if) you can seed the cache from a test without a network call, add
   an internal `seedStreamCacheForTests(videoId, result)` alongside the existing
   `clearStreamCacheForTests()` (`YouTubeStreamExtractor.kt:49-51`) and assert that
   `prefetchBestAudio` returns `true` immediately for a seeded id.

Construct the extractor the way the search repository test does — via a
`createForTests()`-style factory built on
`YouTubeInitializer(YouTubeDownloaderImpl.createStandalone())`. Add such a factory
as an `internal companion object` member if one does not exist. No test may make a
real HTTP request.

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeStreamPrefetchTest"`
→ exit 0

### Step 5: Full verification

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin`
2. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest`
3. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug`

All exit 0.

## Test plan

- New: `YouTubeStreamPrefetchTest` (cases in step 4).
- Existing tests that must keep passing: all of
  `app/src/test/java/com/yuukifst/orpheus/data/youtube/`.
- Structural pattern: `YouTubeSearchRepositoryCacheTest.kt`.
- Verification: `:app:testDebugUnitTest` → all pass.

## Done criteria

ALL must hold:

- [ ] Plan 002 is marked DONE in `plans/README.md` before this plan is executed
- [ ] `:app:compileDebugKotlin` exits 0
- [ ] `:app:testDebugUnitTest` exits 0, new test class present and passing
- [ ] `:app:lintDebug` exits 0
- [ ] `prefetchBestAudio` never throws except `CancellationException`
- [ ] Exactly one videoId is prefetched per published result set
      (`rg -n "prefetchBestAudio" app/src/main/java` shows no loop / `take(n)` around it)
- [ ] No stream URL is logged (`rg -n "streamUrl" app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeStreamExtractor.kt` shows no Timber call with it)
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row for 005 updated

## STOP conditions

Stop and report back (do not improvise) if:

- Plan 002 is not DONE. The prefetch is unsafe before it (it will fight the search
  request over `YouTubeDownloaderImpl`'s call slot).
- You find that `StreamInfo.getInfo` mutates shared NewPipe state in a way that
  makes a concurrent search + prefetch unsafe beyond HTTP-level contention.
- The prefetch measurably delays the search results publication in a way visible in
  the code (e.g. you cannot avoid it running on the same dispatcher-blocking path).
- You cannot write any of the step-4 tests without a network call. Report it and
  ship the change with case 1 and 2 only rather than adding network-dependent tests.

## Maintenance notes

- The prefetch is intentionally minimal: one track, best-effort, silent. Resist
  requests to widen it — prefetching the visible page would multiply YouTube
  requests per search and raise the risk of HTTP 429 / `ReCaptchaException`
  (`YouTubeDownloaderImpl.kt:46-49`), which fails the *search*, not just the prefetch.
- The 2-hour `STREAM_CACHE_TTL_MS` matters: YouTube audio URLs expire. If users
  report playback failing on a track they searched a long time ago, that TTL and
  the retry path are the places to look, not this prefetch.
- A reviewer should check that the prefetch result is never awaited and never
  reaches `uiState` — if it ever gates the UI, the plan has been inverted.
- Rejected on purpose: prefetching on scroll position (needs a visibility signal
  from the `LazyColumn` and turns every scroll into network traffic) and
  prefetching for suggestions (no video ids available at that point).
