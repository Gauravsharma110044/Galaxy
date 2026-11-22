package app.aether.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/** The listener's balance, mono and preamp choices, read by every player's [StereoProcessor]. */
internal object StereoOutput {
    /** -1 is all left, 0 centre, 1 all right. */
    @Volatile var balance = 0f

    /** Both ears hear the left and right added together. */
    @Volatile var mono = false

    /** A gain applied to everything, in decibels. */
    @Volatile var preampDb = 0f

    private var decided = false
    private var inChain = false

    /**
     * The processor sits in the sound path only when one of the three choices was already on as the
     * player was built (the first time decides, for every player after it). With all of them off the
     * path is exactly the one without it, so a phone that never uses them is not touched by it.
     */
    @Synchronized fun processorIfWanted(): StereoProcessor? {
        if (!decided) { decided = true; inChain = active }
        return if (inChain) StereoProcessor() else null
    }

    /** Whether the sound is being changed at all. */
    val active: Boolean get() = mono || abs(balance) > 0.001f || abs(preampDb) > 0.05f
}

/**
 * Balance, mono and a preamp, in the decoded audio. Balance and mono apply to two-channel sound;
 * the preamp to any. With everything at its default every byte passes through as it came, so a
 * bit-perfect route is not disturbed (the service treats any of them being on as shaping, as it
 * does the equaliser). A boost is kept from clipping by a soft knee above 80 percent of full scale.
 */
internal class StereoProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var channels = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    private fun soft(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
        return if (x < 0f) -y else y
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes)
        if (!StereoOutput.active) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val stereo = channels == 2
        val mono = stereo && StereoOutput.mono
        val balance = StereoOutput.balance.coerceIn(-1f, 1f)
        val leftGain = if (stereo && balance > 0f) 1f - balance else 1f
        val rightGain = if (stereo && balance < 0f) 1f + balance else 1f
        val gain = 10f.pow(StereoOutput.preampDb / 20f)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val short = encoding == C.ENCODING_PCM_16BIT
        val frame = (if (short) 2 else 4) * channels
        while (input.remaining() >= frame) {
            if (stereo) {
                var l = if (short) input.getShort() / 32768f else input.getFloat()
                var r = if (short) input.getShort() / 32768f else input.getFloat()
                if (mono) {
                    val m = (l + r) * 0.5f
                    l = m
                    r = m
                }
                l = soft(l * leftGain * gain)
                r = soft(r * rightGain * gain)
                put(out, l, short)
                put(out, r, short)
            } else {
                for (c in 0 until channels) {
                    val x = if (short) input.getShort() / 32768f else input.getFloat()
                    put(out, soft(x * gain), short)
                }
            }
        }
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }

    private fun put(out: ByteBuffer, v: Float, short: Boolean) {
        if (short) out.putShort((v * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()) else out.putFloat(v.coerceIn(-1f, 1f))
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        channels = 0
    }

    private companion object {
        const val KNEE = 0.8f
    }
}
