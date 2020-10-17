package com.chrisf.socialq.processor

import android.app.job.JobParameters
import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.network.AuthService
import com.chrisf.socialq.network.AuthService.AuthResponse.*
import com.chrisf.socialq.processor.AccessProcessor.AccessAction
import com.chrisf.socialq.processor.AccessProcessor.AccessAction.RequestAccessRefresh
import com.chrisf.socialq.processor.AccessProcessor.AccessState
import com.chrisf.socialq.processor.AccessProcessor.AccessState.AccessRefreshComplete
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

/**
 * Processor for refreshing the user's access token (given we have already gotten a refresh token)
 */
class AccessProcessor @Inject constructor(
    private val authService: AuthService,
    private val preferences: SocialQPreferences,
    subscriptions: CompositeDisposable
) : BaseProcessor<AccessState, AccessAction>(null, subscriptions) {

    override fun handleAction(action: AccessAction) {
        when (action) {
            is RequestAccessRefresh -> handleRequestAccessRefresh(action)
        }
    }

    private fun handleRequestAccessRefresh(action: RequestAccessRefresh) {
        val token = preferences.refreshToken
        if (token.isNullOrBlank()) {
            Timber.e("Error invalid refresh token")
            // TODO: Should get a new refresh token and try again
        } else {
            authService.getAccessTokenWithRefreshToken(token)
                .subscribeOn(Schedulers.io())
                .onErrorReturn { Failure(Throwable("Unknown error")) }
                .map { response ->
                    if (response is Success) {
                        preferences.accessToken = response.body
                    }
                    AccessRefreshComplete(action.jobParameters)
                }
                .subscribe(stateStream)
                .addTo(subscriptions)
        }
    }

    sealed class AccessState {
        // TODO: Should add a state for failure
        data class AccessRefreshComplete(val jobParameters: JobParameters) : AccessState()
    }

    sealed class AccessAction {
        data class RequestAccessRefresh(val jobParameters: JobParameters) : AccessAction()
    }
}