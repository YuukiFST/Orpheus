# Task 3 Report — Liked UI: Reorder toggle + drag

**Status:** Complete  
**Branch:** `feat/drag-reorder-liked-playlists`

## Summary

Added drag-to-reorder for the Library **Liked** tab, mirroring the `PlaylistDetailScreen` Calvin reorderable pattern.

### UI (`LibraryScreen.kt`, `LibrarySongsAndFavoritesTabs.kt`)

- **Reorder toggle** (`LikedReorderModeToggleRow`) appears on the Liked tab (hidden during multi-select). Uses tertiary container color when active, matching playlist detail chrome.
- **Drag handles** (`DragIndicator` + `draggableHandle`) shown when reorder mode is on and selection mode is off.
- **Full list path:** When reorder mode is on *or* sort is `LikedSongManual`, the tab uses `likedSongsFullList` instead of Paging3 + separate YouTube list so drag indices stay stable.
- **On drag end:** `onReorderPersist` sends the reordered media-id list to the ViewModel.

### State (`LibraryStateHolder.kt`, `PlayerViewModel.kt`)

- `isLikedReorderMode` / `setLikedReorderMode()` — toggle + load full list via `getFavoriteSongsOnce`.
- `likedSongsFullList` / `refreshLikedSongsFullList()` — loads manual order (`getLikedSongsInManualOrder`) or current-sort snapshot for reorder.
- `reorderLikedSongs(orderedMediaIds)` — **publishes UI first** (sort → Manual, list reordered), then persists to Room + preferences.
- Reorder mode auto-clears when leaving the Liked tab.
- `getFavoriteSongsForSelection()` already prefers manual order when sort is Manual (play queue consistency).

### Test

- `LibraryStateHolderLikedReorderTest.reorderLikedSongs_setsManualSortAndPersistsOrder` — verifies UI state update, `reorderLikedSongs` repo call, and `setLikedSongsSortOption(LikedSongManual)`.

## Verification

| Check | Result |
|-------|--------|
| `./scripts/build.sh :app:compileDebugKotlin` | PASS |
| `:app:testDebugUnitTest --tests LibraryStateHolderLikedReorderTest` | PASS |

## How to enable reorder (user)

1. Open **Library → Liked**.
2. Tap the **Reorder** button below the tab header chrome (drag icon + label). Button turns tertiary when active.
3. Drag songs via the **≡** handle on the left of each row.
4. Release to save — sort switches to **Manual** automatically.
5. To exit reorder without dragging: tap **Reorder** again.
6. To leave Manual sort: open the sort sheet and pick any other criterion (e.g. Date Liked).

## Concerns / follow-ups

1. **Reorder toggle vs sort sheet:** Picking a non-Manual sort from the sheet does not auto-disable the reorder toggle. If both are active, the full list still shows with handles (by design per “reorder on → full list”). Consider auto-clearing reorder mode on sort change in Task 4 polish if UX feels confusing.
2. **Large libraries:** Reorder mode loads all favorites into memory (`getFavoriteSongsOnce`). Acceptable for typical liked counts; very large libraries may feel heavy.
3. **YouTube + local mix:** Full-list path merges via repository manual-order merge (Task 2); reorder mode snapshot uses current sort before first drag, then Manual merge after persist.
4. **Task 5:** Backup export/import of `liked_order` not yet wired (planned separately).
