package com.metrolist.desktop.offline

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineStoreTest {
    @Test
    fun persistsRecordsAndRecoversInterruptedState() {
        val db = testPath("offline-store.db")
        val song = SongItem(
            id = "song-1",
            title = "Song One",
            artists = listOf(Artist("Artist", "artist-1")),
            duration = 123,
            thumbnail = "https://example.invalid/thumb",
        )

        OfflineStore(db).use { store ->
            store.upsert(OfflineRecord.queued(song, nowMs = 100L))
            store.upsert(requireNotNull(store.get("song-1")).copy(state = OfflineState.DOWNLOADING))
        }

        OfflineStore(db).use { store ->
            store.recoverInterrupted()
            assertEquals(OfflineState.QUEUED, requireNotNull(store.get("song-1")).state)

            store.upsert(
                requireNotNull(store.get("song-1")).copy(
                    state = OfflineState.COMPLETED,
                    localPath = "media/song-1.audio",
                    bytesDownloaded = 456L,
                    contentLength = 456L,
                ),
            )
            assertEquals(listOf("song-1"), store.listCompleted().map { it.videoId })

            store.remove("song-1")
            assertNull(store.get("song-1"))
        }
    }

    @Test
    fun migratesUnversionedDatabaseWithoutLosingOfflineRecords() {
        val db = testPath("offline-store-migration.db")
        val song =
            SongItem(
                id = "legacy-song",
                title = "Legacy Song",
                artists = emptyList(),
                thumbnail = "",
            )

        OfflineStore(db).use { store ->
            store.upsert(
                OfflineRecord.queued(song, nowMs = 10L).copy(
                    state = OfflineState.COMPLETED,
                    localPath = "media/legacy-song.media",
                    bytesDownloaded = 1234L,
                    contentLength = 1234L,
                ),
            )
        }

        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version=0")
            }
        }

        OfflineStore(db).use { store ->
            val migrated = requireNotNull(store.get("legacy-song"))
            assertEquals(OfflineState.COMPLETED, migrated.state)
            assertEquals(1234L, migrated.contentLength)
        }

        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    @Test
    fun rejectsDatabaseFromNewerUnsupportedSchema() {
        val db = testPath("offline-store-future.db")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version=999")
            }
        }

        val error =
            assertFailsWith<IllegalStateException> {
                OfflineStore(db)
            }

        assertTrue(error.message.orEmpty().contains("newer", ignoreCase = true))
        assertTrue(error.message.orEmpty().contains("999"))
    }

    private fun testPath(name: String): Path {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "offline-store-test")
        Files.createDirectories(dir)
        Files.deleteIfExists(dir.resolve(name))
        return dir.resolve(name)
    }
}
