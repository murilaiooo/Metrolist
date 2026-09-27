package com.metrolist.music.car.importer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CarPortableRealBackupContractTest {
    @Test
    fun windowsGeneratedBackupMatchesCarContract() {
        val path = System.getenv("METROLIST_CAR_BACKUP_PATH")
        assumeTrue(!path.isNullOrBlank())

        val backup = File(path!!)
        assumeTrue(backup.isFile)

        val validated =
            backup.inputStream().buffered().use { input ->
                CarPortableArchiveValidator().validate(
                    input = input,
                    availableDestinationBytes = Long.MAX_VALUE,
                )
            }

        assertTrue(validated.songs.isNotEmpty())
        assertEquals(validated.manifest.songIds.size, validated.songs.size)
        assertEquals(validated.manifest.requestIds.size, validated.transfers.size)
    }
}
