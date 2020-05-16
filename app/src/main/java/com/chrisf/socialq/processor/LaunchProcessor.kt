package com.chrisf.socialq.processor

import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.enums.SpotifyUserType
import com.chrisf.socialq.model.QueueModel
import com.chrisf.socialq.network.ApiResponse
import com.chrisf.socialq.network.SpotifyService
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction
import com.chrisf.socialq.processor.LaunchProcessor.LaunchAction.*
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState
import com.chrisf.socialq.processor.LaunchProcessor.LaunchState.*
import com.chrisf.socialq.userinterface.common.AlertDialogBinding
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject

class LaunchProcessor @Inject constructor(
    private val resources: Resources,
    private val spotifyService: SpotifyService,
    lifecycle: Lifecycle,
    subscriptions: CompositeDisposable
) : BaseProcessor<LaunchState, LaunchAction>(lifecycle, subscriptions) {
    private var queueSearchDisposable: Disposable? = null

    override fun handleAction(action: LaunchAction) {
        when (action) {
            is ViewCreated -> handleViewCreated()
            is ViewPausing -> handleViewPausing()
            is QueueRefreshRequested -> handleQueueRefreshRequested()
            is LocationPermissionDenied -> handleLocationPermissionDenied()
            is QueueSelected -> handleQueueSelected(action)
            is StartNewQueueButtonTouched -> handleStartNewQueueButtonTouched(action)
            is EndpointListUpdated -> handleEndpointListUpdated(action)
            is OpenAppSettingsDialogButtonTouched -> stateStream.accept(NavigateToAppSettings)
            is QueueSearchStopped -> handleQueueSearchStopped(action)
        }
    }

    private fun handleViewCreated() {
        spotifyService.getCurrentUser()
            .subscribeOn(Schedulers.io())
            .map {
                when (it) {
                    is ApiResponse.Success -> {
                        val isPremium = SpotifyUserType
                            .getSpotifyUserTypeFromProductType(it.body.product) == SpotifyUserType.PREMIUM
                        EnableNewQueueButton(isPremium)
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

    private fun handleViewPausing() {
        queueSearchDisposable?.dispose()
    }

    private fun handleLocationPermissionDenied() {
        stateStream.accept(ShowQueueRefreshFailed)
        stateStream.accept(
            ShowLocationPermissionRequiredDialog(
                AlertDialogBinding(
                    title = resources.getString(R.string.location_permission_required_title),
                    message = resources.getString(R.string.location_permission_required_message),
                    isCancelable = true,
                    positiveButtonText = resources.getString(R.string.open_settings),
                    positiveAction = OpenAppSettingsDialogButtonTouched,
                    negativeButtonText = resources.getString(R.string.cancel)
                )
            )
        )
    }

    private fun handleQueueRefreshRequested() {
        stateStream.accept(SearchForQueues)

        queueSearchDisposable = Observable.just(Unit)
            .delay(5, TimeUnit.SECONDS)
            .map { StopSearchingForQueues }
            .subscribe(stateStream)
            .addTo(subscriptions)
    }

    private fun handleEndpointListUpdated(action: EndpointListUpdated) {
        val displayList = action.updatedList.filter {
            Pattern.compile(AppConstants.NEARBY_HOST_NAME_REGEX)
                .matcher(it.endpointInfo.endpointName)
                .matches()
        }.map {
            val hostNameMatcher = Pattern.compile(AppConstants.NEARBY_HOST_NAME_REGEX).matcher(it.endpointInfo.endpointName)
            hostNameMatcher.find()
            val queueName = hostNameMatcher.group(1) ?: resources.getString(R.string.unknown_queue)
            val ownerName = hostNameMatcher.group(2) ?: resources.getString(R.string.unknown_host)
            val isFairPlayCharacter = hostNameMatcher.group(3)
            val isFairPlay = isFairPlayCharacter == AppConstants.FAIR_PLAY_TRUE_CHARACTER

            QueueModel(
                endpointId = it.endpointId,
                queueName = queueName,
                ownerName = ownerName,
                isFairPlayActive = isFairPlay
            )
        }

        stateStream.accept(DisplayAvailableQueues(displayList))
    }

    private fun handleQueueSelected(action: QueueSelected) {
        stateStream.accept(LaunchClientActivity(action.queue.endpointId, action.queue.queueName))
    }

    private fun handleStartNewQueueButtonTouched(action: StartNewQueueButtonTouched) {
        stateStream.accept(
            if (action.isUserPremium) {
                NavigateToNewQueue
            } else {
                val dialogBinding = AlertDialogBinding<LaunchAction>(
                    title = resources.getString(R.string.premium_required_title),
                    message = resources.getString(R.string.premium_required_message),
                    isCancelable = true,
                    positiveButtonText = resources.getString(R.string.ok)
                )
                ShowPremiumRequiredDialog(dialogBinding)
            }
        )
    }

    private fun handleQueueSearchStopped(action: QueueSearchStopped) {
        if (action.queueList.isEmpty()) stateStream.accept(NoQueuesFound)
    }

    sealed class LaunchState {
        object SearchForQueues : LaunchState()

        object StopSearchingForQueues : LaunchState()

        data class EnableNewQueueButton(val isUserPremium: Boolean) : LaunchState()

        object NoQueuesFound : LaunchState()

        object ShowQueueRefreshFailed : LaunchState()

        data class DisplayAvailableQueues(val queueList: List<QueueModel>) : LaunchState()

        data class LaunchClientActivity(
            val hostEndpoint: String,
            val queueTitle: String
        ) : LaunchState()

        data class ShowAuthFailedDialog(val binding: AlertDialogBinding<LaunchAction>) : LaunchState()

        data class ShowLocationPermissionRequiredDialog(val binding: AlertDialogBinding<LaunchAction>) : LaunchState()

        data class ShowPremiumRequiredDialog(val binding: AlertDialogBinding<LaunchAction>) : LaunchState()

        object NavigateToNewQueue : LaunchState()

        object NavigateToAppSettings : LaunchState()
    }

    sealed class LaunchAction {
        object ViewCreated : LaunchAction()

        object ViewPausing : LaunchAction()

        object QueueRefreshRequested : LaunchAction()

        data class StartedNearbySearch(val wasSuccessful: Boolean) : LaunchAction()

        data class QueueSelected(val queue: QueueModel) : LaunchAction()

        object LocationPermissionDenied : LaunchAction()

        data class StartNewQueueButtonTouched(val isUserPremium: Boolean) : LaunchAction()

        data class EndpointListUpdated(val updatedList: List<SocialQEndpoint>) : LaunchAction()

        object OpenAppSettingsDialogButtonTouched : LaunchAction()

        data class QueueSearchStopped(val queueList: List<SocialQEndpoint>) : LaunchAction()
    }
}

/**
 * SocialQ supporting endpoint found by the view
 */
data class SocialQEndpoint(
    val endpointId: String,
    val endpointInfo: DiscoveredEndpointInfo
)