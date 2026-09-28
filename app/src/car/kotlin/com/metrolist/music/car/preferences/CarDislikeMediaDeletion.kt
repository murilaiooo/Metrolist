package com.metrolist.music.car.preferences

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.metrolist.music.car.importer.CarArtworkStore
import com.metrolist.music.car.library.AndroidCarLibraryTreeAccessFactory
import com.metrolist.music.car.library.CarLibraryLocation
import com.metrolist.music.car.library.CarLibrarySession
import com.metrolist.music.car.library.CarSongSourceKind
import com.metrolist.music.car.library.CarUnifiedSong
import com.metrolist.music.car.library.SharedPreferencesCarLibraryLocationStore
import com.metrolist.music.playback.DownloadUtil
import java.nio.file.Files
import java.nio.file.Path

class CarDislikeMediaDeletion(
    context: Context,
    private val downloadUtil: DownloadUtil,
    private val carLibrarySession: CarLibrarySession,
    private val catalogProvider: () -> List<CarUnifiedSong>,
) {
    private val appContext = context.applicationContext
    private val artworkStore = CarArtworkStore(appContext.filesDir)
    private val locationStore = SharedPreferencesCarLibraryLocationStore(appContext)
    private val accessFactory = AndroidCarLibraryTreeAccessFactory(appContext)

    suspend fun delete(record: CarDislikeRecord): Result<Unit> =
        runCatching {
            val catalogSong = catalogProvider().firstOrNull { it.id == record.songId }
            val sources = record.sourceKinds.toSet()

            val hasCarLibrary =
                CarSongSourceKind.CAR_LIBRARY_LOCAL.name in sources ||
                    catalogSong?.carLibrarySong != null
            val hasManagedDownload =
                CarSongSourceKind.CAR_LOCAL_DOWNLOAD.name in sources ||
                    CarSongSourceKind.LEGACY_IMPORTED.name in sources ||
                    catalogSong?.downloadedSong != null

            if (hasManagedDownload) {
                deleteManagedDownload(record.songId)
            }
            if (hasCarLibrary) {
                val carSong =
                    catalogSong?.carLibrarySong
                        ?: error("Car Library metadata is unavailable for ${record.title}")
                deleteCarLibraryPath(carSong.mediaPath)

                val artworkPath = carSong.artworkPath
                val artworkShared =
                    artworkPath != null &&
                        carLibrarySession.index.value
                            ?.songs
                            ?.count { it.artworkPath == artworkPath }
                            ?.let { it > 1 } == true
                if (artworkPath != null && !artworkShared) {
                    deleteCarLibraryPath(artworkPath)
                }
            }
        }

    private suspend fun deleteManagedDownload(songId: String) {
        downloadUtil.downloadManager.removeDownload(songId)
        downloadUtil.downloadCache.removeResource(songId)
        downloadUtil.playerCache.removeResource(songId)
        downloadUtil.database.updateDownloadedInfo(songId, false, null)
        check(artworkStore.delete(songId)) {
            "Could not remove local artwork for $songId"
        }
    }
    private fun deleteCarLibraryPath(relativePath: String) {
        val location =
            locationStore.load()
                ?: error("Car Library location is unavailable")
        when (location) {
            is CarLibraryLocation.FilePath -> {
                val target = resolveSafeFile(Path.of(location.path), relativePath)
                Files.deleteIfExists(target)
            }
            is CarLibraryLocation.TreeUri -> {
                val treeUri = Uri.parse(location.uri)
                val writable =
                    appContext.contentResolver.persistedUriPermissions.any {
                        it.uri == treeUri && it.isWritePermission
                    }
                check(writable) { "Car Library folder is not writable" }
                val access =
                    accessFactory.open(location)
                        ?: error("Car Library folder is unavailable")
                if (!access.exists(relativePath)) return
                val documentUri = Uri.parse(access.locator(relativePath))
                check(DocumentsContract.deleteDocument(appContext.contentResolver, documentUri)) {
                    "Android could not delete the Car Library file"
                }
            }
        }
    }

    companion object {
        internal fun resolveSafeFile(
            root: Path,
            relativePath: String,
        ): Path {
            require(relativePath.isNotBlank()) { "Car Library path is blank" }
            val normalizedRoot = root.toAbsolutePath().normalize()
            val resolved =
                relativePath
                    .split("/")
                    .filter(String::isNotBlank)
                    .fold(normalizedRoot) { current, segment -> current.resolve(segment) }
                    .normalize()
            require(resolved.startsWith(normalizedRoot)) {
                "Car Library path escapes its root"
            }
            return resolved
        }
    }
}
