package com.example.chargingshow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

class ChargingReceiver : BroadcastReceiver() {

    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val prefs = context.getSharedPreferences("ChargingPrefs", Context.MODE_PRIVATE)

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val text = prefs.getString("text_connected", "Şarj kablosu bağlandı.")
                speakText(context, text)
                
                val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(overlayIntent)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val text = prefs.getString("text_disconnected", "Şarj kablosu çıkarıldı.")
                speakText(context, text)
            }
        }
    }

    private fun speakText(context: Context, text: String?) {
        if (text.isNull_or_empty()) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("tr", "TR")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ChargingTTS")
            }
        }
    }
}
