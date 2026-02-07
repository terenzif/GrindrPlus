package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.grindrplus.persistence.model.BlockEventEntity

@Dao
interface BlockEventDao {
    @Insert
    fun insert(event: BlockEventEntity)

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC")
    fun getAll(): List<BlockEventEntity>

    @Query("DELETE FROM block_events")
    fun deleteAll()
}
