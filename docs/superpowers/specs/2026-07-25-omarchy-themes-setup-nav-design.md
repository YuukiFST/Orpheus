# Omarchy Themes + Sakura + Setup Arrow + Navbar Corners — Design

Date: 2026-07-25  
Status: approved for planning  
Scope: Omarchy-derived themes + Sakura (Settings-only), ThemePersonality (bounded “vivo”), Setup next-arrow chrome fix, navbar tip square when Rounded — then plan / implement

## Constraint (frontend.md HARD EXCEPTION)

Orpheus already has a Design System (`OrpheusMotion`, `OrpheusShapeSet`, `LocalTerminalChrome`, Pixel soft chrome, Terminal typography).

- **Do not** run the greenfield frontend.md skill pipeline.
- **Do** extend Orpheus tokens/schemes/chrome in place.
- Colorful themes use soft chrome like Pixel; Light/Dark stay mono terminal (user Visual Style Square/Rounded).

## Goals

1. Add **Ethereal**, **Rose Pine**, and **Catppuccin Mocha** as app theme modes from [Omarchy](https://omarchytheme.com/) palettes (exact page hex).
2. Add **Sakura** as a creative pink Settings-only theme (not Omarchy).
3. New themes appear only in **Settings → Appearance**; they must **not** appear in Setup theme picker.
4. Add bounded **ThemePersonality** so colorful themes feel distinct (shapes + motion recipes on shared chrome) without redesigning Library/Player layouts.
5. Fix Setup bottom-bar **Next** control: remove square chrome (FAB + outline) so it matches Back (`IconButton` family).
6. Fix navbar tips staying square when Visual Style is **Rounded** (radius `0` / promo gaps).

## Non-goals

- Changing player album-art / dynamic theme prefs (`ThemePreference`).
- Follow System selecting colorful themes (Follow System stays OS mono Light/Dark only, same as Pixel).
- Adding colorful themes to Setup, or a separate “Dawn vs Main” Rose Pine picker (use Omarchy page palette as-is: Rose Pine = light `#faf4ed`).
- Per-theme full layout redesign of Library / Player / Search (phase later if wanted).
- Release bump / ship (unless requested after implementation).
- Emulator / adb UI driving (`CLAUDE.md`).

---

## 1. Themes (Settings-only)

### Preference model

Extend `AppThemeMode` with:

| Mode | Storage key | Appearance |
|------|-------------|------------|
| Ethereal | `ethereal` | Dark navy `#060B1E`, fg `#ffcead`, accent `#7d82d9` |
| Rose Pine | `rose_pine` | Light `#faf4ed`, fg `#575279`, accent `#56949f` (Omarchy = Dawn-like) |
| Catppuccin Mocha | `catppuccin_mocha` | Dark `#11111B`, fg `#CDD6F4`, accent `#F5C2E7` |
| Sakura | `sakura` | Light cream-pink `#FFF5F7`, fg `#5C2A3A`, accent `#E85A8C` |

Existing modes unchanged: `LIGHT`, `DARK`, `PIXEL`, `FOLLOW_SYSTEM`.

### Resolution

Extend `AppThemeScheme` with `ETHEREAL`, `ROSE_PINE`, `CATPPUCCIN_MOCHA`, `SAKURA`.

```
when (appThemeMode) {
  LIGHT -> darkTheme=false, scheme=LIGHT
  DARK -> darkTheme=true, scheme=DARK
  PIXEL -> darkTheme=true, scheme=PIXEL
  ETHEREAL -> darkTheme=true, scheme=ETHEREAL
  ROSE_PINE -> darkTheme=false, scheme=ROSE_PINE
  CATPPUCCIN_MOCHA -> darkTheme=true, scheme=CATPPUCCIN_MOCHA
  SAKURA -> darkTheme=false, scheme=SAKURA
  FOLLOW_SYSTEM -> OS light/dark → mono LIGHT|DARK only
  else / legacy "terminal" -> DARK
}
```

### Color schemes + chrome

- Build Material 3 `ColorScheme`s from Omarchy palette roles for the three Omarchy themes (background, foreground, accent, color0–15 as supporting containers / error / secondary / tertiary). Exact hex from the Omarchy pages.
- Sakura (creative):

| Role | Hex |
|------|-----|
| background | `#FFF5F7` |
| surface / surfaceContainer | `#FFE8EE` |
| surfaceContainerHigh | `#FFD6E0` |
| onBackground / onSurface | `#5C2A3A` |
| primary / primaryContainer | `#E85A8C` / `#FFB7C5` |
| onPrimary | `#FFFFFF` |
| secondary | `#F2A0B8` |
| tertiary | `#C45C7A` |
| error | `#D64545` |
| outline | `#E8A0B5` |

- Soft chrome: `LocalTerminalChrome = false` for Pixel + Ethereal + Rose Pine + Catppuccin Mocha + Sakura.
- Shapes: soft sets (Pixel / personality variants), not Square terminal, for colorful schemes. Typography: Terminal for mono Light/Dark; soft/`Typography` (Pixel typography) for colorful schemes.
- Settings hub category pastel fills: apply for Pixel **and** all four new modes; Light/Dark/Follow stay mono tiles.

### Surfaces that list themes

| Surface | Themes shown |
|---------|----------------|
| Settings Appearance `ThemeSelectorItem` | Light, Dark, Pixel, Ethereal, Rose Pine, Catppuccin Mocha, Sakura, Follow System |
| Setup theme page | Light, Dark, Pixel, Follow System only (unchanged) |

Strings: `setcat_theme_ethereal`, `setcat_theme_rose_pine`, `setcat_theme_catppuccin_mocha`, `setcat_theme_sakura`.

### Touch points

- `AppThemeMode`, `AppThemeScheme`, `resolveAppTheme`
- `Theme.kt` / `Color.kt` color schemes + `OrpheusTheme` branch (`isSoftChrome` / not only `isPixel`)
- `SettingsCategoryScreen` options map
- `SettingsScreen` / `settingsCategoryColorsOrMono` colorful path
- Unit tests: `AppThemeModeResolutionTest` for four new modes + Follow System still mono

---

## 2. ThemePersonality (bounded “vivo”)

### Mechanism

```kotlin
enum class ThemeMotionRecipe { SLIDE_FADE, SLIDE_SHORT, FADE_LONG, SCALE_FADE }

data class ThemePersonality(
    val softChrome: Boolean,
    val shapeSet: OrpheusShapeSet, // or key resolved in OrpheusTheme
    val motionRecipe: ThemeMotionRecipe,
    val openDistance: Dp, // OrpheusMotion Distance* tokens only
)

val LocalThemePersonality = staticCompositionLocalOf { /* terminal default */ }
```

`OrpheusTheme` sets `LocalThemePersonality` from `scheme`.

| Scheme | Feeling | Shape | Motion recipe |
|--------|---------|-------|---------------|
| LIGHT / DARK | terminal | Square or Rounded (user pref) | `SLIDE_FADE`, `DistanceBase` |
| PIXEL | soft Pixel | `OrpheusShapeSets.Pixel` | `SLIDE_FADE`, `DistanceBase` |
| ETHEREAL | navy cool | soft + slightly more pill (reuse Pixel or Rounded with larger search/button) | `FADE_LONG` (open `DurationFast`, close `DurationQuick`; prefer fade over travel) |
| ROSE_PINE | calm cream | soft médio (`OrpheusShapeSets.Rounded`) | `SLIDE_SHORT` (`DistanceSmall`) |
| CATPPUCCIN_MOCHA | pastel cozy | soft Pixel-like | `SCALE_FADE` (scale from `ContentSwapScale` 0.97f → 1 + fade; open/close asymmetry) |
| SAKURA | petal | larger radius soft (Pixel with searchBar/smooth28+ bump or dedicated Sakura set copying Pixel + +4.dp on medium radii) | `SLIDE_FADE` with `DistanceBase` |

### Consumers (v1)

- `OrpheusTheme` shape / chrome / typography selection
- Shared animated chrome that already uses `OrpheusMotion` and can read `LocalThemePersonality` (e.g. Settings enter / Setup nav icon transition) — optional wire where cheap
- Settings pastel tiles via colorful-mode helper

### Non-consumers (v1)

- Do not fork Library / Player / Search layout trees per theme.

---

## 3. Setup Next arrow (no square)

### Root cause

Setup bottom bar: Back = plain `IconButton`; Next/Finish = `MediumExtendedFloatingActionButton` + `setupOutline()` → visible square around the forward arrow.

### Target

- Next and Finish use the same control family as Back: `IconButton` / `OrpheusFilledIconButton` (no FAB, no terminal outline on that control).
- Icon only: forward arrow on intermediate pages; check (or close where already used) on last page — no extended FAB label required for parity.
- Keep existing slide+fade `AnimatedContent` motion; do not reintroduce container rotation.

### Touch points

- `SetupScreen.kt` → `SetupBottomBar` (primary forward control only)

---

## 4. Navbar tips square when Rounded

### Root cause

Navbar clip radius comes from `navBarCornerRadius`, independent of Visual Style. Rounded ON + radius `0` ⇒ flat tips. Promo helpers exist (`promoteZeroNavBarRadiusIfRounded`, `setUseSmoothCorners` promoting `0`→`28`) but gaps can leave radius `0` or preview lying.

### Target

| Case | Behavior |
|------|----------|
| Rounded ON + radius `0` / unset | Persist and display as `DEFAULT_NAV_BAR_CORNER_RADIUS_DP` (28) |
| Explicit Square | Keep square chrome; do not force radius |
| Cold start MainUI | Still call `promoteZeroNavBarRadiusIfRounded()` |
| `NavBarCornerRadiusScreen` preview | Same radius + smooth/square delegate behavior as live bar (`DynamicSmoothCornerShape` / equivalent), not a fake flat Surface when Rounded |

### Touch points

- Verify / harden `UserPreferencesRepository` promo + `MainActivity` LaunchedEffect
- `NavBarCornerRadiusScreen` preview shape
- Tests already covering promo remain green; add case if a Settings-only path still reads `0` while Rounded

---

## 5. Testing

- Unit: resolve four new modes; Follow System ignores them; pastel path for colorful modes; promo Rounded+0→28.
- Compile: `compileDebugKotlin` / relevant unit tests.
- Manual (user device): Settings lists four themes and applies colors; Setup theme page has no Ethereal/Rose Pine/Catppuccin/Sakura; Setup Next matches Back (no square); Library navbar tips rounded when Visual Style = Rounded; soft themes feel distinct (shapes/motion) without layout forks.

---

## Approach

**Chosen:** Approach A — new `AppThemeMode` values + schemes + `ThemePersonality`, Settings-only listing, soft chrome like Pixel, Setup IconButton Next, radius promo harden.

Rejected: scheme-override without modes; terminal chrome on colorful palettes; full per-theme Library/Player redesign in this cycle.
