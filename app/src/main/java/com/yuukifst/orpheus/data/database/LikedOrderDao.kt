package com.yuukifst.orpheus.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LikedOrderDao {
    @Query("SELECT * FROM liked_order ORDER BY sort_order ASC")
    suspend fun getAllOrdered(): List<LikedOrderEntity>

    @Query("DELETE FROM liked_order")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<LikedOrderEntity>)

    @Query("DELETE FROM liked_order WHERE media_id = :mediaId")
    suspend fun delete(mediaId: String)

    @Transaction
    suspend fun replaceAllOrdered(mediaIds: List<String>) {
        clear()
        insertAll(mediaIds.mapIndexed { index, id -> LikedOrderEntity(id, index) })
    }
}
