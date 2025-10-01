package io.fastpix.data.fastpixlive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class ConfigurationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FastPixLive"
        private const val DEFAULT_STREAM_KEY = "23f1e1416f163515c299284e9121a551ke2bb641aeb829e4018f542d3a50927d5"

        private val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
        )

    }

    private lateinit var streamKeyField: TextInputEditText
    private lateinit var selectedButton: Button
    private val qualityButtons = mutableListOf<Button>()
    private var preset = LiveStreamActivity.Preset.sd_360p_30fps_1mbps

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        setContentView(R.layout.activity_configuration)
        initializeConfiguration()
    }

    private fun initializeConfiguration() {
        // Hide action bar for full screen experience
        supportActionBar?.hide()

        // Initialize views
        initializeViews()

        // Setup quality buttons
        setupQualityButtons()

        // Request permissions if needed
        if (!hasPermissions(this, PERMISSIONS)) {
            Log.i(TAG, "Requesting Permissions")
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
        }

        Log.i(TAG, "FastPix Studio Configuration Activity Started")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "Configuration changed - orientation: ${newConfig.orientation}")
    }

    private fun initializeViews() {
        streamKeyField = findViewById(R.id.streamKeyField)

        // Initialize quality buttons
        qualityButtons.addAll(listOf(
            findViewById(R.id.p1080p),
            findViewById(R.id.p720p),
            findViewById(R.id.p540p),
            findViewById(R.id.p360p)
        ))
    }

    private fun setupQualityButtons() {
        // Set default selection (360p instead of 720p)
        selectedButton = findViewById(R.id.p360p)

        // Set click listeners programmatically
        findViewById<Button>(R.id.p1080p).setOnClickListener { changeProfile(it) }
        findViewById<Button>(R.id.p720p).setOnClickListener { changeProfile(it) }
        findViewById<Button>(R.id.p540p).setOnClickListener { changeProfile(it) }
        findViewById<Button>(R.id.p360p).setOnClickListener { changeProfile(it) }

        // Apply initial selection to 360p
        updateButtonSelection(selectedButton)
    }

    private fun changeProfile(view: View) {
        val clickedButton = view as Button
        Log.i(TAG, "Quality changed to: ${clickedButton.text}")

        // Update visual selection FIRST
        updateButtonSelection(clickedButton)

        // Update preset based on selection
        preset = when (view.id) {
            R.id.p360p -> {
                showToast("Quality set to 360p")
                LiveStreamActivity.Preset.sd_360p_30fps_1mbps
            }
            R.id.p540p -> {
                showToast("Quality set to 540p")
                LiveStreamActivity.Preset.sd_540p_30fps_2mbps
            }
            R.id.p720p -> {
                showToast("Quality set to 720p (Recommended)")
                LiveStreamActivity.Preset.hd_720p_30fps_3mbps
            }
            R.id.p1080p -> {
                showToast("Quality set to 1080p")
                LiveStreamActivity.Preset.hd_1080p_30fps_5mbps
            }
            else -> LiveStreamActivity.Preset.hd_720p_30fps_3mbps
        }
    }

    private fun updateButtonSelection(clickedButton: Button) {
        Log.e(TAG, "=== UPDATING SELECTION ===")

        // Reset all buttons to transparent (no background, gray text)
        qualityButtons.forEach { button ->
            button.background = null
            button.backgroundTintList = null
            button.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }

        // Set clicked button to green
        val greenDrawable = ContextCompat.getDrawable(this, R.drawable.quality_button_selected)
        clickedButton.background = greenDrawable
        clickedButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        selectedButton = clickedButton

        Log.e(TAG, "Selected: ${clickedButton.text}")
    }

    fun startCamera(view: View) {
        Log.i(TAG, "Start Live Streaming button tapped")

        if (!hasPermissions(this, PERMISSIONS)) {
            Log.w(TAG, "Permissions not granted, requesting...")
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
            showToast("Please grant camera and microphone permissions")
            return
        }

        // Get stream key from input
        var streamKey = streamKeyField.text.toString().trim()
        Log.i(TAG, "Entered stream key: ${if (streamKey.isNotEmpty()) "***HIDDEN***" else "EMPTY"}")

        // Use default key if empty
        if (streamKey.isEmpty() && DEFAULT_STREAM_KEY.isNotEmpty()) {
            streamKey = DEFAULT_STREAM_KEY
            Log.i(TAG, "Using default stream key")
            showToast("Using default stream key for testing")
        }

        // Validate stream key
        if (streamKey.isEmpty()) {
            showToast("Please enter a stream key or enable default key")
            streamKeyField.requestFocus()
            return
        }

        // Show loading state
        val button = view as Button
        val originalText = button.text
        button.text = "Starting..."
        button.isEnabled = false

        try {
            // Create intent to launch LiveStreamActivity
            val intent = Intent(this, LiveStreamActivity::class.java).apply {
                putExtra(LiveStreamActivity.intentExtraStreamKey, streamKey)
                putExtra(LiveStreamActivity.intentExtraPreset, preset)
            }

            Log.i(TAG, "Launching LiveStreamActivity with ${selectedButton.text} quality")
            startActivity(intent)

            // Add smooth transition
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)

        } catch (e: RuntimeException) {
            Log.e(TAG, "Error starting LiveStreamActivity: ${e.message}")
            showToast("Error starting live stream: ${e.message}")
        } finally {
            // Restore button state
            button.text = originalText
            button.isEnabled = true
        }
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Permission not granted: $permission")
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.i(TAG, "All permissions granted")
                    showToast("✅ Permissions granted! Ready to stream")
                } else {
                    Log.w(TAG, "Some permissions denied")
                    showToast("❌ Camera and microphone permissions are required")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Re-enable start button when returning from LiveStreamActivity
        val startButton = findViewById<Button>(R.id.startCamera)
        startButton?.isEnabled = true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Add smooth transition when going back
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "FastPix Studio Configuration Activity Destroyed")
    }
}
