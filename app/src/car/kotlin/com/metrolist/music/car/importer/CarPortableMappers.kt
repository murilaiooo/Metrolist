package com.metrolist.music.car.importer

import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime

object CarPortableMappers {
    fun toSongEntity(song: CarPortableSong): SongEntity =
        SongEntity(
            id = song.id,
            title = song.title,
            duration = song.duration ?: -1,
            thumbnailUrl = song.thumbnailUrl,
            albumId = song.albumId,
            albumName = song.albumName,
            explicit = song.explicit,
            year = song.year,
            date = parseDate(song.date),
            dateModified = parseDate(song.dateModified),
            dateDownload = parseDate(song.dateDownload),
            isDownloaded = true,
            isVideo = song.isVideo,
            isEpisode = song.isEpisode,
        )

    fun mergeSongEntity(
        existing: SongEntity,
        portable: CarPortableSong,
    ): SongEntity {
        require(existing.id == portable.id) { "Song IDs do not match" }
        return existing.copy(
            title = portable.title,
            duration = portable.duration ?: -1,
            thumbnailUrl = portable.thumbnailUrl,
            albumId = portable.albumId,
            albumName = portable.albumName,
            explicit = portable.explicit,
            year = portable.year,
            date = parseDate(portable.date),
            dateModified = parseDate(portable.dateModified),
            dateDownload = parseDate(portable.dateDownload),
            isDownloaded = true,
            isVideo = portable.isVideo,
            isEpisode = portable.isEpisode,
        )
    }

    fun toArtistEntity(artist: CarPortableArtist): ArtistEntity =
        ArtistEntity(
            id = artist.id,
            name = artist.name,
            thumbnailUrl = artist.thumbnailUrl,
            channelId = artist.channelId,
            lastUpdateTime = parseDate(artist.lastUpdateTime) ?: LocalDateTime.MIN,
            isPodcastChannel = artist.isPodcastChannel,
        )

    fun toFormatEntity(format: CarPortableFormat): FormatEntity =
        FormatEntity(
            id = format.id,
            itag = format.itag,
            mimeType = requireNotNull(format.mimeType) { "Portable format mimeType is required" },
            codecs = requireNotNull(format.codecs) { "Portable format codecs is required" },
            bitrate = requireNotNull(format.bitrate) { "Portable format bitrate is required" },
            sampleRate = format.sampleRate,
            contentLength = format.contentLength,
            loudnessDb = format.loudnessDb,
            perceptualLoudnessDb = format.perceptualLoudnessDb,
            playbackUrl = null,
        )

    fun toSongArtistMaps(
        songId: String,
        maps: List<CarPortableMap>,
    ): List<SongArtistMap> =
        maps
            .asSequence()
            .filter { it.songId == songId }
            .sortedBy(CarPortableMap::position)
            .distinctBy(CarPortableMap::artistId)
            .map {
                SongArtistMap(
                    songId = songId,
                    artistId = it.artistId,
                    position = it.position,
                )
            }.toList()

    private fun parseDate(value: String?): LocalDateTime? =
        value?.let(LocalDateTime::parse)
}
