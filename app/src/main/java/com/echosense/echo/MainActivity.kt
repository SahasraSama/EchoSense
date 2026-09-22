package com.echosense.echo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.echosense.echo.ui.screens.CalibrationScreen
import com.echosense.echo.ui.screens.DiagnosticsScreen
import com.echosense.echo.ui.screens.MainScreen
import com.echosense.echo.ui.screens.PresentationDemoScreen
import com.echosense.echo.ui.screens.SensorTestScreen
import com.echosense.echo.ui.screens.SpatialScanScreen
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoTheme
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: EchoViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        if (cameraGranted) {
            viewModel.cameraEngine.startCamera(this)
        }
        if (audioGranted) {
            viewModel.startAcousticRanging()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        checkAndRequestPermissions()

        setContent {
            EchoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EchoBackground
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    when (currentScreen) {
                        AppScreen.MAIN -> MainScreen(viewModel = viewModel)
                        AppScreen.DIAGNOSTICS -> DiagnosticsScreen(viewModel = viewModel)
                        AppScreen.CALIBRATION -> CalibrationScreen(viewModel = viewModel)
                        AppScreen.SENSOR_TEST -> SensorTestScreen(viewModel = viewModel)
                        AppScreen.SPATIAL_SCAN -> SpatialScanScreen(viewModel = viewModel)
                        AppScreen.PRESENTATION_DEMO -> PresentationDemoScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.cameraEngine.startCamera(this)
            viewModel.startAcousticRanging()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.currentScreen.value != AppScreen.MAIN) {
            viewModel.navigateTo(AppScreen.MAIN)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
