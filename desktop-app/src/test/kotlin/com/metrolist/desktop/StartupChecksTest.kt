package com.metrolist.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupChecksTest {
    @Test
    fun healthyInstallPassesEveryCheck() {
        val fixture = fixture("healthy")
        val checks =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, _ -> true },
                writableProbe = { true },
                databaseProbe = { true },
                mpvProbe = { true },
            )

        val report = checks.run()

        assertTrue(report.healthy)
        assertTrue(report.checks.all { it.ok })
    }

    @Test
    fun missingAuthHelperFails() {
        val fixture = fixture("missing-helper")
        Files.deleteIfExists(fixture.environment.authHelper)

        val report =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, _ -> true },
                writableProbe = { true },
                databaseProbe = { true },
                mpvProbe = { true },
            ).run()

        assertFalse(report.healthy)
        assertFalse(report.checks.first { it.name == "auth-helper" }.ok)
    }

    @Test
    fun missingMpvFails() {
        val fixture = fixture("missing-mpv")
        Files.deleteIfExists(fixture.environment.mpvDll)

        val report =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, _ -> true },
                writableProbe = { true },
                databaseProbe = { true },
                mpvProbe = { true },
            ).run()

        assertFalse(report.healthy)
        assertFalse(report.checks.first { it.name == "libmpv" }.ok)
    }

    @Test
    fun unwritableAppDataFails() {
        val fixture = fixture("unwritable")

        val report =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, _ -> true },
                writableProbe = { false },
                databaseProbe = { true },
                mpvProbe = { true },
            ).run()

        assertFalse(report.healthy)
        assertFalse(report.checks.first { it.name == "app-data" }.ok)
    }

    @Test
    fun unhealthyDatabaseFails() {
        val fixture = fixture("bad-db")
        Files.writeString(fixture.environment.database, "not a database")

        val report =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, _ -> true },
                writableProbe = { true },
                databaseProbe = { false },
                mpvProbe = { true },
            ).run()

        assertFalse(report.healthy)
        assertFalse(report.checks.first { it.name == "database" }.ok)
    }

    @Test
    fun missingWebView2RuntimeFails() {
        val fixture = fixture("missing-webview2")

        val report =
            StartupChecks(
                environment = fixture.environment,
                helperProbe = { _, argument -> argument != "--runtime-check" },
                writableProbe = { true },
                databaseProbe = { true },
                mpvProbe = { true },
            ).run()

        assertFalse(report.healthy)
        assertFalse(report.checks.first { it.name == "webview2" }.ok)
    }

    private fun fixture(name: String): Fixture {
        val root = Path.of(System.getProperty("java.io.tmpdir"), "startup-checks-$name")
        Files.createDirectories(root)
        val appData = root.resolve("app-data")
        Files.createDirectories(appData)

        val authHelper = root.resolve("Metrolist.AuthHelper.exe")
        val mpv = root.resolve("libmpv-2.dll")
        Files.write(authHelper, byteArrayOf(1))
        Files.write(mpv, byteArrayOf(2))

        return Fixture(
            StartupEnvironment(
                appData = appData,
                database = appData.resolve("offline.db"),
                authHelper = authHelper,
                mpvDll = mpv,
            ),
        )
    }

    private data class Fixture(
        val environment: StartupEnvironment,
    )
}
