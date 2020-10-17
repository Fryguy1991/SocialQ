package com.chrisf.socialq.processor

import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.processor.SplashProcessor.SplashAction
import com.chrisf.socialq.processor.SplashProcessor.SplashAction.ViewCreated
import com.chrisf.socialq.processor.SplashProcessor.SplashState
import com.chrisf.socialq.processor.SplashProcessor.SplashState.NavigateToGate
import com.chrisf.socialq.processor.SplashProcessor.SplashState.NavigateToLaunch
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

/**
 * Processor for determining what screen should be presented on app launch
 */
class SplashProcessor @Inject constructor(
    private val socialQPreferences: SocialQPreferences,
    subscriptions: CompositeDisposable
) : BaseProcessor<SplashState, SplashAction>(null, subscriptions) {

    override fun handleAction(action: SplashAction) {
        when (action) {
            is ViewCreated -> handleViewCreated()
        }
    }

    private fun handleViewCreated() {
        val destination = if (socialQPreferences.refreshToken == null) {
            NavigateToGate
        } else {
            NavigateToLaunch
        }
        stateStream.accept(destination)
    }

    sealed class SplashState {
        object NavigateToGate : SplashState()

        object NavigateToLaunch : SplashState()
    }

    sealed class SplashAction {
        object ViewCreated : SplashAction()
    }
}