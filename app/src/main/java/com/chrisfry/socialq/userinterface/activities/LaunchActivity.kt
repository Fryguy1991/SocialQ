package com.chrisfry.socialq.userinterface.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.fragments.JoinQueueFragment

class LaunchActivity : BaseActivity(), JoinQueueFragment.JoinQueueFragmentListener {
    companion object {
        val TAG = LaunchActivity::class.java.name
    }

    // Reference to nav controller
    private lateinit var navController: NavController

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launch_activity)

        // Retrieve nav controller
        navController = findNavController(R.id.frag_nav_host)
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        // Setup the app toolbar
        toolbar = findViewById(R.id.app_toolbar)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Stop soft keyboard from pushing UI up
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    override fun showQueueTitle(queueTitle: String) {
        toolbar.title = queueTitle
    }
}