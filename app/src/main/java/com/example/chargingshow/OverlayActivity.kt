package com.example.chargingshow

import android.animation.ValueAnimator
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Şarj takılınca ekranda çıkan animasyon. Dokununca ya da ~5 sn sonra kapanır. */
class OverlayActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable { finish() }
    private var pulse: ValueAnimator? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_overlay)

        val ring = findViewById<ChargeRingView>(R.id.ring)
        val tvPercent = findViewById<TextView>(R.id.tvPercent)

        val level = currentBatteryPercent()
        tvPercent.text = "%$level"

        // halka gerçek pil seviyesine kadar dolar
        ring.progress = 0f
        ValueAnimator.ofFloat(0f, level / 100f).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
            addUpdateListener { ring.progress = it.animatedValue as Float }
            start()
        }

        // yıldırım nabız gibi atar
        pulse = ValueAnimator.ofFloat(0.92f, 1.08f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { ring.boltScale = it.animatedValue as Float }
            start()
        }

        findViewById<android.view.View>(R.id.root).setOnClickListener { finish() }
        handler.postDelayed(closeRunnable, 5000)
    }

    private fun currentBatteryPercent(): Int {
        val intent: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level < 0 || scale <= 0) 0 else level * 100 / scale
    }

    override fun onDestroy() {
        handler.removeCallbacks(closeRunnable)
        pulse?.cancel()
        super.onDestroy()
    }
}
