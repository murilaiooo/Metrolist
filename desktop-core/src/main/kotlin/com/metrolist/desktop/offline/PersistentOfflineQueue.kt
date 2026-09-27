package com.metrolist.desktop.offline

import com.metrolist.desktop.media.MediaTransferProgress
import com.metrolist.innertube.models.SongItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

class PersistentOfflineQueue(
    private val store: OfflineStore,
    private val worker: suspend (OfflineRecord, (MediaTransferProgress) -> Unit) -> OfflineRecord,
    maxConcurrent: Int = 3,
    autoResumePending: Boolean = true,
) : AutoCloseable {
    constructor(
        store: OfflineStore,
        worker: suspend (OfflineRecord) -> OfflineRecord,
        maxConcurrent: Int = 3,
        autoResumePending: Boolean = true,
    ) : this(
        store = store,
        worker = { record, _ -> worker(record) },
        maxConcurrent = maxConcurrent,
        autoResumePending = autoResumePending,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(maxConcurrent)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private val mutableDownloads = MutableStateFlow<List<OfflineRecord>>(emptyList())

    val downloads: StateFlow<List<OfflineRecord>> = mutableDownloads.asStateFlow()

    init {
        require(maxConcurrent > 0)
        store.recoverInterrupted()
        refresh()
        if (autoResumePending) {
            resumePending()
        }
    }

    fun resumePending() {
        store.listByState(OfflineState.QUEUED).forEach { launchIfNeeded(it.videoId) }
    }

    fun enqueue(song: SongItem) {
        val existing = store.get(song.id)
        if (existing?.state == OfflineState.COMPLETED) {
            refresh()
            return
        }

        val record =
            existing?.copy(
                state = OfflineState.QUEUED,
                bytesPerSecond = 0L,
                lastError = null,
                updateTimeMs = System.currentTimeMillis(),
            ) ?: OfflineRecord.queued(song)

        store.upsert(record)
        refresh()
        launchIfNeeded(record.videoId)
    }

    fun enqueueAll(songs: List<SongItem>) {
        songs.distinctBy(SongItem::id).forEach(::enqueue)
    }

    suspend fun enqueuePlaylist(
        playlistId: String,
        loader: PlaylistSongLoader = PlaylistSongLoader(),
    ): Int {
        val songs = loader.loadAll(playlistId)
        enqueueAll(songs)
        return songs.size
    }

    fun retry(videoId: String) {
        val record = store.get(videoId) ?: return
        if (record.state == OfflineState.COMPLETED) return
        store.upsert(
            record.copy(
                state = OfflineState.QUEUED,
                bytesPerSecond = 0L,
                lastError = null,
                updateTimeMs = System.currentTimeMillis(),
            ),
        )
        refresh()
        launchIfNeeded(videoId)
    }

    fun remove(videoId: String) {
        jobs.remove(videoId)?.cancel()
        store.remove(videoId)
        refresh()
    }

    suspend fun awaitIdle(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val active = jobs.values.any(Job::isActive)
                val pending =
                    store.listAll().any {
                        it.state == OfflineState.QUEUED || it.state == OfflineState.DOWNLOADING
                    }
                if (!active && !pending) return@withTimeoutOrNull true
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false

    private fun launchIfNeeded(videoId: String) {
        if (closed.get()) return

        synchronized(lock) {
            if (closed.get()) return
            if (jobs[videoId]?.isActive == true) return

            val job =
                scope.launch {
                    semaphore.withPermit {
                        val queued = store.get(videoId) ?: return@withPermit
                        if (queued.state == OfflineState.COMPLETED) return@withPermit

                        val downloading =
                            queued.copy(
                                state = OfflineState.DOWNLOADING,
                                bytesPerSecond = 0L,
                                updateTimeMs = System.currentTimeMillis(),
                                lastError = null,
                            )
                        store.upsert(downloading)
                        refresh()

                        val workerContext = coroutineContext
                        try {
                            val completed =
                                worker(downloading) { progress ->
                                    workerContext.ensureActive()
                                    updateProgress(videoId, progress)
                                }
                            workerContext.ensureActive()

                            val latest = store.get(videoId) ?: return@withPermit
                            store.upsert(
                                completed.copy(
                                    state = OfflineState.COMPLETED,
                                    bytesDownloaded =
                                        completed.bytesDownloaded.coerceAtLeast(
                                            latest.bytesDownloaded,
                                        ),
                                    contentLength =
                                        completed.contentLength
                                            ?: latest.contentLength,
                                    bytesPerSecond = 0L,
                                    updateTimeMs = System.currentTimeMillis(),
                                    lastError = null,
                                ),
                            )
                        } catch (error: Throwable) {
                            if (error is kotlinx.coroutines.CancellationException) throw error

                            workerContext.ensureActive()
                            val latest = store.get(videoId) ?: return@withPermit
                            store.upsert(
                                latest.copy(
                                    state = OfflineState.FAILED,
                                    bytesPerSecond = 0L,
                                    updateTimeMs = System.currentTimeMillis(),
                                    lastError = safeErrorMessage(error),
                                ),
                            )
                        } finally {
                            refresh()
                        }
                    }
                }

            jobs[videoId] = job
            job.invokeOnCompletion {
                jobs.remove(videoId, job)
                if (closed.get()) return@invokeOnCompletion

                if (store.get(videoId)?.state == OfflineState.QUEUED) {
                    launchIfNeeded(videoId)
                } else {
                    refresh()
                }
            }
        }
    }

    private fun updateProgress(
        videoId: String,
        progress: MediaTransferProgress,
    ) {
        val current = store.get(videoId) ?: return
        if (current.state != OfflineState.DOWNLOADING) return

        store.upsert(
            current.copy(
                bytesDownloaded = progress.bytesDownloaded,
                contentLength = progress.totalBytes,
                bytesPerSecond = progress.bytesPerSecond,
                updateTimeMs = System.currentTimeMillis(),
                lastError = null,
            ),
        )
        refresh()
    }

    private fun safeErrorMessage(error: Throwable): String {
        val type = error::class.simpleName ?: "TransferError"
        val detail =
            error.message
                ?.replace(Regex("""https?://\S+"""), "[url]")
                ?.trim()
                ?.take(240)
                .orEmpty()

        return if (detail.isBlank() || detail == type) {
            type
        } else {
            type + ": " + detail
        }
    }

    private fun refresh() {
        if (closed.get()) return
        mutableDownloads.value = store.listAll()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            scope.cancel()
        }
    }
}
