package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun windowsVersionUsesSemver() {
        assertTrue(
            METROLIST_WINDOWS_VERSION.matches(Regex("""\d+\.\d+\.\d+""")),
            "Expected semantic version, got $METROLIST_WINDOWS_VERSION",
        )
    }

    @Test
    fun compactVersionLabelIsStable() {
        assertEquals("v1.2.3", appVersionLabel("1.2.3"))
    }
}
