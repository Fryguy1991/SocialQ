package com.chrisf.socialq.userinterface

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chrisf.socialq.BuildConfig
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.AppComponent
import com.chrisf.socialq.dagger.components.DaggerAppComponent
import com.chrisf.socialq.dagger.modules.AppModule
import timber.log.Timber

class App : Application() {
    companion object {
        // Flag used to determine if we need to start or rebind to service
        var hasServiceBeenStarted = false
        // Notification channel ID
        val CHANNEL_ID = "SocialQServiceChannel"
    }

    lateinit var appComponent: AppComponent

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        appComponent = DaggerAppComponent.builder().appModule(AppModule(this)).build()
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