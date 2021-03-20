package com.chrisf.socialq.userinterface.activities

import android.content.Intent
import android.os.Bundle
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.extensions.filterEmissions
import com.chrisf.socialq.processor.GateProcessor
import com.chrisf.socialq.processor.GateProcessor.GateAction
import com.chrisf.socialq.processor.GateProcessor.GateAction.AuthCodeRetrievalFailed
import com.chrisf.socialq.processor.GateProcessor.GateAction.SpotifySignInButtonTouched
import com.chrisf.socialq.processor.GateProcessor.GateState
import com.chrisf.socialq.processor.GateProcessor.GateState.*
import com.jakewharton.rxbinding3.view.clicks
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_gate.*
import timber.log.Timber

/**
 * Gate screen for signing into Spotify
 */
class GateActivity : BaseActivity<GateState, GateAction, GateProcessor>() {
    override val FRAGMENT_HOLDER_ID: Int = android.view.View.NO_ID

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)

        initUi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SPOTIFY_AUTH_REQUEST_CODE -> {
                val response = AuthenticationClient.getResponse(resultCode, data)
                if (response.type == AuthenticationResponse.Type.CODE && !response.code.isNullOrBlank()) {
                    Timber.d("Authorization code granted")
                    actionStream.accept(GateAction.AuthCodeRetrieved(response.code))
                } else {
                    actionStream.accept(AuthCodeRetrievalFailed)
                }
            }
        }
    }

    override fun onBackPressed() {
        finish()
    }

    override fun handleState(state: GateState) {
        when (state) {
            is CloseApp -> finishAndRemoveTask()
            is NavigateToLaunch -> navigateToLaunch()
            is ShowSignInFailedDialog -> showAlertDialog(state.binding)
            is RequestSpotifyAuthorization -> requestSpotifyAuthorization()
        }
    }

    private fun navigateToLaunch() {
        val intent = Intent(this, LaunchActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun requestSpotifyAuthorization() {
        val accessScopes = arrayOf(
            "user-read-private",
            "streaming",
            "playlist-modify-private",
            "playlist-modify-public",
            "playlist-read-private"
        )
        val request = AuthenticationRequest.Builder(
            AppConstants.CLIENT_ID,
            AuthenticationResponse.Type.CODE,
            AppConstants.REDIRECT_URI
        ).setScopes(accessScopes)
            .build()

        AuthenticationClient.openLoginActivity(
            this,
            SPOTIFY_AUTH_REQUEST_CODE,
            request
        )
    }

    private fun initUi() {
        spotifyButton.clicks().map { SpotifySignInButtonTouched }
            .filterEmissions()
            .subscribe(actionStream)
            .addTo(subscriptions)
    }

    companion object {
        private const val SPOTIFY_AUTH_REQUEST_CODE = 100
    }
}