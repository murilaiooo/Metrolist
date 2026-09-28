package com.metrolist.music.car.diagnostics

data class CarDiagnosticsSnapshot(
    val versionName: String,
    val deviceModel: String,
    val apiLevel: Int,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val density: Float,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val roomDownloadedCount: Int,
    val media3CachedKeyCount: Int,
    val importState: String,
    val carLibraryState: String,
    val logicalCatalogCount: Int,
    val carLibraryPrimaryCount: Int,
    val carLocalPrimaryCount: Int,
    val legacyPrimaryCount: Int,
    val lastInputObservation: String?,
) {
    fun lines(): List<String> =
        listOf(
            "Car version: " + versionName,
            "Device: " + deviceModel + " · Android API " + apiLevel,
            "Viewport: " + viewportWidthPx + "×" + viewportHeightPx + "px · density " + "%.2f".format(density),
            "Storage: " + formatBytes(freeStorageBytes) + " free / " + formatBytes(totalStorageBytes) + " total",
            "Catalog: " + logicalCatalogCount + " logical tracks · " +
                carLibraryPrimaryCount + " Car Library · " +
                carLocalPrimaryCount + " Car downloads · " +
                legacyPrimaryCount + " legacy",
            "Car Library: " + carLibraryState,
            "Room downloaded tracks: " + roomDownloadedCount,
            "Media3 cached entries: " + media3CachedKeyCount,
            "Legacy import: " + importState,
            "Last input: " + (lastInputObservation ?: "none observed"),
        )

    private fun formatBytes(value: Long): String {
        val gib = value.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return "%.1f GB".format(gib)
    }
}