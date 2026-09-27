package com.metrolist.desktop.offline

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineStore(
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
            }
            migrateSchema()
        } catch (error: Throwable) {
            runCatching { connection.close() }
            throw error
        }
    }

    private fun migrateSchema() {
        var version = readSchemaVersion()
        check(version <= CURRENT_SCHEMA_VERSION) {
            "Offline database schema version $version is newer than supported version $CURRENT_SCHEMA_VERSION"
        }
        if (version == CURRENT_SCHEMA_VERSION) return

        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            while (version < CURRENT_SCHEMA_VERSION) {
                version =
                    when (version) {
                        0 -> {
                            migrateVersion0To1()
                            1
                        }

                        else ->
                            error("No migration path from offline database schema version $version")
                    }
                setSchemaVersion(version)
            }
            connection.commit()
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun migrateVersion0To1() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS offline_records (
                    video_id TEXT PRIMARY KEY NOT NULL,
                    state TEXT NOT NULL,
                    record_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun readSchemaVersion(): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result ->
                check(result.next()) { "Could not read offline database schema version" }
                result.getInt(1)
            }
        }

    private fun setSchemaVersion(version: Int) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA user_version=$version")
        }
    }

    @Synchronized
    fun upsert(record: OfflineRecord) {
        connection.prepareStatement(
            """
            INSERT INTO offline_records(video_id, state, record_json, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(video_id) DO UPDATE SET
                state=excluded.state,
                record_json=excluded.record_json,
                updated_at=excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, record.videoId)
            statement.setString(2, record.state.name)
            statement.setString(3, json.encodeToString(record))
            statement.setLong(4, record.updateTimeMs)
            statement.executeUpdate()
        }
    }

    @Synchronized
    fun get(videoId: String): OfflineRecord? =
        connection.prepareStatement(
            "SELECT record_json FROM offline_records WHERE video_id = ?",
        ).use { statement ->
            statement.setString(1, videoId)
            statement.executeQuery().use { result ->
                if (result.next()) json.decodeFromString(result.getString(1)) else null
            }
        }

    @Synchronized
    fun listAll(): List<OfflineRecord> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT record_json FROM offline_records ORDER BY updated_at, video_id",
            ).use { result ->
                buildList {
                    while (result.next()) {
                        add(json.decodeFromString(result.getString(1)))
                    }
                }
            }
        }

    @Synchronized
    fun listCompleted(): List<OfflineRecord> =
        listByState(OfflineState.COMPLETED)

    @Synchronized
    fun listByState(state: OfflineState): List<OfflineRecord> =
        connection.prepareStatement(
            "SELECT record_json FROM offline_records WHERE state = ? ORDER BY updated_at, video_id",
        ).use { statement ->
            statement.setString(1, state.name)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(json.decodeFromString(result.getString(1)))
                    }
                }
            }
        }

    @Synchronized
    fun recoverInterrupted() {
        listByState(OfflineState.DOWNLOADING).forEach { record ->
            upsert(
                record.copy(
                    state = OfflineState.QUEUED,
                    lastError = null,
                    updateTimeMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    @Synchronized
    fun remove(videoId: String) {
        connection.prepareStatement(
            "DELETE FROM offline_records WHERE video_id = ?",
        ).use { statement ->
            statement.setString(1, videoId)
            statement.executeUpdate()
        }
    }

    override fun close() {
        connection.close()
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
