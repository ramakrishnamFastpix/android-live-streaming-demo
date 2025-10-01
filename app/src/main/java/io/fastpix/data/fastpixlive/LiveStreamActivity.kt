package io.fastpix.data.fastpixlive

import android.content.pm.ActivityInfo
import android.hardware.Camera
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
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.util.FpsListener

class LiveStreamActivity : AppCompatActivity(), SurfaceHolder.Callback, ConnectChecker, View.OnTouchListener {

    companion object {
        private const val TAG = "FastPixLive"
        private const val rtmpEndpoint = "rtmps://live.fastpix.app:443/live"

        const val intentExtraStreamKey = "STREAMKEY"
        const val intentExtraPreset = "PRESET"
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

        Log.i(TAG, "📱 FastPix LiveStream started")
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
        bitrateLabel.text = "0 kbps"
        fpsLabel.text = "0 fps"

        updateButtonColorsInstant()
    }

    private fun setupCamera() {
        try {
            rtmpCamera = RtmpCamera1(surfaceView, this)


            val callback = object : FpsListener.Callback {
                override fun onFps(fps: Int) {
                    Log.i(TAG, "FPS: $fps")
                    runOnUiThread {
                        fpsLabel.text = "$fps fps"
                    }
                }
            }
            rtmpCamera.setFpsListener(callback)

            Log.i(TAG, "✅ RTMP Camera initialized (Mux-style)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize camera: ${e.message}")
            showMuxToast("Camera initialization failed")
        }
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            Log.i(TAG, "❌ Close button tapped")
            finish()
        }

        backCameraButton.setOnClickListener {
            Log.i(TAG, "📷 Back camera button tapped")
            changeCameraClicked()
        }

        frontCameraButton.setOnClickListener {
            Log.i(TAG, "🤳 Front camera button tapped")
            changeCameraClicked()
        }
    }

    private fun changeCameraClicked() {
        Log.i(TAG, "Change Camera Button tapped")
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
            Log.i(TAG, "🔑 Stream Key: ${streamKey?.take(8)}...")
            Log.i(TAG, "⚙️ Preset: ${preset?.width}x${preset?.height}@${preset?.frameRate}fps")
        }
    }




    fun goLiveClicked(view: View) {
        Log.i(TAG, "Go Live Button tapped")

        if (liveDesired) { goLiveButton.text = "Stopping..."
            Thread {
                rtmpCamera.stopStream()
                runOnUiThread {
                    goLiveButton.text = "Go Live!"
                    connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
                    bitrateLabel.text = "0 kbps"
                    fpsLabel.text = "0 fps"
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

                val streamUrl = "$rtmpEndpoint/$streamKey"
                rtmpCamera.startStream(streamUrl)
                liveDesired = true
                goLiveButton.text = "Connecting... (Cancel)"
            }
        }
    }

    private fun showMuxToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "🖥️ Surface created")
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

        Log.i(TAG, "🖥️ Surface changed: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "🖥️ Surface destroyed")
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            goLiveButton.text = "Stop Streaming!"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_green)
            showMuxToast("RTMP Connection Successful!")
        }
        Log.i(TAG, "RTMP Connection Success")
    }

    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "RTMP Connection Started: $url")
        runOnUiThread {
            goLiveButton.text = "Connecting... (Cancel)"
        }
    }

    override fun onConnectionFailed(reason: String) {
        Log.w(TAG, "RTMP Connection Failure: $reason")
        runOnUiThread {
            goLiveButton.text = "Reconnecting... (Cancel)"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        Log.d(TAG, "RTMP Bitrate Changed: ${bitrate / 1024}")
        runOnUiThread {
            bitrateLabel.text = "${bitrate / 1024} kbps"
        }
    }

    override fun onDisconnect() {
        Log.i(TAG, "RTMP Disconnect")
        runOnUiThread {
            bitrateLabel.text = "0 kbps"
            fpsLabel.text = "0 fps"
            goLiveButton.text = "Go Live!"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthError() {
        Log.w(TAG, "RTMP Auth Error")
        runOnUiThread {
            goLiveButton.text = "Go Live!"
            connectionStatus.setBackgroundResource(R.drawable.connection_dot_red)
        }
        liveDesired = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "RTMP Auth Success")
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (liveDesired) {
            try {
                rtmpCamera.stopStream()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in onDestroy: ${e.message}")
            }
        }
        Log.i(TAG, "🏁 Activity destroyed")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Log.i(TAG, "⬅️ Back button pressed")

        if (liveDesired) {
            try {
                rtmpCamera.stopStream()
                liveDesired = false
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping stream: ${e.message}")
            }
        }
        finish()
    }
}
