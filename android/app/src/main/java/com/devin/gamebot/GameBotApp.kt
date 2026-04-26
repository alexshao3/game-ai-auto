package com.devin.gamebot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.devin.gamebot.data.api.BackendClient
import com.devin.gamebot.data.db.AppDatabase
import com.devin.gamebot.data.repo.RecipeRepository
import com.devin.gamebot.data.repo.RecordingRepository

class GameBotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_CAPTURE,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_CAPTURE = "capture"

        @Volatile private var instance: GameBotApp? = null

        fun appDatabase(context: Context): AppDatabase = AppDatabase.get(context)
        fun backendClient(): BackendClient = BackendClient.get()
        fun recipeRepo(context: Context): RecipeRepository = RecipeRepository(appDatabase(context))
        fun recordingRepo(context: Context): RecordingRepository = RecordingRepository(appDatabase(context))
    }
}
