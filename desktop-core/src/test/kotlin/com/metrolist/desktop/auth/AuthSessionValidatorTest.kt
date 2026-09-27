package com.metrolist.desktop.auth

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthSessionValidatorTest {
    @Test
    fun failedValidationRestoresPreviousYouTubeSession() = runBlocking {
        val originalCookie = YouTube.cookie
        val originalVisitorData = YouTube.visitorData
        val originalDataSyncId = YouTube.dataSyncId
        val originalAuthUser = YouTube.authUser
        val originalUseLogin = YouTube.useLoginForBrowse

        try {
            YouTube.cookie = "SAPISID=previous"
            YouTube.visitorData = "previous-visitor"
            YouTube.dataSyncId = "previous-sync"
            YouTube.authUser = "7"
            YouTube.useLoginForBrowse = false

            val candidate = AuthSession(
                cookie = "SAPISID=candidate",
                visitorData = "candidate-visitor",
                dataSyncId = "candidate-sync",
                authUser = "2",
            )

            val error = runCatching {
                AuthSessionValidator(
                    accountLoader = { error("invalid session") },
                ).validate(candidate)
            }.exceptionOrNull()

            assertIs<IllegalStateException>(error)
            assertEquals("SAPISID=previous", YouTube.cookie)
            assertEquals("previous-visitor", YouTube.visitorData)
            assertEquals("previous-sync", YouTube.dataSyncId)
            assertEquals("7", YouTube.authUser)
            assertEquals(false, YouTube.useLoginForBrowse)
        } finally {
            YouTube.cookie = originalCookie
            YouTube.visitorData = originalVisitorData
            YouTube.dataSyncId = originalDataSyncId
            YouTube.authUser = originalAuthUser
            YouTube.useLoginForBrowse = originalUseLogin
        }
    }
}
