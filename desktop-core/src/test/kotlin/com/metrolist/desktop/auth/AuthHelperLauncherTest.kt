package com.metrolist.desktop.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthHelperLauncherTest {
    @Test
    fun consumesHelperJsonWithoutPrintingSecrets() {
        val script = Files.createTempFile("metrolist-auth-helper", ".ps1")
        Files.writeString(
            script,
            "Write-Output '{\"cookie\":\"SAPISID=secret\",\"visitorData\":\"visitor\",\"dataSyncId\":\"page\",\"authUser\":\"0\"}'",
        )

        val session = AuthHelperLauncher(
            command = listOf(
                "powershell",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script.toString(),
            ),
        ).login()

        assertEquals("SAPISID=secret", session.cookie)
        assertEquals("visitor", session.visitorData)
        assertEquals("page", session.dataSyncId)
    }

    @Test
    fun helperFailureDoesNotLeakStderrSecrets() {
        val script = Files.createTempFile("metrolist-auth-helper-fail", ".ps1")
        Files.writeString(
            script,
            "[Console]::Error.WriteLine('SAPISID=do-not-leak'); exit 5",
        )

        val error = assertFailsWith<IllegalStateException> {
            AuthHelperLauncher(
                command = listOf(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.toString(),
                ),
            ).login()
        }

        assertTrue("do-not-leak" !in error.message.orEmpty())
        assertTrue("exit 5" in error.message.orEmpty())
    }
}
