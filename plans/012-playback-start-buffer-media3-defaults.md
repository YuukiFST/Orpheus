# Plan 012: Start playback after 1 s of buffer, not 2.5 s

> **Executor instructions**: Follow this plan step by step.
> Run every verification command and confirm the expected result before moving to the next step.
> If anything in the "STOP conditions" section occurs, stop and report; do not improvise.
> When done, update the status row for this plan in `plans/README.md`, unless a reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**:
> `git diff --stat b8d4cc1..HEAD -- app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt gradle/libs.versions.toml`
> If either file changed since this plan was written, compare the "Current state" excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: perf
- **Planned at**: commit `b8d4cc1`, 2026-11-25

## Why this matters

`DualPlayerEngine.buildAdaptiveLoadControl()` tells ExoPlayer to hold audio until 2 500 ms of media is buffered, on every play, seek and skip.
Its KDoc says 2 500 ms is "ExoPlayer's documented default"; that is stale.
In the Media3 version this repo ships (1.10.1) the defaults are 1 000 ms to start and 2 000 ms after a rebuffer.
For a YouTube stream on a slow or throttled connection, the extra 1.5 s of required buffer is extra silence after every tap.
After this plan the engine uses the current Media3 thresholds; buffer ceilings (`minBufferMs`/`maxBufferMs`) stay as they are.

## Current state

- `app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`: builds both ExoPlayer instances. `buildAdaptiveLoadControl()` is the only function this plan changes.
- `gradle/libs.versions.toml:35`: `media3Session = "1.10.1"` (the Media3 version in use).

`DualPlayerEngine.kt:820-860` today:

```kotlin
    /**
     * Returns a [DefaultLoadControl] tuned to the device's RAM tier.
     *
     * Low-RAM devices ([ActivityManager.isLowRamDevice]) receive halved buffer ceilings
     * to prevent memory pressure when both players co-exist during a crossfade.
     * [bufferForPlaybackMs] is set to ExoPlayer's documented default of 2 500 ms on both
     * tiers — the previous value of 5 000 ms doubled first-audio latency with no benefit.
     */
    private fun buildAdaptiveLoadControl(): DefaultLoadControl {
        val isLowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .isLowRamDevice
        // setPrioritizeTimeOverSizeThresholds(true): ... (long comment, keep it)
        return if (isLowRam) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 15_000,
                    /* maxBufferMs                      */ 30_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs                      */ 30_000,
                    /* maxBufferMs                      */ 60_000,
                    /* bufferForPlaybackMs              */  2_500,
                    /* bufferForPlaybackAfterRebufferMs */  5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }
```

Facts verified against the Media3 `1.10.1` source (`libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java`):

- `DEFAULT_BUFFER_FOR_PLAYBACK_MS = 1000`
- `DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2000`
- `Builder.setBufferDurationsMs(...)` applies the four values to both streaming and local playback.

The class already has a private `companion object` with constants (`DualPlayerEngine.kt:180-194`), for example:

```kotlin
    private companion object {
        private const val AUDIO_OFFLOAD_STALL_FALLBACK_MS = 4_000L
        ...
        private const val YOUTUBE_AUDIO_CACHE_BYTES = 256L * 1024 * 1024
    }
```

Conventions: constants live in that companion with a short comment that says why. Kotlin code style is `official`.

## Commands you will need

Commands come from `CONTRIBUTING.md`. The advisor did not run them (no JDK 21 or Android SDK on the advisor's machine).
Point `JAVA_HOME` at a JDK 21 on your machine; on Windows use `gradlew.bat`.

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Compile | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| Engine unit tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` | `BUILD SUCCESSFUL`, tests pass |
| Lint | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug` | `BUILD SUCCESSFUL` |

Do not start an emulator or drive UI with `adb` unless the operator asks.

## Scope

**In scope** (the only file you modify):

- `app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt`: the companion object (two new constants) and `buildAdaptiveLoadControl()` with its KDoc.

**Out of scope** (do not touch):

- `minBufferMs` and `maxBufferMs` on either RAM tier. They bound memory during crossfade.
- `setPrioritizeTimeOverSizeThresholds(true)` and its comment. It is required for lossless files.
- `buildPlayer()` and everything else in the file. Plan 014 edits `buildPlayer`'s callers; keep the diffs separate.
- The literal `5_000L` at `DualPlayerEngine.kt:780` (`playerA.currentPosition > 5_000L`). It is a position guard, not a buffer value.

## Git workflow

- Branch: `perf/012-playback-start-buffer-media3-defaults`
- One commit. Conventional Commits, matching history such as `perf(ui): paint the saved theme on the first frame`.
  Suggested: `perf(player): start playback after 1s of buffer`
- No `Co-Authored-By:` trailer and no tool attribution in the message (`CONTRIBUTING.md`).
- Do not push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add the two constants

In the `private companion object` of `DualPlayerEngine`, add:

```kotlin
        // Media3 1.10.1 defaults (DefaultLoadControl). The earlier 2 500 / 5 000 ms came from
        // an older ExoPlayer default and held audio back on every play, seek and skip.
        private const val BUFFER_FOR_PLAYBACK_MS = 1_000
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000
```

**Verify**: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

### Step 2: Use them on both RAM tiers and fix the KDoc

In `buildAdaptiveLoadControl()`, replace `2_500` with `BUFFER_FOR_PLAYBACK_MS` and the buffer-threshold `5_000` with `BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS`, in both branches.
Rewrite the last KDoc sentence so it no longer claims 2 500 ms is the default.
Target KDoc ending:

```kotlin
     * Start and rebuffer thresholds follow the Media3 defaults on both tiers
     * ([BUFFER_FOR_PLAYBACK_MS], [BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS]).
```

**Verify**: `grep -n "2_500" app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt` → no matches
**Verify**: `grep -c "BUFFER_FOR_PLAYBACK_MS\b" app/src/main/java/com/yuukifst/orpheus/data/service/player/DualPlayerEngine.kt` → `4` or more (1 declaration, 2 uses, 1 KDoc reference)

### Step 3: Full gate

**Verify**: `./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` → `BUILD SUCCESSFUL`

## Test plan

- No new automated test.
  The values are player configuration; the user-visible effect is latency, which has no deterministic end-to-end reproduction without a device, and the owner's standard is end-to-end tests only.
- Existing suites must stay green: `com.yuukifst.orpheus.data.service.player.*`.
- Manual check for the owner on a device (list it in your report, do not run it yourself):
  1. Tap a YouTube track on a slow network: audio starts sooner than before, without repeated rebuffering.
  2. Seek inside a YouTube track: playback resumes sooner.
  3. Play a large FLAC file and a crossfade between two local tracks: no new stutter.

## Done criteria

- [ ] `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.yuukifst.orpheus.data.service.player.*"` → `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:lintDebug` → `BUILD SUCCESSFUL`
- [ ] `grep -n "2_500" .../DualPlayerEngine.kt` returns no matches
- [ ] `minBufferMs` / `maxBufferMs` literals unchanged: `grep -n "15_000\|30_000\|60_000" .../DualPlayerEngine.kt` still shows the four ceiling lines
- [ ] `git status --short` lists only `DualPlayerEngine.kt` and `plans/README.md`
- [ ] `plans/README.md` row 012 status is `DONE`

## STOP conditions

Stop and report back if:

- `buildAdaptiveLoadControl()` does not match the excerpt above (drift).
- `gradle/libs.versions.toml` no longer pins Media3 at `1.10.1`; re-check the defaults of the pinned version before choosing values.
- The build fails with a `DefaultLoadControl` argument check (for example a threshold larger than `minBufferMs`).
- A verification fails twice after a reasonable fix attempt.

## Maintenance notes

- A lower start threshold trades a little rebuffer safety for latency; that is why the after-rebuffer value stays at twice the start value.
- If users on very poor networks report stutter right after start, raise only `BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS`.
- `DefaultLoadControl.Builder` also has `setBufferDurationsMsForStreaming` and `setBufferDurationsMsForLocalPlayback` if the two cases ever need different values.
