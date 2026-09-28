package com.metrolist.music.car.library

class CarArtworkResolver {
    fun resolve(
        carLibraryArtwork: String?,
        legacyArtwork: String? = null,
        remoteArtwork: String? = null,
    ): String? =
        carLibraryArtwork
            .takeUnless(String?::isNullOrBlank)
            ?: legacyArtwork.takeUnless(String?::isNullOrBlank)
            ?: remoteArtwork.takeUnless(String?::isNullOrBlank)
}