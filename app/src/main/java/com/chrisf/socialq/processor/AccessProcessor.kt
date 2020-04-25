package com.chrisf.socialq.processor

import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.processor.AccessProcessor.AccessAction
import com.chrisf.socialq.processor.AccessProcessor.AccessAction.RequestAccessRefresh
import com.chrisf.socialq.processor.AccessProcessor.AccessState
import io.reactivex.disposables.CompositeDisposable
import okhttp3.*
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Processor for refreshing the user's access token (given we have already gotten a refresh token)
 */
class AccessProcessor @Inject constructor(
        private val preferences: SocialQPreferences,
        subscriptions: CompositeDisposable
) : BaseProcessor<AccessState, AccessAction>(null, subscriptions) {

    override fun handleAction(action: AccessAction) {
        when (action) {
            is RequestAccessRefresh -> handleRequestAccessRefresh()
        }
    }

    private fun handleRequestAccessRefresh() {
        if (preferences.refreshToken.isNullOrBlank()) {
            Timber.e("Error invalid refresh token")
        } else {
            Timber.d("Request new access token")
            // TODO: Should be able to support requesting multiple AWS instances
            val client = OkHttpClient()
            val request = Request.Builder().url(String.format(AppConstants.AUTH_REQ_URL_FORMAT, preferences.refreshToken)).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseString = response.body()?.string()

                    if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                        val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)
                        val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                        val expiresIn = bodyJson.getInt(AppConstants.  JSON_EXPIRES_IN_KEY)

                        preferences.accessToken = accessToken

                        Timber.d("Received new access token:\nAccess Token: $accessToken\nExpires In: $expiresIn seconds")
                    } else {
                        Timber.e("Response was unsuccessful or response string was null")
                    }
                }

                override fun onFailure(call: Call, exception: IOException) {
                    Timber.e(exception)
                }
            })
        }
    }

    sealed class AccessState {
        // Currently don't need to send any states
    }

    sealed class AccessAction {
        object RequestAccessRefresh : AccessAction()
    }
}