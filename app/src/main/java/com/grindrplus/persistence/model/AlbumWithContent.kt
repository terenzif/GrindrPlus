package com.grindrplus.persistence.model

import androidx.room.Embedded
import androidx.room.Relation

data class AlbumWithContent(
    @Embedded val album: AlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "albumId"
    )
    val content: List<AlbumContentEntity>
)
