package com.example.chargingshow

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ChargingService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
