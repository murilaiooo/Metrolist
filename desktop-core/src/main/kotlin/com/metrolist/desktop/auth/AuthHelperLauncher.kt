package com.metrolist.desktop.auth

import com.metrolist.innertube.utils.parseCookieString
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class AuthHelperLauncher(
    private val command: List<String>,
    private val workingDirectory: Path? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    fun login(): AuthSession {
        require(command.isNotEmpty()) { "Authentication helper command is empty" }

        val processBuilder = ProcessBuilder(command)
        workingDirectory?.let { processBuilder.directory(it.toFile()) }

        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException("Authentication helper failed with exit $exitCode")
        }

        val session = runCatching {
            json.decodeFromString<AuthSession>(stdout.trim())
        }.getOrElse {
            throw IllegalStateException("Authentication helper returned invalid session data")
        }

        if ("SAPISID" !in parseCookieString(session.cookie) || session.visitorData.isBlank()) {
            throw IllegalStateException("Authentication helper returned incomplete session data")
        }

        return session.copy(
            dataSyncId = session.dataSyncId.substringBefore("||"),
            authUser = session.authUser.filter(Char::isDigit).ifBlank { "0" },
        )
    }
}
