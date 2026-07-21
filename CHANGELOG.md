# Changelog

All notable changes to Orpheus will be documented in this file.

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
