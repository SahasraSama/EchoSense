# ECHO: Adaptive Acoustic + Vision Obstacle Awareness

> *"When vision fades, sound takes over."*

ECHO is a production-quality Android application built for an **iQOO 15 smartphone** for a 30-hour hackathon. It combines camera-based visual perception in adequate lighting with **real acoustic echo-based ranging** in low-light and darkness, guided by ambient light and phone motion sensors, with accessible haptic and voice feedback.

---

## 1. Core Physics & Real Acoustic Ranging Pipeline

ECHO implements **real acoustic signal processing** using the phone's stereo speakers and microphone array. It does **not** simulate distances, fake sonar visualizations, or rely on cloud APIs.

```
[Chirp Generator] -> [AudioTrack (MODE_STATIC)] -> [Phone Speaker]
                                                          |
                                                  (Acoustic Wave)
                                                          |
                                                  [Obstacle Surface]
                                                          |
                                                  (Reflected Echo)
                                                          v
[AudioRecord (UNPROCESSED)] <- [Phone Microphone] <-------+
         |
         v
[4th-Order Butterworth Bandpass Filter (17.5–21.5 kHz)]
         |
         v
[Normalized Cross-Correlator (Rxy)]
         |
         v
[Echo Detector & Direct-Path Blanking]
         |
         +--> Measure Direct-Path Delay (speaker-to-mic bleed: ~0.25 ms)
         +--> Identify Reflection Peak (round-trip time: dt = 2d / c)
         +--> Validate SNR (> 6 dB) & Peak-to-Sidelobe Ratio (> 1.6)
         |
         v
[Distance Estimator] (c(T) = 331.3 * sqrt(1 + T/273.15) m/s, EMA smoothing)
         |
         v
[Confidence Estimator] (HIGH, MEDIUM, LOW, or UNRELIABLE)
```

If no reliable reflection peak is detected above the noise floor, ECHO displays:
```
NO RELIABLE ECHO
```
instead of inventing an artificial distance.

---

## 2. Hardware Validation on iQOO 15

ECHO automatically detects and verifies the target device's physical capabilities at startup:

1. **Audio Capabilities**:
   - Probes sample rates: `48000 Hz`, `44100 Hz`, `96000 Hz`.
   - Tests near-ultrasound candidate band: **18 kHz – 21 kHz**.
   - If the microphone or speaker hardware rolls off severely above 17 kHz, automatically falls back to **15 kHz – 18 kHz**.
   - Captures uncompressed PCM 16-bit audio via `AudioSource.UNPROCESSED` to bypass Android OS noise cancellation filters.
2. **Direct-Path Delay Calibration**:
   - Automatically measures the physical speaker-to-mic chassis transmission delay (~0.25 ms) instead of hardcoding a fixed value.
   - Blanks the direct-path region to prevent self-detection.
3. **Sensor Suite**:
   - Ambient Light Sensor (`Sensor.TYPE_LIGHT`)
   - Accelerometer (`Sensor.TYPE_ACCELEROMETER`)
   - Gyroscope (`Sensor.TYPE_GYROSCOPE`)
   - CameraX on-device ImageAnalysis
   - Hardware Haptics (`VibrationEffect` with amplitude control)

---

## 3. Sensing Modes & State Transitions

| Mode | Trigger Condition | Primary Sensor | Feedback |
| :--- | :--- | :--- | :--- |
| **VISION** | Ambient light > 45 lux | CameraX on-device contour analysis | Visual bounding & semantic labels |
| **HYBRID** | Ambient light 10–45 lux | Camera + Acoustic Ranging | Fused distance anchored on acoustic |
| **ECHO** | Ambient light < 5 lux or darkness | Real Acoustic Echo Ranging | Large distance readout + Haptic pulses |

### Motion-Aware Adaptive Pings
- **Stationary**: 1 ping every 1200 ms (saves battery).
- **Scanning / Moving**: 1 ping every 200–350 ms (high responsiveness).
- **Pocket / Face-Down**: Active ranging suspended automatically.

---

## 4. Haptic Vocabulary

| Obstacle Proximity | State | Haptic Pattern | Spoken Guidance |
| :--- | :--- | :--- | :--- |
| > 2.0 m | **SAFE** | No vibration | *"Path clear"* |
| 1.0 m – 2.0 m | **CAUTION** | Slow gentle pulse (80ms on, 900ms off) | *"Caution. Obstacle detected."* |
| 0.5 m – 1.0 m | **CLOSE** | Medium rhythmic pulse (100ms on, 400ms off) | *"Obstacle ahead. 0.8 metres."* |
| 0.3 m – 0.5 m | **VERY CLOSE** | Urgent rapid pulse (80ms on, 150ms off) | *"Very close. 0.4 metres."* |
| < 0.3 m | **CRITICAL** | Strong rapid buzz (120ms on, 60ms off) | *"Critical obstacle! Stop."* |

---

## 5. Screen Directory

1. **Main Screen**: Large consumer-grade distance display readable in under 2 seconds, mode indicator, sensor status chips, and real-time DSP waveform.
2. **Diagnostics Screen**: Live technical telemetry (sample rate, frequency, SNR, delay, calculated distance, PSR, confidence, lux, sensor availability) and `EXPORT DIAGNOSTICS` button.
3. **Calibration Screen**: Interactive 1-meter wall calibration wizard measuring noise floor, speaker response, direct-path latency, and reflection baseline.
4. **Sensor Test Screen**: Individual PASS / WARNING / UNAVAILABLE diagnostics for Mic, Speaker, Echo Ranging, Light, Gyro, Accel, Camera, and Haptics.
5. **Spatial Scan Screen**: Directional Acoustic Spatial Scan dividing measurements into **LEFT**, **CENTER**, and **RIGHT** sectors using gyroscope orientation.
6. **Presentation Demo Screen**: Structured demo walkthrough for hackathon judges illustrating the bright $\to$ dim $\to$ dark acoustic takeover flow.

---

## 6. Architecture & Project Layout

```
com.echosense.echo
├── core
│   ├── audio
│   │   ├── AudioConfig.kt
│   │   ├── ChirpGenerator.kt
│   │   ├── AudioTrackPlayer.kt
│   │   ├── AudioRecordCapture.kt
│   │   ├── BandpassFilter.kt
│   │   ├── CrossCorrelator.kt
│   │   ├── EchoDetector.kt
│   │   ├── DistanceEstimator.kt
│   │   ├── ConfidenceEstimator.kt
│   │   └── AcousticRangingEngine.kt
│   ├── sensors
│   │   ├── HardwareCapabilityDetector.kt
│   │   ├── LightSensorManager.kt
│   │   └── MotionManager.kt
│   ├── vision
│   │   ├── CameraEngine.kt
│   │   └── VisionEngine.kt
│   ├── fusion
│   │   ├── SensingMode.kt
│   │   ├── ObstacleState.kt
│   │   ├── DistanceStateMachine.kt
│   │   ├── SensorFusionEngine.kt
│   │   └── SpatialScanManager.kt
│   ├── feedback
│   │   ├── HapticEngine.kt
│   │   └── VoiceEngine.kt
│   ├── calibration
│   │   └── CalibrationManager.kt
│   └── diagnostics
│       └── DiagnosticsManager.kt
└── ui
    ├── theme (Color, Theme, Type)
    ├── components (DistanceDisplay, ModeIndicator, SensorStatusRow, AcousticWaveformVisualizer, ModeTransitionBanner)
    ├── screens (MainScreen, DiagnosticsScreen, CalibrationScreen, SensorTestScreen, SpatialScanScreen, PresentationDemoScreen)
    └── viewmodel (EchoViewModel)
```
