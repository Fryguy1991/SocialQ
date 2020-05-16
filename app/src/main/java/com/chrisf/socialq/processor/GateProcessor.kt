package com.chrisf.socialq.processor

import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import com.chrisf.socialq.R
import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.network.AuthService
import com.chrisf.socialq.processor.GateProcessor.GateAction
import com.chrisf.socialq.processor.GateProcessor.GateAction.*
import com.chrisf.socialq.processor.GateProcessor.GateState
import com.chrisf.socialq.processor.GateProcessor.GateState.StartAuthRefreshJobAndNavigateToLaunch
import com.chrisf.socialq.processor.GateProcessor.GateState.ShowSignInFailedDialog
import com.chrisf.socialq.userinterface.common.AlertDialogBinding
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

/**
 * Processor for signing into Spotify
 */
class GateProcessor @Inject constructor(
    private val authService: AuthService,
    private val socialQPreferences: SocialQPreferences,
    private val resources: Resources,
    lifecycle: Lifecycle,
    subscriptions: CompositeDisposable
) : BaseProcessor<GateState, GateAction>(lifecycle, subscriptions) {
    private val authFailedDialogBinding: AlertDialogBinding<GateAction> by lazy {
        AlertDialogBinding(
            title = resources.getString(R.string.auth_fail_title),
            message = resources.getString(R.string.auth_fail_message),
            isCancelable = false,
            positiveButtonText = resources.getString(R.string.retry),
            positiveAction = RetryAuthDialogButtonTouched,
            negativeButtonText = resources.getString(R.string.close_app),
            negativeAction = CloseAppDialogButtonTouched,
            cancelAction = CloseAppDialogButtonTouched
        )
    }

    override fun handleAction(action: GateAction) {
        when (action) {
            is AuthCodeRetrieved -> handleAuthCodeRetrieved(action)
            is AuthCodeRetrievalFailed -> handleAuthCodeRetrievalFailed()
            is CloseAppDialogButtonTouched -> stateStream.accept(GateState.CloseApp)
            is RetryAuthDialogButtonTouched -> stateStream.accept(GateState.RequestSpotifyAuthorization)
            is SpotifySignInButtonTouched -> stateStream.accept(GateState.RequestSpotifyAuthorization)
        }
    }

    private fun handleAuthCodeRetrieved(action: AuthCodeRetrieved) {
        socialQPreferences.authCode = action.authCode
        authService.getTokensWithAuthCode(action.authCode)
            .subscribeOn(Schedulers.io())
            .map { response ->
                when (response) {
                    is AuthService.AuthResponse.Success -> {
                        socialQPreferences.accessToken = response.body.accessToken
                        socialQPreferences.refreshToken = response.body.refreshToken
                        StartAuthRefreshJobAndNavigateToLaunch
                    }
                    is AuthService.AuthResponse.Failure,
                    AuthService.AuthResponse.Timeout -> ShowSignInFailedDialog(authFailedDialogBinding)
                }
            }
            .onErrorReturn { ShowSignInFailedDialog(authFailedDialogBinding) }
            .subscribe(stateStream)
            .addTo(subscriptions)
    }

    private fun handleAuthCodeRetrievalFailed() {
        stateStream.accept(ShowSignInFailedDialog(authFailedDialogBinding))
    }

    sealed class GateState {
        object CloseApp : GateState()

        object StartAuthRefreshJobAndNavigateToLaunch : GateState()

        data class ShowSignInFailedDialog(val binding: AlertDialogBinding<GateAction>) : GateState()

        object RequestSpotifyAuthorization : GateState()
    }

    sealed class GateAction {
        data class AuthCodeRetrieved(val authCode: String) : GateAction()

        object AuthCodeRetrievalFailed : GateAction()

        object CloseAppDialogButtonTouched : GateAction()

        object RetryAuthDialogButtonTouched : GateAction()

        object SpotifySignInButtonTouched : GateAction()
    }
}