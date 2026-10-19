package com.example.chargingshow

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var etConnectedText: EditText
    private lateinit var etDisconnectedText: EditText
    private lateinit var btnSave: Button
    private lateinit var btnTestTts: Button
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etConnectedText = findViewById(R.id.etConnectedText)
        etDisconnectedText = findViewById(R.id.etDisconnectedText)
        btnSave = findViewById(R.id.btnSave)
        btnTestTts = findViewById(R.id.btnTestTts)

        tts = TextToSpeech(this, this)

        val prefs = getSharedPreferences("ChargingPrefs", Context.MODE_PRIVATE)
        etConnectedText.setText(prefs.getString("text_connected", "Şarj kablosu bağlandı."))
        etDisconnectedText.setText(prefs.getString("text_disconnected", "Şarj kablosu çıkarıldı."))

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("text_connected", etConnectedText.text.toString())
                .putString("text_disconnected", etDisconnectedText.text.toString())
                .apply()
            Toast.makeText(this, "Metinler Kaydedildi!", Toast.LENGTH_SHORT).show()
            startChargingService()
        }

        btnTestTts.setOnClickListener {
            val text = etConnectedText.text.toString()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TestTTS")
        }

        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startChargingService()
        }
    }

    private fun startChargingService() {
        val serviceIntent = Intent(this, ChargingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("tr", "TR")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
