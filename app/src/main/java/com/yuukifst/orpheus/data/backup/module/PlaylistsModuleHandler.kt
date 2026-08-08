package com.yuukifst.orpheus.data.backup.module

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.SortOption
import com.yuukifst.orpheus.data.model.isSmartPlaylistSource
import com.yuukifst.orpheus.data.backup.model.BackupSection
import com.yuukifst.orpheus.data.backup.model.PlaylistConflict
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictAction
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictMatchReason
import com.yuukifst.orpheus.data.backup.model.PlaylistYouTubeBackupEntry
import com.yuukifst.orpheus.data.database.MusicDao
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.SongSummary
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.preferences.PreferenceBackupEntry
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import com.yuukifst.orpheus.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val youTubePlaylistDao: YouTubePlaylistDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        // Only export local playlists. Cloud playlists are tied to service auth
        // and would be empty on restore.
        val playlists = allPlaylists.filter { isBackedUpPlaylistSource(it.source) }

        // Build a set of cloud song IDs to exclude from backup
        val cloudSongIds = buildCloudSongIdSet()

        // Get metadata for local songs so we can match them on restore
        val allLocalSummaries = musicDao.getAllLocalSongSummaries()
        val summaryById = allLocalSummaries.associateBy { it.id.toString() }

        // Filter cloud songs out of playlists and collect metadata
        val songMetadata = mutableMapOf<String, SongMetadataEntry>()
        val filteredPlaylists = playlists.map { playlist ->
            val localSongIds = playlist.songIds.filter { id -> id !in cloudSongIds }
            // Collect metadata for matched local songs
            localSongIds.forEach { id ->
                if (id !in songMetadata) {
                    summaryById[id]?.let { summary ->
                        songMetadata[id] = SongMetadataEntry(
                            title = summary.title,
                            artist = summary.artistName,
                            album = summary.albumName,
                            duration = summary.duration
                        )
                    }
                }
            }
            playlist.copy(songIds = localSongIds)
        }

        // Encode cover images as Base64
        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val backedUpIds = filteredPlaylists.map { it.id }.toSet()
        val youtubeTracks = youTubePlaylistDao.getAllOnce()
            .filter { it.playlistId in backedUpIds }
            .map { it.toBackupEntry() }

        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null },
            youtubeTracks = youtubeTracks.ifEmpty { null },
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .filter { isBackedUpPlaylistSource(it.source) }
        val backedUpIds = playlists.map { it.id }.toSet()
        val youtubeCount = youTubePlaylistDao.getAllOnce().count { it.playlistId in backedUpIds }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        playlists.size + youtubeCount + orderModes.size + if (sortOption.isNotBlank()) 1 else 0
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        // Snapshot captures the current state as-is (including cloud songs) for rollback
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
        val payload = PlaylistsBackupPayload(
            playlists = playlists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            youtubeTracks = youTubePlaylistDao.getAllOnce().map { it.toBackupEntry() }.ifEmpty { null },
        )
        gson.toJson(payload)
    }

    /**
     * Interface entry used only when decisions are empty (no conflicts). Prefer
     * [restore] with explicit decisions from [RestorePlan.playlistConflictDecisions].
     */
    override suspend fun restore(payload: String) = restore(payload, emptyMap())

    /**
     * Import playlists without wiping device data.
     *
     * - New playlists (no id/name match) are created.
     * - Conflicts require a [PlaylistConflictAction] keyed by backup playlist id.
     * - Sort option and per-playlist song order modes are left untouched.
     */
    suspend fun restore(
        payload: String,
        decisions: Map<String, PlaylistConflictAction>
    ) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload, decisions)
            return@withContext
        }

        val parsed = parsePayloadOrThrow(payload)
        applyPlaylistImport(
            backupPlaylists = parsed.playlists.orEmpty(),
            songMetadata = parsed.songMetadata,
            coverImages = parsed.coverImages,
            youtubeTracks = parsed.youtubeTracks.orEmpty(),
            decisions = decisions
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    /** Snapshot rollback must fully replace state, including order modes / sort. */
    override suspend fun rollback(snapshot: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(snapshot)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntriesReplacing(snapshot)
            return@withContext
        }

        val parsed = parsePayloadOrThrow(snapshot)
        val playlists = parsed.playlists.orEmpty()
        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )
        replaceAllYouTubeTracks(parsed.youtubeTracks.orEmpty())
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    suspend fun detectConflicts(payload: String): List<PlaylistConflict> = withContext(Dispatchers.IO) {
        val backupPlaylists = parseBackupPlaylists(payload)
        val devicePlaylists = playlistPreferencesRepository.getPlaylistsOnce()
            .filter { isBackedUpPlaylistSource(it.source) }
        detectConflicts(backupPlaylists, devicePlaylists)
    }

    fun detectConflicts(
        backupPlaylists: List<Playlist>,
        devicePlaylists: List<Playlist>
    ): List<PlaylistConflict> {
        val claimedDeviceIds = mutableSetOf<String>()
        return backupPlaylists.mapNotNull { backup ->
            val match = findDeviceMatch(backup, devicePlaylists, claimedDeviceIds) ?: return@mapNotNull null
            claimedDeviceIds.add(match.playlist.id)
            PlaylistConflict(
                backupPlaylistId = backup.id,
                backupPlaylistName = backup.name,
                devicePlaylistId = match.playlist.id,
                devicePlaylistName = match.playlist.name,
                matchReason = match.reason
            )
        }
    }

    // ---- Cover image helpers ----

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read cover image: $path")
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to restore cover image for playlist ${playlist.id}")
                playlist.copy(coverImageUri = null)
            }
        }
    }

    // ---- Song matching ----

    /**
     * Resolves backup song IDs to current device song IDs using metadata matching.
     *
     * Strategy:
     * 1. Direct ID match + metadata verification → confirmed
     * 2. If direct ID exists but metadata doesn't match → try metadata match (avoids false positives)
     * 3. If direct ID doesn't exist → try metadata match
     * 4. Metadata match: title + artist (case-insensitive), disambiguate with album + duration
     * 5. No confident match → song is dropped from the playlist (kept as unresolved would risk false matches)
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, SongMetadataEntry>
    ): List<Playlist> {
        val localSummaries = musicDao.getAllLocalSongSummaries()
        val currentSongsById = localSummaries.associateBy { it.id.toString() }

        // Build index for metadata matching: normalized "title|artist" → list of candidates
        val metadataIndex = mutableMapOf<String, MutableList<SongSummary>>()
        localSummaries.forEach { song ->
            val key = normalizeMatchKey(song.title, song.artistName)
            metadataIndex.getOrPut(key) { mutableListOf() }.add(song)
        }

        // Build the resolution map: backup songId → resolved songId (or null if unresolved)
        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = resolveSongId(songId, songMetadata, currentSongsById, metadataIndex)
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Timber.tag(TAG).w("Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        // Apply resolution to playlists, dropping unresolved songs
        return playlists.map { playlist ->
            val resolvedSongIds = playlist.songIds.mapNotNull { songId ->
                resolutionCache[songId]
            }
            playlist.copy(songIds = resolvedSongIds)
        }
    }

    private fun resolveSongId(
        backupSongId: String,
        songMetadata: Map<String, SongMetadataEntry>,
        currentSongsById: Map<String, SongSummary>,
        metadataIndex: Map<String, List<SongSummary>>
    ): String? {
        val meta = songMetadata[backupSongId]

        // 1. Try direct ID match
        val directMatch = currentSongsById[backupSongId]
        if (directMatch != null) {
            if (meta == null) {
                // No metadata to verify — accept direct match (same-device restore)
                return backupSongId
            }
            // Verify metadata matches to avoid false positives (e.g., reused MediaStore ID)
            if (metadataMatches(meta, directMatch)) {
                return backupSongId
            }
            // Direct ID exists but is a different song — fall through to metadata matching
        }

        // 2. No metadata available — can't do metadata matching
        if (meta == null) {
            return if (directMatch != null) backupSongId else null
        }

        // 3. Try metadata matching
        val matchKey = normalizeMatchKey(meta.title, meta.artist)
        val candidates = metadataIndex[matchKey] ?: return null

        if (candidates.size == 1) {
            return candidates[0].id.toString()
        }

        // Multiple candidates — disambiguate with album and duration
        val albumMatch = candidates.filter { candidate ->
            normalizeText(candidate.albumName) == normalizeText(meta.album)
        }
        if (albumMatch.size == 1) {
            return albumMatch[0].id.toString()
        }

        // Try duration (within 2 second tolerance)
        val durationCandidates = (albumMatch.ifEmpty { candidates }).filter { candidate ->
            kotlin.math.abs(candidate.duration - meta.duration) <= DURATION_TOLERANCE_MS
        }
        if (durationCandidates.size == 1) {
            return durationCandidates[0].id.toString()
        }

        // Ambiguous — no confident match
        return null
    }

    private fun metadataMatches(meta: SongMetadataEntry, song: SongSummary): Boolean {
        return normalizeText(meta.title) == normalizeText(song.title) &&
            normalizeText(meta.artist) == normalizeText(song.artistName)
    }

    private fun normalizeMatchKey(title: String, artist: String): String {
        return "${normalizeText(title)}|${normalizeText(artist)}"
    }

    private fun normalizeText(text: String): String {
        return text.trim().lowercase()
    }

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllNavidromeSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllJellyfinSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    // ---- Import helpers ----

    private suspend fun applyPlaylistImport(
        backupPlaylists: List<Playlist>,
        songMetadata: Map<String, SongMetadataEntry>?,
        coverImages: Map<String, String>?,
        youtubeTracks: List<PlaylistYouTubeBackupEntry>,
        decisions: Map<String, PlaylistConflictAction>
    ) {
        val resolvedPlaylists = if (songMetadata != null && songMetadata.isNotEmpty()) {
            resolvePlaylists(backupPlaylists, songMetadata)
        } else {
            backupPlaylists
        }
        val withCovers = if (coverImages != null && coverImages.isNotEmpty()) {
            restoreCoverImages(resolvedPlaylists, coverImages)
        } else {
            resolvedPlaylists
        }

        val youtubeByBackupPlaylistId = youtubeTracks.groupBy { it.playlistId }
        val devicePlaylists = playlistPreferencesRepository.getPlaylistsOnce()
        val claimedDeviceIds = mutableSetOf<String>()

        withCovers.forEach { backupPlaylist ->
            val match = findDeviceMatch(backupPlaylist, devicePlaylists, claimedDeviceIds)
            val backupYoutube = youtubeByBackupPlaylistId[backupPlaylist.id].orEmpty()
            if (match == null) {
                val created = playlistPreferencesRepository.createPlaylist(
                    name = backupPlaylist.name,
                    songIds = backupPlaylist.songIds,
                    isQueueGenerated = backupPlaylist.isQueueGenerated,
                    coverImageUri = backupPlaylist.coverImageUri,
                    coverColorArgb = backupPlaylist.coverColorArgb,
                    coverIconName = backupPlaylist.coverIconName,
                    coverShapeType = backupPlaylist.coverShapeType,
                    coverShapeDetail1 = backupPlaylist.coverShapeDetail1,
                    coverShapeDetail2 = backupPlaylist.coverShapeDetail2,
                    coverShapeDetail3 = backupPlaylist.coverShapeDetail3,
                    coverShapeDetail4 = backupPlaylist.coverShapeDetail4,
                    customId = backupPlaylist.id,
                    source = backupPlaylist.source,
                    displayOrder = backupPlaylist.displayOrder,
                )
                replaceYouTubeForPlaylist(created.id, backupYoutube)
                return@forEach
            }

            claimedDeviceIds.add(match.playlist.id)
            val action = decisions[backupPlaylist.id]
                ?: throw IllegalStateException(
                    "Missing conflict decision for playlist \"${backupPlaylist.name}\" (${backupPlaylist.id})."
                )
            when (action) {
                PlaylistConflictAction.IGNORE -> Unit
                PlaylistConflictAction.MERGE -> {
                    val mergedSongs = (match.playlist.songIds + backupPlaylist.songIds).distinct()
                    val merged = mergeMetadata(match.playlist, backupPlaylist).copy(songIds = mergedSongs)
                    playlistPreferencesRepository.updatePlaylist(merged)
                    mergeYouTubeForPlaylist(match.playlist.id, backupYoutube)
                }
                PlaylistConflictAction.REPLACE -> {
                    val replaced = backupPlaylist.copy(
                        id = match.playlist.id,
                        createdAt = match.playlist.createdAt,
                        lastModified = System.currentTimeMillis()
                    )
                    playlistPreferencesRepository.updatePlaylist(replaced)
                    replaceYouTubeForPlaylist(match.playlist.id, backupYoutube)
                }
            }
        }
    }

    private suspend fun replaceYouTubeForPlaylist(
        playlistId: String,
        entries: List<PlaylistYouTubeBackupEntry>,
    ) {
        val entities = entries.mapNotNull { it.toEntity(playlistId) }
        youTubePlaylistDao.replaceForPlaylist(playlistId, entities)
    }

    private suspend fun mergeYouTubeForPlaylist(
        playlistId: String,
        entries: List<PlaylistYouTubeBackupEntry>,
    ) {
        if (entries.isEmpty()) return
        val existingIds = youTubePlaylistDao.observeForPlaylist(playlistId).first()
            .map { it.videoId }
            .toSet()
        val toInsert = entries
            .filter { it.videoId.isNotBlank() && it.videoId !in existingIds }
            .mapNotNull { it.toEntity(playlistId) }
        if (toInsert.isNotEmpty()) {
            youTubePlaylistDao.upsertAll(toInsert)
        }
    }

    private suspend fun replaceAllYouTubeTracks(entries: List<PlaylistYouTubeBackupEntry>) {
        val current = youTubePlaylistDao.getAllOnce()
        val targetPlaylistIds = entries.map { it.playlistId }.toSet() +
            current.map { it.playlistId }.toSet()
        targetPlaylistIds.forEach { playlistId ->
            val forPlaylist = entries.filter { it.playlistId == playlistId }
            replaceYouTubeForPlaylist(playlistId, forPlaylist)
        }
    }

    private fun mergeMetadata(device: Playlist, backup: Playlist): Playlist {
        return device.copy(
            name = backup.name.takeIf { it.isNotBlank() } ?: device.name,
            coverImageUri = backup.coverImageUri?.takeIf { it.isNotBlank() } ?: device.coverImageUri,
            coverColorArgb = backup.coverColorArgb ?: device.coverColorArgb,
            coverIconName = backup.coverIconName?.takeIf { it.isNotBlank() } ?: device.coverIconName,
            coverShapeType = backup.coverShapeType ?: device.coverShapeType,
            coverShapeDetail1 = backup.coverShapeDetail1 ?: device.coverShapeDetail1,
            coverShapeDetail2 = backup.coverShapeDetail2 ?: device.coverShapeDetail2,
            coverShapeDetail3 = backup.coverShapeDetail3 ?: device.coverShapeDetail3,
            coverShapeDetail4 = backup.coverShapeDetail4 ?: device.coverShapeDetail4,
            source = backup.source.takeIf { it.isNotBlank() } ?: device.source,
            isQueueGenerated = backup.isQueueGenerated,
            displayOrder = backup.displayOrder,
            lastModified = System.currentTimeMillis()
        )
    }

    private data class DeviceMatch(
        val playlist: Playlist,
        val reason: PlaylistConflictMatchReason
    )

    private fun findDeviceMatch(
        backup: Playlist,
        devicePlaylists: List<Playlist>,
        claimedDeviceIds: Set<String>
    ): DeviceMatch? {
        val available = devicePlaylists.filter {
            it.id !in claimedDeviceIds && isBackedUpPlaylistSource(it.source)
        }
        available.firstOrNull { it.id == backup.id }?.let {
            return DeviceMatch(it, PlaylistConflictMatchReason.ID)
        }
        val normalizedBackupName = normalizeText(backup.name)
        if (normalizedBackupName.isEmpty()) return null
        val nameMatches = available.filter { normalizeText(it.name) == normalizedBackupName }
        val best = nameMatches.maxByOrNull { it.lastModified } ?: return null
        return DeviceMatch(best, PlaylistConflictMatchReason.NAME)
    }

    private fun parsePayloadOrThrow(payload: String): PlaylistsBackupPayload {
        return runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrElse { e ->
            throw IllegalStateException("Playlists payload could not be parsed: ${e.message}", e)
        } ?: throw IllegalStateException("Playlists payload could not be parsed: empty JSON document")
    }

    private fun parseBackupPlaylists(payload: String): List<Playlist> {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            return parseLegacyPlaylists(payload)
        }
        return parsePayloadOrThrow(payload).playlists.orEmpty()
    }

    private fun parseLegacyPlaylists(payload: String): List<Playlist> {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)
        return entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()
    }

    // ---- Legacy format ----

    private suspend fun restoreLegacyPreferenceEntries(
        payload: String,
        decisions: Map<String, PlaylistConflictAction>
    ) {
        val playlists = parseLegacyPlaylists(payload)
        applyPlaylistImport(
            backupPlaylists = playlists,
            songMetadata = null,
            coverImages = null,
            youtubeTracks = emptyList(),
            decisions = decisions
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    private suspend fun restoreLegacyPreferenceEntriesReplacing(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = parseLegacyPlaylists(payload)

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    // ---- Data classes ----

    /** Song metadata stored alongside playlists for cross-device matching. */
    data class SongMetadataEntry(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long
    )

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, SongMetadataEntry>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null,
        /** YouTube Search tracks stored outside playlist.songIds. Null when none / legacy. */
        val youtubeTracks: List<PlaylistYouTubeBackupEntry>? = null,
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val DURATION_TOLERANCE_MS = 2000L

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private fun isBackedUpPlaylistSource(source: String): Boolean =
            source == "LOCAL" || isSmartPlaylistSource(source)

        private fun PlaylistYouTubeTrackEntity.toBackupEntry(): PlaylistYouTubeBackupEntry =
            PlaylistYouTubeBackupEntry(
                playlistId = playlistId,
                videoId = videoId,
                sortOrder = sortOrder,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                displayTitle = displayTitle,
            )

        private fun PlaylistYouTubeBackupEntry.toEntity(targetPlaylistId: String): PlaylistYouTubeTrackEntity? {
            if (videoId.isBlank()) return null
            return PlaylistYouTubeTrackEntity(
                playlistId = targetPlaylistId,
                videoId = videoId,
                sortOrder = sortOrder,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                displayTitle = displayTitle,
            )
        }

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY
        )
    }
}
