package com.chrisfry.socialq.userinterface

import android.app.Application

class App : Application() {
    companion object {
        // Flag used to determine if we need to start or rebind to service
        var hasServiceBeenStarted = false
    }
}