package com.chrisfry.socialq.userinterface

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.dagger.modules.FrySpotifyModule
import com.chrisfry.socialq.business.dagger.modules.components.DaggerFrySpotifyComponent
import com.chrisfry.socialq.business.dagger.modules.components.FrySpotifyComponent

class App : Application() {
    companion object {
        // Flag used to determine if we need to start or rebind to service
        var hasServiceBeenStarted = false
        // Notification channel ID
        val CHANNEL_ID = "SocialQServiceChannel"
    }

    @JvmField
    var spotifyComponent: FrySpotifyComponent? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        spotifyComponent = DaggerFrySpotifyComponent.builder().frySpotifyModule(FrySpotifyModule(this)).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_name),
                    NotificationManager.IMPORTANCE_LOW)

            val manager = getSystemService(NotificationManager::class.java) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }
}