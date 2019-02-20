package com.chrisfry.socialq.userinterface.activities

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.services.AccessService
import com.chrisfry.socialq.userinterface.fragments.JoinQueueFragment
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LaunchActivity : BaseActivity(), JoinQueueFragment.JoinQueueFragmentListener {
    companion object {
        val TAG = LaunchActivity::class.java.name
    }

    // Reference to nav controller
    private lateinit var navController: NavController
    // Toolbar reference
    private lateinit var toolbar: Toolbar
    // Retry count for retrieving authorization
    private var retryCount = 0
    // Scheduler for refreshing access token
    private lateinit var scheduler: JobScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launch_activity)

        // Retrieve nav controller
        navController = findNavController(R.id.frag_nav_host)
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        // Setup the app toolbar
        toolbar = findViewById(R.id.app_toolbar)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Stop soft keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Allow network operation in main thread
        val policy = StrictMode.ThreadPolicy.Builder()
                .permitAll().build()
        StrictMode.setThreadPolicy(policy)

        scheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        requestAuthorization()
    }

    override fun onDestroy() {
        // Cancel access refresh jobs
        scheduler.cancel(AppConstants.ACCESS_SERVICE_ID)
        super.onDestroy()
    }

    override fun showQueueTitle(queueTitle: String) {
        toolbar.title = queueTitle
    }

    private fun requestAuthorization() {
        val accessScopes = arrayOf("user-read-private", "streaming", "playlist-modify-private", "playlist-modify-public", "playlist-read-private")
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.CODE,
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
                        Log.d(TAG, "Authorization code granted")

                        // Store authorization code
                        AccessModel.setAuthorizationCode(response.code)

                        // Auth code received, retrieve access and refresh tokens
                        authCodeReceived()
                        return
                    }
                    AuthenticationResponse.Type.TOKEN -> {
                        Log.e(TAG, "Should be retrieving access tokens from backend server")
                        // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                        val expireTime = SystemClock.elapsedRealtime() + (response.expiresIn - 60) * 1000

                        // Set access token and expire time into model
                        AccessModel.setAccess(response.accessToken, expireTime)

                        // Schedule access token refresh to occur every 20 minutes
                        startPeriodicAccessRefresh()
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
                if (retryCount < 3) {
                    Log.e(TAG, "Trying again to get access token")
                    retryCount++
                    requestAuthorization()
                } else {
                    Log.e(TAG, "Reached maximum number of auth attempts")
                    Toast.makeText(this, R.string.toast_authentication_error_client, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            RequestType.SEARCH_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Log.e(TAG, "Launch activity should not receiver $requestType")
            }
            RequestType.NONE -> {
                Log.e(TAG, "Unhandled request code")
            }
        }
    }

    private fun authCodeReceived() {
        if (AccessModel.getAuthorizationCode().isNullOrEmpty()) {
            Log.e(TAG, "Error invalid authorization code")
        } else {
            Log.d(TAG, "Have authorization code. Request access/refresh tokens")
            val client = OkHttpClient()
            val request = Request.Builder().url(AppConstants.AWS_SERVER_URL + AccessModel.getAuthorizationCode()).build()

            val response = client.newCall(request).execute()
            val responseString = response.body()?.string()

            if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val refreshToken = bodyJson.getString(AppConstants.JSON_REFRESH_TOEKN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Log.d(TAG, "Received authorization:\nAccess Token: $accessToken\nRefresh Token: $refreshToken\nExpires In: $expiresIn seconds")

                // Store refresh token
                AccessModel.setRefreshToken(refreshToken)

                // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val expireTime = SystemClock.elapsedRealtime() + (expiresIn - 60) * 1000
                // Set access token and expire time into model
                AccessModel.setAccess(accessToken, expireTime)

                // Schedule access token refresh to occur every 20 minutes
                startPeriodicAccessRefresh()
            } else {
                Log.e(TAG, "Response was unsuccessful or response string was null")
            }
        }
    }

    private fun startPeriodicAccessRefresh() {
        scheduler.schedule(JobInfo.Builder(AppConstants.ACCESS_SERVICE_ID,
                ComponentName(this, AccessService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.MINUTES.toMillis(20))
                .build())
    }
}