package com.metrolist.desktop.media

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatedMediaWriterTest {
    @Test
    fun exactBytesAreAtomicallyPromoted() {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val destination = Files.createTempDirectory("metrolist-media").resolve("song.audio")

        val result = ValidatedMediaWriter().write(
            input = ByteArrayInputStream(bytes),
            expectedLength = bytes.size.toLong(),
            destination = destination,
        )

        assertTrue(Files.exists(destination))
        assertFalse(Files.exists(destination.resolveSibling("song.audio.part")))
        assertContentEquals(bytes, Files.readAllBytes(destination))
        assertTrue(result.sha256.length == 64)
    }

    @Test
    fun mismatchedLengthLeavesNoFinalOrPartialFile() {
        val bytes = ByteArray(1024) { 7 }
        val destination = Files.createTempDirectory("metrolist-media").resolve("song.audio")

        assertFailsWith<IllegalStateException> {
            ValidatedMediaWriter().write(
                input = ByteArrayInputStream(bytes),
                expectedLength = bytes.size.toLong() + 1,
                destination = destination,
            )
        }

        assertFalse(Files.exists(destination))
        assertFalse(Files.exists(destination.resolveSibling("song.audio.part")))
    }
}
