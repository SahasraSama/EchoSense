package com.echosense.echo.core.vision

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, image analysis pipeline, and visual obstacle flow.
 */
class CameraEngine(
    private val context: Context,
    private val visionEngine: VisionEngine = VisionEngine()
) {
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _visualObstacle = MutableStateFlow(
        DetectedVisualObstacle(
            label = "Initializing",
            confidence = 0f,
            estimatedDistanceMeters = null,
            boundingBox = null,
            isReliable = false
        )
    )
    val visualObstacle: StateFlow<DetectedVisualObstacle> = _visualObstacle.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    var ambientLuxProvider: () -> Float = { 100f }

    /**
     * Binds CameraX to the provided lifecycle owner.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    previewSurfaceProvider?.let { provider -> it.setSurfaceProvider(provider) }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    val currentLux = ambientLuxProvider()
                    val obstacle = visionEngine.analyzeFrame(imageProxy, currentLux)
                    _visualObstacle.value = obstacle
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                if (previewSurfaceProvider != null) {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } else {
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                }

                _isCameraActive.value = true
            } catch (e: Exception) {
                _isCameraActive.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Stops the camera and frees executor threads.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor?.shutdown()
        } catch (e: Exception) {
            // Ignore
        } finally {
            cameraProvider = null
            cameraExecutor = null
            _isCameraActive.value = false
        }
    }
}
