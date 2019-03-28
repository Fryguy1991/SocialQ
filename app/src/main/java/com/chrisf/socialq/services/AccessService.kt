package com.chrisf.socialq.services

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chrisf.socialq.business.AppConstants
import com.chrisf.socialq.model.AccessModel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AccessService : JobService() {
    companion object {
        val TAG = AccessService::class.java.name
    }
    override fun onStartJob(params: JobParameters?): Boolean {
        refreshAccessToken()
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

    private fun refreshAccessToken() {
        if (AccessModel.getRefreshToken().isNullOrEmpty()) {
            Log.e(TAG, "Error invalid refresh token")
        } else {
            Log.d(TAG, "Request new access token")
            // TODO: Should be able to support requesting multiple AWS instances
            val client = OkHttpClient()
            val request = Request.Builder().url(String.format(AppConstants.AUTH_REQ_URL_FORMAT, AccessModel.getRefreshToken())).build()

            val response = client.newCall(request).execute()
            val responseString = response.body()?.string()

            if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Log.d(TAG, "Received new access token:\nAccess Token: $accessToken\nExpires In: $expiresIn seconds")

                // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val expireTime = SystemClock.elapsedRealtime() + (expiresIn - 60) * 1000
                // Set access token and expire time into model
                AccessModel.setAccess(accessToken, expireTime)

                // Broadcast that the access code has been updated
                Log.d(TAG, "Broadcasting that access token has been updated")
                val accessRefreshIntent = Intent(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED)
                LocalBroadcastManager.getInstance(this).sendBroadcast(accessRefreshIntent)
            } else {
                Log.e(TAG, "Response was unsuccessful or response string was null")
            }
        }
    }
}