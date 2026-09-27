package com.metrolist.desktop.library

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LibraryStore(
    databasePath: Path,
    private val json: Json =
        Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        },
) : AutoCloseable {
    private val connection: Connection

    init {
        databasePath.parent?.let(Files::createDirectories)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("PRAGMA synchronous=NORMAL")
                statement.execute("PRAGMA foreign_keys=ON")
            }
            migrateSchema()
        } catch (error: Throwable) {
            runCatching { connection.close() }
            throw error
        }
    }

    @Synchronized
    fun schemaVersion(): Int = readSchemaVersion()

    private fun migrateSchema() {
        var version = readSchemaVersion()
        check(version <= CURRENT_SCHEMA_VERSION) {
            "Library database schema version $version is newer than supported version $CURRENT_SCHEMA_VERSION"
        }
        if (version == CURRENT_SCHEMA_VERSION) return

        transaction {
            while (version < CURRENT_SCHEMA_VERSION) {
                version =
                    when (version) {
                        0 -> {
                            migrateVersion0To1()
                            1
                        }
                        else -> error("No migration path from library database schema version $version")
                    }
                setSchemaVersion(version)
            }
        }
    }

    private fun migrateVersion0To1() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS local_playlists (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    youtube_playlist_id TEXT,
                    sync_state TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS local_playlist_tracks (
                    playlist_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    video_id TEXT NOT NULL,
                    song_json TEXT NOT NULL,
                    source_import_line TEXT,
                    sync_state TEXT NOT NULL,
                    last_sync_error TEXT,
                    PRIMARY KEY (playlist_id, position),
                    FOREIGN KEY (playlist_id) REFERENCES local_playlists(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS import_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT,
                    source_text TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS import_rows (
                    session_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    original_text TEXT NOT NULL,
                    left_text TEXT NOT NULL,
                    right_text TEXT,
                    state TEXT NOT NULL,
                    selected_song_json TEXT,
                    score REAL,
                    candidates_json TEXT NOT NULL,
                    error TEXT,
                    PRIMARY KEY (session_id, position),
                    FOREIGN KEY (session_id) REFERENCES import_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS remembered_matches (
                    identity TEXT PRIMARY KEY NOT NULL,
                    video_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    duration INTEGER,
                    explicit INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun readSchemaVersion(): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result ->
                check(result.next()) { "Could not read library database schema version" }
                result.getInt(1)
            }
        }

    private fun setSchemaVersion(version: Int) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version=$version")
        }
    }

    @Synchronized
    fun upsertPlaylist(playlist: LocalPlaylist) {
        connection.prepareStatement(
            """
            INSERT INTO local_playlists(id, name, created_at, updated_at, youtube_playlist_id, sync_state)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name,
                created_at=excluded.created_at,
                updated_at=excluded.updated_at,
                youtube_playlist_id=excluded.youtube_playlist_id,
                sync_state=excluded.sync_state
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlist.id)
            statement.setString(2, playlist.name)
            statement.setLong(3, playlist.createdAtMs)
            statement.setLong(4, playlist.updatedAtMs)
            statement.setString(5, playlist.youtubePlaylistId)
            statement.setString(6, playlist.syncState.name)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun listPlaylists(): List<LocalPlaylist> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, name, created_at, updated_at, youtube_playlist_id, sync_state " +
                    "FROM local_playlists ORDER BY updated_at DESC, name COLLATE NOCASE, id",
            ).use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            LocalPlaylist(
                                id = result.getString("id"),
                                name = result.getString("name"),
                                createdAtMs = result.getLong("created_at"),
                                updatedAtMs = result.getLong("updated_at"),
                                youtubePlaylistId = result.getString("youtube_playlist_id"),
                                syncState = PlaylistSyncState.valueOf(result.getString("sync_state")),
                            ),
                        )
                    }
                }
            }
        }

    @Synchronized
    fun getPlaylist(id: String): LocalPlaylist? =
        connection.prepareStatement(
            "SELECT id, name, created_at, updated_at, youtube_playlist_id, sync_state FROM local_playlists WHERE id = ?",
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                LocalPlaylist(
                    id = result.getString("id"),
                    name = result.getString("name"),
                    createdAtMs = result.getLong("created_at"),
                    updatedAtMs = result.getLong("updated_at"),
                    youtubePlaylistId = result.getString("youtube_playlist_id"),
                    syncState = PlaylistSyncState.valueOf(result.getString("sync_state")),
                )
            }
        }

    @Synchronized
    fun deletePlaylist(id: String) {
        connection.prepareStatement("DELETE FROM local_playlists WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun finalizeImportPlaylist(
        playlist: LocalPlaylist,
        tracks: List<LocalPlaylistTrack>,
        session: ImportSession,
    ) {
        require(tracks.all { it.playlistId == playlist.id }) {
            "Track playlist IDs must match finalized playlist"
        }
        require(session.id.isNotBlank()) { "Import session ID must not be blank" }
        transaction {
            upsertPlaylist(playlist)
            replacePlaylistTracks(playlist.id, tracks)
            upsertImportSession(session)
        }
    }

    @Synchronized
    fun replacePlaylistTracks(
        playlistId: String,
        tracks: List<LocalPlaylistTrack>,
    ) {
        require(tracks.all { it.playlistId == playlistId }) { "Track playlist IDs must match playlistId" }
        transaction {
            connection.prepareStatement("DELETE FROM local_playlist_tracks WHERE playlist_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO local_playlist_tracks(
                    playlist_id, position, video_id, song_json, source_import_line, sync_state, last_sync_error
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                tracks.forEach { track ->
                    statement.setString(1, playlistId)
                    statement.setInt(2, track.position)
                    statement.setString(3, track.song.videoId)
                    statement.setString(4, json.encodeToString(track.song))
                    statement.setString(5, track.sourceImportLine)
                    statement.setString(6, track.syncState.name)
                    statement.setString(7, track.lastSyncError)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    @Synchronized
    fun upsertPlaylistTrack(track: LocalPlaylistTrack) {
        connection.prepareStatement(
            """
            INSERT INTO local_playlist_tracks(
                playlist_id, position, video_id, song_json, source_import_line, sync_state, last_sync_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(playlist_id, position) DO UPDATE SET
                video_id=excluded.video_id,
                song_json=excluded.song_json,
                source_import_line=excluded.source_import_line,
                sync_state=excluded.sync_state,
                last_sync_error=excluded.last_sync_error
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, track.playlistId)
            statement.setInt(2, track.position)
            statement.setString(3, track.song.videoId)
            statement.setString(4, json.encodeToString(track.song))
            statement.setString(5, track.sourceImportLine)
            statement.setString(6, track.syncState.name)
            statement.setString(7, track.lastSyncError)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun playlistTracks(playlistId: String): List<LocalPlaylistTrack> =
        connection.prepareStatement(
            """
            SELECT position, song_json, source_import_line, sync_state, last_sync_error
            FROM local_playlist_tracks
            WHERE playlist_id = ?
            ORDER BY position
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            LocalPlaylistTrack(
                                playlistId = playlistId,
                                position = result.getInt("position"),
                                song = json.decodeFromString(result.getString("song_json")),
                                sourceImportLine = result.getString("source_import_line"),
                                syncState = TrackSyncState.valueOf(result.getString("sync_state")),
                                lastSyncError = result.getString("last_sync_error"),
                            ),
                        )
                    }
                }
            }
        }

    @Synchronized
    fun upsertImportSession(session: ImportSession) {
        connection.prepareStatement(
            """
            INSERT INTO import_sessions(id, name, source_text, created_at, updated_at, status)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name=excluded.name,
                source_text=excluded.source_text,
                created_at=excluded.created_at,
                updated_at=excluded.updated_at,
                status=excluded.status
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, session.id)
            statement.setString(2, session.name)
            statement.setString(3, session.sourceText)
            statement.setLong(4, session.createdAtMs)
            statement.setLong(5, session.updatedAtMs)
            statement.setString(6, session.status.name)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun getImportSession(id: String): ImportSession? =
        connection.prepareStatement(
            "SELECT id, name, source_text, created_at, updated_at, status FROM import_sessions WHERE id = ?",
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                ImportSession(
                    id = result.getString("id"),
                    name = result.getString("name"),
                    sourceText = result.getString("source_text"),
                    createdAtMs = result.getLong("created_at"),
                    updatedAtMs = result.getLong("updated_at"),
                    status = ImportSessionStatus.valueOf(result.getString("status")),
                )
            }
        }

    @Synchronized
    fun upsertImportRows(rows: List<ImportRow>) {
        if (rows.isEmpty()) return
        transaction {
            connection.prepareStatement(
                """
                INSERT INTO import_rows(
                    session_id, position, original_text, left_text, right_text, state,
                    selected_song_json, score, candidates_json, error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id, position) DO UPDATE SET
                    original_text=excluded.original_text,
                    left_text=excluded.left_text,
                    right_text=excluded.right_text,
                    state=excluded.state,
                    selected_song_json=excluded.selected_song_json,
                    score=excluded.score,
                    candidates_json=excluded.candidates_json,
                    error=excluded.error
                """.trimIndent(),
            ).use { statement ->
                rows.forEach { row ->
                    statement.setString(1, row.sessionId)
                    statement.setInt(2, row.position)
                    statement.setString(3, row.original)
                    statement.setString(4, row.left)
                    statement.setString(5, row.right)
                    statement.setString(6, row.state.name)
                    statement.setString(7, row.selectedSong?.let(json::encodeToString))
                    if (row.score == null) statement.setObject(8, null) else statement.setDouble(8, row.score)
                    statement.setString(9, json.encodeToString(row.candidates))
                    statement.setString(10, row.error)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    @Synchronized
    fun importRows(sessionId: String): List<ImportRow> =
        connection.prepareStatement(
            """
            SELECT position, original_text, left_text, right_text, state, selected_song_json, score, candidates_json, error
            FROM import_rows
            WHERE session_id = ?
            ORDER BY position
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toImportRow(sessionId))
                }
            }
        }

    @Synchronized
    fun getImportRow(
        sessionId: String,
        position: Int,
    ): ImportRow? =
        connection.prepareStatement(
            """
            SELECT position, original_text, left_text, right_text, state, selected_song_json, score, candidates_json, error
            FROM import_rows
            WHERE session_id = ? AND position = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.setInt(2, position)
            statement.executeQuery().use { result ->
                if (result.next()) result.toImportRow(sessionId) else null
            }
        }

    private fun java.sql.ResultSet.toImportRow(sessionId: String): ImportRow {
        val selectedJson = getString("selected_song_json")
        val scoreValue = getDouble("score")
        val score = if (wasNull()) null else scoreValue
        return ImportRow(
            sessionId = sessionId,
            position = getInt("position"),
            original = getString("original_text"),
            left = getString("left_text"),
            right = getString("right_text"),
            state = ImportRowState.valueOf(getString("state")),
            selectedSong = selectedJson?.let(json::decodeFromString),
            score = score,
            candidates = json.decodeFromString(getString("candidates_json")),
            error = getString("error"),
        )
    }

    @Synchronized
    fun upsertRememberedMatch(match: RememberedMatch) {
        connection.prepareStatement(
            """
            INSERT INTO remembered_matches(identity, video_id, title, artist, duration, explicit, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(identity) DO UPDATE SET
                video_id=excluded.video_id,
                title=excluded.title,
                artist=excluded.artist,
                duration=excluded.duration,
                explicit=excluded.explicit,
                updated_at=excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, match.identity)
            statement.setString(2, match.videoId)
            statement.setString(3, match.title)
            statement.setString(4, match.artist)
            if (match.duration == null) statement.setObject(5, null) else statement.setInt(5, match.duration)
            statement.setInt(6, if (match.explicit) 1 else 0)
            statement.setLong(7, match.updatedAtMs)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun getRememberedMatch(identity: String): RememberedMatch? =
        connection.prepareStatement(
            "SELECT identity, video_id, title, artist, duration, explicit, updated_at FROM remembered_matches WHERE identity = ?",
        ).use { statement ->
            statement.setString(1, identity)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                val durationValue = result.getInt("duration")
                RememberedMatch(
                    identity = result.getString("identity"),
                    videoId = result.getString("video_id"),
                    title = result.getString("title"),
                    artist = result.getString("artist"),
                    duration = if (result.wasNull()) null else durationValue,
                    explicit = result.getInt("explicit") != 0,
                    updatedAtMs = result.getLong("updated_at"),
                )
            }
        }

    private inline fun <T> transaction(block: () -> T): T {
        if (!connection.autoCommit) return block()

        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() {
        connection.close()
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
