package com.metrolist.music.car.library

class CarLibraryRepository(
    private val locationStore: CarLibraryLocationStore,
    private val accessFactory: CarLibraryTreeAccessFactory,
    private val validator: CarLibraryValidator = CarLibraryValidator(),
) {
    private var activeAccess: CarLibraryTreeAccess? = null
    private var activeIndex: CarLibraryIndex? = null

    fun index(): CarLibraryIndex? = activeIndex

    fun disconnect() {
        activeAccess = null
        activeIndex = null
        locationStore.clear()
    }

    fun open(access: CarLibraryTreeAccess): CarLibraryOpenResult {
        val raw =
            runCatching { access.readText(MANIFEST_PATH) }
                .getOrElse { error ->
                    return CarLibraryOpenResult.Unavailable(
                        location = access.location,
                        reason = error.message ?: "Could not read library.json",
                    )
                }
                ?: return CarLibraryOpenResult.Unavailable(
                    location = access.location,
                    reason = "library.json is missing",
                )

        val validation = validator.parseAndValidate(raw)
        if (!validation.valid) {
            return CarLibraryOpenResult.Invalid(
                location = access.location,
                errors = validation.errors,
            )
        }

        val manifest = requireNotNull(validation.manifest)
        val index = CarLibraryIndex.from(manifest)

        activeAccess = access
        activeIndex = index
        locationStore.save(access.location)

        return CarLibraryOpenResult.Opened(
            location = access.location,
            index = index,
        )
    }

    fun reopen(): CarLibraryOpenResult {
        val location =
            locationStore.load()
                ?: return CarLibraryOpenResult.Unavailable(
                    location = null,
                    reason = "No Car Library folder has been selected",
                )

        val access =
            accessFactory.open(location)
                ?: return CarLibraryOpenResult.Unavailable(
                    location = location,
                    reason = "Saved Car Library location is unavailable",
                )

        return open(access)
    }

    fun refresh(): CarLibraryRefreshResult {
        val access =
            activeAccess
                ?: locationStore.load()
                    ?.let(accessFactory::open)
                ?: return CarLibraryRefreshResult.Unavailable(
                    location = locationStore.load(),
                    reason = "No Car Library location is available",
                )

        val previous = activeIndex
        access.invalidateCaches()
        val raw =
            runCatching { access.readText(MANIFEST_PATH) }
                .getOrElse { error ->
                    return CarLibraryRefreshResult.Unavailable(
                        location = access.location,
                        reason = error.message ?: "Could not read library.json",
                    )
                }
                ?: return CarLibraryRefreshResult.Unavailable(
                    location = access.location,
                    reason = "library.json is missing",
                )

        val validation = validator.parseAndValidate(raw)
        if (!validation.valid) {
            return CarLibraryRefreshResult.Invalid(
                location = access.location,
                errors = validation.errors,
            )
        }

        val index = CarLibraryIndex.from(requireNotNull(validation.manifest))
        activeAccess = access
        activeIndex = index
        locationStore.save(access.location)

        return if (
            previous != null &&
            previous.libraryId == index.libraryId &&
            previous.revision == index.revision
        ) {
            CarLibraryRefreshResult.Unchanged(index)
        } else {
            CarLibraryRefreshResult.Updated(
                previousRevision = previous?.revision.orEmpty(),
                index = index,
            )
        }
    }

    fun trackAvailability(songId: String): CarLibraryTrackAvailability {
        val index =
            activeIndex
                ?: return CarLibraryTrackAvailability.Unavailable(
                    songId = songId,
                    reason = CarLibraryUnavailableReason.NO_ACTIVE_LIBRARY,
                )
        val access =
            activeAccess
                ?: return CarLibraryTrackAvailability.Unavailable(
                    songId = songId,
                    reason = CarLibraryUnavailableReason.ACCESS_LOST,
                )
        val song =
            index.song(songId)
                ?: return CarLibraryTrackAvailability.Unavailable(
                    songId = songId,
                    reason = CarLibraryUnavailableReason.UNKNOWN_TRACK,
                )

        val mediaEntry =
            runCatching { access.entry(song.mediaPath) }
                .getOrElse {
                    return CarLibraryTrackAvailability.Unavailable(
                        songId = songId,
                        reason = CarLibraryUnavailableReason.ACCESS_LOST,
                    )
                }
                ?: return CarLibraryTrackAvailability.Unavailable(
                    songId = songId,
                    reason = CarLibraryUnavailableReason.MEDIA_MISSING,
                )
        if (mediaEntry.size != null && mediaEntry.size != song.fileSize) {
            return CarLibraryTrackAvailability.Unavailable(
                songId = songId,
                reason = CarLibraryUnavailableReason.MEDIA_CORRUPT,
            )
        }

        val artworkLocator =
            song.artworkPath
                ?.let { artworkPath ->
                    runCatching { access.entry(artworkPath) }
                        .getOrNull()
                        ?.takeIf { entry ->
                            val expectedSize = song.artworkFileSize
                            expectedSize == null || entry.size == null || entry.size == expectedSize
                        }
                        ?.locator
                }

        return CarLibraryTrackAvailability.Available(
            song = song,
            locator = mediaEntry.locator,
            artworkLocator = artworkLocator,
        )
    }

    private companion object {
        const val MANIFEST_PATH = "library.json"
    }
}