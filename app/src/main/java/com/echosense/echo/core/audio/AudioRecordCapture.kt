package com.echosense.echo.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.sqrt

/**
 * Captures raw, uncompressed PCM 16-bit audio for acoustic echo detection.
 * Uses AudioSource.UNPROCESSED where available to prevent OS noise filters from scrubbing near-ultrasound.
 */
class AudioRecordCapture(
    private val config: AudioConfig = AudioConfig()
) {
    companion object {
        private const val TAG = "AudioRecordCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var bufferSizeInBytes: Int = 0

    val isCapturing: Boolean
        get() = isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    /**
     * Initializes the AudioRecord instance.
     * Tries UNPROCESSED first, falling back to VOICE_RECOGNITION then MIC.
     */
    @SuppressLint("MissingPermission")
    fun initialize(sampleRate: Int = config.sampleRate): Boolean {
        release()
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize for sampleRate $sampleRate")
            return false
        }

        // Use at least 4x minimum buffer size for smooth continuous recording without dropouts
        bufferSizeInBytes = minBufferSize * 4

        // Audio sources in order of preference for raw acoustic sensing
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    Log.d(TAG, "AudioRecord initialized with source=$source, sampleRate=$sampleRate, bufferSize=$bufferSizeInBytes")
                    return true
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed initializing AudioRecord with source $source: ${e.message}")
            }
        }

        Log.e(TAG, "Could not initialize AudioRecord with any available audio source")
        return false
    }

    /**
     * Starts audio recording.
     */
    fun start(): Boolean {
        val record = audioRecord ?: return false
        try {
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.startRecording()
            }
            isRecording = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            isRecording = false
            return false
        }
    }

    /**
     * Reads audio samples into [buffer].
     * @return Number of samples read, or negative error code.
     */
    fun read(buffer: ShortArray, offset: Int = 0, size: Int = buffer.size): Int {
        val record = audioRecord ?: return -1
        if (!isRecording) return -1
        return record.read(buffer, offset, size)
    }

    /**
     * Computes RMS amplitude of a PCM 16-bit buffer.
     */
    fun computeRms(buffer: ShortArray, size: Int): Float {
        if (size <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until size) {
            val normalized = buffer[i] / 32768.0
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / size).toFloat()
    }

    /**
     * Stops audio recording.
     */
    fun stop() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            isRecording = false
        }
    }

    /**
     * Releases AudioRecord resources.
     */
    fun release() {
        stop()
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            isRecording = false
        }
    }
}
