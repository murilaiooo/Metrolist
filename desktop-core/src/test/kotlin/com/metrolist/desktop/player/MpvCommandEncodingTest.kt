package com.metrolist.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvCommandEncodingTest {
    @Test
    fun quotesWindowsPathsAndDoubleQuotes() {
        assertEquals(
            "\"C:\\\\Music\\\\A \\\"quoted\\\" song.webm\"",
            quoteMpvArgument("C:\\Music\\A \"quoted\" song.webm"),
        )
    }
}
