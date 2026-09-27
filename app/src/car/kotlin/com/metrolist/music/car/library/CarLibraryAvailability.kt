package com.metrolist.music.car.library

enum class CarLibraryUnavailableReason {
    NO_ACTIVE_LIBRARY,
    UNKNOWN_TRACK,
    MEDIA_MISSING,
    ACCESS_LOST,
}

sealed interface CarLibraryTrackAvailability {
    data class Available(
        val song: CarLibrarySong,
        val locator: String,
        val artworkLocator: String? = null,
    ) : CarLibraryTrackAvailability

    data class Unavailable(
        val songId: String,
        val reason: CarLibraryUnavailableReason,
    ) : CarLibraryTrackAvailability
}

sealed interface CarLibraryOpenResult {
    data class Opened(
        val location: CarLibraryLocation,
        val index: CarLibraryIndex,
    ) : CarLibraryOpenResult

    data class Unavailable(
        val location: CarLibraryLocation?,
        val reason: String,
    ) : CarLibraryOpenResult

    data class Invalid(
        val location: CarLibraryLocation,
        val errors: List<String>,
    ) : CarLibraryOpenResult
}

sealed interface CarLibraryRefreshResult {
    data class Unchanged(
        val index: CarLibraryIndex,
    ) : CarLibraryRefreshResult

    data class Updated(
        val previousRevision: String,
        val index: CarLibraryIndex,
    ) : CarLibraryRefreshResult

    data class Unavailable(
        val location: CarLibraryLocation?,
        val reason: String,
    ) : CarLibraryRefreshResult

    data class Invalid(
        val location: CarLibraryLocation,
        val errors: List<String>,
    ) : CarLibraryRefreshResult
}