package com.chrisfry.socialq.userinterface

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chrisfry.socialq.R

class App : Application() {
    companion object {
        // Flag used to determine if we need to start or rebind to service
        var hasServiceBeenStarted = false
        // Notification channel ID
        val CHANNEL_ID = "SocialQServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_name),
                    NotificationManager.IMPORTANCE_DEFAULT)

            val manager = getSystemService(NotificationManager::class.java) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }
}