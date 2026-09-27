package com.metrolist.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse

class OfflineMediaRemovalTest {
    @Test
    fun deletesFinalAndPartialFilesForVideoId() {
        val mediaDir = Files.createTempDirectory("offline-media-removal")
        val final = mediaDir.resolve("video-1.media")
        val partial = mediaDir.resolve("video-1.media.part")
        Files.write(final, byteArrayOf(1, 2, 3))
        Files.write(partial, byteArrayOf(4, 5))

        deleteOfflineMediaFiles(mediaDir, "video-1")

        assertFalse(Files.exists(final))
        assertFalse(Files.exists(partial))
    }
}
