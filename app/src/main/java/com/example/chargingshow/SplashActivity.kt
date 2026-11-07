package com.example.chargingshow

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Açılış animasyonu: logo ~2 saniye animasyonla gelir, sonra uygulama açılır. */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val openMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ring = findViewById<ChargeRingView>(R.id.ring)
        val title = findViewById<TextView>(R.id.title)
        val tagline = findViewById<TextView>(R.id.tagline)

        ring.progress = 0f
        ring.boltScale = 0f
        ring.alpha = 0f
        title.alpha = 0f
        tagline.alpha = 0f

        // 1) logo belirir
        ring.animate().alpha(1f).setDuration(300).start()

        // 2) şarj halkası dolar
        ValueAnimator.ofFloat(0f, 0.75f).apply {
            duration = 1100
            interpolator = DecelerateInterpolator()
            addUpdateListener { ring.progress = it.animatedValue as Float }
            start()
        }

        // 3) yıldırım "pop" ile gelir
        ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 350
            duration = 650
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { ring.boltScale = it.animatedValue as Float }
            start()
        }

        // 4) isim ve slogan
        title.animate().alpha(1f).setStartDelay(800).setDuration(450).start()
        tagline.animate().alpha(1f).setStartDelay(1050).setDuration(450).start()

        handler.postDelayed(openMain, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openMain)
        super.onDestroy()
    }
}
