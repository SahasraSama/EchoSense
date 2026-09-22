package com.echosense.echo.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Low-latency AudioTrack player for emitting acoustic FMCW chirps.
 * Uses MODE_STATIC for sample-accurate, zero-jitter playback.
 */
class AudioTrackPlayer(
    private val config: AudioConfig = AudioConfig()
) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var isInitialized = false

    /**
     * Initializes the static AudioTrack with the synthesized chirp.
     */
    fun initialize(chirpBytes: ByteArray, sampleRate: Int = config.sampleRate): Boolean {
        release()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(chirpBytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            val written = audioTrack?.write(chirpBytes, 0, chirpBytes.size) ?: -1
            if (written != chirpBytes.size) {
                Log.e(TAG, "Failed to write complete chirp into AudioTrack buffer: written=$written")
                return false
            }

            audioTrack?.setVolume(1.0f)
            isInitialized = true
            Log.d(TAG, "AudioTrack initialized successfully with sampleRate=$sampleRate, size=${chirpBytes.size}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack initialization exception: ${e.message}", e)
            isInitialized = false
            return false
        }
    }

    /**
     * Plays the preloaded chirp burst.
     */
    fun playChirp(): Boolean {
        val track = audioTrack ?: return false
        if (!isInitialized) return false
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing chirp: ${e.message}", e)
            return false
        }
    }

    /**
     * Releases AudioTrack resources.
     */
    fun release() {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during AudioTrack release: ${e.message}")
        } finally {
            audioTrack = null
            isInitialized = false
        }
    }
}
