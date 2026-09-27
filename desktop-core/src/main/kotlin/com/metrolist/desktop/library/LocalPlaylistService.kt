package com.metrolist.desktop.library

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import java.util.Locale
import java.util.UUID

class LocalPlaylistService(
    private val store: LibraryStore,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun createFromImport(
        sessionId: String,
        name: String,
    ): LocalPlaylist {
        val cleanName = requireName(name)
        val session = requireNotNull(store.getImportSession(sessionId)) {
            "Import session not found: $sessionId"
        }
        val timestamp = nowMs()
        val playlist =
            LocalPlaylist(
                id = idGenerator(),
                name = cleanName,
                createdAtMs = timestamp,
                updatedAtMs = timestamp,
            )
        val tracks =
            store.importRows(sessionId)
                .asSequence()
                .filter { it.state == ImportRowState.READY && it.selectedSong != null }
                .sortedBy { it.position }
                .mapIndexed { position, row ->
                    LocalPlaylistTrack(
                        playlistId = playlist.id,
                        position = position,
                        song = requireNotNull(row.selectedSong),
                        sourceImportLine = row.original,
                    )
                }.toList()

        store.finalizeImportPlaylist(
            playlist = playlist,
            tracks = tracks,
            session =
                session.copy(
                    status = ImportSessionStatus.FINALIZED,
                    updatedAtMs = timestamp,
                ),
        )
        return playlist
    }

    fun playlists(): List<LocalPlaylist> = store.listPlaylists()

    fun rename(
        playlistId: String,
        name: String,
    ): LocalPlaylist {
        val current = requirePlaylist(playlistId)
        val updated = current.copy(name = requireName(name), updatedAtMs = nowMs())
        store.upsertPlaylist(updated)
        return updated
    }

    fun delete(playlistId: String) {
        requirePlaylist(playlistId)
        store.deletePlaylist(playlistId)
    }

    fun removeTrack(
        playlistId: String,
        position: Int,
    ) {
        requirePlaylist(playlistId)
        val tracks = store.playlistTracks(playlistId).toMutableList()
        val index = tracks.indexOfFirst { it.position == position }
        require(index >= 0) { "Track position not found: $position" }
        tracks.removeAt(index)
        store.replacePlaylistTracks(
            playlistId,
            tracks.mapIndexed { newPosition, track -> track.copy(position = newPosition) },
        )
        touch(playlistId)
    }

    fun moveTrack(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    ) {
        requirePlaylist(playlistId)
        val tracks = store.playlistTracks(playlistId).toMutableList()
        val fromIndex = tracks.indexOfFirst { it.position == fromPosition }
        require(fromIndex >= 0) { "Track position not found: $fromPosition" }
        require(toPosition in tracks.indices) { "Target position out of range: $toPosition" }
        val moved = tracks.removeAt(fromIndex)
        tracks.add(toPosition, moved)
        store.replacePlaylistTracks(
            playlistId,
            tracks.mapIndexed { newPosition, track -> track.copy(position = newPosition) },
        )
        touch(playlistId)
    }

    fun songs(playlistId: String): List<SongItem> {
        requirePlaylist(playlistId)
        return store.playlistTracks(playlistId).map { it.song.toSongItem() }
    }

    fun searchSongs(
        playlistId: String,
        query: String,
    ): List<SongItem> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val all = songs(playlistId)
        if (normalized.isEmpty()) return all
        return all.filter { song ->
            buildString {
                append(song.title)
                append(' ')
                append(song.artists.joinToString(" ") { it.name })
                append(' ')
                append(song.album?.name.orEmpty())
            }.lowercase(Locale.ROOT).contains(normalized)
        }
    }

    private fun touch(playlistId: String) {
        val current = requirePlaylist(playlistId)
        store.upsertPlaylist(current.copy(updatedAtMs = nowMs()))
    }

    private fun requirePlaylist(playlistId: String): LocalPlaylist =
        requireNotNull(store.getPlaylist(playlistId)) { "Local playlist not found: $playlistId" }

    private fun requireName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Playlist name must not be blank" } }
}

internal fun LibrarySongSnapshot.toSongItem(): SongItem =
    SongItem(
        id = videoId,
        title = title,
        artists = artists.map { Artist(it.name, it.id) },
        album = album?.let { Album(it.name, it.id) },
        duration = duration,
        musicVideoType = musicVideoType,
        thumbnail = thumbnail,
        explicit = explicit,
        isEpisode = isEpisode,
    )
