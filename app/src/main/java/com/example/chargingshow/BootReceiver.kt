package com.example.chargingshow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Telefon yeniden başlayınca, kullanıcı servisi açık bırakmışsa tekrar başlatır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ENABLED, false)
        if (enabled) {
            ContextCompat.startForegroundService(context, Intent(context, ChargingService::class.java))
        }
    }
}
