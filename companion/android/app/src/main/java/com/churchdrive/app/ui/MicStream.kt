package com.churchdrive.app.ui

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * The microphone as a stream of 16 kHz, 16-bit, mono audio (what Home Assistant's voice pipeline wants), read on its own thread
 * and given to [onChunk] in pieces of about 80 ms. Needs the microphone permission; [start] is false without it.
 */
class MicStream(private val onChunk: (ByteArray, Int) -> Unit, private val onLevel: (Float) -> Unit = {}) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK) * 2)
        } catch (e: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        record = r
        running = true
        r.startRecording()
        thread = Thread {
            val buf = ByteArray(CHUNK)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    onChunk(buf, n)
                    onLevel(levelOf(buf, n))
                } else if (n < 0) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
        return true
    }

    fun stop() {
        running = false
        thread = null
        record?.let { runCatching { it.stop() }; runCatching { it.release() } }
        record = null
    }

    companion object {
        const val RATE = 16_000
        /** About 80 ms of audio. */
        const val CHUNK = 2_560

        /** How loud a piece is, 0 to 1. */
        fun levelOf(buf: ByteArray, length: Int): Float {
            var sum = 0.0
            var i = 0
            val samples = length / 2
            if (samples == 0) return 0f
            while (i + 1 < length) {
                val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                sum += v.toShort().toDouble() * v.toShort().toDouble()
                i += 2
            }
            return (sqrt(sum / samples) / 6000.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
