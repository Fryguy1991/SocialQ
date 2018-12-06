package com.chrisfry.socialq.userinterface.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.services.HostService
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse

class AccessTokenReceiverActivity : Activity() {
    companion object {
        val TAG = AccessTokenReceiverActivity::class.java.name
    }

    // Access token scopes to request from Spotify
    private lateinit var accessScopes: Array<String>
    //
    private var isHostFlag: Boolean = true
    private var isBound = false
    private lateinit var hostService: HostService

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Access token retriever activity being created")
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(AppConstants.IS_HOST_KEY, true)) {
            accessScopes = arrayOf("user-read-private", "streaming", "playlist-modify-private", "playlist-read-private")
        } else {
            isHostFlag = false
            accessScopes = arrayOf("user-read-private")
        }

        // Bind to service
        if (isHostFlag) {
            val serviceTokenIntent = Intent(baseContext, HostService::class.java)
            bindService(serviceTokenIntent, hostConnection, Context.BIND_AUTO_CREATE)
        } else {
            // TODO: client service as well
        }

        requestAccessToken()
    }

    private fun requestAccessToken() {
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI)
        builder.setScopes(accessScopes)
        val request = builder.build()
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.requestCode, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                when (response.type) {
                    AuthenticationResponse.Type.CODE -> {
                        Log.e(TAG, "Not handling request for auth code")
                    }
                    AuthenticationResponse.Type.TOKEN -> {
                        Log.d(TAG, "Access token granted")
                        // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                        val expireTime = System.currentTimeMillis() + (response.expiresIn - 60) * 1000

                        // Set access token and expire time into model
                        AccessModel.setAccess(response.accessToken, expireTime)

                        // Notify service access token is updated
                        Log.d(TAG, "Notifying service of new access token and unbind")
                        hostService.accessTokenUpdated()
                        unbindService(hostConnection)
                        isBound = false
                        finish()
                        return
                    }
                    AuthenticationResponse.Type.ERROR -> {
                        Log.e(TAG, "Authentication error: ${response.error}")
                    }
                    AuthenticationResponse.Type.EMPTY -> {
                        Log.e(TAG, "Not handling empty case")
                    }
                    AuthenticationResponse.Type.UNKNOWN -> {
                        Log.e(TAG, "Not handling unknown case")
                    }
                    else -> {
                        Log.e(TAG, "Something went wrong. Not handling response type: ${response.type}")
                    }
                }
                Log.e(TAG, "Trying again to get access token")
                requestAccessToken()
                // TODO: Should put a limit on this so it doesn't hold up app for long
            }
            RequestType.SEARCH_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Log.e(TAG, "Access token receiver activity should not receiver $requestType")
            }
            RequestType.NONE -> {
                Log.e(TAG, "Unhandled request code")
            }
        }
    }

    private val hostConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected from access token receiver")

            isBound = false
            finish()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Access token receiver activity is bound to service")

            isBound = true
            val binder = service as HostService.HostServiceBinder
            hostService = binder.getService()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Access token receiver activity being destroyed")
        if (isBound) {
            unbindService(hostConnection)
        }
        super.onDestroy()
    }
}