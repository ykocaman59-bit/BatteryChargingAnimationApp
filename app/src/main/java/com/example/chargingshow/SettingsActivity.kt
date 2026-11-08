package com.example.chargingshow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var ring: ChargeRingView
    private lateinit var tvPercent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvOverlayState: TextView
    private lateinit var switchService: SwitchCompat
    private lateinit var etPlug: EditText
    private lateinit var etUnplug: EditText
    private lateinit var switchLow: SwitchCompat
    private lateinit var etLowLevel: EditText
    private lateinit var etLowText: EditText

    private val prefs by lazy { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBattery(intent)
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // izin verilsin verilmesin servis çalışır, sadece bildirim görünmeyebilir
            startChargingService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ring = findViewById(R.id.ring)
        tvPercent = findViewById(R.id.tvPercent)
        tvStatus = findViewById(R.id.tvStatus)
        tvOverlayState = findViewById(R.id.tvOverlayState)
        switchService = findViewById(R.id.switchService)
        etPlug = findViewById(R.id.etPlug)
        etUnplug = findViewById(R.id.etUnplug)
        switchLow = findViewById(R.id.switchLow)
        etLowLevel = findViewById(R.id.etLowLevel)
        etLowText = findViewById(R.id.etLowText)

        switchLow.isChecked = prefs.getBoolean(Prefs.LOW_ENABLED, true)
        etLowLevel.setText(prefs.getInt(Prefs.LOW_LEVEL, Prefs.DEFAULT_LOW_LEVEL).toString())
        etLowText.setText(prefs.getString(Prefs.LOW_TEXT, Prefs.DEFAULT_LOW_TEXT))

        etPlug.setText(prefs.getString(Prefs.TEXT_PLUG, Prefs.DEFAULT_PLUG))
        etUnplug.setText(prefs.getString(Prefs.TEXT_UNPLUG, Prefs.DEFAULT_UNPLUG))

        val enabled = prefs.getBoolean(Prefs.ENABLED, false)
        switchService.isChecked = enabled
        switchService.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.ENABLED, checked).apply()
            if (checked) ensureNotificationPermissionThenStart()
            else stopService(Intent(this, ChargingService::class.java))
        }
        if (enabled) startChargingService()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val lowLevel = etLowLevel.text.toString().toIntOrNull()?.coerceIn(1, 99)
                ?: Prefs.DEFAULT_LOW_LEVEL
            etLowLevel.setText(lowLevel.toString())
            prefs.edit()
                .putString(Prefs.TEXT_PLUG, etPlug.text.toString().trim())
                .putString(Prefs.TEXT_UNPLUG, etUnplug.text.toString().trim())
                .putBoolean(Prefs.LOW_ENABLED, switchLow.isChecked)
                .putInt(Prefs.LOW_LEVEL, lowLevel)
                .putString(Prefs.LOW_TEXT, etLowText.text.toString().trim())
                .apply()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { updateBattery(it) }
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        tvOverlayState.setText(
            if (Settings.canDrawOverlays(this)) R.string.overlay_on else R.string.overlay_off
        )
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        ring.progress = pct / 100f
        tvPercent.text = "%$pct"
        tvStatus.setText(if (plugged) R.string.charging else R.string.not_charging)
    }

    private fun ensureNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startChargingService()
        }
    }

    private fun startChargingService() {
        ContextCompat.startForegroundService(this, Intent(this, ChargingService::class.java))
    }
}
