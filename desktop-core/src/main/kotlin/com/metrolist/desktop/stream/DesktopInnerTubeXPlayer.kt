package com.metrolist.desktop.stream

import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamExtractor
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import java.nio.file.Path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopInnerTubeXPlayer private constructor(
    private val extractorProvider: suspend () -> StreamExtractor,
) {
    constructor(configPath: Path) : this(
        extractorProvider = ProductionExtractorProvider(configPath)::get,
    )

    internal constructor(extractor: StreamExtractor) : this(
        extractorProvider = { extractor },
    )

    suspend fun prewarm() {
        extractorProvider().prewarm()
    }

    suspend fun resolve(
        videoId: String,
        contentHints: ContentHints = ContentHints(),
    ): ResolvedAudio {
        val hints =
            contentHints.withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = true,
            )

        val stream =
            requireNotNull(
                extractorProvider().extract(
                    videoId = videoId,
                    hints = hints,
                    excludedClients = emptySet(),
                    audioQuality = AudioQuality.HIGH,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                ),
            ) { "InnerTubeX returned no playable audio stream" }

        check(stream.sabrBootstrap == null) {
            "SABR is not supported by the Windows downloader"
        }

        return stream.toResolvedAudio()
    }

    private fun ExtractedStream.toResolvedAudio(): ResolvedAudio =
        ResolvedAudio(
            videoId = videoId,
            url = audioUrl,
            headers = headers,
            clientName = clientName,
            profileId = profileId,
            expiresAtEpochMs = expiresAt?.toEpochMilliseconds(),
            requireBoundedRange = requireBoundedRange,
            rangeChunkSizeBytes = rangeChunkSizeBytes,
            useRangeChunks = useRangeChunks,
            itag = itag,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = bitrate,
            sampleRate = sampleRate,
            contentLength = contentLengthBytes,
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb,
        )

    private class ProductionExtractorProvider(
        private val configPath: Path,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var currentBundle: ExtractionBundle? = null

        suspend fun get(): StreamExtractor {
            val transport = YouTube.extractionTransport()
            currentBundle
                ?.takeIf { it.transportGeneration == transport.generation }
                ?.let { return it.extractor }

            return mutex.withLock {
                val latest = YouTube.extractionTransport()
                currentBundle
                    ?.takeIf { it.transportGeneration == latest.generation }
                    ?.let { return@withLock it.extractor }

                currentBundle?.cipherService?.dispose()

                val repository = FilePlayerConfigRepository(configPath)
                val remoteStore =
                    RemotePlayerConfigStore(
                        latest.httpClient,
                        repository,
                        InnerTubeLogger.NONE,
                    )
                val cipherService =
                    YouTubeCipherService(
                        latest.httpClient,
                        remoteStore,
                        InnerTubeLogger.NONE,
                    )
                val parser =
                    YtConfigParserImpl(
                        latest.httpClient,
                        latest.innerTube,
                        remoteStore,
                        InnerTubeLogger.NONE,
                    ).withEmbeddedConfigFallback()

                val extractor =
                    InnerTubeExtractor(
                        configParser = parser,
                        cipherService = cipherService,
                        innerTube = latest.innerTube,
                        tokenProvider = null,
                        logger = InnerTubeLogger.NONE,
                    )

                ExtractionBundle(
                    transportGeneration = latest.generation,
                    cipherService = cipherService,
                    extractor = extractor,
                ).also { currentBundle = it }.extractor
            }
        }
    }

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

}

private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
    object : YtConfigParser by this {
        override suspend fun fetchConfig(
            videoId: String,
            useLoginCookies: Boolean,
        ) =
            try {
                this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
            } catch (_: IllegalStateException) {
                this@withEmbeddedConfigFallback.fetchEmbeddedConfig(
                    videoId,
                    useLoginCookies = false,
                )
            }
    }
