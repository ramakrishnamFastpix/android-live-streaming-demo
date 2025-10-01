package io.fastpix.data.fastpixlive

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION = 2500L
    }

    private var hasNavigated = false
    private var handler: Handler? = null
    private var navigationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        setContentView(R.layout.activity_splash)

        // Make it fullscreen
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Start animations
        startSplashAnimations()

        // Setup navigation
        setupNavigation()
    }

    private fun setupNavigation() {
        handler = Handler(Looper.getMainLooper())
        navigationRunnable = Runnable {
            if (!hasNavigated && !isFinishing) {
                startMainActivityDirectly()
            }
        }

        handler?.postDelayed(navigationRunnable!!, SPLASH_DURATION)
    }

    private fun startSplashAnimations() {
        val logoImage = findViewById<ImageView>(R.id.splashLogo)

        val logoAnimationSet = AnimationSet(true).apply {
            val scaleAnimation = ScaleAnimation(
                0.3f, 1.0f, 0.3f, 1.0f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 1500
                interpolator = AccelerateInterpolator()
            }

            val fadeInAnimation = AlphaAnimation(0.0f, 1.0f).apply {
                duration = 1500
            }

            addAnimation(scaleAnimation)
            addAnimation(fadeInAnimation)
        }

        logoImage.startAnimation(logoAnimationSet)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                addBounceEffect(logoImage)
            }
        }, 1600L)
    }

    private fun addBounceEffect(logoImage: ImageView) {
        val bounceAnimation = ScaleAnimation(
            1.0f, 1.1f, 1.0f, 1.1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            repeatCount = 1
            repeatMode = ScaleAnimation.REVERSE
        }

        logoImage.startAnimation(bounceAnimation)
    }

    private fun startMainActivityDirectly() {
        if (hasNavigated || isFinishing) return

        hasNavigated = true

        val intent = Intent(this, ConfigurationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationRunnable?.let { handler?.removeCallbacks(it) }
        handler = null
        navigationRunnable = null
    }

    override fun onBackPressed() {
        // Do nothing - force user to wait for splash
    }
}
