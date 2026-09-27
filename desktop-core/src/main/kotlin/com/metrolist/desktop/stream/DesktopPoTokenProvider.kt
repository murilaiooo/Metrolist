package com.metrolist.desktop.stream

import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val desktopPoTokenJson =
    Json {
        ignoreUnknownKeys = true
    }

@Serializable
internal data class DesktopPoTokenResponse(
    val playerRequestToken: String,
    val streamingDataToken: String,
    val visitorData: String,
)

internal fun desktopPoTokenHelperCommand(
    baseCommand: List<String>,
    videoId: String,
    visitorData: String,
): List<String> =
    baseCommand + listOf("--po-token", videoId, visitorData)

internal fun parseDesktopPoTokenHelperResponse(raw: String): DesktopPoTokenResponse {
    val response = desktopPoTokenJson.decodeFromString<DesktopPoTokenResponse>(raw.trim())
    require(response.playerRequestToken.isNotBlank()) { "PO-token helper returned an empty player token" }
    require(response.streamingDataToken.isNotBlank()) { "PO-token helper returned an empty streaming token" }
    require(response.visitorData.isNotBlank()) { "PO-token helper returned empty visitor data" }
    return response
}

private class DesktopPoTokenHelperClient(
    private val baseCommand: List<String>,
    private val workingDirectory: Path?,
    private val timeoutMs: Long,
) {
    init {
        require(baseCommand.isNotEmpty()) { "PO-token helper command is empty" }
        require(timeoutMs > 0L)
    }

    suspend fun request(
        videoId: String,
        visitorData: String,
    ): DesktopPoTokenResponse =
        withContext(Dispatchers.IO) {
            val command =
                desktopPoTokenHelperCommand(
                    baseCommand = baseCommand,
                    videoId = videoId,
                    visitorData = visitorData,
                )
            val processBuilder = ProcessBuilder(command)
            workingDirectory?.let { processBuilder.directory(it.toFile()) }

            val process = processBuilder.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("PO-token helper timed out")
            }

            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            if (process.exitValue() != 0) {
                val safeDetail =
                    stderr
                        .lineSequence()
                        .firstOrNull()
                        ?.take(160)
                        .orEmpty()
                throw IllegalStateException(
                    if (safeDetail.isBlank()) {
                        "PO-token helper failed with exit " + process.exitValue()
                    } else {
                        "PO-token helper failed with exit " + process.exitValue() + ": " + safeDetail
                    },
                )
            }

            parseDesktopPoTokenHelperResponse(stdout).also { response ->
                check(response.visitorData == visitorData) {
                    "PO-token helper returned mismatched visitor data"
                }
            }
        }
}

class DesktopPoTokenProvider internal constructor(
    private val requestTokens: suspend (String, String) -> DesktopPoTokenResponse,
) : TokenProvider {
    constructor(
        baseCommand: List<String>,
        workingDirectory: Path? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ) : this(
        DesktopPoTokenHelperClient(
            baseCommand = baseCommand,
            workingDirectory = workingDirectory,
            timeoutMs = timeoutMs,
        )::request,
    )

    private val mutex = Mutex()

    override val capabilities =
        TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true,
        )

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult =
        mutex.withLock {
            val response = requestTokens(videoId, visitorData)
            check(response.visitorData == visitorData) {
                "PO-token helper returned mismatched visitor data"
            }
            PoTokenResult(
                playerRequestToken = response.playerRequestToken,
                streamingDataToken = response.streamingDataToken,
                visitorData = response.visitorData,
            )
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 75_000L
    }
}
