package com.devin.gamebot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devin.gamebot.service.CaptureService
import com.devin.gamebot.ui.MainScreen

/**
 * Main entry point for AI Game Bot.
 *
 * Phase 0 responsibilities:
 *   - Render the main test screen (Compose).
 *   - Request MediaProjection permission and launch the capture foreground service.
 *   - Surface a button to start an Accessibility Service settings deep link.
 */
class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptureService.start(this, result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)

        setContent {
            MainScreen(
                onRequestCapture = { requestProjection() },
                onStopCapture = { CaptureService.stop(this) },
                onOpenAccessibility = { openAccessibilitySettings() }
            )
        }
    }

    private fun requestProjection() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
