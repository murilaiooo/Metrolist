package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedSessionUiTest {
    @Test
    fun validationErrorDoesNotExposeSubmittedSecret() {
        val secret = "SAPISID=VERY-PRIVATE-VALUE"
        val message =
            advancedSessionErrorMessage(
                IllegalStateException(
                    "Request failed while validating $secret",
                ),
            )

        assertTrue(message.contains("could not be validated", ignoreCase = true))
        assertFalse(message.contains(secret))
    }

    @Test
    fun parserErrorsStayActionable() {
        val message =
            advancedSessionErrorMessage(
                IllegalArgumentException("Advanced session is missing SAPISID"),
            )

        assertTrue(message.contains("SAPISID"))
        assertTrue(message.contains("complete session block", ignoreCase = true))
    }
}
