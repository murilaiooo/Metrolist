package com.metrolist.desktop.portable

data class PortableExportEstimate(
    val songCount: Int,
    val audioBytes: Long,
    val artworkBytes: Long,
) {
    val estimatedContentBytes: Long
        get() = audioBytes + artworkBytes
}

enum class PortableExportPhase {
    PREPARING,
    WRITING,
    VALIDATING,
    FINALIZING,
    COMPLETE,
}

data class PortableExportProgress(
    val phase: PortableExportPhase,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val bytesWritten: Long = 0L,
    val totalBytes: Long = 0L,
)

class InsufficientExportSpaceException(
    val requiredBytes: Long,
    val availableBytes: Long,
) : IllegalStateException(
    "Not enough free space for backup: required=$requiredBytes available=$availableBytes",
)

class PortableValidationException(
    val validationErrors: List<String>,
) : IllegalStateException(
    "Portable backup validation failed: " + validationErrors.joinToString("; "),
)
