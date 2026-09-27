package com.metrolist.desktop.player

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MpvPlayer(
    private val sourceSelector: PlayerSourceSelector,
    private val backend: MpvBackend,
    private val shuffleIndices: (List<Int>) -> List<Int> = { it.shuffled() },
) : DesktopPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(PlayerState())
    private val transitionMutex = Mutex()
    private var pollJob: Job? = null
    private var queueOfflineOnly = false
    private var playbackOrder: List<Int> = emptyList()
    private var playbackOrderPosition = -1
    private var endHandled = false

    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    override suspend fun play(
        song: SongItem,
        offlineOnly: Boolean,
    ) {
        playQueue(
            songs = listOf(song),
            startIndex = 0,
            offlineOnly = offlineOnly,
        )
    }

    override suspend fun playQueue(
        songs: List<SongItem>,
        startIndex: Int,
        offlineOnly: Boolean,
    ) {
        require(songs.isNotEmpty()) { "Playback queue cannot be empty" }
        require(startIndex in songs.indices) { "Playback queue start index is invalid" }

        transitionMutex.withLock {
            queueOfflineOnly = offlineOnly
            val shuffleEnabled = mutableState.value.shuffleEnabled
            configurePlaybackOrderLocked(
                queueSize = songs.size,
                currentIndex = startIndex,
                shuffleEnabled = shuffleEnabled,
            )
            mutableState.value =
                mutableState.value.copy(
                    queue = songs,
                    queueIndex = startIndex,
                    ended = false,
                    error = null,
                )
            playAtLocked(startIndex)
        }
    }

    override suspend fun next() {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            val nextIndex =
                nextQueueIndexLocked(
                    snapshot = snapshot,
                    allowWrap = snapshot.repeatMode == RepeatMode.ALL,
                )
            if (nextIndex != null) {
                playAtLocked(nextIndex)
            }
        }
    }

    override suspend fun previous() {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            if (snapshot.currentSong == null) return@withLock

            if (snapshot.positionMs > 3_000L) {
                restartCurrentLocked(snapshot)
                return@withLock
            }

            val previousIndex =
                previousQueueIndexLocked(
                    snapshot = snapshot,
                    allowWrap = snapshot.repeatMode == RepeatMode.ALL,
                )
            if (previousIndex == null || previousIndex == snapshot.queueIndex) {
                restartCurrentLocked(snapshot)
            } else {
                playAtLocked(previousIndex)
            }
        }
    }

    override suspend fun playNext(song: SongItem) {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            require(snapshot.queueIndex in snapshot.queue.indices) {
                "Play next requires an active playback queue"
            }
            val result = insertNext(snapshot.queue, snapshot.queueIndex, song)
            applyQueueMutationLocked(
                result = result,
                priorityNextIndex = result.currentIndex + 1,
            )
        }
    }

    override suspend fun addToQueue(song: SongItem) {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            require(snapshot.queueIndex in snapshot.queue.indices) {
                "Add to queue requires an active playback queue"
            }
            applyQueueMutationLocked(
                appendToQueue(snapshot.queue, snapshot.queueIndex, song),
            )
        }
    }

    override suspend fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            applyQueueMutationLocked(
                moveQueueEntry(snapshot.queue, snapshot.queueIndex, fromIndex, toIndex),
            )
        }
    }

    override suspend fun removeQueueItem(index: Int) {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            applyQueueMutationLocked(
                removeQueueEntry(snapshot.queue, snapshot.queueIndex, index),
            )
        }
    }

    override suspend fun clearUpcoming() {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            applyQueueMutationLocked(
                clearUpcomingEntries(snapshot.queue, snapshot.queueIndex),
            )
        }
    }

    override suspend fun toggleShuffle() {
        transitionMutex.withLock {
            val snapshot = mutableState.value
            if (snapshot.queue.isEmpty() || snapshot.queueIndex !in snapshot.queue.indices) {
                return@withLock
            }

            val enabled = !snapshot.shuffleEnabled
            configurePlaybackOrderLocked(
                queueSize = snapshot.queue.size,
                currentIndex = snapshot.queueIndex,
                shuffleEnabled = enabled,
            )
            mutableState.value = snapshot.copy(shuffleEnabled = enabled)
            updateCanSkipNextLocked()
        }
    }

    override suspend fun cycleRepeatMode() {
        transitionMutex.withLock {
            val nextMode =
                when (mutableState.value.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            mutableState.value = mutableState.value.copy(repeatMode = nextMode)
            updateCanSkipNextLocked()
        }
    }

    private suspend fun playAtLocked(index: Int) {
        val song = mutableState.value.queue[index]
        try {
            val source = sourceSelector.select(song, queueOfflineOnly)
            val audioFormat =
                when (source) {
                    is PlayerSource.Local -> {
                        backend.load(
                            source = source.path.toString(),
                            headers = emptyMap(),
                        )
                        source.format?.toPlaybackAudioFormat()
                    }

                    is PlayerSource.Remote -> {
                        backend.load(
                            source = source.resolved.url,
                            headers = source.resolved.headers,
                        )
                        source.resolved.toPlaybackAudioFormat()
                    }
                }

            backend.setPaused(false)
            endHandled = false
            playbackOrderPosition = playbackOrder.indexOf(index)
            mutableState.value =
                mutableState.value.copy(
                    currentSong = song,
                    queueIndex = index,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = null,
                    ended = false,
                    error = null,
                    audioFormat = audioFormat,
                )
            updateCanSkipNextLocked()
            startPolling()
        } catch (error: Throwable) {
            playbackOrderPosition = playbackOrder.indexOf(index)
            mutableState.value =
                mutableState.value.copy(
                    currentSong = song,
                    queueIndex = index,
                    isPlaying = false,
                    ended = false,
                    error =
                        error.message
                            ?.takeIf { it.isNotBlank() }
                            ?: error::class.simpleName
                            ?: "PlaybackError",
                    audioFormat = null,
                )
            updateCanSkipNextLocked()
            throw error
        }
    }

    override fun pause() {
        backend.setPaused(true)
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override fun resume() {
        val snapshot = mutableState.value
        if (snapshot.ended) {
            backend.seek(0.0)
            endHandled = false
        }
        backend.setPaused(false)
        mutableState.value =
            snapshot.copy(
                isPlaying = true,
                positionMs = if (snapshot.ended) 0L else snapshot.positionMs,
                ended = false,
                error = null,
            )
    }

    override fun seek(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        backend.seek(safePosition / 1000.0)
        if (mutableState.value.ended) {
            endHandled = false
        }
        mutableState.value =
            mutableState.value.copy(
                positionMs = safePosition,
                ended = false,
            )
    }

    override fun setVolume(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        backend.setVolume(safeVolume)
        mutableState.value = mutableState.value.copy(volume = safeVolume)
    }

    override fun stop() {
        backend.stop()
        pollJob?.cancel()
        endHandled = false
        mutableState.value =
            mutableState.value.copy(
                isPlaying = false,
                positionMs = 0L,
                ended = false,
            )
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob =
            scope.launch {
                while (isActive) {
                    val reachedEnd =
                        backend.property("eof-reached")
                            ?.equals("yes", ignoreCase = true)
                            ?: false
                    if (reachedEnd) {
                        handleEndOfTrack()
                        delay(100)
                        continue
                    }

                    val positionMs =
                        backend.property("time-pos")
                            ?.toDoubleOrNull()
                            ?.times(1000.0)
                            ?.toLong()
                            ?: mutableState.value.positionMs
                    val durationMs =
                        backend.property("duration")
                            ?.toDoubleOrNull()
                            ?.times(1000.0)
                            ?.toLong()
                    val paused =
                        backend.property("pause")
                            ?.equals("yes", ignoreCase = true)
                            ?: !mutableState.value.isPlaying
                    val volume =
                        backend.property("volume")
                            ?.toDoubleOrNull()
                            ?.toInt()
                            ?.coerceIn(0, 100)
                            ?: mutableState.value.volume

                    mutableState.value =
                        mutableState.value.copy(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isPlaying = !paused,
                            volume = volume,
                        )

                    delay(250)
                }
            }
    }

    private suspend fun handleEndOfTrack() {
        transitionMutex.withLock {
            if (endHandled) {
                mutableState.value = mutableState.value.copy(isPlaying = false)
                return@withLock
            }

            endHandled = true
            val snapshot = mutableState.value
            if (snapshot.repeatMode == RepeatMode.ONE && snapshot.queueIndex in snapshot.queue.indices) {
                playAtLocked(snapshot.queueIndex)
                return@withLock
            }

            val nextIndex =
                nextQueueIndexLocked(
                    snapshot = snapshot,
                    allowWrap = snapshot.repeatMode == RepeatMode.ALL,
                )
            if (nextIndex != null) {
                playAtLocked(nextIndex)
            } else {
                mutableState.value =
                    snapshot.copy(
                        isPlaying = false,
                        positionMs = snapshot.durationMs ?: snapshot.positionMs,
                        ended = true,
                    )
                updateCanSkipNextLocked()
            }
        }
    }

    private fun applyQueueMutationLocked(
        result: QueueMutationResult,
        priorityNextIndex: Int? = null,
    ) {
        val snapshot = mutableState.value
        mutableState.value =
            snapshot.copy(
                queue = result.queue,
                queueIndex = result.currentIndex,
            )

        configurePlaybackOrderLocked(
            queueSize = result.queue.size,
            currentIndex = result.currentIndex,
            shuffleEnabled = snapshot.shuffleEnabled,
        )

        if (
            snapshot.shuffleEnabled &&
            priorityNextIndex != null &&
            priorityNextIndex in result.queue.indices &&
            priorityNextIndex != result.currentIndex
        ) {
            val currentPosition = playbackOrder.indexOf(result.currentIndex)
            val reordered = playbackOrder.toMutableList()
            reordered.remove(priorityNextIndex)
            reordered.add(currentPosition + 1, priorityNextIndex)
            playbackOrder = reordered
            playbackOrderPosition = playbackOrder.indexOf(result.currentIndex)
        }

        updateCanSkipNextLocked()
    }

    private fun configurePlaybackOrderLocked(
        queueSize: Int,
        currentIndex: Int,
        shuffleEnabled: Boolean,
    ) {
        val naturalOrder = (0 until queueSize).toList()
        playbackOrder =
            if (shuffleEnabled) {
                val remaining = naturalOrder.filter { it != currentIndex }
                listOf(currentIndex) + shuffleIndices(remaining)
            } else {
                naturalOrder
            }
        playbackOrderPosition = playbackOrder.indexOf(currentIndex)
    }

    private fun nextQueueIndexLocked(
        snapshot: PlayerState,
        allowWrap: Boolean,
    ): Int? {
        if (snapshot.queue.isEmpty()) return null

        val currentPosition =
            playbackOrderPosition
                .takeIf { it in playbackOrder.indices && playbackOrder[it] == snapshot.queueIndex }
                ?: playbackOrder.indexOf(snapshot.queueIndex)
        if (currentPosition < 0) return null

        val nextPosition = currentPosition + 1
        if (nextPosition in playbackOrder.indices) {
            return playbackOrder[nextPosition]
        }
        return if (allowWrap && playbackOrder.size > 1) playbackOrder.first() else null
    }

    private fun previousQueueIndexLocked(
        snapshot: PlayerState,
        allowWrap: Boolean,
    ): Int? {
        if (snapshot.queue.isEmpty()) return null

        val currentPosition =
            playbackOrderPosition
                .takeIf { it in playbackOrder.indices && playbackOrder[it] == snapshot.queueIndex }
                ?: playbackOrder.indexOf(snapshot.queueIndex)
        if (currentPosition < 0) return null

        val previousPosition = currentPosition - 1
        if (previousPosition in playbackOrder.indices) {
            return playbackOrder[previousPosition]
        }
        return if (allowWrap && playbackOrder.size > 1) playbackOrder.last() else null
    }

    private fun restartCurrentLocked(snapshot: PlayerState) {
        backend.seek(0.0)
        backend.setPaused(false)
        endHandled = false
        mutableState.value =
            snapshot.copy(
                isPlaying = true,
                positionMs = 0L,
                ended = false,
                error = null,
            )
    }

    private fun updateCanSkipNextLocked() {
        val snapshot = mutableState.value
        val canSkip =
            nextQueueIndexLocked(
                snapshot = snapshot,
                allowWrap = snapshot.repeatMode == RepeatMode.ALL,
            ) != null
        mutableState.value = snapshot.copy(canSkipNext = canSkip)
    }

    override fun close() {
        pollJob?.cancel()
        scope.cancel()
        backend.close()
    }
}
