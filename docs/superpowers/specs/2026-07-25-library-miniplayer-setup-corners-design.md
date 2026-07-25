# Library Rows + Mini Player Corners + Setup Insets — Design

Date: 2026-07-25  
Status: approved (user: proceed with recommended fixes; no more questions)  
Scope: three surgical UI bugfixes — Library song-row chrome, mini-player corners when NavBar hidden, Setup bottom-bar system inset

## Constraint (frontend.md HARD EXCEPTION)

Orpheus already has a Design System (`OrpheusMotion`, `OrpheusShapeSet`, `TerminalCornerShape`, terminal chrome).

- Do **not** invent a new aesthetic or greenfield frontend pipeline.
- Polish in place; song rows stay on existing list-item chrome; mini player keeps using `navBarCornerRadiusDp`.

## Goals

1. Library Liked/Favorites song rows no longer look like inflated NavBar-radius cards.
2. Mini player (bottom “now playing” bar) keeps Rounded corners on screens where the tab NavBar is hidden (e.g. Settings).
3. Setup Back/Next sit above the system navigation gesture/button zone so taps register.

## Non-goals

- Redesign Library list layout or `EnhancedSongListItem`.
- Change NavBar style modes (DEFAULT / FULL_WIDTH) when the tab bar is visible.
- New Settings toggles or preference migrations.

## Root causes

1. **Library:** `LibraryFavoritesTab` applies `customShape = RoundedCornerShape(navBarCornerRadius.dp)` from commit `b98a694`. That radius belongs on the NavBar / mini player, not song rows. Pre-regression visual used default list-item shape (`TerminalCornerShape` path inside `EnhancedSongListItem`).
2. **Mini player:** `SheetVisualState` forces `0.dp` top/bottom radius when `isNavBarHidden`. On Settings the tab bar is hidden, so the floating mini player becomes square despite Rounded + non-zero `navBarCornerRadius`. Original fork used non-zero radii when hidden; regression zeroed them.
3. **Setup:** `SetupBottomBar` pads only `14.dp` bottom and never calls `navigationBarsPadding()` (import already present, unused). Scaffold bottomBar can sit too close to system buttons.

## Design

### 1. Library song rows

- In `LibrarySongsAndFavoritesTabs.kt` (`LibraryFavoritesTab`):
  - Remove `navBarCornerRadius` / `cardShape` collection and `remember`.
  - Remove `customShape = cardShape` from all `LibraryPlaybackAwareSongItem` / skeleton call sites in that tab.
  - Drop unused imports (`sanitizeNavBarCornerRadius`, `RoundedCornerShape` if unused).
- `LibrarySongsTabPaginated` already omits `customShape` — leave it.
- Result: rows match Songs-tab / release-era list chrome again.

### 2. Mini player corners when NavBar hidden

- In `SheetVisualState.kt`, when `isNavBarHidden`:
  - `overallSheetTopCornerRadiusProvider` collapsed target → `navBarCornerRadiusDp` (not `0.dp`).
  - `playerContentActualBottomRadiusProvider` collapsed / idle branches → `navBarCornerRadiusDp` (not `0.dp`).
- When NavBar is visible, keep existing DEFAULT / FULL_WIDTH flush behavior unchanged.
- Prefer preference radius over hardcoded `32.dp` so Rounded + user slider stay consistent.

### 3. Setup bottom inset

- On `SetupBottomBar` root `Surface` (or inner `Column`): add `.navigationBarsPadding()`.
- Keep existing `padding(bottom = 14.dp)` as internal spacing above the inset.
- No motion / chrome redesign of the Setup bar.

## Testing

- Unit/compile: `compileDebugKotlin` (and any existing Sheet/Library unit tests if touched).
- Manual (user on device via `./scripts/celular.sh --install`):
  - Library → Liked: song rows look like old list items, not NavBar-radius pills.
  - Play a song → open Settings: mini player bottom corners match Rounded.
  - Fresh Setup: Back/Next clear of system nav; taps reliable.

## Out of scope follow-ups

- None required for this fix set.
