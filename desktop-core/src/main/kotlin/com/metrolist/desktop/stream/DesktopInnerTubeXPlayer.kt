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
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import java.nio.file.Path
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopInnerTubeXPlayer private constructor(
    private val extractorProvider: suspend (Boolean) -> StreamExtractor,
    private val tokenFallbackEnabled: Boolean,
) {
    constructor(
        configPath: Path,
        tokenProvider: TokenProvider? = null,
    ) : this(
        extractorProvider = ProductionExtractorProvider(configPath, tokenProvider)::get,
        tokenFallbackEnabled = tokenProvider != null,
    )

    internal constructor(extractor: StreamExtractor) : this(
        extractorProvider = { extractor },
        tokenFallbackEnabled = false,
    )

    internal constructor(
        extractor: StreamExtractor,
        tokenFallbackExtractor: StreamExtractor,
    ) : this(
        extractorProvider = { useTokenFallback ->
            if (useTokenFallback) tokenFallbackExtractor else extractor
        },
        tokenFallbackEnabled = true,
    )

    suspend fun prewarm() {
        extractorProvider(false).prewarm()
    }

    suspend fun resolve(
        videoId: String,
        contentHints: ContentHints = ContentHints(),
    ): ResolvedAudio =
        resolveInternal(
            videoId = videoId,
            contentHints = contentHints,
            excludedClients = emptySet(),
            audioQuality = AudioQuality.HIGH,
        )

    internal suspend fun resolveForAudit(
        videoId: String,
        excludedClients: Set<String>,
        contentHints: ContentHints = ContentHints(),
        audioQuality: AudioQuality = AudioQuality.HIGH,
    ): ResolvedAudio =
        resolveInternal(
            videoId = videoId,
            contentHints = contentHints,
            excludedClients = excludedClients,
            audioQuality = audioQuality,
        )

    private suspend fun resolveInternal(
        videoId: String,
        contentHints: ContentHints,
        excludedClients: Set<String>,
        audioQuality: AudioQuality,
    ): ResolvedAudio {
        val hints =
            contentHints.withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = false,
            )

        val stream =
            try {
                extractRequired(
                    extractor = extractorProvider(false),
                    videoId = videoId,
                    hints = hints,
                    excludedClients = excludedClients,
                    audioQuality = audioQuality,
                )
            } catch (error: StreamResolveException) {
                if (!tokenFallbackEnabled ||
                    error.reason != StreamResolveException.Reason.NO_PLAYABLE_STREAM
                ) {
                    throw error
                }

                extractRequired(
                    extractor = extractorProvider(true),
                    videoId = videoId,
                    hints = hints,
                    excludedClients = excludedClients,
                    audioQuality = audioQuality,
                )
            }

        check(stream.sabrBootstrap == null) {
            "SABR is not supported by the Windows downloader"
        }

        return stream.toResolvedAudio()
    }

    private suspend fun extractRequired(
        extractor: StreamExtractor,
        videoId: String,
        hints: ContentHints,
        excludedClients: Set<String>,
        audioQuality: AudioQuality,
    ): ExtractedStream =
        requireNotNull(
            extractor.extract(
                videoId = videoId,
                hints = hints,
                excludedClients = excludedClients,
                audioQuality = audioQuality,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ),
        ) { "InnerTubeX returned no playable audio stream" }

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
        private val tokenProvider: TokenProvider?,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var currentBundle: ExtractionBundle? = null

        suspend fun get(useTokenFallback: Boolean): StreamExtractor {
            val transport = YouTube.extractionTransport()
            currentBundle
                ?.takeIf { it.transportGeneration == transport.generation }
                ?.let { return it.select(useTokenFallback) }

            return mutex.withLock {
                val latest = YouTube.extractionTransport()
                currentBundle
                    ?.takeIf { it.transportGeneration == latest.generation }
                    ?.let { return@withLock it.select(useTokenFallback) }

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
                fun parser(): YtConfigParser =
                    YtConfigParserImpl(
                        latest.httpClient,
                        latest.innerTube,
                        remoteStore,
                        InnerTubeLogger.NONE,
                    ).withEmbeddedConfigFallback()

                val extractor =
                    InnerTubeExtractor(
                        configParser = parser(),
                        cipherService = cipherService,
                        innerTube = latest.innerTube,
                        tokenProvider = null,
                        logger = InnerTubeLogger.NONE,
                    )
                val tokenExtractor =
                    tokenProvider?.let { provider ->
                        InnerTubeExtractor(
                            configParser = parser(),
                            cipherService = cipherService,
                            innerTube = latest.innerTube,
                            tokenProvider = provider,
                            logger = InnerTubeLogger.NONE,
                        )
                    }

                ExtractionBundle(
                    transportGeneration = latest.generation,
                    cipherService = cipherService,
                    extractor = extractor,
                    tokenExtractor = tokenExtractor,
                ).also { currentBundle = it }.select(useTokenFallback)
            }
        }
    }

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
        val tokenExtractor: InnerTubeExtractor?,
    ) {
        fun select(useTokenFallback: Boolean): StreamExtractor =
            if (useTokenFallback) tokenExtractor ?: extractor else extractor
    }

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
