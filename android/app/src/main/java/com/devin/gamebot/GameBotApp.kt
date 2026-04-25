package com.devin.gamebot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GameBotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_CAPTURE,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_CAPTURE = "capture"
    }
}
