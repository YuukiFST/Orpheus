# Changelog

All notable changes to Orpheus will be documented in this file.

## [1.0.25] - 2026-07-25

### Fixed
- Mini player reappears after swipe-dismiss when starting a new song (stale dismiss clear no longer wipes the new session).

## [1.0.24] - 2026-07-25

### Fixed
- Library Liked song rows no longer use NavBar corner radius as card chrome; default list shape restored.
- Mini player keeps Rounded corners on screens where the tab NavBar is hidden (e.g. Settings).
- Setup Back/Next sit above system navigation via `navigationBarsPadding()`.

## [1.0.23] - 2026-07-25

### Added
- Settings-only themes: Ethereal, Rose Pine, Catppuccin Mocha (Omarchy palettes), and Sakura (creative pink), with ThemePersonality soft chrome.

### Fixed
- Setup Next no longer uses square FAB/outline chrome; matches Back IconButton family.
- Rounded Visual Style: navbar tips and corner-radius preview coerce radius 0 → 28; dp label matches preview.

## [1.0.22] - 2026-07-25

### Fixed
- App defaults to Rounded corners; navbar radius promotes from 0 to 28 when Rounded is on (legacy installs no longer show square tips).
- Setup Back/Next share the same directional icon slide+fade (no spinning FAB).
- Library bottom-nav tap no longer sticks when the nav graph was briefly unavailable.

### Changed
- Pixel theme surface/container roles filled for closer PixelPlayerOSS look; CRT overlay experiment removed.
- Device helper script `scripts/celular.sh` for opening the emulator without agent UI driving.

## [1.0.21] - 2026-07-25

### Changed
- Design-system foundation: shared `OrpheusSpacing` scale, motion distance tokens, documented Title/Meta/Label type roles, and `LocalTerminalChrome` gates for terminal borders/press chrome.
- Player and mini player layout rhythm, typography roles, and spacing tokens; mini player height 72dp for readable title/meta.
- Library song rows, Search/YouTube insets and row chrome, Settings row borders and hub chevrons, Setup page padding and heading roles — polish in place on the existing Orpheus look (no new palette).

## [1.0.20] - 2026-07-25

### Changed
- Setup: Next/Back use directional icon slide instead of the large spinning FAB morph.
- Mini player swipe dismiss stops playback without the Playlist dismissed undo bar.

### Fixed
- System media notification swipe pauses audio but keeps the in-app mini player so playback can resume from the same point.
- Closing Orpheus from Recents always stops and unloads playback.

## [1.0.19] - 2026-07-24

### Added
- Setup: Back control on the bottom bar for previous steps.

### Fixed
- Notification swipe unload no longer falls through to Media3 sticky start and revive playback.
- Mini player stay hidden after Playlist dismissed undo timeout; DualPlayer engines cleared on dismiss.

## [1.0.18] - 2026-07-24

### Fixed
- Mini player no longer flashes behind the Playlist dismissed undo bar after swipe dismiss.
- Swiping away the media notification stops and unloads playback (both DualPlayer engines).
- YouTube Search/player artist label prefers the publishing channel when NewPipe returns a collab byline (e.g. `nikmouu and Novatroop` → `nikmouu`).

## [1.0.17] - 2026-07-24

### Added
- Search: Add to queue menu action for appending tracks while playback is active.

### Changed
- Setup: corner radius customization removed from first-run flow.
- Search: Play Once menu item removed; tap-to-play remains the default action.
- Search: artist label and click use YouTube uploader when available.

### Fixed
- Mini player dismiss clears the queue so a second bar does not appear.
- Processing playback action notification is no longer shown.
- Search playback is isolated from Liked so switching contexts does not continue the wrong queue.

## [1.0.16] - 2026-07-23

### Added
- Pixel theme mode with Google-style pastel palette, selectable in Settings and setup.

### Changed
- Settings category tiles use monochrome styling in Light, Dark, and Follow System; pastel fills remain for Pixel theme only.
- Search bars show a terminal-style `>` prompt instead of a search icon.
- Settings navigation transitions use OrpheusMotion fast duration (250ms) instead of 450ms.

### Fixed
- Liked queue wraps to the first track at the end of the list when repeat is off.
- Dismissing the mini player or swiping away the notification stops playback; sheet no longer revives after a single dismiss swipe.
- Corner radius preview, Liked song rows, and mini player sheet honor the nav bar radius preference.
- About screen no longer shows GitHub Sponsors or F-Droid links.

## [1.0.15] - 2026-07-22

### Fixed
- About screen now credits YuukiFST and links to the Orpheus repository instead of upstream PixelPlayer maintainers.
- Search playback switches tracks correctly when selecting another song while one is already playing.

## [1.0.14] - 2026-07-22

### Changed
- Light mode is now the default theme for new installs and unset preferences.

### Fixed
- Light mode search bar and library tabs now show black outlines.
- Library header tabs and surfaces honor the rounded visual style setting instead of staying square.

## [1.0.13] - 2026-07-22

### Added
- Visual style setting: square or rounded corners for buttons, cards, and search bar.
- `OrpheusSwitch` respects the visual style preference (square track/thumb in square mode).

### Changed
- App theme uses monochrome black/white palette: light mode white background with black text and outlines; dark mode inverted.
- Settings screens no longer block input during navigation transitions and scroll the collapsible top bar without per-frame coroutine churn.

### Fixed
- Search playback keeps optimistic mini player updates while ExoPlayer still reports the previous track.
- Switches no longer stay rounded when square visual style is selected.

## [1.0.12] - 2026-07-22

### Changed
- Unified `Orpheus*` button components across sheets, dialogs, settings, and player.
- Tokenized motion with typed `OrpheusMotion` tween helpers (open 250ms / close 150ms).
- Terminal press scale and phosphor glow feedback on interactive controls.

### Fixed
- YouTube-liked tracks no longer reuse local MediaController queue when the playback context includes YouTube media IDs.

## [1.0.11] - 2026-07-22

### Changed
- Mini player updates instantly when selecting a new track from search or library; optimistic UI state applies before async queue prep.
- Faster mini player color transition (120ms) when switching songs.

## [1.0.10] - 2026-07-22

### Fixed
- Liked tab playback: full-queue handoff no longer cancels itself before `playSongs` starts; favorites queue now includes YouTube likes via `getFavoriteSongsForSelection()`.

### Changed
- CRT overlay, motion tokens, and list stagger polish across library and player UI.
- Replaced ad-hoc animation durations with `OrpheusMotion` in scroll bar, full player, queue sheet, and setup flows.

## [1.0.9] - 2026-07-21

### Fixed
- Library crash on v1.0.8 (`pageEventFlow` double collection) when opening Liked with YouTube favorites: stop merging YouTube into the local pager flow; show YouTube likes above the paged local list and collect paging only on the visible tab.

## [1.0.8] - 2026-07-21

### Fixed
- Library crash (`pageEventFlow` double collection) when opening Library after liking a YouTube track: cache paging flows in `LibraryStateHolder`, collect only per visible tab, and merge YouTube favorites without re-subscribing to the local pager.

## [1.0.7] - 2026-07-21

### Fixed
- Library crash from collecting the same Paging 3 flow twice (`pageEventFlow` IllegalStateException) when opening songs/albums/artists/liked tabs.
- Removed duplicate `cachedIn` subscription on `songsPagingFlow` in `PlayerViewModel` and simplified repository `Pager` wiring.

## [1.0.6] - 2026-07-21

### Changed
- CRT/terminal UI redesign: flat corners everywhere, black monochrome palette, green accent, JetBrains Mono Nerd typography.
- Sliders, scrollbars, widgets, equalizer, and canvas draws use square shapes instead of rounded pills/circles.
- Default player theme is now static DEFAULT instead of album-art-driven colors for new installs.

### Removed
- RoundedStarShape and remaining smooth-corner shape helpers superseded by TerminalCornerShape.

## [0.1.0] - 2026-06-09

### Initial release
- First public FOSS release of Orpheus, an OSS-focused Android music player.
- Includes local music playback, playlists, favorites, lyrics, listening stats, dynamic Material 3 theming, widgets, and backup/restore.
- Keeps self-hosted library support for Navidrome/Subsonic and Jellyfin, plus optional LRCLIB lyrics and Deezer artist artwork lookups.

### Removed for FOSS
- Removed non-FOSS and Google Play oriented integrations: Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play Store billing, Firebase, Crashlytics, and Google Play Services runtime dependencies.
- Removed public scrobbling integrations such as Last.fm and ListenBrainz; self-hosted Navidrome/Subsonic playback reporting remains scoped to the user's own server.
- Removed bundled translations and the in-app language selector for the first FOSS release; the initial source release ships with English resources only.
- Removed release paths that depended on local/private signing artifacts, dummy signing values, or app-store-only assumptions.

### Release readiness
- Added F-Droid metadata, Fastlane store metadata, dependency/license documentation, privacy notes, security notes, and contributor guidance.
- Release builds now stay unsigned when local signing keys are absent, and `orpheus.disableReleaseSigning=true` forces unsigned verification builds even on a maintainer machine.
- Documented third-party asset and dependency licenses, including native/binary Maven artifacts and JitPack source trails.

### Security and privacy
- The loopback cloud-stream proxy now requires a per-session token so other apps on the device cannot stream the user's cloud library by guessing local proxy URLs.
- Backup restore now ignores preference keys owned by dedicated module handlers, preventing crafted global-settings payloads from bypassing module validation.
- Release logging is tightened so HTTP request headers and remaining raw Android logs do not bypass the Timber release filter.

### App polish included in this FOSS release
- Added smart playlist persistence, duplicate-track scanning, playback speed control, clearer playback/sync failure messages, and retry actions on album/artist detail failures.
- Improved accessibility for toggle states and song row actions.
