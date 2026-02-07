package com.grindrplus.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class AlbumDaoBenchmarkTest {

    private lateinit var db: GPDatabase
    private lateinit var dao: AlbumDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GPDatabase::class.java)
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.albumDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun benchmarkNPlusOneVsRelation() = runBlocking {
        // 1. Setup Data
        val albumCount = 50
        val contentPerAlbum = 5
        val albums = (1..albumCount).map { i ->
            AlbumEntity(
                id = i.toLong(),
                albumName = "Album $i",
                createdAt = "2023-01-01",
                profileId = i.toLong() * 10,
                updatedAt = "2023-01-01"
            )
        }
        dao.upsertAlbums(albums)

        val contents = albums.flatMap { album ->
            (1..contentPerAlbum).map { j ->
                AlbumContentEntity(
                    id = album.id * 1000 + j,
                    albumId = album.id,
                    contentType = "image",
                    coverUrl = "http://example.com/cover/${album.id}/$j",
                    thumbUrl = "http://example.com/thumb/${album.id}/$j",
                    url = "http://example.com/url/${album.id}/$j"
                )
            }
        }
        dao.upsertAlbumContents(contents)

        // Warmup N+1
        repeat(5) {
             val dbAlbums = dao.getAlbums()
             dbAlbums.forEach { album ->
                 dao.getAlbumContent(album.id)
             }
        }

        // 2. Measure N+1 Approach
        val iterations = 5
        var totalTime = 0L
        repeat(iterations) {
            val time = measureTimeMillis {
                 val dbAlbums = dao.getAlbums()
                 dbAlbums.forEach { album ->
                     val content = dao.getAlbumContent(album.id)
                     // Simulate usage
                     content.size
                 }
            }
            totalTime += time
        }
        println("N+1 Approach Average Time: ${totalTime / iterations} ms")

        // 3. Measure Relation Approach
        // Warmup
        repeat(5) {
            dao.getAlbumsWithContent()
        }

        var totalTimeRelation = 0L
        repeat(iterations) {
            val time = measureTimeMillis {
                val albumsWithContent = dao.getAlbumsWithContent()
                albumsWithContent.forEach {
                    // Simulate usage
                    it.content.size
                }
            }
            totalTimeRelation += time
        }
        println("Relation Approach Average Time: ${totalTimeRelation / iterations} ms")
    }
}
