package com.yuukifst.orpheus.data.backup.module

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.yuukifst.orpheus.data.backup.model.BackupSection
import com.yuukifst.orpheus.data.backup.model.LikedBackupPayload
import com.yuukifst.orpheus.data.backup.model.YouTubeLikedBackupEntry
import com.yuukifst.orpheus.data.database.FavoritesDao
import com.yuukifst.orpheus.data.database.FavoritesEntity
import com.yuukifst.orpheus.data.database.LikedOrderDao
import com.yuukifst.orpheus.data.database.YouTubeCachedTrackDao
import com.yuukifst.orpheus.data.database.YouTubeCachedTrackEntity
import com.yuukifst.orpheus.data.database.mediaId
import com.yuukifst.orpheus.data.library.mergeLikedManualOrder
import com.yuukifst.orpheus.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesModuleHandler @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val youTubeCachedTrackDao: YouTubeCachedTrackDao,
    private val likedOrderDao: LikedOrderDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.FAVORITES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(currentPayload())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        currentPayload().entryCount()
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val parsed = parsePayload(payload)
        mergeLocal(parsed.local)
        mergeYouTube(parsed.youtube)
        mergeOrder(parsed.order)
    }

    override suspend fun rollback(snapshot: String) = withContext(Dispatchers.IO) {
        val parsed = parsePayload(snapshot)
        favoritesDao.replaceAll(parsed.local.filter { it.isFavorite })
        replaceYouTubeFavorites(parsed.youtube)
        if (parsed.order.isNotEmpty()) {
            likedOrderDao.replaceAllOrdered(parsed.order)
        } else {
            likedOrderDao.clear()
        }
    }

    private suspend fun currentPayload(): LikedBackupPayload {
        val local = favoritesDao.getAllFavoritesOnce()
        val youtube = youTubeCachedTrackDao.getFavoriteTracksOnce().map { entity ->
            YouTubeLikedBackupEntry(
                videoId = entity.videoId,
                title = entity.title,
                channelName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                durationMs = entity.durationMs,
                displayTitle = entity.displayTitle,
                favoritedAt = entity.favoritedAt
            )
        }
        val order = likedOrderDao.getAllOrdered().map { it.mediaId }
        return LikedBackupPayload(version = 2, local = local, youtube = youtube, order = order)
    }

    private fun parsePayload(payload: String): LikedBackupPayload {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            val type = TypeToken.getParameterized(List::class.java, FavoritesEntity::class.java).type
            val local: List<FavoritesEntity> = gson.fromJson(element, type)
            return LikedBackupPayload(version = 1, local = local, youtube = emptyList())
        }
        return gson.fromJson(element, LikedBackupPayload::class.java)
    }

    private suspend fun mergeLocal(local: List<FavoritesEntity>) {
        local.filter { it.isFavorite }.forEach { favoritesDao.setFavorite(it) }
    }

    private suspend fun mergeYouTube(entries: List<YouTubeLikedBackupEntry>) {
        entries.forEach { entry ->
            if (entry.videoId.isBlank()) return@forEach
            val existing = youTubeCachedTrackDao.getByVideoId(entry.videoId)
            val favoritedAt = entry.favoritedAt ?: existing?.favoritedAt ?: System.currentTimeMillis()
            youTubeCachedTrackDao.upsert(
                YouTubeCachedTrackEntity(
                    videoId = entry.videoId,
                    title = entry.title,
                    channelName = entry.channelName,
                    thumbnailUrl = entry.thumbnailUrl,
                    durationMs = entry.durationMs,
                    displayTitle = entry.displayTitle,
                    isFavorite = true,
                    lastPlayedAt = existing?.lastPlayedAt ?: 0L,
                    favoritedAt = favoritedAt
                )
            )
        }
    }

    private suspend fun mergeOrder(backupOrder: List<String>) {
        if (backupOrder.isEmpty()) return

        val favoriteIds = currentFavoriteMediaIds()
        val existingOrder = likedOrderDao.getAllOrdered().map { it.mediaId }
        val backupOrdered = backupOrder.filter { it in favoriteIds }
        val deviceOnlyOrder = existingOrder.filter { it in favoriteIds && it !in backupOrdered.toSet() }
        val combinedOrder = backupOrdered + deviceOnlyOrder
        val mergedOrder = mergeLikedManualOrder(
            favoriteMediaIds = favoriteIds,
            orderedMediaIds = combinedOrder,
            dateLikedById = currentDateLikedById(),
        )
        likedOrderDao.replaceAllOrdered(mergedOrder)
    }

    private suspend fun currentFavoriteMediaIds(): Set<String> {
        val local = favoritesDao.getAllFavoritesOnce()
            .filter { it.isFavorite }
            .map { it.songId.toString() }
        val youtube = youTubeCachedTrackDao.getFavoriteTracksOnce().map { it.mediaId() }
        return (local + youtube).toSet()
    }

    private suspend fun currentDateLikedById(): Map<String, Long> {
        val local = favoritesDao.getAllFavoritesOnce()
            .filter { it.isFavorite }
            .associate { it.songId.toString() to it.timestamp }
        val youtube = youTubeCachedTrackDao.getFavoriteTracksOnce().associate { entity ->
            entity.mediaId() to (entity.favoritedAt ?: 0L)
        }
        return local + youtube
    }

    private suspend fun replaceYouTubeFavorites(entries: List<YouTubeLikedBackupEntry>) {
        val desiredIds = entries.map { it.videoId }.filter { it.isNotBlank() }.toSet()
        youTubeCachedTrackDao.getFavoriteTracksOnce().forEach { existing ->
            if (existing.videoId !in desiredIds) {
                youTubeCachedTrackDao.upsert(existing.copy(isFavorite = false, favoritedAt = null))
            }
        }
        mergeYouTube(entries)
    }
}
