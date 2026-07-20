# Orpheus — Product Requirements Document (PRD)

**Repository:** https://github.com/YuukiFST/Orpheus
**Package ID:** `com.yuukifst.orpheus`
**License:** GPLv3
**Base project:** Fork of [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) (MIT-licensed, official open-source edition of PixelPlayer)
**Target platform:** Android only (API 30+ recommended, minimum aligned with PixelPlayerOSS baseline)
**Document status:** Approved for implementation. This PRD is the source of truth for an autonomous development agent building this project. Do not deviate from decisions marked as final without explicit confirmation from the project owner.

---

## 1. Product Vision

Orpheus is a lightweight, ad-free, offline-capable Android music player that combines:

1. A local music library player (inherited from PixelPlayerOSS).
2. The ability to **search for and stream any song available on YouTube**, directly, in audio-only mode, without ads, without watching a video, and without a companion server — inspired by the core value proposition of SnapTube's music search feature, but stripped of everything else SnapTube bundles (WhatsApp status downloader, phone cleaner/optimizer, and heavy ad load).

The guiding design principle for every decision in this document is: **lightweight, performant, efficient, no bloatware, no ads, no unnecessary features.**

### 1.1 Explicit Non-Goals

To keep the agent from scope-creeping, the following are explicitly **out of scope** for v1:

- No video playback of any kind (audio-only, always).
- No WhatsApp status download, file cleaner, phone optimizer, or any non-music-related utility.
- No ads, no in-app purchases, no monetization of any kind.
- No analytics, telemetry, or crash reporting sent off-device.
- No Navidrome / Subsonic / Jellyfin integration (explicitly removed from the PixelPlayerOSS base).
- No Android Auto support (deferred to a future version, not part of v1).
- No YouTube playlist import.
- No pasting a direct YouTube link to play a video (search-by-text only).
- No automatic sync/rebase with upstream PixelPlayerOSS after the initial fork.

---

## 2. Legal & Licensing (Read First — Governs Implementation Constraints)

This section is foundational and constrains architectural choices throughout the rest of the document. The agent must not violate these constraints.

### 2.1 License

- **PixelPlayerOSS** is MIT-licensed. It is the correct fork base (not the proprietary PixelPlayer, whose license changed to proprietary as of 2026-05-12).
- **NewPipeExtractor** (the library used for YouTube search/extraction) is **GPLv3**.
- Because YouTube search/extraction is a **core, non-optional feature** of Orpheus (not an optional plugin), including NewPipeExtractor causes GPLv3 "copyleft contamination" of the entire codebase.
- **Decision: Orpheus as a whole project is licensed under GPLv3.** Replace the MIT `LICENSE` file inherited from PixelPlayerOSS with the full GPLv3 license text. Retain a `THIRD_PARTY_NOTICES.md` (or equivalent) crediting PixelPlayerOSS (MIT) and NewPipeExtractor (GPLv3) and any other inherited dependencies with their original licenses.

### 2.2 Legal Disclaimer (Required)

Because Orpheus extracts audio streams from YouTube by parsing rather than using YouTube's official API — which is against YouTube's Terms of Service, though technically the same approach used by NewPipe, PipePipe, and YouPipe, all of which remain active and distributed on F-Droid/GitHub — the README **must** include a disclaimer following the established convention in that ecosystem. At minimum, the disclaimer must state:

- The project is provided for educational/personal-use purposes.
- Orpheus is not affiliated with, endorsed by, or sponsored by YouTube or Google.
- The user is solely responsible for how they use the application, and for compliance with applicable terms of service and laws in their jurisdiction.

No further legal engineering (restrictive commercial-use terms, additional legal review) is required for v1.

### 2.3 Extraction Approach (Architectural Consequence)

- Use **NewPipeExtractor**, embedded directly inside the Android app (client-side, on-device extraction). No self-hosted backend, no dependency on third-party Piped/Invidious instances, no yt-dlp/Python bridge.
- This keeps the app fully self-contained: no server for the project owner to maintain, no external infrastructure dependency, and it is the proven approach used by NewPipe for years.
- **Consequence of this architecture (explicitly desired):** because playback goes directly to ExoPlayer via an extracted stream URL, it never passes through YouTube's official player — so mid-content ads (the type SnapTube also avoids) are structurally impossible, not just "blocked." No ad-blocking logic is needed at all.

---

## 3. Base Project & Tech Stack

### 3.1 Fork Base

Fork from `PixelPlayerHQ/PixelPlayerOSS`, **not** the original `PixelPlayerHQ/PixelPlayer` (which is proprietary as of 2026-05-12).

PixelPlayerOSS already strips out, relative to the original PixelPlayer, the following (do not re-add any of these):

- Telegram integration
- NetEase / QQ Music integration
- Google Drive integration
- Gemini / AI features
- Cast support
- Wear OS support
- Play Store billing
- Firebase / Crashlytics
- Google Play Services dependencies

### 3.2 Stack (unchanged from PixelPlayerOSS — do not replace)

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Media playback | AndroidX Media3 (ExoPlayer) |
| Dependency injection | Hilt |
| Local persistence | Room |
| Networking | Retrofit |
| Tag editing (local files + downloaded YouTube files) | TagLib (already used by PixelPlayerOSS) |

**Rationale (do not revisit):** Kotlin + Compose + Media3 is the current best-practice native Android stack for a media-focused app in terms of performance, responsiveness, and efficiency. Rewriting in a cross-platform framework (Flutter, React Native) would be a regression, not an improvement, against the project's stated performance goals.

### 3.3 New Dependency

- **NewPipeExtractor** (GPLv3), added via JitPack, for YouTube search and stream extraction.

### 3.4 Removed From Base

- All Navidrome / Subsonic client code, screens, and configuration UI.
- All Jellyfin client code, screens, and configuration UI.
- Any associated Retrofit service definitions, server-login screens, and sync logic tied to the above two.

### 3.5 Retained Optional Integrations (off by default, unchanged from base)

- **LRCLIB** — online synced-lyrics lookup, opt-in, used for both local library tracks and YouTube-sourced tracks (via title + edited title as query).
- **Deezer** — online artist photo lookup, opt-in, unchanged from base behavior.

---

## 4. Information Architecture

The app has **three top-level, clearly separated sections**. They must not be merged or cross-contaminated in the data model or UI:

1. **Library** — the existing local-file music library from PixelPlayerOSS (unchanged behavior, minus the removed Navidrome/Jellyfin sources).
2. **Search** (new) — YouTube search interface. Ephemeral by nature; not a media browser/library.
3. **Downloads** (new) — a dedicated section containing only YouTube tracks the user has explicitly downloaded. Separate from both Library and Search.

### 4.1 Rationale

YouTube-sourced content has inherently different metadata quality (raw titles, no verified tags) than the user's curated local library, and mixing them would pollute both the "trusted library" experience and the "quick search and play" experience. Keeping Search, Downloads, and Library as three distinct sections was an explicit, repeated decision throughout scoping.

---

## 5. Feature Specification: Search (YouTube)

### 5.1 Search Behavior

- Text-only search box. No URL-paste-to-play support in v1.
- Query goes to NewPipeExtractor's YouTube search, returning whatever YouTube's search returns — **no automatic content filtering** (no forced "music only" category, no hiding of live streams, lyric videos, covers, podcasts, etc.). The user sees the same breadth of results they would get searching YouTube directly.
- Below the search box, maintain a **local search history** (list of the user's own past queries, stored on-device only). This is purely a local convenience list — it does not call any YouTube "trending" or "suggestions" endpoint. No YouTube homepage/discovery feed of any kind.

### 5.2 Result List Item

Each search result displays:
- **Thumbnail:** YouTube's default video thumbnail (as returned by NewPipeExtractor). Not editable, not replaced by channel avatar.
- **Title:** The raw YouTube video title, unmodified, no cleanup heuristics applied.
- **Channel name:** displayed as supplementary metadata (not manipulated into an "Artist" field via string parsing).
- **Duration.**

### 5.3 Actions on a Search Result

Each result must support exactly two primary actions:

1. **Play once** — extracts the audio stream and plays it immediately as a standalone, non-queued play. When it finishes, playback **stops** (no autoplay, no related-video suggestion, no next YouTube result).
2. **Add to playlist** — adds the track (metadata: video ID, title, channel, thumbnail URL, duration) to one of the user's own Orpheus playlists (see Section 7). Does not trigger a download.

There is no third action to import a YouTube playlist by link.

### 5.4 Audio-Only Extraction

- Only extract the audio stream (e.g., M4A/Opus/WebM-audio track) via NewPipeExtractor. Never extract or play a video stream.
- **Always select the highest available audio bitrate/quality** returned by NewPipeExtractor for that video. No quality settings screen, no per-connection-type adaptive logic, no user-facing quality toggle in v1.

### 5.5 Extraction Failure Handling

When stream extraction or playback fails for a given track:

1. Automatically retry the extraction **once** (covers transient network/instability failures — this is a lightweight, single retry, not an aggressive retry loop).
2. If the retry also fails:
   - If the track was played via "Play once": show a brief, non-blocking error message (snackbar/toast), e.g. "Couldn't play this track."
   - If the track is part of a playlist/queue: show the same non-blocking error message, and **automatically skip to the next track in the queue** rather than halting playback. This is especially important given looping playlist behavior (Section 6.2) — a single broken track must never stall the whole queue.

---

## 6. Feature Specification: Playback Behavior

### 6.1 Single Track ("Play Once")

- Plays the selected track.
- When it finishes: **playback stops**. No autoplay, no queue continuation, no related suggestions. This applies both to Library tracks played standalone and to YouTube "Play once" tracks.

### 6.2 Playlist Playback

- When a user is playing through one of their own playlists (see Section 7):
  - On track end → automatically advance to the **next track in the playlist**.
  - On reaching the **end of the playlist** → **loop back to the first track** and continue (i.e., the playlist plays on infinite loop until the user manually stops it).
- This looping behavior applies uniformly to playlists regardless of whether their tracks are local library tracks, YouTube tracks played via streaming, or a mix of both.

### 6.3 Hybrid Streaming/Local Playback for Playlists (Important Architectural Detail)

When a playlist reaches a track that originated from YouTube:

1. Check whether that exact track is also present in the **Downloads** section (i.e., has been manually downloaded before).
2. **If yes:** play the local downloaded audio file directly (no network extraction needed, works offline).
3. **If no:** perform a fresh NewPipeExtractor stream extraction and play via streaming (requires network).

This must be a simple existence check (e.g., match by YouTube video ID against the Downloads table) — **do not** implement automatic "download on playlist add" behavior. Downloading remains exclusively a manual, per-track, opt-in user action (Section 8). Adding a track to a playlist never triggers a download by itself.

### 6.4 No Autoplay / No Related-Video Suggestions

Regardless of context (single play or playlist), Orpheus must never use NewPipeExtractor's "related videos" data to automatically continue playback beyond what is explicitly defined above (stop on single play; loop on playlist end). This is a deliberate rejection of YouTube's/SnapTube's "autoplay into unrelated content" pattern.

---

## 7. Feature Specification: Playlists

- Playlists are created and managed entirely within Orpheus (name, add/remove tracks, reorder) — this is the same CRUD structure PixelPlayerOSS already has for the local library, extended to also accept YouTube-sourced track entries.
- A playlist may contain a mix of local library tracks and YouTube tracks.
- Tracks are added to a playlist either:
  - Directly from a Search result ("Add to playlist" action — Section 5.3), or
  - From the Library, using existing PixelPlayerOSS behavior.
- There is no feature to import an entire YouTube playlist by pasting a playlist link. Playlists are built manually by the user, one track at a time.

---

## 8. Feature Specification: Downloads

### 8.1 Trigger

- Download is a **manual, explicit, per-track action** available from a Search result (and/or from a track already added to a playlist). There is no automatic caching of every played track.

### 8.2 Storage Location

- Downloaded files are saved to a **public storage location**: `Music/Orpheus/` (i.e., the shared/public Music directory, not app-private internal storage).
- Rationale: the user wants these files to survive app uninstallation, be manageable via any file manager or PC connection, and be usable as a natural backup — not to be treated as disposable app-internal cache.
- Implementation must correctly handle Android Scoped Storage requirements for writing to the public Music directory on the target API levels.

### 8.3 Embedded Metadata (ID3 Tags)

- When a track is downloaded, write ID3 tags directly into the audio file using TagLib (already a PixelPlayerOSS dependency):
  - **Title:** the track's current title as shown in Orpheus (i.e., respecting any manual rename — see 8.4).
  - **Cover art:** the YouTube thumbnail, embedded as the file's cover image.
- This ensures the file displays correctly (title + artwork) if opened in any other music player or on a PC, not just within Orpheus.

### 8.4 Title Editing

- YouTube video titles are shown raw/unmodified by default (per Section 5.2), but the user can manually rename any track (in Search results, in a playlist, and/or once downloaded) via a simple edit action. This is a local, user-controlled override — no automatic title-cleanup heuristics, no artist/title parsing logic.
- Thumbnails/cover art are **not** user-editable — always the default YouTube thumbnail, in both Search and Downloads.

### 8.5 Section Isolation

- Downloaded tracks appear **only** in the dedicated "Downloads" section.
- Downloaded tracks must **not** be merged into or displayed within the Library section (Library remains exclusively the user's own local file collection, untouched by this feature).
- Downloaded tracks also do not persist inside the "Search" tab's UI state — Search is a transient/ephemeral browsing surface; Downloads is the persistent home for anything saved from it.

---

## 9. Removed PixelPlayerOSS Features (Explicit Removal List)

The development agent must remove the following from the forked codebase. This includes removing associated UI screens, navigation routes, Retrofit service/API definitions, Room entities/DAOs tied exclusively to these features, and any related settings entries:

- Navidrome / Subsonic client integration (server login screen, sync logic, associated data source).
- Jellyfin client integration (server login screen, sync logic, associated data source).

Do **not** remove:

- LRCLIB integration (opt-in synced lyrics) — retained, unchanged, and should also be usable for YouTube-sourced tracks (query LRCLIB using the track's current title, respecting user renames per 8.4).
- Deezer integration (opt-in artist photo lookup) — retained, unchanged, local-library-only behavior as it already exists in the base.

---

## 10. Privacy & Telemetry

- **Zero telemetry.** No usage analytics, no crash reporting, no data of any kind transmitted off-device without an explicit, per-action user request (e.g., the opt-in LRCLIB/Deezer lookups the user has already enabled are the only outbound calls besides YouTube search/extraction itself).
- No user accounts, no sign-in, no cloud sync of any kind.

---

## 11. Branding & Identity

- **App name:** Orpheus (Greek mythology reference — the musician whose song could enchant gods and nearly reclaim the dead from the underworld).
- **Repository:** `github.com/YuukiFST/Orpheus`
- **Package ID:** `com.yuukifst.orpheus`
- **Visual identity:** Keep the PixelPlayerOSS visual design as-is (Material 3, dynamic Material You theming). Only the app name, app icon placeholder text/label, and package identifiers change. No new custom color palette, no custom launcher icon artwork required for v1 (a themed icon can be revisited post-v1, but is explicitly not required now).
- **UI language:** English only for v1 (both source code strings and UI-facing text). No localization/i18n system required for v1.

---

## 12. Distribution

- **Primary channel:** GitHub Releases on `github.com/YuukiFST/Orpheus` (signed APK attached to each release).
- **Secondary channel:** Support installation/update via **Obtainium** (an Android app that tracks GitHub Releases and handles install/update notifications) by ensuring the repository's release tagging and APK naming are consistent and Obtainium-compatible. No F-Droid submission for v1 (deferred; the project *is* eligible for F-Droid in the future, as confirmed by precedent — NewPipe, PipePipe, and YouPipe are all listed there despite the same YouTube-extraction pattern — but the additional build-reproducibility and metadata maintenance overhead is not justified for v1).
- No Google Play Store distribution (would not survive Play policy review given the YouTube extraction feature; this is expected and accepted).

---

## 13. Upstream Relationship

- Orpheus is a **one-time, standalone fork** of PixelPlayerOSS. There is no requirement or process to periodically merge/rebase future PixelPlayerOSS upstream changes into Orpheus.
- Future selective adoption of a specific upstream improvement may be considered later as a standalone, manually-evaluated task — it is explicitly **not** part of this PRD's scope or an ongoing obligation for the development agent.

---

## 14. Acceptance Criteria Summary (Definition of Done for v1)

The implementation is considered complete when all of the following hold:

- [ ] Project is a fork of PixelPlayerOSS with package ID `com.yuukifst.orpheus`, app name "Orpheus", licensed GPLv3, with updated `LICENSE` and `THIRD_PARTY_NOTICES.md`.
- [ ] README includes the required legal disclaimer (Section 2.2).
- [ ] Navidrome and Jellyfin integrations are fully removed (code, UI, settings, dependencies).
- [ ] LRCLIB and Deezer opt-in integrations remain functional, unchanged from base behavior, and LRCLIB also works for YouTube-sourced tracks.
- [ ] NewPipeExtractor is integrated as an embedded, on-device dependency (no backend server).
- [ ] A new **Search** section exists: text search only, unfiltered YouTube results, local-only search history, no trending/discovery feed, no URL-paste support.
- [ ] Each search result supports "Play once" (stops on completion, no autoplay) and "Add to playlist."
- [ ] Audio-only extraction, always at the highest available quality; no quality settings UI.
- [ ] A new **Downloads** section exists, fully separate from Library and Search, storing files in the public `Music/Orpheus/` directory with embedded ID3 title + cover art.
- [ ] Playlists support mixed local + YouTube tracks; playlist playback advances on track end and loops at playlist end.
- [ ] Playlist playback prefers a locally downloaded file over streaming when the track exists in Downloads; otherwise streams via fresh extraction.
- [ ] Extraction failures trigger one automatic retry, then a non-blocking error + auto-skip-to-next-in-queue if part of a playlist.
- [ ] Titles are editable per-track by the user; thumbnails are not editable anywhere.
- [ ] Zero telemetry/analytics/crash-reporting of any kind.
- [ ] No Android Auto support, no video playback, no YouTube playlist import, no ads, no unrelated utilities (status downloader, cleaner, optimizer, etc.).
- [ ] UI language is English throughout.
- [ ] GitHub Releases publishing is set up with Obtainium-compatible release/APK naming.

---

## 15. Open Items for Future Versions (Not Part of v1)

Explicitly deferred, not to be built now, but noted for future reference:

- Android Auto support (technically viable via Media3 `MediaLibraryService`, no Play Services required).
- F-Droid distribution.
- Themed app icon / custom visual identity.
- Localization (Portuguese and other languages).
- Possible selective adoption of future upstream PixelPlayerOSS improvements.
