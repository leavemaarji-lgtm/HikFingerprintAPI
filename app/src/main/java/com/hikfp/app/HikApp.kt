package com.hikfp.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class HikApp : Application() {
    companion object {
        lateinit var instance: HikApp
        const val CHANNEL_SYNC = "hik_sync"
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SYNC, "Sync", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
