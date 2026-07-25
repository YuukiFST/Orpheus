# Rounded Default + Setup Motion + Library Nav — Design

Date: 2026-07-25  
Status: approved for planning (user: proceed; bugs first)  
Scope: three bug fixes + ship — navbar/corners default Rounded, Setup Back/Next motion parity, Library tab sometimes stuck; then PR / merge / release as YuukiFST

## Constraint (frontend.md HARD EXCEPTION)

Orpheus already has a Design System (`OrpheusMotion`, `OrpheusShapeSet`, `TerminalCornerShape`, terminal chrome).

- Do **not** invent a new aesthetic or greenfield motion system.
- Do polish in place with existing tokens (`OrpheusMotion.DurationFast` / `DurationQuick`, `DistanceBase` = 8.dp, `EaseSmoothOut`).

## Goals

1. App default is **Rounded** (`useSmoothCorners = true`); navbar corners visibly rounded out of the box.
2. When Rounded is ON and `navBarCornerRadius` is still `0` (legacy installs), promote radius to `28` (`DEFAULT_NAV_BAR_CORNER_RADIUS`).
3. Setup wizard Back and Next (and other forward/back arrow pairs in that bar) share the same directional icon motion — no spinning FAB / rotating container.
4. Library bottom-nav tap reliably navigates even when lifecycle/graph was briefly not ready.
5. Ship: PR → merge → release bump after `1.0.21` → `1.0.22`, author YuukiFST `<faustoyuuki@gmail.com>`.

## Non-goals

- Full Setup page redesign (Phase 5 polish).
- Changing Square mode behavior when user explicitly chooses Square.
- Reworking all app-wide IconButtons outside Setup bottom bar + shared top-level nav helpers in this change.
- Emulator / adb UI driving (`CLAUDE.md`).

---

## 1. Corners / default Rounded

### Root cause

- `USE_SMOOTH_CORNERS` DataStore default is `false` → Square shape set.
- `NAV_BAR_CORNER_RADIUS` DataStore default is `0`.
- Navbar clip uses `navBarCornerRadius` independently of the Rounded/Square toggle. Rounded ON + radius `0` ⇒ square tips (screenshot bug).

### Target

| Surface | Behavior |
|---------|----------|
| New installs | `useSmoothCorners = true`, `navBarCornerRadius = 28` |
| Legacy: Rounded ON + radius `0` | One-shot write radius `28` |
| Explicit Square | Keep square shapes; do **not** force radius |
| Switching settings to Rounded while radius `0` | Also set radius `28` |

### Touch points

- `UserPreferencesRepository`: defaults `?: true` / `?: 28`; migration helper or inline in flows / `setUseSmoothCorners`.
- `Shape.kt`: `OrpheusActiveShapes` / `LocalOrpheusShapes` cold default → `OrpheusShapeSets.Rounded`.
- `OrpheusTheme(useSmoothCorners: Boolean = true)`.
- `PlayerViewModel` / similar `stateIn` initial values aligned (`true`, `28`).
- Unit tests in `UserPreferencesRepositoryTest` for defaults + migration.

---

## 2. Setup Back / Next motion parity

### Root cause

Historical: Next FAB spun `currentPage * 360f`; Back was plain. Spec `2026-07-24-setup-playback-dismiss-design.md` §3 already targeted shared slide+fade; verify workspace matches and finish any leftover asymmetry (container rotation, unused morph, mismatched transition specs).

### Target motion (Approach A — keep)

- Shared recipe: horizontal slide ±`OrpheusMotion` distance base (8.dp) + fade; open `DurationFast` + `EaseSmoothOut`, exit `DurationQuick`.
- Direction mirrors pager: forward = exit left / enter from right; back = opposite.
- Back = `IconButton` + same `AnimatedContent` transitionSpec as Next icon.
- Next/Finish keep `MediumExtendedFloatingActionButton` **without** container `rotate`.
- No ad-hoc 900ms / 360° spins.

### Touch points

- `SetupScreen.kt` → `SetupBottomBar` only for this bug (dropdown chevron rotate elsewhere is expand UI, out of scope unless it is the reported “avançar” control — it is not).

---

## 3. Library tab stuck

### Root cause (hypotheses confirmed in code)

1. `navigateToTopLevelSafely` returns `false` when `graph.startDestinationId` is unavailable — click swallowed, no fallback.
2. Bottom items use `enabled = currentRoute != null`; brief null route disables all tabs.
3. No lifecycle readiness gate / retry (unlike `navigateSafely`).

### Target

- `navigateToTopLevelSafely`: if start id missing, fall back to `navigateSafely(route)` (or equivalent singleTop navigate).
- Gate on `isReadyForNavigation()`; if not ready, return `false` only after attempting safe path — callers may no-op once, but prefer retry on next tap (already user-driven).
- Keep Search double-tap behavior unchanged.
- Prefer enabling tabs when graph exists even if `currentRoute` briefly null (use last non-null route for selection only).

### Touch points

- `NavControllerExtensions.kt` → `navigateToTopLevelSafely`
- `PlayerInternalNavigationBar.kt` → click / enabled wiring
- Unit test if NavController helpers are testable; otherwise logic-level test / compile verification

---

## 4. Release

After fixes green:

1. Commit(s) on feature branch.
2. PR → merge to `main`.
3. Bump `APP_VERSION_NAME` / `APP_VERSION_CODE` → **1.0.22** / **23**.
4. CHANGELOG Unreleased → 1.0.22.
5. Tag `v1.0.22`, push, GitHub release per `docs/RELEASE.md`.
6. Commits/tags with author **YuukiFST `<faustoyuuki@gmail.com>`**.

---

## 5. Testing / verification

- Unit: defaults + Rounded+radius0 migration; radius clamp still works.
- Compile: `:app:compileDebugKotlin`
- Unit suite for touched prefs / nav if present.
- Manual (user device): Rounded tips on navbar; Setup Next/Back same slide; spam Library from Search/Downloads/Settings.

## Error handling

- Migration idempotent: radius already >0 untouched.
- Square mode never auto-writes radius.
- Nav failure returns false; next user tap retries (no infinite loop).
