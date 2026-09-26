package com.metrolist.desktop.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdvancedSessionParserTest {
    @Test
    fun parsesMetroListAdvancedTokenTemplate() {
        val session = AdvancedSessionParser.parse(
            """
            ***INNERTUBE COOKIE*** =SAPISID=secret-value; HSID=other
            ***VISITOR DATA*** =visitor-123
            ***DATASYNC ID*** =page-456||user-7
            ***AUTH USER*** =7
            ***ACCOUNT NAME*** =My Account
            ***ACCOUNT EMAIL*** =user@example.com
            ***ACCOUNT CHANNEL HANDLE*** =@channel
            """.trimIndent(),
        )

        assertEquals("SAPISID=secret-value; HSID=other", session.cookie)
        assertEquals("visitor-123", session.visitorData)
        assertEquals("page-456", session.dataSyncId)
        assertEquals("7", session.authUser)
        assertEquals("My Account", session.accountName)
        assertEquals("user@example.com", session.accountEmail)
        assertEquals("@channel", session.accountChannelHandle)
    }

    @Test
    fun normalizesAuthUserAndRejectsMissingRequiredDataWithoutLeakingCookie() {
        val error = assertFailsWith<IllegalArgumentException> {
            AdvancedSessionParser.parse(
                """
                ***INNERTUBE COOKIE*** =SAPISID=do-not-leak-this
                ***VISITOR DATA*** =
                ***DATASYNC ID*** =
                ***AUTH USER*** =user-12
                """.trimIndent(),
            )
        }

        assertTrue("do-not-leak-this" !in error.message.orEmpty())
    }

    @Test
    fun rejectsCookieWithoutSapisid() {
        assertFailsWith<IllegalArgumentException> {
            AdvancedSessionParser.parse(
                """
                ***INNERTUBE COOKIE*** =HSID=only
                ***VISITOR DATA*** =visitor
                ***DATASYNC ID*** =page
                ***AUTH USER*** =0
                """.trimIndent(),
            )
        }
    }
}
