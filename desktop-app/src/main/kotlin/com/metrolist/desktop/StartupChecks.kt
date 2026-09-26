package com.metrolist.desktop

import com.metrolist.desktop.player.LibMpvBackend
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.TimeUnit

data class StartupEnvironment(
    val appData: Path,
    val database: Path,
    val authHelper: Path,
    val mpvDll: Path,
)

data class StartupCheck(
    val name: String,
    val ok: Boolean,
)

data class StartupReport(
    val checks: List<StartupCheck>,
) {
    val healthy: Boolean
        get() = checks.all(StartupCheck::ok)
}

class StartupChecks(
    private val environment: StartupEnvironment,
    private val helperProbe: (Path, String) -> Boolean = ::runHelperProbe,
    private val writableProbe: (Path) -> Boolean = ::runWritableProbe,
    private val databaseProbe: (Path) -> Boolean = ::runDatabaseProbe,
    private val mpvProbe: (Path) -> Boolean = ::runMpvProbe,
) {
    fun run(): StartupReport {
        val helperExists = Files.isRegularFile(environment.authHelper)
        val mpvExists = Files.isRegularFile(environment.mpvDll)

        return StartupReport(
            checks =
                listOf(
                    StartupCheck(
                        name = "auth-helper",
                        ok =
                            helperExists &&
                                helperProbe(environment.authHelper, "--self-test"),
                    ),
                    StartupCheck(
                        name = "webview2",
                        ok =
                            helperExists &&
                                helperProbe(environment.authHelper, "--runtime-check"),
                    ),
                    StartupCheck(
                        name = "app-data",
                        ok = writableProbe(environment.appData),
                    ),
                    StartupCheck(
                        name = "database",
                        ok =
                            !Files.exists(environment.database) ||
                                databaseProbe(environment.database),
                    ),
                    StartupCheck(
                        name = "libmpv",
                        ok =
                            mpvExists &&
                                mpvProbe(environment.mpvDll),
                    ),
                ),
        )
    }

    companion object {
        fun forCurrentInstall(projectRoot: Path): StartupChecks =
            StartupChecks(
                environment = currentEnvironment(projectRoot),
            )

        fun currentEnvironment(projectRoot: Path): StartupEnvironment {
            val localAppData =
                requireNotNull(System.getenv("LOCALAPPDATA")) {
                    "LOCALAPPDATA is not available"
                }
            val appData = Path.of(localAppData, "MetroListWindows")
            val resources =
                System.getProperty("compose.application.resources.dir")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.takeIf(Files::exists)
                    ?: projectRoot
                        .resolve("runtime-dev")
                        .resolve("package-resources-current")
                        .resolve("common")

            return StartupEnvironment(
                appData = appData,
                database = appData.resolve("desktop").resolve("offline.db"),
                authHelper =
                    resources
                        .resolve("auth-helper")
                        .resolve("Metrolist.AuthHelper.exe"),
                mpvDll =
                    resources
                        .resolve("mpv")
                        .resolve("libmpv-2.dll"),
            )
        }
    }
}

private fun runHelperProbe(
    helper: Path,
    argument: String,
): Boolean =
    runCatching {
        val process =
            ProcessBuilder(
                helper.toAbsolutePath().toString(),
                argument,
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)

private fun runWritableProbe(path: Path): Boolean =
    runCatching {
        Files.createDirectories(path)
        Files.isDirectory(path) && Files.isWritable(path)
    }.getOrDefault(false)

private fun runDatabaseProbe(database: Path): Boolean =
    runCatching {
        Class.forName("org.sqlite.JDBC")
        val normalized =
            database
                .toAbsolutePath()
                .toString()
                .replace('\\', '/')
        val url = "jdbc:sqlite:file:" + normalized + "?mode=ro"

        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA quick_check").use { result ->
                    result.next() &&
                        result.getString(1).equals("ok", ignoreCase = true)
                }
            }
        }
    }.getOrDefault(false)

private fun runMpvProbe(mpvDll: Path): Boolean =
    runCatching {
        LibMpvBackend(mpvDll).use { }
        true
    }.getOrDefault(false)
