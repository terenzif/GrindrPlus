package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "block_events", indices = [Index("timestamp")])
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: String,
    val displayName: String,
    val eventType: String,
    val timestamp: Long,
    val packageName: String
)
