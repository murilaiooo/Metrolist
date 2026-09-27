package com.metrolist.desktop

import com.metrolist.desktop.portable.InsufficientExportSpaceException
import com.metrolist.desktop.portable.PortableExportPhase
import com.metrolist.desktop.portable.PortableExportProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportUiTest {
    @Test
    fun exportProgressReservesTimeForPreparationValidationAndFinalization() {
        assertEquals(
            0.1f,
            exportProgressFraction(
                PortableExportProgress(
                    phase = PortableExportPhase.PREPARING,
                    completedItems = 1,
                    totalItems = 2,
                ),
            ),
        )
        val writing =
            exportProgressFraction(
                PortableExportProgress(
                    phase = PortableExportPhase.WRITING,
                    completedItems = 2,
                    totalItems = 2,
                    bytesWritten = 50L,
                    totalBytes = 100L,
                ),
            )
        assertEquals(0.55f, writing)
        assertEquals(
            0.9f,
            exportProgressFraction(
                PortableExportProgress(phase = PortableExportPhase.VALIDATING),
            ),
        )
        assertEquals(
            1f,
            exportProgressFraction(
                PortableExportProgress(phase = PortableExportPhase.COMPLETE),
            ),
        )
    }

    @Test
    fun diskSpaceErrorExplainsRequiredAndAvailableSpace() {
        val message =
            exportErrorMessage(
                InsufficientExportSpaceException(
                    requiredBytes = 200L * 1024L * 1024L,
                    availableBytes = 50L * 1024L * 1024L,
                ),
            )

        assertTrue(message.contains("Not enough free space"))
        assertTrue(message.contains("200.0 MB"))
        assertTrue(message.contains("50.0 MB"))
        assertTrue(message.contains("another drive", ignoreCase = true))
    }
}
