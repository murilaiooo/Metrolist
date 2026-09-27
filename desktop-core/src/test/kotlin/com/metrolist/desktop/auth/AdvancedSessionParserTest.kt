package com.metrolist.desktop.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedSessionParserTest {
    @Test
    fun parsesTrimmedAdvancedSessionBlock() {
        val parsed =
            AdvancedSessionParser.parse(
                """
                  ***INNERTUBE COOKIE*** = SAPISID=secret-value; HSID=other
                  ***VISITOR DATA*** = visitor-123
                  ***DATASYNC ID*** = sync-abc||ignored
                  ***AUTH USER*** = user 7
                  ***ACCOUNT NAME*** = Example User
                  ***ACCOUNT EMAIL*** = example@example.com
                  ***ACCOUNT CHANNEL HANDLE*** = @example
                """.trimIndent(),
            )

        assertEquals("visitor-123", parsed.visitorData)
        assertEquals("sync-abc", parsed.dataSyncId)
        assertEquals("7", parsed.authUser)
        assertEquals("Example User", parsed.accountName)
    }
    @Test
    fun parseErrorsNeverEchoSubmittedSecretValues() {
        val secret = "SUPER-SECRET-COOKIE-VALUE"
        val error =
            assertFailsWith<IllegalArgumentException> {
                AdvancedSessionParser.parse(
                    """
                    ***INNERTUBE COOKIE*** = HSID=$secret
                    ***VISITOR DATA*** = visitor-$secret
                    """.trimIndent(),
                )
            }

        val message = error.message.orEmpty()
        assertTrue(message.contains("SAPISID"))
        assertFalse(message.contains(secret))
    }
}
