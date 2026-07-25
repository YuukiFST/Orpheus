# Setup Motion + Playback Dismiss Semantics — Design

Date: 2026-07-24  
Status: approved for planning  
Scope: four fixes — mini-player dismiss UX, system-notification dismiss semantics, Setup Next/Back motion, Recents task kill

## Goals

1. Mini-player horizontal swipe stops audio and hides the bar **without** the “Playlist dismissed” undo snackbar.
2. Swiping away the **system media notification** stops audio but **keeps** the in-app mini player (paused, same track/position) so the user can resume from the app.
3. Setup first-run Next **and** Back use the same directional icon motion; remove the large 360° FAB spin that collides with system navigation.
4. Swiping Orpheus away from Recents **always** stops and unloads playback (ignore `keepPlayingInBackground` for this path).

## Non-goals

- Redesigning Setup page content, theme picker, or permission cards.
- Reintroducing undo for mini-player dismiss.
- Changing `keepPlayingInBackground` behavior for Home / multi-tasking (only Recents kill is forced stop).
- New motion libraries or design-system overhaul (Orpheus tokens already exist).

### Supersedes (partial)

Earlier specs (`2026-07-23-ui-fixes-pixel-theme-design`, `2026-07-24-playback-queue-ux-design`) said notification dismiss = full `stopPlaybackAndUnload` and mini dismiss shows undo. This document **overrides** those two behaviors for this release:

| Surface | Old intent | New intent |
|---------|------------|------------|
| Mini-player swipe | Stop + undo bar | Stop + hide, **no undo** |
| System notification swipe | Stop + unload session | Stop audio, **keep** mini player / queue UI |

---

## 1. Mini-player swipe — stop, hide, no undo

### Behavior

One horizontal dismiss:

- Stops / clears playback (same soft-clear spirit as today’s `ACTION_CLEAR_PLAYBACK` / engine stop).
- Hides mini player / sheet.
- Does **not** set `showDismissUndoBar`.
- Does **not** show `DismissUndoBar` / `playlist_dismissed_message`.

### Fix locus

- `MiniPlayerDismissGestureHandler` / sheet host callback currently calls `onDismissPlaylistAndShowUndo`.
- `PlaylistDismissUndoStateHolder.dismissPlaylistAndShowUndo` and `PlayerViewModel` dismiss path: either bypass undo entirely or add a `dismissPlaylistWithoutUndo` that clears playback + sheet without undo state.
- `MiniPlayerVisibilityPolicy` / `dismissJustCommitted` can stay if still needed to prevent ghost rehydrate; undo-specific UI state goes unused on this path.

### Error handling

- Dismiss while already idle → no-op.
- No second snackbar / no flash of previous track under an undo bar (undo bar gone).

---

## 2. System notification swipe — stop audio, keep mini player

### Behavior

When the user dismisses the shade media notification (Media3 `NOTIFICATION_DISMISSED` / `ACTION_STOP_AND_UNLOAD` deleteIntent today):

- Audio **stops** (pause + stop engines; no continued playback).
- In-app mini player **remains visible** with the same current song and position (paused).
- Queue / `StablePlayerState.currentSong` stay populated so the user can press play on the mini player and continue from where they left off.
- Notification does not remain in the shade.

### Why this differs from full unload

Full `stopPlaybackAndUnload` clears the session → UI loses `currentSong` → mini player disappears (today’s bug report: bar gone while audio sometimes still ran). New path must **not** clear the UI playback identity.

### Fix locus

Split notification-dismiss from Recents / explicit close:

| Trigger | Action |
|---------|--------|
| Notification swipe / Media3 dismissed | New “pause & park for UI” path (name TBD, e.g. `pausePlaybackKeepUi` / dedicated action) |
| Recents `onTaskRemoved` | Full `stopPlaybackAndUnload` (see §4) |
| Explicit close-player command / in-app close that means “kill session” | Keep existing unload if already used for that |

Wire:

- `MusicService.onStartCommand` early-return that currently always unloads on media3 dismiss → call the park path instead.
- `LocalOnlyMediaNotificationProvider` deleteIntent: either a new action or reuse unload only where full kill is intended; notification swipe uses park.
- `PlayerViewModel` / MediaController listeners: when service parks, UI must keep sheet visible and song stable (paused), not treat as clear-queue dismiss.

### Error handling

- Dismiss when already idle / no media → no-op.
- Park must not leave a silent FGS notification reappearing without user play.

---

## 3. Setup Next/Back — directional icon motion

### Context (frontend.md)

Orpheus already has a design system (`OrpheusMotion`, `TerminalCornerShape`, terminal aesthetic). Do **not** apply greenfield aesthetic skills; improve motion in place.

### Current problem

`SetupBottomBar` in `SetupScreen.kt`:

- FAB rotates `currentPage * 360f` over **900ms** — large spinning box overlaps system Recents/Home.
- Corner morph animates values that are never applied (`shape` stays `TerminalCornerShape`).
- Back `IconButton` has no page-change motion; Next does.

### Target motion (Approach A — approved)

- Remove container rotation and unused corner morph.
- Next and Back both use `AnimatedContent` (or shared icon transition): **horizontal slide ±8dp + fade**, `OrpheusMotion.DurationFast` (250ms), `EaseSmoothOut`.
- Direction mirrors pager: forward = content exits left / enters from right; back = opposite.
- Keep fixed `TerminalCornerShape` on the primary FAB.
- Optional: light press scale on both controls later — out of scope unless free while touching the same composable.

### Touch points

- `SetupBottomBar` only (and any dead animation locals it owns).
- Prefer existing `OrpheusMotion` tokens; no new duration constants.

---

## 4. Recents swipe — always stop + unload

### Behavior

When the user opens Recents and swipes Orpheus away (`Service.onTaskRemoved` / equivalent task-removal path):

- Always `stopPlaybackAndUnload` (or equivalent full stop + clear + tear down FGS).
- Do **not** honor `keepPlayingInBackground` on this path — user explicitly killed the task.

### Fix locus

- Implement / harden `MusicService.onTaskRemoved` → unload with `preservePlaybackSnapshot = false` (or project’s existing unload API).
- Ensure sticky restart does not revive audio after Recents kill.

### Error handling

- Already idle → no-op unload.
- Preference `keepPlayingInBackground` continues to apply only to normal backgrounding (Home), not Recents kill.

---

## 5. Testing / verification

Manual checklist:

1. Play a track → swipe mini player once → bar gone, audio stopped, **no** “Playlist dismissed”.
2. Play a track → swipe system media notification → audio stopped, **mini player still visible** paused → play resumes same position.
3. Setup wizard: tap Next then Back — both show directional icon/content motion; **no** spinning FAB behind system nav.
4. Play a track → Recents → swipe Orpheus up → audio fully stopped; returning to app does not continue that session’s audio.

Build: compile + unit tests for notification-dismiss vs unload branching / `MusicServiceShould` if the early-return contract changes.

---

## 6. Architecture notes

| Area | Touch points (expected) |
|------|-------------------------|
| Mini dismiss no-undo | `MiniPlayerDismissGestureHandler`, sheet host, `PlaylistDismissUndoStateHolder`, `PlayerViewModel` |
| Notif park vs unload | `MusicService`, `LocalOnlyMediaNotificationProvider`, `MusicServiceShould`, UI sheet visibility |
| Setup motion | `SetupScreen.kt` → `SetupBottomBar` |
| Recents kill | `MusicService.onTaskRemoved` |

## Error handling (summary)

- Idle dismiss / idle Recents kill → no-op.
- Notification park must not clear `currentSong` or hide sheet.
- Mini dismiss must not show undo UI.
