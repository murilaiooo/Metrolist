package com.metrolist.music.car.service

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarImportServiceCommandTest {
    @Test
    fun startRequiresAndParsesSourceUri() {
        val uri = Uri.parse("content://backup/library")
        val intent =
            Intent(CarImportService.ACTION_START)
                .setData(uri)

        val command = CarImportServiceCommand.from(intent)

        assertEquals(CarImportServiceCommand.Start(uri), command)
    }

    @Test
    fun resumeUsesPersistedSource() {
        val command =
            CarImportServiceCommand.from(
                Intent(CarImportService.ACTION_RESUME),
            )

        assertTrue(command is CarImportServiceCommand.Resume)
    }
    @Test
    fun discardDoesNotRequireSourceUri() {
        val command =
            CarImportServiceCommand.from(
                Intent(CarImportService.ACTION_DISCARD),
            )

        assertTrue(command is CarImportServiceCommand.Discard)
    }

    @Test
    fun missingStartUriIsRejected() {
        val error =
            runCatching {
                CarImportServiceCommand.from(
                    Intent(CarImportService.ACTION_START),
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
