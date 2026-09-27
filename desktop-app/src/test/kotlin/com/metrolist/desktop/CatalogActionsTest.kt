package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogActionsTest {
    @Test
    fun songActionsExposeUsefulTasksInStableOrder() {
        val actions =
            availableCatalogActions(
                target = CatalogActionTarget.SONG,
                capabilities =
                    CatalogActionCapabilities(
                        canPlayNext = true,
                        canAddToQueue = true,
                        canDownload = true,
                        canAddToLocalPlaylist = true,
                        canOpenAlbum = true,
                        canOpenArtist = true,
                        canCopyLink = true,
                    ),
            )

        assertEquals(
            listOf(
                CatalogAction.PlayNext,
                CatalogAction.AddToQueue,
                CatalogAction.Download,
                CatalogAction.AddToLocalPlaylist,
                CatalogAction.OpenAlbum,
                CatalogAction.OpenArtist,
                CatalogAction.CopyLink,
            ),
            actions,
        )
    }

    @Test
    fun completedSongShowsRemoveDownloadInsteadOfDownload() {
        val actions =
            availableCatalogActions(
                CatalogActionTarget.SONG,
                CatalogActionCapabilities(
                    canDownload = true,
                    downloadCompleted = true,
                ),
            )

        assertEquals(listOf(CatalogAction.RemoveDownload), actions)
    }

    @Test
    fun albumArtistAndYoutubePlaylistOnlyExposeRelevantActions() {
        assertEquals(
            listOf(CatalogAction.OpenAlbum, CatalogAction.CopyLink),
            availableCatalogActions(
                CatalogActionTarget.ALBUM,
                CatalogActionCapabilities(canOpenAlbum = true, canCopyLink = true),
            ),
        )
        assertEquals(
            listOf(CatalogAction.OpenArtist, CatalogAction.CopyLink),
            availableCatalogActions(
                CatalogActionTarget.ARTIST,
                CatalogActionCapabilities(canOpenArtist = true, canCopyLink = true),
            ),
        )
        assertEquals(
            listOf(CatalogAction.Download, CatalogAction.CopyLink),
            availableCatalogActions(
                CatalogActionTarget.YOUTUBE_PLAYLIST,
                CatalogActionCapabilities(canDownload = true, canCopyLink = true),
            ),
        )
    }

    @Test
    fun localPlaylistSongAddsRemoveFromPlaylistWithoutLosingSongActions() {
        val actions =
            availableCatalogActions(
                CatalogActionTarget.LOCAL_PLAYLIST_SONG,
                CatalogActionCapabilities(
                    canPlayNext = true,
                    canAddToQueue = true,
                    canDownload = true,
                    canAddToLocalPlaylist = true,
                    canOpenAlbum = true,
                    canOpenArtist = true,
                    canCopyLink = true,
                    canRemoveFromLocalPlaylist = true,
                ),
            )

        assertEquals(CatalogAction.RemoveFromLocalPlaylist, actions.last())
        assertEquals(8, actions.size)
    }

    @Test
    fun copyLinksUseTheCorrectYouTubeMusicSurface() {
        assertEquals(
            "https://music.youtube.com/watch?v=song-id",
            catalogItemLink(CatalogActionTarget.SONG, "song-id"),
        )
        assertEquals(
            "https://music.youtube.com/playlist?list=playlist-id",
            catalogItemLink(CatalogActionTarget.YOUTUBE_PLAYLIST, "playlist-id"),
        )
        assertEquals(
            "https://music.youtube.com/browse/album-id",
            catalogItemLink(CatalogActionTarget.ALBUM, "album-id"),
        )
        assertEquals(
            "https://music.youtube.com/browse/artist-id",
            catalogItemLink(CatalogActionTarget.ARTIST, "artist-id"),
        )
    }

    @Test
    fun unavailableCapabilitiesNeverLeakIntoMenu() {
        assertEquals(
            emptyList(),
            availableCatalogActions(CatalogActionTarget.SONG, CatalogActionCapabilities()),
        )
    }
}
