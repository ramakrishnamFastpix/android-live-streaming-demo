package io.fastpix.data.fastpixlive

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.util.FpsListener

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
        hd_1080p_30fps_5mbps(5000 * 1024, 1920, 1080, 30),
        hd_720p_30fps_3mbps(3000 * 1024, 1280, 720, 30),
        sd_540p_30fps_2mbps(2000 * 1024, 960, 540, 30),
        sd_360p_30fps_1mbps(1000 * 1024, 640, 360, 30)
    }

    // UI Components
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
        surfaceView.setOnTouchListener(this) // ✅ Add touch listener

        goLiveButton = findViewById(R.id.goLiveButton)
        closeButton = findViewById(R.id.closeButton)
        backCameraButton = findViewById(R.id.backCameraButton)
        frontCameraButton = findViewById(R.id.frontCameraButton)

        bitrateLabel = findViewById(R.id.bitrateLabel)
        fpsLabel = findViewById(R.id.fpslabel)
        connectionStatus = findViewById(R.id.connectionStatus)

        // ✅ MUX-STYLE - Initial state
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
        }
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        backCameraButton.setOnClickListener {
            changeCameraClicked()
        }

        frontCameraButton.setOnClickListener {
            changeCameraClicked()
        }
    }

    private fun changeCameraClicked() {
        rtmpCamera.switchCamera()

        isBackCamera = !isBackCamera
        updateButtonColorsInstant()
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

        if (liveDesired) { goLiveButton.text = "Stopping..."
            Thread {
                rtmpCamera.stopStream()
                runOnUiThread {
                    goLiveButton.text = GO_LIVE_TEXT
                    connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
                    bitrateLabel.text = ZERO_KBPS
                    fpsLabel.text = ZERO_FPS
                }
            }.start()
            liveDesired = false

            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        } else {

            val rotation = windowManager.defaultDisplay.rotation
            when (rotation) {
                Surface.ROTATION_90 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            preset?.let { p ->
                rtmpCamera.prepareVideo(
                    p.width,
                    p.height,
                    p.frameRate,
                    p.bitrate,
                    2, // Fixed 2s keyframe interval
                    CameraHelper.getCameraOrientation(this)
                )
                rtmpCamera.prepareAudio(
                    128 * 1024, // 128kbps
                    48000, // 48k
                    true // Stereo
                )

                val streamUrl = "$rtmpEndpoint/${streamKey ?: ""}"
                rtmpCamera.startStream(streamUrl)
                liveDesired = true
                goLiveButton.text = "Connecting... (Cancel)"
            }
        }
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

        rtmpCamera.startPreview(1920, 1080)

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            goLiveButton.text = "Stop Streaming!"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_green)
            showToast("RTMP Connection Successful!")
        }
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread {
            goLiveButton.text = "Connecting... (Cancel)"
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            goLiveButton.text = "Reconnecting... (Cancel)"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        runOnUiThread {
            bitrateLabel.text = "${bitrate / 1024} kbps"
        }
    }

    override fun onDisconnect() {
        runOnUiThread {
            bitrateLabel.text = ZERO_KBPS
            fpsLabel.text = ZERO_FPS
            goLiveButton.text =GO_LIVE_TEXT
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthError() {
      runOnUiThread {
            goLiveButton.text = GO_LIVE_TEXT
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthSuccess() {
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean=false

    override fun onDestroy() {
        super.onDestroy()
        if (liveDesired) {
            try {
                rtmpCamera.stopStream()
            } catch (e: RuntimeException) {

            }
        }

    }

    override fun onBackPressed() {
        super.onBackPressed()

        if (liveDesired) {
            try {
                rtmpCamera.stopStream()
                liveDesired = false
            } catch (e: RuntimeException) {
            }
        }
        finish()
    }
}
