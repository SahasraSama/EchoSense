package com.echosense.echo.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Result of an acoustic echo ranging ping.
 */
data class AcousticResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val distanceMeters: Float = -1f,
    val formattedDistance: String = "NO RELIABLE ECHO",
    val isReliable: Boolean = false,
    val confidence: ConfidenceResult = ConfidenceResult(0f, ConfidenceRating.UNRELIABLE, "No measurement"),
    val directPathDelayMs: Float = 0f,
    val echoDelayMs: Float = 0f,
    val peakCorrelation: Float = 0f,
    val snrDb: Float = 0f,
    val psr: Float = 0f,
    val ambientNoiseRms: Float = 0f,
    val activeBand: String = "18-21 kHz",
    val statusMessage: String = "IDLE"
)

/**
 * Summary of hardware audio validation for near-ultrasound capability.
 */
data class AudioHardwareCapability(
    val supportedSampleRates: List<Int> = emptyList(),
    val selectedSampleRate: Int = 48000,
    val isNearUltrasoundUsable: Boolean = false,
    val isFallbackUsable: Boolean = false,
    val activeStartFreqHz: Float = 18000f,
    val activeEndFreqHz: Float = 21000f,
    val measuredDirectPathDelayMs: Float = 0.25f,
    val validationStatus: String = "NOT_TESTED"
)

/**
 * Complete Acoustic Ranging Engine coordinating chirp generation, playback,
 * microphone capture, filtering, correlation, and echo detection.
 */
class AcousticRangingEngine(
    private val context: Context,
    val config: AudioConfig = AudioConfig()
) {
    companion object {
        private const val TAG = "AcousticRangingEngine"
    }

    private val chirpGenerator = ChirpGenerator(config)
    private val bandpassFilter = BandpassFilter(config.sampleRate, config.startFreqHz - 500f, config.endFreqHz + 500f)
    private val crossCorrelator = CrossCorrelator()
    private val echoDetector = EchoDetector(config)
    private val distanceEstimator = DistanceEstimator(config)
    private val confidenceEstimator = ConfidenceEstimator(config)

    private val audioTrackPlayer = AudioTrackPlayer(config)
    private val audioRecordCapture = AudioRecordCapture(config)

    private val _latestResult = MutableStateFlow(AcousticResult())
    val latestResult: StateFlow<AcousticResult> = _latestResult.asStateFlow()

    private val _hardwareCapability = MutableStateFlow(AudioHardwareCapability())
    val hardwareCapability: StateFlow<AudioHardwareCapability> = _hardwareCapability.asStateFlow()

    private val _correlationCurve = MutableStateFlow(FloatArray(0))
    val correlationCurve: StateFlow<FloatArray> = _correlationCurve.asStateFlow()

    private var activeReferenceChirp: FloatArray = FloatArray(0)
    private var activeReferenceBytes: ByteArray = ByteArray(0)

    private var rangingJob: Job? = null
    private var pingIntervalMs: Long = 350L // Adaptive: 200ms (scanning) to 1200ms (stationary)

    private var calibratedDirectDelayMs: Float? = null

    /**
     * Probes device audio hardware and determines whether near-ultrasound (18-21 kHz)
     * or fallback (15-18 kHz) produces reliable acoustic response.
     */
    suspend fun probeAndValidateHardware(): AudioHardwareCapability = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting audio hardware capability detection & validation...")

        // 1. Check supported sample rates
        val testRates = listOf(48000, 44100, 96000)
        val validRates = mutableListOf<Int>()

        for (rate in testRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf > 0) {
                validRates.add(rate)
            }
        }

        val primarySampleRate = if (validRates.contains(48000)) 48000 else validRates.firstOrNull() ?: 44100
        Log.i(TAG, "Supported sample rates: $validRates, selected: $primarySampleRate")

        // 2. Validate Primary Near-Ultrasound Band (18 - 21 kHz)
        val nearUltrasoundValid = testFrequencyBand(
            f0 = config.startFreqHz,
            f1 = config.endFreqHz,
            sampleRate = primarySampleRate
        )

        var fallbackValid = false
        var selectedF0 = config.startFreqHz
        var selectedF1 = config.endFreqHz
        var bandName = "18-21 kHz (Near-Ultrasound)"

        if (nearUltrasoundValid) {
            Log.i(TAG, "Near-ultrasound band (18-21 kHz) verified successfully!")
        } else {
            Log.w(TAG, "Near-ultrasound band (18-21 kHz) had insufficient SNR. Testing fallback band (15-18 kHz)...")
            fallbackValid = testFrequencyBand(
                f0 = config.fallbackStartFreqHz,
                f1 = config.fallbackEndFreqHz,
                sampleRate = primarySampleRate
            )
            if (fallbackValid) {
                selectedF0 = config.fallbackStartFreqHz
                selectedF1 = config.fallbackEndFreqHz
                bandName = "15-18 kHz (Audible Fallback)"
                Log.i(TAG, "Fallback band (15-18 kHz) verified successfully!")
            } else {
                Log.e(TAG, "Neither near-ultrasound nor fallback band produced reliable direct-path signal.")
                bandName = "UNSUPPORTED_AUDIO_HARDWARE"
            }
        }

        // Configure active reference chirp and filter
        activeReferenceChirp = chirpGenerator.generateChirp(selectedF0, selectedF1, config.chirpDurationMs, primarySampleRate)
        activeReferenceBytes = chirpGenerator.generatePcm16ByteArray(selectedF0, selectedF1, config.chirpDurationMs, primarySampleRate)
        bandpassFilter.configure(primarySampleRate, selectedF0 - 500f, selectedF1 + 500f)

        val capability = AudioHardwareCapability(
            supportedSampleRates = validRates,
            selectedSampleRate = primarySampleRate,
            isNearUltrasoundUsable = nearUltrasoundValid,
            isFallbackUsable = fallbackValid,
            activeStartFreqHz = selectedF0,
            activeEndFreqHz = selectedF1,
            measuredDirectPathDelayMs = calibratedDirectDelayMs ?: 0.25f,
            validationStatus = if (nearUltrasoundValid || fallbackValid) "VALIDATED ($bandName)" else "UNRELIABLE_DEVICE_ACOUSTICS"
        )

        _hardwareCapability.value = capability
        capability
    }

    /**
     * Tests a candidate frequency band by playing a test chirp and measuring direct-path response.
     */
    private suspend fun testFrequencyBand(f0: Float, f1: Float, sampleRate: Int): Boolean {
        try {
            val testRef = chirpGenerator.generateChirp(f0, f1, config.chirpDurationMs, sampleRate)
            val testBytes = chirpGenerator.generatePcm16ByteArray(f0, f1, config.chirpDurationMs, sampleRate)

            val record = AudioRecordCapture(config)
            val player = AudioTrackPlayer(config)

            if (!record.initialize(sampleRate) || !player.initialize(testBytes, sampleRate)) {
                record.release()
                player.release()
                return false
            }

            if (!record.start()) {
                record.release()
                player.release()
                return false
            }

            // Capture window: 60ms
            val captureSampleCount = (sampleRate * 0.060f).toInt()
            val captureBuffer = ShortArray(captureSampleCount)

            // Emit chirp
            player.playChirp()

            // Read audio
            var totalRead = 0
            val readBuf = ShortArray(1024)
            val startTime = System.currentTimeMillis()

            while (totalRead < captureSampleCount && (System.currentTimeMillis() - startTime) < 150) {
                val read = record.read(readBuf, 0, (captureSampleCount - totalRead).coerceAtMost(readBuf.size))
                if (read > 0) {
                    System.arraycopy(readBuf, 0, captureBuffer, totalRead, read)
                    totalRead += read
                } else {
                    delay(5)
                }
            }

            player.release()
            record.release()

            if (totalRead < testRef.size * 2) return false

            // Filter captured signal
            val filter = BandpassFilter(sampleRate, f0 - 500f, f1 + 500f)
            val filteredSignal = filter.filterPcm16(captureBuffer)

            // Cross-correlate
            val corr = crossCorrelator.correlate(testRef, filteredSignal)
            if (corr.isEmpty()) return false

            // Look for direct-path peak in first 5ms
            val maxDirectSamples = (sampleRate * 0.005f).toInt().coerceAtMost(corr.size)
            var peakVal = 0f
            var peakIdx = 0
            for (i in 0 until maxDirectSamples) {
                if (corr[i] > peakVal) {
                    peakVal = corr[i]
                    peakIdx = i
                }
            }

            // A valid direct-path peak should be prominent (> 0.25 correlation)
            val isValid = peakVal >= 0.25f
            if (isValid) {
                val measuredDelay = (peakIdx.toFloat() / sampleRate) * 1000f
                calibratedDirectDelayMs = measuredDelay
                Log.d(TAG, "Direct path peak detected at sample $peakIdx ($measuredDelay ms), correlation=$peakVal")
            }
            return isValid
        } catch (e: Exception) {
            Log.w(TAG, "Exception testing frequency band $f0-$f1 Hz: ${e.message}")
            return false
        }
    }

    /**
     * Starts continuous acoustic echo ranging loop in a background coroutine.
     */
    fun startRanging(scope: CoroutineScope) {
        if (rangingJob != null && rangingJob?.isActive == true) return

        rangingJob = scope.launch(Dispatchers.Default) {
            Log.i(TAG, "Starting acoustic ranging loop...")

            val cap = _hardwareCapability.value
            val sampleRate = cap.selectedSampleRate

            if (activeReferenceBytes.isEmpty()) {
                activeReferenceChirp = chirpGenerator.generateChirp(cap.activeStartFreqHz, cap.activeEndFreqHz, config.chirpDurationMs, sampleRate)
                activeReferenceBytes = chirpGenerator.generatePcm16ByteArray(cap.activeStartFreqHz, cap.activeEndFreqHz, config.chirpDurationMs, sampleRate)
                bandpassFilter.configure(sampleRate, cap.activeStartFreqHz - 500f, cap.activeEndFreqHz + 500f)
            }

            if (!audioRecordCapture.initialize(sampleRate) || !audioTrackPlayer.initialize(activeReferenceBytes, sampleRate)) {
                Log.e(TAG, "Failed initializing audio components for ranging")
                _latestResult.value = AcousticResult(
                    statusMessage = "AUDIO_INIT_FAILED",
                    formattedDistance = "AUDIO UNAVAILABLE"
                )
                return@launch
            }

            if (!audioRecordCapture.start()) {
                Log.e(TAG, "Failed starting AudioRecord")
                _latestResult.value = AcousticResult(
                    statusMessage = "RECORDING_FAILED",
                    formattedDistance = "MIC ERROR"
                )
                return@launch
            }

            // Capture window: 50ms (covers up to 8.5m round trip)
            val captureSamples = (sampleRate * 0.050f).toInt()
            val captureBuffer = ShortArray(captureSamples)
            val readBuffer = ShortArray(1024)

            try {
                while (isActive) {
                    val pingStartTime = System.currentTimeMillis()

                    // 1. Measure ambient noise floor before chirp
                    var noiseReadCount = 0
                    val noiseBuffer = ShortArray(512)
                    val nRead = audioRecordCapture.read(noiseBuffer, 0, noiseBuffer.size)
                    val ambientRms = if (nRead > 0) audioRecordCapture.computeRms(noiseBuffer, nRead) else 0f

                    // 2. Play Chirp
                    audioTrackPlayer.playChirp()

                    // 3. Capture audio window
                    var totalCaptured = 0
                    val windowTimeoutMs = 120L
                    val captureStart = System.currentTimeMillis()

                    while (totalCaptured < captureSamples && (System.currentTimeMillis() - captureStart) < windowTimeoutMs && isActive) {
                        val needed = captureSamples - totalCaptured
                        val toRead = needed.coerceAtMost(readBuffer.size)
                        val read = audioRecordCapture.read(readBuffer, 0, toRead)
                        if (read > 0) {
                            System.arraycopy(readBuffer, 0, captureBuffer, totalCaptured, read)
                            totalCaptured += read
                        } else {
                            delay(2)
                        }
                    }

                    if (totalCaptured >= activeReferenceChirp.size * 2) {
                        // 4. Bandpass filter captured PCM
                        val filteredSignal = bandpassFilter.filterPcm16(captureBuffer)

                        // 5. Cross-correlation
                        val correlation = crossCorrelator.correlate(activeReferenceChirp, filteredSignal)
                        _correlationCurve.value = correlation

                        // 6. Echo Detection
                        val echoResult = echoDetector.detectEcho(
                            correlation = correlation,
                            sampleRate = sampleRate,
                            calibratedDirectDelayMs = calibratedDirectDelayMs
                        )

                        // 7. Distance Estimation
                        val distResult = distanceEstimator.estimateDistance(
                            roundTripDelayMs = echoResult.roundTripDelayMs,
                            isEchoValid = echoResult.isValidEcho
                        )

                        // 8. Confidence Estimation
                        val confResult = confidenceEstimator.estimateConfidence(
                            peakCorrelation = echoResult.peakCorrelation,
                            snrDb = echoResult.snrDb,
                            psr = echoResult.psr,
                            distanceMeters = distResult.smoothedDistanceMeters,
                            ambientNoiseRms = ambientRms
                        )

                        val activeBandStr = "${cap.activeStartFreqHz.toInt() / 1000}-${cap.activeEndFreqHz.toInt() / 1000} kHz"

                        _latestResult.value = AcousticResult(
                            timestampMs = System.currentTimeMillis(),
                            distanceMeters = if (distResult.isReliable) distResult.smoothedDistanceMeters else -1f,
                            formattedDistance = distResult.formattedDisplay,
                            isReliable = distResult.isReliable && confResult.rating != ConfidenceRating.UNRELIABLE,
                            confidence = confResult,
                            directPathDelayMs = echoResult.directPathDelayMs,
                            echoDelayMs = echoResult.roundTripDelayMs,
                            peakCorrelation = echoResult.peakCorrelation,
                            snrDb = echoResult.snrDb,
                            psr = echoResult.psr,
                            ambientNoiseRms = ambientRms,
                            activeBand = activeBandStr,
                            statusMessage = echoResult.statusMessage
                        )
                    }

                    // Adaptive interval sleep
                    val elapsed = System.currentTimeMillis() - pingStartTime
                    val sleepMs = max(20L, pingIntervalMs - elapsed)
                    delay(sleepMs)
                }
            } finally {
                audioTrackPlayer.release()
                audioRecordCapture.release()
                Log.i(TAG, "Acoustic ranging loop stopped and resources released.")
            }
        }
    }

    /**
     * Updates adaptive ping interval based on device motion.
     * @param intervalMs Ping period in ms (e.g. 200ms when scanning, 1200ms when stationary).
     */
    fun setPingInterval(intervalMs: Long) {
        pingIntervalMs = intervalMs.coerceIn(150L, 2500L)
    }

    /**
     * Sets a calibrated direct path delay in milliseconds.
     */
    fun setCalibratedDirectDelay(delayMs: Float) {
        calibratedDirectDelayMs = delayMs
    }

    /**
     * Stops the continuous ranging loop.
     */
    fun stopRanging() {
        rangingJob?.cancel()
        rangingJob = null
        audioTrackPlayer.release()
        audioRecordCapture.release()
        _latestResult.value = AcousticResult(statusMessage = "STOPPED")
    }
}
