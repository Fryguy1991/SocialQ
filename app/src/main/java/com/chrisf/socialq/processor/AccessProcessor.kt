package com.chrisf.socialq.processor

import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.network.AuthService
import com.chrisf.socialq.network.AuthService.AuthResponse.*
import com.chrisf.socialq.processor.AccessProcessor.AccessAction
import com.chrisf.socialq.processor.AccessProcessor.AccessAction.RequestAccessRefresh
import com.chrisf.socialq.processor.AccessProcessor.AccessState
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
            is RequestAccessRefresh -> handleRequestAccessRefresh()
        }
    }

    private fun handleRequestAccessRefresh() {
        val token = preferences.refreshToken
        if (token.isNullOrBlank()) {
            Timber.e("Error invalid refresh token")
            // TODO: Should get a new refresh token and try again
        } else {
            authService.getAccessTokenWithRefreshToken(token)
                    .subscribeOn(Schedulers.io())
                    .onErrorReturn { Failure(Throwable("Unknown error")) }
                    .subscribe { response ->
                        when (response) {
                            is Success -> {
                                preferences.accessToken = response.body
                            }
                            is Failure,
                            is Timeout -> Unit // TODO: Future should probably exit wherever user is and go back to launch
                        }
                    }
                    .addTo(subscriptions)
        }
    }

    sealed class AccessState {
        // Currently don't need to send any states
    }

    sealed class AccessAction {
        object RequestAccessRefresh : AccessAction()
    }
}