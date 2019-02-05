package com.chrisfry.socialq.userinterface.activities

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.fragments.LaunchFragment

class LaunchActivity : BaseActivity(), LaunchFragment.LaunchFragmentListener {
    companion object {
        val TAG = LaunchActivity::class.java.name
    }

    // Reference to nav controller
    private lateinit var navController: NavController

    // LaunchFragment methods
    override fun queueSelected(endpointId: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launch_activity)

        // Retrieve nav controller
        navController = findNavController(R.id.frag_nav_host)
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        // Setup the app toolbar
        val toolbar = findViewById<Toolbar>(R.id.app_toolbar)
        if (toolbar != null) {
            toolbar.setupWithNavController(navController, appBarConfiguration)
        }
    }
}