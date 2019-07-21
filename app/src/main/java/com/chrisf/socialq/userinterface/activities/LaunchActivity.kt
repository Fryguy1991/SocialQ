package com.chrisf.socialq.userinterface.activities

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.enums.RequestType
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.services.AccessService
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LaunchActivity : AppCompatActivity() {

    // Reference to nav controller
    private lateinit var navController: NavController
    // Toolbar reference
    private lateinit var toolbar: Toolbar
    // Retry count for retrieving authorization
    private var retryCount = 0
    // Scheduler for refreshing access token
    private lateinit var scheduler: JobScheduler
    // Builder for dialogs
    private lateinit var alertbuilder: AlertDialog.Builder

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

        alertbuilder = AlertDialog.Builder(this)
                .setView(R.layout.dialog_auth_fail)
                .setPositiveButton(R.string.retry) { dialog, which ->
                    requestAuthorization()
                }
                .setNegativeButton(R.string.close_app) {dialog, which ->
                    finish()
                }
                .setOnCancelListener {
                    finish()
                }

        requestAuthorization()
    }

    override fun onDestroy() {
        // Cancel access refresh jobs
        scheduler.cancel(AppConstants.ACCESS_SERVICE_ID)
        super.onDestroy()
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
        super.onActivityResult(requestCode, resultCode, data)

        val requestType = RequestType.getRequestTypeFromRequestCode(requestCode)
        when (requestType) {
            RequestType.SPOTIFY_AUTHENTICATION_REQUEST -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                when (response.type) {
                    AuthenticationResponse.Type.CODE -> {
                        Timber.d("Authorization code granted")

                        // Store authorization code
                        AccessModel.setAuthorizationCode(response.code)

                        // Auth code received, retrieve access and refresh tokens
                        authCodeReceived()
                        return
                    }
                    AuthenticationResponse.Type.TOKEN -> {
                        Timber.e("Should be retrieving access tokens from backend server")
                        // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                        val expireTime = SystemClock.elapsedRealtime() + (response.expiresIn - 60) * 1000

                        // Set access token and expire time into model
                        AccessModel.setAccess(response.accessToken, expireTime)

                        // Schedule access token refresh to occur every 20 minutes
                        startPeriodicAccessRefresh()
                        return
                    }
                    AuthenticationResponse.Type.ERROR -> {
                        Timber.e("Authentication error: ${response.error}")
                    }
                    AuthenticationResponse.Type.EMPTY -> {
                        Timber.e("Not handling empty case")
                    }
                    AuthenticationResponse.Type.UNKNOWN -> {
                        Timber.e("Not handling unknown case")
                    }
                    else -> {
                        Timber.e("Something went wrong. Not handling response type: ${response.type}")
                    }
                }
                // Show authorization failed dialog
                alertbuilder.create().show()
            }
            RequestType.SEARCH_REQUEST,
            RequestType.LOCATION_PERMISSION_REQUEST -> {
                Timber.e("Launch activity should not receiver $requestType")
            }
            RequestType.NONE -> {
                Timber.e("Unhandled request code")
            }
        }
    }

    private fun authCodeReceived() {
        if (AccessModel.getAuthorizationCode().isNullOrEmpty()) {
            Timber.e("Error invalid authorization code")
        } else {
            Timber.d("Have authorization code. Request access/refresh tokens")
            val client = OkHttpClient()
            val request = Request.Builder().url(String.format(AppConstants.AUTH_REQ_URL_FORMAT, AccessModel.getAuthorizationCode())).build()

            val response = client.newCall(request).execute()
            val responseString = response.body()?.string()

            if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val refreshToken = bodyJson.getString(AppConstants.JSON_REFRESH_TOEKN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Timber.d("Received authorization:\nAccess Token: $accessToken\nRefresh Token: $refreshToken\nExpires In: $expiresIn seconds")

                // Store refresh token
                AccessModel.setRefreshToken(refreshToken)

                // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val expireTime = SystemClock.elapsedRealtime() + (expiresIn - 60) * 1000
                // Set access token and expire time into model
                AccessModel.setAccess(accessToken, expireTime)

                // Schedule access token refresh to occur every 20 minutes
                startPeriodicAccessRefresh()
            } else {
                Timber.e("Response was unsuccessful or response string was null")
            }
        }
    }

    private fun startPeriodicAccessRefresh() {
        scheduler.schedule(
                JobInfo.Builder(
                        AppConstants.ACCESS_SERVICE_ID,
                        ComponentName(this, AccessService::class.java)
                )
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(TimeUnit.MINUTES.toMillis(20))
                        .build()
        )
    }
}