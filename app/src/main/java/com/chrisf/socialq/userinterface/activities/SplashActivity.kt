package com.chrisf.socialq.userinterface.activities

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.chrisf.socialq.R
import com.chrisf.socialq.dagger.components.ActivityComponent
import com.chrisf.socialq.processor.SplashProcessor
import com.chrisf.socialq.processor.SplashProcessor.SplashAction
import com.chrisf.socialq.processor.SplashProcessor.SplashAction.ViewCreated
import com.chrisf.socialq.processor.SplashProcessor.SplashState
import com.chrisf.socialq.processor.SplashProcessor.SplashState.NavigateToGate
import com.chrisf.socialq.processor.SplashProcessor.SplashState.NavigateToLaunch
import kotlinx.android.synthetic.main.activity_splash.*

/**
 * Activity for displaying the a splash screen, branching activity for navigating to different screen after launch
 * of the app
 */
class SplashActivity : BaseActivity<SplashState, SplashAction, SplashProcessor>() {
    override val FRAGMENT_HOLDER_ID = View.NO_ID

    override fun resolveDependencies(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        actionStream.accept(ViewCreated)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun handleState(state: SplashState) {
        when (state) {
            is NavigateToGate -> navigateToGate()
            is NavigateToLaunch -> navigateToLaunch()
        }
    }

    private fun navigateToGate() {
        val intent = Intent(this, GateActivity::class.java)

        val activityOptions = ActivityOptions.makeSceneTransitionAnimation(this, badgeImage, "badgeImage").toBundle()

        startActivity(intent, activityOptions)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun navigateToLaunch() {
        val intent = Intent(this, LaunchActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}