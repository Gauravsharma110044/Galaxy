package app.aether.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** The listener's balance and mono choice, read by every player's [StereoProcessor]. */
internal object StereoOutput {
    /** -1 is all left, 0 centre, 1 all right. */
    @Volatile var balance = 0f

    /** Both ears hear the left and right added together. */
    @Volatile var mono = false

    /** Whether the sound is being changed at all. */
    val active: Boolean get() = mono || abs(balance) > 0.001f
}

/**
 * Balance and mono for two-channel sound, in the decoded audio. With both at their defaults every
 * byte passes through as it came, so a bit-perfect route is not disturbed (the service treats an
 * active balance or mono as shaping, as it does the equaliser).
 */
internal class StereoProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
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
        val mono = StereoOutput.mono
        val balance = StereoOutput.balance.coerceIn(-1f, 1f)
        val leftGain = if (balance > 0f) 1f - balance else 1f
        val rightGain = if (balance < 0f) 1f + balance else 1f
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val short = encoding == C.ENCODING_PCM_16BIT
        val frame = if (short) 4 else 8
        while (input.remaining() >= frame) {
            var l = if (short) input.getShort() / 32768f else input.getFloat()
            var r = if (short) input.getShort() / 32768f else input.getFloat()
            if (mono) {
                val m = (l + r) * 0.5f
                l = m
                r = m
            }
            l *= leftGain
            r *= rightGain
            if (short) {
                out.putShort((l * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                out.putShort((r * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            } else {
                out.putFloat(l.coerceIn(-1f, 1f))
                out.putFloat(r.coerceIn(-1f, 1f))
            }
        }
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
    }
}
