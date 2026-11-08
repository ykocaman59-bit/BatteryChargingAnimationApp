package com.example.chargingshow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Arka planda çalışan servis. Android 8+ sürümlerde "şarj takıldı/çıkarıldı" yayınları
 * manifest ile dinlenemediği için receiver burada, çalışma anında kaydedilir.
 */
class ChargingService : Service() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var lowWarned = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkLowBattery(intent)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPlugChanged(true)
                Intent.ACTION_POWER_DISCONNECTED -> onPlugChanged(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("tr", "TR"))
                ttsReady = true
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // düşük şarj uyarısı için pil seviyesini izle
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) checkLowBattery(sticky)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
        unregisterReceiver(batteryReceiver)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun onPlugChanged(connected: Boolean) {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val text = if (connected) {
            prefs.getString(Prefs.TEXT_PLUG, Prefs.DEFAULT_PLUG)
        } else {
            prefs.getString(Prefs.TEXT_UNPLUG, Prefs.DEFAULT_UNPLUG)
        }
        if (ttsReady && !text.isNullOrBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "charge_event")
        }

        // "Diğer uygulamaların üstünde göster" izni varsa arka plandan ekran açılabilir
        if (connected && Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Şarj, ayarlardaki yüzdeye (varsayılan 19) düşünce bir kez uyarır. */
    private fun checkLowBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean(Prefs.LOW_ENABLED, true)
        val threshold = prefs.getInt(Prefs.LOW_LEVEL, Prefs.DEFAULT_LOW_LEVEL)

        if (plugged || pct > threshold) {
            // şarja takıldı ya da seviye yükseldi: bir sonraki düşüşte tekrar uyarabilsin
            lowWarned = false
            getSystemService(NotificationManager::class.java).cancel(LOW_NOTIFICATION_ID)
            return
        }
        if (enabled && !lowWarned) {
            lowWarned = true
            val text = prefs.getString(Prefs.LOW_TEXT, Prefs.DEFAULT_LOW_TEXT)
                ?.takeIf { it.isNotBlank() } ?: Prefs.DEFAULT_LOW_TEXT

            if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "low_battery")

            val pendingIntent = PendingIntent.getActivity(
                this, 1,
                Intent(this, SettingsActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(this, LOW_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Şarj %$pct")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            getSystemService(NotificationManager::class.java).notify(LOW_NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    LOW_CHANNEL_ID,
                    getString(R.string.low_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "charging_service"
        private const val NOTIFICATION_ID = 1
        private const val LOW_CHANNEL_ID = "low_battery"
        private const val LOW_NOTIFICATION_ID = 2
    }
}
