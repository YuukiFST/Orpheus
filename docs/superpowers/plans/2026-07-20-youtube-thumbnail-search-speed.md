# YouTube Thumbnail & Search Speed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve YouTube search thumbnails (sharp, high-resolution) and make search feel faster (no stale results, fewer wasted API calls, instant suggestions while typing).

**Architecture:** Add small pure helpers in `data/youtube` for thumbnail URL selection and search result caching. Refactor `YouTubeSearchViewModel` to mirror the request-sequencing pattern already used in `SearchStateHolder` (debounce + `requestId` + `collectLatest`). Add a lightweight suggestions path via NewPipe's `SuggestionExtractor` for text chips; keep full video search debounced behind a minimum query length.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, NewPipe Extractor v0.26.4, Coil (`SmartImage`), JUnit 5, `android.util.LruCache`

## Global Constraints

- No new dependencies; reuse NewPipe Extractor and existing `android.util.LruCache` patterns (`ColorRoles.kt`, `LyricsRepositoryImpl.kt`).
- Keep `YouTubeInitializer.ensureInitialized()` before any NewPipe call.
- Full search must continue using `SearchInfo.getInfo(ServiceList.YouTube, handler)` (not the extractor-only overload).
- Debounce constants live in the ViewModel companion object (same style as `SearchStateHolder.SEARCH_DEBOUNCE_MS`).
- User-facing copy stays English (existing YouTube screen strings).
- Do not change audio download/stream logic in this plan.

## File Map

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnails.kt` | **Create.** Pick highest-res thumbnail URL from NewPipe `Image` list; fallback to `i.ytimg.com` by `videoId`. |
| `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt` | **Modify.** Use thumbnail helper; add in-memory LRU cache for search results. |
| `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt` | **Create.** Thin wrapper around `ServiceList.YouTube.getSuggestionExtractor().suggestionList()`. |
| `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt` | **Modify.** Request sequencing, min query length, separate suggestion debounce, cache-aware search. |
| `app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt` | **Modify.** Suggestion chips UI; bump thumbnail decode size. |
| `app/src/main/java/com/yuukifst/orpheus/presentation/components/SmartImage.kt` | **Modify.** Add `SmartImageYouTubeListTargetSize` constant (240×240). |
| `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnailsTest.kt` | **Create.** Unit tests for thumbnail selection and fallback. |
| `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt` | **Create.** Unit tests for cache hit/miss behavior (test double or package-visible cache). |

---

### Task 1: Thumbnail URL selection helper

**Files:**
- Create: `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnails.kt`
- Test: `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnailsTest.kt`

**Interfaces:**
- Consumes: `org.schabi.newpipe.extractor.Image`
- Produces:
  ```kotlin
  internal fun selectBestThumbnailUrl(thumbnails: List<Image>, videoId: String): String
  ```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

class YouTubeThumbnailsTest {
    @Test
    fun selectBestThumbnailUrl_picksHighestResolution() {
        val low = Image("https://example.com/low.jpg", 120, 90, ResolutionLevel.LOW)
        val high = Image("https://example.com/high.jpg", 480, 360, ResolutionLevel.MEDIUM)
        val best = Image("https://example.com/best.jpg", 1280, 720, ResolutionLevel.HIGH)

        assertEquals(
            "https://example.com/best.jpg",
            selectBestThumbnailUrl(listOf(low, best, high), "abc123"),
        )
    }

    @Test
    fun selectBestThumbnailUrl_fallsBackToHqDefaultWhenEmpty() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            selectBestThumbnailUrl(emptyList(), "abc123"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeThumbnailsTest" -Porcheus.disableReleaseSigning=true`

Expected: FAIL — `selectBestThumbnailUrl` not defined

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.Image

internal fun selectBestThumbnailUrl(thumbnails: List<Image>, videoId: String): String {
    val best = thumbnails.maxByOrNull { image ->
        val width = image.width.takeIf { it > 0 } ?: 0
        val height = image.height.takeIf { it > 0 } ?: 0
        width * height
    }?.url?.takeIf { it.isNotBlank() }

    return best ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2

Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnails.kt \
        app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeThumbnailsTest.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "feat: pick highest-res YouTube thumbnail URL"
```

---

### Task 2: Wire thumbnail helper into search mapping

**Files:**
- Modify: `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt:36-45`

**Interfaces:**
- Consumes: `selectBestThumbnailUrl(thumbnails, videoId)` from Task 1
- Produces: `YouTubeTrack.thumbnailUrl` populated with best URL

- [ ] **Step 1: Update `toYouTubeTrack()`**

Replace:
```kotlin
thumbnailUrl = thumbnails.firstOrNull()?.url.orEmpty(),
```
With:
```kotlin
thumbnailUrl = selectBestThumbnailUrl(thumbnails, id),
```

- [ ] **Step 2: Run existing YouTube tests**

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.*" -Porcheus.disableReleaseSigning=true`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "feat: use best thumbnail URL in YouTube search results"
```

---

### Task 3: Sharper list thumbnails in UI

**Files:**
- Modify: `app/src/main/java/com/yuukifst/orpheus/presentation/components/SmartImage.kt:41-43`
- Modify: `app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt:69-70,334`
- Modify: `app/src/main/java/com/yuukifst/orpheus/presentation/screens/DownloadsScreen.kt:56-57,181` (YouTube downloads list uses same 72dp thumb)

**Interfaces:**
- Produces: `SmartImageYouTubeListTargetSize = Size(240, 240)` — covers 72dp at 3× density (~216px) with headroom

- [ ] **Step 1: Add constant in `SmartImage.kt`**

```kotlin
val SmartImageYouTubeListTargetSize = Size(240, 240)
```

- [ ] **Step 2: Use it in YouTube lists**

In `YouTubeSearchScreen.kt` and `DownloadsScreen.kt`, replace `SmartImageListTargetSize` with `SmartImageYouTubeListTargetSize` for YouTube track rows only.

- [ ] **Step 3: Manual check**

Build debug APK and open YouTube search on a 1080p+ device. Thumbnails at 72dp should look sharp, not upscaled from 128px.

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./scripts/build.sh :app:assembleDebug -Porcheus.disableReleaseSigning=true`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/presentation/components/SmartImage.kt \
        app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt \
        app/src/main/java/com/yuukifst/orpheus/presentation/screens/DownloadsScreen.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "feat: decode YouTube list thumbnails at 240px"
```

---

### Task 4: Search result LRU cache

**Files:**
- Modify: `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt`
- Test: `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Singleton
  class YouTubeSearchRepository @Inject constructor() {
      suspend fun search(query: String): List<YouTubeTrack>
      internal fun clearSearchCacheForTests()
  }
  ```
- Cache: `LruCache<String, List<YouTubeTrack>>(32)` keyed by `query.trim().lowercase()`

- [ ] **Step 1: Write failing cache test**

Use a test that calls `clearSearchCacheForTests()`, then verifies two identical queries only hit network once. Because `search()` calls NewPipe (network), test the cache layer directly:

```kotlin
package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class YouTubeSearchRepositoryCacheTest {
    @Test
    fun searchCache_returnsSameListInstanceForSameQuery() {
        val repo = YouTubeSearchRepository()
        repo.clearSearchCacheForTests()
        val cached = listOf(
            com.yuukifst.orpheus.data.youtube.model.YouTubeTrack(
                videoId = "x", title = "t", channelName = "c",
                thumbnailUrl = "u", durationMs = 1L,
            ),
        )
        repo.seedSearchCacheForTests("query", cached)

        val first = repo.searchCachedOnly("query")
        val second = repo.searchCachedOnly("query")

        assertSame(first, second)
    }
}
```

Expose two `@VisibleForTesting internal` helpers on the repository:
- `seedSearchCacheForTests(query, results)`
- `searchCachedOnly(query): List<YouTubeTrack>?` — returns cache entry without network

Then wire real `search()` as:
```kotlin
suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
    val key = query.trim().lowercase()
    if (key.isBlank()) return@withContext emptyList()
    searchCache.get(key)?.let { return@withContext it }

    // existing NewPipe search logic...
    val results = /* ... */
    searchCache.put(key, results)
    results
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeSearchRepositoryCacheTest" -Porcheus.disableReleaseSigning=true`

- [ ] **Step 3: Implement cache in repository**

Add at class level:
```kotlin
private val searchCache = android.util.LruCache<String, List<YouTubeTrack>>(32)

internal fun clearSearchCacheForTests() {
    searchCache.evictAll()
}

internal fun seedSearchCacheForTests(query: String, results: List<YouTubeTrack>) {
    searchCache.put(query.trim().lowercase(), results)
}

internal fun searchCachedOnly(query: String): List<YouTubeTrack>? {
    return searchCache.get(query.trim().lowercase())
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepository.kt \
        app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSearchRepositoryCacheTest.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "perf: cache YouTube search results in memory"
```

---

### Task 5: YouTube suggestion repository (lightweight autocomplete)

**Files:**
- Create: `app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt`
- Test: `app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepositoryTest.kt` (smoke test with mocked behavior OR skip network test and only test blank-query guard)

**Interfaces:**
- Produces:
  ```kotlin
  @Singleton
  class YouTubeSuggestionRepository @Inject constructor() {
      suspend fun suggestions(query: String): List<String>
  }
  ```

- [ ] **Step 1: Write implementation**

```kotlin
package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSuggestionRepository @Inject constructor() {

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        YouTubeInitializer.ensureInitialized()
        runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(trimmed)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_SUGGESTIONS = 8
    }
}
```

Note: verify accessor name via IDE/compiler — may be `getSuggestionExtractor()` in Java, `suggestionExtractor` in Kotlin.

- [ ] **Step 2: Write blank-query unit test**

```kotlin
@Test
fun suggestions_blankQuery_returnsEmpty() = runBlocking {
    val repo = YouTubeSuggestionRepository()
    assertEquals(emptyList<String>(), repo.suggestions("  "))
}
```

- [ ] **Step 3: Run test**

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.YouTubeSuggestionRepositoryTest" -Porcheus.disableReleaseSigning=true`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepository.kt \
        app/src/test/java/com/yuukifst/orpheus/data/youtube/YouTubeSuggestionRepositoryTest.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "feat: add lightweight YouTube search suggestions"
```

---

### Task 6: ViewModel — request sequencing, debounce tuning, suggestions

**Files:**
- Modify: `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt`

**Interfaces:**
- Consumes: `YouTubeSuggestionRepository.suggestions(query)`, `YouTubeSearchRepository.search(query)`
- Produces: extended `YouTubeSearchUiState`:
  ```kotlin
  data class YouTubeSearchUiState(
      // existing fields...
      val suggestions: List<String> = emptyList(),
  )
  ```
- Pattern: copy from `SearchStateHolder` — `AtomicLong` request IDs, ignore stale responses

- [ ] **Step 1: Extend UI state and inject suggestion repo**

```kotlin
data class YouTubeSearchUiState(
    // ...existing...
    val suggestions: List<String> = emptyList(),
)

@HiltViewModel
class YouTubeSearchViewModel @Inject constructor(
    private val searchRepository: YouTubeSearchRepository,
    private val suggestionRepository: YouTubeSuggestionRepository,
    // ...existing deps...
) : ViewModel() {

    private val latestSearchRequestId = AtomicLong(0L)
    private var debouncedSearchJob: Job? = null
    private var debouncedSuggestionJob: Job? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
        const val SUGGESTION_DEBOUNCE_MS = 150L
        const val MIN_QUERY_LENGTH = 2
    }
}
```

Add imports: `java.util.concurrent.atomic.AtomicLong`, `kotlinx.coroutines.CancellationException`

- [ ] **Step 2: Refactor `updateQuery`**

```kotlin
fun updateQuery(query: String) {
    _uiState.update { it.copy(query = query, error = null) }
    debouncedSearchJob?.cancel()
    debouncedSuggestionJob?.cancel()

    val trimmed = query.trim()
    if (trimmed.isBlank()) {
        _uiState.update {
            it.copy(
                results = emptyList(),
                suggestions = emptyList(),
                isLoading = false,
                hasSearched = false,
            )
        }
        return
    }

    debouncedSuggestionJob = viewModelScope.launch {
        delay(SUGGESTION_DEBOUNCE_MS)
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return@launch
        }
        val suggestions = suggestionRepository.suggestions(trimmed)
        _uiState.update { state ->
            if (state.query.trim() != trimmed) state else state.copy(suggestions = suggestions)
        }
    }

    debouncedSearchJob = viewModelScope.launch {
        delay(SEARCH_DEBOUNCE_MS)
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
            return@launch
        }
        executeSearch(trimmed, saveHistory = false)
    }
}
```

- [ ] **Step 3: Refactor `executeSearch` with request ID guard**

```kotlin
private suspend fun executeSearch(trimmed: String, saveHistory: Boolean) {
    val requestId = latestSearchRequestId.incrementAndGet()
    _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
    try {
        val results = searchRepository.search(trimmed)
        if (requestId != latestSearchRequestId.get()) return
        if (saveHistory) {
            searchHistoryDao.deleteByQuery(trimmed)
            searchHistoryDao.insert(
                SearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis()),
            )
            refreshSearchHistory()
        }
        _uiState.update { it.copy(results = results, isLoading = false, suggestions = emptyList()) }
    } catch (e: CancellationException) {
        throw e
    } catch (error: Exception) {
        if (requestId != latestSearchRequestId.get()) return
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error.message ?: "Search failed",
            )
        }
    }
}
```

- [ ] **Step 4: Add `searchSuggestion(text: String)` for chip taps**

```kotlin
fun searchSuggestion(text: String) {
    debouncedSearchJob?.cancel()
    debouncedSuggestionJob?.cancel()
    val trimmed = text.trim()
    _uiState.update { it.copy(query = trimmed, error = null) }
    viewModelScope.launch {
        executeSearch(trimmed, saveHistory = true)
    }
}
```

- [ ] **Step 5: Build and run unit tests**

Run: `cd /mnt/Others/Projects/PersonalProjects/Orpheus && nix develop --impure -c ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.youtube.*" -Porcheus.disableReleaseSigning=true`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/YouTubeSearchViewModel.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "perf: faster YouTube search with suggestions and stale-result guard"
```

---

### Task 7: Suggestion chips UI

**Files:**
- Modify: `app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt`

**Interfaces:**
- Consumes: `uiState.suggestions`, `viewModel.searchSuggestion(text)`

- [ ] **Step 1: Render suggestions above results/history**

When `uiState.suggestions.isNotEmpty()` and `uiState.results.isEmpty()` and not loading, show a `LazyColumn` section:

```kotlin
items(uiState.suggestions, key = { it }) { suggestion ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.searchSuggestion(suggestion) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.History, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

Insert this block in the `when` that currently switches between loading, results, empty, and history — only when query is non-blank, suggestions exist, and full results not yet shown.

- [ ] **Step 2: Sync `searchQuery` when user taps suggestion**

In `searchSuggestion` path, also update local `searchQuery` in screen:

```kotlin
onClick = {
    searchQuery = suggestion
    viewModel.searchSuggestion(suggestion)
}
```

- [ ] **Step 3: Manual QA checklist**

1. Type `bo` — suggestion chips appear within ~200ms (network permitting)
2. Pause typing `bohemian` — full results load; suggestions clear
3. Type fast `abc` then `xyz` — only `xyz` results show (no stale `abc` flash)
4. Query with 1 char — no API search, no suggestions
5. Repeat same query — instant from cache

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yuukifst/orpheus/presentation/screens/YouTubeSearchScreen.kt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "feat: show YouTube search suggestion chips while typing"
```

---

### Task 8: Version bump & changelog (release prep)

**Files:**
- Modify: `gradle.properties` (`APP_VERSION_NAME=1.0.4`, `APP_VERSION_CODE=5`)
- Create: `fastlane/metadata/android/en-US/changelogs/5.txt`

- [ ] **Step 1: Bump version**

- [ ] **Step 2: Add changelog**

```
Sharper YouTube thumbnails, faster search with suggestions, and in-memory result caching.
```

- [ ] **Step 3: Commit** (only if user requests release)

```bash
git add gradle.properties fastlane/metadata/android/en-US/changelogs/5.txt
/home/yuuki/Projects/my-harness-config/scripts/git-safe-commit.sh -m "chore: bump version to 1.0.4"
```

---

## Self-Review

**Spec coverage:**
- Thumbnail max resolution: Tasks 1–3
- UI decode size: Task 3
- Search speed (debounce, min chars, cache, stale guard, suggestions): Tasks 4–7
- Download metadata gets better URL automatically via Task 2 (same `thumbnailUrl` field flows into `writeYouTubeTags`)

**Placeholder scan:** No TBD/TODO steps.

**Type consistency:** `selectBestThumbnailUrl`, `YouTubeSuggestionRepository.suggestions`, `YouTubeSearchUiState.suggestions`, `searchSuggestion()` used consistently across tasks.

**Skipped (YAGNI for v1):**
- Dedicated suggestions API response caching (suggestions are cheap; add LRU only if profiling shows need)
- `maxresdefault.jpg` probing (404-prone; `hqdefault` fallback is enough)
- Parallel suggestion + search on first launch (sequential debounce is simpler and sufficient)

## Expected Impact

| Change | User-visible effect |
|--------|---------------------|
| Best thumbnail URL + 240px decode | Sharper 72dp thumbs on high-DPI screens |
| 200ms debounce + min 2 chars | Fewer wasted searches, snappier feel |
| LRU cache (32 queries) | Instant repeat searches |
| Suggestion chips (150ms) | Text appears while typing, like SnapTube/YouTube |
| Request ID guard | No wrong results after fast typing |
