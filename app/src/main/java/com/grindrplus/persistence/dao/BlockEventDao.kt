package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.grindrplus.persistence.model.BlockEventEntity

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Insert
    suspend fun insertAll(events: List<BlockEventEntity>)

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<BlockEventEntity>

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}
