# Plan 002: Cut YouTube Search latency (debounce, suggestion cache, HTTP call slot)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 85bcada..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/youtube app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
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

Typing a query in the YouTube Search tab costs a fixed **450 ms** of debounce
before the network request even starts, then one NewPipe search round trip. On
top of that, three avoidable problems make it feel worse than it is:

1. Suggestions are fetched with **no cache**, so every keystroke pattern re-hits
   the network — and they share a single HTTP "slot" with the search request,
   where each new request **cancels the previous one**. A suggestion fired at
   150 ms can kill an in-flight search.
2. The `searchCachedOnly` fast path is only consulted for the exact trimmed
   query, so typing `bea` → `beat` → `bea` (backspace) re-issues a network search
   for a query that is already cached under a different key length.
3. `warmUpConnection()` competes for the same single HTTP slot on screen open.

After this plan: the debounce is shorter and adaptive, suggestions are cached,
concurrent search + suggestion requests no longer cancel each other, and a
backspace to a previously-seen query renders from cache instantly.

## Current state

### Files

- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
  — debounce constants and the search/suggestion job orchestration.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt`
  — the NewPipe `Downloader` implementation with the single-`activeCall` slot.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt`
  — suggestion fetch, no cache.
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
  — search with a 32-entry `LruCache` and in-flight dedup. Reference for the
  caching style you should match in the suggestion repository.

### Debounce constants

```63:67:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val SUGGESTION_DEBOUNCE_MS = 150L
        const val MIN_QUERY_LENGTH = 2
    }
```

```115:122:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt
        debouncedSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (trimmed.length < MIN_QUERY_LENGTH) {
                _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
                return@launch
            }
            executeSearch(trimmed, saveHistory = false)
        }
```

### The single HTTP call slot — every new request cancels the previous one

```38:45:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt
        val call = client.newCall(httpRequest)
        synchronized(this) {
            activeCall?.cancel()
            activeCall = call
        }

        try {
            val httpResponse = call.execute()
```

This was presumably added so that `cancelActiveRequest()` could abort a stale
search (`CLAUDE.md`: "Cancelling a coroutine does not cancel blocking HTTP").
But because the cancel happens for *every* `execute()`, unrelated concurrent
NewPipe work cancels each other:

- suggestion request (t=150 ms) starts → becomes `activeCall`
- search request (t=450 ms) starts → **cancels the suggestion**, becomes `activeCall`
- next keystroke's suggestion (t=+150 ms) starts → **cancels the in-flight search**

A NewPipe `SearchInfo.getInfo` can also issue more than one HTTP request
internally; with this design a suggestion landing in between kills the search
mid-flight, which surfaces as the "cancel" branch in `executeSearch`
(`YouTubeSearchViewModel.kt:199-202`) and an apparently dead search box.

### Suggestions have no cache

```14:25:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        youTubeInitializer.ensureInitialized()
        runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(trimmed)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
    }
```

### The caching style to match (search repository)

```26:33:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt
    private val searchCache = LruCache<String, List<YouTubeTrack>>(32)
    private val inFlightSearches = mutableMapOf<String, Deferred<List<YouTubeTrack>>>()
    private val inFlightMutex = Mutex()

    suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.isBlank()) return@withContext emptyList()
        searchCache.get(key)?.let { return@withContext it }
```

```88:90:app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt
    internal fun searchCachedOnly(query: String): List<YouTubeTrack>? {
        return searchCache.get(query.trim().lowercase())
    }
```

Note `searchCachedOnly` is `internal` and already used as a synchronous fast path
in the ViewModel (`YouTubeSearchViewModel.kt:166-181`) — that part is good and
must keep working.

### Repo conventions that apply

- `@Singleton` + constructor injection via Hilt, as in every class above.
- Timber for logging.
- Unit tests: JUnit Jupiter. Existing examples to model on:
  `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt`
  and `.../YouTubeSuggestionRepositoryTest.kt`.
- `LruCache` is `android.util.LruCache` — it works in JVM unit tests in this repo
  (see `YouTubeSearchRepositoryCacheTest`), so no Robolectric is needed.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` | exit 0, all pass |
| Single class | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeSuggestionCacheTest"` | exit 0 |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | exit 0 |

**Do NOT** start an emulator, run `adb`, or drive the UI (`CLAUDE.md` hard rule).
There is no automated way to measure real network latency here — the gates are
compile, lint and unit tests; the maintainer measures on device.

## Scope

**In scope**:
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt`
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt`
- `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionCacheTest.kt` (create)
- `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchPrefixCacheTest.kt` (create)

**Out of scope** (do NOT touch):
- `YouTubeStreamExtractor.kt` / `YouTubePlaybackResolver.kt` — playback resolution,
  not search. Prefetch is plan 005.
- `AppModule.kt`'s shared `OkHttpClient` — the pool/timeouts there are fine and
  shared with unrelated features.
- The result list rendering in `YouTubeSearchScreen.kt` — it already uses
  `LazyColumn` with stable keys and the correct `SmartImageYouTubeListTargetSize`
  constant. Do not add a new `Size(...)` literal anywhere (`CLAUDE.md`: image
  target sizes are cache keys).
- `NewPipe` version bumps.

## Git workflow

- Branch: `advisor/002-youtube-search-latency`
- Commit per step or per logical unit; short imperative subject, no
  `Co-Authored-By:` trailer.
- Do NOT push or open a PR unless the operator asks.

## Steps

### Step 1: Make request cancellation explicit instead of global

Change `YouTubeDownloaderImpl` so that `execute()` no longer cancels whatever
call happens to be in flight, while `cancelActiveRequest()` keeps working for the
search path.

Replace the single `activeCall` field with a set of live search calls plus a
tag-scoped notion of what `cancelActiveRequest()` targets. The simplest shape
that preserves current behavior for search and stops the cross-cancellation:

```kotlin
    private val activeCalls = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<okhttp3.Call, Boolean>(),
    )

    override fun execute(request: Request): Response {
        val httpRequest = /* unchanged builder */
        val call = client.newCall(httpRequest)
        activeCalls.add(call)
        try {
            val httpResponse = call.execute()
            /* unchanged response handling */
        } finally {
            activeCalls.remove(call)
        }
    }

    /**
     * Coroutine cancellation does not interrupt a blocking OkHttp `execute()`,
     * so a superseded query's HTTP work has to be cancelled explicitly.
     * Cancels all in-flight NewPipe calls; only invoked when the caller knows
     * every outstanding request is stale.
     */
    fun cancelActiveRequest() {
        val snapshot = activeCalls.toList()
        activeCalls.clear()
        snapshot.forEach { runCatching { it.cancel() } }
    }
```

Keep `warmUpConnection()` as-is, but do **not** register its call in
`activeCalls` — it must neither be cancelled by, nor cancel, a real query.

**Verify**:
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0
- `rg -n "activeCall\?\.cancel\(\)" app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt`
  → no matches (the per-`execute()` cancel is gone)

### Step 2: Stop cancelling in-flight HTTP for a query that is still current

In `YouTubeSearchViewModel.executeSearch`, `searchRepository.cancelActiveRequest()`
is called unconditionally before every network search (line 183). With step 1
this now cancels *all* NewPipe calls, including a suggestion fetch the user still
wants and an in-flight search for the same query that the repository would have
shared via `inFlightSearches`.

Change it so the cancel only happens when a *different* query is superseded:

- Track the last query for which a network search was actually started (a
  `private var activeNetworkQuery: String? = null`).
- Call `cancelActiveRequest()` only when `activeNetworkQuery != null &&
  activeNetworkQuery != trimmed`.
- Set `activeNetworkQuery = trimmed` before `searchRepository.search(trimmed)`
  and clear it in a `finally` if it is still equal to `trimmed`.

This preserves the intent ("a superseded query's HTTP work must die") without
killing work the user is still waiting for.

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 3: Shorten the search debounce and make it adaptive

In `YouTubeSearchViewModel`:

- `SEARCH_DEBOUNCE_MS`: `450L` → `260L`.
- `SUGGESTION_DEBOUNCE_MS`: keep `150L`.
- Add `const val SEARCH_DEBOUNCE_CACHED_MS = 0L` and, in the debounced search
  job, skip the delay entirely when the trimmed query is already in the search
  cache — a cache hit costs nothing, so there is no reason to wait:

```kotlin
        debouncedSearchJob = viewModelScope.launch {
            val cachedAlready = searchRepository.searchCachedOnly(trimmed) != null
            delay(if (cachedAlready) SEARCH_DEBOUNCE_CACHED_MS else SEARCH_DEBOUNCE_MS)
            ...
        }
```

Rationale for 260 ms: it is below the ~300 ms threshold where a pause between
keystrokes reads as "I stopped typing", and it matches the local-library search
debounce already used in this app (`SearchStateHolder.kt`, 300 ms). Do not go
below ~200 ms: each fired search is a full NewPipe page fetch and parse, and
over-firing will make things slower, not faster.

**Verify**:
- `rg -n "SEARCH_DEBOUNCE_MS = 260L" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt` → 1 match
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0

### Step 4: Add an LRU cache to the suggestion repository

Give `YouTubeSuggestionRepository` the same shape as `YouTubeSearchRepository`:
a `LruCache<String, List<String>>(64)` keyed on `query.trim().lowercase()`,
checked before the network call and populated after a successful fetch. Do not
cache the empty list returned by the `runCatching` failure path — caching a
failure would make the suggestion list permanently empty for that prefix.

Also add, mirroring the search repository's test hooks:

```kotlin
    internal fun suggestionsCachedOnly(query: String): List<String>? =
        suggestionCache.get(query.trim().lowercase())

    internal fun clearSuggestionCacheForTests() { suggestionCache.evictAll() }

    internal fun seedSuggestionCacheForTests(query: String, suggestions: List<String>) {
        suggestionCache.put(query.trim().lowercase(), suggestions)
    }
```

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 5: Serve a backspaced query from cache without a round trip

`executeSearch` already checks `searchCachedOnly(trimmed)` first — keep that.
Add one more fast path in `updateQuery`, *before* scheduling the debounced job:
if the trimmed query is already in the search cache, publish the cached results
immediately (synchronously, no delay) so a backspace to a previously-typed query
repaints instantly instead of waiting 260 ms.

Guard it with the same `MIN_QUERY_LENGTH` check and do not set `isLoading`.
Reuse the existing state-update shape from `executeSearch` lines 166-181 so
behavior (`hasSearched = true`, `suggestions = emptyList()`) stays consistent.
Do not persist history on this path (`saveHistory = false` semantics).

**Verify**: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` → exit 0.

### Step 6: Add unit tests

Create `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionCacheTest.kt`:

1. After `seedSuggestionCacheForTests("bea", listOf("beatles", "beach"))`,
   `suggestionsCachedOnly("BEA ")` returns the same list instance
   (assert with `assertSame`, exactly as `YouTubeSearchRepositoryCacheTest` does).
2. `suggestionsCachedOnly("unseen")` returns `null`.
3. `clearSuggestionCacheForTests()` makes a previously seeded key return `null`.

You will need a construction path for the repository in tests. Follow the pattern
in `YouTubeSearchRepository.createForTests()`
(`YouTubeSearchRepository.kt:92-100`) and add an equivalent
`internal companion object { fun createForTests(): YouTubeSuggestionRepository }`
to `YouTubeSuggestionRepository` that builds it with
`YouTubeInitializer(YouTubeDownloaderImpl.createStandalone())`. The tests must not
perform any network call — only the cache accessors are exercised.

Also check whether `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepositoryTest.kt`
already constructs the repository; if it does, reuse that mechanism instead of
adding a second one.

Create `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchPrefixCacheTest.kt`:

4. `searchCachedOnly` is case- and whitespace-insensitive:
   seed `"Beatles"`, then assert `searchCachedOnly("  beatles ")` is non-null.
   (This pins the behavior step 5's fast path depends on.)

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.*"`
→ exit 0, new tests pass, existing YouTube tests still pass.

### Step 7: Full verification

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin`
2. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest`
3. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug`

All exit 0.

## Test plan

- New: `YouTubeSuggestionCacheTest` (cache hit / miss / evict — 3 cases).
- New: `YouTubeSearchPrefixCacheTest` (key normalization — 1 case).
- Existing tests that must keep passing:
  `YouTubeSearchRepositoryCacheTest`, `YouTubeSuggestionRepositoryTest`,
  `YouTubeTrackTest`, `YouTubeThumbnailsTest`, `YouTubeVideoIdExtractionTest`,
  `YouTubeUploaderLabelTest`, `YouTubePlayOnceIsolationTest`.
- Structural pattern: `YouTubeSearchRepositoryCacheTest.kt` (JUnit Jupiter,
  `assertSame`, `createForTests()` factory, no network).
- Verification: `:app:testDebugUnitTest` → all pass.

## Done criteria

ALL must hold:

- [ ] `:app:compileDebugKotlin` exits 0
- [ ] `:app:testDebugUnitTest` exits 0, new suggestion-cache tests present and passing
- [ ] `:app:lintDebug` exits 0
- [ ] `rg -n "activeCall\?\.cancel" app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeDownloaderImpl.kt` → no matches
- [ ] `rg -n "SEARCH_DEBOUNCE_MS" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt` shows `260L`
- [ ] `rg -n "suggestionCache" app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt` → at least 3 matches
- [ ] No new `Size(` literal introduced anywhere (`rg -n "Size\(" app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt` unchanged)
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row for 002 updated

## STOP conditions

Stop and report back (do not improvise) if:

- Removing the per-`execute()` cancel breaks an existing test, or you find a code
  path that *relies* on `execute()` cancelling the previous call (search for other
  callers: `rg -n "cancelActiveRequest" app/src/main/java`).
- NewPipe turns out to call `execute()` concurrently from multiple threads for a
  single `SearchInfo.getInfo` in a way that makes `cancelActiveRequest()`'s
  "cancel everything" semantics wrong. Report what you observed.
- You cannot construct `YouTubeSuggestionRepository` in a unit test without a
  network call or an Android framework dependency. Report it; do not add
  Robolectric to the project.
- Lowering the debounce makes an existing test that asserts 450 ms fail. Update
  the test to the new constant only if the test is asserting the constant itself;
  if it is asserting user-visible behavior, STOP and report.

## Maintenance notes

- `cancelActiveRequest()` is now "cancel every in-flight NewPipe call". If a
  future feature issues NewPipe requests in the background (e.g. plan 005's
  stream prefetch, or channel browsing), it must either tolerate being cancelled
  by a search, or the cancellation needs to become tag-scoped. Flag this in
  review of any new NewPipe caller.
- The debounce constant is a UX knob. If YouTube starts rate-limiting (HTTP 429 →
  `ReCaptchaException` in `YouTubeDownloaderImpl.kt:46-49`), raising it back is the
  first mitigation to try.
- The suggestion cache is memory-only and unbounded in time; suggestions go stale
  slowly and the LRU bound is the only eviction. That is deliberate — a TTL would
  add complexity for no perceivable benefit within a session.
- Explicitly deferred: HTTP/2 and gzip are left to OkHttp defaults; no disk cache
  for search responses (YouTube results change and the memory LRU already covers
  the within-session case).
