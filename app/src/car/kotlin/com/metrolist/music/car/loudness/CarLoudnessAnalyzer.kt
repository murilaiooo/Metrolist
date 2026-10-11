package com.metrolist.music.car.loudness

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.metrolist.music.playback.loudness.CarLoudnessMeasurement
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

class CarLoudnessAnalyzer(
    private val context: Context,
) {
    suspend fun analyze(
        songId: String,
        fingerprint: String,
        locator: String,
    ): CarLoudnessMeasurement {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            setDataSource(extractor, locator)
            val trackIndex = selectAudioTrack(extractor)
            require(trackIndex >= 0) { "No decodable audio track" }
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val meter = StreamingBs1770Meter()
            decode(extractor, codec, meter)
            val result = meter.finish()

            return CarLoudnessMeasurement(
                songId = songId,
                fingerprint = fingerprint,
                integratedLufs = result.integratedLufs,
                truePeakDbtp = result.peakDbtp,
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
    private fun setDataSource(
        extractor: MediaExtractor,
        locator: String,
    ) {
        val uri = Uri.parse(locator)
        when (uri.scheme?.lowercase()) {
            "content", "file" -> extractor.setDataSource(context, uri, null)
            null -> extractor.setDataSource(locator)
            else -> extractor.setDataSource(locator)
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return index
            }
        }
        return -1
    }

    private suspend fun decode(
        extractor: MediaExtractor,
        codec: MediaCodec,
        meter: StreamingBs1770Meter,
    ) {
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false

        while (!outputEnded) {
            coroutineContext.ensureActive()

            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = requireNotNull(codec.getInputBuffer(inputIndex))
                    input.clear()
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            size,
                            extractor.sampleTime,
                            extractor.sampleFlags,
                        )
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    meter.configure(codec.outputFormat)
                else -> {
                    if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val output = requireNotNull(codec.getOutputBuffer(outputIndex))
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            meter.consume(output.slice().order(ByteOrder.LITTLE_ENDIAN))
                        }
                        outputEnded =
                            info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        }
    }

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
    }
}

private data class LoudnessResult(
    val integratedLufs: Double,
    val peakDbtp: Double,
)

private class StreamingBs1770Meter {
    private var sampleRate = 0
    private var channels = 0
    private var encoding = AudioFormat.ENCODING_INVALID
    private var blockFrames = 0
    private var stepFrames = 0

    private lateinit var shelf: Array<Biquad>
    private lateinit var highPass: Array<Biquad>
    private lateinit var energyRing: DoubleArray
    private lateinit var peakEstimator: Bs1770TruePeakEstimator

    private var frameCount = 0L
    private var ringPosition = 0
    private var runningEnergy = 0.0
    private val blockEnergies = ArrayList<Double>()
    fun configure(format: MediaFormat) {
        val newRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val newChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val newEncoding =
            runCatching {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }.getOrDefault(AudioFormat.ENCODING_PCM_16BIT)

        if (sampleRate != 0) {
            require(newRate == sampleRate && newChannels == channels) {
                "Audio format changed during loudness analysis"
            }
            encoding = newEncoding
            return
        }

        require(newChannels in 1..5) { "Unsupported channel count: $newChannels" }
        require(
            newEncoding == AudioFormat.ENCODING_PCM_16BIT ||
                newEncoding == AudioFormat.ENCODING_PCM_24BIT_PACKED ||
                newEncoding == AudioFormat.ENCODING_PCM_32BIT ||
                newEncoding == AudioFormat.ENCODING_PCM_FLOAT,
        ) { "Unsupported PCM encoding: $newEncoding" }

        sampleRate = newRate
        channels = newChannels
        encoding = newEncoding
        blockFrames = (sampleRate * 0.400).toInt().coerceAtLeast(1)
        stepFrames = (sampleRate * 0.100).toInt().coerceAtLeast(1)
        energyRing = DoubleArray(blockFrames)
        shelf = Array(channels) { Biquad.deManShelf(sampleRate.toDouble()) }
        highPass = Array(channels) { Biquad.deManHighPass(sampleRate.toDouble()) }
        peakEstimator = Bs1770TruePeakEstimator(channels, sampleRate)
    }

    fun consume(buffer: ByteBuffer) {
        check(sampleRate > 0) { "Decoder output format was not reported" }
        val bytesPerSample =
            when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
                -> 4
                else -> error("Unsupported PCM encoding: $encoding")
            }
        val frameBytes = bytesPerSample * channels
        val completeFrames = buffer.remaining() / frameBytes

        repeat(completeFrames) {
            var frameEnergy = 0.0
            for (channel in 0 until channels) {
                val sample =
                    when (encoding) {
                        AudioFormat.ENCODING_PCM_16BIT ->
                            buffer.getShort() / 32768.0
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                            val b0 = buffer.get().toInt() and 0xFF
                            val b1 = buffer.get().toInt() and 0xFF
                            val b2 = buffer.get().toInt()
                            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608.0
                        }
                        AudioFormat.ENCODING_PCM_32BIT ->
                            buffer.getInt() / 2_147_483_648.0
                        AudioFormat.ENCODING_PCM_FLOAT ->
                            buffer.getFloat().toDouble()
                        else -> 0.0
                    }.coerceIn(-4.0, 4.0)

                peakEstimator.add(channel, sample)
                val weighted = highPass[channel].process(shelf[channel].process(sample))
                frameEnergy += channelWeight(channel, channels) * weighted * weighted
            }
            addFrameEnergy(frameEnergy)
        }
    }
    private fun addFrameEnergy(energy: Double) {
        if (frameCount >= blockFrames) {
            runningEnergy -= energyRing[ringPosition]
        }
        energyRing[ringPosition] = energy
        runningEnergy += energy
        ringPosition = (ringPosition + 1) % blockFrames
        frameCount++

        if (
            frameCount >= blockFrames &&
            (frameCount - blockFrames) % stepFrames == 0L
        ) {
            blockEnergies += runningEnergy / blockFrames.toDouble()
        }
    }

    fun finish(): LoudnessResult {
        require(frameCount >= blockFrames) { "Audio is too short for loudness analysis" }
        require(blockEnergies.isNotEmpty()) { "No loudness blocks were produced" }

        val absoluteGated =
            blockEnergies.filter { energy ->
                energy > 0.0 && loudnessOf(energy) >= ABSOLUTE_GATE_LUFS
            }
        require(absoluteGated.isNotEmpty()) { "Audio is below the absolute loudness gate" }

        val absoluteMean = absoluteGated.average()
        val relativeGate = loudnessOf(absoluteMean) - RELATIVE_GATE_LU
        val finalBlocks =
            blockEnergies.filter { energy ->
                if (energy <= 0.0) return@filter false
                val loudness = loudnessOf(energy)
                loudness > ABSOLUTE_GATE_LUFS && loudness > relativeGate
            }
        require(finalBlocks.isNotEmpty()) { "Audio is below the relative loudness gate" }

        val integrated = loudnessOf(finalBlocks.average())
        peakEstimator.finish()
        val peak = peakEstimator.maximum.coerceAtLeast(MIN_PEAK)
        val peakDb = 20.0 * log10(peak)

        return LoudnessResult(
            integratedLufs = integrated,
            peakDbtp = peakDb,
        )
    }
    private fun loudnessOf(energy: Double): Double =
        LOUDNESS_OFFSET + 10.0 * log10(energy)

    private fun channelWeight(
        channel: Int,
        channelCount: Int,
    ): Double {
        if (channelCount <= 3) return 1.0
        return if (channel >= channelCount - 2) 1.41 else 1.0
    }

    private companion object {
        const val LOUDNESS_OFFSET = -0.691
        const val ABSOLUTE_GATE_LUFS = -70.0
        const val RELATIVE_GATE_LU = 10.0
        const val MIN_PEAK = 1e-12
    }
}

private class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(input: Double): Double {
        val output =
            b0 * input +
                b1 * x1 +
                b2 * x2 -
                a1 * y1 -
                a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }
    companion object {
        fun deManShelf(rate: Double): Biquad {
            val gain = 3.99984385397
            val q = 0.7071752369554193
            val frequency = 1681.9744509555319
            val k = tan(PI * frequency / rate)
            val vh = 10.0.pow(gain / 20.0)
            val vb = vh.pow(0.499666774155)
            val denominator = 1.0 + k / q + k * k
            return Biquad(
                b0 = (vh + vb * k / q + k * k) / denominator,
                b1 = 2.0 * (k * k - vh) / denominator,
                b2 = (vh - vb * k / q + k * k) / denominator,
                a1 = 2.0 * (k * k - 1.0) / denominator,
                a2 = (1.0 - k / q + k * k) / denominator,
            )
        }

        fun deManHighPass(rate: Double): Biquad {
            val q = 0.5003270373253953
            val frequency = 38.13547087613982
            val k = tan(PI * frequency / rate)
            val denominator = 1.0 + k / q + k * k
            return Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2.0 * (k * k - 1.0) / denominator,
                a2 = (1.0 - k / q + k * k) / denominator,
            )
        }
    }
}
private class Bs1770TruePeakEstimator(
    private val channels: Int,
    sampleRate: Int,
) {
    private data class Tap(
        val delayIndex: Int,
        val coefficient: Double,
    )

    private val factor =
        when {
            sampleRate < 96_000 -> 4
            sampleRate < 192_000 -> 2
            else -> 1
        }
    private val delay = if (factor == 1) 1 else (FIR_TAPS + factor - 1) / factor
    private val buffers = Array(channels) { DoubleArray(delay) }
    private val filters: Array<List<Tap>> =
        if (factor == 1) {
            emptyArray()
        } else {
            Array(factor) { phase ->
                buildList {
                    for (tap in 0 until FIR_TAPS) {
                        if (tap % factor != phase) continue
                        val m = tap.toDouble() - (FIR_TAPS - 1) / 2.0
                        var coefficient =
                            if (abs(m) > ALMOST_ZERO) {
                                val x = m * PI / factor
                                sin(x) / x
                            } else {
                                1.0
                            }
                        coefficient *=
                            0.5 * (1.0 - cos(2.0 * PI * tap / (FIR_TAPS - 1)))
                        if (abs(coefficient) > ALMOST_ZERO) {
                            add(
                                Tap(
                                    delayIndex = tap / factor,
                                    coefficient = coefficient,
                                ),
                            )
                        }
                    }
                }
            }
        }

    private var position = 0
    private var finished = false

    var maximum: Double = 0.0
        private set

    fun add(
        channel: Int,
        sample: Double,
    ) {
        maximum = maxOf(maximum, abs(sample))

        if (factor > 1) {
            buffers[channel][position] = sample
            filters.forEach { filter ->
                var accumulator = 0.0
                filter.forEach { tap ->
                    var index = position - tap.delayIndex
                    if (index < 0) index += delay
                    accumulator += buffers[channel][index] * tap.coefficient
                }
                maximum = maxOf(maximum, abs(accumulator))
            }
        }

        if (channel == channels - 1 && factor > 1) {
            position++
            if (position == delay) position = 0
        }
    }

    fun finish() {
        if (finished || factor == 1) return
        finished = true
        repeat(delay) {
            for (channel in 0 until channels) {
                add(channel, 0.0)
            }
        }
    }

    private companion object {
        const val FIR_TAPS = 49
        const val ALMOST_ZERO = 1e-6
    }
}
