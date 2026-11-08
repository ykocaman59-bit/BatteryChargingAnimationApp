package com.example.chargingshow

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/** Osmanlı temalı ana ekran (launcher). */
class LauncherActivity : ComponentActivity() {

    // Compose tarafına "bir şey değişti" sinyali veren sayaçlar
    private val refreshKey = mutableIntStateOf(0) // uygulama listesi yenilensin
    private val resetKey = mutableIntStateOf(0)   // arama kutusu, menüler, klasör penceresi sıfırlansın
    private val homeKey = mutableIntStateOf(0)    // ilk sayfaya dön

    private val coverRef = mutableStateOf("")
    private val applyLock = mutableStateOf(true)

    private val prefs by lazy { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Thread {
                    val ref = Covers.saveCustom(this, uri)
                    runOnUiThread {
                        if (ref != null) setCover(ref)
                        else Toast.makeText(this, "Resim açılamadı", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arka plan her zaman koyu olduğu için durum çubuğu simgeleri (saat, şarj %, sinyal, wifi)
        // telefonun açık/koyu temasından bağımsız olarak HER ZAMAN açık (beyaz) renkte çizilsin.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        coverRef.value = prefs.getString(Prefs.COVER, null)
            ?: Covers.listAssets(this).firstOrNull()
            ?: ""
        applyLock.value = prefs.getBoolean(Prefs.APPLY_LOCK, true)

        // Geri tuşu: arama kutusunu temizler, menüleri kapatır, ilk sayfaya döner
        onBackPressedDispatcher.addCallback(this) {
            resetKey.intValue++
            homeKey.intValue++
        }

        // Servis açık bırakıldıysa (şarj animasyonu + düşük şarj uyarısı) çalıştır
        if (prefs.getBoolean(Prefs.ENABLED, false)) {
            ContextCompat.startForegroundService(this, Intent(this, ChargingService::class.java))
        }

        setContent {
            OsmanliLauncherTheme {
                LauncherRoot(
                    refreshKey = refreshKey.intValue,
                    resetKey = resetKey.intValue,
                    homeKey = homeKey.intValue,
                    coverRef = coverRef.value,
                    applyLock = applyLock.value,
                    onSetCover = { setCover(it) },
                    onPickGallery = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onApplyLockChanged = { on ->
                        applyLock.value = on
                        prefs.edit().putBoolean(Prefs.APPLY_LOCK, on).apply()
                        if (on) applyLockNow()
                    },
                    onApplyLockNow = { applyLockNow() }
                )
            }
        }
    }

    private fun setCover(ref: String) {
        prefs.edit().putString(Prefs.COVER, ref).apply()
        coverRef.value = ref
        if (applyLock.value) applyLockNow()
    }

    private fun applyLockNow() {
        val ref = coverRef.value
        if (ref.isEmpty()) return
        Thread {
            val ok = Covers.applyToLock(this, ref)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "Kilit ekranı duvar kağıdı ayarlandı" else "Kilit ekranına uygulanamadı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ana ekran tuşuna basıldı: arama temizlensin, ilk sayfaya dön
        resetKey.intValue++
        homeKey.intValue++
    }

    override fun onPause() {
        super.onPause()
        // Geri / ana ekran / son uygulamalar (kare) tuşu: yazılan arama silinsin
        resetKey.intValue++
    }

    override fun onResume() {
        super.onResume()
        refreshKey.intValue++
    }
}
