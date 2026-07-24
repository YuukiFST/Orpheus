# Playback + Queue UX Fixes — Design

Date: 2026-07-24  
Status: approved for planning  
Scope: one release — Setup cleanup, dismiss/stop, Search queue, artist label, Play Once removal

## Goals

1. Keep Adjust Corner Radius out of first-run Setup (Settings only).
2. Mini-player swipe dismisses once — no second bar from the previous track.
3. System media notification swipe **stops and unloads** playback (not pause).
4. Remove the sticky “Processing playback action…” notification.
5. Search playback must not hand off into Liked when a Search track ends.
6. Search ⋮ menu: **Add to queue**; inspect/edit/remove via existing `QueueBottomSheet`.
7. YouTube/online artist label = channel/uploader; local = full artist tag (no split).
8. Remove **Play Once** from Search (tap-to-play already covers it).

## Non-goals

- Changing NavBar style (pill/full) Setup page — only corner-radius customize path leaves Setup.
- New Queue screen or Search-only queue UI.
- Splitting local multi-artist tags (“X e Y”) for display.
- Changing album-art / dynamic player theme prefs.

---

## 1. Setup — no corner radius

`SetupPage.NavBarLayout` stays (style picker).

Remove from Setup:

- Customize / Adjust Corner Radius button on `NavBarLayoutPage`
- `showCornerRadiusOverlay` + `NavBarCornerRadiusContent` overlay wired from Setup

Corner radius remains available under Settings Appearance (existing screen).

Default radius for new installs: keep current default (no forced Setup step).

---

## 2. Mini player — single dismiss

**Symptom:** After swiping the now-playing bar, another bar (previous track) appears and needs a second swipe.

**Intent:** One horizontal dismiss clears the playing surface and stops audio. Undo snackbar may appear once; no second mini-player sheet underneath.

**Fix locus:** Same family as dismiss/sheet-visibility race (`PlaylistDismissUndoStateHolder` / `PlayerViewModel` sheet flags / optimistic mini-player state). Ensure dismiss clears current + any stale pending mini-player identity so a previous track cannot rehydrate the bar under the undo state.

---

## 3. Notification dismiss = stop + unload

Swipe-away of the system media notification must:

- Stop audio
- Unload / clear the media session queue (same spirit as closing the app’s playback session)
- Not leave paused audio playing in the background

Wire through existing `ACTION_STOP_AND_UNLOAD` / `stopPlaybackAndUnload` path. Harden any path that only `pause()`s on notification delete.

---

## 4. Remove “Processing playback action…” notification

String: `service_processing_action` (“Processing playback action…”).

Find the foreground/service notification (or transient status) that surfaces this while queuing controller actions when MediaController is unavailable, and remove or stop posting it so it never appears in the shade.

Playback may still queue actions internally (Timber log OK); no user-visible notification for that.

---

## 5. Search end must not start Liked

**Symptom:** After a Search-started track finishes, Liked (Library) starts.

**Intent:** Search playback uses an isolated queue. When that queue ends under normal sequential play, playback **stops** (or continues only with tracks explicitly in that Search/queue session). It must **not** resume or attach a Liked favorites queue.

Root fix at queue handoff / next-track / leftovers from Liked `REPEAT_MODE_ALL` session wrap — ensure leaving or ending a Search session restores/clears so Liked is not the next source.

---

## 6. Add to queue (Search) + existing Queue sheet

- Search track ⋮ menu: add **Add to queue** (append after current queue / at end).
- Reuse existing player `QueueBottomSheet` to list queue items and remove unwanted tracks (already supports queue editing — wire Search adds into the same queue model).
- No new destination; user opens queue from the player UI as today.

If local Search already has Add to queue via `SongInfoBottomSheet`, ensure YouTube/Search path exposes the same action. Prefer one shared append API on the playback controller / `PlayerViewModel`.

---

## 7. Artist label — YouTube uploader only

| Source | Display / click target |
|--------|------------------------|
| YouTube / online | Channel / uploader name only |
| Local library | Full `artist` tag as stored (no “X e Y” split) |

Clicking the YouTube artist control opens/filters that uploader/channel — not a composite multi-name string.

---

## 8. Remove Play Once

Remove Play Once from Search ⋮ / row overflow (and any Search-specific Play Once entry point called out in UI). Tap on the track remains the way to start playback. Do not remove unrelated library play helpers unless they are the same visible “Play Once” affordance.

---

## 9. Testing / verification

Manual checklist:

1. Fresh Setup: NavBar style page has no Adjust Corner Radius; Settings still has it.
2. Play A, then B; swipe mini player once → bar gone, audio stopped; no second bar.
3. Swipe media notification → audio fully stopped/unloaded.
4. Shade never shows “Processing playback action…”.
5. Play from Search to end → does not start Liked.
6. Search ⋮ → Add to queue → item appears in QueueBottomSheet; can remove.
7. YouTube result shows uploader; local shows full artist tag.
8. No Play Once in Search menu.

Build: compile + unit tests for dismiss / queue-next / search isolation if touched.

---

## 10. Ship

After fixes: commit(s), push, bump version (next after 1.0.16 → **1.0.17**), tag, release per `docs/RELEASE.md`.

---

## Architecture notes

| Area | Touch points (expected) |
|------|-------------------------|
| Setup radius | `SetupScreen.kt` (`NavBarLayoutPage`, overlay) |
| Mini dismiss | `PlaylistDismissUndoStateHolder`, `PlayerViewModel`, mini-player sheet state |
| Notification stop | `MusicService`, `LocalOnlyMediaNotificationProvider` |
| Processing notif | `MusicService` / wherever `service_processing_action` is posted |
| Search≠Liked | Search play paths, queue source name, repeat/Liked wrap restore |
| Add to queue | Search ⋮ menus, `PlayerViewModel` append, `QueueBottomSheet` |
| Artist | YouTube track model / Search row artist click |
| Play Once | `YouTubeSearchScreen` / search overflow |

## Error handling

- Add to queue with empty/no controller → toast, no crash.
- Notification delete when already idle → no-op.
- Unknown artist/uploader missing → show fallback (“Unknown”) without inventing co-artists.
