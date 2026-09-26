package com.metrolist.desktop.auth

import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DpapiSessionStoreTest {
    @Test
    fun roundTripsEncryptedSessionAndClearsIt() {
        val dir = Files.createTempDirectory("metrolist-auth-test")
        val path = dir.resolve("session.bin")
        val store = DpapiSessionStore(path)
        val session = AuthSession(
            cookie = "SAPISID=super-secret",
            visitorData = "visitor",
            dataSyncId = "page",
            authUser = "0",
            accountName = "Name",
            accountEmail = "mail@example.com",
            accountChannelHandle = "@name",
        )

        store.save(session)

        assertTrue(Files.exists(path))
        assertFalse(path.readBytes().decodeToString().contains("super-secret"))
        assertEquals(session, store.load())

        val encryptedBeforeClear = path.readBytes()
        assertTrue(encryptedBeforeClear.isNotEmpty())

        store.clear()
        assertNull(store.load())
        assertFalse(Files.exists(path))
    }
}
