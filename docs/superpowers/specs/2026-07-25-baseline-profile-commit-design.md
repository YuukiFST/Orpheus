# Commit Generated Baseline Profiles — Design

Date: 2026-07-25  
Status: approved (approach A: generate + commit profiles only)  
Scope: produce Orpheus ART baseline/startup profiles and check them into the release source set so cold-start AOT covers app code, not only AndroidX/Compose library rules.

## Problem

`app/src/release/generated/baselineProfiles/` exists but is empty. Release builds still merge library ART profiles from dependencies, but critical Orpheus paths (Activity, Compose screens, player, Room) are not pre-profiled. Fresh install / sideload / local APK cold start stays more interpret/JIT for app code until Cloud Profiles or on-device usage catch up.

## Goals

1. Generate release baseline + startup profiles via the existing `:baselineprofile` module.
2. Persist non-empty files under `app/src/release/generated/baselineProfiles/`.
3. Commit those files so every release APK ships Orpheus rules (`dexLayoutOptimization` already enabled in `app/build.gradle.kts`).

## Non-goals

- CONTRIBUTING / README docs for regeneration.
- CI job that auto-generates profiles (needs a device; slow).
- Flipping `automaticGenerationDuringBuild` to `true`.
- Changing `BaselineProfileGenerator` flows, iterations, or UI selectors.
- Emulator UI automation beyond what Gradle `generateReleaseBaselineProfile` already drives.
- Measuring before/after `timeToInitialDisplay` in this change (optional follow-up via existing `StartupBenchmarks`).

## Constraints

- `:baselineprofile` sets `useConnectedDevices = true` — needs a connected device/emulator (API ≥ module `minSdk` 30).
- Repo rule: do not start emulator / drive adb UI unless the user explicitly authorizes it. User confirmed a device is already connected for this run.
- `automaticGenerationDuringBuild = false` stays false (local builds stay fast).
- Do not commit secrets or unrelated dirty tree files; only generated profile artifacts (and this spec).

## Design

### Generation

1. Confirm a device is visible to the Android SDK (`adb devices`, using the project/SDK `adb` if not on `PATH`).
2. Run:

```sh
./gradlew :app:generateReleaseBaselineProfile
```

3. Plugin runs instrumentation from `:baselineprofile`:
   - `generateStartupProfile` — cold start only (`includeInStartupProfile = true`).
   - `generateBaselineProfile` — broader UI path (home, library, search, player sheet; `includeInStartupProfile = false`).
4. Outputs land in `app/src/release/generated/baselineProfiles/` (`saveInSrc = true`).

### Verification

- Directory no longer empty; expected profile text files present (typically `baseline-prof.txt` and/or `startup-prof.txt` depending on AGP output layout).
- Spot-check that rules mention Orpheus packages (e.g. `com/yuukifst/orpheus`), not only `androidx/*`.
- Optional: rebuild release and confirm merged/combined ART profile intermediates grew vs library-only baseline.

### Commit

- Single commit containing only the generated files under `app/src/release/generated/baselineProfiles/`.
- Message style: chore/perf focused on why (ship AOT baseline for cold start).

## Failure handling

- No device / `adb` missing: stop; ask user to open device via `./scripts/celular.sh` (or authorize emulator) and re-run generation.
- Generator flaky UI step: capture shortest decisive Gradle/log line; fix only if a trivial selector issue blocks generation — otherwise report and pause (generator changes are out of scope unless required to finish).

## Testing

- Generation command succeeds (exit 0).
- Files committed and present in tree.
- No requirement to run emulator smoke or Macrobenchmark compare in this task; user may later run `StartupBenchmarks` manually.

## Out of scope follow-ups

- Document regeneration in CONTRIBUTING.
- CI managed device pool for periodic regen.
- Before/after startup benchmark numbers in PR description.
