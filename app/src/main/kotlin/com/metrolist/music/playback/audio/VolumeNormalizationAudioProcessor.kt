package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

@UnstableApi
class VolumeNormalizationAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var sampleRate = 0
    private var channelCount = 0
    private var currentLinearGain = 1.0
    private var rampTargetLinearGain = 1.0
    private var rampStep = 0.0
    private var rampSamplesRemaining = 0

    @Volatile
    var signalLevel: Float = 0f
        private set

    @Volatile
    var signalTrackingEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) signalLevel = 0f
        }

    @Volatile
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                Timber.tag(TAG).d("Normalization processor enabled: $value")
            }
        }

    private data class GainState(val targetGainMb: Int, val linearGain: Double)

    @Volatile
    private var currentGain = GainState(0, 1.0)

    @Synchronized
    fun setTargetGain(gainMb: Int) {
        if (currentGain.targetGainMb != gainMb) {
            val linearGain = 10.0.pow(gainMb / 2000.0)
            currentGain = GainState(gainMb, linearGain)
            Timber.tag(TAG).d("Target gain set to $gainMb mB (Linear multiplier: $linearGain)")
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        bytesPerSample =
            when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
                else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }

        Timber.tag(TAG).d(
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, channels=${inputAudioFormat.channelCount}, encoding=$encoding",
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val gain = currentGain
        val targetLinearGain = if (enabled) gain.linearGain else 1.0
        val sampleCount = inputBuffer.remaining() / bytesPerSample
        val output = replaceOutputBuffer(sampleCount * bytesPerSample)
        val trackSignal = signalTrackingEnabled
        var peakLevel = 0f

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        when (encoding) {
            C.ENCODING_PCM_16BIT ->
                repeat(sampleCount) {
                    val sample = inputBuffer.getShort()
                    val sampleGain = nextLinearGain(targetLinearGain)
                    if (trackSignal) {
                        peakLevel = maxOf(peakLevel, abs(sample.toInt()) / 32768f)
                    }
                    output.putShort(
                        (sample * sampleGain)
                            .coerceIn(-32768.0, 32767.0)
                            .toInt()
                            .toShort(),
                    )
                }

            C.ENCODING_PCM_24BIT ->
                repeat(sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt()
                    val sample = (b2 shl 16) or (b1 shl 8) or b0
                    if (trackSignal) {
                        peakLevel = maxOf(
                            peakLevel,
                            (abs(sample.toLong()).coerceAtMost(8_388_608L) / 8_388_608f),
                        )
                    }
                    val sampleGain = nextLinearGain(targetLinearGain)
                    val processed =
                        (sample * sampleGain)
                            .coerceIn(-8388608.0, 8388607.0)
                            .toInt()
                    output.put((processed and 0xFF).toByte())
                    output.put(((processed shr 8) and 0xFF).toByte())
                    output.put(((processed shr 16) and 0xFF).toByte())
                }

            C.ENCODING_PCM_32BIT ->
                repeat(sampleCount) {
                    val sample = inputBuffer.getInt()
                    if (trackSignal) {
                        peakLevel = maxOf(
                            peakLevel,
                            (abs(sample.toDouble()) / 2_147_483_648.0).toFloat(),
                        )
                    }
                    val sampleGain = nextLinearGain(targetLinearGain)
                    output.putInt(
                        (sample * sampleGain)
                            .coerceIn(-2147483648.0, 2147483647.0)
                            .toLong()
                            .toInt(),
                    )
                }

            C.ENCODING_PCM_FLOAT ->
                repeat(sampleCount) {
                    val sample = inputBuffer.getFloat()
                    if (trackSignal) {
                        peakLevel = maxOf(peakLevel, abs(sample).coerceAtMost(1f))
                    }
                    val sampleGain = nextLinearGain(targetLinearGain)
                    output.putFloat(
                        (sample * sampleGain.toFloat()).coerceIn(-1.0f, 1.0f),
                    )
                }
        }

        if (trackSignal) {
            val target = peakLevel.coerceIn(0f, 1f)
            val previous = signalLevel
            signalLevel =
                if (target >= previous) {
                    (previous * 0.35f) + (target * 0.65f)
                } else {
                    (previous * 0.80f) + (target * 0.20f)
                }
        }

        output.flip()
    }

    private fun nextLinearGain(targetLinearGain: Double): Double {
        if (abs(targetLinearGain - rampTargetLinearGain) > GAIN_EPSILON) {
            rampTargetLinearGain = targetLinearGain
            rampSamplesRemaining =
                ((sampleRate * channelCount * GAIN_RAMP_MS) / 1000)
                    .coerceAtLeast(1)
            rampStep =
                (rampTargetLinearGain - currentLinearGain) /
                    rampSamplesRemaining.toDouble()
        }

        if (rampSamplesRemaining > 0) {
            currentLinearGain += rampStep
            rampSamplesRemaining--
            if (rampSamplesRemaining == 0) {
                currentLinearGain = rampTargetLinearGain
            }
        }
        return currentLinearGain
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
        sampleRate = 0
        channelCount = 0
        currentLinearGain = 1.0
        rampTargetLinearGain = 1.0
        rampStep = 0.0
        rampSamplesRemaining = 0
        signalLevel = 0f
    }

    private companion object {
        const val TAG = "VolumeNormalizationProcessor"
        const val GAIN_RAMP_MS = 15
        const val GAIN_EPSILON = 1e-9
    }
}
