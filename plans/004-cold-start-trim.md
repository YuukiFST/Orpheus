# Plan 004: Trim cold start (defer non-first-frame work)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 85bcada..HEAD -- app/src/main/java/com/yuukifst/orpheus/MainActivity.kt app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt app/src/main/java/com/yuukifst/orpheus/OrpheusApplication.kt CLAUDE.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `85bcada`, 2026-07-26

## Why this matters

Cold start has already been optimized once: `Application.onCreate` does no
blocking work, the splash screen is released immediately, `dagger.Lazy` is used
correctly, the MediaController bind is deferred to `onMainActivityStart()`, and
baseline + startup profiles are committed. What remains is concentrated in one
place: **`PlayerViewModel`'s constructor and `init` block**, which is materialized
during the first composition and drags in the whole `@Singleton` graph (Room,
two OkHttp clients, Retrofit, WorkManager, 12+ state holders) plus a long list of
eagerly-started flows and DataStore reads before the first frame can settle.

This plan removes the parts of that work that are provably not needed for the
first frame. It is deliberately conservative: no architectural refactor, no
Paging migration, no `PlayerViewModel` split.

## Current state

### Application: already clean — do not "fix" it

```82:121:app/src/main/java/com/yuukifst/orpheus/OrpheusApplication.kt
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        startupScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Orpheus Music Playback",
                    NotificationManager.IMPORTANCE_LOW,
                )
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
            runCatching { userPreferencesRepository.get().refreshStartupMirrorFromDataStore() }
            youTubeInitializer.get().ensureInitialized()
```

The heavy work is on `startupScope` (IO) and the injections are `dagger.Lazy`.
This file is **out of scope** except for step 1's ordering fix.

### MainActivity: three non-lazy field injections

```171:179:app/src/main/java/com/yuukifst/orpheus/MainActivity.kt
    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var syncManager: SyncManager
```

`by viewModels()` is a lazy delegate, so `PlayerViewModel` is constructed at first
*access*, which is inside `setContent` (`MainAppContent(playerViewModel, mainViewModel)`
at line 312) — during the first composition, not in `onCreate`. The three `@Inject`
fields, however, are populated eagerly by Hilt during `super.onCreate()`.
`SyncManager`'s constructor calls `WorkManager.getInstance(context)`.

`syncManager` is only used in `onCreate` inside the benchmark-only branch
(`MainActivity.kt:218-226`) and in `onStart`; check every usage with
`rg -n "syncManager" app/src/main/java/com/yuukifst/orpheus/MainActivity.kt`.

### PlayerViewModel: eager flows created at construction

```1165:1170:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
```

`initialValue = true` means `isSyncingLibrary` starts `true`, which gates the
initial library load in the `init` block and can show the sync overlay on a frame
where nothing is actually syncing:

```1809:1825:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
            viewModelScope.launch {
                isSyncingStateFlow.collect { isSyncing ->
                    val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                    _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                    if (oldSyncingLibraryState && !isSyncing) {
                        Timber.tag("PlayerViewModel").i("Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                        resetAndLoadInitialData("isSyncingStateFlow observer")
                    }
                }
            }

            viewModelScope.launch {
                if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                    Timber.tag("PlayerViewModel").i("Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                    resetAndLoadInitialData("Initial Check")
                }
            }
```

Several `stateIn`ed Room flows are also constructed as properties, i.e. at
ViewModel construction time, even though nothing on the first frame reads them:

```1186:1203:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
    val paletteRegenerationTargets: StateFlow<List<Song>> = musicRepository.getDistinctAlbumArtSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = musicRepository.getHomeMixPreviewSongs(
        limit = HOME_MIX_PREVIEW_LIMIT
    ).map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )
```

`WhileSubscribed` means the *upstream* only runs once someone collects, so these
are cheap-ish; the cost is object graph construction, not queries. Do not
"optimize" them into `by lazy` unless you can show the construction itself is hot
— see step 4, which is explicitly a measurement-first step.

### PlayerViewModel init: two one-shot DataStore reads and a legacy migration

```1663:1675:app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt
            viewModelScope.launch {
                val legacyFavoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
                if (legacyFavoriteIds.isNotEmpty()) {
                    val roomFavoriteIds = musicRepository.getFavoriteSongIdsOnce()
                    if (roomFavoriteIds.isEmpty()) {
                        legacyFavoriteIds.forEach { songId ->
                            musicRepository.setFavoriteStatus(songId, true)
                        }
                    }
                    userPreferencesRepository.clearFavoriteSongIds()
                }
            }
```

This is a one-time migration from a long-past version, but it runs a DataStore
read plus a Room read on **every** cold start forever.

### Sync competes with the first frame

```258:265:app/src/main/java/com/yuukifst/orpheus/MainActivity.kt
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                    withFrameNanos { }
                    LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                    mainViewModel.startSync()
                }
            }
```

`withFrameNanos { }` waits exactly one frame. `SyncManager` also enqueues a
foreground catch-up sync from its own `ProcessLifecycleOwner` observer, so on a
cold start two sync paths can fire around the first frames.

### CLAUDE.md contains a stale claim

`CLAUDE.md` says baseline profiles are pending and
`app/src/release/generated/baselineProfiles/` is empty. It is not:
`baseline-prof.txt` is 40,082 lines and `startup-prof.txt` is 35,016 lines, and
`docs/BASELINE_PROFILES.md` already records the work as done.

### Repo conventions that apply

- Timber for logging.
- UI-gating flags must be mirrored in synchronous `SharedPreferences`, never read
  from DataStore on the first-frame path (`CLAUDE.md`).
- `dagger.Lazy` only helps if you do not call `.get()` at startup (`CLAUDE.md`).
- JUnit Jupiter for tests.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest` | exit 0, all pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | exit 0 |

**Do NOT** start an emulator, run `adb`, install the APK, run the macrobenchmarks,
or drive the UI (`CLAUDE.md` hard rule — macrobenchmark and baseline-profile
generation require a connected device and are the maintainer's call). The
macrobenchmark suite that *would* measure this lives in
`baselineprofile/src/main/java/com/yuukifst/orpheus/baselineprofile/StartupBenchmarks.kt`;
reference it in your report as the way the maintainer can verify, but do not run it.

## Scope

**In scope**:
- `app/src/main/java/com/yuukifst/orpheus/MainActivity.kt`
- `app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt`
- `app/src/main/java/com/yuukifst/orpheus/data/preferences/UserPreferencesRepository.kt` — only to add
  a one-time migration flag if step 3 needs it
- `CLAUDE.md` — only the stale baseline-profile paragraph

**Out of scope** (do NOT touch):
- `OrpheusApplication.kt` — already correct; changing it risks regressing the
  documented startup rules.
- `AppModule.kt` — the `@Singleton` graph shape is a bigger decision than this plan.
- `SyncManager.kt` / `SyncWorker` — sync scheduling policy is its own concern, and
  breaking the first-install path is a much worse outcome than a slower start.
- `LibraryScreen.kt`, `UnifiedPlayerSheetV2.kt` — reducing their collector count is
  a real opportunity but needs its own plan with careful slice design.
- Any Room migration or query change.
- `baselineprofile/` module and the generated profile files.

## Git workflow

- Branch: `advisor/004-cold-start-trim`
- Commit per step; short imperative subject, no `Co-Authored-By:` trailer.
- Do NOT push or open a PR unless the operator asks.

## Steps

### Step 1: Make `SyncManager` lazy in `MainActivity`

Change `@Inject lateinit var syncManager: SyncManager` to
`@Inject lateinit var syncManager: dagger.Lazy<SyncManager>` and update every use
site to `syncManager.get()`. Find them with
`rg -n "syncManager" app/src/main/java/com/yuukifst/orpheus/MainActivity.kt`.

This keeps `WorkManager.getInstance` off the activity-creation path; it will still
be constructed from `OrpheusApplication`'s IO `startupScope` (`OrpheusApplication.kt:112`),
which is where it belongs.

Do the same evaluation for `userPreferencesRepository` and
`themePreferencesRepository`: they are read inside `setContent`
(`MainActivity.kt:230-232`), so they are needed for the first composition anyway —
**leave those two alone** unless the only uses you find are inside coroutines.

**Verify**:
- `rg -n "lateinit var syncManager" app/src/main/java/com/yuukifst/orpheus/MainActivity.kt` shows `dagger.Lazy<SyncManager>`
- `:app:compileDebugKotlin` → exit 0

### Step 2: Fix the `isSyncing` initial value so the first frame is not pessimistic

`isSyncingStateFlow` starts as `true`, which (a) shows the sync overlay for
libraries that are not syncing and (b) defers `resetAndLoadInitialData` until
WorkManager reports idle.

Change `initialValue = true` to a synchronous, correct-by-construction value:

- If `UserPreferencesRepository` already mirrors a "sync in progress" or
  "last sync timestamp" flag into `SharedPreferences` (search with
  `rg -n "readInitialSetupDoneSync|startup_mirror|orpheus_startup_mirror" app/src/main/java/com/yuukifst/orpheus/data/preferences/UserPreferencesRepository.kt`),
  use the same synchronous mirror mechanism to seed `initialValue`.
- If no such mirror exists, change `initialValue` to `false` **and** verify the
  `init`-block consequence: with `false`, the second `viewModelScope.launch` at
  `PlayerViewModel.kt:1822-1827` will now call `resetAndLoadInitialData("Initial Check")`
  on cold start when the library is empty, which is the desired behavior (it is
  what makes the library appear). Confirm that `resetAndLoadInitialData` is
  idempotent — read it and the `_isInitialDataLoaded` guard before deciding. If it
  is not idempotent, STOP and report.

Do **not** simply delete the observer at lines 1809-1820; the
"sync finished → reload" transition still has to work.

**Verify**:
- `rg -n "initialValue = true" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt` → no match on the `isSyncingStateFlow` declaration
- `:app:compileDebugKotlin` → exit 0
- `:app:testDebugUnitTest` → exit 0

### Step 3: Gate the legacy favorites migration behind a one-time flag

The migration at `PlayerViewModel.kt:1663-1675` performs a DataStore read plus a
Room read on every cold start. Add a boolean preference (e.g.
`legacyFavoritesMigrationDone`) to `UserPreferencesRepository`, mirrored in the
existing synchronous startup `SharedPreferences` if that mechanism is available so
the check itself costs nothing, and skip the whole block when it is set. Set it
after a successful migration, and also set it when `legacyFavoriteIds` comes back
empty (nothing to migrate ever again).

Follow the existing preference-declaration style in `UserPreferencesRepository`
(same `booleanPreferencesKey` + flow + setter shape as its neighbors).

**Verify**:
- `rg -n "legacyFavoritesMigrationDone" app/src/main/java` → declaration, setter, and the guard in `PlayerViewModel`
- `:app:compileDebugKotlin` → exit 0
- `:app:testDebugUnitTest` → exit 0

### Step 4: Move only provably-unneeded eager flows behind `by lazy` — measurement first

For each of `paletteRegenerationTargets`, `homeMixPreviewSongs`, `songCountFlow`
and `hasCloudSongsFlow` (`PlayerViewModel.kt:1186-1210`):

1. Find every consumer: `rg -n "paletteRegenerationTargets|homeMixPreviewSongs|songCountFlow|hasCloudSongsFlow" app/src/main/java`.
2. If a property has **no** consumer on the start destination's composable tree
   (the start destination is resolved in `AppNavigation.kt:76-82` and defaults to
   `Screen.Library.route`; `Screen.Home.route` immediately redirects to Library at
   `AppNavigation.kt:115-118`), convert it to `by lazy { ... }` so the flow object
   is not built during ViewModel construction.
3. If a property **is** read on the first frame, leave it exactly as it is.
4. Do not change any `SharingStarted` value. `WhileSubscribed(5000)` is load-bearing
   for the app's subscription lifetimes.

Record in your report which properties you converted and which consumers you found.
If a property is read from Compose via `collectAsStateWithLifecycle` in a composable
that *might* be on the first frame and you cannot tell, leave it alone and say so —
a wrong call here trades a small win for a behavioral regression.

**Verify**:
- `:app:compileDebugKotlin` → exit 0
- `:app:testDebugUnitTest` → exit 0
- `rg -n "SharingStarted" app/src/main/java/com/yuukifst/orpheus/presentation/viewmodel/PlayerViewModel.kt | wc -l` — the count must be unchanged from before your edit

### Step 5: Correct the stale baseline-profile claim in CLAUDE.md

Replace the bullet that says baseline profiles are pending and the generated
directory is empty with an accurate statement: profiles are generated and
committed under `app/src/release/generated/baselineProfiles/`
(`baseline-prof.txt`, `startup-prof.txt`), regeneration requires a connected
device, and the procedure is in `docs/BASELINE_PROFILES.md`. Keep the existing
"do not start an emulator or run profile generation unless the user asks"
sentence — it is still a rule.

Verify the file sizes yourself before writing the sentence:
`wc -l app/src/release/generated/baselineProfiles/*`

**Verify**: `rg -n "Baseline profiles are pending" CLAUDE.md` → no matches.

### Step 6: Full verification

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin`
2. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest`
3. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug`

All exit 0.

## Test plan

- No new unit tests are required for steps 1, 4 and 5 (dependency laziness and doc
  text are not unit-testable here).
- Step 3 introduces a preference. If `UserPreferencesRepository` has existing unit
  tests (check `rg -l "UserPreferencesRepository" app/src/test`), add a case in the
  same style asserting the new flag defaults to `false` and round-trips through the
  setter. If there are no such tests, do not create a new test infrastructure for
  it — note the gap in your report instead.
- Existing tests that must keep passing: everything under `app/src/test`, notably
  `PlayerViewModelTest`.
- Verification: `:app:testDebugUnitTest` → all pass.

## Done criteria

ALL must hold:

- [ ] `:app:compileDebugKotlin` exits 0
- [ ] `:app:testDebugUnitTest` exits 0
- [ ] `:app:lintDebug` exits 0
- [ ] `MainActivity.syncManager` is a `dagger.Lazy<SyncManager>`
- [ ] `isSyncingStateFlow` no longer uses `initialValue = true`
- [ ] The legacy favorites migration is guarded by a persisted flag
- [ ] `rg -n "Baseline profiles are pending" CLAUDE.md` → no matches
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row for 004 updated, including a note listing which
      step-4 properties were converted and which were left alone

## STOP conditions

Stop and report back (do not improvise) if:

- `resetAndLoadInitialData` turns out not to be idempotent, making step 2's
  `initialValue = false` risky (duplicate library loads).
- Making `syncManager` lazy breaks the benchmark branch at `MainActivity.kt:218-226`
  in a way that is not a mechanical `.get()` change.
- The synchronous startup `SharedPreferences` mirror does not exist or is not
  accessible from where steps 2 and 3 need it. Do **not** add a DataStore read to
  the first-frame path as a fallback (`CLAUDE.md` rule); report instead.
- Any change makes the library appear empty on cold start in a way you can see
  from the code (e.g. `_isInitialDataLoaded` never flips).
- You conclude the remaining startup cost is dominated by the `@Singleton` graph or
  by the ~50 `collectAsStateWithLifecycle` collectors on the first frame. Both are
  real but out of scope here — report them as candidates for a follow-up plan
  rather than starting a refactor.

## Maintenance notes

- The rule this plan defends: **nothing may be added to `PlayerViewModel`'s
  constructor or `init` block that the first frame does not need.** A reviewer
  should push back on new `stateIn(..., Eagerly)` properties and new
  `userPreferencesRepository.<flow>.first()` calls in `init`.
- Startup regressions are invisible without measurement. The macrobenchmark exists
  (`baselineprofile/.../StartupBenchmarks.kt`); ask the maintainer to run it
  before and after, on device, rather than guessing.
- Known remaining opportunities, deliberately deferred: splitting `PlayerViewModel`
  (a ~4.6k-line God Object with 15 state holders), reducing the collector count on
  `LibraryScreen` / `UnifiedPlayerSheetV2`, and moving the unbounded
  `getAllSongs()` observer behind Paging. Each deserves its own plan with its own
  risk budget.
- If a future change re-adds `profileable` to the release manifest for on-device
  profiling, remember it is currently only in the `benchmark` manifest — that is
  intentional.
