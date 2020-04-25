package com.chrisf.socialq.processor

import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.enums.SpotifyUserType
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.network.ApiResponse
import com.chrisf.socialq.network.SpotifyService
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction.*
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState.*
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject

class LaunchProcessor @Inject constructor(
        private val preferences: SocialQPreferences,
        private val spotifyService: SpotifyService,
        lifecycle: Lifecycle,
        subscriptions: CompositeDisposable
) : BaseProcessor<LaunchState, LaunchAction>(lifecycle, subscriptions) {

    private val joinableQueues = mutableListOf<QueueModel>()

    private var isNearbySearching = false
    private var isRequestingAuth = false

    override fun handleAction(action: LaunchAction) {
        when (action) {
            is ViewResumed -> onViewResumed(action)
            QueueRefreshRequested -> onRefreshRequested()
            is EndpointFound -> onEndpointFound(action)
            is EndpointLost -> onEndpointLost(action)
            is AuthCodeRetrieved -> onAuthCodeRetrieved(action)
            is LocationPermissionRequestComplete -> handlePermissionRequest(action)
            is QueueSelected -> handleQueueSelected(action)
        }
    }

    private fun onViewResumed(action: ViewResumed) {
        if (isRequestingAuth) {
            return
        }

        if (preferences.authCode.isNullOrBlank()) {
            isRequestingAuth = true
            stateStream.accept(RequestAuthorization)
            return
        }

        getCurrentUser()

        if (action.hasLocationPermission) {
            searchForQueues()
        } else {
            stateStream.accept(RequestLocationPermission)
        }
    }

    private fun handlePermissionRequest(action: LocationPermissionRequestComplete) {
        if (action.hasPermission) {
            searchForQueues()
        } else {
            // TODO: Should show a dialog that location permission is required for app functionality
        }
    }

    private fun getCurrentUser() {
        spotifyService.getCurrentUser()
                .subscribeOn(Schedulers.io())
                .map {
                    when (it) {
                        is ApiResponse.Success -> {
                            val isPremium = SpotifyUserType
                                    .getSpotifyUserTypeFromProductType(it.body.product) == SpotifyUserType.PREMIUM
                            DisplayCanHostQueue(isPremium)
                        }
                        is ApiResponse.NetworkError -> TODO()
                        ApiResponse.NetworkTimeout -> TODO()
                        ApiResponse.Unauthorized -> TODO()
                        ApiResponse.UnknownError -> TODO()
                    }
                }
                .subscribe(stateStream)
                .addTo(subscriptions)
    }

    private fun onRefreshRequested() {
        if (!isNearbySearching) {
            searchForQueues()
        }
    }

    private fun onEndpointFound(state: EndpointFound) {
        if (state.endpointInfo.serviceId == AppConstants.SERVICE_NAME) {
            Timber.d("Found a SocialQ host with endpoint ID ${state.endpointId}")

            val hostNameMatcher = Pattern.compile(AppConstants.NEARBY_HOST_NAME_REGEX).matcher(state.endpointInfo.endpointName)

            if (hostNameMatcher.find()) {
                val queueName = hostNameMatcher.group(1)
                val ownerName = hostNameMatcher.group(2)
                val isFairplayCharacter = hostNameMatcher.group(3)

                val isFairplay = isFairplayCharacter == AppConstants.FAIR_PLAY_TRUE_CHARACTER
                joinableQueues.add(QueueModel(state.endpointId, queueName, ownerName, isFairplay))
            } else {
                Timber.e("Endpoint ID ${state.endpointId} has an invalid name")
            }
        }
    }

    private fun onEndpointLost(state: EndpointLost) {
        joinableQueues.removeAll { it.endpointId == state.endpointId }
    }

    private fun searchForQueues() {
        if (isNearbySearching) {
            return
        }

        joinableQueues.clear()
        isNearbySearching = true
        stateStream.accept(SearchForQueues)
        Observable.just(Unit)
                .delay(5, TimeUnit.SECONDS)
                .subscribe {
                    isNearbySearching = false
                    stateStream.accept(StopSearchingForQueues)

                    if (joinableQueues.size > 0) {
                        stateStream.accept(DisplayAvailableQueues(joinableQueues.toList()))
                    } else {
                        stateStream.accept(NoQueuesFound)
                    }
                }
                .addTo(subscriptions)
    }

    private fun handleQueueSelected(action: QueueSelected) {
        stateStream.accept(LaunchClientActivity(action.queue.endpointId, action.queue.queueName))
    }

    private fun onAuthCodeRetrieved(action: AuthCodeRetrieved) {
        preferences.authCode = action.code
        if (action.code.isBlank()) {
            Timber.e("Error invalid authorization code")
            isRequestingAuth = false
            stateStream.accept(AuthorizationFailed)
        } else {
            Timber.d("Have authorization code. Request access/refresh tokens")
            val client = OkHttpClient()
            val request = Request.Builder().url(String.format(AppConstants.AUTH_REQ_URL_FORMAT, action.code)).build()

            client.newCall(request).enqueue(authCallback)
        }
    }

    private val authCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Timber.e(e)
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
            val responseString = response.body()?.string()
            if (response.isSuccessful && responseString != null) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val refreshToken = bodyJson.getString(AppConstants.JSON_REFRESH_TOEKN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Timber.d("Received authorization:\nAccess Token: $accessToken\nRefresh Token: $refreshToken\nExpires In: $expiresIn seconds")

                preferences.accessToken = accessToken
                preferences.refreshToken = refreshToken

                isRequestingAuth = false

                // Schedule access token refresh to occur every 20 minutes. Access expires every 60 minutes, this
                // should ensure that a user never loses access
                stateStream.accept(StartAuthRefreshJob)
                searchForQueues()
                getCurrentUser()
            }
        }
    }

    sealed class LaunchState {
        object SearchForQueues : LaunchState()
        object StopSearchingForQueues : LaunchState()
        data class DisplayCanHostQueue(val canHost: Boolean) : LaunchState()
        object NoQueuesFound : LaunchState()
        data class DisplayAvailableQueues(val queueList: List<QueueModel>) : LaunchState()
        object RequestAuthorization : LaunchState()
        object RequestLocationPermission : LaunchState()
        object StartAuthRefreshJob : LaunchState()
        data class LaunchClientActivity(
                val hostEndpoint: String,
                val queueTitle: String
        ) : LaunchState()

        object AuthorizationFailed : LaunchState()
    }

    sealed class LaunchAction {
        data class ViewResumed(val hasLocationPermission: Boolean) : LaunchAction()
        object QueueRefreshRequested : LaunchAction()
        data class StartedNearbySearch(val wasSuccessful: Boolean) : LaunchAction()
        data class EndpointFound(
                val endpointId: String,
                val endpointInfo: DiscoveredEndpointInfo
        ) : LaunchAction()

        data class EndpointLost(val endpointId: String) : LaunchAction()
        data class QueueSelected(val queue: QueueModel) : LaunchAction()
        data class AuthCodeRetrieved(val code: String) : LaunchAction()
        data class LocationPermissionRequestComplete(val hasPermission: Boolean) : LaunchAction()
    }
}