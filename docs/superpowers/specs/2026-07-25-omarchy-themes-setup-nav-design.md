# Omarchy Themes + Setup Arrow + Navbar Corners — Design

Date: 2026-07-25  
Status: approved for planning  
Scope: three Omarchy-derived app themes (Settings-only), Setup next-arrow chrome fix, navbar tip square when Rounded — then plan / implement

## Goals

1. Add **Ethereal**, **Rose Pine**, and **Catppuccin Mocha** as app theme modes, mapped from [Omarchy](https://omarchytheme.com/) palettes (as published on those theme pages).
2. New themes appear only in **Settings → Appearance**; they must **not** appear in Setup theme picker.
3. Fix Setup bottom-bar **Next** control: remove the square chrome (FAB + outline) so it matches Back (`IconButton` family).
4. Fix navbar tips staying square when Visual Style is **Rounded** (radius `0` / promo gaps).

## Non-goals

- Changing player album-art / dynamic theme prefs (`ThemePreference`).
- Follow System selecting Omarchy themes (Follow System stays OS mono Light/Dark only, same as Pixel).
- Adding Omarchy themes to Setup, or a separate “Dawn vs Main” Rose Pine picker (use the Omarchy page palette as-is: Rose Pine = light `#faf4ed`).
- Release bump / ship (unless requested after implementation).
- Emulator / adb UI driving (`CLAUDE.md`).

---

## 1. Omarchy themes (Settings-only)

### Preference model

Extend `AppThemeMode` with three string constants:

| Mode | Storage key | Appearance |
|------|-------------|------------|
| Ethereal | `ethereal` | Dark navy `#060B1E`, fg `#ffcead`, accent `#7d82d9` |
| Rose Pine | `rose_pine` | Light `#faf4ed`, fg `#575279`, accent `#56949f` (Omarchy page = Dawn-like) |
| Catppuccin Mocha | `catppuccin_mocha` | Dark `#11111B`, fg `#CDD6F4`, accent `#F5C2E7` |

Existing modes unchanged: `LIGHT`, `DARK`, `PIXEL`, `FOLLOW_SYSTEM`.

### Resolution

Extend `AppThemeScheme` with `ETHEREAL`, `ROSE_PINE`, `CATPPUCCIN_MOCHA`.

```
when (appThemeMode) {
  LIGHT -> darkTheme=false, scheme=LIGHT
  DARK -> darkTheme=true, scheme=DARK
  PIXEL -> darkTheme=true, scheme=PIXEL
  ETHEREAL -> darkTheme=true, scheme=ETHEREAL
  ROSE_PINE -> darkTheme=false, scheme=ROSE_PINE
  CATPPUCCIN_MOCHA -> darkTheme=true, scheme=CATPPUCCIN_MOCHA
  FOLLOW_SYSTEM -> OS light/dark → mono LIGHT|DARK only
  else / legacy "terminal" -> DARK
}
```

### Color schemes + chrome

- Build Material 3 `ColorScheme`s from Omarchy palette roles (background, foreground, accent, color0–15 as supporting containers / error / secondary / tertiary). Exact hex from the three Omarchy theme pages — no invented variants.
- Soft chrome like Pixel: `LocalTerminalChrome = false` for Ethereal / Rose Pine / Catppuccin Mocha / Pixel.
- Shapes: reuse Pixel / Rounded soft set (not Square terminal). Typography: Terminal for mono Light/Dark; Pixel (or shared soft) typography for colorful schemes including the three new ones.
- Settings hub category pastel fills: apply for Pixel **and** the three new modes (same “colorful theme” path); Light/Dark/Follow stay mono tiles.

### Surfaces that list themes

| Surface | Themes shown |
|---------|----------------|
| Settings Appearance `ThemeSelectorItem` | Light, Dark, Pixel, Ethereal, Rose Pine, Catppuccin Mocha, Follow System |
| Setup theme page | Light, Dark, Pixel, Follow System only (unchanged) |

Strings: `setcat_theme_ethereal`, `setcat_theme_rose_pine`, `setcat_theme_catppuccin_mocha` (and translations already used by Appearance).

### Touch points

- `AppThemeMode`, `AppThemeScheme`, `resolveAppTheme`
- `Theme.kt` color schemes + `OrpheusTheme` branch (`isSoftChrome` / not only `isPixel`)
- `SettingsCategoryScreen` options map
- `SettingsScreen` / category color helpers that currently special-case `PIXEL`
- Unit tests: `AppThemeModeResolutionTest` for three new modes + Follow System still mono

---

## 2. Setup Next arrow (no square)

### Root cause

Setup bottom bar: Back = plain `IconButton`; Next/Finish = `MediumExtendedFloatingActionButton` + `setupOutline()` → visible square around the forward arrow.

### Target

- Next and Finish use the same control family as Back: `IconButton` / `OrpheusFilledIconButton` (no FAB, no terminal outline on that control).
- Icon only: forward arrow on intermediate pages; check (or close where already used) on last page — no extended FAB label required for parity.
- Keep existing slide+fade `AnimatedContent` motion; do not reintroduce container rotation.

### Touch points

- `SetupScreen.kt` → `SetupBottomBar` (primary forward control only)

---

## 3. Navbar tips square when Rounded

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

## 4. Testing

- Unit: resolve three new modes; Follow System ignores them; promo Rounded+0→28.
- Compile: `compileDebugKotlin` / relevant unit tests.
- Manual (user device): Settings lists three themes and applies colors; Setup theme page has no Ethereal/Rose Pine/Catppuccin; Setup Next matches Back (no square); Library navbar tips rounded when Visual Style = Rounded.

---

## Approach

**Chosen:** Approach A — new `AppThemeMode` values + schemes, Settings-only listing, soft chrome like Pixel, Setup IconButton Next, radius promo harden.

Rejected: scheme-override without modes (fragile resolution); terminal chrome on Omarchy palettes (fights color).
