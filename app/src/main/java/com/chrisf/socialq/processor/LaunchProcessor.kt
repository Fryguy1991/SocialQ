package com.chrisf.socialq.processor

import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.enums.SpotifyUserType
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.network.ApiResponse
import com.chrisf.socialq.network.AuthService
import com.chrisf.socialq.network.SpotifyService
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction.*
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState.*
import com.chrisf.socialq.userinterface.common.AlertDialogBinding
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject

class LaunchProcessor @Inject constructor(
    private val resources: Resources,
    private val authService: AuthService,
    private val preferences: SocialQPreferences,
    private val spotifyService: SpotifyService,
    lifecycle: Lifecycle,
    subscriptions: CompositeDisposable
) : BaseProcessor<LaunchState, LaunchAction>(lifecycle, subscriptions) {

    private val joinableQueues = mutableListOf<QueueModel>()

    private var isNearbySearching = false

    override fun handleAction(action: LaunchAction) {
        when (action) {
            is ViewCreated -> handleViewCreated(action)
            is QueueRefreshRequested -> handleQueueRefreshRequested()
            is EndpointFound -> handleEndpointFound(action)
            is EndpointLost -> handleEndpointLost(action)
            is AuthCodeRetrieved -> handleAuthCodeRetrieved(action)
            is LocationPermissionDenied -> handleLocationPermissionDenied(action)
            is QueueSelected -> handleQueueSelected(action)
        }
    }

    private fun handleViewCreated(action: ViewCreated) {
        val refreshToken = preferences.refreshToken
        if (refreshToken.isNullOrBlank()) {
            stateStream.accept(RequestAuthorization)
        } else {
            authService.getAccessTokenWithRefreshToken(refreshToken)
                .subscribeOn(Schedulers.io())
                .map { response ->
                    when (response) {
                        is AuthService.AuthResponse.Success -> {
                            preferences.accessToken = response.body
                            getCurrentUser()
                            StartAuthRefreshJob
                        }
                        is AuthService.AuthResponse.Failure,
                        AuthService.AuthResponse.Timeout -> AuthorizationFailed
                    }
                }
                .onErrorReturn { AuthorizationFailed }
                .subscribe(stateStream)
                .addTo(subscriptions)
        }
    }

    private fun handleAuthCodeRetrieved(action: AuthCodeRetrieved) {
        preferences.authCode = action.code
        authService.getTokensWithAuthCode(action.code)
            .subscribeOn(Schedulers.io())
            .map { response ->
                when (response) {
                    is AuthService.AuthResponse.Success -> {
                        preferences.accessToken = response.body.accessToken
                        preferences.refreshToken = response.body.refreshToken
                        StartAuthRefreshJob
                    }
                    is AuthService.AuthResponse.Failure,
                    AuthService.AuthResponse.Timeout -> AuthorizationFailed
                }
            }
            .onErrorReturn { AuthorizationFailed }
            .subscribe(stateStream)
            .addTo(subscriptions)
    }

    private fun handleLocationPermissionDenied(action: LocationPermissionDenied) {
        stateStream.accept(NoQueuesFound)

        // Don't want to show dialogs during start up or when view is resumed. Only launch when event is specifically
        // launching from a refresh UI event
        if (action.fromRefresh) {
            stateStream.accept(
                ShowAlertDialog(
                    AlertDialogBinding(
                        title = resources.getString(R.string.location_permission_required_title),
                        message = resources.getString(R.string.location_permission_required_message),
                        positiveButtonText = resources.getString(R.string.ok),
                        positiveAction = null,
                        negativeButtonText = null,
                        negativeAction = null,
                        neutralButtonText = null,
                        neutralAction = null
                    )
                )
            )
        }
    }

    private fun handleQueueRefreshRequested() {
        if (!isNearbySearching) {
            searchForQueues()
        }
    }

    private fun handleEndpointFound(state: EndpointFound) {
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

    private fun handleEndpointLost(state: EndpointLost) {
        joinableQueues.removeAll { it.endpointId == state.endpointId }
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

        data class ShowAlertDialog(val binding: AlertDialogBinding<LaunchAction>) : LaunchState()
    }

    sealed class LaunchAction {
        object ViewCreated : LaunchAction()

        object QueueRefreshRequested : LaunchAction()

        data class StartedNearbySearch(val wasSuccessful: Boolean) : LaunchAction()

        data class EndpointFound(
            val endpointId: String,
            val endpointInfo: DiscoveredEndpointInfo
        ) : LaunchAction()

        data class EndpointLost(val endpointId: String) : LaunchAction()

        data class QueueSelected(val queue: QueueModel) : LaunchAction()

        data class AuthCodeRetrieved(val code: String) : LaunchAction()

        data class LocationPermissionDenied(val fromRefresh: Boolean) : LaunchAction()
    }
}