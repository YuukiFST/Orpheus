# Plan 010: Honor 6h interval on foreground library catch-up sync

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat 6e75b37..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt app/src/test/java/com/yuukifst/orpheus/data/worker/SyncWorkerRequestTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `6e75b37`, 2026-08-28

## Why this matters

`sync()` skips incremental WorkManager enqueue when `lastSyncTimestamp` is
newer than **6 hours**. Foreground catch-up (`ProcessLifecycleOwner.onStart`)
only has a **60 second** in-memory cooldown, then always enqueues
`incrementalSyncWork(runMaintenance = false)`. Cold start / app restore then
scans MediaStore + Room in the same second the first frame is trying to stay
smooth. Catch-up should use the same 6h library interval as startup sync,
while keeping the 60s flap guard and **not** touching MediaStore-change sync.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt`
  — `sync()`, `maybeRunForegroundCatchUpSync()`, companion constants.
- Tests: `app/src/test/java/com/yuukifst/orpheus/data/worker/SyncWorkerRequestTest.kt`
  — request builders only; **no** SyncManager policy tests yet. Match JUnit
  Jupiter style there.

Startup skip (reuse this interval for catch-up):

```235:254:app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt
    fun sync() {
        sharingScope.launch {
            val now = System.currentTimeMillis()
            val lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()
            val shouldRunSync =
                lastSyncTimestamp <= 0L || (now - lastSyncTimestamp) >= MIN_SYNC_INTERVAL_MS
            // ...
            enqueueSyncWork(
                request = SyncWorker.incrementalSyncWork(),
                policy = ExistingWorkPolicy.KEEP,
                notifyObserver = false
            )
        }
    }
```

Catch-up today (no 6h check):

```387:408:app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt
    private fun maybeRunForegroundCatchUpSync() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncTime < FOREGROUND_SYNC_COOLDOWN_MS) {
            Timber.tag(TAG).d("Skipping foreground catch-up sync (cooldown active)")
            return
        }
        sharingScope.launch {
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
                Timber.tag(TAG).d("Skipping foreground catch-up sync: initial setup not finished")
                return@launch
            }
            lastForegroundSyncTime = now
            Timber.tag(TAG).i("Foreground catch-up - scheduling local incremental sync")
            enqueueSyncWork(
                request = SyncWorker.incrementalSyncWork(runMaintenance = false),
                policy = ExistingWorkPolicy.KEEP,
                notifyObserver = false
            )
        }
    }
```

Constants:

```429:433:app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt
        private const val TAG = "SyncManager"
        private const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val MEDIASTORE_CHANGE_DEBOUNCE_MS = 1_500L
        private const val FOREGROUND_SYNC_COOLDOWN_MS = 60_000L
```

**Do not change** `runLocalAutoSyncAfterDebounce` — storage-change path must
still enqueue after debounce + setup. Pull-to-refresh / `incrementalSync()` /
`forceRefresh()` stay immediate.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| New policy tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.worker.SyncManagerPolicyTest"` | `BUILD SUCCESSFUL`, tests pass |
| Existing worker tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.worker.SyncWorkerRequestTest"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do **not** start the emulator or drive UI with `adb` unless the operator asks.

## Suggested executor toolkit

- If available: `verification-before-completion` before claiming DONE.

## Scope

**In scope**:

- `app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManager.kt`
- New `app/src/main/java/com/yuukifst/orpheus/data/worker/SyncManagerPolicy.kt`
  with `internal` function + constants used by SyncManager **or** `internal`
  functions in the same file as SyncManager if you prefer one file — then the
  test still lives under `data/worker`. Prefer a tiny `SyncManagerPolicy.kt` so
  tests do not construct `SyncManager` (needs WorkManager + Context).
- `app/src/test/java/com/yuukifst/orpheus/data/worker/SyncManagerPolicyTest.kt`

**Out of scope**:

- Changing `MIN_SYNC_INTERVAL_MS` duration (keep 6 hours).
- MediaStore observer debounce / `incrementalSync()` / full / rebuild.
- WorkManager unique-work names.
- YouTube playback / search plans.

## Git workflow

- Branch: `advisor/010-foreground-sync-honor-interval`
- Commits: e.g. `fix(sync): skip foreground catch-up inside the 6h library interval`
- Do **not** push or open a PR unless the operator asked.

## Steps

### Step 1: Pure policy function

`SyncManagerPolicy.kt`:

```kotlin
internal const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
internal const val FOREGROUND_SYNC_COOLDOWN_MS = 60_000L

internal fun shouldEnqueueForegroundCatchUp(
    nowMs: Long,
    lastForegroundSyncTimeMs: Long,
    lastLibrarySyncTimestampMs: Long,
    initialSetupDone: Boolean,
    cooldownMs: Long = FOREGROUND_SYNC_COOLDOWN_MS,
    minIntervalMs: Long = MIN_SYNC_INTERVAL_MS,
): Boolean {
    if (!initialSetupDone) return false
    if (nowMs - lastForegroundSyncTimeMs < cooldownMs) return false
    if (lastLibrarySyncTimestampMs > 0L &&
        nowMs - lastLibrarySyncTimestampMs < minIntervalMs
    ) {
        return false
    }
    return true
}
```

Semantics:

- `lastLibrarySyncTimestampMs <= 0` (never synced): **allow** catch-up after
  setup (same as `sync()` treating `<= 0` as must-run).
- Recent library sync within 6h: **deny**.
- Cooldown 60s: **deny** (also applied on the main-thread early return today).

Move `MIN_SYNC_INTERVAL_MS` / `FOREGROUND_SYNC_COOLDOWN_MS` off
`SyncManager.companion` to these `internal` constants so `sync()` and catch-up
cannot drift. Keep `MEDIASTORE_CHANGE_DEBOUNCE_MS` on SyncManager.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 2: Wire `maybeRunForegroundCatchUpSync`

Keep the cheap main-thread cooldown check (`now - lastForegroundSyncTime`).

Inside `sharingScope.launch`:

1. If setup not done → return (do **not** set `lastForegroundSyncTime`).
2. `lastSync = userPreferencesRepository.getLastSyncTimestamp()`.
3. If `!shouldEnqueueForegroundCatchUp(now, lastForegroundSyncTime, lastSync, true)`:
   - Still set `lastForegroundSyncTime = now` when the **only** failing reason
     is the 6h interval (setup already true, cooldown already passed on main).
     That preserves the 60s flap guard and avoids DataStore on every `onStart`.
   - Log skip like `sync()` (`Skipping foreground catch-up (last sync …s ago)`).
   - Return **without** `enqueueSyncWork`.
4. Else set `lastForegroundSyncTime = now` and enqueue as today
   (`incrementalSyncWork(runMaintenance = false)`, `KEEP`, `notifyObserver = false`).

Implementation tip: you can call the full `shouldEnqueueForegroundCatchUp`
again inside the launch (cooldown will pass). For the 6h skip, set
`lastForegroundSyncTime` before return.

`sync()` must use the shared `MIN_SYNC_INTERVAL_MS` import.

**Verify**: compileDebugKotlin → `BUILD SUCCESSFUL`

### Step 3: `SyncManagerPolicyTest`

JUnit Jupiter, match `SyncWorkerRequestTest` style. Cases:

| Case | Inputs | Expect |
|------|--------|--------|
| Setup incomplete | `initialSetupDone = false`, lastSync = 0 | `false` |
| Cooldown | setup true, `now - lastForeground = 30_000`, lastSync = 0 | `false` |
| Fresh install | setup true, lastForeground = 0, lastSync = 0 | `true` |
| Inside 6h | setup true, lastForeground = 0, lastSync = now - 1h | `false` |
| After 6h | setup true, lastForeground = 0, lastSync = now - 7h | `true` |
| Boundary | `now - lastSync == MIN_SYNC_INTERVAL_MS` | `true` (same as `>=` in `sync()`) |

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.worker.SyncManagerPolicyTest"`
→ pass

### Step 4: Regression + lint

**Verify**:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.worker.SyncWorkerRequestTest" --tests "com.yuukifst.orpheus.data.worker.SyncManagerPolicyTest"`
→ `BUILD SUCCESSFUL`

## Test plan

- New policy tests above (oracle).
- Do not instantiate `SyncManager` in unit tests (WorkManager).
- Reviewer: open app twice within 6h of a completed library sync → no extra
  incremental worker from catch-up; add a file on disk → MediaStore path still
  syncs.

## Done criteria

- [ ] Catch-up enqueue uses the same 6h rule as `sync()`.
- [ ] 60s cooldown still exists.
- [ ] `runLocalAutoSyncAfterDebounce` unchanged in behavior.
- [ ] `lastSyncTimestamp <= 0` still allows catch-up after setup.
- [ ] `SyncManagerPolicyTest` + `SyncWorkerRequestTest` pass.
- [ ] No files outside in-scope list.
- [ ] `plans/README.md` row 010 status `DONE`.

## STOP conditions

- Product intent is "catch-up every foreground because ContentObserver is
  foreground-only" **without** 6h — that would contradict this plan; report
  and do not ship a silent 6h skip if a new comment in `SyncManager` (added
  after `6e75b37`) says catch-up must always run. As of `6e75b37` the 6h skip
  on `sync()` is the intended library interval; catch-up was an oversight.
- `getLastSyncTimestamp()` cannot be called from `sharingScope` (it can;
  `sync()` already does).

## Maintenance notes

- First install: setup screen must still skip catch-up (`initialSetupDone`).
- After first successful sync, timestamp > 0; catch-up waits 6h unless
  MediaStore fires.
- Do not apply 6h to `forceRefresh` / pull-to-refresh.
