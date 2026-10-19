package com.example.chargingshow

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView

class OverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay)

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        lottieView.setAnimation(R.raw.anim_charging_1)
        lottieView.playAnimation()

        // 5 saniye sonra animasyonu otomatik kapat
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 5000)

        findViewById<android.view.View>(R.id.overlayRoot).setOnClickListener {
            finish()
        }
    }
}
