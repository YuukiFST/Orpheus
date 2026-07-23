# UI Fixes + Pixel Theme — Design

Date: 2026-07-23  
Status: approved for planning  
Scope: one release — Pixel theme + listed bugs, then commit / push / release

## Goals

1. Add **Pixel** as a fourth app theme mode (PixelPlayerOSS-style colors).
2. Keep **Light** mono white+black; make **Dark** mono black+white.
3. Fix search, Liked loop, Settings motion, corner radius, dismiss, About links.
4. Ship as a normal version bump + release.

## Non-goals

- Changing player album-art / dynamic theme prefs (`ThemePreference`).
- Replacing Orpheus branding or terminal aesthetic outside Pixel mode.
- Adding Sponsors / F-Droid (they are removed from About).
- Follow System selecting Pixel (Follow System = OS mono Light/Dark only).

---

## 1. Appearance / Pixel theme

### Preference model

Extend `AppThemeMode` with `PIXEL`:

| Mode | Result |
|------|--------|
| `LIGHT` | Existing mono Light (`LightColorScheme`) — leave look as-is |
| `DARK` | Mono Dark (`DarkColorScheme` / black+white) — strip residual green tint if any path still uses phosphor |
| `PIXEL` | New `PixelColorScheme` (colorful Material 3, PixelPlayerOSS feel) |
| `FOLLOW_SYSTEM` | OS light/dark → mono Light or mono Dark only |

Wire into:

- `AppThemeMode` constants
- Settings Appearance chooser + Setup theme picker
- `MainActivity` / `ExternalPlayerActivity` theme resolution
- Strings for the new option

### Color schemes

- **Light / Dark:** keep current mono schemes as source of truth.
- **Pixel:** new scheme(s) with saturated primary/secondary/tertiary and colored containers. Settings hub `getCategoryColors(...)` applies **only** when mode is `PIXEL`. In Light/Dark/Follow, Settings category tiles use surface / onSurface (no per-section pastel fills).

Pixel does not have separate Light/Dark variants in this release: one colorful look, always.

### Resolution rule

```
when (appThemeMode) {
  LIGHT -> darkTheme = false, scheme = Light
  DARK -> darkTheme = true, scheme = Dark
  PIXEL -> darkTheme = true (or scheme-defined), scheme = Pixel
  FOLLOW_SYSTEM -> darkTheme = isSystemInDarkTheme(), scheme = Light|Dark mono
}
```

---

## 2. Search bar prompt

Local Search (and any matching DockedSearchBar used as the main search field) shows a terminal prompt **`>`** as `leadingIcon` (text, not Material Search icon).

Empty / placeholder field keeps current hint; prompt stays visible.

---

## 3. Liked queue loop

When the last track of the Liked (favorites) queue finishes under normal sequential play, playback continues at the **first** Liked track (wrap), instead of stopping.

Root fix at the shared end-of-queue / next-track path used by Liked, not a one-off UI hack.

---

## 4. Dismiss stops playback

Any dismiss of the playing surface stops audio for real:

- In-app mini player swipe-dismiss
- Full player dismiss that clears the session
- System media notification dismiss / remove

Also fix **double swipe / “Playlist Dismissed”**: one swipe must dismiss and show undo once; the bar must not reappear requiring a second swipe. Root cause likely in `PlaylistDismissUndoStateHolder` / mini-player dismiss gesture vs undo snackbar race — fix at that holder so state transitions are single-shot.

After dismiss: pause/stop player, clear or park queue per existing undo model, but **audio must not continue**.

---

## 5. Settings motion

Settings enter/swap animations must use the same `OrpheusMotion` tokens as the rest of the app (`DurationFast` / `DurationQuick`, `EaseSmoothOut`). Remove any slower ad-hoc tweens (e.g. `DurationSlow` / `DurationVerySlow`) on Settings tab/screen transitions so perceived speed matches Library/Search/etc.

---

## 6. Corner radius

### Adjust Corner Radius preview

The black preview bar in Adjust Corner Radius must morph its corners live with the slider, using the same shape token / radius preference as the rest of the app.

### Library Liked rows + mini player

Song cards in Liked and the bottom now-playing bar must consume the configured corner radius. Today they render square despite a non-zero setting — wire them through the shared shape (`TerminalCornerShape` / nav-bar corner radius preference) instead of hardcoded sharp shapes.

---

## 7. About cleanup

Remove **GitHub Sponsors** and **F-Droid** entries/links from About (and any About-related bottom sheets that surface the same links). Leave other credits/links intact.

---

## 8. Testing / verification

Manual checklist before release:

1. Appearance: Light / Dark / Pixel / Follow System each look correct; Settings cards colored only in Pixel.
2. Search shows `>` prompt.
3. Play last Liked track to end → first Liked starts.
4. Swipe mini player once → dismiss + undo once; audio stops.
5. Dismiss media notification → audio stops.
6. Settings navigation feels same speed as other tabs.
7. Corner radius slider preview rounds; Liked rows + mini player match.
8. About has no Sponsors / F-Droid.

Build: compile + relevant unit tests for dismiss / theme mode if touched.

---

## 9. Ship

After fixes: single commit (or small logical commits if needed), push `main`, bump version, cut release per existing project release process.

---

## Architecture notes

| Area | Touch points (expected) |
|------|-------------------------|
| Theme mode | `AppThemeMode`, `ThemePreferencesRepository`, `Theme.kt`, Settings/Setup UI, MainActivity |
| Settings colors | `SettingsScreen.getCategoryColors` gated on Pixel |
| Search | `SearchScreen` / `YouTubeSearchScreen` leadingIcon |
| Liked wrap | Player next/end handlers (`PlayerViewModel` / Media3 listener) |
| Dismiss | `PlaylistDismissUndoStateHolder`, mini player gesture, `MusicService` notification dismiss |
| Motion | Settings screens using `OrpheusMotion` |
| Corners | Corner radius settings UI + Liked list item + mini player shapes |
| About | `AboutScreen` (+ related sheets) |

## Error handling

- Invalid stored theme mode string → fall back to `LIGHT`.
- Dismiss while already idle → no-op, no double undo snackbar.
