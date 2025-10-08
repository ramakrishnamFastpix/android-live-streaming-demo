package io.fastpix.data.fastpixlive

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.util.FpsListener
import java.io.IOException

class LiveStreamActivity : AppCompatActivity(), SurfaceHolder.Callback, ConnectChecker, View.OnTouchListener {

    companion object {
        private const val TAG = "FastPixLive"
        private const val rtmpEndpoint = "rtmps://live.fastpix.app:443/live"

        const val intentExtraStreamKey = "STREAMKEY"
        const val intentExtraPreset = "PRESET"
        private const val ZERO_KBPS = "0 kbps"
        private const val ZERO_FPS = "0 fps"
        private const val GO_LIVE_TEXT = "Go Live!"
    }

    enum class Preset(val bitrate: Int, val width: Int, val height: Int, val frameRate: Int) {
        hd_1080p_30fps_5mbps(3000000, 1920, 1080, 30),
        hd_720p_30fps_3mbps(3000000, 1280, 720, 30),
        sd_540p_30fps_2mbps(3000000, 640, 480, 30),
        sd_360p_30fps_1mbps(3000000, 640, 360, 30)
    }

    private lateinit var goLiveButton: Button
    private lateinit var bitrateLabel: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var surfaceView: SurfaceView
    private lateinit var connectionStatus: View
    private lateinit var backCameraButton: TextView
    private lateinit var frontCameraButton: TextView
    private lateinit var closeButton: ImageView

    private lateinit var rtmpCamera: RtmpCamera1
    private var liveDesired = false
    private var streamKey: String? = null
    private var preset: Preset? = null
    private var isBackCamera = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_livestream)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews()
        setupCamera()
        setupClickListeners()
        handleIntent()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initializeViews() {
        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener(this)

        goLiveButton = findViewById(R.id.goLiveButton)
        closeButton = findViewById(R.id.closeButton)
        backCameraButton = findViewById(R.id.backCameraButton)
        frontCameraButton = findViewById(R.id.frontCameraButton)

        bitrateLabel = findViewById(R.id.bitrateLabel)
        fpsLabel = findViewById(R.id.fpslabel)
        connectionStatus = findViewById(R.id.connectionStatus)

        connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        bitrateLabel.text = ZERO_KBPS
        fpsLabel.text = ZERO_FPS

        updateButtonColorsInstant()
    }

    private fun setupCamera() {
        try {
            rtmpCamera = RtmpCamera1(surfaceView, this)

            val callback = object : FpsListener.Callback {
                override fun onFps(fps: Int) {
                    runOnUiThread {
                        fpsLabel.text = "$fps fps"
                    }
                }
            }
            rtmpCamera.setFpsListener(callback)

        } catch (e: RuntimeException) {
            Log.e(TAG, "Camera setup failed: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        goLiveButton.setOnClickListener {
            goLiveClicked()
        }

        backCameraButton.setOnClickListener {
            if (!isBackCamera) {
                isBackCamera = true
                updateButtonColorsInstant()

                Thread {
                    try {
                        rtmpCamera.switchCamera()
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera switch failed: ${e.message}")
                    }
                }.start()
            }
        }

        frontCameraButton.setOnClickListener {
            if (isBackCamera) {
                isBackCamera = false
                updateButtonColorsInstant()

                Thread {
                    try {
                        rtmpCamera.switchCamera()
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera switch failed: ${e.message}")
                    }
                }.start()
            }
        }
    }

    private fun updateButtonColorsInstant() {
        if (isBackCamera) {
            backCameraButton.setBackgroundResource(R.drawable.quality_button_selected)
            backCameraButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            frontCameraButton.setBackgroundResource(R.drawable.quality_button_unselected)
            frontCameraButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            frontCameraButton.setBackgroundResource(R.drawable.quality_button_selected)
            frontCameraButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            backCameraButton.setBackgroundResource(R.drawable.quality_button_unselected)
            backCameraButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun handleIntent() {
        intent.extras?.let { extras ->
            streamKey = extras.getString(intentExtraStreamKey)
            preset = extras.getSerializable(intentExtraPreset) as? Preset
        }
    }

    fun goLiveClicked() {
        if (liveDesired) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    private fun startStreaming() {
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_90 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_180 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            Surface.ROTATION_270 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        preset?.let { p ->
            try {
                rtmpCamera.prepareVideo(
                    p.width,
                    p.height,
                    p.frameRate,
                    p.bitrate,
                    4,
                    CameraHelper.getCameraOrientation(this)
                )

                rtmpCamera.prepareAudio(
                    128 * 1024,
                    48000,
                    true
                )

                val streamUrl = "$rtmpEndpoint/${streamKey ?: ""}"
                rtmpCamera.startStream(streamUrl)
                liveDesired = true
                goLiveButton.text = "Connecting... (Cancel)"

                Log.i(TAG, "Stream started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start stream: ${e.message}")
                showToast("Failed to start streaming")
            }
        }
    }

    private var isStoppingStream = false

    private fun stopStreaming() {
        if (isStoppingStream) return

        isStoppingStream = true
        goLiveButton.text = "Stopping..."
        liveDesired = false

        // Use a separate thread to avoid blocking UI
        Thread {
            try {
                // Force immediate disconnection without graceful SSL shutdown
                if (rtmpCamera.isStreaming) {
                    // Don't wait for SSL cleanup - just force stop
                    rtmpCamera.stopStream()
                }
            } catch (e: IOException) {
                // Specifically catch and ignore SSL/TLS cleanup errors
                if (e.message?.contains("Channel is closed for write") == true) {
                    Log.w(TAG, "SSL cleanup race condition (expected): ${e.message}")
                } else {
                    Log.e(TAG, "Stream stop error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream stop error: ${e.message}")
            } finally {
                isStoppingStream = false
                runOnUiThread {
                    goLiveButton.text = GO_LIVE_TEXT
                    connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
                    bitrateLabel.text = ZERO_KBPS
                    fpsLabel.text = ZERO_FPS
                }
            }
        }.start()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }


    private fun showToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        rtmpCamera.stopPreview()

        val rotation = windowManager.defaultDisplay.rotation
        val layoutParams = surfaceView.layoutParams as ConstraintLayout.LayoutParams

        when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                layoutParams.dimensionRatio = "w,16:9"
            }
            else -> {
                layoutParams.dimensionRatio = "h,9:16"
            }
        }
        surfaceView.layoutParams = layoutParams

        rtmpCamera.startPreview(1280, 720)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            goLiveButton.text = "Stop Streaming!"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_green)
            showToast("RTMP Connection Successful!")
        }
        Log.i(TAG, "RTMP connection successful")
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread {
            goLiveButton.text = "Connecting... (Cancel)"
        }
        Log.i(TAG, "RTMP connection started")
    }

    override fun onConnectionFailed(reason: String) {
        Log.w(TAG, "RTMP connection failed: $reason")
        runOnUiThread {
            goLiveButton.text = "Connection Failed"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
            showToast("Connection failed: $reason")
        }

        if (liveDesired) {
            // Clean stop before retry
            Thread {
                try {
                    if (rtmpCamera.isStreaming) {
                        rtmpCamera.stopStream()
                    }
                    Thread.sleep(1000)
                } catch (e: IOException) {
                    if (e.message?.contains("Channel is closed for write") == true) {
                        Log.w(TAG, "SSL cleanup during retry (expected)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during retry cleanup: ${e.message}")
                }

                // Retry after cleanup
                Handler(Looper.getMainLooper()).postDelayed({
                    if (liveDesired && !isStoppingStream) {
                        runOnUiThread {
                            goLiveButton.text = "Reconnecting..."
                        }
                        startStreaming()
                    }
                }, 3000)
            }.start()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        runOnUiThread {
            bitrateLabel.text = "${bitrate / 1024} kbps"
        }
    }

    override fun onDisconnect() {
        Log.i(TAG, "RTMP disconnected")
        runOnUiThread {
            bitrateLabel.text = ZERO_KBPS
            fpsLabel.text = ZERO_FPS
            goLiveButton.text = GO_LIVE_TEXT
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
            showToast("Disconnected")
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthError() {
        Log.w(TAG, "RTMP auth error")
        runOnUiThread {
            goLiveButton.text = GO_LIVE_TEXT
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
            showToast("Authentication Error")
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "RTMP auth success")
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean = false

    override fun onDestroy() {
        super.onDestroy()
        liveDesired = false

        if (::rtmpCamera.isInitialized) {
            try {
                if (rtmpCamera.isStreaming) {
                    rtmpCamera.stopStream()
                }
            } catch (e: IOException) {
                if (e.message?.contains("Channel is closed for write") == true) {
                    Log.w(TAG, "SSL cleanup in onDestroy (expected)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup error in onDestroy (non-fatal): ${e.message}")
            }
        }
    }

    override fun onBackPressed() {
        if (liveDesired && !isStoppingStream) {
            // Stop streaming first, then navigate back
            stopStreaming()

            // Wait for cleanup before finishing
            Handler(Looper.getMainLooper()).postDelayed({
                super.onBackPressed()
                finish()
            }, 1500)
        } else {
            super.onBackPressed()
            finish()
        }
    }



}
